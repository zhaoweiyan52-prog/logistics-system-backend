# 物流管理系统：Spring Boot + MySQL 后端

这是一个 Spring Boot + MySQL 项目，现已接通组员页面约定的全部 21 个接口：客户寄件、订单、签收反馈；员工任务、凭证、库存；管理员用户角色、统计报表。另增加用户启停、系统设置的 3 个接口，将原页面仅在浏览器修改的操作真正保存到数据库。原有路径和字段保留。

**验证状态：H2 37 项测试通过（含云端代理回归）；本机 MySQL 8.0.46 原 35 项业务测试与增量升级检查通过，前端 17 项回归通过。Render Docker + Aiven MySQL 已上线，23 项公网接口检查通过；浏览器登录 403 修复及云端管理员验收进展见 [云端验证记录](docs/云端部署验证记录.md)。**

**已有第一阶段数据库必须先执行增量升级，再启动新版。管理员首次启用、24 个接口、权限和业务边界见 [全功能对接与升级](docs/全功能对接与升级.md)。**

本机 MySQL 使用 `F:\MYSQL\Server` 程序目录、`F:\MYSQL\Data` 数据目录和 `MySQL80Logistics` 服务。已有第一阶段四张表，在 Workbench 中选择 logistics 后执行 **`sql/03-full-features.sql`**，增加九张表并保留旧数据；不要重新初始化数据目录。全新数据库执行 01、02 即可。

升级、打包并启动后，打开 `http://localhost:8080/logistics-admin.html` 即可访问。可在项目目录执行下面命令启动；脚本读取 F 盘由 Windows 用户加密保护的项目账号凭证，不在仓库保存密码：

```powershell
& .\scripts\start-mysql-backend.ps1
```

下面的手动建库步骤供其他电脑首次配置参考。

**先阅读 `docs/接口核对与待确认问题.md`。本机验证的结果和局限见 `docs/验证记录.md`。**

免费公网部署见 [Render + Aiven MySQL说明](docs/免费部署-Render与Aiven.md)。Docker、Free套餐及cloud配置已准备；云端账号、数据库凭证和证书配置完成后才能创建并验证公网网址。

## 1. 从你已经学过的 Java、MySQL 理解代码

Spring Boot 帮你启动 Web 服务器、接收 HTTP 请求、把 JSON 转成 Java 对象、连接数据库。你不需要自己写 Socket 或配置外部 Tomcat。

一次下单走这条链路：

```text
浏览器 POST /api/orders
  → ApiAuthFilter：用 token 找到真实登录用户
  → OrderController：接收并校验 receiver/dest/goods/weight/fee
  → OrderService：生成运单号，把用户编号作为 owner_id
  → JdbcTemplate：执行带占位符的 SQL，写订单及初始轨迹
  → ApiResponse：返回 {code:0,data:订单,message:"成功"}
```

- `@RestController`：这个 Java 类负责 HTTP 接口，返回对象自动转 JSON。
- `@RequestBody`：把请求 JSON 转成方法参数。
- `@Valid`、`@NotBlank` 等：检查字段是否缺失、超长、金额为负等。
- `@Service`：放业务逻辑，不把业务全塞进 Controller。
- `@Transactional`：订单和轨迹一起提交；其中一步失败就一起回滚。注册和签发 token 也在同一事务。
- `JdbcTemplate`：简化你学过的 JDBC；`?` 绑定参数，避免把输入拼成 SQL。
- `@RequestAttribute CurrentUser`：取鉴权过滤器查出的身份，绝不相信浏览器声称自己是谁。

密码用 BCrypt 保存，数据库只保存带盐哈希。登录时用 `matches` 比对，而不是把哈希解密。每次登录生成 32 字节随机 token，前端继续按 `Authorization: Bearer <token>` 发送。数据库只保存 token 的 SHA-256 摘要及有效期，默认 8 小时；登出删除对应记录。多次登录的其他 token 不受单次退出影响。

## 2. 项目结构

```text
pom.xml                              Maven 依赖与构建配置
src/main/java/com/example/logistics/
  LogisticsApplication.java          程序入口
  api/                               统一返回体、中文错误处理
  auth/                              注册登录、密码哈希、token 校验
  order/                             订单接口、字段校验、SQL 与归属检查
  operations/                        员工任务、库存、管理员、报表、首位管理员初始化
  config/                            本地跨域配置
src/main/resources/application.yml   MySQL 配置（密码从环境变量读取）
src/main/resources/schema.sql        表结构源文件，正式启动不自动执行
src/main/resources/static/           真实接口模式的前端副本和本地依赖
src/test/                           H2 测试，H2 不打进正式 jar
sql/01-create-database.sql           建库 SQL
sql/02-schema.sql                    可直接在 MySQL 执行的建表 SQL
sql/03-full-features.sql             第一阶段旧库的增量升级，不删除原有数据
scripts/mvn.ps1                      优先使用本项目便携 Maven
scripts/verify-api.ps1               对运行中的后端执行 HTTP 验证
reference/                          原始页面、接口文档、mock 服务器
docs/                               接口核对、验证记录
```

`sql/02-schema.sql` 与 `src/main/resources/schema.sql` 内容一致；以后修改表结构应同时维护。原始文件只作为接口参考，其中的建议和“已验证”声明不代表本项目已完成。

## 3. 环境要求与本机情况

- 项目使用 Spring Boot 3.5.7，编译目标 Java 17；当前已在本机 JDK **24.0.1** 上编译测试。
- Maven 3.6.3+。本机 PATH 没有 Maven，已下载 **Maven 3.9.11** 到 `.tools/apache-maven-3.9.11`，未修改系统 PATH。
- MySQL **8.0.16+**；本机已配置 **8.0.46**，程序在 F 盘，服务为 `MySQL80Logistics`，本机端口 3306。其他电脑请先安装或连接自己的 MySQL。
- `.tools` 和 `.m2` 是本机工具及依赖缓存，已加入 `.gitignore`，不属于源代码。其他组员可自行安装 Maven，然后直接使用 `mvn`。

在 PowerShell 中进入项目目录：

```powershell
cd 'C:\Users\bet\Documents\ChatGPT\3200'
$env:JAVA_HOME = 'D:\jdk'    # 本机已验证；其他电脑改成自己的 JDK 目录
java -version
javac -version
& .\scripts\mvn.ps1 -version
```

如果系统存在旧 Java 7/8，不要把它们设置为本项目的 `JAVA_HOME`。脚本只在没有 `JAVA_HOME` 且 `D:\jdk` 存在时自动使用它，不修改系统设置。

## 4. 创建 MySQL 数据库和表

先启动 MySQL 服务。用 MySQL 命令行、Workbench 或 Navicat 以有建库权限的账号执行下面两份文件：

1. `sql/01-create-database.sql`。
2. 选择 `logistics` 数据库，再执行 `sql/02-schema.sql`。

MySQL 命令行示例（`SOURCE` 后使用你实际的绝对路径）：

```sql
SOURCE C:/Users/bet/Documents/ChatGPT/3200/sql/01-create-database.sql;
USE logistics;
SOURCE C:/Users/bet/Documents/ChatGPT/3200/sql/02-schema.sql;
SHOW TABLES;
```

应出现四张表：`app_users`、`auth_tokens`、`logistics_orders`、`order_traces`。用户名大小写敏感，与原 mock 的严格比较一致。建表脚本只对新库执行一次：索引不是重复执行迁移脚本。脚本没有 DROP，不会清空你的旧数据；若同名表已存在，应先核对结构。

用数据库管理员创建一个应用账号，以下密码只是占位符，执行前请替换：

```sql
CREATE USER 'logistics_app'@'localhost' IDENTIFIED BY '替换为自己的数据库密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON logistics.* TO 'logistics_app'@'localhost';
```

没有自动创建 `zhangsan/staff01/admin` 演示账号。请使用前端注册自己的客户账号。

## 5. 配置、测试、启动

测试不需要 MySQL，使用独立的 H2 内存数据库，结束后数据消失：

```powershell
& .\scripts\mvn.ps1 test
```

连接真实 MySQL 并运行：

```powershell
$env:DB_USERNAME = 'logistics_app'
$env:DB_PASSWORD = '你刚设置的数据库密码'
$env:DB_URL = 'jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
& .\scripts\mvn.ps1 spring-boot:run
```

环境变量只作用于当前 PowerShell 及其子进程；换一个窗口启动时要重新设置。密码不写进代码，不把自己的环境配置提交到仓库。服务启动不代表数据库连通，必须继续验证 API。

浏览器打开：**http://localhost:8080/logistics-admin.html**。项目内这份页面已设置 `USE_MOCK:false`、`BASE:'/api'`，使用本地 Vue 3.4.38 / Element Plus 2.7.8 文件。原始页面完整保存在 `reference`，未修改外部原文件。

项目页面默认启用 `PHASE_ONE:true`：登录及刷新后只加载已实现的订单接口，下单后不再请求统计。客户首页提供寄件、订单和轨迹入口，不显示尚未实现的统计、签收、反馈和通知；员工/管理员登录后显示功能说明页。真实模式不展示演示账号或角色切换入口，角色以实际登录账号为准。已实现接口的业务失败、HTTP错误及401仍会正常提示。

后续完整后端就绪后，可在页面 CONFIG 中设置 `PHASE_ONE:false` 恢复完整模块界面。`USE_MOCK:true` 的演示模式仍保留完整功能，reference 中的原始页面没有修改。

前端请求回归检查可执行（需要 Node.js）：

```powershell
node --test .\scripts\test-phase-one-frontend.cjs
```

不要同时启动 mock-server.js，它默认也占用 8080。也可改端口：

```powershell
$env:SERVER_PORT = '8081'
& .\scripts\mvn.ps1 spring-boot:run
```

这时访问 `http://localhost:8081/logistics-admin.html`，同源相对地址 `/api` 会跟随端口。

如果使用另一份独立托管的前端，按原文档改 `USE_MOCK:false` 与 `BASE` 即可。默认放行 `http://localhost:5500`、`http://127.0.0.1:5500` 以及同主机 8000 端口，OPTIONS 预检成功返回 204。其他端口通过逗号分隔的 `CORS_ORIGINS` 环境变量配置；不建议直接双击 `file://` 页面。

## 6. 对真实 MySQL 服务执行验证

保持后端运行，在另一个 PowerShell 窗口执行：

```powershell
cd 'C:\Users\bet\Documents\ChatGPT\3200'
& .\scripts\verify-api.ps1
# 如果改过后端端口：
# & .\scripts\verify-api.ps1 -Base 'http://localhost:8081/api'
```

脚本创建两个随机客户和一个订单，验证登录、下单、本人查询、跨客户隔离、错误密码、登出及失效 token。**这些测试数据会保留在你连接的数据库中**，脚本不执行清库操作，也不打印密码/token。后端必须连接真实 MySQL 才能据此说明 MySQL 联调通过。

在数据库检查持久化及哈希（不要对外展示自己的真实密码或 token）：

```sql
SELECT id, account, role, password_hash FROM app_users ORDER BY id DESC LIMIT 5;
SELECT no, owner_id, receiver, weight, fee, status, created_at FROM logistics_orders ORDER BY created_at DESC LIMIT 5;
SELECT order_no, event_time, txt, done FROM order_traces ORDER BY id DESC LIMIT 5;
```

停止并重新启动后端，再使用你自己注册的账号登录；原订单仍应可查询。这一步专门验证进程重启后持久化，不等同于 H2 内存测试。

## 7. 已实现的接口

所有地址前缀为 `/api`；成功 HTTP 200 且 `code:0`。业务错误 HTTP 200 且 `code!=0`，使原前端能显示 `message`；无效/过期 token 使用 HTTP 401。服务端内部故障使用 HTTP 500。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | `{username,password,role}`，返回 `{token,role,name,account}` |
| POST | `/auth/login` | `{username,password}`，返回同上 |
| POST | `/auth/logout` | 必须有 token，返回 `{ok:true}` |
| POST | `/orders` | `{receiver,dest,goods,weight,fee}`，返回 Order |
| GET | `/orders` | `kw/status/page/size`，返回 `{list,total}` |
| GET | `/orders/{no}` | 客户返回本人 Order，员工/管理员可查全局，含 traces、fb |

上表为原核心接口，新增模块的完整清单见 [全功能接口与权限](docs/全功能对接与升级.md)。订单列表按下单时间倒序，同时间用运单号稳定排序；`kw` 搜索运单号/物品；`page` 默认 1，`size` 默认 10、最大 100。客户不存在与非本人订单返回同一错误。

订单号格式为 `SF + UTC日期8位 + UUID十六进制32位`，共 42 位；数据库主键保证唯一。下单初始 pending、预计三天后送达，并生成真实轨迹和揽收任务。时间是 ISO 8601，重量和运费为 JSON number；fb 在无反馈时为 null，提交后为真实 Feedback。

## 8. 打包与交给组员

```powershell
& .\scripts\mvn.ps1 package
java -jar .\target\logistics-backend-0.0.1-SNAPSHOT.jar
```

运行 jar 前仍需设置数据库环境变量、完成建表。Java 编译目标为 17，组员可使用兼容的 JDK 17+。有系统 Maven 的电脑可把上文 `& .\scripts\mvn.ps1` 替换为 `mvn`。

`target`、`.m2`、`.tools` 不提交 Git。给组员源码、SQL、说明和 `src/main/resources/static` 即可；不需要把本机数据库目录或自己的密码发出去。

启动失败常见原因：`JAVA_HOME` 指向 Java 7/8、MySQL 未启动、库未创建、密码错误、账号没有表权限、8080 已被 mock 占用。数据库异常对前端只返回通用消息，避免暴露 SQL、内部路径和账号信息。

Spring Boot 3.5 系列的 Java/Maven 兼容要求可参阅[官方说明](https://docs.spring.io/spring-boot/3.5/system-requirements.html)。依赖具体版本由 `pom.xml` 固定。
