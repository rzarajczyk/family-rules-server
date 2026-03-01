package pl.zarajczyk.familyrules.migrations

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
    migrateKnownApps(firestore)
    migrateAppGroupsEmbedMembersAndStates(firestore)
}

/**
 * Migration 1: Ensure all device instances have a knownApps field.
 */
private fun migrateKnownApps(firestore: Firestore) {
    println("=== Migration: knownApps ===")
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
                println("    - Instance '$instanceName': Already has knownApps, skipping")
                skippedInstances++
            }
        }
    }

    println()
    println("  Summary:")
    println("  - Total instances: $totalInstances")
    println("  - Updated:         $updatedInstances")
    println("  - Skipped:         $skippedInstances")
}

/**
 * Migration 2: Embed `members` and `groupStates` subcollections directly into each appGroup document
 * as Firestore native maps, then delete the subcollection documents.
 *
 * Old schema:
 *   users/{uid}/appGroups/{gid}/members/{deviceId}    → { "apps": "[\"pkg1\",\"pkg2\"]" }
 *   users/{uid}/appGroups/{gid}/groupStates/{stateId} → { "id", "name", "deviceStates": "{...}" }
 *
 * New schema (fields on the appGroup document itself):
 *   members: { deviceId: ["pkg1","pkg2"], ... }
 *   states:  { stateId: { name, deviceStates: { deviceId: { deviceState, extra } } }, ... }
 */
@Suppress("UNCHECKED_CAST")
private fun migrateAppGroupsEmbedMembersAndStates(firestore: Firestore) {
    println("=== Migration: embed members + groupStates into appGroup documents ===")

    val users = firestore.collection("users").get().get().documents
    println("  Found ${users.size} user(s)")

    var totalGroups = 0
    var migratedGroups = 0
    var skippedGroups = 0

    users.forEach { userDoc ->
        val username = userDoc.getString("username") ?: userDoc.id
        println("  Processing user: $username")

        val appGroups = userDoc.reference.collection("appGroups").get().get().documents
        println("    Found ${appGroups.size} app group(s)")

        appGroups.forEach { groupDoc ->
            totalGroups++
            val groupName = groupDoc.getString("name") ?: groupDoc.id

            // Already migrated: the field is a Map (native), not a String (old JSON blob)
            val existingMembers = groupDoc.get("members")
            val existingStates = groupDoc.get("states")
            val alreadyMigrated = existingMembers is Map<*, *> || existingStates is Map<*, *>

            // ---- Read members subcollection ----
            val memberDocs = groupDoc.reference.collection("members").get().get().documents
            val membersMap = mutableMapOf<String, List<String>>()

            memberDocs.forEach { memberDoc ->
                val deviceId = memberDoc.id
                val appsJson = memberDoc.getString("apps") ?: "[]"
                // Parse JSON array like ["pkg1","pkg2"]
                val apps = appsJson
                    .removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
                membersMap[deviceId] = apps
            }

            // ---- Read groupStates subcollection ----
            val stateDocs = groupDoc.reference.collection("groupStates").get().get().documents
            val statesMap = mutableMapOf<String, Map<String, Any?>>()

            stateDocs.forEach { stateDoc ->
                val stateId = stateDoc.getString("id") ?: stateDoc.id
                val name = stateDoc.getString("name") ?: ""
                val deviceStatesJson = stateDoc.getString("deviceStates") ?: "{}"
                statesMap[stateId] = mapOf(
                    "name" to name,
                    "deviceStates" to parseDeviceStatesJson(deviceStatesJson),
                )
            }

            if (alreadyMigrated && memberDocs.isEmpty() && stateDocs.isEmpty()) {
                println("    - Group '$groupName': already migrated, skipping")
                skippedGroups++
                return@forEach
            }

            // Write embedded maps onto the appGroup document
            groupDoc.reference.update(
                mapOf<String, Any>(
                    "members" to membersMap,
                    "states" to statesMap,
                )
            ).get()

            // Delete subcollection documents (Firestore does not cascade-delete them)
            memberDocs.forEach { it.reference.delete().get() }
            stateDocs.forEach { it.reference.delete().get() }

            println("    - Group '$groupName': migrated (${memberDocs.size} member doc(s), ${stateDocs.size} state doc(s))")
            migratedGroups++
        }
    }

    println()
    println("  Summary:")
    println("  - Total app groups: $totalGroups")
    println("  - Migrated:         $migratedGroups")
    println("  - Skipped:          $skippedGroups")
}

/**
 * Parses the JSON deviceStates string stored by the old schema.
 * Format: {"uuid":{"deviceState":"BLOCKED","extra":null},...}
 * Uses Gson which is a transitive dependency of google-cloud-firestore.
 */
private fun parseDeviceStatesJson(json: String): Map<String, Any?> = try {
    @Suppress("UNCHECKED_CAST")
    val raw = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any?>
    raw.mapValues { (_, v) ->
        when (v) {
            null -> null
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val m = v as Map<String, Any?>
                mapOf(
                    "deviceState" to (m["deviceState"] as? String ?: "ACTIVE"),
                    "extra" to m["extra"],
                )
            }
            else -> null
        }
    }
} catch (_: Exception) {
    emptyMap()
}
