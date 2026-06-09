package pl.zarajczyk.familyrules

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import pl.zarajczyk.familyrules.util.pngBase64ToWebP
import pl.zarajczyk.familyrules.util.webPToPngBase64
import java.util.Base64

class IconConverterUnitSpec : FunSpec({
    test("webPToPngBase64 round-trips WebP produced from PNG") {
        // 1x1 red PNG
        val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val webpBytes = pngBase64ToWebP(pngBase64)

        val pngResult = webPToPngBase64(webpBytes)

        pngResult.shouldNotBeNull()
        Base64.getDecoder().decode(pngResult).size shouldBe Base64.getDecoder().decode(pngBase64).size
    }

    test("webPToPngBase64 returns null for invalid bytes") {
        webPToPngBase64(byteArrayOf(0x00, 0x01, 0x02)) shouldBe null
    }
})
