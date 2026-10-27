package com.example.logistics.order;

import com.example.logistics.api.BusinessException;
import com.example.logistics.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.example.logistics.order.OrderModels.*;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private static final Set<String> STATUSES = Set.of("pending","transit","delivering","delivered","exception","cancelled");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    public OrderService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public OrderView create(CurrentUser user, CreateOrder r) {
        com.example.logistics.auth.Permissions.require(user,"customer");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // 随机 UUID 防止多进程、重启后的序列冲突；数据库唯一键是最终保障。
        String no = "SF" + DATE.format(now) + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO logistics_orders(no,owner_id,receiver,dest,goods,weight,fee,status,created_at,eta,sign_code)
                VALUES (?,?,?,?,?,?,?,'pending',?,?,?)
                """, no, user.id(), r.receiver().trim(), r.dest().trim(), r.goods().trim(), r.weight(), r.fee(),
                Timestamp.from(now), Timestamp.from(now.plus(3, ChronoUnit.DAYS)), String.valueOf(1000+random.nextInt(9000)));
        jdbc.update("INSERT INTO order_traces(order_no,event_time,txt,done) VALUES (?,?,?,?)",
                no, Timestamp.from(now), "寄件成功，等待揽收", true);
        jdbc.update("INSERT INTO logistics_tasks(order_no,type,addr,created_at,status) VALUES (?,'上门揽收',?,?,'todo')",
                no,r.dest().trim(),Timestamp.from(now));
        return detail(user, no);
    }

    @Transactional(readOnly=true)
    public OrderView detail(CurrentUser user, String no) {
        // 归属条件直接进入 SQL，不能依赖浏览器菜单或前端传来的 ownerId/role。
        List<OrderView> rows = "customer".equals(user.role())
                ? query("SELECT * FROM logistics_orders WHERE no=? AND owner_id=?", no, user.id())
                : query("SELECT * FROM logistics_orders WHERE no=?",no);
        if (rows.isEmpty()) throw new BusinessException(40401, "运单不存在或无权查看");
        return rows.get(0);
    }

    @Transactional(readOnly=true)
    public OrderPage list(CurrentUser user, String kw, String status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(40001, "page 从 1 开始，size 为 1-100");
        if (kw.length() > 200) throw new BusinessException(40001, "搜索关键词不能超过 200 字");
        if (!status.isEmpty() && !STATUSES.contains(status)) throw new BusinessException(40001, "订单状态不正确");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if("customer".equals(user.role())) {where.append(" AND owner_id=?");args.add(user.id());}
        if (!kw.isBlank()) {
            where.append(" AND (no LIKE ? ESCAPE '!' OR goods LIKE ? ESCAPE '!')");
            String pattern = "%" + kw.replace("!","!!").replace("%","!%").replace("_","!_") + "%";
            for (int i=0;i<2;i++) args.add(pattern);
        }
        if (!status.isEmpty()) { where.append(" AND status=?"); args.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM logistics_orders" + where, Long.class, args.toArray());
        args.add(size);
        args.add(((long)page-1)*size);
        return new OrderPage(query("SELECT * FROM logistics_orders" + where + " ORDER BY created_at DESC,no DESC LIMIT ? OFFSET ?",
                args.toArray()), total == null ? 0 : total);
    }

    private List<OrderView> query(String sql, Object... args) {
        return jdbc.query(sql, (rs,n) -> {
            String no = rs.getString("no");
            List<Trace> traces = jdbc.query("SELECT event_time,txt,done FROM order_traces WHERE order_no=? ORDER BY event_time,id",
                    (t,i) -> new Trace(t.getTimestamp("event_time").toInstant(),t.getString("txt"),t.getBoolean("done")), no);
            var feedback=jdbc.query("SELECT * FROM order_feedback WHERE order_no=?",(f,i)->Map.of(
                    "type",f.getString("type"),"content",f.getString("content"),"time",f.getTimestamp("created_at").toInstant(),"reply",f.getString("reply")),no);
            return new OrderView(no,rs.getString("goods"),rs.getString("dest"),rs.getString("receiver"),
                    rs.getBigDecimal("fee"),rs.getBigDecimal("weight"),rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("eta").toInstant(),
                    rs.getString("sign_code"), traces, feedback.isEmpty()?null:feedback.get(0));
        }, args);
    }
}
