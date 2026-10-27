package com.example.logistics.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        try {
            // Check readiness without exposing user data or assuming tables contain rows.
            for (String table : new String[]{"app_users","auth_tokens","logistics_orders","order_traces",
                    "user_access","order_feedback","logistics_tasks","task_vouchers","warehouses",
                    "warehouse_stock","inventory_records","system_settings","notifications"}) {
                jdbc.queryForList("SELECT 1 FROM " + table + " LIMIT 1");
            }
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
