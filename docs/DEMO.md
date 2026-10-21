# 课堂演示手册

## 准备

1. 执行 `.\mvnw.cmd clean verify`，确认 BUILD SUCCESS。
2. 执行 `.\run.cmd`。首次启动会创建 `data/campus-rbac.db`。
3. 四个账号初始密码均为 `Demo@12345`：admin、manager、staff、viewer。

为保证动态授权演示可重复，开始前可备份数据库。不要在程序运行期间直接修改数据库文件。

## 建议讲解顺序（约 8–10 分钟）

1. **VIEWER 登录**：只有工作台和工单菜单；进入工单页，查询可用，其余按钮禁用。说明菜单/页面/按钮只是界面呈现。
2. **STAFF 登录**：新增一条“课堂演示工单”，再编辑标题或修改状态；删除、分派和导出仍禁用。
3. **MANAGER 登录**：选择该工单，分派给 staff；打开统计页；展示 manager 看不到用户、角色和审计菜单。
4. **ADMIN 登录**：展示全部菜单、用户表、角色表、审计日志，以及前面真实产生的数据和记录。
5. **动态授权**：角色与权限 → 选中 STAFF → 配置权限 → 勾选 `workorder:delete` → 确定。不要修改 `ADMIN` 的管理权限。
6. **即时生效**：用 staff 登录或在已有 staff 会话点击“刷新权限”，删除按钮变为可用；创建临时工单并删除。
7. **撤销**：admin 取消 STAFF 的 `workorder:delete`。staff 刷新后按钮再次禁用。即使不刷新旧界面，直接 Service 调用也会被拒绝，自动测试 TC12/TC13 提供证据。
8. **审计**：admin 打开审计页，查看登录、授权成功、工单操作和 DENIED 记录。
9. **数据库证据**：用数据库工具执行 README 中的授权联表查询，指出角色授权保存在 `role_permissions`，用户角色保存在 `user_roles`。

## 页面、菜单和服务权限讲法

以删除工单为例：`menu:workorders` 决定左侧入口，`page:workorders:view` 决定能否导航，`workorder:delete` 决定按钮与真实删除操作。隐藏入口或禁用按钮不能代替服务检查，所以 `BusinessServices.delete` 仍经过 `Access` 查询数据库。

## 查看数据库

数据库文件：`data/campus-rbac.db`。使用 DB Browser for SQLite 打开后，依次展示：

```sql
SELECT * FROM users;
SELECT * FROM roles;
SELECT * FROM permissions;
SELECT * FROM user_roles;
SELECT * FROM role_permissions;
SELECT * FROM work_orders;
SELECT * FROM audit_logs ORDER BY id DESC;
```

密码列只会看到形如 `pbkdf2-sha256$600000$...` 的摘要。关闭数据库编辑器中的写事务后再回到程序操作。

## 管理员保护演示

尝试停用唯一的 admin 或移除 ADMIN 角色的 `permission:grant`，系统会提示必须保留有效管理员且事务回滚。需要展示“删除原管理员可行”时，应先创建备用用户并授予具备完整管理能力的角色。

## 演示结束

退出所有账号并关闭程序。若需要恢复纯净数据，先保留课堂结果截图，再退出程序并删除 `data/campus-rbac.db`；下一次启动会重新生成教学数据。
