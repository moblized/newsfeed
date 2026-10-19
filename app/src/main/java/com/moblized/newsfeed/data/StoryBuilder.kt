package com.moblized.newsfeed.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** One news event, with every outlet's article about it. */
data class Story(
    val id: String,
    val lead: Article,
    /** Headline picked across outlets (original text; cleaned for display when DeArrow mode is on). */
    val consensusTitle: String,
    val image: String?,
    val articles: List<Article>,
    val category: Category,
    val latest: Long,
    val outlets: Int,
    val left: Int,
    val center: Int,
    val right: Int,
    /** The side that is barely covering this story, if any (Ground News calls it a Blindspot). */
    val blindspot: Side?,
    val score: Double,
) {
    val rated: Int get() = left + center + right
}

object StoryBuilder {

    const val ENRICH_SOURCE = "enrich"

    private const val PAIR_THRESHOLD = 0.33
    private const val MAX_CLUSTER = 90
    private const val WINDOW_MS = 48L * 3_600_000L

    private val STOP = setOf(
        "the", "and", "for", "are", "was", "were", "with", "that", "this", "these", "those", "from", "into", "onto",
        "over", "under", "after", "before", "about", "amid", "than", "then", "they", "them", "their", "there", "his",
        "her", "hers", "him", "its", "our", "your", "you", "has", "have", "had", "not", "but", "all", "any", "out",
        "off", "more", "most", "some", "just", "now", "get", "gets", "got", "one", "two", "new", "news", "says", "say",
        "said", "how", "why", "what", "who", "whom", "when", "where", "which", "will", "would", "could", "should",
        "can", "may", "might", "must", "been", "being", "also", "against", "between", "during", "without", "within",
        "while", "because", "like", "upon", "per", "via", "yet", "still", "back", "make", "makes", "made", "take",
        "takes", "took", "big", "top", "latest", "breaking", "exclusive", "live", "update", "updates", "video",
        "watch", "photos", "report", "reports", "here", "heres", "first", "last", "year", "years", "day", "days",
        "week", "weeks", "today", "tonight", "told", "tells", "people", "man", "woman", "set", "sets", "amp", "nbsp",
        "does", "did", "doing", "dont", "isnt", "cant", "wont", "didnt", "doesnt", "he", "she", "it", "us", "we",
        "his", "who", "out", "way", "ways", "know", "need", "see", "look", "looks", "call", "calls", "called",
    )

    private val DEMONYMS = mapOf(
        "iranian" to "iran", "russian" to "russia", "ukrainian" to "ukraine", "chinese" to "china",
        "israeli" to "israel", "palestinian" to "palestine", "mexican" to "mexico", "canadian" to "canada",
        "venezuelan" to "venezuela", "syrian" to "syria", "korean" to "korea", "japanese" to "japan",
        "german" to "germany", "french" to "france", "british" to "britain", "european" to "europe",
        "lebanese" to "lebanon", "egyptian" to "egypt", "turkish" to "turkey", "afghan" to "afghanistan",
        "pakistani" to "pakistan", "indian" to "india", "cuban" to "cuba", "haitian" to "haiti",
        "american" to "america", "taiwanese" to "taiwan", "yemeni" to "yemen", "iraqi" to "iraq",
    )

    private val splitRe = Regex("[^a-z0-9']+")

    fun tokens(text: String): Set<String> {
        val out = HashSet<String>()
        val normalized = text.lowercase(Locale.ROOT).replace('’', '\'').replace('‘', '\'')
        for (raw in splitRe.split(normalized)) {
            var w = raw.trim('\'')
            if (w.endsWith("'s")) w = w.dropLast(2)
            w = w.replace("'", "")
            val numeric = w.isNotEmpty() && w.all { it.isDigit() }
            if (w.length < 3 && !(numeric && w.length == 2)) continue
            if (w in STOP) continue
            out.add(stem(DEMONYMS[w] ?: w))
        }
        return out
    }

    private fun stem(word: String): String {
        if (word.all { it.isDigit() }) return word
        var s = word
        s = when {
            s.length > 4 && s.endsWith("ies") -> s.dropLast(3) + "y"
            s.length > 4 && (s.endsWith("sses") || s.endsWith("shes") || s.endsWith("ches") || s.endsWith("xes")) -> s.dropLast(2)
            s.length > 3 && s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us") && !s.endsWith("is") -> s.dropLast(1)
            else -> s
        }
        s = when {
            s.length > 6 && s.endsWith("ing") -> s.dropLast(3)
            s.length > 5 && s.endsWith("ed") -> s.dropLast(2)
            else -> s
        }
        if (s.length > 3 && s.endsWith("e")) s = s.dropLast(1)
        return s
    }

    /** A short Google News query that finds other outlets' versions of this headline. */
    fun searchQuery(title: String): String {
        val words = title.replace('’', '\'').split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' && c != '-' } }
            .filter { it.isNotEmpty() }
        data class Cand(val word: String, val index: Int, val cap: Boolean)
        val cands = words.mapIndexedNotNull { i, w ->
            val t = w.lowercase(Locale.ROOT).replace("'s", "").replace("'", "")
            if (t.length < 3 || t in STOP) null else Cand(w.removeSuffix("'s"), i, w.first().isUpperCase() || w.first().isDigit())
        }
        val picked = cands.sortedWith(compareByDescending<Cand> { it.cap }.thenBy { it.index }).take(5).sortedBy { it.index }
        return picked.joinToString(" ") { it.word }
    }

    /** True when a search result is about the same thing as the story. */
    fun matches(storyTokens: Set<String>, title: String): Boolean {
        val t = tokens(title)
        if (t.isEmpty()) return false
        val shared = t.count { it in storyTokens }
        return shared >= 2 && shared.toDouble() / t.size >= 0.34
    }

    fun storyTokens(story: Story): Set<String> =
        story.articles.take(12).flatMapTo(HashSet()) { tokens(it.title) }

    fun build(articles: List<Article>, resolver: BiasResolver, now: Long = System.currentTimeMillis()): List<Story> {
        val n = articles.size
        if (n == 0) return emptyList()

        val toks: List<Set<String>> = articles.map { tokens(it.title) }
        val df = HashMap<String, Int>()
        for (set in toks) for (t in set) df[t] = (df[t] ?: 0) + 1
        val idf = HashMap<String, Double>(df.size * 2)
        for ((t, c) in df) idf[t] = ln((n + 1.0) / (c + 0.5))
        val norms = DoubleArray(n) { i -> sqrt(toks[i].sumOf { val w = idf[it] ?: 0.0; w * w }) }

        val parent = IntArray(n) { it }
        val size = IntArray(n) { 1 }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        fun union(a: Int, b: Int, capped: Boolean) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            if (capped && size[ra] + size[rb] > MAX_CLUSTER) return
            parent[rb] = ra
            size[ra] += size[rb]
        }

        // 1) Groups we already know about: Google News clusters and earlier coverage searches.
        val indexById = HashMap<String, Int>(n * 2)
        articles.forEachIndexed { i, a -> if (!indexById.containsKey(a.id)) indexById[a.id] = i }
        val firstInGroup = HashMap<String, Int>()
        articles.forEachIndexed { i, a ->
            val g = a.groupKey ?: return@forEachIndexed
            indexById[g]?.let { union(it, i, capped = false) }
            val first = firstInGroup[g]
            if (first == null) firstInGroup[g] = i else union(first, i, capped = false)
        }

        // 2) Headline similarity, comparing only articles that share a reasonably rare word.
        val maxDf = maxOf(8, n / 15)
        val postings = HashMap<String, MutableList<Int>>()
        toks.forEachIndexed { i, set ->
            for (t in set) {
                val c = df[t] ?: 0
                if (c in 2..maxDf) postings.getOrPut(t) { ArrayList() }.add(i)
            }
        }
        val seen = HashSet<Long>()
        val pairs = ArrayList<Pair<Long, Double>>()
        for (list in postings.values) {
            for (x in 0 until list.size) {
                for (y in x + 1 until list.size) {
                    val i = list[x]
                    val j = list[y]
                    val key = i.toLong() * n + j
                    if (!seen.add(key)) continue
                    val ti = articles[i].publishedAt
                    val tj = articles[j].publishedAt
                    if (ti > 0 && tj > 0 && abs(ti - tj) > WINDOW_MS) continue
                    val sim = similarity(toks[i], toks[j], idf, norms[i], norms[j])
                    if (sim >= PAIR_THRESHOLD) pairs.add(key to sim)
                }
            }
        }
        pairs.sortByDescending { it.second }
        for ((key, _) in pairs) union((key / n).toInt(), (key % n).toInt(), capped = true)

        val groups = HashMap<Int, MutableList<Article>>()
        for (i in 0 until n) groups.getOrPut(find(i)) { ArrayList() }.add(articles[i])
        return groups.values.map { makeStory(it, resolver, now) }.sortedByDescending { it.score }
    }

    private fun similarity(a: Set<String>, b: Set<String>, idf: Map<String, Double>, na: Double, nb: Double): Double {
        if (na == 0.0 || nb == 0.0) return 0.0
        val small = if (a.size <= b.size) a else b
        val large = if (a.size <= b.size) b else a
        var dot = 0.0
        var shared = 0
        for (t in small) {
            if (t in large) {
                val w = idf[t] ?: 0.0
                dot += w * w
                shared++
            }
        }
        if (shared < 2) return 0.0
        return dot / (na * nb)
    }

    private fun makeStory(items: List<Article>, resolver: BiasResolver, now: Long): Story {
        val sorted = items.sortedByDescending { it.publishedAt }
        val primary = items.filter { it.groupKey == null && it.sourceId != ENRICH_SOURCE }.ifEmpty { items }
        val lead = primary.maxWith(
            compareBy<Article>(
                { if (it.imageUrl != null) 1 else 0 },
                { if (it.summary.isNotBlank()) 1 else 0 },
                { if (resolver.bias(it) == Bias.CENTER) 1 else 0 },
                { it.publishedAt },
            )
        )
        val image = lead.imageUrl ?: items.firstOrNull { it.imageUrl != null }?.imageUrl

        val category = items.map { it.category }
            .filter { it != Category.OTHER }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: lead.category

        // Count each outlet once.
        val outlets = LinkedHashMap<String, Bias?>()
        for (a in items) {
            val r = resolver.resolve(a)
            if (!outlets.containsKey(r.key)) outlets[r.key] = r.bias
        }
        val biases = outlets.values.filterNotNull()
        val left = biases.count { it.side == Side.LEFT }
        val center = biases.count { it.side == Side.CENTER }
        val right = biases.count { it.side == Side.RIGHT }
        val rated = left + center + right
        val blindspot = when {
            rated < 5 -> null
            left <= rated * 0.15 && right >= rated * 0.5 && right >= 3 -> Side.LEFT
            right <= rated * 0.15 && left >= rated * 0.5 && left >= 3 -> Side.RIGHT
            else -> null
        }

        // Consensus headline across outlets (one headline per outlet, wire/center slightly preferred).
        val perOutlet = LinkedHashMap<String, Article>()
        for (a in sorted) {
            val key = resolver.resolve(a).key
            if (!perOutlet.containsKey(key)) perOutlet[key] = a
        }
        val candidates = perOutlet.values.toList().take(25)
        val preferred = candidates.indices.filter { resolver.bias(candidates[it]) == Bias.CENTER }.toSet()
        val consensus = if (candidates.size >= 3) {
            candidates[Headlines.consensus(candidates.map { it.title }, preferred)].title
        } else {
            lead.title
        }

        val latest = items.maxOf { it.publishedAt }
        val ageHours = if (latest > 0) ((now - latest) / 3_600_000.0).coerceAtLeast(0.0) else 24.0
        val score = ln(1.0 + outlets.size) * 4.0 - ageHours / 5.0

        return Story(
            id = lead.id,
            lead = lead,
            consensusTitle = consensus,
            image = image,
            articles = sorted,
            category = category,
            latest = latest,
            outlets = outlets.size,
            left = left,
            center = center,
            right = right,
            blindspot = blindspot,
            score = score,
        )
    }
}
