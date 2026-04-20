package app.service;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class QueryParser {

    public record ParsedQuery(
        QueryType type,
        Set<String> terms,
        String phrase
    ) {}

    public enum QueryType {
        AND, OR, PHRASE
    }

    public ParsedQuery parse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ParsedQuery(QueryType.AND, Collections.emptySet(), null);
        }

        String raw = query.trim();

        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            String phrase = raw.substring(1, raw.length() - 1);
            String processedPhrase = TextProcessor.normalize(phrase);
            return new ParsedQuery(QueryType.PHRASE, Collections.emptySet(), processedPhrase);
        }

        if (raw.contains("|")) {
            String[] parts = raw.split("\\|");
            Set<String> terms = new HashSet<>();
            for (String part : parts) {
                String processed = TextProcessor.normalize(part);
                if (!processed.isEmpty() && !TextProcessor.STOP_WORDS.contains(processed)) {
                    terms.add(processed);
                }
            }
            return new ParsedQuery(QueryType.OR, terms, null);
        }

        String processed = TextProcessor.normalize(raw);
        String[] parts = processed.split("\\s+");
        Set<String> terms = new HashSet<>();
        for (String part : parts) {
            if (!part.isEmpty() && !TextProcessor.STOP_WORDS.contains(part)) {
                terms.add(part);
            }
        }
        return new ParsedQuery(QueryType.AND, terms, null);
    }
}