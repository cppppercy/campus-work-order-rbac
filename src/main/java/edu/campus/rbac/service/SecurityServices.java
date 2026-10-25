package edu.campus.rbac.service;

import edu.campus.rbac.infrastructure.Access;
import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.infrastructure.Passwords;
import edu.campus.rbac.infrastructure.SessionRegistry;
import edu.campus.rbac.model.Models.*;
import java.sql.*;
import java.util.*;

/** 可复用 RBAC0 核心。不通过用户名或角色名称判断管理员身份。 */
public final class SecurityServices {
    private final Database database;
    private final SessionRegistry sessions = new SessionRegistry();
    private final Access access;
    private final Services.Authentication authentication = new AuthenticationImpl();
    private final Services.Permissions permissions = new PermissionsImpl();
    private final Services.Users users = new UsersImpl();
    private final Services.Roles roles = new RolesImpl();
    // 确保至少有一个启用用户可以通过真实菜单、页面和按钮恢复管理权限。
    private static final Set<String> ADMIN_CAPABILITIES = Set.of("menu:users", "page:users:view", "user:list",
            "user:manage", "menu:roles", "page:roles:view", "role:list", "role:manage", "permission:grant");

    public SecurityServices(Database database) { this.database = database; this.access = new Access(database, sessions); }
    public Services.Authentication authentication() { return authentication; }
    public Services.Permissions permissions() { return permissions; }
    public Services.Users users() { return users; }
    public Services.Roles roles() { return roles; }
    public Access access() { return access; }

    private final class AuthenticationImpl implements Services.Authentication {
        @Override public Session login(String username, char[] password) {
            String name = username == null ? "" : username.trim();
            Long[] actor = {null};
            try {
                Session data = database.write(c -> {
                    try (PreparedStatement p = c.prepareStatement("SELECT id,password_hash,active FROM users WHERE username=?")) {
                        p.setString(1, name);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) throw new Access.Denied("用户名或密码错误，或账号已停用");
                            long id = r.getLong("id");
                            if (password == null || !Passwords.verify(password, r.getString("password_hash")) || !r.getBoolean("active"))
                                throw new Access.Denied("用户名或密码错误，或账号已停用");
                            actor[0] = id;
                            Access.audit(c, id, "auth:login", name, "SUCCESS", "登录成功");
                            return new Session("", user(c, id), Access.effectivePermissions(c, id));
                        }
                    }
                });
                return new Session(sessions.issue(data.user().id()), data.user(), data.permissions());
            } catch (Access.Denied denied) {
                access.record(actor[0], "auth:login", name, "DENIED", denied.getMessage());
                throw denied;
            }
        }

        @Override public void logout(String token) {
            Long actor = sessions.resolve(token);
            sessions.revoke(token);
            if (actor != null) access.record(actor, "auth:logout", "session", "SUCCESS", "退出登录");
        }
    }

    private final class PermissionsImpl implements Services.Permissions {
        @Override public Session refresh(String token) {
            return access.read(token, "permission:refresh", "session", Set.of(),
                    (c, actor) -> new Session(token, user(c, actor), Access.effectivePermissions(c, actor)));
        }
        @Override public void require(String token, String permission) {
            if (permission == null || permission.isBlank()) throw new DomainException("权限编码不能为空");
            access.read(token, "permission:check", permission, Set.of(permission), (c, actor) -> null);
        }
    }

    private final class UsersImpl implements Services.Users {
        @Override public List<User> list(String token) {
            return access.read(token, "user:list", "users", Set.of("user:list"), (c, actor) -> {
                List<User> result = new ArrayList<>();
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM users ORDER BY id"); ResultSet r = p.executeQuery()) {
                    while (r.next()) result.add(user(c, r.getLong(1)));
                }
                return List.copyOf(result);
            });
        }
        @Override public User create(String token, String username, String displayName, char[] password) {
            return access.write(token, "user:create", username, Set.of("user:manage"), (c, actor) -> {
                String name = text(username, "用户名", 64);
                if (!name.matches("[A-Za-z0-9_.-]+")) throw new DomainException("用户名只能包含英文字母、数字、下划线、点和短横线");
                String display = text(displayName, "显示名称", 100);
                validatePassword(password);
                unique(c, "SELECT 1 FROM users WHERE username=?", name, "用户名已存在");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO users(username,display_name,password_hash,active) VALUES(?,?,?,1)")) {
                    p.setString(1, name); p.setString(2, display); p.setString(3, Passwords.hash(password)); p.executeUpdate();
                }
                return user(c, insertedId(c));
            });
        }
        @Override public void update(String token, long id, String displayName, boolean active) {
            access.write(token, "user:update", Long.toString(id), Set.of("user:manage"), (c, actor) -> {
                user(c, id);
                try (PreparedStatement p = c.prepareStatement("UPDATE users SET display_name=?,active=? WHERE id=?")) {
                    p.setString(1, text(displayName, "显示名称", 100)); p.setBoolean(2, active); p.setLong(3, id); p.executeUpdate();
                }
                ensureAdministrator(c);
                return null;
            });
            if (!active) sessions.revokeUser(id);
        }
        @Override public void resetPassword(String token, long id, char[] password) {
            access.write(token, "user:reset-password", Long.toString(id), Set.of("user:manage", "permission:grant"), (c, actor) -> {
                user(c, id); validatePassword(password);
                try (PreparedStatement p = c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
                    p.setString(1, Passwords.hash(password)); p.setLong(2, id); p.executeUpdate();
                }
                return null;
            });
            sessions.revokeUser(id);
        }
        @Override public void delete(String token, long id) {
            access.write(token, "user:delete", Long.toString(id), Set.of("user:manage"), (c, actor) -> {
                user(c, id);
                executeId(c, "DELETE FROM users WHERE id=?", id);
                ensureAdministrator(c); return null;
            });
            sessions.revokeUser(id);
        }
        @Override public Set<Long> roleIds(String token, long id) {
            return access.read(token, "user:roles", Long.toString(id), Set.of("user:list"), (c, actor) -> {
                user(c, id); return ids(c, "SELECT role_id FROM user_roles WHERE user_id=?", id);
            });
        }
        @Override public void setRoles(String token, long id, Set<Long> roleIds) {
            access.write(token, "user:set-roles", Long.toString(id), Set.of("user:manage", "permission:grant"), (c, actor) -> {
                user(c, id); Set<Long> selected = selection(roleIds);
                for (long roleId : selected) role(c, roleId);
                executeId(c, "DELETE FROM user_roles WHERE user_id=?", id);
                try (PreparedStatement p = c.prepareStatement("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)")) {
                    for (long roleId : selected) { p.setLong(1, id); p.setLong(2, roleId); p.addBatch(); } p.executeBatch();
                }
                ensureAdministrator(c); return null;
            });
        }
    }

    private final class RolesImpl implements Services.Roles {
        @Override public List<Role> list(String token) {
            return access.read(token, "role:list", "roles", Set.of("role:list"), (c, actor) -> {
                List<Role> result = new ArrayList<>();
                try (PreparedStatement p = c.prepareStatement("SELECT id,code,name FROM roles ORDER BY id"); ResultSet r = p.executeQuery()) {
                    while (r.next()) result.add(new Role(r.getLong(1), r.getString(2), r.getString(3)));
                }
                return List.copyOf(result);
            });
        }
        @Override public Role create(String token, String code, String name) {
            return access.write(token, "role:create", code, Set.of("role:manage"), (c, actor) -> {
                String roleCode = text(code, "角色编码", 64).toUpperCase(Locale.ROOT);
                if (!roleCode.matches("[A-Z0-9_]+")) throw new DomainException("角色编码只能包含英文字母、数字和下划线");
                String roleName = text(name, "角色名称", 100);
                unique(c, "SELECT 1 FROM roles WHERE code=?", roleCode, "角色编码已存在");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO roles(code,name) VALUES(?,?)")) {
                    p.setString(1, roleCode); p.setString(2, roleName); p.executeUpdate();
                }
                return role(c, insertedId(c));
            });
        }
        @Override public void update(String token, long id, String name) {
            access.write(token, "role:update", Long.toString(id), Set.of("role:manage"), (c, actor) -> {
                role(c, id);
                try (PreparedStatement p = c.prepareStatement("UPDATE roles SET name=? WHERE id=?")) {
                    p.setString(1, text(name, "角色名称", 100)); p.setLong(2, id); p.executeUpdate();
                }
                return null;
            });
        }
        @Override public void delete(String token, long id) {
            access.write(token, "role:delete", Long.toString(id), Set.of("role:manage", "permission:grant"), (c, actor) -> {
                role(c, id); executeId(c, "DELETE FROM roles WHERE id=?", id); ensureAdministrator(c); return null;
            });
        }
        @Override public List<Permission> permissions(String token) {
            return access.read(token, "permission:list", "permissions", Set.of("role:list"), (c, actor) -> {
                List<Permission> result = new ArrayList<>();
                try (PreparedStatement p = c.prepareStatement("SELECT id,code,name,type FROM permissions ORDER BY id"); ResultSet r = p.executeQuery()) {
                    while (r.next()) result.add(new Permission(r.getLong(1), r.getString(2), r.getString(3), r.getString(4)));
                }
                return List.copyOf(result);
            });
        }
        @Override public Set<Long> permissionIds(String token, long id) {
            return access.read(token, "role:permissions", Long.toString(id), Set.of("role:list"), (c, actor) -> {
                role(c, id); return ids(c, "SELECT permission_id FROM role_permissions WHERE role_id=?", id);
            });
        }
        @Override public List<RolePermissionGrant> effectivePermissions(String token, long id) {
            return access.read(token, "role:effective-permissions", Long.toString(id), Set.of("role:list"), (c, actor) -> {
                role(c, id);
                Set<Long> direct = ids(c, "SELECT permission_id FROM role_permissions WHERE role_id=?", id);
                Set<Long> inherited = inheritedPermissionIds(c, id);
                List<RolePermissionGrant> result = new ArrayList<>();
                try (PreparedStatement p = c.prepareStatement("SELECT id,code,name,type FROM permissions ORDER BY type,id");
                     ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long permissionId = r.getLong(1);
                        boolean isDirect = direct.contains(permissionId), isInherited = inherited.contains(permissionId);
                        if (isDirect || isInherited) result.add(new RolePermissionGrant(
                                new Permission(permissionId, r.getString(2), r.getString(3), r.getString(4)), isDirect, isInherited));
                    }
                }
                return List.copyOf(result);
            });
        }
        @Override public void setPermissions(String token, long id, Set<Long> permissionIds) {
            access.write(token, "role:set-permissions", Long.toString(id), Set.of("permission:grant"), (c, actor) -> {
                role(c, id); Set<Long> selected = selection(permissionIds);
                for (long permission : selected) {
                    try (PreparedStatement p = c.prepareStatement("SELECT id FROM permissions WHERE id=?")) {
                        p.setLong(1, permission);
                        try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new DomainException("权限不存在：" + permission); }
                    }
                }
                executeId(c, "DELETE FROM role_permissions WHERE role_id=?", id);
                try (PreparedStatement p = c.prepareStatement("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)")) {
                    for (long permission : selected) { p.setLong(1, id); p.setLong(2, permission); p.addBatch(); } p.executeBatch();
                }
                ensureAdministrator(c); return null;
            });
        }
        @Override public List<Role> juniorRoles(String token, long id) {
            return access.read(token, "role:juniors", Long.toString(id), Set.of("role:list"), (c, actor) -> {
                role(c, id);
                return directJuniorRoles(c, id);
            });
        }
        @Override public void addInheritance(String token, long seniorId, long juniorId) {
            access.write(token, "role:add-inheritance", seniorId + "->" + juniorId,
                    Set.of("role:manage", "permission:grant"), (c, actor) -> {
                role(c, seniorId); role(c, juniorId); validateInheritance(c, seniorId, juniorId);
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO role_hierarchy(senior_role_id,junior_role_id) VALUES(?,?)")) {
                    p.setLong(1, seniorId); p.setLong(2, juniorId); p.executeUpdate();
                }
                ensureAdministrator(c); return null;
            });
        }
        @Override public void removeInheritance(String token, long seniorId, long juniorId) {
            access.write(token, "role:remove-inheritance", seniorId + "->" + juniorId,
                    Set.of("role:manage", "permission:grant"), (c, actor) -> {
                role(c, seniorId); role(c, juniorId);
                try (PreparedStatement p = c.prepareStatement(
                        "DELETE FROM role_hierarchy WHERE senior_role_id=? AND junior_role_id=?")) {
                    p.setLong(1, seniorId); p.setLong(2, juniorId);
                    if (p.executeUpdate() != 1) throw new DomainException("继承关系不存在");
                }
                ensureAdministrator(c); return null;
            });
        }
        @Override public void setJuniorRoles(String token, long seniorId, Set<Long> juniorRoleIds) {
            access.write(token, "role:set-juniors", Long.toString(seniorId), Set.of("role:manage", "permission:grant"), (c, actor) -> {
                role(c, seniorId); Set<Long> selected = selection(juniorRoleIds);
                for (long juniorId : selected) role(c, juniorId);
                executeId(c, "DELETE FROM role_hierarchy WHERE senior_role_id=?", seniorId);
                for (long juniorId : selected) {
                    validateInheritance(c, seniorId, juniorId);
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO role_hierarchy(senior_role_id,junior_role_id) VALUES(?,?)")) {
                        p.setLong(1, seniorId); p.setLong(2, juniorId); p.executeUpdate();
                    }
                }
                ensureAdministrator(c); return null;
            });
        }
    }

    private static List<Role> directJuniorRoles(Connection c, long seniorId) throws SQLException {
        List<Role> result = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT r.id,r.code,r.name FROM roles r "
                + "JOIN role_hierarchy h ON h.junior_role_id=r.id WHERE h.senior_role_id=? ORDER BY r.code")) {
            p.setLong(1, seniorId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new Role(r.getLong(1), r.getString(2), r.getString(3)));
            }
        }
        return List.copyOf(result);
    }
    private static Set<Long> inheritedPermissionIds(Connection c, long seniorId) throws SQLException {
        Set<Long> result = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement("WITH RECURSIVE descendants(role_id) AS ("
                + "SELECT junior_role_id FROM role_hierarchy WHERE senior_role_id=? "
                + "UNION SELECT h.junior_role_id FROM role_hierarchy h JOIN descendants d ON h.senior_role_id=d.role_id"
                + ") SELECT DISTINCT rp.permission_id FROM role_permissions rp JOIN descendants d ON d.role_id=rp.role_id")) {
            p.setLong(1, seniorId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(r.getLong(1));
            }
        }
        return Set.copyOf(result);
    }
    private static void validateInheritance(Connection c, long seniorId, long juniorId) throws SQLException {
        if (seniorId == juniorId) throw new DomainException("角色不能继承自身");
        try (PreparedStatement p = c.prepareStatement("WITH RECURSIVE descendants(role_id) AS ("
                + "SELECT junior_role_id FROM role_hierarchy WHERE senior_role_id=? "
                + "UNION SELECT h.junior_role_id FROM role_hierarchy h JOIN descendants d ON h.senior_role_id=d.role_id"
                + ") SELECT 1 FROM descendants WHERE role_id=?")) {
            p.setLong(1, juniorId); p.setLong(2, seniorId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) throw new DomainException("该继承关系会形成角色层次循环");
            }
        }
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM role_hierarchy WHERE senior_role_id=? AND junior_role_id=?")) {
            p.setLong(1, seniorId); p.setLong(2, juniorId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) throw new DomainException("继承关系已存在");
            }
        }
    }

    private static void ensureAdministrator(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE active=1"); ResultSet r = p.executeQuery()) {
            while (r.next()) if (Access.effectivePermissions(c, r.getLong(1)).containsAll(ADMIN_CAPABILITIES)) return;
        }
        throw new Access.Denied("必须保留至少一个启用且具备完整用户、角色、授权管理能力的管理员");
    }

    private static User user(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT username,display_name,active FROM users WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new DomainException("用户不存在或已删除");
                List<String> roleNames = new ArrayList<>();
                try (PreparedStatement roles = c.prepareStatement("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.code")) {
                    roles.setLong(1, id);
                    try (ResultSet names = roles.executeQuery()) { while (names.next()) roleNames.add(names.getString(1)); }
                }
                return new User(id, r.getString(1), r.getString(2), r.getBoolean(3), List.copyOf(roleNames));
            }
        }
    }

    private static Role role(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT code,name FROM roles WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new DomainException("角色不存在或已删除"); return new Role(id, r.getString(1), r.getString(2)); }
        }
    }
    private static Set<Long> ids(Connection c, String sql, long id) throws SQLException {
        Set<Long> result = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id); try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(r.getLong(1)); }
        }
        return Set.copyOf(result);
    }
    private static long insertedId(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); return r.getLong(1); }
    }
    private static void executeId(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) { p.setLong(1, id); p.executeUpdate(); }
    }
    private static void unique(Connection c, String sql, String value, String message) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, value); try (ResultSet r = p.executeQuery()) { if (r.next()) throw new DomainException(message); }
        }
    }
    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new DomainException(label + "长度应为 1–" + max + " 个字符");
        return value.trim();
    }
    private static Set<Long> selection(Set<Long> values) {
        if (values == null || values.stream().anyMatch(v -> v == null || v <= 0)) throw new DomainException("请选择有效的授权项");
        return Set.copyOf(values);
    }
    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8 || password.length > 128) throw new DomainException("密码长度应为 8–128 个字符");
    }
}
