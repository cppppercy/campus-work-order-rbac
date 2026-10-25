package edu.campus.rbac.service;

import edu.campus.rbac.model.Models.*;
import java.util.List;
import java.util.Set;

public final class Services {
    private Services() {}
    public interface Authentication {
        Session login(String username, char[] password);
        void logout(String token);
    }
    public interface Permissions {
        Session refresh(String token);
        void require(String token, String permission);
    }
    public interface Users {
        List<User> list(String token);
        User create(String token, String username, String displayName, char[] password);
        void update(String token, long userId, String displayName, boolean active);
        void resetPassword(String token, long userId, char[] password);
        void delete(String token, long userId);
        Set<Long> roleIds(String token, long userId);
        void setRoles(String token, long userId, Set<Long> roleIds);
    }
    public interface Roles {
        List<Role> list(String token);
        Role create(String token, String code, String name);
        void update(String token, long roleId, String name);
        void delete(String token, long roleId);
        List<Permission> permissions(String token);
        Set<Long> permissionIds(String token, long roleId);
        List<RolePermissionGrant> effectivePermissions(String token, long roleId);
        void setPermissions(String token, long roleId, Set<Long> permissionIds);
        List<Role> juniorRoles(String token, long roleId);
        void addInheritance(String token, long seniorRoleId, long juniorRoleId);
        void removeInheritance(String token, long seniorRoleId, long juniorRoleId);
        void setJuniorRoles(String token, long seniorRoleId, Set<Long> juniorRoleIds);
    }
    public interface WorkOrders {
        List<WorkOrder> list(String token, String query, String status);
        WorkOrder create(String token, WorkOrderInput input);
        void update(String token, long id, WorkOrderInput input);
        void delete(String token, long id);
        List<User> assignees(String token);
        void assign(String token, long id, Long assigneeId);
        void changeStatus(String token, long id, String status);
        String exportCsv(String token, String query, String status);
    }
    public interface Reports { Statistics statistics(String token); }
    public interface Audit { List<AuditEntry> list(String token, String query); }
    public record Bundle(Authentication authentication, Permissions permissions, Users users, Roles roles,
                         WorkOrders workOrders, Reports reports, Audit audit) {}
}
