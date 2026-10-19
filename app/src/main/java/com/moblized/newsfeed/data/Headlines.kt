package com.moblized.newsfeed.data

import java.util.Locale

/**
 * DeArrow-style headline cleanup for news. DeArrow's crowd-sourced titles only exist for
 * YouTube, so here the "crowd" is the other outlets covering the same story: we strip clickbait
 * framing from every headline and, when several outlets cover a story, pick the plainest and
 * most representative one.
 */
object Headlines {

    private val leadingTag = Regex(
        "^\\s*[\\[(]?(breaking(?: news)?|just in|watch(?: live)?|live(?: updates?)?|exclusive|update[d]?|video|photos?|" +
            "developing|must[- ]see|shock(?:ing)?|revealed|new|listen|pics|in pictures)[\\])]?\\s*[:|\\-–—]+\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val trailingTag = Regex(
        "\\s*[\\[(|]\\s*(video|watch|photos?|pics|live|updated?|exclusive|gallery|podcast)\\s*[\\])]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val trailingTease = Regex(
        "\\s*(?:[-–—:,;.]\\s*|\\s+and\\s+)(here'?s|here is) (why|what|how|who|when|where|the reason)[^.?!]*[.?!]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val trailingTease2 = Regex(
        "\\s*[-–—:,;]\\s*(what (we|you) (know|need to know)|what to know|what it means|why it matters|the details|details inside|see (it|the video|photos))[^.?!]*[.?!]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val hypeAdjectives = Regex(
        "\\b(shocking|stunning|jaw[- ]dropping|mind[- ]blowing|unbelievable|bombshell)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}\\x{FE0F}\\x{200D}\\x{2B50}\\x{2B55}\\x{2934}\\x{2935}\\x{3030}\\x{303D}]")
    private val repeatedPunct = Regex("([!?])[!?]+")
    private val shout = Regex("\\b[A-Z]{5,}\\b")
    private val spaces = Regex("\\s+")

    /** Cleans one headline. Never returns an empty string. */
    fun clean(title: String): String {
        val original = title.trim()
        var s = original
        s = emoji.replace(s, "")
        repeat(2) { s = leadingTag.replace(s, "") }
        s = trailingTag.replace(s, "")
        s = trailingTease.replace(s, "")
        s = trailingTease2.replace(s, "")
        s = hypeAdjectives.replace(s, "")
        s = repeatedPunct.replace(s, "$1")
        s = s.trimEnd().removeSuffix("!").trimEnd()
        s = calmCaps(s)
        s = spaces.replace(s, " ").trim().trimEnd(':', '-', '–', '—', '|', ',', ';').trim()
        if (s.length < 12) return original
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /** Turns shouted words into normal ones; a fully upper-case headline becomes sentence case. */
    private fun calmCaps(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.length >= 12 && letters.count { it.isUpperCase() } > letters.length * 0.8) {
            val lower = s.lowercase(Locale.getDefault())
            return lower.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        return shout.replace(s) { m ->
            val w = m.value
            "${w.first()}${w.drop(1).lowercase(Locale.getDefault())}"
        }
    }

    private val hypeWords = setOf(
        "slams", "blasts", "rips", "torches", "destroys", "eviscerates", "shreds", "obliterates", "humiliates",
        "shocking", "stunning", "wild", "insane", "epic", "meltdown", "bombshell", "unhinged", "furious",
        "erupts", "chaos", "outrage", "explodes", "brutal", "savage", "mocks", "owned", "goes viral", "viral",
    )

    /** Higher means more clickbait-y. */
    fun clickbaitScore(title: String): Double {
        val t = title.trim()
        val lower = t.lowercase(Locale.ROOT)
        var score = 0.0
        if (t.contains('?')) score += 1.5
        if (t.contains('!')) score += 1.5
        if (Regex("\\byou(r|'re|'ll)?\\b").containsMatchIn(lower)) score += 2.0
        if (Regex("^(this|these|here'?s|watch|why|how)\\b").containsMatchIn(lower)) score += 1.5
        if (Regex("^\\d+\\s+(things|ways|reasons|times|signs|photos|facts)").containsMatchIn(lower)) score += 2.0
        if (t.contains("...") || t.contains('…')) score += 1.0
        if (leadingTag.containsMatchIn(t)) score += 1.0
        if (trailingTease.containsMatchIn(t) || trailingTease2.containsMatchIn(t)) score += 1.5
        score += shout.findAll(t).count() * 0.7
        score += hypeWords.count { lower.contains(it) } * 1.0
        if (t.length < 35) score += 0.5
        if (t.length > 150) score += 0.5
        return score
    }

    /**
     * Picks the headline that best represents what most outlets are saying, with the least
     * clickbait. Returns the original (uncleaned) headline of the chosen article.
     */
    fun consensus(titles: List<String>, preferred: Set<Int> = emptySet()): Int {
        if (titles.size <= 1) return 0
        val tokens = titles.map { StoryBuilder.tokens(it) }
        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in titles.indices) {
            val ti = tokens[i]
            if (ti.isEmpty()) continue
            var rep = 0.0
            for (j in titles.indices) {
                if (i == j) continue
                val tj = tokens[j]
                if (tj.isEmpty()) continue
                val shared = ti.count { it in tj }
                rep += shared.toDouble() / (ti.size + tj.size - shared)
            }
            rep /= (titles.size - 1)
            val score = rep * 4.0 - clickbaitScore(titles[i]) * 0.6 + if (i in preferred) 0.3 else 0.0
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }
}
