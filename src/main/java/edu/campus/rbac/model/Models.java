package edu.campus.rbac.model;

import java.util.List;
import java.util.Set;

public final class Models {
    private Models() {}
    public record User(long id, String username, String displayName, boolean active, List<String> roles) {}
    public record Role(long id, String code, String name) {}
    public record Permission(long id, String code, String name, String type) {}
    public record Session(String token, User user, Set<String> permissions) {}
    public record WorkOrder(long id, String title, String content, long creatorId, String creatorName,
                            Long assigneeId, String assigneeName, String status, String createdAt, String updatedAt) {}
    public record WorkOrderInput(String title, String content) {}
    public record AuditEntry(long id, String createdAt, Long actorId, String actorName,
                             String action, String target, String result, String detail) {}
    public record Statistics(long total, java.util.Map<String, Long> byStatus) {}
}
