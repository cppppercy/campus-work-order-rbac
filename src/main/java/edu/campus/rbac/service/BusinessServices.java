package edu.campus.rbac.service;

import edu.campus.rbac.infrastructure.Access;
import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.repository.WorkOrderRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** 工单、统计、审计的服务入口，每次调用均读取数据库授权。 */
public final class BusinessServices implements Services.WorkOrders, Services.Reports, Services.Audit {
    public static final List<String> STATUSES=List.of("待处理","处理中","已完成","已关闭");
    private final Access access;
    private final WorkOrderRepository repository=new WorkOrderRepository();
    public BusinessServices(Access access){this.access=access;}
    @Override public List<WorkOrder> list(String token,String query,String status){
        return access.read(token,"workorder:list","work_orders",Set.of("workorder:list"),(c,actor)->repository.list(c,clean(query),filterStatus(status)));
    }
    @Override public WorkOrder create(String token,WorkOrderInput input){
        return access.write(token,"workorder:create","work_orders",Set.of("workorder:create"),(c,actor)->{
            validate(input);long id=repository.create(c,actor,input,Instant.now().toString());return repository.get(c,id);
        });
    }
    @Override public void update(String token,long id,WorkOrderInput input){
        access.write(token,"workorder:update",Long.toString(id),Set.of("workorder:update"),(c,actor)->{
            validate(input);try(PreparedStatement p=c.prepareStatement("UPDATE work_orders SET title=?,content=?,updated_at=? WHERE id=?")){
                p.setString(1,input.title().trim());p.setString(2,input.content().trim());p.setString(3,Instant.now().toString());p.setLong(4,id);changed(p.executeUpdate());}return null;
        });
    }
    @Override public void delete(String token,long id){
        access.write(token,"workorder:delete",Long.toString(id),Set.of("workorder:delete"),(c,actor)->{
            try(PreparedStatement p=c.prepareStatement("DELETE FROM work_orders WHERE id=?")){p.setLong(1,id);changed(p.executeUpdate());}return null;
        });
    }
    @Override public List<User> assignees(String token){
        return access.read(token,"workorder:assignees","users",Set.of("workorder:assign"),(c,actor)->{
            boolean canAssignSystemManagers=hasSystemManagement(c,actor);
            List<User> result=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT id,username,display_name,active FROM users WHERE active=1 ORDER BY id");ResultSet r=p.executeQuery()){
                while(r.next()){
                    long candidate=r.getLong(1);
                    if(canAssignSystemManagers||!hasSystemManagement(c,candidate))
                        result.add(new User(candidate,r.getString(2),r.getString(3),true,List.of()));
                }}return List.copyOf(result);
        });
    }
    @Override public void assign(String token,long id,Long assigneeId){
        access.write(token,"workorder:assign",Long.toString(id),Set.of("workorder:assign"),(c,actor)->{
            if(assigneeId!=null)try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE id=? AND active=1")){p.setLong(1,assigneeId);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new DomainException("负责人不存在或已停用");
                if(!hasSystemManagement(c,actor)&&hasSystemManagement(c,assigneeId))
                    throw new DomainException("没有权限向系统管理员分派工单");
            }}
            try(PreparedStatement p=c.prepareStatement("UPDATE work_orders SET assignee_id=?,updated_at=? WHERE id=?")){
                if(assigneeId==null)p.setNull(1,Types.INTEGER);else p.setLong(1,assigneeId);p.setString(2,Instant.now().toString());p.setLong(3,id);changed(p.executeUpdate());}return null;
        });
    }
    /** 依据实际授权识别系统管理能力，不依赖 ADMIN 角色名称。 */
    private static boolean hasSystemManagement(Connection connection,long userId)throws SQLException{
        return Access.effectivePermissions(connection,userId).contains("permission:grant");
    }
    @Override public void changeStatus(String token,long id,String status){
        access.write(token,"workorder:status",Long.toString(id),Set.of("workorder:update"),(c,actor)->{
            if(!STATUSES.contains(status))throw new DomainException("请选择有效工单状态");
            try(PreparedStatement p=c.prepareStatement("UPDATE work_orders SET status=?,updated_at=? WHERE id=?")){p.setString(1,status);p.setString(2,Instant.now().toString());p.setLong(3,id);changed(p.executeUpdate());}return null;
        });
    }
    @Override public String exportCsv(String token,String query,String status){
        return access.read(token,"workorder:export","work_orders",Set.of("workorder:list","workorder:export"),(c,actor)->{
            StringBuilder out=new StringBuilder("编号,标题,内容,创建人,负责人,状态,创建时间,更新时间\r\n");
            for(WorkOrder w:repository.list(c,clean(query),filterStatus(status))){
                out.append(w.id());for(String v:List.of(w.title(),w.content(),w.creatorName(),w.assigneeName(),w.status(),w.createdAt(),w.updatedAt()))out.append(',').append(csv(v));out.append("\r\n");
            }return out.toString();
        });
    }
    @Override public Statistics statistics(String token){
        return access.read(token,"report:view","work_orders",Set.of("report:view"),(c,actor)->{
            Map<String,Long> counts=new LinkedHashMap<>();STATUSES.forEach(s->counts.put(s,0L));long total=0;
            try(PreparedStatement p=c.prepareStatement("SELECT status,COUNT(*) FROM work_orders GROUP BY status");ResultSet r=p.executeQuery()){
                while(r.next()){long n=r.getLong(2);counts.put(r.getString(1),n);total+=n;}}
            return new Statistics(total,Collections.unmodifiableMap(counts));
        });
    }
    @Override public List<AuditEntry> list(String token,String query){
        return access.read(token,"audit:view","audit_logs",Set.of("audit:view"),(c,actor)->{
            List<AuditEntry> result=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT a.*,u.username actor_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id WHERE ?='' OR instr(lower(a.action||' '||a.result||' '||a.detail||' '||COALESCE(u.username,'')),lower(?))>0 ORDER BY a.id DESC LIMIT 1000")){
                p.setString(1,clean(query));p.setString(2,clean(query));try(ResultSet r=p.executeQuery()){while(r.next()){
                    long actorId=r.getLong("actor_id");Long nullable=r.wasNull()?null:actorId;
                    result.add(new AuditEntry(r.getLong("id"),r.getString("created_at"),nullable,Objects.toString(r.getString("actor_name"),"匿名/已删除"),r.getString("action"),r.getString("target"),r.getString("result"),r.getString("detail")));}}}
            return List.copyOf(result);
        });
    }
    private static String clean(String text){return text==null?"":text.trim();}
    private static String filterStatus(String status){String s=clean(status);if(!s.isEmpty()&&!STATUSES.contains(s))throw new DomainException("无效状态筛选");return s;}
    private static void validate(WorkOrderInput input){if(input==null||input.title()==null||input.title().isBlank()||input.title().trim().length()>120)throw new DomainException("标题需为 1–120 个字符");if(input.content()==null||input.content().length()>10000)throw new DomainException("内容不能超过 10000 个字符");}
    private static void changed(int n){if(n!=1)throw new DomainException("工单不存在或已删除");}
    private static String csv(String value){String v=value;String stripped=v.stripLeading();if(!stripped.isEmpty()&&"=+-@".indexOf(stripped.charAt(0))>=0)v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
}
