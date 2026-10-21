package edu.campus.rbac.infrastructure;

import edu.campus.rbac.service.DomainException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** 每次操作使用独立连接；写事务先取得锁，授权和业务写入共享该事务。 */
public final class Database {
    private final Path path;

    @FunctionalInterface
    public interface SqlFunction<T> { T apply(Connection connection) throws SQLException; }

    public Database(Path path) { this.path = Objects.requireNonNull(path).toAbsolutePath().normalize(); }

    public void initialize() {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ex) {
            throw new DomainException("无法创建数据库目录", ex);
        }
        write(connection -> {
            executeScript(connection, "schema.sql");
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
                if (result.next()) {
                    int version = result.getInt(1);
                    if (version > 1) throw new DomainException("数据库版本高于当前程序支持的版本");
                    if (version == 1) return null;
                }
            }
            // 只有全新数据库生成演示密码；重启不补回用户、角色或撤销的权限。
            char[] demoPassword = "Demo@12345".toCharArray();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users(username,display_name,password_hash,active) VALUES (?,?,?,1)")) {
                String[][] users = {{"admin", "系统管理员"}, {"manager", "业务主管"},
                        {"staff", "普通员工"}, {"viewer", "只读用户"}};
                for (String[] user : users) {
                    insert.setString(1, user[0]);
                    insert.setString(2, user[1]);
                    insert.setString(3, Passwords.hash(demoPassword));
                    insert.executeUpdate();
                }
            } finally {
                Arrays.fill(demoPassword, '\0');
            }
            executeScript(connection, "seed.sql");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_version(version,applied_at) VALUES(1,?)")) {
                insert.setString(1, Instant.now().toString());
                insert.executeUpdate();
            }
            return null;
        });
    }

    public <T> T read(SqlFunction<T> work) { return transaction("BEGIN", work); }

    public <T> T write(SqlFunction<T> work) { return transaction("BEGIN IMMEDIATE", work); }

    private <T> T transaction(String begin, SqlFunction<T> work) {
        try (Connection connection = open(); Statement control = connection.createStatement()) {
            control.execute(begin);
            try {
                T result = work.apply(connection);
                control.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException | Error ex) {
                try { control.execute("ROLLBACK"); }
                catch (SQLException rollbackFailure) { ex.addSuppressed(rollbackFailure); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DomainException("数据库操作失败：" + ex.getMessage(), ex);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            return connection;
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }

    private static void executeScript(Connection connection, String resource) throws SQLException {
        try (InputStream input = Database.class.getResourceAsStream("/" + resource)) {
            if (input == null) throw new DomainException("缺少数据库初始化资源：" + resource);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
        } catch (IOException ex) {
            throw new DomainException("无法读取数据库初始化脚本", ex);
        }
    }
}
