package com.example.logistics.operations;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 仅供部署者首次提升已注册账号，不创建默认账号或密码。 */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AdminService admin;
    private final String account;
    public BootstrapAdmin(JdbcTemplate jdbc,AdminService admin,@Value("${BOOTSTRAP_ADMIN_ACCOUNT:}") String account) {
        this.jdbc=jdbc;this.admin=admin;this.account=account;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if(account.isBlank()) return;
        admin.lockAdministration();
        // 重启幂等；已有管理员时不允许借此提升其他账号。
        if(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE role='admin'",Long.class)>0) return;
        if(jdbc.update("UPDATE app_users SET role='admin' WHERE account=?",account)!=1)
            throw new IllegalStateException("初始化管理员失败：请先注册指定账号，再配置 BOOTSTRAP_ADMIN_ACCOUNT");
        jdbc.update("DELETE FROM auth_tokens WHERE user_id IN (SELECT id FROM app_users WHERE account=?)",account);
    }
}
