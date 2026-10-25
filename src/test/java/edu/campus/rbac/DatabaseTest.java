package edu.campus.rbac;

import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.infrastructure.Passwords;
import edu.campus.rbac.service.DomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path directory;
    private Database database;
    private Path file;

    @BeforeEach void initialize() {
        file = directory.resolve("nested/campus.db");
        database = new Database(file);
        database.initialize();
    }

    @Test void tc01InitializesAllTablesAndDemoData() {
        assertTrue(Files.isRegularFile(file));
        assertEquals(4, count("users"));
        assertEquals(4, count("roles"));
        assertEquals(23, count("permissions"));
        assertEquals(4, count("user_roles"));
        assertEquals(41, count("role_permissions"));
        assertEquals(2, count("work_orders"));
        assertEquals(0, count("audit_logs"));
        assertEquals(2, count("schema_version"));
        assertEquals(0, count("role_hierarchy"));
        database.read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(result.next());
            }
            return null;
        });
    }

    @Test void tc15And19RestartPreservesRevocationsAndBusinessData() {
        database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE code='STAFF')");
                statement.executeUpdate("UPDATE work_orders SET title='重启前修改标题' WHERE id=1");
            }
            return null;
        });
        Database restarted = new Database(file);
        restarted.initialize();
        restarted.initialize();
        assertEquals(4, count("users"));
        assertEquals(36, count("role_permissions"));
        assertEquals(2, count("work_orders"));
        restarted.read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT title FROM work_orders WHERE id=1")) {
                assertTrue(result.next());
                assertEquals("重启前修改标题", result.getString(1));
            }
            return null;
        });
    }

    @Test void passwordsUseIndependentSaltsAndRejectWrongOrMalformedInput() {
        database.read(connection -> {
            java.util.Set<String> hashes = new java.util.HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT password_hash FROM users")) {
                while (result.next()) {
                    String hash = result.getString(1);
                    assertTrue(hash.startsWith("pbkdf2-sha256$600000$"));
                    assertFalse(hash.contains("Demo@12345"));
                    assertTrue(Passwords.verify("Demo@12345".toCharArray(), hash));
                    assertFalse(Passwords.verify("incorrect".toCharArray(), hash));
                    hashes.add(hash);
                }
            }
            assertEquals(4, hashes.size());
            return null;
        });
        assertFalse(Passwords.verify("x".toCharArray(), "invalid"));
        assertFalse(Passwords.verify("x".toCharArray(), "pbkdf2-sha256$2147483647$a$b"));
        assertFalse(Passwords.verify(null, "anything"));
    }

    @Test void foreignKeysAndUniqueConstraintsAreEnforced() {
        assertThrows(DomainException.class, () -> database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO user_roles(user_id,role_id) VALUES(99999,1)");
            }
            return null;
        }));
        assertThrows(DomainException.class, () -> database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO user_roles(user_id,role_id) SELECT user_id,role_id FROM user_roles LIMIT 1");
            }
            return null;
        }));
        assertEquals(4, count("user_roles"));
    }

    @Test void failedTransactionRollsBackAllChanges() {
        assertThrows(DomainException.class, () -> database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO roles(code,name) VALUES('ROLLBACK','事务测试')");
                statement.executeUpdate("INSERT INTO user_roles(user_id,role_id) VALUES(99999,1)");
            }
            return null;
        }));
        assertEquals(4, count("roles"));
        assertThrows(IllegalStateException.class, () -> database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM work_orders");
            }
            throw new IllegalStateException("模拟业务异常");
        }));
        assertEquals(2, count("work_orders"));
    }

    @Test void deletingUsersPreservesWorkOrdersAndAuditHistory() {
        database.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO audit_logs(created_at,actor_id,action,target,result,detail) "
                        + "SELECT '2026-10-21T00:00:00Z',id,'TEST','user','SUCCESS','历史审计' FROM users WHERE username='staff'");
                statement.executeUpdate("DELETE FROM users WHERE username='staff'");
            }
            return null;
        });
        assertEquals(2, count("work_orders"));
        assertEquals(1, count("audit_logs"));
        assertEquals(3, count("user_roles"));
        database.read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT actor_id FROM audit_logs")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT creator_id FROM work_orders WHERE id=1")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
            }
            return null;
        });
    }

    private int count(String table) {
        return database.read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                result.next();
                return result.getInt(1);
            }
        });
    }
}
