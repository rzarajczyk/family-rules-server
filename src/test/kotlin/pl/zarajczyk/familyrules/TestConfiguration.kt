package pl.zarajczyk.familyrules

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@TestConfiguration
@Profile("test")
class TestConfiguration {

    @Bean
    @Primary
    fun testFirestore(): Firestore {
        // Always use Firestore emulator for tests
        return FirestoreOptions.getDefaultInstance()
            .toBuilder()
            .setHost("localhost:8080")  // Firestore emulator host
            .setProjectId("demo-family-rules")
            .build()
            .service
    }
}
