package app.service;

import java.util.*;

public class TextProcessor {

    public static final Set<String> STOP_WORDS = Set.of(
        "the", "is", "a", "an", "in", "on", "at", "to", "for", "of", "and", "or", "but"
    );

    private TextProcessor() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static Set<String> tokenize(String text) {
        String processed = normalize(text);
        Set<String> tokens = new HashSet<>();
        for (String word : processed.split("\\s+")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public static List<String> tokenizeToList(String text) {
        String processed = normalize(text);
        List<String> tokens = new ArrayList<>();
        for (String word : processed.split("\\s+")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public static boolean containsExactPhrase(String content, String phrase) {
        String normalizedContent = normalize(content);
        String normalizedPhrase = normalize(phrase);
        
        if (normalizedPhrase.isEmpty()) {
            return false;
        }
        
        String[] contentWords = normalizedContent.split("\\s+");
        String[] phraseWords = normalizedPhrase.split("\\s+");
        
        if (phraseWords.length == 0) {
            return false;
        }
        
        for (int i = 0; i <= contentWords.length - phraseWords.length; i++) {
            boolean match = true;
            for (int j = 0; j < phraseWords.length; j++) {
                if (!contentWords[i + j].equals(phraseWords[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsTerm(String content, String term) {
        Set<String> contentTokens = tokenize(content);
        String normalizedTerm = normalize(term);
        return contentTokens.contains(normalizedTerm);
    }
}