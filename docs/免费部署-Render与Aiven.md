# 免费部署：Render + Aiven MySQL

> 当前站点已上线：https://logistics-system-backend-o7if.onrender.com/ 。数据库连接和带浏览器 Origin 的业务检查已通过，个人云端管理员 betswj 已启用并验证，详见 [云端验证记录](云端部署验证记录.md)。下文是最初部署步骤，初始“未验证”状态以新记录为准。

网页和Spring Boot运行在同一个Render Free Web Service，数据库使用Aiven Free MySQL。云端是新库，不上传F盘数据、账号或Windows加密凭证。

**当前只完成部署准备，尚未生成并验证公网网址。以下步骤全部选择Free；如果平台没有Free选项，先停止，不改选付费套餐。**

## 一、注册与授权

1. 注册或登录 [Render](https://dashboard.render.com/)，连接GitHub。
2. 授权Render读取私有仓库 `zhaoweiyan52-prog/logistics-system-backend`，无需把仓库公开。
3. 注册或登录 [Aiven](https://console.aiven.io/)，创建MySQL服务，选择Free并确认价格为0，不选择付费套餐的限时试用。

官网登录、验证码和协议由你本人完成。不要向聊天发送密码、API令牌或带密码的Service URI。

## 二、初始化云端MySQL

在Aiven服务Overview / Connection information查看Host、Port和管理账号，下载CA Certificate（ca.pem）。端口使用平台实际值，不默认填3306。

用本机Workbench连接这个云端地址；在本机输入管理密码，启用SSL证书验证并选择下载的CA。只在新云端库执行：

1. `sql/01-create-database.sql`。
2. `USE logistics;`。
3. `sql/02-schema.sql`，仅一次，避免重复创建索引。

如果云端已经运行过第一阶段四表版，只需选择 logistics 后执行 `sql/03-full-features.sql`；首位管理员的启用见 [全功能升级说明](全功能对接与升级.md)。

然后在本机SQL编辑器创建应用账号。下面密码是占位符，执行前必须本机替换；不把真实密码保存到Git：

```sql
CREATE USER 'logistics_app'@'%' IDENTIFIED BY '在本机替换为强密码' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON logistics.* TO 'logistics_app'@'%';
```

这和本机logistics_app是不同账号。Render使用云端密码，不使用本机root或本机logistics_app密码。

## 三、创建Render Web Service

建议使用控制台：New → Web Service → 选择GitHub仓库 → main分支 → Docker → Dockerfile路径 `./Dockerfile` → Root Directory留空 → Instance Type选Free。

环境变量填写：

| 名称 | 值 |
|---|---|
| SPRING_PROFILES_ACTIVE | cloud |
| DB_USERNAME | logistics_app |
| DB_PASSWORD | 云端应用账号密码，只填写Render控制台 |
| DB_URL | 下方JDBC地址，替换HOST和PORT |

```text
jdbc:mysql://HOST:PORT/logistics?useUnicode=true&characterEncoding=UTF-8&sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

DB_URL不能直接使用带用户名密码的 `mysql://...` Service URI。HOST和PORT使用Aiven实际值，不填写localhost。

在Advanced → Secret Files中添加文件 **ca.pem**，粘贴完整Aiven CA证书。该文件运行时位于 `/etc/secrets/ca.pem`。CA不提交Git，私钥不上传。

Health Check Path设置为 `/health`。确认套餐显示Free后创建服务。

Docker脚本把CA导入临时Java信任库，cloud配置强制验证数据库证书和主机名；缺少证书、DB_URL或密码会停止启动，不关闭SSL验证。信任库的changeit只保护公开CA证书，不是数据库密码。

仓库另有 `render.yaml` Blueprint，明确指定 `plan: free`，只创建一个Web Service，不创建付费磁盘或Render数据库。使用Blueprint同样需要填写环境变量、添加CA并先建好Aiven库表。

## 四、验证上线

以Render实际显示的域名为准，不能把以下占位地址当作已部署网址：

```text
https://你的域名.onrender.com/
https://你的域名.onrender.com/health
```

根路径会跳转到物流页面。health正常返回 `{"status":"UP"}`；数据库或任一必需表不可用时返回HTTP503和DOWN，不泄露数据库详情。

注册客户 → 创建订单 → 查询 → 退出重登 → 另一个客户看不到该订单。云端没有本机账号，需要重新注册。

接口检查可运行 `scripts/verify-api.ps1 -Base 'https://你的实际域名.onrender.com/api'`。脚本会保留测试账号和订单，不清库。

## 五、限制与验证边界

Render免费Web服务15分钟无访问会休眠，再次唤醒可能约一分钟；Aiven Free有存储和活跃度限制。按平台当前免费额度使用，不添加付费资源。参考 [Render Free](https://render.com/docs/free) 和 [Aiven Free](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)。

当前本机验证结果见 README 和全功能升级说明。本机没有Docker，尚未运行docker build/run。Aiven TLS连接、Render镜像构建、在线健康检查与公网浏览器联调尚待验证。

遇到证书错误先核对CA和Aiven域名，不关闭验证；Access denied核对云端账号密码；health DOWN核对表结构；连接超时核对Host/Port、云端服务状态及访问控制。不要把本机F盘路径填入Render。

参考：[Render Web Services](https://render.com/docs/web-services)、[Blueprint配置](https://render.com/docs/blueprint-spec)、[Aiven TLS证书](https://aiven.io/docs/platform/concepts/tls-ssl-certificates)。
