# 测试报告

## 测试环境

- 日期：2026-10-25（Asia/Shanghai）
- 操作系统：Windows 11 10.0 amd64
- Java：Oracle JDK 17.0.14
- Maven：项目 Wrapper 固定 Apache Maven 3.9.9
- 数据库：SQLite JDBC 3.47.2.0
- 测试框架：JUnit Jupiter 5.11.4

测试全部使用临时真实 SQLite 文件。权限用例调用正式 Service 和 SQL，没有用固定 `true/false` 模拟权限。

## 实际执行命令与结果

### 默认构建与自动化验收

```powershell
.\mvnw.cmd clean verify
```

结果：`BUILD SUCCESS`，35 项测试，0 失败、0 错误、0 跳过；生成 `target/campus-work-order-rbac.jar`。

| 测试类 | 数量 | 结果 | 覆盖重点 |
|---|---:|---|---|
| AcceptanceTest | 14 | 通过 | TC02–TC19、工单生命周期、统计、CSV、历史保留 |
| DatabaseTest | 6 | 通过 | TC01、TC15、TC19、密码、外键、事务回滚 |
| SecurityTest | 12 | 通过 | 可信会话、即时撤权、最后管理员、原子授权、拒绝审计、并发事务顺序 |
| GuiEventTest | 3 | 通过 | 后台线程、EDT 回调、过期页面回调丢弃 |

### 原生 Swing 验收

```powershell
.\mvnw.cmd -Pgui-tests test
```

结果：`BUILD SUCCESS`，3 项，0 失败、0 错误、0 跳过。其中 `GuiDatabaseTest` 的 2 项测试使用临时真实 SQLite 和正式 Service：登录 ADMIN、STAFF 与 VIEWER，检查菜单、同一工单页按钮状态、实际表数据、权限配置弹窗的取消/保存，以及删除确认弹窗的取消/确认；`GuiTest` 的 1 项测试使用可观测测试服务检查页面权限调用、刷新权限和后台线程。测试生成 8 张真实 Swing 组件截图并已逐张检查，保存在 `docs/images/`。

动态授权 GUI 流程已实际完成：ADMIN 在分组弹窗中给 STAFF 增加 `workorder:delete`，保存后数据库关联存在；STAFF 登录后删除按钮可用。随后由另一管理员会话撤销权限，STAFF 在同一会话刷新后删除按钮禁用。服务层的授予后实际删除、撤权后立即拒绝和重启持久化由 `AcceptanceTest` 使用同类真实数据库继续验证。

### 可执行 JAR 冒烟测试

```powershell
java "-Dfile.encoding=UTF-8" -jar target\campus-work-order-rbac.jar --db target\final-smoke.db --init-only
java "-Dfile.encoding=UTF-8" -jar target\campus-work-order-rbac.jar --help
```

结果：数据库初始化成功，生成 90,112 字节 SQLite 文件；帮助参数正常输出。打包 JAR 为 14,431,795 字节，包含 SQLite 驱动。

### GitHub Actions 双平台构建

最终工作流运行 [39606457493](https://github.com/cppppercy/campus-work-order-rbac/actions/runs/39606457493) 实际完成：Ubuntu 作业 47 秒、Windows 作业 1 分 7 秒，两个作业的构建、测试和产物上传均成功。

## TC01–TC20 验收映射

| 用例 | 结果 | 自动化证据 |
|---|---|---|
| TC01 首次初始化 | 通过 | DatabaseTest 建表、4 用户、4 角色、23 权限、41 授权、2 工单 |
| TC02 ADMIN 登录 | 通过 | AcceptanceTest |
| TC03 VIEWER 查看工单 | 通过 | AcceptanceTest、GuiDatabaseTest |
| TC04 VIEWER 不能新增 | 通过 | AcceptanceTest、GuiDatabaseTest 按钮禁用 |
| TC05 VIEWER 不能删除 | 通过 | AcceptanceTest |
| TC06 STAFF 新增并持久 | 通过 | AcceptanceTest 重开数据库验证 |
| TC07 STAFF 不能删除 | 通过 | AcceptanceTest |
| TC08 MANAGER 分派 | 通过 | AcceptanceTest：可分派 STAFF，负责人列表不含 ADMIN，直调 Service 分派 ADMIN 被拒绝 |
| TC09 MANAGER 不能配置权限 | 通过 | AcceptanceTest、SecurityTest |
| TC10 ADMIN 给 STAFF 增加删除权 | 通过 | AcceptanceTest、GuiDatabaseTest 权限弹窗 |
| TC11 刷新/旧会话获得新增权限 | 通过 | AcceptanceTest、GuiDatabaseTest 按钮启用 |
| TC12 撤销后 STAFF 不能删除 | 通过 | AcceptanceTest、SecurityTest、GuiDatabaseTest 按钮禁用 |
| TC13 绕过 UI 调 Service 被拒 | 通过 | AcceptanceTest |
| TC14 多角色权限并集 | 通过 | AcceptanceTest |
| TC15 重启后数据和授权保留 | 通过 | AcceptanceTest、DatabaseTest |
| TC16 非管理员不能分配角色权限 | 通过 | AcceptanceTest、SecurityTest |
| TC17 无页面权限不能导航 | 通过 | AcceptanceTest、GuiTest |
| TC18 成功和拒绝写入审计 | 通过 | AcceptanceTest、SecurityTest |
| TC19 初始化幂等 | 通过 | DatabaseTest |
| TC20 构建与自动测试 | 通过 | Wrapper `clean verify`，35/35 |

## 补充安全与边界用例

- 随机、伪造和注销令牌均被拒绝；未知令牌的审计操作人为空。
- 不存在用户和错误密码使用相同错误信息，不泄露账号状态。
- 已签发 STAFF 会话在撤权提交后的下一次调用立即失败。
- 停用、删除唯一管理员，撤销其角色或关键能力、删除关键角色均回滚。
- 创建第二个有效管理员后，可以删除原管理员；原会话立即失效。
- 用户角色批量替换遇到不存在角色时整笔回滚。
- 双连接并发测试证明：撤权事务等待已经开始的写操作提交；撤权提交后的下一次操作被拒绝。
- CSV 对引号、换行和公式起始字符转义，降低表格公式注入风险。
- 删除用户后工单与审计历史保留，外键改为 NULL。

## 失败与修复记录

- 初次下载 Maven/GitHub CLI 时，沙箱内 Windows TLS 凭据不可用；经授权在沙箱外下载官方便携包后解决。便携工具位于被忽略的 `.tools/`，未提交。
- 初次截图在数据异步加载完成前生成，表格为空；GUI 测试增加等待真实行数的条件后重跑，最终截图包含工单和角色数据。
- 本轮首次截取删除确认弹窗时沿用了主窗口 10 KB 的图片大小阈值，导致正常的小型弹窗截图被误判；测试按窗口类型设置合理阈值后通过。
- GUI 测试关闭临时数据库时发现异步注销审计与临时目录清理存在竞态；测试改为等待注销服务完成，最终连续执行通过。
- 首次 GitHub Actions 使用 `bash mvnw`，Wrapper 将脚本名误判为目录并在 Ubuntu 失败；工作流改为从仓库根执行 `./mvnw`，并升级 checkout/setup-java action 后重新验证。
- Maven Shade 报告依赖 JAR 中许可证与 Manifest 元数据重叠；可执行 JAR、驱动加载和初始化实测正常，该警告不影响运行。

## 尚未自动化的内容

自动 GUI 测试已覆盖本次重点模态框：新增工单表单、角色权限配置、删除确认及其取消/确认分支。尚未逐个点击用户/角色的全部编辑弹窗，也未自动操作系统文件选择器；相关 Service 与数据库行为由非 GUI 验收测试覆盖，课堂展示仍应按 `DEMO.md` 人工复核窗口缩放、投影清晰度和 CSV 保存位置。
