package com.example.logistics;

import com.example.logistics.auth.*;
import com.example.logistics.operations.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FullWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AdminService admins;
    @Autowired WorkflowService workflows;
    private String account() {return "f"+UUID.randomUUID().toString().replace("-","").substring(0,12);}
    private String user(String role) {
        String name=account();var login=auth.register(name,"TestOnly123!","customer");
        jdbc.update("UPDATE app_users SET role=? WHERE account=?",role,name);return login.token();
    }
    private JsonNode req(String method,String path,Object body,String token) throws Exception {
        var request=switch(method) {case "POST"->post(path);case "PATCH"->patch(path);case "PUT"->put(path);default->get(path);};
        request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode create(String token) throws Exception {
        return req("POST","/api/orders",Map.of("receiver","测试同学","dest","某大学","goods","图书","weight",1,"fee",12),token).path("data");
    }
    private long task(String no) {
        return jdbc.queryForObject("SELECT id FROM logistics_tasks WHERE order_no=? AND status='todo'",Long.class,no);
    }
    private JsonNode update(String staff,long id,String state) throws Exception {
        return req("POST","/api/tasks/"+id+"/status",Map.of("status",state,"note","测试节点"),staff);
    }
    @Test void customerStaffSignAndFeedbackPersistWithPrivateNotifications() throws Exception {
        String customer=user("customer"),staff=user("staff"),other=user("customer");
        var order=create(customer);String no=order.path("no").asText();
        assertThat(update(staff,task(no),"delivered").path("code").asInt()).isEqualTo(40905);
        long first=task(no);
        assertThat(update(staff,first,"transit").path("code").asInt()).isZero();
        assertThat(update(staff,first,"transit").path("code").asInt()).isEqualTo(40904);
        assertThat(update(staff,task(no),"delivering").path("code").asInt()).isZero();
        assertThat(req("POST","/api/orders/"+no+"/sign",Map.of("code",order.path("signCode").asText()),other).path("code").asInt()).isEqualTo(40401);
        String wrong=order.path("signCode").asText().equals("0000")?"0001":"0000";
        assertThat(req("POST","/api/orders/"+no+"/sign",Map.of("code",wrong),customer).path("code").asInt()).isEqualTo(40003);
        var signed=req("POST","/api/orders/"+no+"/sign",Map.of("code",order.path("signCode").asText(),"by","本人"),customer);
        assertThat(signed.path("data").path("status").asText()).isEqualTo("delivered");
        assertThat(signed.path("data").path("traces")).hasSize(4);
        var body=Map.of("type","服务态度","content","配送很好");
        assertThat(req("POST","/api/orders/"+no+"/feedback",body,customer).path("code").asInt()).isZero();
        assertThat(req("POST","/api/orders/"+no+"/feedback",body,customer).path("code").asInt()).isEqualTo(40903);
        assertThat(req("GET","/api/orders/"+no,null,customer).path("data").path("fb").path("content").asText()).isEqualTo("配送很好");
        assertThat(req("GET","/api/notifications",null,customer).path("data")).hasSize(1);
        assertThat(req("GET","/api/notifications",null,other).path("data")).isEmpty();
    }
    @Test void everyManagementRouteRejectsCustomerEvenWithForgedRoleQuery() throws Exception {
        String customer=user("customer");
        for(String path:List.of("/api/users?role=admin","/api/tasks","/api/inventory","/api/warehouses","/api/stats/monthly"))
            assertThat(req("GET",path,null,customer).path("code").asInt()).isEqualTo(40301);
        assertThat(req("POST","/api/tasks/1/status",Map.of("status","transit"),customer).path("code").asInt()).isEqualTo(40301);
        assertThat(req("PATCH","/api/users/1/role",Map.of("role","admin"),customer).path("code").asInt()).isEqualTo(40301);
    }
    @Test void staffAndAdminSeeGlobalOrdersButCustomersRemainIsolated() throws Exception {
        String c=user("customer"),other=user("customer"),staff=user("staff"),admin=user("admin");
        String no=create(c).path("no").asText();
        assertThat(req("GET","/api/orders/"+no,null,other).path("code").asInt()).isEqualTo(40401);
        for(String token:List.of(staff,admin)) assertThat(req("GET","/api/orders/"+no,null,token).path("code").asInt()).isZero();
        assertThat(req("POST","/api/orders",Map.of("receiver","甲","dest","乙","goods","书","weight",1,"fee",12),staff).path("code").asInt()).isEqualTo(40301);
    }
    @Test void inventoryChecksSpecificGoodsCapacityAndSavesOperator() throws Exception {
        String staff=user("staff"),wh="仓"+account();
        jdbc.update("INSERT INTO warehouses(name,used,cap) VALUES (?,0,10)",wh);
        Map<String,Object> body=new HashMap<>(Map.of("type","in","goods","图书","qty",8,"wh",wh));
        assertThat(req("POST","/api/inventory",body,staff).path("code").asInt()).isZero();
        body.put("qty",3);
        assertThat(req("POST","/api/inventory",body,staff).path("code").asInt()).isEqualTo(40910);
        body.put("type","out");body.put("goods","食品");body.put("qty",1);
        assertThat(req("POST","/api/inventory",body,staff).path("code").asInt()).isEqualTo(40909);
        body.put("goods","图书");body.put("qty",8);
        assertThat(req("POST","/api/inventory",body,staff).path("code").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT used FROM warehouses WHERE name=?",Long.class,wh)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_records WHERE wh=?",Long.class,wh)).isEqualTo(2);
    }
    @Test void disablingAccountRevokesSessionsAndBlocksLoginUntilEnabled() throws Exception {
        String admin=user("admin"),target=user("customer");CurrentUser u=auth.authenticate(target);
        assertThat(req("PATCH","/api/users/"+u.id()+"/active",Map.of("active",false),admin).path("code").asInt()).isZero();
        mvc.perform(get("/api/orders").header("Authorization","Bearer "+target)).andExpect(status().isUnauthorized());
        assertThat(req("POST","/api/auth/login",Map.of("username",u.account(),"password","TestOnly123!"),target).path("code").asInt()).isEqualTo(40302);
        assertThat(req("PATCH","/api/users/"+u.id()+"/active",Map.of("active",true),admin).path("code").asInt()).isZero();
        assertThat(auth.login(u.account(),"TestOnly123!").role()).isEqualTo("customer");
    }
    @Test void roleChangesAreAtomicAcceptChineseRolesAndInvalidateOldTokens() throws Exception {
        String admin=user("admin"),target=user("customer");long id=auth.authenticate(target).id();
        assertThat(req("POST","/api/users/batch-role",Map.of("role","物流员工","ids",List.of(id,Long.MAX_VALUE)),admin).path("code").asInt()).isEqualTo(40403);
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,id)).isEqualTo("customer");
        assertThat(req("PATCH","/api/users/"+id+"/role",Map.of("role","物流员工"),admin).path("code").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,id)).isEqualTo("staff");
        assertThat(auth.authenticate(target)).isNull();
    }
    @Test void lastEnabledAdministratorCannotBeRemoved() throws Exception {
        String token=user("admin");CurrentUser u=auth.authenticate(token);
        jdbc.update("UPDATE app_users SET role='customer' WHERE role='admin' AND id<>?",u.id());
        assertThat(req("PATCH","/api/users/"+u.id()+"/role",Map.of("role","客户"),token).path("code").asInt()).isEqualTo(40908);
        assertThat(req("PATCH","/api/users/"+u.id()+"/active",Map.of("active",false),token).path("code").asInt()).isEqualTo(40908);
    }
    @Test void settingsArePersistentAdminOnlyAndAvailableForCustomerPricing() throws Exception {
        String admin=user("admin"),customer=user("customer");
        var settings=json.convertValue(req("GET","/api/settings",null,admin).path("data"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        settings.put("name","测试物流");settings.put("firstFee",15);
        assertThat(req("PUT","/api/settings",settings,customer).path("code").asInt()).isEqualTo(40301);
        assertThat(req("PUT","/api/settings",settings,admin).path("code").asInt()).isZero();
        assertThat(req("GET","/api/settings",null,customer).path("data").path("firstFee").asInt()).isEqualTo(15);
    }
    @Test void reportsAreRealScopedAndUseNumericAmounts() throws Exception {
        String customer=user("customer"),other=user("customer"),admin=user("admin");create(customer);create(customer);create(other);
        var stats=req("GET","/api/stats/overview",null,customer).path("data");
        assertThat(stats.path("total").asInt()).isEqualTo(2);assertThat(stats.path("revenue").decimalValue()).isEqualByComparingTo("24");
        assertThat(req("GET","/api/stats/weekly",null,customer).path("data")).hasSize(7);
        assertThat(req("GET","/api/stats/monthly",null,admin).path("data").get(0).path("fee").isNumber()).isTrue();
    }
    @Test void voucherBytesAreStoredAndBadFilesAndDuplicateUploadsRejected() throws Exception {
        String customer=user("customer"),staff=user("staff");String no=create(customer).path("no").asText();
        update(staff,task(no),"transit");update(staff,task(no),"delivering");long id=task(no);
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var bad=new MockMultipartFile("files","fake.png","image/png","<script>bad</script>".getBytes());
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(bad).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(40001));
        var file=new MockMultipartFile("files","proof.png","image/png",png);
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(file).header("Authorization","Bearer "+customer)).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(file).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(0));
        assertThat(jdbc.queryForObject("SELECT content FROM task_vouchers WHERE task_id=?",byte[].class,id)).isEqualTo(png);
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(file).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(40907));
        assertThat(jdbc.queryForObject("SELECT status FROM logistics_orders WHERE no=?",String.class,no)).isEqualTo("delivering");
    }
    @Test void voucherSupportsContractArrayNameAndRejectsTruncatedImage() throws Exception {
        String customer=user("customer"),staff=user("staff");String no=create(customer).path("no").asText();
        update(staff,task(no),"transit");update(staff,task(no),"delivering");long id=task(no);
        var truncated=new MockMultipartFile("files[]","broken.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0});
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(truncated).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(40001));
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var file=new MockMultipartFile("files[]","proof.png","image/png",png);
        mvc.perform(multipart("/api/tasks/"+id+"/voucher").file(file).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(0));
    }
}
