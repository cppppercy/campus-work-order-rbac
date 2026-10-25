package edu.campus.rbac;

import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** RBAC1 tests use SQLite and the production service layer; no permission result is mocked. */
class RoleHierarchyTest {
    @TempDir static Path directory;
    private static Path template;
    private Path file;
    private Database database;
    private Services.Bundle services;

    @BeforeAll static void prepareTemplate() {
        template = directory.resolve("rbac1-template.db");
        new Database(template).initialize();
    }
    @BeforeEach void setUp() throws Exception {
        file = directory.resolve(UUID.randomUUID() + ".db");
        Files.copy(template, file);
        database = new Database(file);
        services = ApplicationFactory.open(file);
    }

    private String login(String username) { return services.authentication().login(username, "Demo@12345".toCharArray()).token(); }
    private long role(String token, String code) {
        return services.roles().list(token).stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow().id();
    }
    private long permission(String token, String code) {
        return services.roles().permissions(token).stream().filter(p -> p.code().equals(code)).findFirst().orElseThrow().id();
    }
    private long user(String token, String username) {
        return services.users().list(token).stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow().id();
    }
    private Role createRole(String token, String code) { return services.roles().create(token, code, code + " role"); }

    @Test void noDefaultHierarchyKeepsRbac0Permissions() {
        String admin = login("admin"), staff = login("staff");
        assertEquals(0, count("role_hierarchy"));
        assertFalse(services.permissions().refresh(staff).permissions().contains("workorder:delete"));
        assertTrue(services.roles().effectivePermissions(admin, role(admin, "STAFF")).stream().allMatch(RolePermissionGrant::direct));
    }

    @Test void inheritanceIsDirectionalTransitiveAndUsesPermissionUnion() {
        String admin = login("admin");
        Role senior = createRole(admin, "SENIOR"), middle = createRole(admin, "MIDDLE"), junior = createRole(admin, "JUNIOR");
        long delete = permission(admin, "workorder:delete"), export = permission(admin, "workorder:export");
        services.roles().setPermissions(admin, middle.id(), Set.of(delete));
        services.roles().setPermissions(admin, junior.id(), Set.of(export));
        services.roles().addInheritance(admin, senior.id(), middle.id());
        services.roles().addInheritance(admin, middle.id(), junior.id());

        long staffId = user(admin, "staff");
        services.users().setRoles(admin, staffId, Set.of(senior.id()));
        String staff = login("staff");
        assertTrue(services.permissions().refresh(staff).permissions().containsAll(Set.of("workorder:delete", "workorder:export")));
        assertFalse(services.permissions().refresh(staff).permissions().contains("permission:grant"));
        List<RolePermissionGrant> grants = services.roles().effectivePermissions(admin, senior.id());
        assertTrue(grants.stream().anyMatch(g -> g.permission().id() == delete && !g.direct() && g.inherited()));
        assertTrue(grants.stream().anyMatch(g -> g.permission().id() == export && !g.direct() && g.inherited()));

        services.roles().setPermissions(admin, senior.id(), Set.of(delete));
        RolePermissionGrant both = services.roles().effectivePermissions(admin, senior.id()).stream()
                .filter(g -> g.permission().id() == delete).findFirst().orElseThrow();
        assertTrue(both.direct() && both.inherited(), "同一权限的直接与继承来源都应可见");
    }

    @Test void hierarchyChangesTakeEffectImmediatelyAndDoNotRemoveDirectGrants() {
        String admin = login("admin");
        Role parent = createRole(admin, "PARENT"), child = createRole(admin, "CHILD");
        long delete = permission(admin, "workorder:delete");
        services.roles().setPermissions(admin, child.id(), Set.of(delete));
        services.users().setRoles(admin, user(admin, "staff"), Set.of(parent.id()));
        String staff = login("staff");
        assertFalse(services.permissions().refresh(staff).permissions().contains("workorder:delete"));
        services.roles().addInheritance(admin, parent.id(), child.id());
        assertTrue(services.permissions().refresh(staff).permissions().contains("workorder:delete"));
        services.roles().setPermissions(admin, parent.id(), Set.of(delete));
        services.roles().removeInheritance(admin, parent.id(), child.id());
        assertTrue(services.permissions().refresh(staff).permissions().contains("workorder:delete"));
        services.roles().setPermissions(admin, parent.id(), Set.of());
        assertFalse(services.permissions().refresh(staff).permissions().contains("workorder:delete"));
    }

    @Test void selfCyclesMissingRolesAndUnprivilegedChangesAreRejected() {
        String admin = login("admin"), manager = login("manager");
        Role a = createRole(admin, "A"), b = createRole(admin, "B"), c = createRole(admin, "C");
        assertThrows(DomainException.class, () -> services.roles().addInheritance(admin, a.id(), a.id()));
        assertThrows(DomainException.class, () -> services.roles().addInheritance(admin, a.id(), 999_999L));
        services.roles().addInheritance(admin, a.id(), b.id());
        services.roles().addInheritance(admin, b.id(), c.id());
        assertThrows(DomainException.class, () -> services.roles().addInheritance(admin, c.id(), a.id()));
        assertThrows(DomainException.class, () -> services.roles().addInheritance(manager, a.id(), c.id()));
        assertEquals(List.of(b.id()), services.roles().juniorRoles(admin, a.id()).stream().map(Role::id).toList());
    }

    @Test void deletingRoleCleansItsHierarchyEdgesAndWritesAudit() {
        String admin = login("admin");
        Role parent = createRole(admin, "PARENT_DELETE"), child = createRole(admin, "CHILD_DELETE");
        services.roles().addInheritance(admin, parent.id(), child.id());
        assertEquals(1, count("role_hierarchy"));
        services.roles().delete(admin, child.id());
        assertEquals(0, count("role_hierarchy"));
        assertTrue(services.audit().list(admin, "role").stream()
                .anyMatch(e -> e.action().equals("role:add-inheritance") && e.result().equals("SUCCESS")));
    }

    @Test void hierarchyCannotRemoveTheLastEffectiveAdministrator() {
        String admin = login("admin");
        Role guardian = createRole(admin, "GUARDIAN");
        long adminRole = role(admin, "ADMIN"), adminUser = user(admin, "admin"), staffUser = user(admin, "staff");
        services.users().setRoles(admin, staffUser, Set.of(guardian.id()));
        services.roles().addInheritance(admin, guardian.id(), adminRole);
        services.users().delete(admin, adminUser);
        String guardianSession = login("staff");
        assertThrows(DomainException.class, () -> services.roles().removeInheritance(guardianSession, guardian.id(), adminRole));
        assertEquals(List.of(adminRole), services.roles().juniorRoles(guardianSession, guardian.id()).stream().map(Role::id).toList());
    }

    @Test void v1DatabaseMigratesOnceWithoutRestoringRevokedPermissionsOrData() {
        String admin = login("admin");
        long staff = role(admin, "STAFF"), create = permission(admin, "workorder:create");
        Set<Long> reduced = new HashSet<>(services.roles().permissionIds(admin, staff));
        reduced.remove(create);
        services.roles().setPermissions(admin, staff, reduced);
        database.write(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE work_orders SET title='migration-preserved' WHERE id=1");
                s.executeUpdate("DROP TABLE role_hierarchy");
                s.executeUpdate("DELETE FROM schema_version WHERE version=2");
            }
            return null;
        });
        Database upgraded = new Database(file);
        upgraded.initialize(); upgraded.initialize();
        assertEquals(2, scalar(upgraded, "SELECT MAX(version) FROM schema_version"));
        assertEquals(0, scalar(upgraded, "SELECT COUNT(*) FROM role_hierarchy"));
        assertEquals(0, scalar(upgraded, "SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id WHERE rp.role_id="
                + staff + " AND p.code='workorder:create'"));
        assertEquals("migration-preserved", text(upgraded, "SELECT title FROM work_orders WHERE id=1"));
    }

    private int count(String table) { return scalar(database, "SELECT COUNT(*) FROM " + table); }
    private static int scalar(Database db, String sql) {
        return db.read(c -> { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getInt(1); } });
    }
    private static String text(Database db, String sql) {
        return db.read(c -> { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getString(1); } });
    }
}
