package com.example.logistics.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderModels {
    private OrderModels() {}

    public record CreateOrder(
            @NotBlank @Size(max=100) String receiver,
            @NotBlank @Size(max=500) String dest,
            @NotBlank @Size(max=200) String goods,
            @NotNull @DecimalMin("0.001") @DecimalMax("99999.999") @Digits(integer=5,fraction=3) BigDecimal weight,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal fee) {}

    public record Trace(Instant t, String txt, boolean done) {}
    public record OrderView(String no, String goods, String dest, String receiver, BigDecimal fee,
                            BigDecimal weight, String status, Instant time, Instant eta, String signCode,
                            List<Trace> traces, Object fb) {}
    public record OrderPage(List<OrderView> list, long total) {}
}
