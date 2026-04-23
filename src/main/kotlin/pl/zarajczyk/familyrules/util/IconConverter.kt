package pl.zarajczyk.familyrules.util

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Converts a Base64-encoded PNG to raw WebP bytes (lossy q=75).
 * Used at the API boundary when icons are received from the Android client.
 */
fun pngBase64ToWebP(pngBase64: String): ByteArray {
    // Use MIME decoder — tolerates whitespace/newlines that some Android clients embed.
    val pngBytes = Base64.getMimeDecoder().decode(pngBase64)
    val image = ImmutableImage.loader().fromBytes(pngBytes)
    return image.bytes(WebpWriter.DEFAULT.withQ(75))
}

/**
 * Converts raw WebP bytes to a Base64-encoded PNG string.
 * Used when serving icons back to the Android client via groups-usage-report.
 */
fun webPToPngBase64(webpBytes: ByteArray): String {
    val image: BufferedImage = ImageIO.read(ByteArrayInputStream(webpBytes))
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return Base64.getEncoder().encodeToString(out.toByteArray())
}

/**
 * Converts raw WebP bytes to a Base64-encoded WebP string.
 * Used when serving icons to the web GUI.
 */
fun webPToWebPBase64(webpBytes: ByteArray): String =
    Base64.getEncoder().encodeToString(webpBytes)
