package app.downly.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.downly.data.AppLanguage
import app.downly.ui.components.FileCategory

/** All user-visible text. Add a language = implement this interface. */
interface AppStrings {
    val appName: String
    val urlHint: String
    val add: String
    val paste: String
    val invalidUrl: String
    val emptyTitle: String
    val emptyBody: String
    val noMatches: String
    val filterAll: String
    val filterActive: String
    val filterDone: String
    val filterFailed: String
    val searchHint: String
    val currentSpeed: String
    val statActive: String
    val statDone: String
    val statTotal: String
    val preparing: String
    val paused: String
    val completed: String
    val failed: String
    val pause: String
    val resume: String
    val retry: String
    val open: String
    val share: String
    val copyLink: String
    val linkCopied: String
    val details: String
    val delete: String
    val cancel: String
    val close: String
    val back: String
    val pauseAll: String
    val resumeAll: String
    val clipboardTitle: String
    val download: String
    val dismiss: String
    val incomingTitle: String
    val deleteTitle: String
    val deleteBody: String
    val deleteAlsoFile: String
    val detailsUrl: String
    val detailsSize: String
    val detailsSaved: String
    val detailsConnections: String
    val detailsStatus: String
    val settings: String
    val language: String
    val theme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val connections: String
    val connectionsHint: String
    val autoResume: String
    val autoResumeHint: String
    val notifications: String
    val notificationsHint: String
    val storage: String
    val clearCompleted: String
    val clearedToast: String
    val about: String
    val aboutBody: String
    val clipboardEmpty: String
    val openFailed: String
    val notifDone: String
    val notifFailed: String
    val secondsUnit: String
    val minutesUnit: String
    val hoursUnit: String
    val and: String
    val decimalSeparator: String
    val byteUnits: List<String>
    val queued: String
    val sortNewest: String
    val sortOldest: String
    val sortName: String
    val sortSize: String
    val editLink: String
    val save: String
    val linkUpdated: String
    val maxSimultaneous: String
    val maxSimultaneousHint: String
    val speedLimit: String
    val speedLimitHint: String
    val unlimited: String
    val savePublic: String
    val savePublicHint: String
    val folderCurrent: String
    val folderDefault: String
    val folderChoose: String
    val folderReset: String
    val batteryTitle: String
    val batteryHint: String
    val batteryButton: String
    val daysLabel: String
    val onboardTitle: String
    val onboardLine1: String
    val onboardLine2: String
    val onboardLine3: String
    val onboardStart: String
    val dayOrder: List<Int>
    fun dayShort(day: Int): String
    fun queueDefaultName(n: Int): String
    fun moveToQueueNamed(name: String): String
    val navDownloads: String
    val navQueues: String
    val navAdd: String
    val navBrowser: String
    val addTitle: String
    val linkLabel: String
    val nameLabel: String
    val nameAutoHint: String
    val pasteAction: String
    val folderAction: String
    val folderTitle: String
    val folderAuto: String
    val folderCustomHint: String
    val folderNote: String
    val moreSettings: String
    val queueLabel: String
    val newQueue: String
    val startNow: String
    val addOnly: String
    val detecting: String
    val queuesTitle: String
    val queueMain: String
    val queueNameHint: String
    val queueEdit: String
    val queueDelete: String
    val queueStart: String
    val queuePause: String
    val queueNoSchedule: String
    val queueShow: String
    val parallelLabel: String
    val parallelAuto: String
    val queueSpeed: String
    val queueFinish: String
    val finishNothing: String
    val finishInternetPanel: String
    val finishNote: String
    val allQueues: String
    val moveToQueue: String
    val startSelected: String
    val pauseSelected: String
    val finishedNotifTitle: String
    val finishedNotifBody: String
    val timeTitle: String
    val activeNow: String
    fun linksFound(n: Int): String
    fun selectedCount(n: Int): String
    fun deleteManyTitle(n: Int): String
    fun queueCount(n: Int): String
    val browserDownloads: String
    val browserAdded: String
    val browserNoMedia: String
    val browserBackToApp: String
    val queueEmpty: String
    val siteGrabber: String
    val siteGrabberHint: String
    val scan: String
    val noFilesFound: String
    val scanFailed: String
    val downloadSelected: String
    val selectAll: String
    val moveToTop: String
    val moveToBottom: String
    val itemSpeedLimit: String
    val verifyChecksum: String
    val checksumHint: String
    val checksumMatch: String
    val checksumMismatch: String
    val checksumInvalid: String
    val checksumNoFile: String
    val wifiOnly: String
    val wifiOnlyHint: String
    val autoRetry: String
    val autoRetryHint: String
    val off: String
    val organizeByCategory: String
    val organizeByCategoryHint: String
    val schedule: String
    val scheduleHint: String
    val scheduleEnable: String
    val scheduleStart: String
    val scheduleStop: String
    val allTypes: String

    fun categoryName(category: FileCategory): String
    fun linksAdded(count: Int): String
    fun notifDownloadingMany(count: Int): String
    fun remaining(time: String): String
    fun digits(text: String): String

    /** Texts of the newer features (proxy, usage, backup, files, browser tools). */
    val more: MoreStrings
}

object EnStrings : AppStrings {
    override val appName = "Downly"
    override val urlHint = "Paste a link (or several)"
    override val add = "Add"
    override val paste = "Paste"
    override val invalidUrl = "Link must start with http:// or https://"
    override val emptyTitle = "No downloads yet"
    override val emptyBody = "Paste a direct link above and tap Add"
    override val noMatches = "No matching downloads"
    override val filterAll = "All"
    override val filterActive = "Active"
    override val filterDone = "Done"
    override val filterFailed = "Failed"
    override val searchHint = "Search downloads"
    override val currentSpeed = "Current speed"
    override val statActive = "Active"
    override val statDone = "Completed"
    override val statTotal = "Downloaded"
    override val preparing = "Preparing…"
    override val paused = "Paused"
    override val completed = "Completed"
    override val failed = "Failed"
    override val pause = "Pause"
    override val resume = "Resume"
    override val retry = "Retry"
    override val open = "Open"
    override val share = "Share"
    override val copyLink = "Copy link"
    override val linkCopied = "Link copied"
    override val details = "Details"
    override val delete = "Delete"
    override val cancel = "Cancel"
    override val close = "Close"
    override val back = "Back"
    override val pauseAll = "Pause all"
    override val resumeAll = "Resume all"
    override val clipboardTitle = "Link found in clipboard"
    override val download = "Download"
    override val dismiss = "Dismiss"
    override val incomingTitle = "Download this file?"
    override val deleteTitle = "Remove download?"
    override val deleteBody = "It will be removed from the list."
    override val deleteAlsoFile = "Also delete the file from storage"
    override val detailsUrl = "Link"
    override val detailsSize = "Size"
    override val detailsSaved = "Saved to"
    override val detailsConnections = "Connections"
    override val detailsStatus = "Status"
    override val settings = "Settings"
    override val language = "Language"
    override val theme = "Theme"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val connections = "Connections per download"
    override val connectionsHint = "More connections can speed up large files. Applies to new downloads."
    override val autoResume = "Auto-resume on launch"
    override val autoResumeHint = "Continue interrupted downloads when the app starts"
    override val notifications = "Completion notifications"
    override val notificationsHint = "Notify when a download finishes or fails"
    override val storage = "Storage"
    override val clearCompleted = "Clear completed list"
    override val clearedToast = "Completed list cleared"
    override val about = "About"
    override val aboutBody = "Open-source download manager for Android and Windows."
    override val clipboardEmpty = "Clipboard is empty"
    override val openFailed = "Couldn't open the file"
    override val notifDone = "Download complete"
    override val notifFailed = "Download failed"
    override val secondsUnit = "sec"
    override val minutesUnit = "min"
    override val hoursUnit = "h"
    override val and = " "
    override val decimalSeparator = "."
    override val byteUnits = listOf("B", "KB", "MB", "GB", "TB")
    override val queued = "Waiting in queue"
    override val sortNewest = "Newest first"
    override val sortOldest = "Oldest first"
    override val sortName = "Name (A–Z)"
    override val sortSize = "Largest first"
    override val editLink = "Edit link"
    override val save = "Save"
    override val linkUpdated = "Link updated"
    override val maxSimultaneous = "Simultaneous downloads"
    override val maxSimultaneousHint = "Extra downloads wait in the queue until a slot is free."
    override val speedLimit = "Speed limit"
    override val speedLimitHint = "Caps the total download speed of the app."
    override val unlimited = "Unlimited"
    override val savePublic = "Save to the Downloads folder"
    override val savePublicHint = "Finished files show up in Files and Gallery and stay if you uninstall Downly. Needs extra free space while moving."
    override val folderCurrent = "Folder"
    override val folderDefault = "Downloads/Downly"
    override val folderChoose = "Choose folder"
    override val folderReset = "Use default"
    override val batteryTitle = "Background downloads"
    override val batteryHint = "Allow Downly to run without battery limits so long downloads are not stopped."
    override val batteryButton = "Open battery settings"
    override val daysLabel = "Start on these days"
    override val onboardTitle = "Welcome to Downly"
    override val onboardLine1 = "Fast downloads with several connections, and resume after any interruption."
    override val onboardLine2 = "Queues with schedules, speed limits and Wi-Fi only mode."
    override val onboardLine3 = "Finished files are saved in your Downloads folder."
    override val onboardStart = "Get started"
    override val dayOrder = listOf(0, 1, 2, 3, 4, 5, 6)
    override fun dayShort(day: Int): String = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[day.coerceIn(0, 6)]
    override fun queueDefaultName(n: Int): String = "Queue $n"
    override fun moveToQueueNamed(name: String): String = "Move to $name"
    override val navDownloads = "Downloads"
    override val navQueues = "Queues"
    override val navAdd = "Add"
    override val navBrowser = "Browser"
    override val addTitle = "New download"
    override val linkLabel = "Link"
    override val nameLabel = "Name"
    override val nameAutoHint = "Detected automatically"
    override val pasteAction = "Paste"
    override val folderAction = "Save location"
    override val folderTitle = "Save to"
    override val folderAuto = "Automatic"
    override val folderCustomHint = "Custom folder name"
    override val folderNote = "Folders are created inside Downly's download folder."
    override val moreSettings = "More settings"
    override val queueLabel = "Queue"
    override val newQueue = "New queue"
    override val startNow = "Start"
    override val addOnly = "Add"
    override val detecting = "Checking the link…"
    override val queuesTitle = "Download queues"
    override val queueMain = "Queue 1"
    override val queueNameHint = "Queue name"
    override val queueEdit = "Edit queue"
    override val queueDelete = "Delete queue"
    override val queueStart = "Start queue"
    override val queuePause = "Pause queue"
    override val queueNoSchedule = "No schedule"
    override val queueShow = "Show downloads"
    override val parallelLabel = "Simultaneous downloads"
    override val parallelAuto = "Auto"
    override val queueSpeed = "Speed limit of the queue"
    override val queueFinish = "When the queue finishes"
    override val finishNothing = "Do nothing"
    override val finishInternetPanel = "Offer to turn off the internet"
    override val finishNote = "Android does not let apps switch Wi-Fi or mobile data off by themselves. Downly shows a notification; one tap opens the internet panel where you switch it off."
    override val allQueues = "All queues"
    override val moveToQueue = "Move to queue"
    override val startSelected = "Start"
    override val pauseSelected = "Pause"
    override val finishedNotifTitle = "Downloads finished"
    override val finishedNotifBody = "Tap to turn off Wi-Fi or mobile data."
    override val timeTitle = "Select time"
    override val activeNow = "active"
    override fun linksFound(n: Int): String = "$n links found"
    override fun selectedCount(n: Int): String = "$n selected"
    override fun deleteManyTitle(n: Int): String = "Delete $n downloads?"
    override fun queueCount(n: Int): String = "$n downloads"
    override val browserDownloads = "Download media"
    override val browserAdded = "Added to downloads"
    override val browserNoMedia = "No video or audio found yet. Start playing it first."
    override val browserBackToApp = "Back to app"
    override val queueEmpty = "No downloads in this queue"
    override val siteGrabber = "Grab files from a page"
    override val siteGrabberHint = "Page link (a site with many files)"
    override val scan = "Scan"
    override val noFilesFound = "No downloadable files found on this page."
    override val scanFailed = "Could not open the page."
    override val downloadSelected = "Download selected"
    override val selectAll = "Select all"
    override val moveToTop = "Move to top of queue"
    override val moveToBottom = "Move to bottom of queue"
    override val itemSpeedLimit = "Speed limit for this file"
    override val verifyChecksum = "Verify"
    override val checksumHint = "Paste MD5 / SHA-1 / SHA-256"
    override val checksumMatch = "Checksum matches. The file is intact."
    override val checksumMismatch = "Checksum does NOT match. The file may be damaged or changed."
    override val checksumInvalid = "This is not a valid MD5, SHA-1 or SHA-256 hash."
    override val checksumNoFile = "The file could not be read."
    override val wifiOnly = "Wi-Fi only"
    override val wifiOnlyHint = "Pause downloads on mobile data and continue on Wi-Fi."
    override val autoRetry = "Auto-retry"
    override val autoRetryHint = "Retry a failed download automatically, waiting a little longer each time."
    override val off = "Off"
    override val organizeByCategory = "Sort into folders"
    override val organizeByCategoryHint = "Save new files into Video, Audio, Documents... sub-folders."
    override val schedule = "Scheduler"
    override val scheduleHint = "Resume all downloads at the start time and pause them at the stop time."
    override val scheduleEnable = "Enable scheduler"
    override val scheduleStart = "Start at"
    override val scheduleStop = "Stop at"
    override val allTypes = "All types"

    override fun categoryName(category: FileCategory) = when (category) {
        FileCategory.Video -> "Video"
        FileCategory.Audio -> "Audio"
        FileCategory.Image -> "Images"
        FileCategory.Document -> "Documents"
        FileCategory.Archive -> "Archives"
        FileCategory.App -> "Apps"
        FileCategory.Program -> "Programs"
        FileCategory.Other -> "Other"
    }

    override fun linksAdded(count: Int) = "$count links added"

    override fun notifDownloadingMany(count: Int) = "Downloading $count files"
    override fun remaining(time: String) = "$time left"
    override fun digits(text: String) = text
    override val more: MoreStrings = MoreEn
}

object FaStrings : AppStrings {
    override val appName = "Downly"
    override val urlHint = "یک یا چند لینک را بچسبانید"
    override val add = "افزودن"
    override val paste = "چسباندن"
    override val invalidUrl = "لینک باید با http:// یا https:// شروع شود"
    override val emptyTitle = "هنوز دانلودی نیست"
    override val emptyBody = "یک لینک مستقیم را بالا بچسبانید و «افزودن» را بزنید"
    override val noMatches = "موردی پیدا نشد"
    override val filterAll = "همه"
    override val filterActive = "در جریان"
    override val filterDone = "تکمیل‌شده"
    override val filterFailed = "ناموفق"
    override val searchHint = "جستجو در دانلودها"
    override val currentSpeed = "سرعت فعلی"
    override val statActive = "فعال"
    override val statDone = "تکمیل‌شده"
    override val statTotal = "حجم دانلودشده"
    override val preparing = "در حال آماده‌سازی…"
    override val paused = "متوقف شده"
    override val completed = "تکمیل شد"
    override val failed = "ناموفق"
    override val pause = "توقف"
    override val resume = "ادامه"
    override val retry = "تلاش مجدد"
    override val open = "باز کردن"
    override val share = "اشتراک‌گذاری"
    override val copyLink = "کپی لینک"
    override val linkCopied = "لینک کپی شد"
    override val details = "جزئیات"
    override val delete = "حذف"
    override val cancel = "انصراف"
    override val close = "بستن"
    override val back = "بازگشت"
    override val pauseAll = "توقف همه"
    override val resumeAll = "ادامه همه"
    override val clipboardTitle = "لینک در کلیپ‌بورد پیدا شد"
    override val download = "دانلود"
    override val dismiss = "نادیده"
    override val incomingTitle = "این فایل دانلود شود؟"
    override val deleteTitle = "حذف دانلود؟"
    override val deleteBody = "این مورد از فهرست حذف می‌شود."
    override val deleteAlsoFile = "فایل از حافظه هم پاک شود"
    override val detailsUrl = "لینک"
    override val detailsSize = "حجم"
    override val detailsSaved = "محل ذخیره"
    override val detailsConnections = "تعداد اتصال"
    override val detailsStatus = "وضعیت"
    override val settings = "تنظیمات"
    override val language = "زبان"
    override val theme = "پوسته"
    override val themeSystem = "سیستم"
    override val themeLight = "روشن"
    override val themeDark = "تیره"
    override val connections = "اتصال برای هر دانلود"
    override val connectionsHint = "اتصال بیشتر می‌تواند سرعت فایل‌های بزرگ را بالا ببرد. فقط روی دانلودهای جدید اعمال می‌شود."
    override val autoResume = "ادامه خودکار هنگام اجرا"
    override val autoResumeHint = "دانلودهای نیمه‌تمام با باز شدن برنامه ادامه پیدا می‌کنند"
    override val notifications = "اعلان پایان دانلود"
    override val notificationsHint = "هنگام اتمام یا خطای دانلود اعلان نشان بده"
    override val storage = "حافظه"
    override val clearCompleted = "پاک کردن فهرست تکمیل‌شده‌ها"
    override val clearedToast = "فهرست پاک شد"
    override val about = "درباره"
    override val aboutBody = "مدیر دانلود متن‌باز برای اندروید و ویندوز."
    override val clipboardEmpty = "کلیپ‌بورد خالی است"
    override val openFailed = "فایل باز نشد"
    override val notifDone = "دانلود کامل شد"
    override val notifFailed = "دانلود ناموفق بود"
    override val secondsUnit = "ثانیه"
    override val minutesUnit = "دقیقه"
    override val hoursUnit = "ساعت"
    override val and = " و "
    override val decimalSeparator = "٫"
    override val byteUnits = listOf("بایت", "کیلوبایت", "مگابایت", "گیگابایت", "ترابایت")
    override val queued = "در صف انتظار"
    override val sortNewest = "جدیدترین"
    override val sortOldest = "قدیمی‌ترین"
    override val sortName = "نام (الف تا ی)"
    override val sortSize = "بیشترین حجم"
    override val editLink = "ویرایش لینک"
    override val save = "ذخیره"
    override val linkUpdated = "لینک به‌روز شد"
    override val maxSimultaneous = "دانلود همزمان"
    override val maxSimultaneousHint = "دانلودهای اضافه تا آزاد شدن جا در صف می‌مانند."
    override val speedLimit = "محدودیت سرعت"
    override val speedLimitHint = "سقف سرعت کل دانلودهای برنامه را تعیین می‌کند."
    override val unlimited = "نامحدود"
    override val savePublic = "ذخیره در پوشه‌ی Downloads"
    override val savePublicHint = "فایل‌های کامل‌شده در فایل‌منیجر و گالری دیده می‌شوند و با حذف برنامه پاک نمی‌شوند. هنگام انتقال به کمی فضای خالی اضافه نیاز است."
    override val folderCurrent = "پوشه"
    override val folderDefault = "Downloads/Downly"
    override val folderChoose = "انتخاب پوشه"
    override val folderReset = "پیش‌فرض"
    override val batteryTitle = "دانلود در پس‌زمینه"
    override val batteryHint = "اجازه بدهید Downly بدون محدودیت باتری اجرا شود تا دانلودهای طولانی قطع نشوند."
    override val batteryButton = "باز کردن تنظیمات باتری"
    override val daysLabel = "شروع در این روزها"
    override val onboardTitle = "به Downly خوش آمدید"
    override val onboardLine1 = "دانلود سریع با چند اتصال و ادامه‌ی دانلود بعد از هر قطعی."
    override val onboardLine2 = "صف‌ها با زمان‌بندی، محدودیت سرعت و حالت فقط وای‌فای."
    override val onboardLine3 = "فایل‌های کامل‌شده در پوشه‌ی Downloads ذخیره می‌شوند."
    override val onboardStart = "شروع"
    override val dayOrder = listOf(6, 0, 1, 2, 3, 4, 5)
    override fun dayShort(day: Int): String = listOf("ی", "د", "س", "چ", "پ", "ج", "ش")[day.coerceIn(0, 6)]
    override fun queueDefaultName(n: Int): String = "صف ${digits(n.toString())}"
    override fun moveToQueueNamed(name: String): String = "انتقال به $name"
    override val navDownloads = "دانلودها"
    override val navQueues = "صف‌ها"
    override val navAdd = "افزودن"
    override val navBrowser = "مرورگر"
    override val addTitle = "دانلود جدید"
    override val linkLabel = "لینک"
    override val nameLabel = "نام فایل"
    override val nameAutoHint = "خودکار تشخیص داده می‌شود"
    override val pasteAction = "چسباندن"
    override val folderAction = "محل ذخیره"
    override val folderTitle = "ذخیره در"
    override val folderAuto = "خودکار"
    override val folderCustomHint = "نام پوشه دلخواه"
    override val folderNote = "پوشه‌ها داخل پوشه‌ی دانلودِ برنامه ساخته می‌شوند."
    override val moreSettings = "تنظیمات بیشتر"
    override val queueLabel = "صف"
    override val newQueue = "صف جدید"
    override val startNow = "شروع"
    override val addOnly = "افزودن"
    override val detecting = "در حال بررسی لینک…"
    override val queuesTitle = "صف‌های دانلود"
    override val queueMain = "صف ۱"
    override val queueNameHint = "نام صف"
    override val queueEdit = "ویرایش صف"
    override val queueDelete = "حذف صف"
    override val queueStart = "شروع صف"
    override val queuePause = "توقف صف"
    override val queueNoSchedule = "بدون زمان‌بندی"
    override val queueShow = "نمایش دانلودها"
    override val parallelLabel = "تعداد دانلود هم‌زمان"
    override val parallelAuto = "خودکار"
    override val queueSpeed = "محدودیت سرعت صف"
    override val queueFinish = "وقتی صف تمام شد"
    override val finishNothing = "کاری نکن"
    override val finishInternetPanel = "پیشنهاد خاموش کردن اینترنت"
    override val finishNote = "اندروید اجازه نمی‌دهد برنامه‌ها خودشان وای‌فای یا اینترنت همراه را خاموش کنند. برنامه یک اعلان نشان می‌دهد و با یک ضربه پنل اینترنت باز می‌شود تا خاموشش کنید."
    override val allQueues = "همه صف‌ها"
    override val moveToQueue = "انتقال به صف"
    override val startSelected = "شروع"
    override val pauseSelected = "توقف"
    override val finishedNotifTitle = "دانلودها تمام شد"
    override val finishedNotifBody = "برای خاموش کردن وای‌فای یا اینترنت همراه ضربه بزنید."
    override val timeTitle = "انتخاب ساعت"
    override val activeNow = "فعال"
    override fun linksFound(n: Int): String = "${digits(n.toString())} لینک پیدا شد"
    override fun selectedCount(n: Int): String = "${digits(n.toString())} مورد انتخاب شد"
    override fun deleteManyTitle(n: Int): String = "${digits(n.toString())} دانلود حذف شود؟"
    override fun queueCount(n: Int): String = "${digits(n.toString())} دانلود"
    override val browserDownloads = "دانلود رسانه"
    override val browserAdded = "به دانلودها اضافه شد"
    override val browserNoMedia = "هنوز ویدیو یا صدایی پیدا نشد. ابتدا پخش آن را شروع کنید."
    override val browserBackToApp = "بازگشت به برنامه"
    override val queueEmpty = "هیچ دانلودی در این صف نیست"
    override val siteGrabber = "گرفتن فایل‌ها از یک صفحه"
    override val siteGrabberHint = "لینک صفحه (سایتی با فایل‌های زیاد)"
    override val scan = "اسکن"
    override val noFilesFound = "فایل قابل دانلودی در این صفحه پیدا نشد."
    override val scanFailed = "صفحه باز نشد."
    override val downloadSelected = "دانلود موارد انتخاب‌شده"
    override val selectAll = "انتخاب همه"
    override val moveToTop = "انتقال به ابتدای صف"
    override val moveToBottom = "انتقال به انتهای صف"
    override val itemSpeedLimit = "محدودیت سرعت این فایل"
    override val verifyChecksum = "بررسی"
    override val checksumHint = "MD5 / SHA-1 / SHA-256 را بچسبانید"
    override val checksumMatch = "مقدار هش یکسان است. فایل سالم است."
    override val checksumMismatch = "مقدار هش یکسان نیست. ممکن است فایل خراب یا تغییر کرده باشد."
    override val checksumInvalid = "این مقدار یک هش معتبر MD5، SHA-1 یا SHA-256 نیست."
    override val checksumNoFile = "فایل خوانده نشد."
    override val wifiOnly = "فقط با وای‌فای"
    override val wifiOnlyHint = "روی اینترنت همراه دانلودها متوقف می‌شوند و با وای‌فای ادامه پیدا می‌کنند."
    override val autoRetry = "تلاش مجدد خودکار"
    override val autoRetryHint = "دانلود ناموفق را خودکار دوباره امتحان می‌کند و هر بار کمی بیشتر صبر می‌کند."
    override val off = "خاموش"
    override val organizeByCategory = "دسته‌بندی در پوشه‌ها"
    override val organizeByCategoryHint = "فایل‌های جدید در پوشه‌های ویدیو، صوت، اسناد و... ذخیره می‌شوند."
    override val schedule = "زمان‌بندی"
    override val scheduleHint = "در ساعت شروع همه دانلودها ادامه پیدا می‌کنند و در ساعت پایان متوقف می‌شوند."
    override val scheduleEnable = "فعال‌سازی زمان‌بندی"
    override val scheduleStart = "شروع در"
    override val scheduleStop = "پایان در"
    override val allTypes = "همه نوع‌ها"

    override fun categoryName(category: FileCategory) = when (category) {
        FileCategory.Video -> "ویدیو"
        FileCategory.Audio -> "صوت"
        FileCategory.Image -> "تصویر"
        FileCategory.Document -> "سند"
        FileCategory.Archive -> "فشرده"
        FileCategory.App -> "برنامه"
        FileCategory.Program -> "نرم‌افزار"
        FileCategory.Other -> "سایر"
    }

    override fun linksAdded(count: Int) = digits("$count لینک اضافه شد")

    override fun notifDownloadingMany(count: Int): String = digits("در حال دانلود $count فایل")
    override fun remaining(time: String) = "$time مانده"
    override val more: MoreStrings = MoreFa
    override fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') '۰' + (c - '0') else c)
    }
}

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.EN -> EnStrings
    AppLanguage.FA -> FaStrings
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { EnStrings }
