package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import app.config.CacheProperties;
import app.config.ExternalApiProperties;

@SpringBootApplication
@EnableConfigurationProperties({CacheProperties.class, ExternalApiProperties.class})
public class CacheFirstApiAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(CacheFirstApiAggregatorApplication.class, args);
    }
}
