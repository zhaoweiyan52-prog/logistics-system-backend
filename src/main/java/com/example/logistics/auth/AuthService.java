package com.example.logistics.auth;

import com.example.logistics.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final long tokenHours;

    public AuthService(JdbcTemplate jdbc, @Value("${app.token-hours:8}") long tokenHours) {
        this.jdbc = jdbc;
        this.tokenHours = tokenHours;
    }

    public record LoginResult(String token, String role, String name, String account) {}
    private record Account(CurrentUser user, String passwordHash) {}

    @Transactional
    public LoginResult register(String username, String password, String role) {
        // BCrypt 最多处理 72 字节；限制可避免多字节密码被截断或编码器抛异常。
        validatePasswordBytes(password);
        if (!"customer".equals(role) && !"staff".equals(role)) {
            throw new BusinessException(40002, "注册角色只能是 customer 或 staff，不开放 admin 注册");
        }
        try {
            jdbc.update("INSERT INTO app_users(account,password_hash,name,role,created_at) VALUES (?,?,?,?,?)",
                    username, passwords.encode(password), username, role, Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40901, "该用户名已被注册，请直接登录");
        }
        return issue(find(username).user());
    }

    @Transactional
    public LoginResult login(String username, String password) {
        validatePasswordBytes(password);
        Account account = find(username);
        if (account == null || !passwords.matches(password, account.passwordHash())) {
            throw new BusinessException(40101, "用户名或密码错误");
        }
        Boolean enabled=jdbc.queryForObject("SELECT COALESCE((SELECT active FROM user_access WHERE user_id=?),true)",Boolean.class,account.user().id());
        if(!Boolean.TRUE.equals(enabled)) throw new BusinessException(40302,"账号已禁用，请联系管理员");
        return issue(account.user());
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(40001, "密码 UTF-8 编码不能超过 72 字节");
        }
    }

    private Account find(String username) {
        return jdbc.query("SELECT id,account,name,role,password_hash FROM app_users WHERE account=?",
                (rs, n) -> new Account(new CurrentUser(rs.getLong("id"), rs.getString("account"),
                        rs.getString("name"), rs.getString("role")), rs.getString("password_hash")), username)
                .stream().findFirst().orElse(null);
    }

    private LoginResult issue(CurrentUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",
                hash(token), user.id(), Timestamp.from(Instant.now().plus(tokenHours, ChronoUnit.HOURS)));
        return new LoginResult(token, user.role(), user.name(), user.account());
    }

    public CurrentUser authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return jdbc.query("""
                SELECT u.id,u.account,u.name,u.role FROM auth_tokens t
                JOIN app_users u ON u.id=t.user_id LEFT JOIN user_access a ON a.user_id=u.id
                WHERE t.token_hash=? AND t.expires_at>? AND COALESCE(a.active,true)=true
                """, (rs, n) -> new CurrentUser(rs.getLong("id"), rs.getString("account"),
                rs.getString("name"), rs.getString("role")), hash(token), Timestamp.from(Instant.now()))
                .stream().findFirst().orElse(null);
    }

    public void logout(String token) { jdbc.update("DELETE FROM auth_tokens WHERE token_hash=?", hash(token)); }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
