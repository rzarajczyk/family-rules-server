package pl.zarajczyk.familyrules.migrations

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("=== Family Rules Database Migration Tool ===")
    println()

    val projectId = System.getenv("FIRESTORE_PROJECT_ID")
        ?: throw IllegalArgumentException("FIRESTORE_PROJECT_ID environment variable is required")

    val serviceAccountPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?: throw IllegalArgumentException("GOOGLE_APPLICATION_CREDENTIALS environment variable is required")

    println("Project ID:   $projectId")
    println("Service acct: $serviceAccountPath")
    println()

    val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
    val firestore = createFirestore(projectId, credentials)

    // Step 1: backup to local JSON file
    val backupFile = try {
        backupToLocalFile(firestore)
    } catch (e: Exception) {
        println()
        println("Backup failed: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }

    // Step 2: migrate
    try {
        migrate(firestore)
        println()
        println("Migration completed successfully!")
        println()
        println("Backup is kept at: ${backupFile.absolutePath}")
        println("You can delete it once you're confident the migration is correct.")
        exitProcess(0)
    } catch (e: Exception) {
        println()
        println("Migration failed: ${e.message}")
        println()
        println("Your pre-migration data was saved to: ${backupFile.absolutePath}")
        println("To restore, run:")
        println("  ./gradlew restore --args=\"${backupFile.absolutePath}\"")
        e.printStackTrace()
        exitProcess(1)
    }
}

// ---------------------------------------------------------------------------
// Local JSON backup
// ---------------------------------------------------------------------------

/**
 * Reads every document in every collection/subcollection under "users" and writes
 * it as a single JSON file on the local filesystem.
 *
 * Format:
 * ```json
 * {
 *   "path/to/document": { ...fields... },
 *   ...
 * }
 * ```
 *
 * Returns the [File] that was written.
 */
private fun backupToLocalFile(firestore: Firestore): File {
    val timestamp = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now())
    val backupFile = File("firestore-backup-$timestamp.json")

    println("=== Step 1: Local backup ===")
    println("  Writing backup to: ${backupFile.absolutePath}")

    val allDocs = mutableMapOf<String, Map<String, Any?>>()
    collectDocuments(firestore, allDocs)

    // Serialize to JSON manually — no extra library needed; values are Firestore primitives
    val json = buildString {
        appendLine("{")
        allDocs.entries.forEachIndexed { i, (path, fields) ->
            append("  ")
            append(jsonString(path))
            append(": ")
            append(jsonValue(fields))
            if (i < allDocs.size - 1) append(",")
            appendLine()
        }
        append("}")
    }

    backupFile.writeText(json)
    println("  Backed up ${allDocs.size} document(s).")
    println()

    return backupFile
}

/** Recursively collects all documents from the "users" root collection. */
private fun collectDocuments(
    firestore: Firestore,
    result: MutableMap<String, Map<String, Any?>>
) {
    val users = firestore.collection("users").get().get().documents
    for (doc in users) {
        collectDocument(doc, result)
    }
}

private fun collectDocument(
    doc: DocumentSnapshot,
    result: MutableMap<String, Map<String, Any?>>
) {
    result[doc.reference.path] = doc.data ?: emptyMap()

    // Recurse into known subcollections
    for (subcollection in doc.reference.listCollections()) {
        for (child in subcollection.get().get().documents) {
            collectDocument(child, result)
        }
    }
}

// ---------------------------------------------------------------------------
// Minimal JSON serialiser (Firestore values only — no external dependency)
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun jsonValue(value: Any?): String = when (value) {
    null -> "null"
    is Boolean -> value.toString()
    is Number -> value.toString()
    is String -> jsonString(value)
    is Map<*, *> -> {
        val entries = (value as Map<String, Any?>).entries
            .joinToString(", ") { (k, v) -> "${jsonString(k)}: ${jsonValue(v)}" }
        "{$entries}"
    }
    is List<*> -> {
        val items = value.joinToString(", ") { jsonValue(it) }
        "[$items]"
    }
    // Firestore Timestamp, GeoPoint, etc. — fall back to toString()
    else -> jsonString(value.toString())
}

private fun jsonString(s: String): String {
    val escaped = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

// ---------------------------------------------------------------------------
// Firestore client
// ---------------------------------------------------------------------------

private fun createFirestore(projectId: String, credentials: GoogleCredentials): Firestore =
    FirestoreOptions.newBuilder()
        .setProjectId(projectId)
        .setCredentials(credentials)
        .build()
        .service

// ---------------------------------------------------------------------------
// Migrations
// ---------------------------------------------------------------------------

fun migrate(firestore: Firestore) {
    cleanupOldScreenTimeEntries(firestore)
}

/**
 * Migration 3: Delete screenTime entries older than March 2026.
 *
 * Collection path: users/{username}/instances/{deviceId}/screenTimes/{date}
 * The document ID is an ISO-8601 local date string, e.g. "2024-03-15".
 * Any document whose ID parses to a date before 2026-03-01 is deleted.
 */
private fun cleanupOldScreenTimeEntries(firestore: Firestore) {
    println("=== Migration: cleanup old screenTime entries ===")

    val cutoff = LocalDate.of(2026, 3, 1)
    println("  Deleting screenTime entries with date before $cutoff")

    val users = firestore.collection("users").get().get().documents
    println("  Found ${users.size} user(s)")

    var totalEntries = 0
    var deletedEntries = 0
    var skippedEntries = 0

    users.forEach { userDoc ->
        val username = userDoc.getString("username") ?: userDoc.id
        println("  Processing user: $username")

        val instances = userDoc.reference.collection("instances").get().get().documents
        println("    Found ${instances.size} instance(s)")

        instances.forEach { instanceDoc ->
            val instanceName = instanceDoc.getString("instanceName") ?: instanceDoc.id

            val screenTimeDocs = instanceDoc.reference.collection("screenTimes").get().get().documents
            println("    - Instance '$instanceName': ${screenTimeDocs.size} screenTime entry(s)")

            screenTimeDocs.forEach { screenTimeDoc ->
                totalEntries++
                val dateId = screenTimeDoc.id
                val date = try {
                    LocalDate.parse(dateId)
                } catch (_: Exception) {
                    println("      Skipping unrecognised document ID: '$dateId'")
                    skippedEntries++
                    return@forEach
                }

                if (date < cutoff) {
                    screenTimeDoc.reference.delete().get()
                    println("      Deleted: $dateId")
                    deletedEntries++
                } else {
                    skippedEntries++
                }
            }
        }
    }

    println()
    println("  Summary:")
    println("  - Total screenTime entries: $totalEntries")
    println("  - Deleted (before $cutoff): $deletedEntries")
    println("  - Kept / skipped:           $skippedEntries")
}
