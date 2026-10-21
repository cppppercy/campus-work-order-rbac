package edu.campus.rbac.repository;

import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.DomainException;
import java.sql.*;
import java.util.*;

/** SQL 访问只接受服务事务的连接，不自行提交。 */
public final class WorkOrderRepository {
    private static final String SELECT = "SELECT w.*,c.display_name creator_name,a.display_name assignee_name FROM work_orders w LEFT JOIN users c ON c.id=w.creator_id LEFT JOIN users a ON a.id=w.assignee_id";
    public List<WorkOrder> list(Connection c, String query, String status) throws SQLException {
        String sql=SELECT+" WHERE (?='' OR instr(lower(w.title),lower(?))>0 OR instr(lower(w.content),lower(?))>0 OR CAST(w.id AS TEXT)=?) AND (?='' OR w.status=?) ORDER BY w.id DESC";
        try (PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=1;i<=4;i++) p.setString(i,query);
            p.setString(5,status);p.setString(6,status);
            try(ResultSet r=p.executeQuery()){List<WorkOrder> items=new ArrayList<>();while(r.next())items.add(map(r));return List.copyOf(items);}
        }
    }
    public WorkOrder get(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(SELECT+" WHERE w.id=?")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new DomainException("工单不存在或已删除");return map(r);}
        }
    }
    public long create(Connection c,long actor,WorkOrderInput input,String now) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO work_orders(title,content,creator_id,status,created_at,updated_at) VALUES(?,?,?,'待处理',?,?)")){
            p.setString(1,input.title().trim());p.setString(2,input.content().trim());p.setLong(3,actor);p.setString(4,now);p.setString(5,now);p.executeUpdate();
        }
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT last_insert_rowid()")){r.next();return r.getLong(1);}
    }
    private WorkOrder map(ResultSet r)throws SQLException{
        long assignee=r.getLong("assignee_id");Long assigneeId=r.wasNull()?null:assignee;
        return new WorkOrder(r.getLong("id"),r.getString("title"),r.getString("content"),r.getLong("creator_id"),Objects.toString(r.getString("creator_name"),"已删除用户"),assigneeId,Objects.toString(r.getString("assignee_name"),"未分派"),r.getString("status"),r.getString("created_at"),r.getString("updated_at"));
    }
}
