package pl.zarajczyk.familyrules.shared

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class DataRepositoryConfiguration {

    @Value("\${database.type:firestore}")
    private lateinit var databaseType: String

    @Bean
    @Primary
    fun dataRepository(
        firestoreDataRepository: FirestoreDataRepository
    ): DataRepository {
        return when (databaseType.lowercase()) {
            "firestore" -> firestoreDataRepository
            else -> throw IllegalArgumentException("Unsupported database type: $databaseType. Only 'firestore' is supported.")
        }
    }
}
