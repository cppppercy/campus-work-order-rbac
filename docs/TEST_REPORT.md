# 测试报告

## 实际执行结果

| 命令 | 结果 | 说明 |
|---|---|---|
| `.\mvnw.cmd clean verify` | 通过 | 42 项非 GUI 测试，0 失败、0 错误、0 跳过；生成 shaded JAR |
| `.\mvnw.cmd -Pgui-tests test` | 通过 | 3 项真实 Swing GUI 测试，0 失败、0 错误、0 跳过 |
| `java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar --db <临时库> --init-only` | 通过 | JAR 成功初始化独立 SQLite 数据库 |

非 GUI 的 42 项测试由 AcceptanceTest 14、DatabaseTest 6、GuiEventTest 3、RoleHierarchyTest 7 和 SecurityTest 12 构成。GUI profile 单独运行 GuiDatabaseTest 2 与 GuiTest 1；它们在当前图形环境实际打开 Swing 窗口，验证角色页面、权限配置、继承配置、新增工单和删除确认等交互，并生成 `docs/images/` 中的截图。

## 关键覆盖

- 初始化、外键与唯一约束、密码散列、事务回滚、数据库重启持久化；
- 伪造/注销会话、停用用户、Service 越权、成功与拒绝审计、并发撤权；
- RBAC0 多角色并集、动态授予与撤销、普通员工和只读用户的按钮差异；
- RBAC1 空层次基线、单向和多级继承、权限并集与来源去重、取消继承、直接授权保留、循环拒绝、删除角色级联清理、最后有效管理员保护；
- 版本 1 到版本 2 迁移保留已撤销授权和业务数据，版本 2 初始化幂等。

## GitHub Actions

公开仓库的 `Java 17 verification` 最近一次可查询运行是第 9 次：2026-10-25 触发，针对合并后的 RBAC1 代码，状态为 `completed / success`。该远端结果用于补充说明；本报告中的测试结论仍以本地实际执行的命令和报告为准。

## GUI 与人工展示边界

本次 GUI profile 在当前环境已实际通过。课程展示前仍建议人工确认投影缩放、系统字体与演示数据库路径。自动化 GUI 测试不替代 Service 层安全验证；最终授权依然由真实 SQLite 事务和 Service 测试保证。