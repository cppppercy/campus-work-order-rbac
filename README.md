# 校园工单 RBAC 权限管理系统

《系统设计与分析》第三次课程作业。项目使用 Java 17、Swing、SQLite JDBC、Maven 和 JUnit 5，实现基于事件监听机制的校园工单 RBAC0/RBAC1 权限管理。

## 已实现

- RBAC0：用户-角色-权限多对多关系、可信会话、数据库持久化和默认拒绝；
- RBAC1：`senior -> junior` 角色层次、递归权限继承、来源显示、环拒绝、级联清理与版本 2 迁移；
- 菜单、页面、按钮、Service 四层控制，其中 Service 按 SQLite 当前授权做最终判断；
- 工单、用户、角色、权限、统计、审计，以及 `ActionListener` + `SwingWorker` 事件链路；
- 密码散列、事务审计、动态授权/撤权和最后有效管理员保护。

## 环境与构建

仅需 JDK 17。项目包含 Maven Wrapper；首次构建会下载 Maven 和依赖。

```powershell
.\mvnw.cmd clean verify
.\run.cmd
# 或直接启动已打包程序
java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar
```

在有图形桌面的环境执行真实 Swing 测试：

```powershell
.\mvnw.cmd -Pgui-tests test
```

## 演示账号

初始密码均为 `Demo@12345`。

| 用户 | 初始角色 | 主要效果 |
|---|---|---|
| `admin` | ADMIN | 管理用户、角色、权限与角色层次 |
| `manager` | MANAGER | 分派、导出、统计；不能删除，也不能向 ADMIN 分派 |
| `staff` | STAFF | 查看、新增、编辑和改状态 |
| `viewer` | VIEWER | 只读查询 |

## 数据库与演示

默认数据库是 `data/campus-rbac.db`。首次启动自动创建版本 2 模式和教学数据；再次启动不会重置已修改的用户、工单、直接授权或角色层次。可为课堂演示指定独立数据库：

```powershell
java -jar target\campus-work-order-rbac.jar --db D:\demo\campus-demo.db
```

需要恢复初始教学数据时，先退出程序并备份已有数据库，再删除指定数据库文件后重新启动。删除数据库会丢失其中已有工单和授权变更。

## 文档

- [课程报告](docs/COURSE_REPORT.md) 与 [PDF](docs/COURSE_REPORT.pdf)
- [RBAC 模型分析](docs/RBAC_ANALYSIS.md)
- [系统设计](docs/DESIGN.md)
- [需求映射](docs/REQUIREMENTS.md)
- [测试报告](docs/TEST_REPORT.md)
- [课堂演示步骤](docs/DEMO.md)