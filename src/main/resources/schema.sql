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
