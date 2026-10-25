package edu.campus.rbac.infrastructure;

import edu.campus.rbac.service.DomainException;
import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** 服务统一授权入口：从可信令牌得到身份，在业务所用连接中读取当前授权。 */
public final class Access {
    private final Database database;
    private final SessionRegistry sessions;

    @FunctionalInterface
    public interface AuthorizedWork<T> {
        T run(Connection connection, long actorId) throws SQLException;
    }

    public static final class Denied extends DomainException {
        public Denied(String message) { super(message); }
    }

    public Access(Database database, SessionRegistry sessions) {
        this.database = database;
        this.sessions = sessions;
    }

    public <T> T read(String token, String action, String target, Set<String> required, AuthorizedWork<T> work) {
        return execute(false, token, action, target, required, work);
    }

    public <T> T write(String token, String action, String target, Set<String> required, AuthorizedWork<T> work) {
        return execute(true, token, action, target, required, work);
    }

    private <T> T execute(boolean write, String token, String action, String target,
                          Set<String> required, AuthorizedWork<T> work) {
        Long[] actor = {null};
        Database.SqlFunction<T> operation = connection -> {
            Long id = sessions.resolve(token);
            if (id == null) throw new Denied("登录会话无效，请重新登录");
            try (PreparedStatement p = connection.prepareStatement("SELECT active FROM users WHERE id=?")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new Denied("用户不存在，请重新登录");
                    actor[0] = id;
                    if (!r.getBoolean(1)) throw new Denied("用户已停用");
                }
            }
            Set<String> current = effectivePermissions(connection, id);
            if (!current.containsAll(required)) {
                Set<String> missing = new java.util.TreeSet<>(required);
                missing.removeAll(current);
                throw new Denied("权限不足：" + String.join("、", missing));
            }
            T result = work.run(connection, id);
            if (write) audit(connection, actor[0], action, target, "SUCCESS", "操作成功");
            return result;
        };
        try {
            T result = write ? database.write(operation) : database.read(operation);
            if (!write) record(actor[0], action, target, "SUCCESS", "查询成功");
            return result;
        } catch (Denied denied) {
            // 原事务已回滚。拒绝审计必须使用新的事务，否则日志也会被回滚。
            record(actor[0], action, target, "DENIED", denied.getMessage());
            throw denied;
        }
    }

    public static Set<String> effectivePermissions(Connection connection, long userId) throws SQLException {
        Set<String> permissions = new HashSet<>();
        try (PreparedStatement p = connection.prepareStatement("WITH RECURSIVE reachable(role_id) AS ("
                + "SELECT role_id FROM user_roles WHERE user_id=? "
                + "UNION SELECT h.junior_role_id FROM role_hierarchy h JOIN reachable r ON h.senior_role_id=r.role_id"
                + ") SELECT DISTINCT p.code FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id "
                + "JOIN reachable r ON r.role_id=rp.role_id")) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) permissions.add(r.getString(1)); }
        }
        return Set.copyOf(permissions);
    }

    public void record(Long actor, String action, String target, String result, String detail) {
        try {
            database.write(connection -> { audit(connection, actor, action, target, result, detail); return null; });
        } catch (DomainException ex) {
            throw new DomainException("操作审计写入失败，请检查数据库：" + ex.getMessage(), ex);
        }
    }

    public static void audit(Connection c, Long actor, String action, String target, String result, String detail)
            throws SQLException {
        // 用户可以在另一个管理员存在时删除自己；历史审计保留，已删除操作人置空。
        try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(created_at,actor_id,action,target,result,detail) "
                + "VALUES (?,(SELECT id FROM users WHERE id=?),?,?,?,?)")) {
            p.setString(1, Instant.now().toString());
            if (actor == null) p.setNull(2, Types.INTEGER); else p.setLong(2, actor);
            p.setString(3, action); p.setString(4, target == null ? "" : target);
            p.setString(5, result); p.setString(6, detail == null ? "" : detail);
            p.executeUpdate();
        }
    }
}
