package com.moblized.newsfeed.data

/** Free, public RSS/Atom feeds. No API keys, no accounts. */
object DefaultSources {

    private fun s(id: String, name: String, url: String, category: Category) =
        Source(id, name, url, category)

    /** Google News topic feed. Each item carries other outlets' coverage of the same story. */
    private fun gTopic(topic: String) =
        "https://news.google.com/rss/headlines/section/topic/$topic?hl=en-US&gl=US&ceid=US:en"

    /** Latest headlines from one outlet, through Google News (for outlets without a public feed). */
    private fun gSite(domain: String) =
        "https://news.google.com/rss/search?q=site:$domain+when:1d&hl=en-US&gl=US&ceid=US:en"

    val all: List<Source> = listOf(
        // Top stories
        s("gnews-top", "Google News", "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", Category.TOP),
        s("ap", "AP News", gSite("apnews.com"), Category.TOP),
        s("reuters", "Reuters", gSite("reuters.com"), Category.TOP),
        s("cnn", "CNN", gSite("cnn.com"), Category.TOP),
        s("fox", "Fox News", "https://moxie.foxnews.com/google-publisher/latest.xml", Category.TOP),
        s("nypost", "New York Post", "https://nypost.com/feed/", Category.TOP),
        s("huffpost", "HuffPost", "https://www.huffpost.com/section/front-page/feed", Category.TOP),
        s("bbc-top", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", Category.TOP),
        s("npr-top", "NPR", "https://feeds.npr.org/1001/rss.xml", Category.TOP),
        s("nbc", "NBC News", gSite("nbcnews.com"), Category.TOP),
        s("wsj", "The Wall Street Journal", gSite("wsj.com"), Category.TOP),

        // World
        s("gnews-world", "Google News World", gTopic("WORLD"), Category.WORLD),
        s("bbc-world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", Category.WORLD),
        s("guardian-world", "The Guardian", "https://www.theguardian.com/world/rss", Category.WORLD),
        s("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", Category.WORLD),
        s("npr-world", "NPR World", "https://feeds.npr.org/1004/rss.xml", Category.WORLD),

        // U.S.
        s("gnews-us", "Google News U.S.", gTopic("NATION"), Category.US),
        s("newsweek", "Newsweek", "https://www.newsweek.com/rss", Category.US),
        s("npr-us", "NPR National", "https://feeds.npr.org/1003/rss.xml", Category.US),
        s("guardian-us", "Guardian US", "https://www.theguardian.com/us-news/rss", Category.US),
        s("bbc-us", "BBC US & Canada", "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml", Category.US),

        // Politics
        s("thehill", "The Hill", "https://thehill.com/feed/", Category.POLITICS),
        s("politico", "Politico", "https://rss.politico.com/politics-news.xml", Category.POLITICS),
        s("vox", "Vox", "https://www.vox.com/rss/index.xml", Category.POLITICS),
        s("washexaminer", "Washington Examiner", "https://www.washingtonexaminer.com/feed/", Category.POLITICS),
        s("dailycaller", "The Daily Caller", "https://dailycaller.com/feed/", Category.POLITICS),
        s("federalist", "The Federalist", "https://thefederalist.com/feed/", Category.POLITICS),
        s("reason", "Reason", "https://reason.com/feed/", Category.POLITICS),
        s("nationalreview", "National Review", "https://www.nationalreview.com/feed/", Category.POLITICS),

        // Business
        s("gnews-business", "Google News Business", gTopic("BUSINESS"), Category.BUSINESS),
        s("bbc-business", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", Category.BUSINESS),
        s("npr-business", "NPR Business", "https://feeds.npr.org/1006/rss.xml", Category.BUSINESS),
        s("guardian-business", "Guardian Business", "https://www.theguardian.com/business/rss", Category.BUSINESS),

        // Tech
        s("gnews-tech", "Google News Tech", gTopic("TECHNOLOGY"), Category.TECH),
        s("verge", "The Verge", "https://www.theverge.com/rss/index.xml", Category.TECH),
        s("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", Category.TECH),
        s("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", Category.TECH),
        s("engadget", "Engadget", "https://www.engadget.com/rss.xml", Category.TECH),
        s("bbc-tech", "BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", Category.TECH),
        s("hn", "Hacker News", "https://hnrss.org/frontpage", Category.TECH),

        // Science
        s("gnews-science", "Google News Science", gTopic("SCIENCE"), Category.SCIENCE),
        s("spacecom", "Space.com", "https://www.space.com/feeds/all", Category.SCIENCE),
        s("physorg", "Phys.org Physics", "https://phys.org/rss-feed/physics-news/", Category.SCIENCE),
        s("npr-science", "NPR Science", "https://feeds.npr.org/1007/rss.xml", Category.SCIENCE),
        s("bbc-science", "BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", Category.SCIENCE),
        s("guardian-science", "Guardian Science", "https://www.theguardian.com/science/rss", Category.SCIENCE),
        s("sciencedaily", "ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml", Category.SCIENCE),
        s("quanta", "Quanta Magazine", "https://www.quantamagazine.org/feed/", Category.SCIENCE),

        // Health
        s("gnews-health", "Google News Health", gTopic("HEALTH"), Category.HEALTH),
        s("npr-health", "NPR Health", "https://feeds.npr.org/1128/rss.xml", Category.HEALTH),
        s("bbc-health", "BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", Category.HEALTH),

        // Sports
        s("gnews-sports", "Google News Sports", gTopic("SPORTS"), Category.SPORTS),
        s("espn", "ESPN", "https://www.espn.com/espn/rss/news", Category.SPORTS),
        s("bbc-sport", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", Category.SPORTS),
        s("guardian-sport", "Guardian Sport", "https://www.theguardian.com/sport/rss", Category.SPORTS),

        // Culture
        s("gnews-ent", "Google News Entertainment", gTopic("ENTERTAINMENT"), Category.ENTERTAINMENT),
        s("variety", "Variety", "https://variety.com/feed/", Category.ENTERTAINMENT),
        s("bbc-culture", "BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", Category.ENTERTAINMENT),
        s("guardian-culture", "Guardian Culture", "https://www.theguardian.com/culture/rss", Category.ENTERTAINMENT),
    )

    /** Turned off on first launch to keep refreshes quick; switch them on in Sources. */
    val offByDefault: Set<String> = setOf(
        "nbc", "wsj", "npr-world", "guardian-us", "bbc-us", "federalist", "reason", "nationalreview",
        "bbc-business", "npr-business", "guardian-business", "techcrunch", "engadget", "bbc-tech", "hn",
        "bbc-science", "guardian-science", "sciencedaily", "bbc-health", "bbc-sport", "guardian-sport",
        "bbc-culture", "guardian-culture",
    )
}
