package pl.zarajczyk.familyrules

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.NoCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@TestConfiguration
@Profile("test")
class TestConfiguration {

    @Value("\${firestore.emulator-host:localhost:8080}")
    private lateinit var emulatorHost: String

    @Bean
    @Primary
    fun testFirestore(): Firestore {
        // Always use Firestore emulator for tests
        return FirestoreOptions.getDefaultInstance()
            .toBuilder()
            .setHost(emulatorHost)  // Firestore emulator host (injected)
            .setProjectId("test-project")
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service
    }
}
