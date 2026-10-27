package com.example.logistics.operations;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class OperationsModels {
    private OperationsModels() {}
    public record Sign(@NotBlank @Pattern(regexp="[0-9]{4}") String code, @Size(max=100) String by) {}
    public record Feedback(@NotBlank @Size(max=40) String type, @NotBlank @Size(max=2000) String content) {}
    public record TaskStatus(@NotBlank String status, @Size(max=500) String note) {}
    public record Inventory(@NotBlank @Pattern(regexp="in|out") String type,
                            @NotBlank @Size(max=200) String goods, @NotNull @Min(1) @Max(1000000) Integer qty,
                            @NotBlank @Size(max=100) String wh) {}
    public record Role(@NotBlank String role) {}
    public record BatchRole(@NotBlank String role, @NotEmpty @Size(max=100) List<@NotNull @Positive Long> ids) {}
    public record Active(@NotNull Boolean active) {}
    public record Settings(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=40) String phone,
                           @NotNull @Size(max=1000) String notice,
                           @NotNull @DecimalMin("1") @DecimalMax("99999") BigDecimal first,
                           @NotNull @DecimalMin("1") @DecimalMax("999999") @Digits(integer=6,fraction=2) BigDecimal firstFee,
                           @NotNull @DecimalMin("1") @DecimalMax("999999") @Digits(integer=6,fraction=2) BigDecimal addFee,
                           @NotNull Boolean signNtf, @NotNull Boolean excNtf, @NotNull Boolean mktNtf) {}
}
