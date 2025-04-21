
package io.github.swampus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.ExecutionMode;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.config.UseCaseConfig;
import io.github.swampus.controller.config.TestQuantumConfig;
import io.github.swampus.dto.*;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.usecase.*;
import io.github.swampus.mapper.QuantumResultMapper;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    @MockBean
    private QuantumResultMapper quantumResultMapper;

    @Test
    void testAddEntryReturnsOk() throws Exception {
        mockMvc.perform(post("/api/rest/v1/collections/test/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AddEntryRequest.builder().key("key1").value("value1").build())))
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

        QuantumResultModel model = mockModel("hello", "world");
        var dto = new QuantumResultDTO();
        dto.setQuantumResult(new QuantumResultDTO.QuantumResult());
        dto.getQuantumResult().setMatchedKey("hello");
        dto.getQuantumResult().setMatchedValue("world");
        dto.getQuantumResult().setConfidenceScore(0.95);

        when(searchEntryUseCase.execute("test", "hello")).thenReturn(model);
        when(quantumResultMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/rest/v1/collections/test/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantum_result.matched_key", is("hello")))
                .andExpect(jsonPath("$.quantum_result.matched_value", is("world")))
                .andExpect(jsonPath("$.quantum_result.confidence_score", is(0.95)));
    }

    @Test
    void testRangeReturnsValue() throws Exception {
        RangeQueryRequest request = new RangeQueryRequest();
        request.setFromKey("a");
        request.setToKey("z");

        QuantumResultModel model = mockModel("k2", "value_for_k2");
        QuantumResultDTO dto = new QuantumResultDTO();
        dto.setQuantumResult(new QuantumResultDTO.QuantumResult());
        dto.getQuantumResult().setMatchedKey("k2");
        dto.getQuantumResult().setMatchedValue("value_for_k2");
        dto.getQuantumResult().setConfidenceScore(0.87);

        when(rangeQueryUseCase.execute("test", "a", "z")).thenReturn(model);
        when(quantumResultMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/rest/v1/collections/test/range")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantum_result.matched_key", is("k2")))
                .andExpect(jsonPath("$.quantum_result.matched_value", is("value_for_k2")))
                .andExpect(jsonPath("$.quantum_result.confidence_score", is(0.87)));
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

        verify(createCollectionUseCase, atLeastOnce()).execute("test");
    }

    @Test
    void testDeleteCollectionReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/rest/v1/collections/test"))
                .andExpect(status().isOk());

        verify(deleteCollectionUseCase).execute("test");
    }

    private QuantumResultModel mockModel(String key, String value) {
        var result = getQuantumResult(key, value);

        var notes = new QuantumResultModel.ScientificNotes();
        notes.setPrinciple("Grover");
        notes.setTheory("Amplification");
        notes.setCircuitBehavior("Flip");
        notes.setConfidenceInterpretation("High");
        notes.setQubitCommentary("Used 2");
        notes.setEncodingMap(Map.of(key, "01"));
        notes.setUsedIterations(1);

        var model = new QuantumResultModel();
        model.setQuantumResult(result);
        model.setScientificNotes(notes);
        return model;
    }

    private static QuantumResultModel.QuantumResult getQuantumResult(String key, String value) {
        var result = new QuantumResultModel.QuantumResult();
        result.setMatchedKey(key);
        result.setMatchedValue(value);
        result.setMatchedIndex(0);
        result.setTopMeasurement("01");
        result.setOracleExpression("(x01)");
        result.setNumQubits(2);
        result.setProbabilities(Map.of("01", 0.9));
        result.setConfidenceScore(0.95);
        result.setExecutionTimeMs(123);
        result.setOracleDepth(3);
        result.setIterations(1);
        result.setNote("Success");
        return result;
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
