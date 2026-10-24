package app.downly.android

/** A small built-in list of advertising and tracking hosts. A host matches itself and all its sub-domains. */
object AdBlock {
    private val domains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "adservice.google.com", "google-analytics.com", "googletagmanager.com", "adnxs.com", "adsrvr.org",
        "advertising.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "scorecardresearch.com", "quantserve.com",
        "moatads.com", "amazon-adsystem.com", "popads.net", "popcash.net", "propellerads.com",
        "exoclick.com", "juicyads.com", "trafficjunky.com", "adsterra.com", "revcontent.com", "mgid.com",
        "zedo.com", "smartadserver.com", "adform.net", "teads.tv", "33across.com", "bidswitch.net",
        "contextweb.com", "yieldmo.com", "sharethrough.com", "mathtag.com", "bluekai.com", "hotjar.com",
        "adcolony.com", "applovin.com", "unityads.unity3d.com", "inmobi.com", "mopub.com", "adskeeper.com",
        "clickadu.com", "hilltopads.net", "onclickads.net", "ad-maven.com", "admaven.com", "adcash.com",
        "yektanet.com", "tapsell.ir", "mediaad.org", "ads.yahoo.com", "adtng.com", "ad.gt",
    )

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return domains.any { h == it || h.endsWith(".$it") }
    }
}
