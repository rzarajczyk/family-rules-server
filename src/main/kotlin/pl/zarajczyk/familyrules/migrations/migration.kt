package pl.zarajczyk.familyrules.migrations

// =============================================================================
// HOW TO USE THIS MIGRATION TOOL
// =============================================================================
//
// This file is a reusable framework for one-off Firestore migrations.
// There is NO migration history kept here — old migrations are deleted once
// they have been run. When a new migration is needed:
//
//   1. Implement the `migrate(firestore)` function below.
//      Import whatever helpers you need; add private helper functions at the bottom.
//
//   2. Run the migration locally:
//        export FIRESTORE_PROJECT_ID=<gcp-project-id>
//        export GOOGLE_APPLICATION_CREDENTIALS=<path-to-service-account.json>
//        ./gradlew migrate
//
//      The tool automatically backs up the entire Firestore to a local JSON file
//      (firestore-backup-<timestamp>.json) before running, so you can recover if
//      something goes wrong.
//
//   3. After confirming success, remove the migration logic from `migrate()`,
//      leaving it empty again (or replace it with the next migration).
//      Commit the clean file — do NOT accumulate old migrations here.
//
// =============================================================================

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.io.File
import java.io.FileInputStream
import java.time.Instant
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
        e.printStackTrace()
        exitProcess(1)
    }
}

// ---------------------------------------------------------------------------
// TODO: implement the current migration here.
//       Delete this function body (and any helpers below) once the migration
//       has been successfully run. Do NOT leave old migration code around.
// ---------------------------------------------------------------------------

fun migrate(firestore: Firestore) {
    // implement migration here
}

// ---------------------------------------------------------------------------
// Framework: backup + Firestore client — do not modify
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

private fun collectDocuments(firestore: Firestore, result: MutableMap<String, Map<String, Any?>>) {
    val users = firestore.collection("users").get().get().documents
    for (doc in users) {
        collectDocument(doc, result)
    }
}

private fun collectDocument(doc: DocumentSnapshot, result: MutableMap<String, Map<String, Any?>>) {
    result[doc.reference.path] = doc.data ?: emptyMap()
    for (subcollection in doc.reference.listCollections()) {
        for (child in subcollection.get().get().documents) {
            collectDocument(child, result)
        }
    }
}

private fun createFirestore(projectId: String, credentials: GoogleCredentials): Firestore =
    FirestoreOptions.newBuilder()
        .setProjectId(projectId)
        .setCredentials(credentials)
        .build()
        .service

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
