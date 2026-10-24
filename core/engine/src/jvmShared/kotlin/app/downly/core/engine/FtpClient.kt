package app.downly.core.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import kotlin.time.TimeSource

private class FtpAddress(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val path: String,
) {
    val fileName: String
        get() = path.substringAfterLast('/').ifBlank { "file" }
}

private fun parseFtp(url: String): FtpAddress {
    val rest = url.substringAfter("://").substringBefore('#').substringBefore('?')
    val authority = rest.substringBefore('/')
    val rawPath = if (rest.contains('/')) "/" + rest.substringAfter('/') else "/"
    val login = if (authority.contains('@')) authority.substringBeforeLast('@') else ""
    val hostPort = authority.substringAfterLast('@')
    val host = hostPort.substringBefore(':')
    val port = hostPort.substringAfter(':', "21").toIntOrNull() ?: 21
    if (host.isBlank()) throw DownloadException("Invalid FTP link")
    fun decode(s: String) = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }
    val user = if (login.isBlank()) "anonymous" else decode(login.substringBefore(':'))
    val password = if (login.isBlank()) "anonymous@" else decode(login.substringAfter(':', ""))
    return FtpAddress(host, port, user, password, decode(rawPath))
}

/** One control connection. Blocking; always used from the IO dispatcher. */
private class FtpSession(private val address: FtpAddress) : Closeable {
    private val socket = Socket()
    private lateinit var reader: BufferedReader
    private lateinit var writer: java.io.Writer

    fun open() {
        socket.connect(InetSocketAddress(address.host, address.port), 15_000)
        socket.soTimeout = 30_000
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        writer = socket.getOutputStream().writer(Charsets.UTF_8)
        expect(readReply(), 220)
        var reply = send("USER ${address.user}")
        if (reply.first == 331) reply = send("PASS ${address.password}")
        if (reply.first != 230) throw DownloadException("FTP login failed (${reply.first})")
        expect(send("TYPE I"), 200)
    }

    fun readReply(): Pair<Int, String> {
        val first = reader.readLine() ?: throw DownloadException("FTP server closed the connection", true)
        val code = first.take(3).toIntOrNull() ?: throw DownloadException("Bad FTP reply", true)
        val text = StringBuilder(first)
        if (first.length > 3 && first[3] == '-') {
            while (true) {
                val line = reader.readLine() ?: break
                text.append('\n').append(line)
                if (line.length >= 4 && line.startsWith("$code ")) break
            }
        }
        return code to text.toString()
    }

    fun send(command: String): Pair<Int, String> {
        writer.write(command + "\r\n")
        writer.flush()
        return readReply()
    }

    fun expect(reply: Pair<Int, String>, vararg codes: Int) {
        if (reply.first !in codes) {
            val retry = reply.first >= 400 && reply.first < 500
            throw DownloadException("FTP error ${reply.first}", retry)
        }
    }

    fun size(path: String): Long {
        val reply = send("SIZE $path")
        if (reply.first == 550) throw DownloadException("FTP: file not found")
        expect(reply, 213)
        return reply.second.substringAfter(' ').trim().toLongOrNull()
            ?: throw DownloadException("FTP server did not report the size")
    }

    /** Opens the passive data connection. The server address is taken from the control connection (works behind NAT). */
    fun openData(): Socket {
        val reply = send("PASV")
        expect(reply, 227)
        val numbers = Regex("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").find(reply.second)
            ?: throw DownloadException("FTP: bad passive reply")
        val port = numbers.groupValues[5].toInt() * 256 + numbers.groupValues[6].toInt()
        val data = Socket()
        data.connect(InetSocketAddress(address.host, port), 15_000)
        data.soTimeout = 30_000
        return data
    }

    override fun close() {
        runCatching { writer.write("QUIT\r\n"); writer.flush() }
        runCatching { socket.close() }
    }
}

internal actual suspend fun ftpProbe(url: String): FtpFileInfo = withContext(Dispatchers.IO) {
    val address = parseFtp(url)
    FtpSession(address).use { session ->
        session.open()
        FtpFileInfo(session.size(address.path), address.fileName)
    }
}

internal actual fun ftpDownload(
    url: String,
    destination: Path,
    fileSystem: FileSystem,
    startAt: Long,
    limiters: List<RateLimiter>,
): Flow<Long> = flow {
    val address = parseFtp(url)
    val part = "$destination.part".toPath()
    destination.parent?.let { fileSystem.createDirectories(it) }
    val session = FtpSession(address)
    var data: Socket? = null
    try {
        session.open()
        if (startAt > 0) session.expect(session.send("REST $startAt"), 350)
        data = session.openData()
        val start = session.send("RETR ${address.path}")
        session.expect(start, 125, 150)

        val sink = (if (startAt > 0) fileSystem.appendingSink(part) else fileSystem.sink(part)).buffer()
        var total = startAt
        var lastEmit = TimeSource.Monotonic.markNow()
        try {
            val input = data.getInputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(chunk)
                if (n < 0) break
                sink.write(chunk, 0, n)
                total += n
                for (limiter in limiters) limiter.acquire(n)
                if (lastEmit.elapsedNow().inWholeMilliseconds >= 250) {
                    lastEmit = TimeSource.Monotonic.markNow()
                    emit(total)
                }
            }
        } finally {
            sink.close()
        }
        runCatching { data.close() }
        runCatching { session.expect(session.readReply(), 226, 250) }
        emit(total)
    } finally {
        runCatching { data?.close() }
        session.close()
    }
}.flowOn(Dispatchers.IO)
