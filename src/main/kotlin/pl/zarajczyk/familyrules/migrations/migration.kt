package pl.zarajczyk.familyrules.migrations

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.io.FileInputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("=== Family Rules Database Migration Tool ===")
    println()

    // Create Firestore instance
    val firestore = createFirestore()
    
    // Run migrations
    try {
        migrate(firestore)
        println()
        println("Migration completed successfully!")
        exitProcess(0)
    } catch (e: Exception) {
        println()
        println("Migration failed with error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

/**
 * Create a Firestore instance based on the environment
 */
private fun createFirestore(): Firestore {
    println("Using Production Firestore")

    val projectId = System.getenv("FIRESTORE_PROJECT_ID")
        ?: throw IllegalArgumentException("FIRESTORE_PROJECT_ID environment variable is required")

    val serviceAccountPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?: throw IllegalArgumentException("GOOGLE_APPLICATION_CREDENTIALS environment variable is required")

    println("Project ID: $projectId")
    println("Service Account: $serviceAccountPath")
    println()

    val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))

    return FirestoreOptions.newBuilder()
        .setProjectId(projectId)
        .setCredentials(credentials)
        .build()
        .service
}

fun migrate(firestore: Firestore) {
    println("  Fetching all users...")
    val users = firestore.collection("users").get().get().documents
    println("  Found ${users.size} user(s)")

    var totalInstances = 0
    var updatedInstances = 0
    var skippedInstances = 0

    users.forEach { userDoc ->
        val username = userDoc.getString("username") ?: userDoc.id
        println("  Processing user: $username")

        val instances = userDoc.reference.collection("instances").get().get().documents
        println("    Found ${instances.size} instance(s)")

        instances.forEach { instanceDoc ->
            totalInstances++
            val instanceName = instanceDoc.getString("instanceName") ?: instanceDoc.id
            val currentValue = instanceDoc.getString("knownApps")

            if (currentValue == null) {
                println("    - Instance '$instanceName': Adding knownApps={}")
                instanceDoc.reference.update("knownApps", "{}").get()
                updatedInstances++
            } else {
                println("    - Instance '$instanceName': Already has knownApps=$currentValue, skipping")
                skippedInstances++
            }
        }
    }

    println()
    println("  Migration Summary:")
    println("  - Total instances processed: $totalInstances")
    println("  - Instances updated: $updatedInstances")
    println("  - Instances skipped (already had value): $skippedInstances")
}
