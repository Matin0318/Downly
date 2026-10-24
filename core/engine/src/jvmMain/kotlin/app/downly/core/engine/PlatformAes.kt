package app.downly.core.engine

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun aesCbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
}
