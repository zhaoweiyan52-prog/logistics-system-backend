package com.example.logistics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"server.forward-headers-strategy=framework",
        "spring.datasource.url=jdbc:h2:mem:proxy_cors;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ProxyCorsTest {
    @Autowired MockMvc mvc;
    private static final String HOST="logistics-system-backend-o7if.onrender.com";

    @Test void sameOriginHttpsLoginBehindProxyReachesAuthentication() throws Exception {
        mvc.perform(post("/api/auth/login").with(req->{req.setScheme("http");req.setServerName("service-internal");req.setServerPort(10000);return req;})
                .header("Origin","https://"+HOST).header("X-Forwarded-Proto","https")
                .header("X-Forwarded-Host",HOST).header("X-Forwarded-Port","443")
                .contentType("application/json").content("{\"username\":\"not_registered\",\"password\":\"TestOnly123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40101));
    }
    @Test void untrustedBrowserOriginIsStillRejectedBehindProxy() throws Exception {
        mvc.perform(post("/api/auth/login").with(req->{req.setScheme("http");req.setServerName("service-internal");req.setServerPort(10000);return req;})
                .header("Origin","https://untrusted.invalid").header("X-Forwarded-Proto","https")
                .header("X-Forwarded-Host",HOST).header("X-Forwarded-Port","443")
                .contentType("application/json").content("{\"username\":\"not_registered\",\"password\":\"TestOnly123!\"}"))
                .andExpect(status().isForbidden());
    }
}
