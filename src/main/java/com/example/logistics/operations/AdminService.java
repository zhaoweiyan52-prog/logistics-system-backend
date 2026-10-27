package com.example.logistics.operations;

import com.example.logistics.api.BusinessException;
import com.example.logistics.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.example.logistics.auth.Permissions.require;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private static final Map<String,String> ROLES=Map.of("客户","customer","物流员工","staff","系统管理员","admin",
            "customer","customer","staff","staff","admin","admin");
    public AdminService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static String pattern(String kw) { return "%"+kw.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
    private String role(String role) {
        String key=ROLES.get(role);
        if(key==null) throw new BusinessException(40001,"角色不正确");
        return key;
    }
    public Map<String,Object> users(CurrentUser user,String kw,String role,int page,int size) {
        require(user,"admin"); WorkflowService.page(page,size);
        if(kw.length()>200) throw new BusinessException(40001,"搜索关键词不能超过 200 字");
        String where=" WHERE (u.name LIKE ? ESCAPE '!' OR u.account LIKE ? ESCAPE '!')";
        List<Object> args=new ArrayList<>(List.of(pattern(kw),pattern(kw)));
        if(!role.isEmpty()) {where+=" AND u.role=?";args.add(role(role));}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM app_users u"+where,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        var list=jdbc.query("""
                SELECT u.*,COALESCE(a.active,true) AS enabled,
                (SELECT COUNT(*) FROM logistics_orders o WHERE o.owner_id=u.id) AS order_count
                FROM app_users u LEFT JOIN user_access a ON a.user_id=u.id
                """+where+" ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?",(rs,n)->Map.of(
                "id",rs.getLong("id"),"name",rs.getString("name"),"account",rs.getString("account"),
                "role",Map.of("customer","客户","staff","物流员工","admin","系统管理员").get(rs.getString("role")),
                "active",rs.getBoolean("enabled"),"orderCount",rs.getLong("order_count"),"created",rs.getTimestamp("created_at").toInstant()),args.toArray());
        return Map.of("list",list,"total",total);
    }
    public void lockAdministration() {
        jdbc.queryForObject("SELECT id FROM system_settings WHERE id=1 FOR UPDATE",Integer.class);
    }
    @Transactional
    public int roles(CurrentUser user,List<Long> ids,String role) {
        require(user,"admin"); lockAdministration();
        String next=role(role); Set<Long> unique=new TreeSet<>(ids);
        for(long id:unique) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=?",Long.class,id)==0)
                throw new BusinessException(40403,"用户不存在");
        }
        for(long id:unique) {
            var old=jdbc.queryForList("SELECT role FROM app_users WHERE id=? FOR UPDATE",id);
            if(old.isEmpty()) throw new BusinessException(40403,"用户不存在");
            if(!next.equals(old.get(0).get("role"))) {
                protectLastAdmin(id,next,false);
                jdbc.update("UPDATE app_users SET role=? WHERE id=?",next,id);
                jdbc.update("DELETE FROM auth_tokens WHERE user_id=?",id);
            }
        }
        return unique.size();
    }
    private void protectLastAdmin(long id,String next,boolean disable) {
        String old=jdbc.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,id);
        Boolean active=jdbc.queryForObject("SELECT COALESCE((SELECT active FROM user_access WHERE user_id=?),true)",Boolean.class,id);
        if("admin".equals(old) && Boolean.TRUE.equals(active) && (disable || !"admin".equals(next))) {
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM app_users u LEFT JOIN user_access a ON a.user_id=u.id WHERE u.role='admin' AND COALESCE(a.active,true)=true",Long.class);
            if(count<=1) throw new BusinessException(40908,"不能禁用或降级最后一位启用的管理员");
        }
    }
    @Transactional
    public void active(CurrentUser user,long id,boolean active) {
        require(user,"admin"); lockAdministration();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=?",Long.class,id)==0) throw new BusinessException(40403,"用户不存在");
        protectLastAdmin(id,"admin",!active);
        if(jdbc.update("UPDATE user_access SET active=? WHERE user_id=?",active,id)==0)
            jdbc.update("INSERT INTO user_access(user_id,active) VALUES (?,?)",id,active);
        if(!active) jdbc.update("DELETE FROM auth_tokens WHERE user_id=?",id);
    }
    public Map<String,Object> settings(CurrentUser user) {
        var r=jdbc.queryForMap("SELECT * FROM system_settings WHERE id=1");
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("name",r.get("name"));data.put("phone",r.get("phone"));data.put("notice",r.get("notice"));
        data.put("first",r.get("first_weight"));data.put("firstFee",r.get("first_fee"));data.put("addFee",r.get("add_fee"));
        data.put("signNtf",r.get("sign_ntf"));data.put("excNtf",r.get("exc_ntf"));data.put("mktNtf",r.get("mkt_ntf"));
        return data;
    }
    @Transactional
    public void settings(CurrentUser user,OperationsModels.Settings r) {
        require(user,"admin"); lockAdministration();
        jdbc.update("UPDATE system_settings SET name=?,phone=?,notice=?,first_weight=?,first_fee=?,add_fee=?,sign_ntf=?,exc_ntf=?,mkt_ntf=? WHERE id=1",
                r.name().trim(),r.phone().trim(),r.notice().trim(),r.first(),r.firstFee(),r.addFee(),r.signNtf(),r.excNtf(),r.mktNtf());
    }
}
