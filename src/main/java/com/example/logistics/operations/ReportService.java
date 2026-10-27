package com.example.logistics.operations;

import com.example.logistics.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.example.logistics.auth.Permissions.require;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private record Order(String status,Instant time,BigDecimal fee) {}
    private List<Order> orders(CurrentUser u) {
        return jdbc.query("SELECT status,created_at,fee FROM logistics_orders"+("customer".equals(u.role())?" WHERE owner_id=?":""),
                (rs,n)->new Order(rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getBigDecimal("fee")),
                "customer".equals(u.role())?new Object[]{u.id()}:new Object[]{});
    }
    public Map<String,Object> overview(CurrentUser u) {
        List<Order> rows=orders(u); Map<String,Object> result=new LinkedHashMap<>();
        result.put("total",rows.size());
        for(String state:List.of("delivered","exception","pending","cancelled")) result.put(state,rows.stream().filter(o->state.equals(o.status())).count());
        result.put("inTransit",rows.stream().filter(o->Set.of("transit","delivering").contains(o.status())).count());
        result.put("revenue",rows.stream().filter(o->!"cancelled".equals(o.status())).map(Order::fee).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.put("fbCount",jdbc.queryForObject("SELECT COUNT(*) FROM order_feedback f JOIN logistics_orders o ON o.no=f.order_no"+
                ("customer".equals(u.role())?" WHERE o.owner_id=?":""),Long.class,"customer".equals(u.role())?new Object[]{u.id()}:new Object[]{}));
        boolean employee=!"customer".equals(u.role());
        result.put("tasksTodo",employee?jdbc.queryForObject("SELECT COUNT(*) FROM logistics_tasks WHERE status='todo'",Long.class):0);
        result.put("tasksDone",employee?jdbc.queryForObject("SELECT COUNT(*) FROM logistics_tasks WHERE status='done'",Long.class):0);
        result.put("noVoucher",employee?jdbc.queryForObject("SELECT COUNT(DISTINCT t.order_no) FROM logistics_tasks t JOIN logistics_orders o ON o.no=t.order_no WHERE t.type='派送' AND t.status='done' AND o.status='delivered' AND NOT EXISTS(SELECT 1 FROM task_vouchers v JOIN logistics_tasks vt ON vt.id=v.task_id WHERE vt.order_no=t.order_no)",Long.class):0);
        Instant monthStart=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        result.put("monthDeliver",rows.stream().filter(o->!o.time().isBefore(monthStart)&&Set.of("delivering","delivered").contains(o.status())).count());
        return result;
    }
    public List<Map<String,Object>> weekly(CurrentUser u) {
        List<Order> rows=orders(u); List<Map<String,Object>> result=new ArrayList<>();
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(int i=6;i>=0;i--) {
            LocalDate day=today.minusDays(i);
            long count;
            if("staff".equals(u.role())) count=jdbc.queryForObject("SELECT COALESCE(SUM(qty),0) FROM inventory_records WHERE created_at>=? AND created_at<?",Long.class,
                    java.sql.Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC)),java.sql.Timestamp.from(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            else count=rows.stream().filter(o->o.time().atOffset(ZoneOffset.UTC).toLocalDate().equals(day)).count();
            result.add(Map.of("day",i==0?"今天":"周"+"一二三四五六日".charAt(day.getDayOfWeek().getValue()-1),"n",count));
        }
        return result;
    }
    public List<Map<String,Object>> monthly(CurrentUser u) {
        require(u,"admin"); Map<String,List<Order>> grouped=new TreeMap<>();
        for(Order o:orders(u)) grouped.computeIfAbsent(YearMonth.from(o.time().atOffset(ZoneOffset.UTC)).toString(),k->new ArrayList<>()).add(o);
        List<Map<String,Object>> result=new ArrayList<>();
        grouped.forEach((month,rows)->result.add(Map.of("month",month,"count",rows.size(),
                "delivered",rows.stream().filter(o->"delivered".equals(o.status())).count(),
                "exception",rows.stream().filter(o->"exception".equals(o.status())).count(),
                "fee",rows.stream().filter(o->!"cancelled".equals(o.status())).map(Order::fee).reduce(BigDecimal.ZERO,BigDecimal::add))));
        return result;
    }
    public List<Map<String,Object>> notices(CurrentUser u) {
        List<Map<String,Object>> result=new ArrayList<>(jdbc.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 100",(rs,n)->Map.of(
                "id",rs.getLong("id"),"icon","📦","title",rs.getString("title"),"desc",rs.getString("description"),
                "time",rs.getTimestamp("created_at").toInstant(),"read",false,"act",rs.getString("act")),u.id()));
        var settings=jdbc.queryForMap("SELECT notice,mkt_ntf FROM system_settings WHERE id=1");
        if(Boolean.TRUE.equals(settings.get("mkt_ntf")) && !settings.get("notice").toString().isBlank())
            result.add(0,Map.of("id",0,"icon","📢","title","系统公告","desc",settings.get("notice"),"time","当前公告","read",false,"act","home"));
        return result;
    }
}
