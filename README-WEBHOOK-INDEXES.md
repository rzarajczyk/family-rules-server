# Firestore Indexes for Webhook Feature

## Required Composite Index

The webhook notification system requires a composite index on the `users` collection to efficiently query users with recent activity and webhooks enabled.

### Index Configuration

**Collection**: `users`

**Fields**:
- `webhookEnabled` (Ascending)
- `lastActivity` (Descending)

### How to Create the Index

#### Option 1: Using firestore.indexes.json (Recommended)

The index is already defined in `firestore.indexes.json`. Deploy it using:

```bash
firebase deploy --only firestore:indexes
```

#### Option 2: Using Firebase Console

If the application fails with an index error, the error message will contain a direct link to create the index in Firebase Console. Click the link and it will automatically configure the correct index.

Example error message:
```
The query requires an index. You can create it here: https://console.firebase.google.com/...
```

#### Option 3: Using Firebase CLI

Create the index manually:

```bash
firebase firestore:indexes
```

Then add the following to the indexes section:

```json
{
  "collectionGroup": "users",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "webhookEnabled", "order": "ASCENDING" },
    { "fieldPath": "lastActivity", "order": "DESCENDING" }
  ]
}
```

### Why This Index Is Required

The `WebhookScheduler` queries the `users` collection to find all users who:
1. Have `webhookEnabled` set to `true` (equality filter)
2. Have `lastActivity` greater than a recent timestamp (inequality filter)

Firestore requires a composite index for queries that combine equality and inequality filters on different fields.

### Verification

After creating the index, you can verify it in the Firebase Console under:
**Firestore Database → Indexes**

The index status should show as "Enabled" (green).

### For Local Development (Firestore Emulator)

The Firestore emulator automatically creates indexes on-the-fly, so this index is not required for local development. However, you may see warning messages in the logs about missing indexes.
