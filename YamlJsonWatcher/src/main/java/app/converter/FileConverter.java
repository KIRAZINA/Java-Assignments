package app.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts files between JSON and YAML formats using Jackson.
 *
 * <p>Both methods are stateless and thread-safe.
 * After parsing, the method counts top-level rows (array size or object key count)
 * so that {@link app.logging.ConversionLogger} can choose verbose vs. compact output.
 */
public class FileConverter {

    private static final Logger log = LoggerFactory.getLogger(FileConverter.class);

    // JSON → YAML: read with JSON mapper, write with YAML mapper
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public FileConverter() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);

        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)   // omit leading "---"
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    /**
     * Converts a JSON file to YAML and writes the result to {@code dest}.
     *
     * @param src  source JSON file
     * @param dest destination YAML file (will be created or overwritten)
     * @return a {@link ConversionResult} describing the outcome
     */
    public ConversionResult convertJsonToYaml(Path src, Path dest) {
        long start = System.currentTimeMillis();
        long inputSize = sizeOf(src);

        try {
            ensureParentDirs(dest);
            JsonNode tree = readJson(src);
            int rowCount = countRows(tree);
            writeYaml(tree, dest);
            long duration = System.currentTimeMillis() - start;
            long outputSize = sizeOf(dest);
            log.debug("JSON→YAML: {} → {} ({} ms, {} rows)", src, dest, duration, rowCount);
            return ConversionResult.success(src, dest, inputSize, outputSize, duration, rowCount);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "JSON→YAML conversion failed: " + e.getMessage();
            log.error(msg, e);
            return ConversionResult.failure(src, inputSize, duration, msg);
        }
    }

    /**
     * Converts a YAML file to JSON and writes the result to {@code dest}.
     *
     * @param src  source YAML file
     * @param dest destination JSON file (will be created or overwritten)
     * @return a {@link ConversionResult} describing the outcome
     */
    public ConversionResult convertYamlToJson(Path src, Path dest) {
        long start = System.currentTimeMillis();
        long inputSize = sizeOf(src);

        try {
            ensureParentDirs(dest);
            JsonNode tree = readYaml(src);
            int rowCount = countRows(tree);
            writeJson(tree, dest);
            long duration = System.currentTimeMillis() - start;
            long outputSize = sizeOf(dest);
            log.debug("YAML→JSON: {} → {} ({} ms, {} rows)", src, dest, duration, rowCount);
            return ConversionResult.success(src, dest, inputSize, outputSize, duration, rowCount);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "YAML→JSON conversion failed: " + e.getMessage();
            log.error(msg, e);
            return ConversionResult.failure(src, inputSize, duration, msg);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private JsonNode readJson(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return jsonMapper.readTree(is);
        }
    }

    private JsonNode readYaml(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return yamlMapper.readTree(is);
        }
    }

    private void writeYaml(JsonNode tree, Path dest) throws IOException {
        try (OutputStream os = Files.newOutputStream(dest)) {
            yamlMapper.writeValue(os, tree);
        }
    }

    private void writeJson(JsonNode tree, Path dest) throws IOException {
        try (OutputStream os = Files.newOutputStream(dest)) {
            jsonMapper.writeValue(os, tree);
        }
    }

    private void ensureParentDirs(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Counts the number of "rows" in a parsed JSON/YAML document:
     * <ul>
     *   <li>Array root → number of elements</li>
     *   <li>Object root → number of immediate keys (typical for a single-object config)</li>
     *   <li>Any other node → 1</li>
     * </ul>
     * This is used as a heuristic for "file size" when deciding log verbosity.
     */
    static int countRows(JsonNode node) {
        if (node == null) return 0;
        if (node.isArray())  return node.size();
        if (node.isObject()) return node.size();
        return 1;
    }
}
