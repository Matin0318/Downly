package app.downly.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.downly.data.AppLanguage
import app.downly.data.ThemeMode
import app.downly.ui.components.DIcon
import app.downly.ui.components.DownlyNavBar
import app.downly.ui.components.DownlyNavRail
import app.downly.ui.components.NavSpec
import app.downly.ui.downloads.AddDownloadDialog
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.downloads.DownloadsScreen
import app.downly.ui.downloads.SiteGrabberDialog
import app.downly.ui.files.FilesScreen
import app.downly.ui.queues.QueuesScreen
import app.downly.ui.settings.SettingsScreen
import app.downly.ui.theme.AccentFrom
import app.downly.ui.theme.DownlyTheme
import app.downly.ui.theme.LocalWindowSize
import app.downly.ui.theme.WindowSize
import app.downly.ui.theme.windowSizeFor
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The three main screens, in swipe order. The browser opens in its own window and Files is a sub-screen.
private const val PAGE_DOWNLOADS = 0
private const val PAGE_QUEUES = 1
private const val PAGE_SETTINGS = 2

@Composable
fun DownlyApp(
    controller: DownloadsController,
    platform: PlatformActions,
    signals: HostSignals,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val settings by controller.settings.collectAsState()
    // Only the number of active downloads is needed here. Reading it through derivedStateOf means this function
    // (navigation bar + pager) is not rebuilt on every progress update, only when the number changes.
    val itemsState = controller.items.collectAsState()
    val activeCount by remember { derivedStateOf { itemsState.value.count { it.isActive } } }
    val strings = stringsFor(settings.language)
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val direction = if (settings.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Until the saved settings and downloads are read, only the background is drawn: no English defaults, no
    // empty "No downloads" screen. The timeout is a safety net so a broken settings file can never keep the
    // screen blank.
    val loadedNow by controller.loaded.collectAsState()
    var gateOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        gateOpen = true
    }
    val ready = loadedNow || gateOpen

    val pagerState = rememberPagerState(initialPage = PAGE_DOWNLOADS, pageCount = { 3 })
    var filesOpen by remember { mutableStateOf(false) }
    fun goToPage(page: Int) {
        filesOpen = false
        scope.launch { pagerState.animateScrollToPage(page) }
    }
    var queueFilter by remember { mutableStateOf<Long?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf<String?>(null) }
    var grabberOpen by remember { mutableStateOf(false) }

    // A link shared from another app opens the Add window with the link filled in.
    val incoming = signals.incomingUrl
    LaunchedEffect(incoming) {
        if (incoming != null) {
            addText = incoming
            addOpen = true
            signals.incomingUrl = null
        }
    }

    backHandler(filesOpen || pagerState.currentPage != PAGE_DOWNLOADS) {
        if (filesOpen) filesOpen = false else goToPage(PAGE_DOWNLOADS)
    }

    CompositionLocalProvider(LocalStrings provides strings, LocalLayoutDirection provides direction) {
        DownlyTheme(dark = dark) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (!ready) return@Surface
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            // A soft brand-colored glow in the top corner gives the dark theme some depth.
                            // The brush is built once per size, not on every frame of a swipe.
                            val glow = Brush.radialGradient(
                                colors = listOf(AccentFrom.copy(alpha = if (dark) 0.13f else 0.10f), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.9f,
                            )
                            onDrawBehind { drawRect(glow) }
                        }
                        .safeDrawingPadding(),
                ) {
                    val windowSize = windowSizeFor(maxWidth)
                    val navItems = listOf(
                        NavSpec(DIcon.Download, strings.navDownloads, activeCount, strings.digits(activeCount.toString())),
                        NavSpec(DIcon.Queue, strings.navQueues),
                        NavSpec(DIcon.Globe, strings.navBrowser),
                        NavSpec(DIcon.Settings, strings.settings),
                    )
                    val selectedIndex = if (filesOpen) {
                        0
                    } else {
                        when (pagerState.currentPage) {
                            PAGE_DOWNLOADS -> 0
                            PAGE_QUEUES -> 1
                            else -> 3
                        }
                    }
                    val onSelect: (Int) -> Unit = { index ->
                        when (index) {
                            0 -> goToPage(PAGE_DOWNLOADS)
                            1 -> goToPage(PAGE_QUEUES)
                            2 -> platform.openBrowser()
                            3 -> goToPage(PAGE_SETTINGS)
                        }
                    }
                    val onAdd = {
                        addText = null
                        addOpen = true
                    }

                    // Swipe left or right to move between the main screens. Forms stay narrow and centered on big
                    // screens; the downloads list gets more room. The neighbouring page is kept ready so a swipe
                    // never waits for a screen to be built.
                    val screens: @Composable (Modifier) -> Unit = { screenModifier ->
                        Box(modifier = screenModifier) {
                            if (filesOpen) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                    Box(modifier = Modifier.widthIn(max = 1240.dp).fillMaxSize()) {
                                        FilesScreen(
                                            controller = controller,
                                            platform = platform,
                                            onClose = { filesOpen = false },
                                        )
                                    }
                                }
                            } else {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    key = { it },
                                ) { page ->
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                        val maxContent = if (page == PAGE_DOWNLOADS) 1240.dp else 760.dp
                                        Box(modifier = Modifier.widthIn(max = maxContent).fillMaxSize()) {
                                            when (page) {
                                                PAGE_DOWNLOADS -> DownloadsScreen(
                                                    controller = controller,
                                                    platform = platform,
                                                    signals = signals,
                                                    queueFilter = queueFilter,
                                                    onQueueFilterChange = { queueFilter = it },
                                                    onOpenAdd = onAdd,
                                                    onOpenFiles = { filesOpen = true },
                                                    backHandler = backHandler,
                                                )
                                                PAGE_QUEUES -> QueuesScreen(controller = controller)
                                                else -> SettingsScreen(controller, platform, resumeTick = signals.resumeCount)
                                            }
                                        }
                                    }
                                }
                            }
                            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }

                    CompositionLocalProvider(LocalWindowSize provides windowSize) {
                        if (windowSize == WindowSize.Compact) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                screens(Modifier.weight(1f).fillMaxWidth())
                                DownlyNavBar(
                                    items = navItems,
                                    selected = selectedIndex,
                                    onSelect = onSelect,
                                    addLabel = strings.navAdd,
                                    onAdd = onAdd,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 2.dp),
                                )
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                DownlyNavRail(
                                    items = navItems,
                                    selected = selectedIndex,
                                    onSelect = onSelect,
                                    addLabel = strings.navAdd,
                                    onAdd = onAdd,
                                    appName = strings.appName,
                                    extended = windowSize == WindowSize.Expanded,
                                )
                                screens(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }

                val loaded by controller.loaded.collectAsState()
                if (loaded && !settings.onboardingDone) {
                    val finish = { controller.updateSettings { it.copy(onboardingDone = true) } }
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text(strings.onboardTitle) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.onboardLine1)
                                Text(strings.onboardLine2)
                                Text(strings.onboardLine3)
                            }
                        },
                        confirmButton = { TextButton(onClick = { finish() }) { Text(strings.onboardStart) } },
                    )
                }

                val directAllowed = remember(signals.resumeCount) { platform.hasFullStorageAccess() }
                if (loaded && settings.onboardingDone && !settings.storagePromptShown && !directAllowed) {
                    val shown = { controller.updateSettings { it.copy(storagePromptShown = true) } }
                    AlertDialog(
                        onDismissRequest = { shown() },
                        title = { Text(strings.more.savePromptTitle) },
                        text = { Text(strings.more.savePromptBody) },
                        confirmButton = {
                            TextButton(onClick = {
                                shown()
                                platform.requestFullStorageAccess()
                            }) { Text(strings.more.saveDirectAllow) }
                        },
                        dismissButton = { TextButton(onClick = { shown() }) { Text(strings.more.savePromptLater) } },
                    )
                }

                if (addOpen) {
                    AddDownloadDialog(
                        controller = controller,
                        platform = platform,
                        initialText = addText,
                        onDismiss = { addOpen = false },
                        onAdded = { count ->
                            addOpen = false
                            goToPage(PAGE_DOWNLOADS)
                            if (count > 1) scope.launch { snackbar.showSnackbar(strings.linksAdded(count)) }
                        },
                        onOpenGrabber = { grabberOpen = true },
                    )
                }
                if (grabberOpen) {
                    SiteGrabberDialog(
                        strings = strings,
                        onScan = { controller.grabLinks(it) },
                        onAdd = { links ->
                            val n = controller.addLinks(links)
                            grabberOpen = false
                            goToPage(PAGE_DOWNLOADS)
                            scope.launch { snackbar.showSnackbar(strings.linksAdded(n)) }
                        },
                        onClose = { grabberOpen = false },
                    )
                }
            }
        }
    }
}
