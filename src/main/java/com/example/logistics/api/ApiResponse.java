package com.example.logistics.api;

public record ApiResponse<T>(int code, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, data, "成功"); }
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
