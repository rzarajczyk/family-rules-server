package pl.zarajczyk.familyrules.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import pl.zarajczyk.familyrules.adapter.firestore.FirestoreDevicesRepository
import pl.zarajczyk.familyrules.domain.DevicesRepository

@Configuration
class DataRepositoryConfiguration {

    @Value("\${database.type:firestore}")
    private lateinit var databaseType: String

    @Bean
    @Primary
    fun dataRepository(
        firestoreDataRepository: FirestoreDevicesRepository
    ): DevicesRepository {
        return when (databaseType.lowercase()) {
            "firestore" -> firestoreDataRepository
            else -> throw IllegalArgumentException("Unsupported database type: $databaseType. Only 'firestore' is supported.")
        }
    }
}