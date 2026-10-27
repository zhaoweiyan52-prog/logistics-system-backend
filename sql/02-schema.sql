-- MySQL 8.0.16+；不包含 DROP，不自动创建演示账号。
CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account VARCHAR(16) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_account UNIQUE (account),
    CONSTRAINT ck_users_role CHECK (role IN ('customer','staff','admin'))
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_tokens_user ON auth_tokens(user_id);
CREATE INDEX idx_tokens_expiry ON auth_tokens(expires_at);

CREATE TABLE IF NOT EXISTS logistics_orders (
    no VARCHAR(48) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    receiver VARCHAR(100) NOT NULL,
    dest VARCHAR(500) NOT NULL,
    goods VARCHAR(200) NOT NULL,
    weight DECIMAL(8,3) NOT NULL,
    fee DECIMAL(10,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    eta TIMESTAMP(6) NOT NULL,
    sign_code CHAR(4) NOT NULL,
    CONSTRAINT fk_orders_owner FOREIGN KEY (owner_id) REFERENCES app_users(id),
    CONSTRAINT ck_orders_weight CHECK (weight > 0),
    CONSTRAINT ck_orders_fee CHECK (fee >= 0),
    CONSTRAINT ck_orders_status CHECK (status IN ('pending','transit','delivering','delivered','exception','cancelled'))
);
CREATE INDEX idx_orders_owner_time ON logistics_orders(owner_id,created_at);
CREATE INDEX idx_orders_owner_status_time ON logistics_orders(owner_id,status,created_at);

CREATE TABLE IF NOT EXISTS order_traces (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(48) NOT NULL,
    event_time TIMESTAMP(6) NOT NULL,
    txt VARCHAR(500) NOT NULL,
    done BOOLEAN NOT NULL,
    CONSTRAINT fk_traces_order FOREIGN KEY (order_no) REFERENCES logistics_orders(no)
);
CREATE INDEX idx_traces_order_time ON order_traces(order_no,event_time);

-- 在现有 logistics 数据库执行一次；不删除或重建第一阶段表，可重复运行。
-- 请先 USE logistics；旧订单待办在末尾补齐。
CREATE TABLE IF NOT EXISTS user_access (
    user_id BIGINT NOT NULL PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE TABLE IF NOT EXISTS order_feedback (
    order_no VARCHAR(48) NOT NULL PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    reply VARCHAR(2000) NOT NULL DEFAULT '',
    FOREIGN KEY (order_no) REFERENCES logistics_orders(no)
);
CREATE TABLE IF NOT EXISTS logistics_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(48) NOT NULL,
    type VARCHAR(20) NOT NULL,
    addr VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(10) NOT NULL,
    operator_id BIGINT NULL,
    FOREIGN KEY (order_no) REFERENCES logistics_orders(no),
    FOREIGN KEY (operator_id) REFERENCES app_users(id),
    CHECK (status IN ('todo','done')),
    CHECK (type IN ('上门揽收','派送'))
);
CREATE TABLE IF NOT EXISTS task_vouchers (
    task_id BIGINT NOT NULL,
    slot INT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    note VARCHAR(500) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (task_id,slot),
    FOREIGN KEY (task_id) REFERENCES logistics_tasks(id),
    FOREIGN KEY (uploaded_by) REFERENCES app_users(id)
);
CREATE TABLE IF NOT EXISTS warehouses (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    used BIGINT NOT NULL DEFAULT 0,
    cap BIGINT NOT NULL,
    CHECK (used >= 0 AND cap > 0 AND used <= cap)
);
CREATE TABLE IF NOT EXISTS warehouse_stock (
    wh VARCHAR(100) NOT NULL,
    goods VARCHAR(200) NOT NULL,
    qty BIGINT NOT NULL,
    PRIMARY KEY (wh,goods),
    FOREIGN KEY (wh) REFERENCES warehouses(name),
    CHECK (qty >= 0)
);
CREATE TABLE IF NOT EXISTS inventory_records (
    no VARCHAR(40) NOT NULL PRIMARY KEY,
    goods VARCHAR(200) NOT NULL,
    qty BIGINT NOT NULL,
    wh VARCHAR(100) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    type VARCHAR(3) NOT NULL,
    FOREIGN KEY (wh) REFERENCES warehouses(name),
    FOREIGN KEY (operator_id) REFERENCES app_users(id),
    CHECK (qty > 0),
    CHECK (type IN ('in','out'))
);
CREATE TABLE IF NOT EXISTS system_settings (
    id INT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    notice VARCHAR(1000) NOT NULL,
    first_weight DECIMAL(8,3) NOT NULL,
    first_fee DECIMAL(8,2) NOT NULL,
    add_fee DECIMAL(8,2) NOT NULL,
    sign_ntf BOOLEAN NOT NULL,
    exc_ntf BOOLEAN NOT NULL,
    mkt_ntf BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    act VARCHAR(20) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_users(id)
);
INSERT INTO system_settings(id,name,phone,notice,first_weight,first_fee,add_fee,sign_ntf,exc_ntf,mkt_ntf)
SELECT 1,'物流管理系统','400-800-1234','',1,12,4,true,true,false
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE id=1);
-- 以下是可修改的仓库基础配置，初始实际库存均为 0，没有生成演示业务数据。
INSERT INTO warehouses(name,used,cap) SELECT '华东一号仓',0,1500 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='华东一号仓');
INSERT INTO warehouses(name,used,cap) SELECT '华东二号仓',0,1200 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='华东二号仓');
INSERT INTO warehouses(name,used,cap) SELECT '华南仓',0,1400 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='华南仓');
INSERT INTO warehouses(name,used,cap) SELECT '华北仓',0,1000 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='华北仓');
INSERT INTO warehouses(name,used,cap) SELECT '西南仓',0,800 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='西南仓');
INSERT INTO warehouses(name,used,cap) SELECT '华中仓',0,900 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE name='华中仓');
INSERT INTO logistics_tasks(order_no,type,addr,created_at,status)
SELECT o.no,CASE WHEN o.status='pending' THEN '上门揽收' ELSE '派送' END,o.dest,o.created_at,'todo'
FROM logistics_orders o WHERE o.status IN ('pending','transit','delivering','exception')
AND NOT EXISTS (SELECT 1 FROM logistics_tasks t WHERE t.order_no=o.no AND t.status='todo');
