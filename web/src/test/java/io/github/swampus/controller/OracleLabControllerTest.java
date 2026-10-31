package io.github.swampus.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OracleLabController.class)
class OracleLabControllerTest {
    @Autowired MockMvc mvc;
    @Test void directoryUrlForwardsToStaticDemo() throws Exception {
        for (String path : new String[]{"/oracle-lab", "/oracle-lab/"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/oracle-lab/index.html"));
        mvc.perform(get("/oracle-lab/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quantum Oracle Lab")));
    }
}
