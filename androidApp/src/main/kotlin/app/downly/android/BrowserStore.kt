package app.downly.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bookmarks, history and the two browser switches, kept in the app's private preferences. */
class BrowserStore(context: Context) {
    class Page(val title: String, val url: String)

    private val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)

    var adBlock: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(value) = prefs.edit().putBoolean("adblock", value).apply()

    var desktop: Boolean
        get() = prefs.getBoolean("desktop", false)
        set(value) = prefs.edit().putBoolean("desktop", value).apply()

    fun bookmarks(): List<Page> = read("bookmarks")

    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    /** Adds the page, or removes it when it is already bookmarked. Returns true when it was added. */
    fun toggleBookmark(title: String, url: String): Boolean {
        val list = bookmarks().toMutableList()
        val existing = list.indexOfFirst { it.url == url }
        return if (existing >= 0) {
            list.removeAt(existing)
            write("bookmarks", list)
            false
        } else {
            list.add(0, Page(title.ifBlank { url }, url))
            write("bookmarks", list)
            true
        }
    }

    fun removeBookmark(url: String) = write("bookmarks", bookmarks().filterNot { it.url == url })

    fun history(): List<Page> = read("history")

    fun addHistory(title: String, url: String) {
        val list = history().filterNot { it.url == url }.toMutableList()
        list.add(0, Page(title.ifBlank { url }, url))
        write("history", list.take(200))
    }

    /** The pages that were open when the browser was closed, and which one was in front. */
    fun savedTabs(): List<String> = try {
        val array = JSONArray(prefs.getString("tabs", "[]"))
        (0 until array.length()).map { array.getString(it) }.filter { it.startsWith("http") }
    } catch (e: Exception) {
        emptyList()
    }

    val savedTabIndex: Int
        get() = prefs.getInt("tab_index", 0)

    fun saveTabs(urls: List<String>, index: Int) {
        val array = JSONArray()
        urls.forEach { array.put(it) }
        prefs.edit().putString("tabs", array.toString()).putInt("tab_index", index).apply()
    }

    fun clearHistory() = write("history", emptyList())

    private fun read(key: String): List<Page> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getJSONObject(it).let { o -> Page(o.optString("t"), o.optString("u")) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(key: String, pages: List<Page>) {
        val array = JSONArray()
        pages.forEach { array.put(JSONObject().put("t", it.title).put("u", it.url)) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
