# Assignment 4 — Analysis, verification and search benchmark (Java)

## 1. Environment, build and evidence

**Selected language:** Java 17 (OpenJDK 17.0.20). The original Java source
template's `Main.java` and `Json.java` transport are unchanged. Only
`Algorithms.java` supplies the eight implementations. No algorithm libraries
or third-party dependencies are used. Python 3.12.14 runs the teacher's public
launcher and an independent test harness; the assessed algorithms run in Java.

**Measured host:** Linux 6.18.57 x86_64, glibc 2.39; 9 logical CPUs and
approximately 10.45 GB RAM visible to the runtime. These are host observations,
not the official grading allocations. The published grader allows one CPU,
768 MiB memory and 20 seconds per requirement-group batch. Java was run with
`-Xmx384m` in the local tests.

**Standard build, run and test commands (from the project root):**

```bash
python tools/run.py --setup
python tools/run.py --check
python -m unittest discover -s tests/student -p 'test_*.py' -v
mkdir -p Java/build
javac --release 17 -encoding UTF-8 -d Java/build Java/source/*.java
java -Xmx384m -cp Java/build Main
javac --release 17 -cp Java/build -d Java/build tests/student/BenchmarkSearch.java
java -Xmx384m -cp Java/build BenchmarkSearch
```

The measured environment had the JDK's `jdk.compiler` module but no ordinary
`javac` executable; for local compilation we invoked
`java --module jdk.compiler/com.sun.tools.javac.Main` with the same `--release
17` compiler options. The standard commands above are supported by a normal
JDK 17 installation, including the GitHub Actions runner. The original
`tools/run.py` remains intact.

**Observed verification:** all **44 supplied public requests passed** using
the instructor's `public_validate.check` and the compiled Java `Main`, and
the repeated **88-request** mixed JSONL batch passed. All **15** student
test methods passed (1.723 seconds in the recorded local run), including
independently computed small optimums and maximum-domain stress tests.
Benchmark measurements are reported in Section 4.

The submitted commit URL, with its full 40-character SHA, must be recorded
in Moodle after the student creates and pushes their repository. A ZIP is not
the Moodle submission target according to the instructor's checklist.

## 2. Algorithms and correctness

### 2.1 `lcs` — two-sequence prefix DP

`dp[i][j]` represents the optimal LCS length of prefixes `a[0..i-1]` and
`b[0..j-1]`; row 0 and column 0 are zero. When the two last characters
match, set `dp[i][j]=dp[i-1][j-1]+1`. Otherwise set it to the larger of
`dp[i-1][j]` and `dp[i][j-1]`. The table is filled by increasing `i`, then
`j`, so every predecessor is available. If the last characters differ, any
LCS omits at least one of them. If they agree, an optimal common subsequence
may end in that character. These facts prove the transition by induction.

Trace backward from `(n,m)`. Matching last characters go diagonally and are
appended to a reversed `StringBuilder`; a mismatch follows the larger
neighbour. A tie prefers the upper neighbour, and either tie choice remains
optimal. Reverse at the end. Empty inputs return length 0 and `""`.

**Time:** O((n+1)(m+1)). **Auxiliary memory:** O((n+1)(m+1)) for the `int[][]`
DP, plus O(min(n,m)+1) traceback workspace. Input and output string storage
is separate. **Test:** `tea_time` against `team` requires a subsequence
(`team`), not a contiguous substring; the 1,000 × 1,000 case checks scale.

### 2.2 `merge` — interval DP for ordered adjacent blocks

`dp[l][r]` is the cheapest cost of reducing the consecutive original blocks
`[l,r]` to one. Base `dp[i][i]=0`. The final merge must join some `[l,k]`
and `[k+1,r]`. A prefix sum `P` gives the final merge's weight in O(1):

`dp[l][r] = P[r+1]-P[l] + min(l<=k<r)(dp[l][k]+dp[k+1][r])`.

Increasing interval length guarantees child costs are ready. Since every
valid final merge has exactly such a split, and the child's operations are
independent while the last payment is fixed, the minimum is optimal. Store
the first optimal `k` in `split[l][r]`. A recursive postorder traversal
emits all left-child merges, all right-child merges and then `[l,k,r]`, which
ensures adjacent blocks exist when each step occurs. Recursion depth is at
most the published maximum n=200. For n<=1, cost=0 and plan is empty.

**Time:** O(n^3). **Auxiliary memory:** O(n^2) for DP/splits, O(n) for prefix
sums and reconstruction stack; output has n-1 merge triples. Java `long` is
used for every cost. **Test:** 200 blocks each weighing `10^9` have optimal
cost `1,544,000,000,000`, and the test independently simulates every merge.

### 2.3 `tree` — maximum-weight independent set on a tree

Use compact primitive-array adjacency (`head`, `to`, `next`). Root at vertex
0 and compute a root-first vertex order and parent array iteratively. In
reverse order, define:

`take[v] = weight[v] + sum(skip[child])`  
`skip[v] = sum(max(take[child], skip[child]))`.

Child states are complete before their parent. If v is selected, no child
may be; if not, every child may independently take its better state.
Induction over rooted subtrees therefore proves correctness. Reconstruct
by walking the root-first order: select v only if the parent was not selected
and `take[v] > skip[v]`. Ties choose skip; this cannot reduce the optimum.
The empty set is always allowed, so all-negative trees return weight 0.
There is no recursive traversal, even for a chain with 100,000 vertices.

**Time:** O(n+1). **Auxiliary memory:** O(n+1) for adjacency, parent/order,
two `long[]` DP arrays, and chosen flags; output selected IDs require O(n).
**Test:** a 100,000-vertex chain with every vertex worth `10^9` yields
`50,000,000,000,000`; adjacency and uniqueness of all selected vertices
are validated independently. The empty tree and negative/tied weights are
also tested.

### 2.4 `naive` — exact baseline matching

For a nonempty pattern of length m, try each start `0..n-m` inclusive.
Compare characters until the first mismatch or m equal characters; report
only full matches. Advance the candidate start by one to keep overlaps.
Every possible start is visited and only fully equal substrings are output,
proving completeness and soundness. For m=0, return all n+1 boundaries,
including n. If m>n, return an empty list.

**Time:** O(n*m+n+1), including empty-pattern output. **Auxiliary memory:**
O(1), plus O(z+1) returned positions. **Test:** `aaaaa`/`aaa` returns
`[0,1,2]`; an empty pattern also checks the final boundary.

### 2.5 `kmp` — prefix function and streaming matching

`pi[i]` stores the length of the longest proper prefix of the pattern that
is a suffix of the prefix ending at i. Initialize `pi[0]=0`. For each later
position start at `pi[i-1]`, follow `pi[j-1]` on mismatches, then increment
on equality. During text scanning, `matched` is the length of the longest
pattern prefix ending at the current text position. Failure-link fallback
preserves all feasible shorter prefixes without rescanning text. On a full
match, report `i-m+1` and set `matched=pi[m-1]` so overlapping occurrences
are also found. The invariant proves each reported start is correct and that
no possible match is skipped.

The empty pattern is handled before any pattern indexing. **Time:**
O(n+m+z+1). **Auxiliary memory:** O(m+1) prefix array, plus O(z+1) output.
**Test:** `abababa`/`aba` gives `[0,2,4]`, and text length 200,000 with
pattern length 100,000 gives exactly 100,001 matches.

### 2.6 `rk` — exact Rabin–Karp with collision verification

Use base 31, modulus 101 and the ASCII code of every character. The
leftmost symbol has the highest polynomial power. For pattern length m,
let `highest=31^(m-1) mod 101`. The window hash is updated by:

`next=((current-code(outgoing)*highest)*31+code(incoming)) mod 101`.

Java's `%` can return a negative residue, so add 101 if necessary. A hash
match is only a *candidate*: verify all m characters before reporting.
The rolling fingerprint visits every window, and exact verification
eliminates false positives, so output matches the naive baseline even under
collisions. Empty and longer-than-text patterns are handled before a window
is formed.

**Time:** O(n+m+c*m+z) for c equal-hash windows; worst case
O(n*m+n+m+1), **not** deterministic linear time. **Auxiliary memory:**
O(1) for rolling variables, plus O(z+1) output. **Test:** in `abdj`/`ab`,
`ab` and `dj` both hash to 75; only offset 0 is reported.

### 2.7 `suffix` — doubling order and Kasai LCP

Store suffix indices in `Integer[]`, with initial rank equal to each
character code. In round k, use Java `Arrays.sort` with a comparator over
`(rank[i], rank[i+k])`, treating missing second halves as -1. Compact equal
pairs to a new rank and double k. Ranks after a round order the first 2k
characters, because both k-character halves are already ranked. Induction
proves suffix order when all n ranks become distinct. This is comparison
sorting on indices, not construction of all suffix strings; no empty suffix
or sentinel is included.

For Kasai LCP, `inverse[sa[i]]=i`. Iterate text starts; compare each suffix
with its predecessor in lexicographic order, reusing at least the previous
matched length minus one. This allows at most O(n) total character-extension
work. Set `lcp[0]=0`. The maximum adjacent LCP gives a longest repeated
substring: if nonadjacent suffixes have a common prefix, all suffixes
between them in lexicographic order share it, so some adjacent pair has at
least that prefix length. Save any maximal witness, including overlaps.

**Time:** O(n log²(n+1)) for comparison-based doubling plus O(n) Kasai.
**Auxiliary memory:** O(n+1) for index wrappers, comparator keys, ranks,
inverse and LCP; output SA/LCP/repeated is O(n+1). **Test:** `banana`
gives SA `[5,3,1,0,4,2]`, LCP `[0,1,3,0,0,2]`, repeated `ana`.
50,000 copies of `a` test the repetitive worst case and sentinel handling.

### 2.8 `aho` — Aho–Corasick dictionary counting

Insert dictionary patterns in one trie over the fixed 95 printable ASCII
characters. Each trie node has an `int[95]` transition row. Multiple equal
patterns share a terminal state but retain their separate original-position
columns. Breadth-first traversal constructs failure links, filling all
missing transitions from the failure state's completed transition row; root
missing transitions lead to root. Consequently each text character makes
one constant-time automaton transition.

For each separate text, initialize a fresh count array, traverse the text,
and count visits to states reached. In reverse BFS order add a state's
visits to its failure parent. Every state then receives matches from states
for which it is a suffix destination. Reading the count at each dictionary
terminal correctly includes suffix patterns, duplicates and overlapping
matches without reporting individual occurrences. Empty texts produce zero
rows; an empty dictionary produces one empty row per text; no texts produce
`[]`. All automaton and visit state resets between requests.

For total pattern length L, dictionary size p, S trie nodes and q texts:
**Time:** O(L+95*S+p + sum over texts (|text|+S+p)). **Auxiliary memory:**
O(95*S+S+p) for trie, failure links, queue and one visit array, excluding
input and O(q*p) output. There is no quadratic copying of inherited output
lists. **Test:** `[he,she,he]` against `sheshe` yields `[2,2,2]`.
A 100,000-node trie with a 200,000-character text and 100 dense texts check
the upper published limits.

## 3. Independent tests and evidence

The test file `tests/student/test_algorithms.py` uses independent reference
methods: exhaustive subsequence enumeration for small LCS, exhaustive
adjacent-merge search, exhaustive subset enumeration for small trees,
Python's `startswith` **only in the test reference**, literal suffix sorting
for short strings, and direct per-pattern reference counts for Aho. Java
algorithm methods do not use a library substring-search replacement. The
test file sends JSONL batches to compiled Java `Main` and validates exact
output schemas and optimal witnesses.

|Command|Input/fixture|Expected|Actual observation|What it detects|
|---|---|---|---|---|
|lcs|`tea_time`, `team`; 80 random pairs; empties|Optimal length and common subsequence|pass|noncontiguous witnesses, ties, empty cases|
|lcs|1,000 `x` in each string|length 1,000|pass|million DP cells|
|merge|All sizes in `{0,1,2}` up to n=6 (1,093 cases)|Exhaustive optimum and valid chronological plan|pass|ties, adjacency, order|
|merge|200 blocks, each `10^9`|cost 1,544 × `10^9`|pass|64-bit costs, large witness|
|tree|330 small random trees, weights -7..7|Exhaustive maximum and independent witness|pass|negative/tied weights|
|tree|100,000-node chain of weight `10^9`|50,000 × `10^9`|pass|depth, 64-bit sum|
|naive/kmp/rk|300 random string/pattern pairs plus boundaries|Exact starts identical to reference|pass|case, spaces, overlaps|
|rk|`abdj` / `ab`|positions `[0]`|pass|mod-101 collision|
|kmp|200,000 `a` with 100,000 `a` pattern|100,001 matches|pass|max domain and overlap|
|suffix|`banana`, 150 random short strings|Exact suffix order, LCP, valid longest repeat|pass|Kasai and witness|
|suffix|50,000 `a`|Reverse SA, LCP 0..49999, repeat length 49,999|pass|doubling scale|
|aho|Duplicates, suffix patterns, 100 random dictionaries|Direct reference counts in each text|pass|overlaps, duplicates, reset|
|aho|100,000 `a` pattern / 200,000 `a` text|100,001 occurrences|pass|large automaton|
|aho|400 nested `a` patterns / 100 long texts|Each row has counts 2000..1601|pass|dense suffix matches and multi-text reset|
|all text commands|all 95 printable ASCII characters, quotes and backslashes|Exact JSONL round-trip, searches and witnesses|pass|escaping and alphabet boundaries|
|all|Five mixed JSONL requests repeated three times|Identical output blocks and exact line count|pass|stateless request handling|

**Observed student-test result:** 15 test methods, 15 passed; 1.723 seconds
on the measured host. **Observed teacher validation:** 44/44 individual
cases and the complete repeated mixed batch passed. Tests are repeatable
with fixed PRNG seeds 841..845 and no network dependencies.

## 4. Java search comparison

**Reproduce** after compiling `Algorithms.java`:

```bash
javac --release 17 -cp Java/build -d Java/build tests/student/BenchmarkSearch.java
java -Xmx384m -cp Java/build BenchmarkSearch
```

`BenchmarkSearch.java` invokes Java methods directly, excluding JSON parsing,
file I/O, JVM startup, compilation, fixture generation and reference checking.
All three algorithms receive the same preconstructed input for each row.
The fixed seed is **20261108**, and random instances use Java
`java.util.Random(seed + 10000*n + m)` to draw independent letters uniformly
from `a..z`. The pattern is a length-m substring taken at offset `n/3`:
these measured fixtures each have one match. Repetitive instances use text
`a` repeated n times and pattern `a` repeated m times, giving n-m+1 matches.
All sizes (300/16, 900/48, 2400/96) are in the three searches' common domain.

For each algorithm and fixture, a separate `String.regionMatches` oracle
verifies output **before** measurements. Warm up with **1,500 untimed calls**
per configuration to allow JIT compilation; then record **three timed runs**
of 250 calls each. Timed scope includes the Java algorithm, output creation,
and a constant-size result-count retrieval, with checksum use to prevent
dead-code elimination. Values are per-call milliseconds, computed from
`System.nanoTime`; the median is over the three run averages. JIT remains
enabled (OpenJDK default); adaptive compilation and machine noise can still
affect timings despite warm-up.

|Case|n|m|Matches|Algorithm|Run 1 ms|Run 2 ms|Run 3 ms|Median ms|Verified|
|---|---:|---:|---:|---|---:|---:|---:|---:|---|
|random|300|16|1|naive|0.0062|0.0056|0.0055|0.0056|yes|
|random|300|16|1|kmp|0.0045|0.0044|0.0048|0.0045|yes|
|random|300|16|1|rk|0.0048|0.0053|0.0054|0.0053|yes|
|random|900|48|1|naive|0.0170|0.0048|0.0027|0.0048|yes|
|random|900|48|1|kmp|0.0190|0.0186|0.0190|0.0190|yes|
|random|900|48|1|rk|0.0074|0.0062|0.0053|0.0062|yes|
|random|2400|96|1|naive|0.0082|0.0086|0.0093|0.0086|yes|
|random|2400|96|1|kmp|0.0044|0.0041|0.0036|0.0041|yes|
|random|2400|96|1|rk|0.0114|0.0108|0.0115|0.0114|yes|
|repetitive|300|16|285|naive|0.0080|0.0079|0.0076|0.0079|yes|
|repetitive|300|16|285|kmp|0.0092|0.0094|0.0093|0.0093|yes|
|repetitive|300|16|285|rk|0.0057|0.0071|0.0064|0.0064|yes|
|repetitive|900|48|853|naive|0.0440|0.0428|0.0432|0.0432|yes|
|repetitive|900|48|853|kmp|0.0118|0.0130|0.0116|0.0118|yes|
|repetitive|900|48|853|rk|0.0246|0.0248|0.0245|0.0246|yes|
|repetitive|2400|96|2305|naive|0.2227|0.2363|0.2307|0.2307|yes|
|repetitive|2400|96|2305|kmp|0.0198|0.0207|0.0236|0.0207|yes|
|repetitive|2400|96|2305|rk|0.0877|0.0946|0.0855|0.0877|yes|

When nearly all windows match the repetitive pattern, naive repeatedly
compares m characters, and exact RK also verifies m characters after each
hash match. KMP avoids these repeated comparisons and is substantially
faster at the largest repetitive size. For random text, most naive starts
fail quickly and fixed JVM overhead, JIT transitions and small input sizes
make the observed ranking less consistent. Modulus 101 creates additional
possible false hash candidates for RK; every candidate is still checked.
These measurements illustrate the implementation's behaviour but **cannot
prove** asymptotic complexity, and modulus 101 is not representative of all
production rolling-hash choices.

## 5. Sources and AI-assistance disclosure

- **Course sources:** the instructor's supplied Assignment 4 archive,
  especially the English assignment specification, the exact request
  contracts, rubric and testing guide. Original Java parser, main transport,
  test launcher, public cases, metadata and GitHub workflow structure were
  used as the template; the workflow now checks only the chosen Java language.
- **AI assistance:** OpenAI ChatGPT assisted with the Java implementations,
  independent Python test harness, Java benchmarking class, explanations,
  submission README, and diagnosis of local Java compiler availability.
- **Verification performed with these tools:** compiling the Java files,
  running the original 44 public validations and 88-request mixed batch,
  running 15 separate test methods with brute-force reference checks and
  maximum-domain inputs, and recording the Java measurements above.
  All the test and benchmark source is included so another person can
  reproduce these observations.
- **Student responsibility:** this records tool-assisted verification, not
  a false claim of unaided student authorship or personal manual verification.
  The student should personally review, understand and independently test
  this material before submission, acknowledge AI assistance under course
  rules, and complete the individual English defence without unauthorized
  assistance.
