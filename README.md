# 校园工单 RBAC 权限管理系统

《系统设计与分析》第三次课程作业。项目使用 Java 17、Swing、SQLite JDBC、Maven 和 JUnit 5，实现基于监听器事件机制的校园工单权限管理。

## 已实现

- RBAC0：用户—角色—权限多对多关系、可信会话和数据库持久化。
- RBAC1：角色层次 `senior → junior`、递归权限继承、来源显示、环拒绝和角色删除级联清理。
- 菜单、页面、按钮和 Service 四层权限控制；Service 每次按 SQLite 当前关系授权。
- 工单新增、编辑、删除、分派、状态、搜索、统计和 CSV 导出。
- PBKDF2-HMAC-SHA256 密码散列、事务审计和最后有效管理员保护。
- Swing `ActionListener` + `SwingWorker`，避免在 EDT 执行数据库访问。

## 启动

```powershell
.\mvnw.cmd clean verify
.\run.cmd
# 或
java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar
```

有图形桌面时，可运行真实 Swing 自动化测试并更新截图：

```powershell
.\mvnw.cmd -Pgui-tests test
```

默认数据库为 `data/campus-rbac.db`；可通过 `--db D:\demo\campus.db` 指定路径。首次启动建立版本 2 模式和演示数据；重启不会恢复已撤销权限，也不会创建默认角色层次。

## 演示账号

初始密码均为 `Demo@12345`。

| 用户 | 角色 | 说明 |
|---|---|---|
| `admin` | ADMIN | 角色、权限和层次管理 |
| `manager` | MANAGER | 工单分派、导出、统计；不能向 ADMIN 分派 |
| `staff` | STAFF | 工单查看、新增、编辑、改状态 |
| `viewer` | VIEWER | 只读查询 |

## 文档入口

- [课程报告](docs/COURSE_REPORT.md)
- [RBAC 模型分析](docs/RBAC_ANALYSIS.md)
- [系统设计](docs/DESIGN.md)
- [演示步骤](docs/DEMO.md)
- [测试报告](docs/TEST_REPORT.md)

## 角色层次示例

在“角色与权限”选择高级角色，点击“配置继承”，勾选其直属下级角色。例如设置 `MANAGER → STAFF` 后，MANAGER 将取得 STAFF 的有效权限。关系变更保存到 SQLite；移除关系后，同一会话的下一次 Service 调用立即按新关系授权。