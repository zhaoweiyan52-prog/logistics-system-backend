-- 以数据库管理员运行一次，然后切到 logistics 库执行 02-schema.sql。
-- utf8mb4_bin 明确用户名大小写敏感，与前端和 mock 的严格匹配一致。
CREATE DATABASE IF NOT EXISTS logistics CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
-- 不在仓库保存真实数据库密码。创建应用账号示例见 README.md。
