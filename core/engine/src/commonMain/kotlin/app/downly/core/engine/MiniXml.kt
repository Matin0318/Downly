package app.downly.core.engine

/** One element of a parsed XML document. */
internal class XmlNode(val name: String, val attrs: Map<String, String>) {
    val children = ArrayList<XmlNode>()
    var text = ""

    fun attr(key: String): String? = attrs[key]
    fun child(tag: String): XmlNode? = children.firstOrNull { it.name == tag }
    fun all(tag: String): List<XmlNode> = children.filter { it.name == tag }
}

/**
 * A very small XML reader, enough for DASH manifests: elements, attributes, text, comments, CDATA.
 * Namespace prefixes of element names are dropped (`ns:Name` becomes `Name`).
 */
internal object MiniXml {
    private val attribute = Regex("""([^\s=]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parse(source: String): XmlNode {
        val stack = ArrayList<XmlNode>()
        var root: XmlNode? = null
        var i = 0
        val n = source.length
        while (i < n) {
            val lt = source.indexOf('<', i)
            if (lt < 0) break
            if (lt > i && stack.isNotEmpty()) stack.last().text += decode(source.substring(i, lt))
            i = lt
            when {
                source.startsWith("<!--", i) -> {
                    val e = source.indexOf("-->", i)
                    i = if (e < 0) n else e + 3
                }
                source.startsWith("<![CDATA[", i) -> {
                    val e = source.indexOf("]]>", i)
                    val end = if (e < 0) n else e
                    if (stack.isNotEmpty()) stack.last().text += source.substring(i + 9, end)
                    i = if (e < 0) n else e + 3
                }
                source.startsWith("<?", i) || source.startsWith("<!", i) -> {
                    val e = source.indexOf('>', i)
                    i = if (e < 0) n else e + 1
                }
                source.startsWith("</", i) -> {
                    val e = source.indexOf('>', i)
                    if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                    i = if (e < 0) n else e + 1
                }
                else -> {
                    var j = i + 1
                    var quote = ' '
                    var inQuote = false
                    while (j < n) {
                        val c = source[j]
                        if (inQuote) {
                            if (c == quote) inQuote = false
                        } else if (c == '"' || c == '\'') {
                            inQuote = true
                            quote = c
                        } else if (c == '>') {
                            break
                        }
                        j++
                    }
                    if (j >= n) break
                    var body = source.substring(i + 1, j)
                    val selfClosing = body.endsWith("/")
                    if (selfClosing) body = body.dropLast(1)
                    val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
                    val name = body.substring(0, nameEnd).substringAfter(':')
                    val attrs = HashMap<String, String>()
                    for (m in attribute.findAll(body.substring(nameEnd))) {
                        attrs[m.groupValues[1]] = decode(m.groups[2]?.value ?: m.groups[3]?.value ?: "")
                    }
                    val node = XmlNode(name, attrs)
                    if (stack.isNotEmpty()) stack.last().children.add(node) else if (root == null) root = node
                    if (!selfClosing) stack.add(node)
                    i = j + 1
                }
            }
        }
        return root ?: throw DownloadException("This is not a valid manifest")
    }

    private fun decode(s: String): String =
        if ('&' !in s) s else s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
