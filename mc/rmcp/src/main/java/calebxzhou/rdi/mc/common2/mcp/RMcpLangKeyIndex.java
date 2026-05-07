package calebxzhou.rdi.mc.common2.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RMcpLangKeyIndex {
    private final Map<String, String> english;
    private final Map<String, String> chinese;
    private final List<Entry> entries;

    private RMcpLangKeyIndex(Map<String, String> english, Map<String, String> chinese, List<Entry> entries) {
        this.english = english;
        this.chinese = chinese;
        this.entries = entries;
    }

    public static RMcpLangKeyIndex create(Map<String, String> english, Map<String, String> chinese) {
        var englishCopy = Map.copyOf(english);
        var chineseCopy = Map.copyOf(chinese);
        var keys = new HashSet<String>();
        keys.addAll(englishCopy.keySet());
        keys.addAll(chineseCopy.keySet());
        var entries = new ArrayList<Entry>();
        for (var key : keys) {
            var englishName = englishCopy.getOrDefault(key, "");
            var chineseName = chineseCopy.getOrDefault(key, "");
            entries.add(new Entry(
                    key,
                    englishName,
                    chineseName,
                    normalize(englishName),
                    normalize(chineseName)
            ));
        }
        return new RMcpLangKeyIndex(englishCopy, chineseCopy, List.copyOf(entries));
    }

    public RMcpLangKeyData langKey(String key) {
        var en = english.getOrDefault(key, "");
        var cn = chinese.getOrDefault(key, "");
        return en.isEmpty() && cn.isEmpty() ? null : new RMcpLangKeyData(en, cn);
    }

    public RMcpLangKeySearchData search(String text) {
        var query = normalize(text);
        if (query.isEmpty()) {
            return new RMcpLangKeySearchData(text, List.of());
        }

        var candidates = new ArrayList<Candidate>();
        for (var entry : entries) {
            var chineseScore = fuzzyScore(query, entry.normalizedCn());
            if (chineseScore > 0) {
                addCandidate(candidates, new Candidate(
                        new RMcpLangKeySearchResult(entry.key(), entry.en(), entry.cn()),
                        0,
                        chineseScore
                ));
                continue;
            }
            var englishScore = fuzzyScore(query, entry.normalizedEn());
            if (englishScore > 0) {
                addCandidate(candidates, new Candidate(
                        new RMcpLangKeySearchResult(entry.key(), entry.en(), entry.cn()),
                        1,
                        englishScore
                ));
            }
        }

        var results = new ArrayList<RMcpLangKeySearchResult>();
        for (var candidate : candidates) {
            results.add(candidate.result());
        }
        return new RMcpLangKeySearchData(text, List.copyOf(results));
    }

    private static void addCandidate(ArrayList<Candidate> candidates, Candidate candidate) {
        candidates.add(candidate);
        candidates.sort(
                Comparator.comparingInt(Candidate::languagePriority)
                        .thenComparing(Comparator.comparingDouble(Candidate::score).reversed())
                        .thenComparing(candidate0 -> candidate0.result().langkey())
        );
        if (candidates.size() > 10) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private static double fuzzyScore(String query, String candidate) {
        if (query.isEmpty() || candidate.isEmpty()) {
            return 0;
        }
        int matched = 0;
        int candidateIndex = 0;
        for (int i = 0; i < query.length(); i++) {
            var ch = query.charAt(i);
            while (candidateIndex < candidate.length() && candidate.charAt(candidateIndex) != ch) {
                candidateIndex++;
            }
            if (candidateIndex >= candidate.length()) {
                continue;
            }
            matched++;
            candidateIndex++;
        }

        var containsQuery = candidate.contains(query);
        if (matched < 2 && !containsQuery) {
            return 0;
        }
        double score = (double) matched / candidate.length();
        if (containsQuery) {
            score += 1.0;
        }
        if (candidate.startsWith(query)) {
            score += 0.25;
        }
        if (candidate.equals(query)) {
            score += 0.5;
        }
        return score;
    }

    private static String normalize(String text) {
        var builder = new StringBuilder();
        var lower = text.trim().toLowerCase(Locale.ROOT);
        for (int offset = 0; offset < lower.length(); ) {
            int codePoint = lower.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == ':' || codePoint == '.') {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private record Entry(String key, String en, String cn, String normalizedEn, String normalizedCn) {
    }

    private record Candidate(RMcpLangKeySearchResult result, int languagePriority, double score) {
    }
}
