package com.example.logistics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    FilterRegistrationBean<CorsFilter> cors(@Value("${app.cors-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET","POST","PATCH","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type","Authorization"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        CorsFilter filter = new CorsFilter(source);
        // 契约要求预检返回 204；仍由标准 CorsFilter 校验来源和请求头。
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            public boolean processRequest(CorsConfiguration c, jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
                boolean accepted = super.processRequest(c, req, res);
                if (accepted && CorsUtils.isPreFlightRequest(req)) res.setStatus(204);
                return accepted;
            }
        });
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(filter);
        // Boot 的 ForwardedHeaderFilter 必须先把代理内的 HTTP 请求恢复为公网 HTTPS 地址，
        // 否则同源浏览器 POST 的 Origin 会被误判为跨域，并在登录前返回 403。
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
