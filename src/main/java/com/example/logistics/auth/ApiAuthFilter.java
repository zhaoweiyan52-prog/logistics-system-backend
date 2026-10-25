package com.example.logistics.auth;

import com.example.logistics.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class ApiAuthFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final ObjectMapper mapper;
    public ApiAuthFilter(AuthService auth, ObjectMapper mapper) { this.auth=auth; this.mapper=mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // 使用框架解析的应用内路径，兼容真实 Servlet 容器和 MockMvc，且去除分号路径参数。
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(req);
        return !path.startsWith("/api/") || "OPTIONS".equals(req.getMethod()) ||
                ("POST".equals(req.getMethod()) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        CurrentUser user;
        try { user = auth.authenticate(token); }
        catch (org.springframework.dao.DataAccessException e) {
            write(res, 500, 50001, "数据库暂不可用"); return;
        }
        if (user == null) { write(res, 401, 401, "登录已失效，请重新登录"); return; }
        req.setAttribute("currentUser", user);
        req.setAttribute("token", token);
        chain.doFilter(req, res);
    }

    private void write(HttpServletResponse res, int status, int code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiResponse.error(code, message));
    }
}
