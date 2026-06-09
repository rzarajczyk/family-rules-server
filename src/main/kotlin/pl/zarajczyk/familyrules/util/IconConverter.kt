package pl.zarajczyk.familyrules.util

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.util.Base64

private val logger = LoggerFactory.getLogger("pl.zarajczyk.familyrules.util.IconConverter")

/**
 * Converts a Base64-encoded PNG to raw WebP bytes (lossy q=75).
 * Used at the API boundary when icons are received from the Android client.
 */
fun pngBase64ToWebP(pngBase64: String): ByteArray {
    // Use MIME decoder — tolerates whitespace/newlines that some Android clients embed.
    val pngBytes = Base64.getMimeDecoder().decode(pngBase64)
    // copy(TYPE_INT_ARGB) normalises unusual colour spaces (e.g. TYPE_CUSTOM) that
    // Java ImageIO produces for some PNGs — scrimage's WebpWriter rejects TYPE_CUSTOM.
    val image = ImmutableImage.loader().fromBytes(pngBytes).copy(BufferedImage.TYPE_INT_ARGB)
    return image.bytes(WebpWriter.DEFAULT.withQ(75))
}

/**
 * Converts raw WebP bytes to a Base64-encoded PNG string.
 * Used when serving icons back to mobile/native clients via groups-usage-report.
 * Returns null when bytes are missing or not decodable as WebP.
 */
fun webPToPngBase64(webpBytes: ByteArray): String? {
    return try {
        val pngBytes = ImmutableImage.loader()
            .fromBytes(webpBytes)
            .copy(BufferedImage.TYPE_INT_ARGB)
            .bytes(PngWriter.NoCompression)
        Base64.getEncoder().encodeToString(pngBytes)
    } catch (e: Exception) {
        logger.warn("Failed to convert WebP icon to PNG ({} bytes)", webpBytes.size, e)
        null
    }
}

/**
 * Converts raw WebP bytes to a Base64-encoded WebP string.
 * Used when serving icons to the web GUI.
 */
fun webPToWebPBase64(webpBytes: ByteArray): String =
    Base64.getEncoder().encodeToString(webpBytes)
