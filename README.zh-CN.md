# 物流管理系统

本科软件工程课程项目，支持客户、物流员工和管理员三种角色。
[在线演示](https://logistics-system-backend-o7if.onrender.com/logistics-admin.html) · [English](README.md)

## 文档导航

- [文档目录与课程提交清单](docs/README.md)：阅读顺序、辅助资料及课程要求。
- [需求规格说明书](docs/SRS.md)：当前需求基线、业务规则、范围限制及验收标准。
- [设计与接口](docs/DESIGN.md)：架构、数据模型、状态转换及 24 项 API 操作。
- [验证与演进](docs/VERIFICATION.md)：测试证据、修复日志及演示步骤。

这些文档描述了截至 2026 年 10 月 28 日、在代码提交 `70bd1d2` 时已部署的系统。它们可用于评估该项目，但与课程的最终报告（最多 1,000 词）或每位学生的个人日志并不相同。

## 功能范围

客户可以下单、查询订单状态和物流信息、使用验证码确认收货，并提交反馈。员工通过共享任务池跟踪进度、上传签收凭证并记录库存变动。管理员负责管理角色及账户启用/停用、查看报告以及保存设置。当前界面和业务提示均为中文；英文文档并不意味着界面已本地化为英文。角色标签分别为“客户”、“物流员工”和“系统管理员”。

## 技术与架构

后端基于 Java 17，使用 Spring Boot 3.5.7、JdbcTemplate 和 MySQL。系统采用分层架构，集成于单个 Spring Boot 应用程序中。Vue 3 和 Element Plus 提供浏览器界面。Spring Boot 负责提供页面及其依赖项，因此无需进行 npm 构建。公开演示版在 Render 上运行，使用 Aiven MySQL 数据库。

## 安装与运行

需要 JDK 17 及以上版本、Maven 3.6.3 及以上版本以及 MySQL 8.0.16 及以上版本。前端脚本检查还要求 Node.js 支持 `node --test` 命令。经过验证的环境使用 JDK 24.0.1、Maven 3.9.11 和 MySQL 8.0.46； 基于云的 Docker 环境使用 Java 17。在新计算机上，您应自行安装工具，而非依赖维护者的便携工具缓存。

### 1. 获取代码

```sh
git clone https://github.com/zhaoweiyan52-prog/logistics-system-backend.git
cd logistics-system-backend
java -version
mvn -version
```

### 2. 准备独立数据库

在代码库的根目录下，使用数据库管理员账户启动 MySQL 命令行客户端。仅当需要创建新的空数据库时，才运行以下 SQL 语句。

```sh
mysql -u root -p
```

```sql
SOURCE sql/01-create-database.sql;
USE logistics;
SOURCE sql/02-schema.sql;
SHOW TABLES;
CREATE USER 'logistics_app'@'localhost' IDENTIFIED BY '<YOUR_LOCAL_DATABASE_PASSWORD>';
GRANT SELECT, INSERT, UPDATE, DELETE ON logistics.* TO 'logistics_app'@'localhost';
```

请将密码占位符替换为您自己的本地数据库密码。此时应包含 13 张表。应用程序在启动时不会自动创建表；请勿在现有数据库上重新运行脚本 02，因为某些索引语句无法重新执行。对于旧版第一阶段数据库，请使用 `sql/03-full-features.sql` 以在升级过程中保留数据；已升级的数据库无需重建。

### 3. 配置和启动

Windows PowerShell：

```powershell
$env:DB_USERNAME = 'logistics_app'
$env:DB_PASSWORD = '<YOUR_LOCAL_DATABASE_PASSWORD>'
$env:DB_URL = 'jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
mvn spring-boot:run
```

macOS / Linux：

```sh
export DB_USERNAME='logistics_app'
export DB_PASSWORD='<YOUR_LOCAL_DATABASE_PASSWORD>'
export DB_URL='jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
mvn spring-boot:run
```

在浏览器中打开 [http://localhost:8080/logistics-admin.html](http://localhost:8080/logistics-admin.html)，并验证 [http://localhost:8080/health](http://localhost:8080/health) 是否返回 `{"status":"UP"}`。请勿直接双击 HTML 文件；该页面的 `/api` 需要后端正在运行。本地账户和生产环境账户存储在不同的数据库中。密码仅在本地配置，不应提交到 Git。

### 4. 创建评估账户

系统没有默认的登录用户名或密码。在“注册账号”选项卡中，您可以选择“我是客户”或“我是物流员工”，注册成功后系统会自动登录。仅当您的新数据库尚未设置管理员时，请按以下步骤操作：

1. 首先，注册一个用于评估的账户，然后停止应用程序。
2. 将环境变量 `BOOTSTRAP_ADMIN_ACCOUNT` 设置为该用户名，然后重启应用程序。
3. 停止应用程序，删除该环境变量，重启应用程序并重新登录；此时该账户即成为管理员。此过程将撤销该账户的原始令牌。

如果已存在管理员，此机制不会提升其他账户的权限；后续的角色分配需在管理员页面进行。生产环境的管理员已初始化，不应再次配置。

请使用以下命令执行步骤 2 和 3，将 `<REGISTERED_USERNAME>` 替换为您创建的账户。每次重启前，请使用 Ctrl+C 停止正在运行的应用程序。请在同一终端中保持数据库环境变量的设置。

| 命令行环境 | 首次重启前设置 | 第二次重启前移除 |
|---|---|---|
| PowerShell | `$env:BOOTSTRAP_ADMIN_ACCOUNT = '<REGISTERED_USERNAME>'` | `Remove-Item Env:BOOTSTRAP_ADMIN_ACCOUNT` |
| macOS / Linux | `export BOOTSTRAP_ADMIN_ACCOUNT='<REGISTERED_USERNAME>'` | `unset BOOTSTRAP_ADMIN_ACCOUNT` |

每个步骤完成后，请使用 `mvn spring-boot:run` 重新启动。

### 5. 测试与打包

```sh
mvn test
node --test scripts/test-phase-one-frontend.cjs
mvn package
java -jar target/logistics-backend-0.0.1-SNAPSHOT.jar
```

在启动 JAR 文件之前，请先停止开发服务，以避免端口冲突；但数据库环境变量仍需设置。后端默认使用独立的 H2 内存数据库进行测试；无需 MySQL，且这不能保证所有 MySQL 行为均正确。前端脚本名称保留了历史惯例，但测试包含完整模式。可使用 `SERVER_PORT` 环境变量更改端口。

如果启动失败，请检查 JDK、MySQL 服务、数据库表、应用程序账户权限以及端口使用情况。免费生产实例可能存在启动延迟。请使用独立的本地数据进行测试；公开演示环境并非可随意清空的数据沙箱。

## 已知限制

库存和发货订单是分离的；不支持个人任务分配、发件人详情或取件地址、完整的订单取消流程、反馈回复、密码重置，也没有签收凭证查看界面。签收码仅为教学功能；运费由页面根据设置计算并提交，随后由后端验证并保存；这些数据不用于实际计费或身份验证。通知已读状态不会在服务器上持久化。详细功能边界请参阅 SRS。

## 代码库结构

`src/main/java`：后端；`src/main/resources/static`：实际页面及本地依赖项； `src/test`：自动化测试；`sql`：数据库创建与增量升级；`scripts`：辅助执行与验证；`docs`：英文评审材料及中文验证记录；`reference`：原始页面、模拟服务及历史接口约定。
`reference` 中的模拟验证不等同于对当前应用程序的验收，亦非应用程序的入口点。构建产物、凭证及 IDE 设置不属于源代码提交范围。

本机专用路径、启动脚本、代码入门和详细排障见 [开发与本机维护说明](docs/开发与本机维护说明.md)。
