package io.github.swampus.controller;

import io.github.swampus.oracle.OracleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OracleController.class)
@Import(OracleConfiguration.class)
@TestPropertySource(properties = {"QUANTUM_ORACLE_ENABLED=false", "QUANTUM_PYTHON_EXEC=/does/not/exist"})
class OracleDisabledTest {
    @Autowired MockMvc mvc;
    @Test void disabledRejectsWithoutLaunchingPython() throws Exception {
        mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(OracleControllerTest.REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("disabled")));
    }
}
