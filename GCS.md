# GCS Setup For SEND_LOGS

This project stores `SEND_LOGS` files in Google Cloud Storage.

Important:

- the generic server-to-client command mechanism does **not** depend on GCS
- only the `SEND_LOGS_V1` server-side processor uses GCS
- your app runs in region `europe-west1`, so the bucket should also be in `europe-west1`

This guide assumes very little prior GCP knowledge and explains exactly what to click in Cloud Console.

## What You Are Setting Up

You need 4 things:

1. a GCS bucket for uploaded logs
2. permission for the Cloud Run service to read/write that bucket
3. automatic cleanup of old logs
4. Cloud Run environment variables telling the app which bucket to use

Suggested bucket name used below:

```text
family-rules-server-logs
```

If that name is already taken globally, use something unique, for example:

```text
family-rules-server-logs-rafal
```

## Step 1: Open the Right Project

1. Open [https://console.cloud.google.com](https://console.cloud.google.com)
2. In the top bar, click the current project selector
3. Choose the Google Cloud project where your `family-rules-server` Cloud Run service is deployed

## Step 2: Create the Bucket

1. In the left menu, click `Cloud Storage`
2. Click `Buckets`
3. Click `Create`

Fill the form like this:

### Bucket name

Enter:

```text
family-rules-server-logs
```

If Google says the name is unavailable, choose another globally unique name.

### Where to store your data

1. For `Choose where to store your data`, select `Region`
2. For region, select:

```text
europe-west1
```

### Choose a storage class

Leave the default `Standard`

### Choose how to control access to objects

Use the default settings unless your organization enforces something else.

### Choose how to protect object data

Leave defaults unless you already have a policy requiring something specific.

4. Click `Create`

## Step 3: Find the Cloud Run Service Account

You need to give the running server permission to access the bucket.

1. In the left menu, click `Cloud Run`
2. Click your server service
3. Click `Edit & Deploy New Revision`
4. Scroll to the `Security` section
5. Find the `Service account` field
6. Copy or note the service account email

It usually looks something like:

```text
123456789-compute@developer.gserviceaccount.com
```

or a custom service account.

You can click `Cancel` after checking it if you are not ready to deploy yet.

## Step 4: Grant Bucket Access to the Cloud Run Service Account

1. Go back to `Cloud Storage` -> `Buckets`
2. Click the bucket you created
3. Open the `Permissions` tab
4. Click `Grant access`

Fill the form like this:

### New principals

Paste the Cloud Run service account email from Step 3

### Assign roles

Click `Select a role`, then choose:

```text
Cloud Storage -> Storage Object User
```

This is the recommended minimum useful role for read/write object access.

5. Click `Save`

## Step 5: Add Automatic Cleanup (TTL)

You said logs should not stay forever. In GCS this is done with a lifecycle rule.

Recommended rule:

- delete objects older than `7` days

To configure it:

1. Open `Cloud Storage` -> `Buckets`
2. Click your bucket
3. Open the `Lifecycle` tab
4. Click `Add a rule`

Configure the rule:

### Action

Choose:

```text
Delete object
```

### Condition

Choose:

```text
Age
```

Enter:

```text
7
```

This means objects older than 7 days will be automatically deleted.

5. Save the rule

## Step 6: Configure Cloud Run Environment Variables

Now tell the app which bucket to use.

1. Open `Cloud Run`
2. Click your server service
3. Click `Edit & Deploy New Revision`
4. Open the `Variables & Secrets` section
5. Under `Environment variables`, add these two variables:

```text
LOGS_STORAGE_BUCKET=family-rules-server-logs
LOGS_STORAGE_PREFIX=device-command-logs
```

If you used a different bucket name, use that exact name in `LOGS_STORAGE_BUCKET`.

6. Click `Deploy`

## Step 7: Verify the Setup

After deployment:

1. Open the app GUI
2. Use `Request logs` for a device
3. Wait until the logs are received
4. Open `Cloud Storage` -> your bucket
5. You should see objects under a path like:

```text
device-command-logs/<deviceId>/<commandId>/2026-05-01.txt
```

There should be one `.txt` file per exported day.

## What the App Stores in GCS

For `SEND_LOGS`:

- one object per day
- plain text file contents
- each file includes:
  - app logs for that day
  - system events for that day

Firestore now stores only metadata about those files, not the full log text.

## Troubleshooting

### Bucket exists, but logs are not appearing

Check:

1. Cloud Run service has `LOGS_STORAGE_BUCKET` set correctly
2. Cloud Run service account has `Storage Object User` on the bucket
3. Bucket region is `europe-west1`
4. Cloud Run revision was redeployed after adding env vars

### Request logs fails after upload

Most likely causes:

1. wrong bucket name in environment variables
2. missing IAM permission on the bucket
3. bucket was created in a different project than the Cloud Run service

### I want longer or shorter retention

Edit the lifecycle rule and change `Age` from `7` to whatever you want.

## Summary

Minimum required settings are:

- bucket in `europe-west1`
- Cloud Run service account has `Storage Object User`
- environment variables:

```text
LOGS_STORAGE_BUCKET=<your-bucket-name>
LOGS_STORAGE_PREFIX=device-command-logs
```

- lifecycle rule deleting objects older than `7` days
