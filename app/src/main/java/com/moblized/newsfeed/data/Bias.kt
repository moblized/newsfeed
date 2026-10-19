package com.moblized.newsfeed.data

import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class Side(val label: String, val letter: String) {
    LEFT("Left", "L"),
    CENTER("Center", "C"),
    RIGHT("Right", "R"),
}

enum class Bias(val label: String, val side: Side) {
    LEFT("Left", Side.LEFT),
    LEAN_LEFT("Lean Left", Side.LEFT),
    CENTER("Center", Side.CENTER),
    LEAN_RIGHT("Lean Right", Side.RIGHT),
    RIGHT("Right", Side.RIGHT);

    companion object {
        fun fromName(name: String?): Bias? = entries.firstOrNull { it.name == name }
    }
}

data class Outlet(
    val id: String,
    val name: String,
    val bias: Bias,
    val domains: List<String>,
    val aliases: List<String> = emptyList(),
)

/**
 * Publication-level bias ratings for U.S. politics, following AllSides media bias ratings
 * (2024–2026 reviews where available). Anything not listed here is "not rated" and is left
 * out of coverage bars, the same way Ground News does it. Users can change any rating.
 */
object BiasDirectory {

    const val ATTRIBUTION =
        "Bias ratings follow AllSides media bias ratings and describe each outlet as a whole, not single articles. " +
            "Outlets without a rating are left out of coverage bars. Tap a source's rating to change it."

    private fun o(id: String, name: String, bias: Bias, domains: String, aliases: String = "") =
        Outlet(id, name, bias, domains.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() })

    val outlets: List<Outlet> = listOf(
        // ---- Left
        o("ap", "Associated Press", Bias.LEFT, "apnews.com", "AP News, AP, The Associated Press"),
        o("guardian", "The Guardian", Bias.LEFT, "theguardian.com", "Guardian, The Guardian US"),
        o("msnow", "MS NOW", Bias.LEFT, "ms.now,msnbc.com", "MSNBC, MSNBC News"),
        o("huffpost", "HuffPost", Bias.LEFT, "huffpost.com,huffingtonpost.com", "Huffington Post"),
        o("vox", "Vox", Bias.LEFT, "vox.com"),
        o("atlantic", "The Atlantic", Bias.LEFT, "theatlantic.com"),
        o("slate", "Slate", Bias.LEFT, "slate.com", "Slate Magazine"),
        o("salon", "Salon", Bias.LEFT, "salon.com"),
        o("motherjones", "Mother Jones", Bias.LEFT, "motherjones.com"),
        o("dailybeast", "The Daily Beast", Bias.LEFT, "thedailybeast.com"),
        o("rawstory", "Raw Story", Bias.LEFT, "rawstory.com"),
        o("nation", "The Nation", Bias.LEFT, "thenation.com"),
        o("newyorker", "The New Yorker", Bias.LEFT, "newyorker.com"),
        o("newrepublic", "The New Republic", Bias.LEFT, "newrepublic.com"),
        o("jacobin", "Jacobin", Bias.LEFT, "jacobin.com"),
        o("intercept", "The Intercept", Bias.LEFT, "theintercept.com"),
        o("democracynow", "Democracy Now!", Bias.LEFT, "democracynow.org"),
        o("dailykos", "Daily Kos", Bias.LEFT, "dailykos.com"),
        o("vice", "VICE", Bias.LEFT, "vice.com"),
        o("esquire", "Esquire", Bias.LEFT, "esquire.com"),
        o("teenvogue", "Teen Vogue", Bias.LEFT, "teenvogue.com"),

        // ---- Lean Left
        o("cnn", "CNN", Bias.LEAN_LEFT, "cnn.com", "CNN International"),
        o("nbc", "NBC News", Bias.LEAN_LEFT, "nbcnews.com", "NBC"),
        o("abc", "ABC News", Bias.LEAN_LEFT, "abcnews.go.com,abcnews.com", "ABC"),
        o("cbs", "CBS News", Bias.LEAN_LEFT, "cbsnews.com", "CBS"),
        o("npr", "NPR", Bias.LEAN_LEFT, "npr.org"),
        o("nyt", "The New York Times", Bias.LEAN_LEFT, "nytimes.com", "New York Times, NYT"),
        o("wapo", "The Washington Post", Bias.LEAN_LEFT, "washingtonpost.com", "Washington Post"),
        o("politico", "Politico", Bias.LEAN_LEFT, "politico.com", "POLITICO"),
        o("axios", "Axios", Bias.LEAN_LEFT, "axios.com"),
        o("bloomberg", "Bloomberg", Bias.LEAN_LEFT, "bloomberg.com", "Bloomberg News"),
        o("usatoday", "USA Today", Bias.LEAN_LEFT, "usatoday.com", "USA TODAY"),
        o("businessinsider", "Business Insider", Bias.LEAN_LEFT, "businessinsider.com,insider.com", "Insider"),
        o("yahoonews", "Yahoo News", Bias.LEAN_LEFT, "news.yahoo.com"),
        o("pbs", "PBS News", Bias.LEAN_LEFT, "pbs.org", "PBS, PBS NewsHour"),
        o("time", "Time", Bias.LEAN_LEFT, "time.com", "TIME, Time Magazine"),
        o("aljazeera", "Al Jazeera", Bias.LEAN_LEFT, "aljazeera.com", "Al Jazeera English"),
        o("verge", "The Verge", Bias.LEAN_LEFT, "theverge.com"),
        o("latimes", "Los Angeles Times", Bias.LEAN_LEFT, "latimes.com", "LA Times"),
        o("economist", "The Economist", Bias.LEAN_LEFT, "economist.com"),
        o("independent", "The Independent", Bias.LEAN_LEFT, "independent.co.uk", "Independent"),
        o("semafor", "Semafor", Bias.LEAN_LEFT, "semafor.com"),
        o("propublica", "ProPublica", Bias.LEAN_LEFT, "propublica.org"),
        o("mediaite", "Mediaite", Bias.LEAN_LEFT, "mediaite.com"),
        o("bostonglobe", "The Boston Globe", Bias.LEAN_LEFT, "bostonglobe.com", "Boston Globe"),
        o("miamiherald", "Miami Herald", Bias.LEAN_LEFT, "miamiherald.com"),
        o("vanityfair", "Vanity Fair", Bias.LEAN_LEFT, "vanityfair.com"),

        // ---- Center
        o("reuters", "Reuters", Bias.CENTER, "reuters.com"),
        o("bbc", "BBC News", Bias.CENTER, "bbc.com,bbc.co.uk,bbci.co.uk", "BBC"),
        o("thehill", "The Hill", Bias.CENTER, "thehill.com"),
        o("newsweek", "Newsweek", Bias.CENTER, "newsweek.com"),
        o("cnbc", "CNBC", Bias.CENTER, "cnbc.com"),
        o("forbes", "Forbes", Bias.CENTER, "forbes.com"),
        o("wsj", "The Wall Street Journal", Bias.CENTER, "wsj.com", "Wall Street Journal, WSJ"),
        o("csmonitor", "The Christian Science Monitor", Bias.CENTER, "csmonitor.com", "Christian Science Monitor"),
        o("reason", "Reason", Bias.CENTER, "reason.com", "Reason Magazine"),
        o("newsnation", "NewsNation", Bias.CENTER, "newsnationnow.com", "NewsNation Now"),
        o("san", "Straight Arrow News", Bias.CENTER, "san.com"),
        o("ft", "Financial Times", Bias.CENTER, "ft.com"),
        o("drudge", "Drudge Report", Bias.CENTER, "drudgereport.com"),
        o("sciam", "Scientific American", Bias.CENTER, "scientificamerican.com"),
        o("sciencedaily", "ScienceDaily", Bias.CENTER, "sciencedaily.com", "Science Daily"),
        o("techcrunch", "TechCrunch", Bias.CENTER, "techcrunch.com"),
        o("cnet", "CNET", Bias.CENTER, "cnet.com"),
        o("jpost", "The Jerusalem Post", Bias.CENTER, "jpost.com", "Jerusalem Post"),
        o("chicagotribune", "Chicago Tribune", Bias.CENTER, "chicagotribune.com"),

        // ---- Lean Right
        o("nypost", "New York Post", Bias.LEAN_RIGHT, "nypost.com", "NY Post"),
        o("washexaminer", "Washington Examiner", Bias.LEAN_RIGHT, "washingtonexaminer.com"),
        o("washtimes", "The Washington Times", Bias.LEAN_RIGHT, "washingtontimes.com", "Washington Times"),
        o("dailymail", "Daily Mail", Bias.LEAN_RIGHT, "dailymail.co.uk,dailymail.com", "Daily Mail Online, MailOnline"),
        o("epochtimes", "The Epoch Times", Bias.LEAN_RIGHT, "theepochtimes.com", "Epoch Times"),
        o("thefp", "The Free Press", Bias.LEAN_RIGHT, "thefp.com"),
        o("justthenews", "Just the News", Bias.LEAN_RIGHT, "justthenews.com"),
        o("zerohedge", "ZeroHedge", Bias.LEAN_RIGHT, "zerohedge.com", "Zero Hedge"),
        o("dispatch", "The Dispatch", Bias.LEAN_RIGHT, "thedispatch.com"),
        o("telegraph", "The Telegraph", Bias.LEAN_RIGHT, "telegraph.co.uk", "Telegraph"),
        o("marketwatch", "MarketWatch", Bias.LEAN_RIGHT, "marketwatch.com"),
        o("deseret", "Deseret News", Bias.LEAN_RIGHT, "deseret.com"),

        // ---- Right
        o("fox", "Fox News", Bias.RIGHT, "foxnews.com", "FOX News"),
        o("newsmax", "Newsmax", Bias.RIGHT, "newsmax.com"),
        o("breitbart", "Breitbart", Bias.RIGHT, "breitbart.com", "Breitbart News"),
        o("dailycaller", "The Daily Caller", Bias.RIGHT, "dailycaller.com", "Daily Caller"),
        o("federalist", "The Federalist", Bias.RIGHT, "thefederalist.com", "Federalist"),
        o("dailywire", "The Daily Wire", Bias.RIGHT, "dailywire.com", "Daily Wire"),
        o("dailysignal", "The Daily Signal", Bias.RIGHT, "dailysignal.com", "Daily Signal"),
        o("freebeacon", "Washington Free Beacon", Bias.RIGHT, "freebeacon.com", "The Washington Free Beacon"),
        o("townhall", "Townhall", Bias.RIGHT, "townhall.com"),
        o("oan", "One America News", Bias.RIGHT, "oann.com", "OAN, One America News Network"),
        o("blaze", "TheBlaze", Bias.RIGHT, "theblaze.com", "The Blaze, Blaze Media"),
        o("gatewaypundit", "The Gateway Pundit", Bias.RIGHT, "thegatewaypundit.com", "Gateway Pundit"),
        o("redstate", "RedState", Bias.RIGHT, "redstate.com", "Red State"),
        o("amspectator", "The American Spectator", Bias.RIGHT, "spectator.org", "American Spectator"),
        o("westernjournal", "The Western Journal", Bias.RIGHT, "westernjournal.com", "Western Journal"),
        o("ijr", "Independent Journal Review", Bias.RIGHT, "ijr.com", "IJR"),
        o("nationalreview", "National Review", Bias.RIGHT, "nationalreview.com"),
    )

    private val byName: Map<String, Outlet> = HashMap<String, Outlet>().apply {
        for (outlet in outlets) {
            put(normalize(outlet.name), outlet)
            outlet.aliases.forEach { put(normalize(it), outlet) }
        }
    }

    private val byDomain: List<Pair<String, Outlet>> =
        outlets.flatMap { o -> o.domains.map { it to o } }.sortedByDescending { it.first.length }

    fun normalize(name: String): String {
        var s = name.lowercase(Locale.ROOT).trim()
        s = s.removeSuffix(".com").removeSuffix(" online")
        s = s.replace("&", " and ").replace(Regex("[^a-z0-9 ]"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.removePrefix("the ")
    }

    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URL(url).host.lowercase(Locale.ROOT).removePrefix("www.") }.getOrNull()
    }

    fun findByHost(host: String?): Outlet? {
        if (host.isNullOrBlank()) return null
        return byDomain.firstOrNull { (d, _) -> host == d || host.endsWith(".$d") }?.second
    }

    fun findByName(name: String?): Outlet? = name?.let { byName[normalize(it)] }

    fun outletById(id: String): Outlet? = outlets.firstOrNull { it.id == id }
}

/** Resolves an article's outlet and bias, applying the user's own rating changes. */
class BiasResolver(val overrides: Map<String, String>) {

    data class Resolved(val key: String, val name: String, val bias: Bias?, val defaultBias: Bias?)

    private val cache = ConcurrentHashMap<String, Resolved>()

    fun resolve(article: Article): Resolved {
        val cacheKey = article.sourceName + "|" + (article.publisherUrl ?: article.link)
        cache[cacheKey]?.let { return it }
        val host = BiasDirectory.hostOf(article.publisherUrl)
            ?: BiasDirectory.hostOf(article.link)?.takeUnless { it.contains("news.google.") }
        val outlet = BiasDirectory.findByHost(host) ?: BiasDirectory.findByName(article.sourceName)
        val resolved = build(outlet, article.sourceName)
        cache[cacheKey] = resolved
        return resolved
    }

    fun resolve(source: Source): Resolved {
        val host = BiasDirectory.hostOf(source.url)?.takeUnless { it.contains("news.google.") }
        val outlet = BiasDirectory.findByName(source.name) ?: BiasDirectory.findByHost(host)
        return build(outlet, source.name)
    }

    fun bias(article: Article): Bias? = resolve(article).bias

    private fun build(outlet: Outlet?, fallbackName: String): Resolved {
        val key = outlet?.id ?: ("name:" + BiasDirectory.normalize(fallbackName))
        val override = overrides[key]
        val bias = when (override) {
            null -> outlet?.bias
            NOT_RATED -> null
            else -> Bias.fromName(override) ?: outlet?.bias
        }
        return Resolved(key, outlet?.name ?: fallbackName, bias, outlet?.bias)
    }

    companion object {
        const val NOT_RATED = "NONE"
    }
}
