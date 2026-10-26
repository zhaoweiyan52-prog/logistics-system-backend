package com.example.logistics;

import com.example.logistics.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoreApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private String username() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0,12); }
    @Test void publicDeploymentReadinessChecksRealTablesAndLandingRedirects() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/logistics-admin.html"));
    }
    private JsonNode postJson(String path, Object body, String token) throws Exception {
        var req = post(path).contentType("application/json").content(json.writeValueAsBytes(body));
        if (token != null) req.header("Authorization","Bearer " + token);
        return read(mvc.perform(req).andExpect(status().isOk()).andReturn());
    }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private String register() throws Exception {
        JsonNode r=postJson("/api/auth/register",Map.of("username",username(),"password","Secret123!","role","customer"),null);
        assertThat(r.path("code").asInt()).isZero();
        return r.path("data").path("token").asText();
    }
    private Map<String,Object> order(String receiver) {
        return Map.of("receiver",receiver,"dest","上海市浦东新区某大学","goods","图书文具","weight",1.25,"fee",16.00);
    }
    private JsonNode create(String token,String receiver) throws Exception {
        JsonNode r = postJson("/api/orders",order(receiver),token);
        assertThat(r.path("code").asInt()).isZero();
        return r.path("data");
    }
    private JsonNode getJson(String path,String token) throws Exception {
        return read(mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn());
    }

    @Test void registrationLogsInAndHashesPasswordAndToken() throws Exception {
        String name=username();
        JsonNode r=postJson("/api/auth/register", Map.of("username",name,"password","Secret123!","role","customer"),null);
        assertThat(r.path("code").asInt()).isZero();
        JsonNode data=r.path("data");
        assertThat(data.path("account").asText()).isEqualTo(name);
        assertThat(data.path("role").asText()).isEqualTo("customer");
        assertThat(data.path("name").asText()).isNotBlank();
        assertThat(data.path("token").asText()).hasSize(43);
        String hash=jdbc.queryForObject("SELECT password_hash FROM app_users WHERE account=?",String.class,name);
        assertThat(hash).startsWith("$2a$12$").isNotEqualTo("Secret123!");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens WHERE token_hash=?",Long.class,
                AuthService.hash(data.path("token").asText()))).isEqualTo(1);
        assertThat(data.has("password_hash")).isFalse();
        assertThat(getJson("/api/orders",data.path("token").asText()).path("code").asInt()).isZero();
    }

    @Test void duplicateRegistrationDoesNotOverwriteAccount() throws Exception {
        String name=username();
        Map<String,String> body=Map.of("username",name,"password","Secret123!","role","customer");
        postJson("/api/auth/register",body,null);
        assertThat(postJson("/api/auth/register",body,null).path("code").asInt()).isEqualTo(40901);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE account=?",Long.class,name)).isEqualTo(1);
    }

    @Test void rejectsAdminAndUnknownRole() throws Exception {
        for(String role:new String[]{"admin","boss"}) {
            JsonNode r=postJson("/api/auth/register",Map.of("username",username(),"password","Secret123!","role",role),null);
            assertThat(r.path("code").asInt()).isEqualTo(40002);
        }
    }

    @Test void allowsStaffAsContractSpecifies() throws Exception {
        JsonNode r=postJson("/api/auth/register",Map.of("username",username(),"password","Secret123!","role","staff"),null);
        assertThat(r.path("code").asInt()).isZero();
        assertThat(r.path("data").path("role").asText()).isEqualTo("staff");
    }

    @Test void validatesUsernameAndPasswordBounds() throws Exception {
        for(String name:new String[]{"abc","bad-name","abcdefghijklmnopq","用户1234"}) {
            assertThat(postJson("/api/auth/register",Map.of("username",name,"password","Secret123!","role","customer"),null)
                    .path("code").asInt()).isEqualTo(40001);
        }
        for(String password:new String[]{"12345","a".repeat(21)}) {
            assertThat(postJson("/api/auth/register",Map.of("username",username(),"password",password,"role","customer"),null)
                    .path("code").asInt()).isEqualTo(40001);
        }
    }

    @Test void acceptsPasswordLengthBoundariesAndUsesDifferentSalts() throws Exception {
        String first=username(), second=username();
        for(String name:new String[]{first,second}) {
            assertThat(postJson("/api/auth/register",Map.of("username",name,"password","123456","role","customer"),null)
                    .path("code").asInt()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE account=?",String.class,first))
                .isNotEqualTo(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE account=?",String.class,second));
        assertThat(postJson("/api/auth/register",Map.of("username",username(),"password","a".repeat(20),"role","customer"),null)
                .path("code").asInt()).isZero();
    }

    @Test void loginAcceptsCorrectPasswordRejectsWrongAndUnknown() throws Exception {
        String name=username();
        postJson("/api/auth/register",Map.of("username",name,"password","Secret123!","role","customer"),null);
        JsonNode success=postJson("/api/auth/login",Map.of("username",name,"password","Secret123!"),null);
        assertThat(success.path("code").asInt()).isZero();
        assertThat(success.path("data").path("token").asText()).hasSize(43);
        assertThat(postJson("/api/auth/login",Map.of("username",name,"password","Wrong123!"),null).path("code").asInt()).isEqualTo(40101);
        assertThat(postJson("/api/auth/login",Map.of("username",username(),"password","Secret123!"),null).path("code").asInt()).isEqualTo(40101);
    }

    @Test void allBusinessPathsRejectMissingOrForgedTokens() throws Exception {
        for(String path:new String[]{"/api/orders","/api/orders/unknown","/api/users","/api/stats/overview","/api;ignored/orders"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
            mvc.perform(get(path).header("Authorization","Bearer mock.admin.123"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIsUnauthorized() throws Exception {
        String token=register();
        jdbc.update("UPDATE auth_tokens SET expires_at=? WHERE token_hash=?",Timestamp.from(Instant.now().minusSeconds(1)),AuthService.hash(token));
        mvc.perform(get("/api/orders").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

    @Test void logoutInvalidatesOnlyItsOwnToken() throws Exception {
        String a=register(),b=register();
        assertThat(postJson("/api/auth/logout",Map.of(),a).path("data").path("ok").asBoolean()).isTrue();
        mvc.perform(get("/api/orders").header("Authorization","Bearer "+a)).andExpect(status().isUnauthorized());
        assertThat(getJson("/api/orders",b).path("code").asInt()).isZero();
    }

    @Test void savesOrderFieldsAndInitialTraceWithIsoTimes() throws Exception {
        String token=register();
        JsonNode o=create(token,"张同学");
        assertThat(o.path("no").asText()).matches("SF[0-9]{8}[A-F0-9]{32}");
        assertThat(o.path("status").asText()).isEqualTo("pending");
        assertThat(o.path("weight").decimalValue()).isEqualByComparingTo("1.25");
        assertThat(o.path("fee").decimalValue()).isEqualByComparingTo("16.00");
        assertThat(o.path("receiver").asText()).isEqualTo("张同学");
        assertThat(o.path("signCode").asText()).matches("[0-9]{4}");
        assertThat(o.path("fb").isNull()).isTrue();
        assertThat(o.path("traces")).hasSize(1);
        assertThat(o.path("traces").get(0).path("done").asBoolean()).isTrue();
        assertThat(Instant.parse(o.path("eta").asText())).isEqualTo(Instant.parse(o.path("time").asText()).plusSeconds(3*86400));
        assertThat(Instant.parse(o.path("traces").get(0).path("t").asText())).isEqualTo(Instant.parse(o.path("time").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM logistics_orders WHERE no=?",Long.class,o.path("no").asText())).isEqualTo(1);
        assertThat(getJson("/api/orders/"+o.path("no").asText(),token).path("data")).isEqualTo(o);
    }

    @Test void rejectsCrossCustomerListDetailAndForgedIdentity() throws Exception {
        String a=register(),b=register();
        JsonNode o=create(a,"甲同学");
        String no=o.path("no").asText();
        assertThat(getJson("/api/orders",b).path("data").path("total").asLong()).isZero();
        assertThat(getJson("/api/orders?kw="+no,b).path("data").path("list")).isEmpty();
        assertThat(getJson("/api/orders/"+no,b).path("code").asInt()).isEqualTo(40401);
        var forged=new java.util.HashMap<String,Object>(order("伪造"));
        forged.put("owner_id",1); forged.put("role","admin");
        assertThat(postJson("/api/orders",forged,b).path("code").asInt()).isEqualTo(40001);
        assertThat(getJson("/api/orders?owner_id=1&role=admin",b).path("data").path("total").asLong()).isZero();
    }

    @Test void paginationFilteringAndDescendingOrder() throws Exception {
        String token=register();
        create(token,"甲");create(token,"乙");JsonNode latest=create(token,"丙");
        JsonNode first=getJson("/api/orders?page=1&size=2",token).path("data");
        assertThat(first.path("total").asLong()).isEqualTo(3);
        assertThat(first.path("list")).hasSize(2);
        assertThat(first.path("list").get(0).path("no").asText()).isEqualTo(latest.path("no").asText());
        assertThat(getJson("/api/orders?page=2&size=2",token).path("data").path("list")).hasSize(1);
        assertThat(getJson("/api/orders?page=99",token).path("data").path("list")).isEmpty();
        assertThat(getJson("/api/orders?kw="+latest.path("no").asText()+"&status=pending",token).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(getJson("/api/orders?status=delivered",token).path("data").path("total").asLong()).isZero();
        assertThat(getJson("/api/orders?kw=%25",token).path("data").path("total").asLong()).isZero();
    }

    @Test void invalidPaginationAndStatusReturnBusinessErrors() throws Exception {
        String token=register();
        for(String query:new String[]{"page=0","page=-1","size=0","size=101","page=oops","status=unknown"}) {
            assertThat(getJson("/api/orders?"+query,token).path("code").asInt()).isEqualTo(40001);
        }
    }

    @Test void validatesOrderAndRejectsMalformedJson() throws Exception {
        String token=register();
        for(Map.Entry<String,Object> invalid:Map.<String,Object>of("receiver"," ","weight",-1,"fee",-1,"dest","","goods","").entrySet()) {
            var body=new java.util.HashMap<String,Object>(order("同学"));body.put(invalid.getKey(),invalid.getValue());
            assertThat(postJson("/api/orders",body,token).path("code").asInt()).isEqualTo(40001);
        }
        var precision=new java.util.HashMap<String,Object>(order("同学"));precision.put("fee",12.345);
        assertThat(postJson("/api/orders",precision,token).path("code").asInt()).isEqualTo(40001);
        mvc.perform(post("/api/orders").header("Authorization","Bearer "+token).contentType("application/json").content("{bad"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40001));
        assertThat(getJson("/api/orders",token).path("data").path("total").asLong()).isZero();
    }

    @Test void missingAndOtherUsersOrdersHaveSameResponse() throws Exception {
        String a=register(),b=register();String no=create(a,"甲").path("no").asText();
        assertThat(getJson("/api/orders/"+no,b)).isEqualTo(getJson("/api/orders/unknown",b));
    }

    @Test void unsupportedStageTwoReturnsExplicitMessage() throws Exception {
        String token=register();
        JsonNode r=getJson("/api/stats/overview",token);
        assertThat(r.path("code").asInt()).isEqualTo(50101);
        assertThat(r.path("message").asText()).contains("第一阶段");
        assertThat(postJson("/api/orders/unknown/sign",Map.of("code","1234"),token).path("code").asInt()).isEqualTo(50101);
    }

    @Test void corsPreflightAllowsLocalFrontendBeforeAuth() throws Exception {
        mvc.perform(options("/api/orders").header("Origin","http://localhost:5500")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5500"));
        mvc.perform(options("/api/orders").header("Origin","https://untrusted.invalid")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
