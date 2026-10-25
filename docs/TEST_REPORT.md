# 测试报告

## 本次实际执行

| 命令 | 结果 | 证据 |
|---|---|---|
| `.\mvnw.cmd clean verify` | 通过 | 42 项非 GUI 测试，0 失败、0 错误、0 跳过；生成 shaded JAR |
| `.\mvnw.cmd -Pgui-tests test` | 通过 | 3 项真实 Swing GUI 测试，0 失败、0 错误、0 跳过 |
| `java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar --db <临时库> --init-only` | 通过 | JAR 成功建立新的 SQLite 数据库 |

`clean verify` 的 42 项测试包括：AcceptanceTest 14、DatabaseTest 6、GuiEventTest 3、RoleHierarchyTest 7、SecurityTest 12。GUI profile 单独执行 GuiDatabaseTest 2 和 GuiTest 1；它们在图形环境中实际打开窗口、测试模态框并生成截图。

## RBAC1 覆盖

`RoleHierarchyTest` 使用真实 SQLite 和生产 Service，验证：

- 初始层次为空，RBAC0 权限不改变；
- 单向、多层继承和权限并集/去重；
- 直接、继承、直接加继承三类权限来源；
- 继承增加和移除对既有会话立即生效；
- 直接授权不随继承移除而丢失；
- 自继承、环、缺失角色和无权调用被拒绝；
- 删除角色自动清理层次关系，写操作留下审计；
- 不能移除最后一个有效管理员的继承来源；
- 版本 1 数据库迁移到版本 2 后保留工单和已撤销权限，版本 2 重启幂等。

原有验收、安全、数据库、事件测试也全部通过，覆盖工单越权、伪造与注销会话、实时撤权、并发撤权、审计、密码散列和管理员保护。

## GUI 检查和截图

GUI 测试验证角色页按 MENU/PAGE/ACTION 显示有效权限，权限配置和新增工单、删除确认、继承配置等模态框可正常打开及取消/确认。实际生成截图：

- `docs/images/admin-roles.png`
- `docs/images/admin-role-hierarchy.png`
- `docs/images/admin-permissions.png`
- `docs/images/viewer-workorders.png`
- `docs/images/staff-workorders-granted.png`
- `docs/images/staff-workorders.png`

测试没有把 GUI 显示结果当作安全边界；所有授权结果仍由 Service 与 SQLite 事务测试验证。

## 已知限制

自动化 GUI 测试依赖有图形桌面；本次在当前环境实际执行成功。课堂演示前仍建议人工确认屏幕缩放、投影字体和本地数据库路径。