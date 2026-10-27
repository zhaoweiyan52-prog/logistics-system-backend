package com.example.logistics.operations;

import com.example.logistics.api.ApiResponse;
import com.example.logistics.auth.CurrentUser;
import com.example.logistics.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OperationsController {
    private final WorkflowService workflow;
    private final InventoryService inventory;
    private final AdminService admin;
    private final ReportService reports;
    private final OrderService orders;
    public OperationsController(WorkflowService w,InventoryService i,AdminService a,ReportService r,OrderService o) {
        workflow=w;inventory=i;admin=a;reports=r;orders=o;
    }
    private ApiResponse<Map<String,Boolean>> ok() { return ApiResponse.ok(Map.of("ok",true)); }
    @PostMapping("/orders/{no}/sign")
    public ApiResponse<?> sign(@RequestAttribute CurrentUser currentUser,@PathVariable String no,@Valid @RequestBody OperationsModels.Sign r) {
        workflow.sign(currentUser,no,r);return ApiResponse.ok(orders.detail(currentUser,no));
    }
    @PostMapping("/orders/{no}/feedback")
    public ApiResponse<?> feedback(@RequestAttribute CurrentUser currentUser,@PathVariable String no,@Valid @RequestBody OperationsModels.Feedback r) {
        return ApiResponse.ok(workflow.feedback(currentUser,no,r));
    }
    @GetMapping("/tasks")
    public ApiResponse<?> tasks(@RequestAttribute CurrentUser currentUser,@RequestParam(defaultValue="") String status,
                               @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(workflow.tasks(currentUser,status,page,size));
    }
    @PostMapping("/tasks/{id}/status")
    public ApiResponse<?> status(@RequestAttribute CurrentUser currentUser,@PathVariable long id,@Valid @RequestBody OperationsModels.TaskStatus r) {
        workflow.status(currentUser,id,r);return ok();
    }
    @PostMapping(value="/tasks/{id}/voucher",consumes="multipart/form-data")
    public ApiResponse<?> voucher(@RequestAttribute CurrentUser currentUser,@PathVariable long id,
                                 @RequestParam(required=false) List<MultipartFile> files,
                                 @RequestParam(name="files[]",required=false) List<MultipartFile> arrayFiles,
                                 @RequestParam(defaultValue="") String note) throws IOException {
        List<MultipartFile> combined=new ArrayList<>();
        if(files!=null) combined.addAll(files);
        if(arrayFiles!=null) combined.addAll(arrayFiles);
        workflow.voucher(currentUser,id,combined,note);return ok();
    }
    @GetMapping("/inventory")
    public ApiResponse<?> inventory(@RequestAttribute CurrentUser currentUser,@RequestParam(defaultValue="") String type) {
        return ApiResponse.ok(inventory.list(currentUser,type));
    }
    @PostMapping("/inventory")
    public ApiResponse<?> inventory(@RequestAttribute CurrentUser currentUser,@Valid @RequestBody OperationsModels.Inventory r) {
        return ApiResponse.ok(inventory.create(currentUser,r));
    }
    @GetMapping("/warehouses")
    public ApiResponse<?> warehouses(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(inventory.warehouses(currentUser));}
    @GetMapping("/users")
    public ApiResponse<?> users(@RequestAttribute CurrentUser currentUser,@RequestParam(defaultValue="") String kw,
                               @RequestParam(defaultValue="") String role,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(admin.users(currentUser,kw,role,page,size));
    }
    @PatchMapping("/users/{id}/role")
    public ApiResponse<?> role(@RequestAttribute CurrentUser currentUser,@PathVariable long id,@Valid @RequestBody OperationsModels.Role r) {
        admin.roles(currentUser,List.of(id),r.role());return ok();
    }
    @PostMapping("/users/batch-role")
    public ApiResponse<?> roles(@RequestAttribute CurrentUser currentUser,@Valid @RequestBody OperationsModels.BatchRole r) {
        return ApiResponse.ok(Map.of("ok",true,"count",admin.roles(currentUser,r.ids(),r.role())));
    }
    @PatchMapping("/users/{id}/active")
    public ApiResponse<?> active(@RequestAttribute CurrentUser currentUser,@PathVariable long id,@Valid @RequestBody OperationsModels.Active r) {
        admin.active(currentUser,id,r.active());return ok();
    }
    @GetMapping("/settings")
    public ApiResponse<?> settings(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(admin.settings(currentUser));}
    @PutMapping("/settings")
    public ApiResponse<?> settings(@RequestAttribute CurrentUser currentUser,@Valid @RequestBody OperationsModels.Settings r) {
        admin.settings(currentUser,r);return ok();
    }
    @GetMapping("/stats/overview")
    public ApiResponse<?> overview(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(reports.overview(currentUser));}
    @GetMapping("/stats/weekly")
    public ApiResponse<?> weekly(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(reports.weekly(currentUser));}
    @GetMapping("/stats/monthly")
    public ApiResponse<?> monthly(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(reports.monthly(currentUser));}
    @GetMapping("/notifications")
    public ApiResponse<?> notices(@RequestAttribute CurrentUser currentUser) {return ApiResponse.ok(reports.notices(currentUser));}
}
