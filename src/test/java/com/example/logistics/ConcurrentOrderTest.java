package com.example.logistics;

import com.example.logistics.auth.*;
import com.example.logistics.order.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentOrderTest {
    @Autowired AuthService auth;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;

    @Test void simultaneousOrdersRemainUniqueAndBelongToTheirCreator() throws Exception {
        String name="c"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        var login=auth.register(name,"Secret123!","customer");
        CurrentUser user=auth.authenticate(login.token());
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            var input=new OrderModels.CreateOrder("同学","某大学","图书",BigDecimal.ONE,new BigDecimal("12.00"));
            List<Callable<String>> jobs=new ArrayList<>();
            for(int i=0;i<40;i++) jobs.add(()->orders.create(user,input).no());
            Set<String> numbers=new HashSet<>();
            for(Future<String> result:pool.invokeAll(jobs)) assertThat(numbers.add(result.get(30,TimeUnit.SECONDS))).isTrue();
            assertThat(numbers).hasSize(40);
            assertThat(orders.list(user,"","",1,100).total()).isEqualTo(40);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_traces t JOIN logistics_orders o ON t.order_no=o.no WHERE o.owner_id=?",Long.class,user.id())).isEqualTo(40);
        } finally { pool.shutdownNow(); }
    }

    @Test void simultaneousDuplicateRegistrationCreatesOnlyOneUser() throws Exception {
        String name="r"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs=new ArrayList<>();
            for(int i=0;i<4;i++) jobs.add(()->{
                try { auth.register(name,"Secret123!","customer");return true; }
                catch(com.example.logistics.api.BusinessException e) { assertThat(e.code).isEqualTo(40901);return false; }
            });
            int success=0;
            for(Future<Boolean> result:pool.invokeAll(jobs)) if(result.get(30,TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE account=?",Long.class,name)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
