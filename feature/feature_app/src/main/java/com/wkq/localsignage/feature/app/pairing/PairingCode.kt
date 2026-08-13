package com.wkq.localsignage.feature.app.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PairingCode(
    val controlAddress: String?,
    val pairingUrl: String?,
    val expiresAt: Long,
    val qrBitmap: Bitmap?
)

object PairingCodeProvider {
    fun create(token: String, expiresAt: Long, port: Int, sizePx: Int = 512): PairingCode {
        val host = localIpv4Address()
        val address = host?.let { "http://$it:$port" }
        val url = address?.let {
            "$it/?pairingToken=${URLEncoder.encode(token, StandardCharsets.UTF_8.name())}"
        }
        return PairingCode(address, url, expiresAt, url?.let { createQrBitmap(it, sizePx) })
    }

    private fun localIpv4Address(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress &&
                    !address.isLoopbackAddress && !address.isLinkLocalAddress
                ) return@runCatching address.hostAddress
            }
        }
        null
    }.getOrNull()

    private fun createQrBitmap(content: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
