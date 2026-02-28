package app.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Validates JSON and YAML files before conversion.
 *
 * <p>Both methods return:
 * <ul>
 *   <li>{@link Optional#empty()} — file is valid</li>
 *   <li>{@link Optional} containing an error message — file is invalid</li>
 * </ul>
 *
 * <p>Implementations are stateless and thread-safe.
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Validates a JSON file.
     *
     * @param path file to validate
     * @return empty Optional on success, or an Optional with an error description
     */
    public Optional<String> validateJson(Path path) {
        if (!fileExistsAndNonEmpty(path)) {
            return Optional.of("File does not exist or is empty: " + path);
        }

        try (InputStream is = Files.newInputStream(path)) {
            jsonMapper.readTree(is);
            return Optional.empty();
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            String msg = String.format("Invalid JSON in '%s': %s (line %d, col %d)",
                    path.getFileName(),
                    e.getOriginalMessage(),
                    e.getLocation().getLineNr(),
                    e.getLocation().getColumnNr());
            log.warn(msg);
            return Optional.of(msg);
        } catch (IOException e) {
            String msg = "Could not read JSON file '" + path.getFileName() + "': " + e.getMessage();
            log.warn(msg);
            return Optional.of(msg);
        }
    }

    /**
     * Validates a YAML file.
     *
     * @param path file to validate
     * @return empty Optional on success, or an Optional with an error description
     */
    public Optional<String> validateYaml(Path path) {
        if (!fileExistsAndNonEmpty(path)) {
            return Optional.of("File does not exist or is empty: " + path);
        }

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(path)) {
            yaml.load(is);   // throws YAMLException on bad syntax
            return Optional.empty();
        } catch (YAMLException e) {
            String msg = String.format("Invalid YAML in '%s': %s",
                    path.getFileName(), e.getMessage());
            log.warn(msg);
            return Optional.of(msg);
        } catch (IOException e) {
            String msg = "Could not read YAML file '" + path.getFileName() + "': " + e.getMessage();
            log.warn(msg);
            return Optional.of(msg);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private boolean fileExistsAndNonEmpty(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
