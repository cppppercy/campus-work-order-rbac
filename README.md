# 基于 RBAC 的校园工单管理系统

《系统设计与分析》第三次课程作业。项目使用 Java 17、Swing、SQLite 和事件监听机制，实现可运行的校园工单桌面程序与可复用 RBAC0 权限模块。

[![Java 17 verification](https://github.com/cppppercy/campus-work-order-rbac/actions/workflows/build.yml/badge.svg)](https://github.com/cppppercy/campus-work-order-rbac/actions/workflows/build.yml)

## 功能

- 登录、退出和刷新权限；密码以 PBKDF2-HMAC-SHA256、独立随机盐和 600,000 次迭代保存。
- 用户、角色、用户角色和角色权限的可视化管理，授权关系全部来自 SQLite。
- 工单新增、编辑、删除、分派、状态流转、搜索、筛选、统计和 CSV 导出。
- 菜单、页面、按钮三级界面呈现；服务层再次读取当前数据库权限，不能通过直接调用 Service 越权。
- 成功写操作、登录、权限查询和授权拒绝记录审计日志。
- 防止停用、删除或撤权导致系统失去最后一个有效管理员。
- Swing `ActionListener` + `SwingWorker`，数据库操作不阻塞 EDT。

## 运行环境与启动

只需安装 JDK 17。项目包含 Maven Wrapper，首次构建需要联网下载 Maven 和依赖，后续可离线使用缓存。

```powershell
# Windows：构建、运行 35 项非 GUI 自动化测试并生成可执行 JAR
.\mvnw.cmd clean verify

# 启动图形程序
.\run.cmd

# 或直接启动 JAR
java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar
```

Linux/macOS 使用 `./mvnw clean verify` 和 `./run.sh`。指定数据库文件可使用：

```powershell
java -jar target\campus-work-order-rbac.jar --db D:\demo\campus.db
```

默认数据库为 `data/campus-rbac.db`，首次运行自动建表并填充教学数据；再次运行不会覆盖工单、用户或管理员修改后的授权。仅验证数据库初始化可执行：

```powershell
java -jar target\campus-work-order-rbac.jar --db data\check.db --init-only
```

## 演示账号

以下账号仅用于本地课堂演示，初始密码统一为 `Demo@12345`。

| 用户名 | 初始角色 | 主要效果 |
|---|---|---|
| `admin` | ADMIN | 全部菜单、业务操作和 RBAC 管理 |
| `manager` | MANAGER | 工单新增、编辑、分派、导出和统计 |
| `staff` | STAFF | 工单查看、新增和编辑 |
| `viewer` | VIEWER | 只能查看工单 |

数据库中不存在明文演示密码。公开仓库中的凭据只能用于本地教学数据，不能用于生产系统。

## 界面截图

截图由 `GuiDatabaseTest` 使用真实 SQLite 和真实服务层生成，并逐页检查。

管理员工单页：

![管理员工单页](docs/images/admin-workorders.png)

只读用户进入同一页面时，新增、编辑、删除、分派、修改状态和导出按钮全部禁用：

![只读用户工单页](docs/images/viewer-workorders.png)

角色管理页：

![角色管理页](docs/images/admin-roles.png)

## 项目结构

```text
src/main/java/edu/campus/rbac/
  ui/             Swing 页面与权限呈现
  event/          ActionListener/SwingWorker 事件适配
  service/        认证、RBAC、工单、统计和审计服务
  repository/     参数化 SQL 数据访问
  infrastructure/ SQLite 事务、可信会话、密码与统一授权
  model/           领域记录类型
src/main/resources/ schema.sql、seed.sql
src/test/           真实 SQLite 验收、权限和 GUI 测试
docs/               需求、设计、分析、演示和测试报告
```

## 测试

```powershell
# 默认：数据库、权限、业务、审计和事件后台线程测试
.\mvnw.cmd clean verify

# 有图形桌面的机器：真实 Swing 窗口与角色界面测试
.\mvnw.cmd -Pgui-tests test
```

教师阅读入口见 [课程报告](docs/COURSE_REPORT.md)，最后一次本地验收结果见 [测试报告](docs/TEST_REPORT.md)。详细设计见 [系统设计](docs/DESIGN.md)，课堂操作顺序见 [演示手册](docs/DEMO.md)。

## 查看数据库授权

可用 DB Browser for SQLite 或 IntelliJ Database 打开 `data/campus-rbac.db`。以下查询展示用户的有效权限并集：

```sql
SELECT u.username, r.code AS role_code, p.type, p.code AS permission_code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
ORDER BY u.username, p.type, p.code;
```

## 常见问题

- 首次构建失败：确认 `java -version` 为 17，并允许 Maven Wrapper 下载依赖。
- 中文乱码：使用 `run.cmd`，或为 Java 添加 `-Dfile.encoding=UTF-8`。
- 希望恢复初始演示数据：退出程序后备份并删除 `data/campus-rbac.db`，再启动程序。删除数据库会丢失已有工单和授权。
- 授权修改后按钮没有变化：点击“刷新权限”；即使未刷新，服务层也会立即按数据库新权限拒绝旧按钮发出的请求。
- SQLite 报忙：关闭其他数据库编辑器的写事务后重试；应用设置了 5 秒 `busy_timeout`。
