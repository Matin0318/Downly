package app.downly.core.engine

import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** Chooses the proxy for every connection from [ProxyHolder], so Settings changes apply without a restart. */
internal object DownlyProxySelector : ProxySelector() {
    override fun select(uri: URI?): MutableList<Proxy> {
        val proxy = ProxyHolder.current
        if (!proxy.enabled) return mutableListOf(Proxy.NO_PROXY)
        val type = if (proxy.kind == ProxyKind.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return mutableListOf(Proxy(type, InetSocketAddress.createUnresolved(proxy.host, proxy.port)))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
}

/** Gives the typed user name and password to SOCKS proxies (HTTP proxies are handled by OkHttp itself). */
internal object DownlyProxyAuth {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val proxy = ProxyHolder.current
                return if (requestorType == Authenticator.RequestorType.PROXY && proxy.enabled && proxy.user.isNotBlank()) {
                    PasswordAuthentication(proxy.user, proxy.password.toCharArray())
                } else {
                    null
                }
            }
        })
    }
}
