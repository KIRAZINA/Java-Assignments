package app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests exercising the real HTTP layer (Task 3 & 4):
 * document indexing (single + batch), search, statistics, CRUD, error codes,
 * and actuator health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private app.service.InvertedIndex invertedIndex;

    /** The SearchService bean wraps the singleton index, so reset it before
     *  each test to keep assertions about exact counts deterministic. */
    @BeforeEach
    void resetIndex() {
        invertedIndex.clear();
    }

    @Test
    void testFullWorkflowViaService() {
        // Retained direct-service workflow (sanity for ranking + boolean logic).
        app.service.SearchService service = new app.service.SearchService();
        service.addDocument(new app.model.Document("doc1", "Java Spring Boot is powerful"));
        service.addDocument(new app.model.Document("doc2", "Python Django is great"));
        service.addDocument(new app.model.Document("doc3", "Java Spring Boot with Python"));
        assertEquals(2, service.search("java spring").size());
        assertEquals(3, service.search("java|python").size());
        assertEquals(2, service.search("\"spring boot\"").size());
    }

    @Test
    void testAddAndSearchOverHttp() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"d1\",\"content\":\"java spring boot\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/search").param("q", "java spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("d1"));
    }

    @Test
    void testBatchIndexingOverHttp() throws Exception {
        String batch = "["
                + "{\"id\":\"b1\",\"content\":\"java kubernetes docker\"},"
                + "{\"id\":\"b2\",\"content\":\"python django\"},"
                + "{\"id\":\"b3\",\"content\":\"java python\"}]";
        mockMvc.perform(post("/documents/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(3));

        mockMvc.perform(get("/search").param("q", "java|python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void testStatsOverHttp() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"s1\",\"content\":\"java spring boot\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.uniqueTerms").value(3))
                .andExpect(jsonPath("$.averageDocumentLength").value(3.0))
                .andExpect(jsonPath("$.indexSizeBytes").exists());
    }

    @Test
    void testGetDocumentAndNotFound() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"g1\",\"content\":\"hello world\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/documents/g1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("g1"));

        mockMvc.perform(get("/documents/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteDocument() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"del1\",\"content\":\"to be deleted\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/documents/del1")).andExpect(status().isOk());
        mockMvc.perform(get("/documents/del1")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/documents/del1")).andExpect(status().isNotFound());
    }

    @Test
    void testQueryTooLongReturnsBadRequest() throws Exception {
        String longQuery = "a".repeat(1001);
        mockMvc.perform(get("/search").param("q", longQuery))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEmptyQueryReturnsEmptyOverHttp() throws Exception {
        mockMvc.perform(get("/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
