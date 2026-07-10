package mx.ipn.escom.bluetoothvideoplayer

import java.security.MessageDigest

object BinaryTransferUtils {

    const val DEFAULT_TEST_SIZE = 256 * 1024
    const val MAX_TEST_SIZE = 480 * 1024

    fun createTestPayload(sizeBytes: Int): ByteArray {
        require(sizeBytes in 1..MAX_TEST_SIZE) {
            "Tamaño de prueba fuera de rango."
        }

        return ByteArray(sizeBytes) { index ->
            ((index * 31 + 17) and 0xFF).toByte()
        }
    }

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(data)

        return digest.joinToString(separator = "") { byte ->
            "%02X".format(byte)
        }
    }
}
