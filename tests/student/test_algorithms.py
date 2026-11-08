"""Independent Java-solution oracles and published-domain scale checks.

Run after (or independently of) the instructor's launcher:
    python -m unittest discover -s tests/student -p 'test_*.py' -v
"""

import itertools
import json
import random
import subprocess
import unittest
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def java_batch(requests):
    if not (ROOT / "Java/build/Main.class").is_file():
        sources = sorted(map(str, (ROOT / "Java/source").glob("*.java")))
        (ROOT / "Java/build").mkdir(exist_ok=True)
        subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8",
                        "-d", str(ROOT / "Java/build"), *sources], check=True, timeout=60)
    payload = "".join(json.dumps(request, ensure_ascii=True) + "\n" for request in requests)
    result = subprocess.run(["java", "-Xmx384m", "-cp", str(ROOT / "Java/build"), "Main"],
                            input=payload, text=True, capture_output=True, timeout=20,
                            check=True, encoding="utf-8")
    assert result.stderr == "", result.stderr
    rows = [json.loads(line) for line in result.stdout.splitlines()]
    assert len(rows) == len(requests), (len(rows), len(requests))
    return rows


def subsequence(needle, haystack):
    position = 0
    for char in needle:
        position = haystack.find(char, position)
        if position == -1:
            return False
        position += 1
    return True


def lcs_oracle(a, b):
    return max((len(chars) for count in range(len(a) + 1)
                for chars in itertools.combinations(a, count)
                if subsequence(chars, b)), default=0)


@lru_cache(None)
def merge_oracle(sizes):
    if len(sizes) <= 1:
        return 0
    return min(sizes[i] + sizes[i + 1]
               + merge_oracle(sizes[:i] + (sizes[i] + sizes[i + 1],) + sizes[i + 2:])
               for i in range(len(sizes) - 1))


def verify_merge(sizes, answer):
    blocks = [(i, i, weight) for i, weight in enumerate(sizes)]
    cost = 0
    for left, middle, right in answer["plan"]:
        valid = False
        for index in range(len(blocks) - 1):
            a, b = blocks[index:index + 2]
            if a[:2] == (left, middle) and b[:2] == (middle + 1, right):
                weight = a[2] + b[2]
                cost += weight
                blocks[index:index + 2] = [(left, right, weight)]
                valid = True
                break
        assert valid, [left, middle, right]
    assert len(answer["plan"]) == max(len(sizes) - 1, 0)
    assert len(blocks) == min(len(sizes), 1)
    assert cost == answer["cost"]


def tree_oracle(weights, edges):
    best = 0
    for mask in range(1 << len(weights)):
        if all(not ((mask >> u & 1) and (mask >> v & 1)) for u, v in edges):
            best = max(best, sum(w for v, w in enumerate(weights) if mask >> v & 1))
    return best


def verify_tree(weights, edges, answer):
    chosen = answer["selected"]
    ids = set(chosen)
    assert len(ids) == len(chosen)
    assert all(0 <= vertex < len(weights) for vertex in chosen)
    assert all(not (u in ids and v in ids) for u, v in edges)
    assert sum(weights[i] for i in chosen) == answer["weight"]


def search_oracle(text, pattern):
    if not pattern:
        return list(range(len(text) + 1))
    return [i for i in range(len(text) - len(pattern) + 1)
            if text.startswith(pattern, i)]


def suffix_oracle(text):
    sa = sorted(range(len(text)), key=lambda i: text[i:])
    lcp = [0] * len(text)
    for index in range(1, len(text)):
        a, b = text[sa[index - 1]:], text[sa[index]:]
        while lcp[index] < min(len(a), len(b)) and a[lcp[index]] == b[lcp[index]]:
            lcp[index] += 1
    return sa, lcp


class SmallIndependentOracles(unittest.TestCase):
    def test_lcs_optimal_witnesses(self):
        rng = random.Random(841)
        pairs = [("", ""), ("abc", ""), ("", "a"), ("tea_time", "team"),
                 ("ab", "ba"), ("aaaa", "aa")]
        pairs += [("".join(rng.choices("ab c", k=rng.randrange(8))),
                   "".join(rng.choices("ab c", k=rng.randrange(8)))) for _ in range(80)]
        outputs = java_batch([{"task": "lcs", "a": a, "b": b} for a, b in pairs])
        for (a, b), result in zip(pairs, outputs):
            with self.subTest(a=a, b=b):
                self.assertEqual(result["length"], lcs_oracle(a, b))
                self.assertEqual(len(result["sequence"]), result["length"])
                self.assertTrue(subsequence(result["sequence"], a))
                self.assertTrue(subsequence(result["sequence"], b))

    def test_all_small_adjacent_merges(self):
        cases = [sizes for n in range(7) for sizes in itertools.product(range(3), repeat=n)]
        outputs = java_batch([{"task": "merge", "sizes": sizes} for sizes in cases])
        for sizes, answer in zip(cases, outputs):
            with self.subTest(sizes=sizes):
                self.assertEqual(answer["cost"], merge_oracle(sizes))
                verify_merge(sizes, answer)

    def test_tree_independent_optima(self):
        rng = random.Random(842)
        cases = []
        for n in range(11):
            for _ in range(30):
                edges = [[i, rng.randrange(i)] for i in range(1, n)]
                rng.shuffle(edges)
                weights = [rng.randrange(-7, 8) for _ in range(n)]
                cases.append({"task": "tree", "n": n, "edges": edges, "weights": weights})
        for request, answer in zip(cases, java_batch(cases)):
            with self.subTest(request=request):
                self.assertEqual(answer["weight"], tree_oracle(request["weights"], request["edges"]))
                verify_tree(request["weights"], request["edges"], answer)

    def test_three_searches_against_independent_oracle(self):
        rng = random.Random(843)
        pairs = [("", ""), ("", "abc"), ("aaa", ""), ("aaaaa", "aaa"),
                 ("abdj", "ab"), ("x x X", " x"), ("aaaaa", "aaaaaa"), ("!?!?", "!?")]
        pairs += [("".join(rng.choices("aA !?xy", k=rng.randrange(31))),
                   "".join(rng.choices("aA !?xy", k=rng.randrange(18)))) for _ in range(300)]
        cases = [{"task": task, "text": text, "pattern": pattern}
                 for text, pattern in pairs for task in ("naive", "kmp", "rk")]
        for request, answer in zip(cases, java_batch(cases)):
            with self.subTest(request=request):
                self.assertEqual(answer["positions"],
                                 search_oracle(request["text"], request["pattern"]))

    def test_suffix_arrays_lcp_and_witness(self):
        rng = random.Random(844)
        texts = ["", "a", "banana", "aaaaaa", "abcdef", "abababa", "a a!a a!"]
        texts += ["".join(rng.choices("ab$ ?!", k=rng.randrange(38))) for _ in range(150)]
        for text, answer in zip(texts, java_batch([{"task": "suffix", "text": s} for s in texts])):
            with self.subTest(text=text):
                sa, lcp = suffix_oracle(text)
                self.assertEqual(answer["sa"], sa)
                self.assertEqual(answer["lcp"], lcp)
                repeated = answer["repeated"]
                self.assertEqual(len(repeated), max(lcp, default=0))
                if repeated:
                    self.assertGreaterEqual(sum(text.startswith(repeated, i)
                                                for i in range(len(text))), 2)

    def test_aho_duplicates_suffix_overlap_and_state_reset(self):
        rng = random.Random(845)
        pairs = [([], ["", "abc"]), (["he", "she", "he"], ["sheshe", "", "sheshe"]),
                 (["a", "aa", "aaa", "a"], ["aaaaa", "", "aaaaa"]),
                 (["a"], []), ([], [])]
        for _ in range(100):
            patterns = ["".join(rng.choices("ab! ", k=rng.randrange(1, 6)))
                        for _ in range(rng.randrange(9))]
            texts = ["".join(rng.choices("ab! ", k=rng.randrange(21)))
                     for _ in range(rng.randrange(5))]
            pairs.append((patterns, texts))
        cases = [{"task": "aho", "patterns": patterns, "texts": texts}
                 for patterns, texts in pairs]
        for request, answer in zip(cases, java_batch(cases)):
            expected = [[len(search_oracle(text, pattern)) for pattern in request["patterns"]]
                        for text in request["texts"]]
            with self.subTest(request=request):
                self.assertEqual(answer["counts"], expected)

    def test_one_process_mixed_requests_three_times(self):
        block = [{"task": "aho", "patterns": ["a", "a"], "texts": ["aaa"]},
                 {"task": "lcs", "a": "ab", "b": "ba"},
                 {"task": "suffix", "text": "banana"},
                 {"task": "aho", "patterns": [], "texts": ["abc", ""]},
                 {"task": "kmp", "text": "aaa", "pattern": "aa"}]
        results = java_batch(block * 3)
        self.assertEqual(results[:5], results[5:10])
        self.assertEqual(results[:5], results[10:])
        self.assertEqual(results[0], {"counts": [[3, 3]]})
        self.assertEqual(results[3], {"counts": [[], []]})
        self.assertEqual(results[4], {"positions": [0, 1]})

    def test_printable_ascii_quotes_and_backslashes(self):
        alphabet = "".join(chr(code) for code in range(32, 127))
        text = 'a"\\a a"\\a'
        pattern = 'a"\\a'
        cases = [
            {"task": task, "text": text, "pattern": pattern}
            for task in ("naive", "kmp", "rk")
        ] + [
            {"task": "lcs", "a": alphabet, "b": alphabet},
            {"task": "suffix", "text": alphabet + alphabet},
            {"task": "aho", "patterns": ['"', '\\', pattern, '!', pattern],
             "texts": [text, "", text]},
        ]
        results = java_batch(cases)
        for result in results[:3]:
            self.assertEqual(result["positions"], search_oracle(text, pattern))
        self.assertEqual(results[3], {"length": len(alphabet), "sequence": alphabet})
        sa, lcp = suffix_oracle(alphabet + alphabet)
        self.assertEqual(results[4]["sa"], sa)
        self.assertEqual(results[4]["lcp"], lcp)
        self.assertEqual(results[4]["repeated"], alphabet)
        self.assertEqual(results[5]["counts"],
                         [[len(search_oracle(t, p)) for p in cases[5]["patterns"]]
                          for t in cases[5]["texts"]])


class PublishedLimitChecks(unittest.TestCase):
    def test_lcs_1000_by_1000(self):
        (answer,) = java_batch([{"task": "lcs", "a": "x" * 1000, "b": "x" * 1000}])
        self.assertEqual(answer, {"length": 1000, "sequence": "x" * 1000})

    def test_merge_200_maximum_blocks(self):
        sizes = [10**9] * 200
        (answer,) = java_batch([{"task": "merge", "sizes": sizes}])
        self.assertEqual(answer["cost"], 1544 * 10**9)
        verify_merge(sizes, answer)

    def test_tree_deep_100000_vertex_chain(self):
        n = 100000
        weights = [10**9] * n
        edges = [[i, i + 1] for i in range(n - 1)]
        (answer,) = java_batch([{"task": "tree", "n": n, "weights": weights, "edges": edges}])
        self.assertEqual(answer["weight"], 50000 * 10**9)
        verify_tree(weights, edges, answer)

    def test_kmp_200000_text_and_100000_pattern(self):
        (answer,) = java_batch([{"task": "kmp", "text": "a" * 200000,
                                 "pattern": "a" * 100000}])
        self.assertEqual(len(answer["positions"]), 100001)
        self.assertEqual(answer["positions"][0], 0)
        self.assertEqual(answer["positions"][-1], 100000)

    def test_suffix_50000_equal_characters(self):
        n = 50000
        (answer,) = java_batch([{"task": "suffix", "text": "a" * n}])
        self.assertEqual(answer["sa"], list(range(n - 1, -1, -1)))
        self.assertEqual(answer["lcp"], list(range(n)))
        self.assertEqual(answer["repeated"], "a" * (n - 1))

    def test_aho_100000_states_200000_text(self):
        (answer,) = java_batch([{"task": "aho", "patterns": ["a" * 100000],
                                 "texts": ["a" * 200000]}])
        self.assertEqual(answer, {"counts": [[100001]]})

    def test_aho_100_dense_texts(self):
        patterns = ["a" * i for i in range(1, 401)]
        texts = ["a" * 2000 for _ in range(100)]
        (answer,) = java_batch([{"task": "aho", "patterns": patterns, "texts": texts}])
        self.assertEqual(len(answer["counts"]), 100)
        self.assertEqual(answer["counts"][0], [2001 - i for i in range(1, 401)])
        self.assertTrue(all(row == answer["counts"][0] for row in answer["counts"]))


if __name__ == "__main__":
    unittest.main()
