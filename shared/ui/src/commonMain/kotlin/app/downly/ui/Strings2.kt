package app.downly.ui

/** Texts of the features added after the first release: proxy, usage, backup, files, quality, browser tools. */
interface MoreStrings {
    // proxy
    val proxyTitle: String
    val proxyHint: String
    val proxyOff: String
    val proxyHttp: String
    val proxySocks: String
    val proxyHost: String
    val proxyPort: String
    val proxyUser: String
    val proxyPass: String
    val proxyNote: String

    // usage
    val statsTitle: String
    val statsHint: String
    val statsOpen: String
    val statsToday: String
    val statsWeek: String
    val statsMonth: String
    val statsLast7: String

    // backup
    val backupTitle: String
    val backupHint: String
    val backupExport: String
    val backupImport: String
    val backupSaved: String
    val backupFailed: String
    val backupInvalid: String
    fun backupRestored(n: Int): String

    val perSecond: String

    // speed
    val connectionsAdvice: String

    // scheduled downloads reliability
    val schedTitle: String
    val schedHint: String
    val schedExactOk: String
    val schedExactNeeded: String
    val schedExactAllow: String
    val schedBatteryOk: String
    val schedBatteryNeeded: String
    val schedBatteryAllow: String
    val schedBrandNote: String

    // queues
    val queueDisabled: String
    val queueDelete: String
    val queueDeleteBody: String
    val queueSwitchLabel: String

    // search
    fun searchResults(n: Int): String

    // where files are saved
    val saveLocationTitle: String
    val saveDirectOn: String
    val saveDirectOff: String
    val saveDirectAllow: String
    val savePromptTitle: String
    val savePromptBody: String
    val savePromptLater: String

    // simultaneous downloads
    val parallelValue: String
    val parallelCustomHint: String
    val parallelManyWarning: String

    // storage
    val storageTitle: String
    val storageHint: String
    val storageUnfinished: String
    val storageFinished: String
    val storageLeftover: String
    val storageClean: String
    val storageNothing: String
    fun storageCleaned(freed: String, moved: Int): String

    val openWith: String
    val noAppToOpen: String

    // custom speed limit
    val speedCustom: String
    val speedValue: String
    val speedCustomHint: String

    // browser extras
    val brFind: String
    val brFindHint: String
    val brFindNext: String
    val brCopyLink: String
    val brShare: String
    val brLinkCopied: String

    // add dialog / edit link
    val qualityLabel: String
    val qualityBest: String
    val mirrorsLabel: String
    val mirrorsHint: String

    // files
    val filesTitle: String
    val filesOpen: String
    val filesEmpty: String
    val filesRename: String
    val filesRenameFailed: String
    val filesPlay: String
    val filesOpenFailed: String
    fun filesSummary(count: Int, size: String): String

    // browser
    val brTabs: String
    val brNewTab: String
    val brBookmarks: String
    val brHistory: String
    val brAddBookmark: String
    val brBookmarkSaved: String
    val brBookmarkRemoved: String
    val brClearHistory: String
    val brAdBlock: String
    val brDesktop: String
    val brEmpty: String
    val brOn: String
    val brOff: String
    val brMenu: String
    val brChooseQuality: String
    val brCloseTab: String
    val brBlocked: String

    // widget
    val widgetIdle: String
}

object MoreEn : MoreStrings {
    override val proxyTitle = "Proxy"
    override val proxyHint = "Send downloads through a proxy server (HTTP or SOCKS5)."
    override val proxyOff = "Off"
    override val proxyHttp = "HTTP"
    override val proxySocks = "SOCKS5"
    override val proxyHost = "Address"
    override val proxyPort = "Port"
    override val proxyUser = "User name (optional)"
    override val proxyPass = "Password (optional)"
    override val proxyNote = "Applies to downloads and to the built-in browser (the browser needs a recent Android System WebView). Running downloads use it when they reconnect."

    override val statsTitle = "Data usage"
    override val statsHint = "How much Downly downloaded each day."
    override val statsOpen = "Show usage chart"
    override val statsToday = "Today"
    override val statsWeek = "Last 7 days"
    override val statsMonth = "Last 30 days"
    override val statsLast7 = "Last 7 days"

    override val backupTitle = "Backup"
    override val backupHint = "Save your settings, queues and download list to a file, and restore them on another phone."
    override val backupExport = "Save backup"
    override val backupImport = "Restore backup"
    override val backupSaved = "Backup saved"
    override val backupFailed = "Could not save the backup"
    override val backupInvalid = "This is not a Downly backup"
    override fun backupRestored(n: Int) = "Restored. $n downloads added (paused)."

    override val perSecond = "/s"

    override val connectionsAdvice = "More connections are usually faster. Some servers allow only a few: when a server refuses, Downly lowers the number by itself and carries on."

    override val schedTitle = "Scheduled downloads with the app closed"
    override val schedHint = "Every queue schedule is set as an Android alarm, so it starts with the app closed and the screen off. If the phone is switched off at the start time, the queue starts as soon as the phone is on again, as long as the time window is still open."
    override val schedExactOk = "Exact alarms are allowed"
    override val schedExactNeeded = "Exact alarms are off: Android may start a queue late. Allow them."
    override val schedExactAllow = "Allow exact alarms"
    override val schedBatteryOk = "Battery saving does not limit Downly"
    override val schedBatteryNeeded = "Battery saving may stop downloads while the screen is off. Remove the limit."
    override val schedBatteryAllow = "Remove battery limit"
    override val schedBrandNote = "Xiaomi, Huawei, Oppo, Vivo and Samsung phones also have their own “Autostart” or “Background activity” switch. Turn it on for Downly in the phone settings."

    override val queueDisabled = "Off"
    override val queueDelete = "Delete queue"
    override val queueDeleteBody = "The queue is removed. Its downloads are not deleted: they move to the main queue."
    override val queueSwitchLabel = "Queue on or off"

    override fun searchResults(n: Int) = "$n results"

    override val saveLocationTitle = "Save location"
    override val saveDirectOn = "Downloads are saved straight into the Downloads/Downly folder while they run. Nothing is stored inside the app, however many downloads you start."
    override val saveDirectOff = "Right now unfinished downloads are kept inside the app and copied to Downloads when they finish, so the app grows while you download. Allow direct saving to avoid that."
    override val saveDirectAllow = "Allow direct saving"
    override val savePromptTitle = "Save without using app space?"
    override val savePromptBody = "Downly can save every download straight into your Downloads folder. Then the app itself never grows, even with unlimited downloads at once. Android asks you to allow “access to all files” once."
    override val savePromptLater = "Not now"

    override val parallelValue = "Number of downloads"
    override val parallelCustomHint = "Type any number. Unlimited starts every download at once."
    override val parallelManyWarning = "Every running download keeps an unfinished file in app storage until it completes. A lower number uses less space."

    override val storageTitle = "Storage used by Downly"
    override val storageHint = "Unfinished downloads are kept inside the app until they complete, then moved to the Downloads folder. Leftovers (files nothing refers to) are safe to remove."
    override val storageUnfinished = "Unfinished downloads"
    override val storageFinished = "Finished files still inside the app"
    override val storageLeftover = "Leftovers"
    override val storageClean = "Clean up"
    override val storageNothing = "Nothing to clean up"
    override fun storageCleaned(freed: String, moved: Int) =
        if (moved > 0) "Freed $freed, moved $moved files to Downloads" else "Freed $freed"

    override val openWith = "Open with"
    override val noAppToOpen = "No app on this phone can open this file"

    override val speedCustom = "Custom"
    override val speedValue = "Speed"
    override val speedCustomHint = "Type any number and pick KB/s or MB/s. The limit applies as soon as the number is valid."

    override val brFind = "Find in page"
    override val brFindHint = "Search text"
    override val brFindNext = "Find next"
    override val brCopyLink = "Copy link"
    override val brShare = "Share page"
    override val brLinkCopied = "Link copied"

    override val qualityLabel = "Quality"
    override val qualityBest = "Best"
    override val mirrorsLabel = "Mirror links (one per line)"
    override val mirrorsHint = "If the main link fails, these are tried next."

    override val filesTitle = "Files"
    override val filesOpen = "File manager"
    override val filesEmpty = "No finished files yet"
    override val filesRename = "Rename"
    override val filesRenameFailed = "Could not rename the file"
    override val filesPlay = "Play"
    override val filesOpenFailed = "Could not open the file"
    override fun filesSummary(count: Int, size: String) = "$count files • $size"

    override val brTabs = "Tabs"
    override val brNewTab = "New tab"
    override val brBookmarks = "Bookmarks"
    override val brHistory = "History"
    override val brAddBookmark = "Bookmark this page"
    override val brBookmarkSaved = "Bookmark saved"
    override val brBookmarkRemoved = "Bookmark removed"
    override val brClearHistory = "Clear history"
    override val brAdBlock = "Ad blocker"
    override val brDesktop = "Desktop site"
    override val brEmpty = "Nothing here yet"
    override val brOn = "on"
    override val brOff = "off"
    override val brMenu = "Menu"
    override val brChooseQuality = "Choose quality"
    override val brCloseTab = "Close tab"
    override val brBlocked = "blocked"

    override val widgetIdle = "No active downloads"
}

object MoreFa : MoreStrings {
    override val proxyTitle = "پروکسی"
    override val proxyHint = "دانلودها را از طریق یک سرور پروکسی (HTTP یا SOCKS5) انجام بده."
    override val proxyOff = "خاموش"
    override val proxyHttp = "HTTP"
    override val proxySocks = "SOCKS5"
    override val proxyHost = "آدرس"
    override val proxyPort = "پورت"
    override val proxyUser = "نام کاربری (اختیاری)"
    override val proxyPass = "رمز عبور (اختیاری)"
    override val proxyNote = "روی دانلودها و مرورگر داخلی اعمال می‌شود (مرورگر به Android System WebView به‌روز نیاز دارد). دانلودهای در حال اجرا هنگام اتصال مجدد از آن استفاده می‌کنند."

    override val statsTitle = "مصرف داده"
    override val statsHint = "هر روز چقدر دانلود شده است."
    override val statsOpen = "نمایش نمودار مصرف"
    override val statsToday = "امروز"
    override val statsWeek = "۷ روز گذشته"
    override val statsMonth = "۳۰ روز گذشته"
    override val statsLast7 = "۷ روز گذشته"

    override val backupTitle = "پشتیبان‌گیری"
    override val backupHint = "تنظیمات، صف‌ها و فهرست دانلودها را در یک فایل ذخیره کنید و روی گوشی دیگر بازیابی کنید."
    override val backupExport = "ذخیره پشتیبان"
    override val backupImport = "بازیابی پشتیبان"
    override val backupSaved = "پشتیبان ذخیره شد"
    override val backupFailed = "ذخیره پشتیبان انجام نشد"
    override val backupInvalid = "این فایل، پشتیبان Downly نیست"
    override fun backupRestored(n: Int) = "بازیابی شد. ${FaStrings.digits(n.toString())} دانلود اضافه شد (متوقف)."

    override val perSecond = "/ثانیه"

    override val connectionsAdvice = "اتصال بیشتر معمولاً سریع‌تر است. بعضی سرورها فقط چند اتصال می‌پذیرند: وقتی سرور رد کند، Downly خودش تعداد را کم می‌کند و ادامه می‌دهد."

    override val schedTitle = "دانلود زمان‌بندی‌شده با برنامه بسته"
    override val schedHint = "زمان‌بندی هر صف به‌صورت آلارم اندروید ثبت می‌شود، پس با بسته بودن برنامه و خاموش بودن صفحه هم شروع می‌شود. اگر در زمان شروع گوشی کاملاً خاموش باشد، به‌محض روشن شدن گوشی و تا وقتی بازه زمانی هنوز باز است، صف شروع می‌شود."
    override val schedExactOk = "آلارم دقیق مجاز است"
    override val schedExactNeeded = "آلارم دقیق خاموش است: ممکن است اندروید صف را با تأخیر شروع کند. مجازش کنید."
    override val schedExactAllow = "مجاز کردن آلارم دقیق"
    override val schedBatteryOk = "صرفه‌جویی باتری Downly را محدود نمی‌کند"
    override val schedBatteryNeeded = "صرفه‌جویی باتری ممکن است هنگام خاموش بودن صفحه دانلودها را متوقف کند. محدودیت را بردارید."
    override val schedBatteryAllow = "برداشتن محدودیت باتری"
    override val schedBrandNote = "در گوشی‌های شیائومی، هوآوی، اوپو، ویوو و سامسونگ یک کلید جداگانه «شروع خودکار» یا «فعالیت در پس‌زمینه» هم هست. آن را برای Downly در تنظیمات گوشی روشن کنید."

    override val queueDisabled = "خاموش"
    override val queueDelete = "حذف صف"
    override val queueDeleteBody = "صف حذف می‌شود. دانلودهای آن پاک نمی‌شوند و به صف اصلی می‌روند."
    override val queueSwitchLabel = "روشن یا خاموش کردن صف"

    override fun searchResults(n: Int) = "${FaStrings.digits(n.toString())} نتیجه"

    override val saveLocationTitle = "محل ذخیره"
    override val saveDirectOn = "دانلودها هنگام اجرا مستقیم داخل پوشه Downloads/Downly ذخیره می‌شوند. هر تعداد دانلود که شروع کنید، چیزی داخل خود برنامه ذخیره نمی‌شود."
    override val saveDirectOff = "الان دانلودهای ناتمام داخل برنامه نگه داشته می‌شوند و بعد از تمام شدن به Downloads کپی می‌شوند؛ برای همین حجم برنامه هنگام دانلود بالا می‌رود. برای جلوگیری، ذخیره مستقیم را مجاز کنید."
    override val saveDirectAllow = "مجاز کردن ذخیره مستقیم"
    override val savePromptTitle = "ذخیره بدون مصرف فضای برنامه؟"
    override val savePromptBody = "Downly می‌تواند هر دانلود را مستقیم داخل پوشه Downloads ذخیره کند. آن‌وقت حجم خود برنامه هیچ‌وقت بالا نمی‌رود، حتی با دانلود نامحدود. اندروید یک‌بار از شما اجازه «دسترسی به همه فایل‌ها» را می‌خواهد."
    override val savePromptLater = "فعلاً نه"

    override val parallelValue = "تعداد دانلود"
    override val parallelCustomHint = "هر عددی را بنویسید. «نامحدود» همه دانلودها را همزمان شروع می‌کند."
    override val parallelManyWarning = "هر دانلود در حال اجرا تا کامل شدن یک فایل ناتمام در حافظه برنامه نگه می‌دارد. عدد کمتر فضای کمتری مصرف می‌کند."

    override val storageTitle = "فضای مصرفی Downly"
    override val storageHint = "دانلودهای ناتمام تا کامل شدن داخل برنامه نگه داشته می‌شوند و بعد به پوشه Downloads منتقل می‌شوند. «باقی‌مانده‌ها» فایل‌هایی هستند که هیچ دانلودی به آن‌ها اشاره نمی‌کند و حذفشان بی‌خطر است."
    override val storageUnfinished = "دانلودهای ناتمام"
    override val storageFinished = "فایل‌های کامل‌شده‌ای که هنوز داخل برنامه‌اند"
    override val storageLeftover = "باقی‌مانده‌ها"
    override val storageClean = "پاک‌سازی"
    override val storageNothing = "چیزی برای پاک‌سازی نیست"
    override fun storageCleaned(freed: String, moved: Int) =
        if (moved > 0) "$freed آزاد شد و ${FaStrings.digits(moved.toString())} فایل به Downloads منتقل شد" else "$freed آزاد شد"

    override val openWith = "باز کردن با"
    override val noAppToOpen = "برنامه‌ای برای باز کردن این فایل روی گوشی نیست"

    override val speedCustom = "دلخواه"
    override val speedValue = "سرعت"
    override val speedCustomHint = "هر عددی را بنویسید و KB/s یا MB/s را انتخاب کنید. به محض معتبر بودن عدد، محدودیت اعمال می‌شود."

    override val brFind = "جستجو در صفحه"
    override val brFindHint = "متن جستجو"
    override val brFindNext = "مورد بعدی"
    override val brCopyLink = "کپی لینک"
    override val brShare = "اشتراک‌گذاری صفحه"
    override val brLinkCopied = "لینک کپی شد"

    override val qualityLabel = "کیفیت"
    override val qualityBest = "بهترین"
    override val mirrorsLabel = "لینک‌های جایگزین (هر خط یک لینک)"
    override val mirrorsHint = "اگر لینک اصلی خطا بدهد، این لینک‌ها به ترتیب امتحان می‌شوند."

    override val filesTitle = "فایل‌ها"
    override val filesOpen = "مدیریت فایل"
    override val filesEmpty = "هنوز فایل کامل‌شده‌ای نیست"
    override val filesRename = "تغییر نام"
    override val filesRenameFailed = "تغییر نام انجام نشد"
    override val filesPlay = "پخش"
    override val filesOpenFailed = "باز کردن فایل ممکن نشد"
    override fun filesSummary(count: Int, size: String) = "${FaStrings.digits(count.toString())} فایل • $size"

    override val brTabs = "تب‌ها"
    override val brNewTab = "تب جدید"
    override val brBookmarks = "نشانک‌ها"
    override val brHistory = "تاریخچه"
    override val brAddBookmark = "نشانک کردن این صفحه"
    override val brBookmarkSaved = "نشانک ذخیره شد"
    override val brBookmarkRemoved = "نشانک حذف شد"
    override val brClearHistory = "پاک کردن تاریخچه"
    override val brAdBlock = "مسدودکننده تبلیغات"
    override val brDesktop = "نسخه دسکتاپ سایت"
    override val brEmpty = "هنوز چیزی نیست"
    override val brOn = "روشن"
    override val brOff = "خاموش"
    override val brMenu = "منو"
    override val brChooseQuality = "انتخاب کیفیت"
    override val brCloseTab = "بستن تب"
    override val brBlocked = "مسدود شد"

    override val widgetIdle = "دانلود فعالی نیست"
}
