package app.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileConverter}.
 */
class FileConverterTest {

    @TempDir
    Path tempDir;

    private FileConverter converter;
    private ObjectMapper  jsonMapper;
    private ObjectMapper  yamlMapper;

    @BeforeEach
    void setUp() {
        converter  = new FileConverter();
        jsonMapper = new ObjectMapper();
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    // ── JSON → YAML ───────────────────────────────────────────────────────────

    @Test
    void jsonToYaml_validSimpleJson_producesCorrectYaml() throws IOException {
        Path src  = createTempFile("simple.json",  "{\"key\":\"value\",\"num\":42}");
        Path dest = tempDir.resolve("simple.yaml");

        ConversionResult result = converter.convertJsonToYaml(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        assertTrue(Files.exists(dest), "Output YAML file should exist");
        JsonNode yamlNode = yamlMapper.readTree(dest.toFile());
        assertEquals("value", yamlNode.get("key").asText());
        assertEquals(42, yamlNode.get("num").asInt());
    }

    @Test
    void jsonToYaml_nestedStructure_preservesStructure() throws IOException {
        String json = """
                {
                  "person": {
                    "name": "Alice",
                    "tags": ["java", "yaml"]
                  }
                }
                """;
        Path src  = createTempFile("nested.json", json);
        Path dest = tempDir.resolve("nested.yaml");

        ConversionResult result = converter.convertJsonToYaml(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        JsonNode node = yamlMapper.readTree(dest.toFile());
        assertEquals("Alice", node.at("/person/name").asText());
        assertEquals("java",  node.at("/person/tags/0").asText());
        assertEquals("yaml",  node.at("/person/tags/1").asText());
    }

    @Test
    void jsonToYaml_recordsInputAndOutputSizes() throws IOException {
        Path src  = createTempFile("sizes.json", "{\"a\":1}");
        Path dest = tempDir.resolve("sizes.yaml");

        ConversionResult result = converter.convertJsonToYaml(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        assertTrue(result.inputSizeBytes() > 0,  "inputSizeBytes should be > 0");
        assertTrue(result.outputSizeBytes() > 0, "outputSizeBytes should be > 0");
        assertTrue(result.durationMs() >= 0,     "durationMs should be >= 0");
    }

    @Test
    void jsonToYaml_invalidJson_returnsFailure() throws IOException {
        Path src  = createTempFile("bad.json", "{\"key\": broken}");
        Path dest = tempDir.resolve("bad.yaml");

        ConversionResult result = converter.convertJsonToYaml(src, dest);

        assertEquals(ConversionStatus.FAILURE, result.status());
        assertFalse(Files.exists(dest), "Output file should NOT exist on failure");
        assertFalse(result.diagnostics().isBlank(), "Diagnostics should contain an error message");
    }

    // ── YAML → JSON ───────────────────────────────────────────────────────────

    @Test
    void yamlToJson_validSimpleYaml_producesCorrectJson() throws IOException {
        Path src  = createTempFile("simple.yaml", "key: value\nnum: 42\n");
        Path dest = tempDir.resolve("simple.json");

        ConversionResult result = converter.convertYamlToJson(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        assertTrue(Files.exists(dest));
        JsonNode node = jsonMapper.readTree(dest.toFile());
        assertEquals("value", node.get("key").asText());
        assertEquals(42, node.get("num").asInt());
    }

    @Test
    void yamlToJson_nestedStructure_preservesStructure() throws IOException {
        String yaml = """
                person:
                  name: Bob
                  tags:
                    - python
                    - devops
                """;
        Path src  = createTempFile("nested.yaml", yaml);
        Path dest = tempDir.resolve("nested.json");

        ConversionResult result = converter.convertYamlToJson(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        JsonNode node = jsonMapper.readTree(dest.toFile());
        assertEquals("Bob",    node.at("/person/name").asText());
        assertEquals("python", node.at("/person/tags/0").asText());
    }

    @Test
    void yamlToJson_recordsMetrics() throws IOException {
        Path src  = createTempFile("m.yaml", "x: 1\n");
        Path dest = tempDir.resolve("m.json");

        ConversionResult result = converter.convertYamlToJson(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        assertTrue(result.inputSizeBytes()  > 0);
        assertTrue(result.outputSizeBytes() > 0);
    }

    @Test
    void yamlToJson_invalidYaml_returnsFailure() throws IOException {
        Path src  = createTempFile("bad.yaml",
                "key: value\n  broken_indent: yes\n   another: [unclosed\n");
        Path dest = tempDir.resolve("bad.json");

        ConversionResult result = converter.convertYamlToJson(src, dest);

        assertEquals(ConversionStatus.FAILURE, result.status());
        assertFalse(result.diagnostics().isBlank());
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTrip_jsonToYamlToJson_preservesData() throws IOException {
        String original = """
                {
                  "name": "roundtrip",
                  "value": 123,
                  "active": true,
                  "items": [1, 2, 3]
                }
                """;
        Path jsonSrc  = createTempFile("rt.json",  original);
        Path yamlDest = tempDir.resolve("rt.yaml");
        Path jsonDest = tempDir.resolve("rt2.json");

        converter.convertJsonToYaml(jsonSrc, yamlDest);
        ConversionResult result = converter.convertYamlToJson(yamlDest, jsonDest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        JsonNode original1 = jsonMapper.readTree(jsonSrc.toFile());
        JsonNode roundtrip = jsonMapper.readTree(jsonDest.toFile());
        assertEquals(original1, roundtrip, "Round-trip data should be equal");
    }

    // ── Large file ────────────────────────────────────────────────────────────

    @Test
    void jsonToYaml_largeFile_completesSuccessfully() throws IOException {
        Path src  = tempDir.resolve("large.json");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(src))) {
            pw.print("{\"items\":[");
            for (int i = 0; i < 10_000; i++) {
                if (i > 0) pw.print(",");
                pw.printf("{\"id\":%d,\"value\":\"item-%d\"}", i, i);
            }
            pw.print("]}");
        }
        Path dest = tempDir.resolve("large.yaml");

        ConversionResult result = converter.convertJsonToYaml(src, dest);

        assertEquals(ConversionStatus.SUCCESS, result.status());
        assertTrue(result.outputSizeBytes() > 100_000, "Large YAML output expected");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Path createTempFile(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
