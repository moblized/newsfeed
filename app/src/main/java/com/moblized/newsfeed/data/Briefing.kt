package com.moblized.newsfeed.data

import org.json.JSONObject
import java.util.Locale

/** One topic in the daily brief, filled from a Google News search plus matching feed stories. */
data class BriefTopic(
    val id: String,
    val section: String,
    val title: String,
    val query: String,
    val count: Int = 2,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("section", section).put("title", title).put("query", query).put("count", count)

    companion object {
        fun fromJson(o: JSONObject) = BriefTopic(
            id = o.optString("id"),
            section = o.optString("section"),
            title = o.optString("title"),
            query = o.optString("query"),
            count = o.optInt("count", 2).coerceIn(1, 5),
        )
    }
}

object Briefing {

    const val CONFLICT = "🛡️ Global Conflict Briefings"
    const val SPACE = "🚀 Space & Fundamental Physics"

    val defaultTopics = listOf(
        BriefTopic("hormuz", CONFLICT, "Strait of Hormuz & Maritime Safety",
            "\"Strait of Hormuz\" OR \"Gulf of Oman\" OR \"maritime security\"", 2),
        BriefTopic("ukraine", CONFLICT, "Russia-Ukraine War", "Russia Ukraine war", 2),
        BriefTopic("space", SPACE, "Space Exploration",
            "NASA OR SpaceX OR \"rocket launch\" OR astronomers", 1),
        BriefTopic("physics", SPACE, "Physics & Frontier Science",
            "physicists OR \"particle physics\" OR \"quantum physics\"", 1),
    )

    /** Each OR branch is a list of words/phrases that must all appear. */
    private fun branches(query: String): List<List<String>> {
        val phraseRe = Regex("\"([^\"]+)\"|(\\S+)")
        return query.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE)).map { branch ->
            phraseRe.findAll(branch).map { m ->
                (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase(Locale.ROOT)
            }.filter { it.isNotBlank() && !it.startsWith("when:") }.toList()
        }.filter { it.isNotEmpty() }
    }

    fun matches(query: String, article: Article): Boolean {
        val text = (article.title + " " + article.summary).lowercase(Locale.ROOT)
        return branches(query).any { terms -> terms.all { text.contains(it) } }
    }

    /** Picks the most widely covered distinct stories for a topic. */
    fun topStories(topic: BriefTopic, candidates: List<Article>, resolver: BiasResolver): List<Story> {
        val stories = StoryBuilder.build(candidates.distinctBy { it.link }, resolver)
        val now = System.currentTimeMillis()
        return stories
            .sortedByDescending { s ->
                val ageHours = if (s.latest > 0) (now - s.latest) / 3_600_000.0 else 48.0
                s.outlets * 1.0 - ageHours / 12.0 + if (s.lead.summary.isNotBlank()) 0.5 else 0.0
            }
            .take(topic.count)
    }

    /** First useful summary among a story's articles. */
    fun summaryOf(story: Story): String =
        (listOf(story.lead) + story.articles).firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty()
}
