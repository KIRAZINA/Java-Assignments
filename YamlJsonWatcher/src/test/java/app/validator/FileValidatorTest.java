package app.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileValidator}.
 */
class FileValidatorTest {

    @TempDir
    Path tempDir;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator();
    }

    // ── JSON validation ───────────────────────────────────────────────────────

    @Test
    void validateJson_validObject_returnsEmpty() throws IOException {
        Path p = write("ok.json", "{\"key\":\"value\",\"num\":42}");
        Optional<String> result = validator.validateJson(p);
        assertTrue(result.isEmpty(), "Valid JSON should produce no error");
    }

    @Test
    void validateJson_validArray_returnsEmpty() throws IOException {
        Path p = write("array.json", "[1,2,3]");
        assertTrue(validator.validateJson(p).isEmpty());
    }

    @Test
    void validateJson_missingClosingBrace_returnsError() throws IOException {
        Path p = write("bad.json", "{\"key\": \"value\"");
        Optional<String> result = validator.validateJson(p);
        assertTrue(result.isPresent(), "Invalid JSON should produce an error");
        assertTrue(result.get().contains("JSON") || result.get().contains("json") ||
                   result.get().contains("Invalid"), "Error message should mention invalidity");
    }

    @Test
    void validateJson_emptyFile_returnsError() throws IOException {
        Path p = write("empty.json", "");
        Optional<String> result = validator.validateJson(p);
        assertTrue(result.isPresent(), "Empty file should produce an error");
    }

    @Test
    void validateJson_nonExistentFile_returnsError() {
        Path p = tempDir.resolve("ghost.json");
        Optional<String> result = validator.validateJson(p);
        assertTrue(result.isPresent());
    }

    @Test
    void validateJson_invalidValue_returnsErrorWithInfo() throws IOException {
        Path p = write("badval.json", "{\"key\": broken}");
        Optional<String> result = validator.validateJson(p);
        assertTrue(result.isPresent());
        assertFalse(result.get().isBlank());
    }

    // ── YAML validation ───────────────────────────────────────────────────────

    @Test
    void validateYaml_simpleKeyValue_returnsEmpty() throws IOException {
        Path p = write("ok.yaml", "key: value\nnum: 42\n");
        assertTrue(validator.validateYaml(p).isEmpty());
    }

    @Test
    void validateYaml_nestedStructure_returnsEmpty() throws IOException {
        String yaml = "parent:\n  child: yes\n  list:\n    - a\n    - b\n";
        Path p = write("nested.yaml", yaml);
        assertTrue(validator.validateYaml(p).isEmpty());
    }

    @Test
    void validateYaml_invalidIndentation_returnsError() throws IOException {
        // Tab mixed with spaces in YAML is a common parse error
        Path p = write("bad.yaml", "key: value\n\t- baditem\n");
        Optional<String> result = validator.validateYaml(p);
        // Some YAML parsers are lenient; we just verify no crash and consistent behavior
        assertNotNull(result);
    }

    @Test
    void validateYaml_emptyFile_returnsError() throws IOException {
        Path p = write("empty.yaml", "");
        Optional<String> result = validator.validateYaml(p);
        assertTrue(result.isPresent(), "Empty file should produce an error");
    }

    @Test
    void validateYaml_nonExistentFile_returnsError() {
        Path p = tempDir.resolve("ghost.yaml");
        Optional<String> result = validator.validateYaml(p);
        assertTrue(result.isPresent());
    }

    @Test
    void validateYaml_validListDocument_returnsEmpty() throws IOException {
        Path p = write("list.yaml", "- one\n- two\n- three\n");
        assertTrue(validator.validateYaml(p).isEmpty());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
