package app.downly.core.engine

import java.security.MessageDigest

internal actual fun createDigester(algorithm: ChecksumAlgorithm): Digester {
    val md = MessageDigest.getInstance(algorithm.javaName)
    return object : Digester {
        override fun update(bytes: ByteArray, offset: Int, length: Int) = md.update(bytes, offset, length)
        override fun hex(): String = md.digest().joinToString("") { "%02x".format(it) }
    }
}
