package com.example.logistics.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    // 页面先检查 HTTP 状态再读 JSON；业务失败用 HTTP 200 + 非零 code，才能显示中文原因。
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> business(BusinessException e) { return ApiResponse.error(e.code, e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> validation(MethodArgumentNotValidException e) {
        return ApiResponse.error(40001, e.getBindingResult().getFieldErrors().stream()
                .map(x -> x.getField() + ": " + x.getDefaultMessage()).sorted().findFirst().orElse("参数不正确"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ApiResponse<Void> malformed(Exception e) { return ApiResponse.error(40001, "参数格式错误，请检查字段、类型及范围"); }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ApiResponse<Void> unsupported(Exception e) { return ApiResponse.error(50101, "该接口或请求方法未在第一阶段实现"); }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> database(DataAccessException e) {
        log.error("数据库操作失败", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(50001, "数据库操作失败，请联系后端同学检查服务日志"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("请求处理失败", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(50000, "服务端异常"));
    }
}
