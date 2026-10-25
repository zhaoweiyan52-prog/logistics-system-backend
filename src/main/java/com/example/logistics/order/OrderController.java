package com.example.logistics.order;

import com.example.logistics.api.ApiResponse;
import com.example.logistics.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.example.logistics.order.OrderModels.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders=orders; }

    @PostMapping
    public ApiResponse<OrderView> create(@RequestAttribute CurrentUser currentUser, @Valid @RequestBody CreateOrder r) {
        return ApiResponse.ok(orders.create(currentUser,r));
    }
    @GetMapping
    public ApiResponse<OrderPage> list(@RequestAttribute CurrentUser currentUser,
            @RequestParam(defaultValue="") String kw, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(orders.list(currentUser,kw,status,page,size));
    }
    @GetMapping("/{no}")
    public ApiResponse<OrderView> detail(@RequestAttribute CurrentUser currentUser, @PathVariable String no) {
        return ApiResponse.ok(orders.detail(currentUser,no));
    }
}
