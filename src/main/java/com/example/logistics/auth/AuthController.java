package com.example.logistics.auth;

import com.example.logistics.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record LoginRequest(
            @NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,16}", message="用户名需为 4-16 位字母、数字或下划线") String username,
            @NotNull @Size(min=6,max=20,message="密码长度为 6-20 位") String password) {}
    public record RegisterRequest(
            @NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,16}", message="用户名需为 4-16 位字母、数字或下划线") String username,
            @NotNull @Size(min=6,max=20,message="密码长度为 6-20 位") String password,
            @NotBlank String role) {}

    @PostMapping("/register")
    public ApiResponse<AuthService.LoginResult> register(@Valid @RequestBody RegisterRequest r) {
        return ApiResponse.ok(auth.register(r.username(), r.password(), r.role()));
    }
    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest r) {
        return ApiResponse.ok(auth.login(r.username(), r.password()));
    }
    @PostMapping("/logout")
    public ApiResponse<Map<String,Boolean>> logout(HttpServletRequest request) {
        auth.logout((String) request.getAttribute("token"));
        return ApiResponse.ok(Map.of("ok",true));
    }
}
