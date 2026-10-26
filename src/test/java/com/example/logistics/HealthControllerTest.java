package com.example.logistics;

import com.example.logistics.api.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {
    @Test void unavailableDatabaseReturns503WithoutLeakingConnectionDetails() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenThrow(new DataAccessResourceFailureException("private connection detail"));
        MockMvcBuilders.standaloneSetup(new HealthController(jdbc)).build().perform(get("/health"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"status\":\"DOWN\"}"));
    }
}
