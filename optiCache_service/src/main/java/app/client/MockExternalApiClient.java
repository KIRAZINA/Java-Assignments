package app.client;

import app.config.ExternalApiProperties;
import org.springframework.stereotype.Component;

@Component
public class MockExternalApiClient implements ExternalApiClient {
    private final ExternalApiProperties properties;

    public MockExternalApiClient(ExternalApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String fetchData(String query) {
        try {
            Thread.sleep(properties.getTimeoutMs());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return "external-data:" + query;
    }
}
