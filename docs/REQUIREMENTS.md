# 作业要求映射

| 要求 | 实现位置与验证 |
|---|---|
| RBAC 用户、角色、权限及多对多关系 | `schema.sql` 的五张 RBAC 表；`SecurityServices`；DatabaseTest |
| 授权关系来自数据库 | `Access.effectivePermissions` 每次查询关联表；AcceptanceTest TC10–TC15 |
| Java Swing 事件监听 | `CampusApplication` 的显式 `ActionListener`；`BackgroundAction`；GuiEventTest |
| 不同角色菜单和同页按钮不同 | `CampusApplication.allowed/action/nav`；GuiTest、GuiDatabaseTest 和真实截图 |
| 防止绕过 UI 越权 | `Access.read/write`；AcceptanceTest TC13、SecurityTest |
| 动态授权与撤销 | 角色权限复选框；刷新权限；旧会话即时撤权测试 |
| 用户和角色管理 | `SecurityServices.UsersImpl/RolesImpl` 及两个管理页面 |
| 工单真实 CRUD | `BusinessServices`、`WorkOrderRepository`；AcceptanceTest |
| 分派、状态、筛选、统计、导出 | 工单和统计 Service/UI；CSV 转义测试 |
| SQLite 外键、约束、参数化 SQL | `schema.sql`、DAO 和 Service；DatabaseTest |
| 初始化幂等且数据持久 | `schema_version`；DatabaseTest、AcceptanceTest TC15/TC19 |
| 密码安全摘要 | `Passwords`；DatabaseTest |
| 多表授权事务 | `Database.write` + `Access`；SecurityTest 原子回滚用例 |
| 最后管理员保护 | `ensureAdministrator`；SecurityTest 覆盖停用、删除、撤角色、撤权限、删角色 |
| 审计成功与拒绝 | `audit_logs`、`Access.audit/record`、审计页；TC18 |
| EDT 线程规则 | `BackgroundAction`；GuiEventTest 验证后台执行与回调线程 |
| RBAC0–RBAC3 分析 | `docs/RBAC_ANALYSIS.md` |
| 可运行 JAR 与说明 | Shade 插件、`run.cmd/run.sh`、README；JAR `--init-only` 启动验证 |
| 自动化测试 TC01–TC20 | DatabaseTest、AcceptanceTest、SecurityTest、GuiEventTest、GUI profile；`docs/TEST_REPORT.md` |
| 中文报告与演示步骤 | DESIGN、RBAC_ANALYSIS、COURSE_REPORT、DEMO、TEST_REPORT |

课程要求的所有核心项均有实现与证据。GUI 的授权复选框弹窗、用户编辑弹窗等模态交互由课堂演示手动操作；自动 GUI 验收覆盖真实登录、菜单差异、工单按钮差异、页面加载和截图。
