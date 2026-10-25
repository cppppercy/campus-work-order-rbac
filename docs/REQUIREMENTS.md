# 作业要求与实现映射

## 教师核心要求的实现

| 核心要求 | 当前实现与证据 |
|---|---|
| 完整 RBAC 应用代码 | Java 17、Swing、SQLite JDBC 源码位于 `src/`；可执行 JAR 由 Maven Shade 生成 |
| 角色与权限页面操作展示 | 登录、工单、用户、角色与权限、统计、审计页面；真实截图位于 `docs/images/` |
| 分析菜单、页面、按钮与业务层差异 | 主报告第 5 节的四层控制表；`Access.read/write` 为最终边界 |
| 授权关系数据库持久化 | `user_roles`、`role_permissions`；重启不会恢复已撤销授权 |
| 监听器与事件响应 | `CampusApplication` 的 `ActionListener` 与 `BackgroundAction`/`SwingWorker`；`GuiEventTest` 覆盖线程行为 |

## 本项目完成的 RBAC1 扩展

以下内容是本项目在基础作业之上完成的 RBAC1 扩展，不把它们表述为教师逐字规定的评分项。

- `role_hierarchy` 表和数据库版本 2 迁移；
- 高级角色到低级角色的递归权限继承、来源显示与动态撤销；
- 自继承、重复关系和循环继承拒绝；
- 角色删除时的关系级联清理和最后有效管理员保护；
- `RoleHierarchyTest` 的真实 SQLite 覆盖，以及 GUI 角色继承配置弹窗测试与截图。

## 验证范围

最近一次 `mvnw.cmd clean verify` 通过 42 项非 GUI 测试；在当前图形环境执行 `mvnw.cmd -Pgui-tests test` 通过 3 项 GUI 测试。完整命令、分类和限制见 `docs/TEST_REPORT.md`。