package com.moblized.newsfeed.data

/** Free, public RSS/Atom feeds. No API keys, no accounts. */
object DefaultSources {

    private fun s(id: String, name: String, url: String, category: Category) =
        Source(id, name, url, category)

    val all: List<Source> = listOf(
        // Top stories
        s("gnews-top", "Google News", "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", Category.TOP),
        s("bbc-top", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", Category.TOP),
        s("npr-top", "NPR", "https://feeds.npr.org/1001/rss.xml", Category.TOP),

        // World
        s("bbc-world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", Category.WORLD),
        s("guardian-world", "The Guardian", "https://www.theguardian.com/world/rss", Category.WORLD),
        s("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", Category.WORLD),
        s("npr-world", "NPR World", "https://feeds.npr.org/1004/rss.xml", Category.WORLD),

        // U.S.
        s("npr-us", "NPR National", "https://feeds.npr.org/1003/rss.xml", Category.US),
        s("guardian-us", "Guardian US", "https://www.theguardian.com/us-news/rss", Category.US),
        s("bbc-us", "BBC US & Canada", "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml", Category.US),

        // Business
        s("bbc-business", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", Category.BUSINESS),
        s("npr-business", "NPR Business", "https://feeds.npr.org/1006/rss.xml", Category.BUSINESS),
        s("guardian-business", "Guardian Business", "https://www.theguardian.com/business/rss", Category.BUSINESS),

        // Tech
        s("verge", "The Verge", "https://www.theverge.com/rss/index.xml", Category.TECH),
        s("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", Category.TECH),
        s("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", Category.TECH),
        s("engadget", "Engadget", "https://www.engadget.com/rss.xml", Category.TECH),
        s("bbc-tech", "BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", Category.TECH),
        s("hn", "Hacker News", "https://hnrss.org/frontpage", Category.TECH),

        // Science
        s("bbc-science", "BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", Category.SCIENCE),
        s("npr-science", "NPR Science", "https://feeds.npr.org/1007/rss.xml", Category.SCIENCE),
        s("guardian-science", "Guardian Science", "https://www.theguardian.com/science/rss", Category.SCIENCE),
        s("sciencedaily", "ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml", Category.SCIENCE),

        // Health
        s("bbc-health", "BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", Category.HEALTH),
        s("npr-health", "NPR Health", "https://feeds.npr.org/1128/rss.xml", Category.HEALTH),

        // Sports
        s("bbc-sport", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", Category.SPORTS),
        s("espn", "ESPN", "https://www.espn.com/espn/rss/news", Category.SPORTS),
        s("guardian-sport", "Guardian Sport", "https://www.theguardian.com/sport/rss", Category.SPORTS),

        // Culture
        s("bbc-culture", "BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", Category.ENTERTAINMENT),
        s("variety", "Variety", "https://variety.com/feed/", Category.ENTERTAINMENT),
        s("guardian-culture", "Guardian Culture", "https://www.theguardian.com/culture/rss", Category.ENTERTAINMENT),
    )

    /** Turned off on first launch to keep the first load quick; switch them on in Sources. */
    val offByDefault: Set<String> = setOf(
        "npr-world", "bbc-us", "guardian-business", "engadget", "bbc-tech", "hn",
        "guardian-science", "sciencedaily", "guardian-sport", "guardian-culture",
    )
}
