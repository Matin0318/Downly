package app.downly.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import app.downly.core.engine.ProxyHolder
import app.downly.core.engine.ProxyKind
import app.downly.core.engine.HlsPlaylist
import app.downly.ui.AppStrings
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor

/**
 * Built-in browser (ADM / 1DM style): tabs, bookmarks, history, an ad blocker and a desktop-site switch.
 * Downloads started on a page and video / audio streams seen while browsing are sent to Downly together
 * with the page's cookies, referer and user agent.
 */
class BrowserActivity : ComponentActivity() {
    private class BrowserTab(val web: WebView) {
        var title: String = ""
        var url: String = ""
        val media = LinkedHashSet<String>()
    }

    private val tabs = ArrayList<BrowserTab>()
    private var current: BrowserTab? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var controller: DownloadsController
    private lateinit var strings: AppStrings
    private lateinit var store: BrowserStore
    private lateinit var holder: FrameLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var mediaButton: Button
    private lateinit var tabsButton: Button
    private lateinit var starButton: Button
    private lateinit var defaultUserAgent: String
    private var adBlock = true
    private var desktop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = (application as DownlyApplication).controller
        strings = stringsFor(controller.settings.value.language)
        store = BrowserStore(this)
        adBlock = store.adBlock
        desktop = store.desktop
        defaultUserAgent = WebSettings.getDefaultUserAgent(this)

        address = EditText(this).apply {
            setSingleLine()
            hint = "https://"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val go = Button(this).apply { text = "→" }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(address, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(go, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        holder = FrameLayout(this)

        starButton = navButton("☆") { toggleBookmark() }
        tabsButton = navButton("[1]") { showTabs() }
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            val weight = { LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
            addView(navButton("‹") { current?.web?.let { if (it.canGoBack()) it.goBack() } }, weight())
            addView(navButton("›") { current?.web?.let { if (it.canGoForward()) it.goForward() } }, weight())
            addView(tabsButton, weight())
            addView(starButton, weight())
            addView(navButton("⋮") { showMenu() }, weight())
        }

        mediaButton = Button(this).apply { setOnClickListener { showMedia() } }
        val toApp = Button(this).apply {
            text = strings.browserBackToApp
            setOnClickListener { finish() }
        }
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(mediaButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(toApp, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, 8))
            addView(holder, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(navBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(root)
        applyStyle(root, topBar, go, navBar, bottomBar, toApp)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        go.setOnClickListener { load() }
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load()
                true
            } else {
                false
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            val tab = current
            when {
                tab != null && tab.web.canGoBack() -> tab.web.goBack()
                tab != null && tabs.size > 1 -> closeTab(tab)
                else -> finish()
            }
        }

        applyWebProxy()
        val opened = intent?.dataString
        if (opened != null) {
            newTab(opened)
        } else {
            // Bring back the pages that were open last time.
            val saved = store.savedTabs()
            if (saved.isEmpty()) {
                newTab("https://www.google.com")
            } else {
                saved.forEach { newTab(it) }
                tabs.getOrNull(store.savedTabIndex)?.let { switchTo(it) }
            }
        }
    }

    override fun onStop() {
        val urls = tabs.map { it.url.ifBlank { it.web.url.orEmpty() } }.filter { it.startsWith("http") }
        val front = tabs.indexOf(current).coerceAtLeast(0)
        // Tabs without an address are dropped, so the saved index is counted among the kept ones.
        val kept = tabs.take(front + 1).count { (it.url.ifBlank { it.web.url.orEmpty() }).startsWith("http") }
        store.saveTabs(urls, (kept - 1).coerceAtLeast(0))
        super.onStop()
    }

    override fun onPause() {
        current?.web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        current?.web?.onResume()
    }

    /** Sends the pages through the proxy typed in Settings (needs a WebView that supports proxy override). */
    private fun applyWebProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val proxy = ProxyHolder.current
        val direct = Executor { it.run() }
        if (!proxy.enabled) {
            ProxyController.getInstance().clearProxyOverride(direct, Runnable { })
            return
        }
        val rule = if (proxy.kind == ProxyKind.SOCKS) "socks5://${proxy.host}:${proxy.port}" else "${proxy.host}:${proxy.port}"
        val config = androidx.webkit.ProxyConfig.Builder().addProxyRule(rule).build()
        ProxyController.getInstance().setProxyOverride(config, direct, Runnable { })
    }

    override fun onDestroy() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(Executor { it.run() }, Runnable { })
        }
        scope.cancel()
        tabs.forEach { it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    // ---------- look

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radiusDp: Int, oval: Boolean = false) = GradientDrawable().apply {
        this.shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    /** Dresses the plain Android views in the same colors as the rest of the app. */
    private fun applyStyle(root: LinearLayout, topBar: LinearLayout, go: Button, navBar: LinearLayout, bottomBar: LinearLayout, toApp: Button) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        fun pick(dark: String, light: String) = Color.parseColor(if (night) dark else light)
        val background = pick("#0A0E13", "#F3F7F9")
        val surface = pick("#141B24", "#FFFFFF")
        val field = pick("#1A232E", "#E3EAEF")
        val text = pick("#E6EDF3", "#0F1720")
        val muted = pick("#9AA8B6", "#55646F")
        val accent = pick("#34D1B6", "#007F6B")
        val onAccent = Color.parseColor("#00241D")

        root.setBackgroundColor(background)
        topBar.setPadding(dp(12), dp(8), dp(12), dp(6))
        topBar.gravity = Gravity.CENTER_VERTICAL

        address.background = shape(field, 26)
        address.setPadding(dp(18), dp(12), dp(18), dp(12))
        address.setTextColor(text)
        address.setHintTextColor(muted)
        address.textSize = 15f

        go.background = shape(accent, 24, oval = true)
        go.setTextColor(onAccent)
        go.textSize = 18f
        go.isAllCaps = false
        go.stateListAnimator = null
        go.minWidth = 0
        go.minimumWidth = 0
        (go.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(46)
            height = dp(46)
            marginStart = dp(8)
        }

        progress.progressTintList = ColorStateList.valueOf(accent)
        progress.progressBackgroundTintList = ColorStateList.valueOf(field)

        navBar.setBackgroundColor(surface)
        navBar.setPadding(dp(4), dp(2), dp(4), dp(2))
        for (i in 0 until navBar.childCount) {
            (navBar.getChildAt(i) as? Button)?.apply {
                setTextColor(text)
                isAllCaps = false
                stateListAnimator = null
            }
        }
        starButton.setTextColor(accent)

        bottomBar.setBackgroundColor(surface)
        bottomBar.setPadding(dp(12), dp(4), dp(12), dp(10))
        mediaButton.background = shape(accent, 24)
        mediaButton.setTextColor(onAccent)
        mediaButton.isAllCaps = false
        mediaButton.textSize = 14f
        mediaButton.stateListAnimator = null
        toApp.background = shape(field, 24)
        toApp.setTextColor(text)
        toApp.isAllCaps = false
        toApp.textSize = 14f
        toApp.stateListAnimator = null
        (mediaButton.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        (toApp.layoutParams as LinearLayout.LayoutParams).marginStart = dp(6)
    }

    // ---------- tabs

    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(url: String?): BrowserTab {
        val web = WebView(this)
        val tab = BrowserTab(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
            applyMode(this)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (adBlock && AdBlock.isBlocked(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                val link = request.url.toString()
                if (isMedia(link)) {
                    runOnUiThread { if (tab.media.add(link) && tab === current) updateMediaButton() }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                when (request.url.scheme?.lowercase()) {
                    "http", "https", "about", "data", "blob", "javascript" -> false
                    "ftp", "magnet" -> {
                        controller.add(request.url.toString())
                        Toast.makeText(this@BrowserActivity, strings.browserAdded, Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> true // intent:, market:, magnet: ... are not opened from here
                }

            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
                val proxy = ProxyHolder.current
                if (proxy.enabled && proxy.user.isNotBlank() && host.equals(proxy.host, ignoreCase = true)) {
                    handler.proceed(proxy.user, proxy.password)
                } else {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm)
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                tab.url = url
                tab.media.clear()
                if (tab === current) {
                    address.setText(url)
                    updateMediaButton()
                    updateStar()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.url = url
                tab.title = view.title.orEmpty()
                if (url != "about:blank" && url.startsWith("http")) store.addHistory(tab.title, url)
                if (tab === current) updateStar()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (tab !== current) return
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (adBlock && !isUserGesture) return false // blocks pop-ups the page opens by itself
                val child = newTab(null)
                (resultMsg.obj as WebView.WebViewTransport).webView = child.web
                resultMsg.sendToTarget()
                return true
            }
        }
        web.setDownloadListener { link, userAgent, _, mime, _ ->
            val torrent = mime == "application/x-bittorrent" && !link.substringBefore('?').lowercase().endsWith(".torrent")
            addMedia(if (torrent) "$link#.torrent" else link, userAgent)
        }

        tabs += tab
        switchTo(tab)
        if (url != null) web.loadUrl(url)
        return tab
    }

    private fun switchTo(tab: BrowserTab) {
        current?.web?.onPause()
        current = tab
        holder.removeAllViews()
        (tab.web.parent as? FrameLayout)?.removeView(tab.web)
        holder.addView(tab.web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        tab.web.onResume()
        address.setText(tab.url)
        progress.visibility = View.GONE
        updateTabsButton()
        updateMediaButton()
        updateStar()
    }

    private fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            tab.web.destroy()
            newTab("https://www.google.com")
            return
        }
        if (tab === current) {
            holder.removeAllViews()
            switchTo(tabs[(index - 1).coerceAtLeast(0)])
        } else {
            updateTabsButton()
        }
        tab.web.destroy()
    }

    private fun applyMode(settings: WebSettings) {
        settings.userAgentString = if (desktop) DESKTOP_USER_AGENT else defaultUserAgent
        settings.useWideViewPort = desktop
        settings.loadWithOverviewMode = desktop
    }

    private fun load() {
        var text = address.text.toString().trim()
        if (text.isEmpty()) return
        if (!text.contains("://")) {
            text = if (text.contains('.') && !text.contains(' ')) {
                "https://$text"
            } else {
                "https://www.google.com/search?q=" + Uri.encode(text)
            }
        }
        current?.web?.loadUrl(text)
    }

    // ---------- buttons

    private fun navButton(label: String, onClick: () -> Unit): Button =
        Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label
            textSize = 18f
            setOnClickListener { onClick() }
        }

    private fun updateTabsButton() {
        tabsButton.text = "[${tabs.size}]"
    }

    private fun updateMediaButton() {
        mediaButton.text = "${strings.browserDownloads} (${current?.media?.size ?: 0})"
    }

    private fun updateStar() {
        val url = current?.url.orEmpty()
        starButton.text = if (url.isNotEmpty() && store.isBookmarked(url)) "★" else "☆"
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---------- dialogs

    private fun showTabs() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 24, 8)
        }
        lateinit var dialog: AlertDialog
        tabs.toList().forEach { tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val name = tab.title.ifBlank { tab.url }.ifBlank { "about:blank" }
            val label = TextView(this).apply {
                text = (if (tab === current) "● " else "") + name
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textSize = 16f
                setPadding(0, 28, 0, 28)
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setOnClickListener {
                    switchTo(tab)
                    dialog.dismiss()
                }
            }
            val close = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = "✕"
                contentDescription = strings.more.brCloseTab
                setOnClickListener {
                    closeTab(tab)
                    dialog.dismiss()
                    showTabs()
                }
            }
            row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(close, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            list.addView(row)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(strings.more.brTabs)
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton(strings.more.brNewTab) { _, _ -> newTab("https://www.google.com") }
            .setNegativeButton(strings.close, null)
            .create()
        dialog.show()
    }

    private fun showMenu() {
        val more = strings.more
        val items = arrayOf(
            more.brNewTab,
            more.brBookmarks,
            more.brHistory,
            "${more.brAdBlock}: ${if (adBlock) more.brOn else more.brOff}",
            "${more.brDesktop}: ${if (desktop) more.brOn else more.brOff}",
            more.brFind,
            more.brCopyLink,
            more.brShare,
            strings.browserBackToApp,
        )
        AlertDialog.Builder(this)
            .setTitle(more.brMenu)
            .setItems(items) { _, index ->
                when (index) {
                    0 -> newTab("https://www.google.com")
                    1 -> showPages(more.brBookmarks, store.bookmarks(), clearLabel = null, onClear = {}, longPressRemoves = true)
                    2 -> showPages(more.brHistory, store.history(), clearLabel = more.brClearHistory, onClear = { store.clearHistory() }, longPressRemoves = false)
                    3 -> {
                        adBlock = !adBlock
                        store.adBlock = adBlock
                        toast("${more.brAdBlock}: ${if (adBlock) more.brOn else more.brOff}")
                    }
                    4 -> {
                        desktop = !desktop
                        store.desktop = desktop
                        tabs.forEach {
                            applyMode(it.web.settings)
                            it.web.reload()
                        }
                        toast("${more.brDesktop}: ${if (desktop) more.brOn else more.brOff}")
                    }
                    5 -> showFind()
                    6 -> copyLink()
                    7 -> shareLink()
                    8 -> finish()
                }
            }
            .show()
    }

    private fun showPages(
        title: String,
        pages: List<BrowserStore.Page>,
        clearLabel: String?,
        onClear: () -> Unit,
        longPressRemoves: Boolean,
    ) {
        val builder = AlertDialog.Builder(this).setTitle(title).setNegativeButton(strings.close, null)
        if (pages.isEmpty()) {
            builder.setMessage(strings.more.brEmpty)
            builder.show()
            return
        }
        val names = pages.map { it.title.ifBlank { it.url } }.toTypedArray()
        builder.setItems(names) { _, i -> current?.web?.loadUrl(pages[i].url) }
        if (clearLabel != null) builder.setNeutralButton(clearLabel) { _, _ -> onClear() }
        val dialog = builder.show()
        if (longPressRemoves) {
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                store.removeBookmark(pages[position].url)
                toast(strings.more.brBookmarkRemoved)
                dialog.dismiss()
                updateStar()
                showPages(title, store.bookmarks(), clearLabel, onClear, longPressRemoves)
                true
            }
        }
    }

    private fun showFind() {
        val web = current?.web ?: return
        var lastQuery = ""
        val input = EditText(this).apply {
            setSingleLine()
            hint = strings.more.brFindHint
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(strings.more.brFind)
            .setView(input)
            .setPositiveButton(strings.more.brFindNext, null)
            .setNegativeButton(strings.close, null)
            .create()
        dialog.setOnShowListener {
            // Set here (not in the builder) so the dialog stays open after "Find next".
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString()
                if (query.isEmpty()) return@setOnClickListener
                if (query != lastQuery) {
                    lastQuery = query
                    web.findAllAsync(query)
                } else {
                    web.findNext(true)
                }
            }
        }
        dialog.setOnDismissListener { web.clearMatches() }
        dialog.show()
    }

    private fun copyLink() {
        val url = current?.url.orEmpty()
        if (url.isBlank()) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", url))
        toast(strings.more.brLinkCopied)
    }

    private fun shareLink() {
        val url = current?.url.orEmpty()
        if (url.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, strings.more.brShare))
    }

    private fun toggleBookmark() {
        val tab = current ?: return
        if (tab.url.isBlank()) return
        val added = store.toggleBookmark(tab.title.ifBlank { tab.url }, tab.url)
        toast(if (added) strings.more.brBookmarkSaved else strings.more.brBookmarkRemoved)
        updateStar()
    }

    private fun showMedia() {
        val tab = current ?: return
        if (tab.media.isEmpty()) {
            Toast.makeText(this, strings.browserNoMedia, Toast.LENGTH_LONG).show()
            return
        }
        val links = tab.media.toList()
        val names = links.map { it.substringBefore('?').substringAfterLast('/').ifBlank { it } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(strings.browserDownloads)
            .setItems(names) { _, i -> addMedia(links[i], null) }
            .show()
    }

    // ---------- sending to Downly

    private fun requestHeaders(url: String, userAgent: String?): Map<String, String> {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = userAgent ?: current?.web?.settings?.userAgentString ?: defaultUserAgent
        CookieManager.getInstance().getCookie(url)?.let { headers["Cookie"] = it }
        current?.web?.url?.let { headers["Referer"] = it }
        return headers
    }

    /** Adds a link to Downly. A multi-quality HLS stream first asks which quality to download. */
    private fun addMedia(url: String, userAgent: String?) {
        val headers = requestHeaders(url, userAgent)
        if (!HlsPlaylist.looksLikeHls(url)) {
            addNow(url, headers, "")
            return
        }
        scope.launch {
            val qualities = controller.hlsQualities(url, headers)
            if (qualities.size <= 1) {
                addNow(url, headers, "")
                return@launch
            }
            val names = (listOf(strings.more.qualityBest) + qualities.map { it.label }).toTypedArray()
            AlertDialog.Builder(this@BrowserActivity)
                .setTitle(strings.more.brChooseQuality)
                .setItems(names) { _, i -> addNow(url, headers, if (i == 0) "" else qualities[i - 1].url) }
                .show()
        }
    }

    private fun addNow(url: String, headers: Map<String, String>, variant: String) {
        controller.add(url, DownloadsController.AddOptions(headers = headers, hlsVariant = variant))
        toast(strings.browserAdded)
    }

    private fun isMedia(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return MEDIA_EXTENSIONS.any { path.endsWith(it) }
    }

    private companion object {
        val MEDIA_EXTENSIONS = listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".flac", ".ogg", ".mov")
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
