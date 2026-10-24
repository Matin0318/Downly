package app.downly.core.engine

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Sockets tuned for big downloads: a 1 MB receive buffer (fills fast, long-distance links keep the pipe full)
 * and no Nagle delay. The buffer is set before connecting, as the TCP window needs.
 */
internal class TunedSocketFactory(
    private val delegate: SocketFactory = SocketFactory.getDefault(),
) : SocketFactory() {
    private fun tune(socket: Socket): Socket {
        try {
            socket.tcpNoDelay = true
            socket.receiveBufferSize = 1 shl 20
            socket.keepAlive = true
        } catch (e: Exception) {
            // A device may refuse a size; the default still works.
        }
        return socket
    }

    override fun createSocket(): Socket = tune(delegate.createSocket())

    override fun createSocket(host: String, port: Int): Socket = tune(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        tune(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket = tune(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        tune(delegate.createSocket(address, port, localAddress, localPort))
}
