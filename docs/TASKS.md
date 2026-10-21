# 模块契约与任务分工

包根：`edu.campus.rbac`。共享模型位于 `model.Models`，服务接口位于 `service.Services`。

数据库模块：Database、Passwords、schema.sql、seed.sql、数据库测试（完成）。
权限模块：Access、SessionRegistry、SecurityServices、权限测试（完成）。
GUI 模块：ui 和 event 包、GUI 测试（完成）。
集成模块：工单、统计、审计服务；共享契约；构建；文档；GitHub 同步（完成）。

Database(Path).initialize() 初始化；read/write 接收可抛 SQLException 的连接函数。write 使用 BEGIN IMMEDIATE，正常 COMMIT，异常 ROLLBACK。每连接外键开启，busy_timeout=5000。
Passwords.hash(char[]) 返回含算法/迭代/盐/哈希的字符串；verify(char[], String) 验证。
Access(Database, SessionRegistry) 提供 read/write(token, action, target, Set<String> required, AuthorizedWork<T>)；work 接收 Connection 和真实 actorId。所有授权在同连接进行，拒绝独立审计。
SecurityServices(Database) 提供 authentication(), permissions(), users(), roles(), access()。主代理通过相同 Access 构造业务服务。

模型与 Services 接口是唯一公共契约。数据库列固定为 snake_case，对外记录采用 camelCase。时间保存 ISO-8601 UTC 文本。工单状态：待处理、处理中、已完成、已关闭。
权限：menu:workorders、page:workorders:view、workorder:list/create/update/delete/assign/export；menu:reports、page:reports:view、report:view；menu:users、page:users:view、user:list、user:manage；menu:roles、page:roles:view、role:list、role:manage、permission:grant；menu:audit、page:audit:view、audit:view。
