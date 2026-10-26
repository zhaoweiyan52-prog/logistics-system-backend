# 本机 MySQL 配置记录

配置及验证日期：2026-10-26，Asia/Shanghai。

## 实际配置

| 项目 | 实际值 |
|---|---|
| MySQL 版本 | Community Server 8.0.46 Win64 |
| 服务程序 | `F:\MYSQL\Server\bin\mysqld.exe` |
| 命令行客户端 | `F:\MYSQL\Server\bin\mysql.exe` |
| 数据目录 | `F:\MYSQL\Data` |
| 配置文件 | `F:\MYSQL\my.ini` |
| Windows 服务名 | `MySQL80Logistics` |
| 服务状态 / 启动类型 | Running / Automatic |
| 服务运行账号 | `NT AUTHORITY\LocalService` |
| 监听地址 / 端口 | `127.0.0.1:3306`，仅本机；MySQL X 协议关闭 |
| 字符集 / 排序规则 | utf8mb4 / utf8mb4_bin |
| 存储引擎 / 时区 | InnoDB / +00:00 |
| 数据库 / 应用账号 | logistics / logistics_app@localhost |
| 错误日志目录 | `F:\MYSQL\Logs` |
| 临时文件目录 | `F:\MYSQL\Temp` |

服务的实际启动命令为：

```text
F:\MYSQL\Server\bin\mysqld.exe --defaults-file=F:\MYSQL\my.ini MySQL80Logistics
```

配置前已核对：数据目录为空，未发现 MySQL 服务或 mysqld 进程，3306/33060 空闲。只对当时确认为空的 F 盘目录初始化一次，未删除原文件，未迁移或清理 C 盘安装器目录。安装器配置向导未用于执行另一套配置；以后不要在它里面对 C 盘重复点击初始化。

## 密码与本机启动

root 和应用账号密码由用户在本机管理员窗口隐藏输入；未在聊天、Git 源码、命令行参数中输出。root 密码未保存。项目专用账号仅有 logistics 数据库的 SELECT/INSERT/UPDATE/DELETE 权限，不授予建库、建表或全局管理权限。

应用账号凭证保存在 `F:\MYSQL\Client\logistics-app.credential.xml`，由 Windows DPAPI 按当前 Windows 用户加密，所在目录只允许该用户、SYSTEM 和管理员访问。该文件在项目目录外，不纳入 Git。不要把它发给组员；其他电脑使用自己的账号密码。

MySQL 随 Windows 自动启动。Java 后端不自动启动；以后在项目目录运行：

```powershell
& .\scripts\start-mysql-backend.ps1
```

脚本自动设置本次 Java 进程所需的 DB_URL、DB_USERNAME、DB_PASSWORD，结束后恢复调用窗口的环境变量。凭证文件不存在时，会在本机隐藏提示输入应用账号密码。Java 默认使用 `D:\jdk`；其他路径可传入 `-JavaHome`。脚本仅将后端绑定在本机地址。

需要在后台运行时：

```powershell
& .\scripts\start-mysql-backend.ps1 -Background
```

后台日志与进程编号放在被 Git 忽略的 target 目录。端口已占用时脚本会停止并提示，不会擅自终止其他程序。

页面地址：[本机物流页面](http://localhost:8080/logistics-admin.html)。前端请注册自己的客户账号，mock 演示账号未在真实库创建。页面已适配第一阶段，默认不再请求尚未实现的统计、任务、库存、用户管理和通知接口；员工/管理员登录后显示功能说明。

## 实际通过的验证

1. MySQL 服务启动、自动启动设置及服务重启成功；重启后数据库四张表和应用账号保留。
2. SQL 实际返回版本8.0.46、数据目录F盘、端口3306、时区+00:00。
3. Spring Boot 使用 MySQL Connector/J 建立真实连接，未使用 H2 profile。
4. `scripts/verify-api.ps1` 的10项实际HTTP检查通过：注册、登录、订单创建、本人查询、跨客户隔离、错误密码、退出及token失效等。
5. 新建另一客户和订单后，只重启本任务的Java后端；原token仍可查到原订单，原账号可重新登录。
6. 直接查询真实MySQL：验证时有3个测试客户、2个测试订单、2条物流轨迹；用户密码均符合BCrypt哈希格式。没有删除这些测试数据。
7. 前端HTML资源通过真实服务返回HTTP200。

本机原始证据在被Git忽略的target目录：`mysql-local-setup-status.json`、`mysql-local-server-verification.txt`、`mysql-http-verification.json`、`mysql-persistence-verification.json`、`mysql-direct-verification.txt`。其中不包含用户密码或有效登录token。

**尚未验证：**浏览器内完整点击交互；MySQL在Windows整机重启后的自动启动；签收、统计、员工任务、库存等未实现阶段的业务。本次没有重新运行全部20项H2测试，也没有把这些H2测试冒充MySQL测试。

首次初始化与Windows服务选项参考[MySQL初始化说明](https://dev.mysql.com/doc/refman/8.0/en/data-directory-initialization.html)和[Windows服务说明](https://dev.mysql.com/doc/refman/8.0/en/windows-start-service.html)。
