import java.util.*;

/** Eight independent Assignment 4 algorithms; the supplied JSONL transport calls these methods. */
public final class Algorithms {
    private Algorithms() { }

    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = (String) input.get("a");
        String b = (String) input.get("b");
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        StringBuilder reversed = new StringBuilder();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                reversed.append(a.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return Map.of("length", dp[n][m], "sequence", reversed.reverse().toString());
    }

    static Map<String, Object> merge(Map<String, Object> input) {
        List<?> sizes = (List<?>) input.get("sizes");
        int n = sizes.size();
        List<int[]> plan = new ArrayList<>();
        if (n <= 1) return Map.of("cost", 0L, "plan", plan);
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + ((Number) sizes.get(i)).longValue();
        }
        long[][] dp = new long[n][n];
        int[][] split = new int[n][n];
        for (int length = 2; length <= n; length++) {
            for (int left = 0; left + length <= n; left++) {
                int right = left + length - 1;
                long best = Long.MAX_VALUE;
                for (int middle = left; middle < right; middle++) {
                    long candidate = dp[left][middle] + dp[middle + 1][right];
                    if (candidate < best) {
                        best = candidate;
                        split[left][right] = middle;
                    }
                }
                dp[left][right] = best + prefix[right + 1] - prefix[left];
            }
        }
        emitMergePlan(0, n - 1, split, plan);
        return Map.of("cost", dp[0][n - 1], "plan", plan);
    }

    private static void emitMergePlan(int left, int right, int[][] split, List<int[]> plan) {
        if (left == right) return;
        int middle = split[left][right];
        emitMergePlan(left, middle, split, plan);
        emitMergePlan(middle + 1, right, split, plan);
        plan.add(new int[] {left, middle, right});
    }

    static Map<String, Object> tree(Map<String, Object> input) {
        int n = ((Number) input.get("n")).intValue();
        List<Integer> selected = new ArrayList<>();
        if (n == 0) return Map.of("weight", 0L, "selected", selected);
        List<?> weights = (List<?>) input.get("weights");
        List<?> edges = (List<?>) input.get("edges");
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] to = new int[2 * (n - 1)], next = new int[2 * (n - 1)];
        int edgeCount = 0;
        for (Object object : edges) {
            List<?> pair = (List<?>) object;
            int u = ((Number) pair.get(0)).intValue();
            int v = ((Number) pair.get(1)).intValue();
            to[edgeCount] = v; next[edgeCount] = head[u]; head[u] = edgeCount++;
            to[edgeCount] = u; next[edgeCount] = head[v]; head[v] = edgeCount++;
        }
        int[] parent = new int[n], order = new int[n];
        Arrays.fill(parent, -1);
        parent[0] = 0;
        order[0] = 0;
        int size = 1;
        for (int index = 0; index < size; index++) {
            int vertex = order[index];
            for (int edge = head[vertex]; edge != -1; edge = next[edge]) {
                int child = to[edge];
                if (child != parent[vertex]) {
                    parent[child] = vertex;
                    order[size++] = child;
                }
            }
        }
        long[] take = new long[n], skip = new long[n];
        for (int index = size - 1; index >= 0; index--) {
            int vertex = order[index];
            long takeWeight = ((Number) weights.get(vertex)).longValue();
            long skipWeight = 0;
            for (int edge = head[vertex]; edge != -1; edge = next[edge]) {
                int child = to[edge];
                if (child != parent[vertex]) {
                    takeWeight += skip[child];
                    skipWeight += Math.max(take[child], skip[child]);
                }
            }
            take[vertex] = takeWeight;
            skip[vertex] = skipWeight;
        }
        boolean[] chosen = new boolean[n];
        for (int index = 0; index < size; index++) {
            int vertex = order[index];
            chosen[vertex] = (vertex == 0 || !chosen[parent[vertex]])
                    && take[vertex] > skip[vertex];
            if (chosen[vertex]) selected.add(vertex);
        }
        return Map.of("weight", Math.max(take[0], skip[0]), "selected", selected);
    }

    static Map<String, Object> naive(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        List<Integer> positions = new ArrayList<>();
        if (m == 0) {
            for (int i = 0; i <= n; i++) positions.add(i);
            return Map.of("positions", positions);
        }
        for (int start = 0; start + m <= n; start++) {
            int j = 0;
            while (j < m && text.charAt(start + j) == pattern.charAt(j)) j++;
            if (j == m) positions.add(start);
        }
        return Map.of("positions", positions);
    }

    static Map<String, Object> kmp(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        List<Integer> positions = new ArrayList<>();
        if (m == 0) {
            for (int i = 0; i <= n; i++) positions.add(i);
            return Map.of("positions", positions);
        }
        if (m > n) return Map.of("positions", positions);
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int j = pi[i - 1];
            while (j > 0 && pattern.charAt(i) != pattern.charAt(j)) j = pi[j - 1];
            if (pattern.charAt(i) == pattern.charAt(j)) j++;
            pi[i] = j;
        }
        int matched = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            while (matched > 0 && c != pattern.charAt(matched)) matched = pi[matched - 1];
            if (c == pattern.charAt(matched)) matched++;
            if (matched == m) {
                positions.add(i - m + 1);
                matched = pi[matched - 1];
            }
        }
        return Map.of("positions", positions);
    }

    static Map<String, Object> rk(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        List<Integer> positions = new ArrayList<>();
        if (m == 0) {
            for (int i = 0; i <= n; i++) positions.add(i);
            return Map.of("positions", positions);
        }
        if (m > n) return Map.of("positions", positions);
        final long base = 31, modulus = 101;
        long highest = 1, needleHash = 0, windowHash = 0;
        for (int i = 0; i < m; i++) {
            if (i > 0) highest = (highest * base) % modulus;
            needleHash = (needleHash * base + pattern.charAt(i)) % modulus;
            windowHash = (windowHash * base + text.charAt(i)) % modulus;
        }
        for (int start = 0; start + m <= n; start++) {
            if (needleHash == windowHash) {
                int j = 0;
                while (j < m && text.charAt(start + j) == pattern.charAt(j)) j++;
                if (j == m) positions.add(start);
            }
            if (start + m < n) {
                windowHash = ((windowHash - text.charAt(start) * highest) * base
                        + text.charAt(start + m)) % modulus;
                if (windowHash < 0) windowHash += modulus;
            }
        }
        return Map.of("positions", positions);
    }

    static Map<String, Object> suffix(Map<String, Object> input) {
        String text = (String) input.get("text");
        int n = text.length();
        if (n == 0) return Map.of("sa", new int[0], "lcp", new int[0], "repeated", "");
        Integer[] sorted = new Integer[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = i;
            rank[i] = text.charAt(i);
        }
        for (int width = 1; ; width *= 2) {
            final int[] previousRank = rank;
            final int step = width;
            Arrays.sort(sorted, (a, b) -> {
                int first = Integer.compare(previousRank[a], previousRank[b]);
                if (first != 0) return first;
                int secondA = a + step < n ? previousRank[a + step] : -1;
                int secondB = b + step < n ? previousRank[b + step] : -1;
                return Integer.compare(secondA, secondB);
            });
            int[] updated = new int[n];
            int classes = 1;
            for (int i = 1; i < n; i++) {
                int left = sorted[i - 1], right = sorted[i];
                int leftSecond = left + width < n ? rank[left + width] : -1;
                int rightSecond = right + width < n ? rank[right + width] : -1;
                if (rank[left] != rank[right] || leftSecond != rightSecond) classes++;
                updated[right] = classes - 1;
            }
            rank = updated;
            if (classes == n) break;
        }
        int[] sa = new int[n], inverse = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = sorted[i];
            inverse[sa[i]] = i;
        }
        int[] lcp = new int[n];
        int matched = 0, best = 0, bestStart = 0;
        for (int start = 0; start < n; start++) {
            int at = inverse[start];
            if (at == 0) {
                matched = 0;
                continue;
            }
            int previous = sa[at - 1];
            while (start + matched < n && previous + matched < n
                    && text.charAt(start + matched) == text.charAt(previous + matched)) {
                matched++;
            }
            lcp[at] = matched;
            if (matched > best) {
                best = matched;
                bestStart = start;
            }
            if (matched > 0) matched--;
        }
        return Map.of("sa", sa, "lcp", lcp,
                "repeated", text.substring(bestStart, bestStart + best));
    }

    private static int[] emptyTransitions() {
        int[] row = new int[95];
        Arrays.fill(row, -1);
        return row;
    }

    static Map<String, Object> aho(Map<String, Object> input) {
        List<?> patterns = (List<?>) input.get("patterns");
        List<?> texts = (List<?>) input.get("texts");
        int p = patterns.size(), q = texts.size();
        int[][] counts = new int[q][p];
        if (p == 0 || q == 0) return Map.of("counts", counts);

        ArrayList<int[]> transitions = new ArrayList<>();
        transitions.add(emptyTransitions());
        int[] failure = new int[16], terminal = new int[p];
        for (int i = 0; i < p; i++) {
            int state = 0;
            String pattern = (String) patterns.get(i);
            for (int j = 0; j < pattern.length(); j++) {
                int character = pattern.charAt(j) - 32;
                int[] row = transitions.get(state);
                if (row[character] == -1) {
                    int child = transitions.size();
                    row[character] = child;
                    transitions.add(emptyTransitions());
                    if (child >= failure.length) {
                        failure = Arrays.copyOf(failure, failure.length * 2);
                    }
                }
                state = row[character];
            }
            terminal[i] = state;
        }

        int states = transitions.size();
        int[] queue = new int[states];
        int head = 0, tail = 0;
        int[] root = transitions.get(0);
        for (int c = 0; c < 95; c++) {
            if (root[c] == -1) root[c] = 0;
            else queue[tail++] = root[c];
        }
        while (head < tail) {
            int state = queue[head++];
            int[] row = transitions.get(state);
            int[] fallback = transitions.get(failure[state]);
            for (int c = 0; c < 95; c++) {
                int child = row[c];
                if (child == -1) row[c] = fallback[c];
                else {
                    failure[child] = fallback[c];
                    queue[tail++] = child;
                }
            }
        }

        for (int t = 0; t < q; t++) {
            String text = (String) texts.get(t);
            int[] visits = new int[states];
            int state = 0;
            for (int i = 0; i < text.length(); i++) {
                state = transitions.get(state)[text.charAt(i) - 32];
                visits[state]++;
            }
            for (int i = tail - 1; i >= 0; i--) {
                int node = queue[i];
                visits[failure[node]] += visits[node];
            }
            for (int i = 0; i < p; i++) counts[t][i] = visits[terminal[i]];
        }
        return Map.of("counts", counts);
    }
}
