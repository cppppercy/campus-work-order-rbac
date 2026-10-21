package edu.campus.rbac;

import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.*;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 直接验证数据库授权、可信会话、管理员保护和审计，不模拟权限结果。 */
class SecurityTest {
    @TempDir Path directory;
    private Path file;
    private Database database;
    private SecurityServices security;
    private Services.Bundle services;

    @BeforeEach void setUp() {
        file = directory.resolve("security.db");
        database = new Database(file);
        database.initialize();
        security = new SecurityServices(database);
        BusinessServices business = new BusinessServices(security.access());
        services = new Services.Bundle(security.authentication(), security.permissions(), security.users(),
                security.roles(), business, business, business);
    }

    private String login(String name) {
        return services.authentication().login(name, "Demo@12345".toCharArray()).token();
    }
    private long user(String token, String username) {
        return services.users().list(token).stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow().id();
    }
    private long role(String token, String code) {
        return services.roles().list(token).stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow().id();
    }
    private long permission(String token, String code) {
        return services.roles().permissions(token).stream().filter(p -> p.code().equals(code)).findFirst().orElseThrow().id();
    }

    @Test void randomAndLoggedOutTokensAreRejectedAndAuditedWithoutTrustedActor() {
        assertThrows(DomainException.class, () -> services.permissions().refresh("forged-admin-id-1"));
        String admin = login("admin");
        services.authentication().logout(admin);
        assertThrows(DomainException.class, () -> services.permissions().require(admin, "permission:grant"));
        String fresh = login("admin");
        List<AuditEntry> denied = services.audit().list(fresh, "DENIED");
        assertTrue(denied.stream().filter(e -> e.action().equals("permission:refresh") || e.action().equals("permission:check"))
                .allMatch(e -> e.actorId() == null));
    }

    @Test void failedLoginUsesUniformMessageAndNeverCreatesSession() {
        DomainException missing = assertThrows(DomainException.class,
                () -> services.authentication().login("not-a-user", "wrong-password".toCharArray()));
        DomainException wrong = assertThrows(DomainException.class,
                () -> services.authentication().login("admin", "wrong-password".toCharArray()));
        assertEquals(missing.getMessage(), wrong.getMessage());
        assertFalse(missing.getMessage().contains("not-a-user"));
        assertThrows(DomainException.class, () -> services.permissions().refresh(""));
    }

    @Test void permissionRevocationAffectsAnAlreadyIssuedSession() {
        String admin = login("admin");
        String staff = login("staff");
        long staffRole = role(admin, "STAFF");
        Set<Long> permissions = new HashSet<>(services.roles().permissionIds(admin, staffRole));
        long create = permission(admin, "workorder:create");
        assertTrue(permissions.remove(create));
        services.roles().setPermissions(admin, staffRole, permissions);
        assertThrows(DomainException.class,
                () -> services.workOrders().create(staff, new WorkOrderInput("已撤权", "旧会话也不能新增")));
        assertFalse(services.permissions().refresh(staff).permissions().contains("workorder:create"));
    }

    @Test void disablingOrDeletingTheOnlyEffectiveAdministratorRollsBack() {
        String admin = login("admin");
        long adminId = user(admin, "admin");
        assertThrows(DomainException.class, () -> services.users().update(admin, adminId, "系统管理员", false));
        assertTrue(services.users().list(admin).stream().filter(u -> u.id() == adminId).findFirst().orElseThrow().active());
        assertThrows(DomainException.class, () -> services.users().delete(admin, adminId));
        assertTrue(services.users().list(admin).stream().anyMatch(u -> u.id() == adminId));
    }

    @Test void removingOnlyAdministratorsRoleOrCapabilitiesRollsBack() {
        String admin = login("admin");
        long adminId = user(admin, "admin");
        long adminRole = role(admin, "ADMIN");
        Set<Long> originalRoles = services.users().roleIds(admin, adminId);
        assertThrows(DomainException.class, () -> services.users().setRoles(admin, adminId, Set.of()));
        assertEquals(originalRoles, services.users().roleIds(admin, adminId));

        Set<Long> originalPermissions = services.roles().permissionIds(admin, adminRole);
        Set<Long> reduced = new HashSet<>(originalPermissions);
        reduced.remove(permission(admin, "permission:grant"));
        assertThrows(DomainException.class, () -> services.roles().setPermissions(admin, adminRole, reduced));
        assertEquals(originalPermissions, services.roles().permissionIds(admin, adminRole));
        assertThrows(DomainException.class, () -> services.roles().delete(admin, adminRole));
        assertTrue(services.roles().list(admin).stream().anyMatch(r -> r.id() == adminRole));
    }

    @Test void anotherEffectiveAdministratorAllowsRemovingTheOriginalOne() {
        String admin = login("admin");
        User backup = services.users().create(admin, "backup_admin", "备用管理员", "Backup@12345".toCharArray());
        services.users().setRoles(admin, backup.id(), Set.of(role(admin, "ADMIN")));
        services.users().delete(admin, user(admin, "admin"));
        assertThrows(DomainException.class, () -> services.permissions().refresh(admin));
        Services.Bundle reopened = ApplicationFactory.open(file);
        Session session = reopened.authentication().login("backup_admin", "Backup@12345".toCharArray());
        assertTrue(session.permissions().contains("permission:grant"));
    }

    @Test void nonAdministratorCannotManageUsersRolesOrPermissionsBySupplyingIds() {
        String manager = login("manager");
        assertThrows(DomainException.class, () -> services.users().update(manager, 1, "伪造管理员", true));
        assertThrows(DomainException.class, () -> services.users().setRoles(manager, 2, Set.of(1L)));
        assertThrows(DomainException.class, () -> services.roles().delete(manager, 1));
        assertThrows(DomainException.class, () -> services.roles().setPermissions(manager, 2, Set.of(1L)));
        String admin = login("admin");
        assertEquals("系统管理员", services.users().list(admin).stream().filter(u -> u.username().equals("admin")).findFirst().orElseThrow().displayName());
    }

    @Test void multiTableRoleAssignmentIsAtomicOnInvalidRole() {
        String admin = login("admin");
        long staffId = user(admin, "staff");
        Set<Long> before = services.users().roleIds(admin, staffId);
        Set<Long> invalid = new HashSet<>(before);
        invalid.add(999_999L);
        assertThrows(DomainException.class, () -> services.users().setRoles(admin, staffId, invalid));
        assertEquals(before, services.users().roleIds(admin, staffId));
    }

    @Test void successfulWritesAndDeniedRequestsPersistAuditRows() {
        String admin = login("admin");
        String viewer = login("viewer");
        WorkOrder order = services.workOrders().create(admin, new WorkOrderInput("审计事务", "成功写入"));
        assertThrows(DomainException.class, () -> services.workOrders().delete(viewer, order.id()));
        List<AuditEntry> entries = services.audit().list(admin, "workorder");
        assertTrue(entries.stream().anyMatch(e -> e.action().equals("workorder:create") && e.result().equals("SUCCESS")));
        assertTrue(entries.stream().anyMatch(e -> e.action().equals("workorder:delete") && e.result().equals("DENIED")));
    }

    @Test void disabledUsersExistingSessionsAreRejected() {
        String admin = login("admin");
        String staff = login("staff");
        long staffId = user(admin, "staff");
        services.users().update(admin, staffId, "普通员工", false);
        assertThrows(DomainException.class, () -> services.workOrders().list(staff, "", ""));
        assertThrows(DomainException.class, () -> services.authentication().login("staff", "Demo@12345".toCharArray()));
    }

    @Test void databaseUniquenessFailureDoesNotLeavePartialRows() {
        String admin = login("admin");
        services.roles().create(admin, "CUSTOM", "自定义角色");
        assertThrows(DomainException.class, () -> services.roles().create(admin, "CUSTOM", "重复角色"));
        assertEquals(1, services.roles().list(admin).stream().filter(r -> r.code().equals("CUSTOM")).count());
        database.read(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM audit_logs WHERE action='role:create' AND result='SUCCESS'");
                 var r = p.executeQuery()) {
                assertTrue(r.next());
                assertEquals(1, r.getInt(1));
            }
            return null;
        });
    }

    @Test void concurrentOperationAndRevocationFollowTransactionOrder() throws Exception {
        String admin = login("admin");
        String staff = login("staff");
        long staffRole = role(admin, "STAFF");
        long deletePermission = permission(admin, "workorder:delete");
        Set<Long> original = services.roles().permissionIds(admin, staffRole);
        Set<Long> granted = new HashSet<>(original);
        granted.add(deletePermission);
        services.roles().setPermissions(admin, staffRole, granted);
        WorkOrder first = services.workOrders().create(staff, new WorkOrderInput("并发顺序一", "撤权前开始"));

        CountDownLatch insideOperation = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        CountDownLatch revocationStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> delete = pool.submit(() -> security.access().write(staff, "workorder:concurrent-delete",
                    Long.toString(first.id()), Set.of("workorder:delete"), (connection, actor) -> {
                        insideOperation.countDown();
                        try {
                            if (!releaseOperation.await(5, TimeUnit.SECONDS)) throw new java.sql.SQLException("等待并发测试超时");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new java.sql.SQLException("并发测试被中断", interrupted);
                        }
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM work_orders WHERE id=?")) {
                            statement.setLong(1, first.id());
                            assertEquals(1, statement.executeUpdate());
                        }
                        return null;
                    }));
            assertTrue(insideOperation.await(5, TimeUnit.SECONDS));
            Future<?> revoke = pool.submit(() -> {
                revocationStarted.countDown();
                services.roles().setPermissions(admin, staffRole, original);
            });
            assertTrue(revocationStarted.await(5, TimeUnit.SECONDS));
            assertFalse(revoke.isDone(), "撤权事务不应越过已经持有写锁的操作");
            releaseOperation.countDown();
            delete.get(5, TimeUnit.SECONDS);
            revoke.get(5, TimeUnit.SECONDS);

            assertTrue(services.workOrders().list(staff, "并发顺序一", "").isEmpty());
            WorkOrder second = services.workOrders().create(admin, new WorkOrderInput("并发顺序二", "撤权提交后"));
            assertThrows(DomainException.class, () -> services.workOrders().delete(staff, second.id()));
        } finally {
            releaseOperation.countDown();
            pool.shutdownNow();
        }
    }
}
