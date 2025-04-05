package io.github.swampus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.ExecutionMode;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.config.UseCaseConfig;
import io.github.swampus.controller.config.TestQuantumConfig;
import io.github.swampus.dto.AddEntryRequest;
import io.github.swampus.dto.DeleteEntryRequest;
import io.github.swampus.dto.RangeQueryRequest;
import io.github.swampus.dto.SearchRequest;
import io.github.swampus.usecase.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuantumCollectionController.class)
@Import({UseCaseConfig.class})
@TestPropertySource(properties = {
        "quantum.quantum-execution-mode=LOCAL",
        "quantum.python-executable=/usr/bin/python3",
        "quantum.local-script-path=python/grover.py",
        "quantum.local-range-script=python/grover_range.py",
        "quantum.ibm-script-path=python/grover_ibm.py",
        "quantum.ibm-range-script-path=python/grover_range_ibm.py",
        "quantum.ibm-token=dummy_token"
})
class QuantumCollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AddEntryUseCase addEntryUseCase;
    @MockBean
    private DeleteEntryUseCase deleteEntryUseCase;
    @MockBean
    private SearchEntryUseCase searchEntryUseCase;
    @MockBean
    private RangeQueryUseCase rangeQueryUseCase;
    @MockBean
    private CreateCollectionUseCase createCollectionUseCase;
    @MockBean
    private DeleteCollectionUseCase deleteCollectionUseCase;
    @MockBean
    private GetAllEntriesUseCase getAllEntriesUseCase;

    @Test
    void testAddEntryReturnsOk() throws Exception {
        AddEntryRequest request = new AddEntryRequest();
        request.setKey("key1");
        request.setValue("value1");

        mockMvc.perform(post("/api/rest/v1/collections/test/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(addEntryUseCase).execute("test", "key1", "value1");
    }

    @Test
    void testDeleteEntryReturnsOk() throws Exception {
        DeleteEntryRequest request = new DeleteEntryRequest();
        request.setKey("key1");

        mockMvc.perform(delete("/api/rest/v1/collections/test/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(deleteEntryUseCase).execute("test", "key1");
    }

    @Test
    void testSearchReturnsValue() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setKey("hello");

        when(searchEntryUseCase.execute("test", "hello")).thenReturn("world");

        mockMvc.perform(post("/api/rest/v1/collections/test/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("world")));
    }

    @Test
    void testRangeQueryReturnsValue() throws Exception {
        RangeQueryRequest request = new RangeQueryRequest();
        request.setToKey("zulu");
        request.setFromKey("alpha");

        when(rangeQueryUseCase.execute("test", "alpha", "zulu"))
                .thenReturn(Optional.of("hello"));

        mockMvc.perform(post("/api/rest/v1/collections/test/range")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is("hello")));
    }

    @Test
    void testGetAllEntriesReturnsMap() throws Exception {
        when(getAllEntriesUseCase.execute("test"))
                .thenReturn(Map.of("key1", "value1", "key2", "value2"));

        mockMvc.perform(get("/api/rest/v1/collections/test/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.key1", is("value1")))
                .andExpect(jsonPath("$.entries.key2", is("value2")));
    }

    @Test
    void testCreateCollectionReturnsOk() throws Exception {
        mockMvc.perform(post("/api/rest/v1/collections/test"))
                .andExpect(status().isOk());

        verify(createCollectionUseCase).execute("test");
    }

    @Test
    void testDeleteCollectionReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/rest/v1/collections/test"))
                .andExpect(status().isOk());

        verify(deleteCollectionUseCase).execute("test");
    }

    @TestConfiguration
    static class QuantumTestConfig {
        @Bean
        public QuantumConfig quantumConfig() {
            TestQuantumConfig config = new TestQuantumConfig();
            config.setQuantumExecutionMode(ExecutionMode.LOCAL);
            config.setPythonExecutable("/usr/bin/python3");
            config.setLocalScriptPath("python/grover.py");
            config.setLocalRangeScriptPath("python/grover_range.py");
            return config;
        }
    }
}
