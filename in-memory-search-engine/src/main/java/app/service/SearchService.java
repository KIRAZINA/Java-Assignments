package app.service;

import app.model.Document;
import app.service.QueryParser.ParsedQuery;
import app.service.QueryParser.QueryType;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {
    private final InvertedIndex invertedIndex;
    private final QueryParser queryParser;

    public SearchService(InvertedIndex invertedIndex, QueryParser queryParser) {
        this.invertedIndex = invertedIndex;
        this.queryParser = queryParser;
    }

    public SearchService() {
        this(new InvertedIndex(), new QueryParser());
    }

    public void addDocument(Document doc) {
        invertedIndex.addDocument(doc);
    }

    public List<Document> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        ParsedQuery parsed = queryParser.parse(query);
        Set<String> resultIds;

        switch (parsed.type()) {
            case PHRASE -> {
                Set<String> phraseTerms = TextProcessor.tokenize(parsed.phrase());
                Set<String> candidates = invertedIndex.searchAND(phraseTerms);
                resultIds = invertedIndex.searchPhrase(parsed.phrase(), candidates);
            }
            case OR -> {
                resultIds = invertedIndex.searchOR(parsed.terms());
            }
            default -> {
                resultIds = invertedIndex.searchAND(parsed.terms());
            }
        }

        List<Document> results = new ArrayList<>();
        for (String id : resultIds) {
            Document doc = invertedIndex.getDocument(id);
            if (doc != null) {
                results.add(doc);
            }
        }

        return rankResults(results, query);
    }

    private List<Document> rankResults(List<Document> results, String query) {
        Set<String> queryTokens = TextProcessor.tokenize(query);

        return results.stream()
            .sorted((d1, d2) -> {
                int score1 = countMatchedTerms(d1.getContent(), queryTokens);
                int score2 = countMatchedTerms(d2.getContent(), queryTokens);
                int cmp = Integer.compare(score2, score1);
                if (cmp != 0) {
                    return cmp;
                }
                return d2.getId().compareTo(d1.getId());
            })
            .collect(Collectors.toList());
    }

    private int countMatchedTerms(String content, Set<String> queryTokens) {
        Set<String> contentTokens = TextProcessor.tokenize(content);
        int count = 0;
        for (String term : queryTokens) {
            if (contentTokens.contains(term)) {
                count++;
            }
        }
        return count;
    }
}