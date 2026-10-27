package com.example.logistics.operations;

import com.example.logistics.api.BusinessException;
import com.example.logistics.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.example.logistics.auth.Permissions.require;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> warehouses(CurrentUser u) {
        require(u,"staff","admin");
        return jdbc.query("SELECT * FROM warehouses ORDER BY name",(rs,n)->Map.of("name",rs.getString("name"),"used",rs.getLong("used"),"cap",rs.getLong("cap")));
    }
    public List<Map<String,Object>> list(CurrentUser u,String type) {
        require(u,"staff","admin");
        if(!type.isEmpty() && !Set.of("in","out").contains(type)) throw new BusinessException(40001,"出入库类型不正确");
        return jdbc.query("SELECT r.*,u.name AS operator_name FROM inventory_records r JOIN app_users u ON u.id=r.operator_id"+
                (type.isEmpty()?"":" WHERE r.type=?")+" ORDER BY r.created_at DESC,r.no DESC",(rs,n)->Map.of(
                "no",rs.getString("no"),"goods",rs.getString("goods"),"qty",rs.getLong("qty"),"wh",rs.getString("wh"),
                "op",rs.getString("operator_name"),"time",rs.getTimestamp("created_at").toInstant(),"type",rs.getString("type")),type.isEmpty()?new Object[]{}:new Object[]{type});
    }
    @Transactional
    public Map<String,Object> create(CurrentUser u,OperationsModels.Inventory r) {
        require(u,"staff"); String wh=r.wh().trim(), goods=r.goods().trim();
        var rows=jdbc.queryForList("SELECT * FROM warehouses WHERE name=? FOR UPDATE",wh);
        if(rows.isEmpty()) throw new BusinessException(40404,"仓库不存在，请选择已配置的仓库");
        var w=rows.get(0);
        var stock=jdbc.queryForList("SELECT qty FROM warehouse_stock WHERE wh=? AND goods=?",wh,goods);
        long qty=stock.isEmpty()?0:((Number)stock.get(0).get("qty")).longValue();
        long delta="in".equals(r.type())?r.qty():-r.qty();
        long used=((Number)w.get("used")).longValue()+delta;
        if(qty+delta<0) throw new BusinessException(40909,"该仓库此物品库存不足");
        if(used>((Number)w.get("cap")).longValue()) throw new BusinessException(40910,"仓库剩余容量不足");
        if(stock.isEmpty()) jdbc.update("INSERT INTO warehouse_stock(wh,goods,qty) VALUES (?,?,?)",wh,goods,qty+delta);
        else jdbc.update("UPDATE warehouse_stock SET qty=? WHERE wh=? AND goods=?",qty+delta,wh,goods);
        jdbc.update("UPDATE warehouses SET used=? WHERE name=?",used,wh);
        String no=(delta>0?"IN":"OUT")+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        Instant now=Instant.now();
        jdbc.update("INSERT INTO inventory_records(no,goods,qty,wh,operator_id,created_at,type) VALUES (?,?,?,?,?,?,?)",no,goods,r.qty(),wh,u.id(),Timestamp.from(now),r.type());
        return Map.of("no",no,"goods",goods,"qty",r.qty(),"wh",wh,"op",u.name(),"time",now,"type",r.type());
    }
}
