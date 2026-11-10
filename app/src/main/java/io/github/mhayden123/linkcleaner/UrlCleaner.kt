package io.github.mhayden123.linkcleaner

import java.net.URLDecoder

/**
 * Pure-Kotlin link cleaner. It has no Android dependencies, so it can be unit
 * tested on a normal JVM.
 *
 * Pipeline:
 *   1. Find the first http(s) URL inside the shared text (apps often share
 *      "Check this out! https://...").
 *   2. Unwrap known redirect wrappers (google.com/url?q=..., l.facebook.com/l.php?u=...).
 *   3. Apply site-specific trimming (Amazon, eBay, AliExpress, Walmart).
 *   4. Remove known tracking query parameters.
 *
 * Everything runs offline; no network requests are made.
 */
object UrlCleaner {

    data class Result(
        val original: String,
        val cleaned: String,
        /** Human-readable notes about what was changed, for the preview dialog. */
        val changes: List<String>,
    )

    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Parameters removed on every site. Compared case-insensitively. */
    private val GLOBAL_PARAMS = setOf(
        // Google / ads
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "_ga", "_gl",
        // Meta
        "fbclid", "igshid", "igsh",
        // Microsoft / Yandex / TikTok / Twitter ads
        "msclkid", "yclid", "ttclid", "twclid",
        // Mailchimp, HubSpot, Marketo, Vero, etc.
        "mc_cid", "mc_eid", "_hsenc", "_hsmi", "__hsfp", "__hssc", "__hstc",
        "hsctatracking", "mkt_tok", "vero_id", "vero_conv", "wickedid",
        "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid", "li_fat_id",
        "ref_src", "ref_url", "sr_share",
    )

    /** Any parameter starting with one of these is removed (e.g. utm_source). */
    private val GLOBAL_PREFIXES = listOf("utm_", "pk_", "mtm_")

    /** Extra parameters removed only on specific sites (domain -> params). */
    private val SITE_PARAMS: Map<String, Set<String>> = mapOf(
        "youtube.com" to setOf("si", "feature", "pp"),
        "youtu.be" to setOf("si", "feature"),
        "open.spotify.com" to setOf("si", "context", "nd", "dl_branch"),
        "x.com" to setOf("s", "t", "ref_src"),
        "twitter.com" to setOf("s", "t", "ref_src"),
        "instagram.com" to setOf("igsh", "igshid"),
        "threads.net" to setOf("xmt", "slof"),
        "threads.com" to setOf("xmt", "slof"),
        "reddit.com" to setOf("share_id", "rdt", "ref", "ref_source"),
        "tiktok.com" to setOf(
            "_r", "_t", "is_from_webapp", "sender_device", "sender_web_id",
            "share_app_id", "share_link_id", "social_sharing", "source", "tt_from",
            "u_code", "user_id", "web_id", "checksum", "sec_uid", "timestamp",
            "share_item_id", "is_copy_url", "embed_source", "refer", "enter_from",
        ),
        "linkedin.com" to setOf("trk", "trkinfo", "lipi", "rcm", "originalsubdomain"),
        "facebook.com" to setOf("mibextid", "rdid", "sfnsn", "__tn__", "__cft__"),
        "amazon" to setOf(
            "ref", "ref_", "tag", "linkcode", "linkid", "camp", "creative",
            "creativeasin", "ascsubtag", "content-id", "crid", "sprefix", "qid",
            "sr", "smid", "spia", "sp_csd", "dib", "dib_tag", "social_share",
            "starsleft", "_encoding", "pd_rd_w", "pd_rd_wg", "pd_rd_r", "pd_rd_i",
            "pf_rd_p", "pf_rd_r", "pf_rd_s", "pf_rd_t", "pf_rd_i", "pf_rd_m",
        ),
        // Paid Facebook/Instagram ad tags (ad format, pay-per-click flag, campaign name).
        "worldmarket.com" to setOf("media_type", "ppc", "camp"),
        "ebay" to setOf("_trkparms", "_trksid", "hash", "amdata", "mkevt", "mkcid", "mkrid", "campid", "toolid", "customid"),
        "aliexpress" to setOf("spm", "scm", "pvid", "algo_pvid", "algo_exp_id", "aff_platform", "aff_trace_key", "sk", "aff_fcid", "terminal_id", "afsmartredirect", "gatewayadapt"),
    )

    /**
     * Redirect wrappers: (host, path prefix) -> name of the parameter holding the real URL.
     * Path prefix "" means any path on that host.
     */
    private data class Redirect(val host: String, val pathPrefix: String, val params: List<String>)

    private val REDIRECTS = listOf(
        Redirect("google.com", "/url", listOf("q", "url")),
        Redirect("l.facebook.com", "/l.php", listOf("u")),
        Redirect("lm.facebook.com", "/l.php", listOf("u")),
        Redirect("l.messenger.com", "/l.php", listOf("u")),
        Redirect("l.instagram.com", "", listOf("u")),
        Redirect("l.threads.net", "", listOf("u")),
        Redirect("l.threads.com", "", listOf("u")),
        Redirect("youtube.com", "/redirect", listOf("q")),
        Redirect("out.reddit.com", "", listOf("url")),
        Redirect("steamcommunity.com", "/linkfilter", listOf("u", "url")),
        Redirect("linkedin.com", "/redir/redirect", listOf("url")),
        Redirect("slack-redir.net", "/link", listOf("url")),
        Redirect("duckduckgo.com", "/l", listOf("uddg")),
        Redirect("t.umblr.com", "/redirect", listOf("z")),
        Redirect("exit.sc", "", listOf("url")),
        Redirect("href.li", "", emptyList()), // href.li/?https://real.url
    )

    /** Returns null when no http(s) URL is found in [sharedText]. */
    fun clean(sharedText: String): Result? {
        val found = URL_IN_TEXT.find(sharedText)?.value ?: return null
        // Trailing punctuation is usually part of the sentence, not the URL.
        val original = found.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')

        val changes = mutableListOf<String>()
        var url = ParsedUrl.parse(original) ?: return Result(original, original, emptyList())

        // Unwrap redirects (loop because wrappers can be nested).
        for (attempt in 1..5) {
            val inner = unwrapRedirect(url) ?: break
            val parsed = ParsedUrl.parse(inner) ?: break
            changes += "Unwrapped redirect from ${url.host}"
            url = parsed
        }

        trimShoppingSite(url)?.let { trimmed ->
            changes += "Shortened ${trimmed.host} product link"
            url = trimmed
        }

        val removed = mutableListOf<String>()
        val kept = url.params.filter { (rawName, _) ->
            val remove = isTracking(url.host, rawName.lowercase())
            if (remove) removed += rawName
            !remove
        }
        if (removed.isNotEmpty()) changes += "Removed: " + removed.joinToString(", ")
        url = url.copy(params = kept)

        return Result(original, url.toString(), changes)
    }

    private fun isTracking(host: String, name: String): Boolean {
        if (name in GLOBAL_PARAMS) return true
        if (GLOBAL_PREFIXES.any { name.startsWith(it) }) return true
        return SITE_PARAMS.any { (site, params) -> hostMatches(host, site) && name in params }
    }

    private fun unwrapRedirect(url: ParsedUrl): String? {
        for (r in REDIRECTS) {
            if (!hostMatches(url.host, r.host)) continue
            if (r.pathPrefix.isNotEmpty() && !url.path.startsWith(r.pathPrefix)) continue
            if (r.params.isEmpty()) {
                // The whole query string is the target URL (e.g. href.li/?https://...)
                val q = url.rawQuery ?: continue
                val decoded = decode(q)
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded
                continue
            }
            for (p in r.params) {
                val value = url.params.firstOrNull { it.first.equals(p, ignoreCase = true) }?.second ?: continue
                val decoded = decode(value)
                if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                    return decoded
                }
            }
        }
        return null
    }

    private val AMAZON_HOST = Regex("""(^|\.)amazon\.[a-z]{2,3}(\.[a-z]{2})?$""")
    private val AMAZON_ASIN = Regex("""/(?:dp|gp/product|gp/aw/d|o|exec/obidos/asin)/([A-Z0-9]{10})(?:[/?]|$)""", RegexOption.IGNORE_CASE)
    private val EBAY_HOST = Regex("""(^|\.)ebay\.[a-z]{2,3}(\.[a-z]{2})?$""")
    private val EBAY_ITEM = Regex("""^/itm/(?:[^/]+/)?(\d{6,})""")
    private val ALIEXPRESS_HOST = Regex("""(^|\.)aliexpress\.[a-z]{2,3}$""")
    private val ALIEXPRESS_ITEM = Regex("""^/item/(\d+)\.html""")
    private val WALMART_ITEM = Regex("""^/ip/(?:[^/]+/)?(\d+)""")

    /** Returns a shortened product URL, or null if this isn't a recognised product page. */
    private fun trimShoppingSite(url: ParsedUrl): ParsedUrl? {
        val host = url.host
        val trimmed = when {
            AMAZON_HOST.containsMatchIn(host) ->
                AMAZON_ASIN.find(url.path)?.let { url.copy(path = "/dp/${it.groupValues[1].uppercase()}") }
            EBAY_HOST.containsMatchIn(host) ->
                EBAY_ITEM.find(url.path)?.let { url.copy(path = "/itm/${it.groupValues[1]}") }
            ALIEXPRESS_HOST.containsMatchIn(host) ->
                ALIEXPRESS_ITEM.find(url.path)?.let { url.copy(path = "/item/${it.groupValues[1]}.html") }
            hostMatches(host, "walmart.com") ->
                WALMART_ITEM.find(url.path)?.let { url.copy(path = "/ip/${it.groupValues[1]}") }
            else -> null
        } ?: return null
        val result = trimmed.copy(params = emptyList(), fragment = null)
        return if (result.toString() == url.toString()) null else result
    }

    /**
     * True if [host] is [site] or a subdomain of it. Sites written without a TLD
     * ("amazon", "ebay", "aliexpress") match any country domain.
     */
    private fun hostMatches(host: String, site: String): Boolean {
        if (!site.contains('.')) {
            return Regex("""(^|\.)${Regex.escape(site)}\.[a-z]{2,3}(\.[a-z]{2})?$""").containsMatchIn(host)
        }
        return host == site || host.endsWith(".$site")
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}

/**
 * Minimal URL splitter that keeps the original encoding of everything it
 * doesn't touch (java.net.URI is too strict for many real-world shared links).
 */
internal data class ParsedUrl(
    val scheme: String,
    val authority: String,
    val path: String,
    val params: List<Pair<String, String?>>,
    val fragment: String?,
    /** The query exactly as received; only used for href.li-style redirects. */
    val rawQuery: String? = null,
) {
    /** Lowercase host without port or user info. */
    val host: String
        get() = authority.substringAfterLast('@').substringBefore(':').lowercase()

    override fun toString(): String = buildString {
        append(scheme).append("://").append(authority).append(path)
        if (params.isNotEmpty()) {
            append('?')
            append(params.joinToString("&") { (k, v) -> if (v == null) k else "$k=$v" })
        }
        if (fragment != null) append('#').append(fragment)
    }

    companion object {
        private val PATTERN = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""")

        fun parse(url: String): ParsedUrl? {
            val m = PATTERN.matchEntire(url.trim()) ?: return null
            val (scheme, authority, path) = m.destructured
            val query = m.groups[4]?.value
            val fragment = m.groups[5]?.value
            val params = query.orEmpty()
                .split('&')
                .filter { it.isNotEmpty() }
                .map { part ->
                    val i = part.indexOf('=')
                    if (i < 0) part to null else part.substring(0, i) to part.substring(i + 1)
                }
            return ParsedUrl(scheme.lowercase(), authority, path, params, fragment, query)
        }
    }
}
