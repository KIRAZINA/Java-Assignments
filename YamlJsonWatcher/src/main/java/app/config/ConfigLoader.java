package app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads application configuration from the classpath {@code config.yaml},
 * then applies environment-variable overrides.
 *
 * <p>Supported environment variables:
 * <ul>
 *   <li>{@code TARGET_DIR}           — directory to watch</li>
 *   <li>{@code OUTPUT_DIR}           — directory for output files</li>
 *   <li>{@code EXTENSIONS}           — comma-separated list, e.g. {@code .json,.yaml}</li>
 *   <li>{@code DEBOUNCE_MS}          — debounce window in milliseconds</li>
 *   <li>{@code LOG_LEVEL}            — SLF4J log level string</li>
 *   <li>{@code LOG_FILE}             — path to the rolling log file</li>
 *   <li>{@code LARGE_FILE_THRESHOLD} — min row count for compact logging (default 500)</li>
 * </ul>
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_RESOURCE = "/config.yaml";

    // Default values used when neither config.yaml nor env vars supply a value
    private static final String DEFAULT_TARGET_DIR              = "./watch";
    private static final String DEFAULT_OUTPUT_DIR              = "";
    private static final long   DEFAULT_DEBOUNCE_MS             = 1000L;
    private static final String DEFAULT_LOG_LEVEL               = "INFO";
    private static final String DEFAULT_LOG_FILE                = "logs/yamlJsonWatcher.log";
    private static final int    DEFAULT_LARGE_FILE_ROW_THRESHOLD = 500;
    private static final List<String> DEFAULT_EXTENSIONS =
            List.of(".json", ".yaml", ".yml");

    public AppConfig load() {
        Map<String, Object> yaml = readYaml();

        String targetDir   = resolve("TARGET_DIR",   getString(yaml, "targetDir",   DEFAULT_TARGET_DIR));
        String outputDir   = resolve("OUTPUT_DIR",   getString(yaml, "outputDir",   DEFAULT_OUTPUT_DIR));
        long   debounceMs  = resolveLong("DEBOUNCE_MS", getLong(yaml, "debounceMs",  DEFAULT_DEBOUNCE_MS));
        String logLevel    = resolve("LOG_LEVEL",    getString(yaml, "logLevel",    DEFAULT_LOG_LEVEL));
        String logFilePath = resolve("LOG_FILE",     getString(yaml, "logFilePath", DEFAULT_LOG_FILE));
        int largeFileRowThreshold = (int) resolveLong("LARGE_FILE_THRESHOLD",
                getLong(yaml, "largeFileRowThreshold", DEFAULT_LARGE_FILE_ROW_THRESHOLD));

        List<String> extensions = resolveExtensions(yaml);

        AppConfig config = new AppConfig(targetDir, outputDir, extensions, debounceMs, logLevel,
                logFilePath, largeFileRowThreshold);
        log.info("Configuration loaded: targetDir={}, outputDir='{}', extensions={}, debounceMs={}, "
                        + "logLevel={}, largeFileRowThreshold={}",
                config.targetDir(), config.effectiveOutputDir(), config.extensions(),
                config.debounceMs(), config.logLevel(), config.largeFileRowThreshold());
        return config;
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private Map<String, Object> readYaml() {
        try (InputStream is = ConfigLoader.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (is == null) {
                log.warn("config.yaml not found on classpath; using defaults.");
                return Map.of();
            }
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(is);
            return map != null ? map : Map.of();
        } catch (Exception e) {
            log.error("Failed to parse config.yaml: {}; using defaults.", e.getMessage());
            return Map.of();
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return (val instanceof String s && !s.isBlank()) ? s : defaultVal;
    }

    private long getLong(Map<String, Object> map, String key, long defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        return defaultVal;
    }

    /** Returns env var value if set and non-blank, otherwise returns {@code fallback}. */
    String resolve(String envKey, String fallback) {
        String env = System.getenv(envKey);
        return (env != null && !env.isBlank()) ? env : fallback;
    }

    long resolveLong(String envKey, long fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid value for env var {}: '{}'; using default {}.", envKey, env, fallback);
            }
        }
        return fallback;
    }

    private List<String> resolveExtensions(Map<String, Object> map) {
        String envVal = System.getenv("EXTENSIONS");
        if (envVal != null && !envVal.isBlank()) {
            return Arrays.stream(envVal.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        Object yamlVal = map.get("extensions");
        if (yamlVal instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof String)
                    .map(o -> (String) o)
                    .collect(Collectors.toList());
        }
        return DEFAULT_EXTENSIONS;
    }
}
