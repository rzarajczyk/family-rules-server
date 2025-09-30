# Firestore Migration Guide

This application has been migrated from PostgreSQL with JetBrains Exposed to Google Cloud Firestore.

## Local Development Setup

### Option 1: Firestore Emulator (Recommended)

1. Install Firebase CLI:
   ```bash
   npm install -g firebase-tools
   ```

2. Start the Firestore emulator:
   ```bash
   firebase emulators:start --only firestore --project demo-family-rules
   ```

3. The emulator will be available at:
   - Firestore: `localhost:8080`
   - Emulator UI: `http://localhost:4000`

4. Run the application:
   ```bash
   ./gradlew bootRun
   ```

The application will automatically detect the emulator via the `FIRESTORE_EMULATOR_HOST` environment variable.

### Option 2: Connect to Production Firestore

1. Set up a GCP project with Firestore enabled
2. Create a service account with Firestore permissions
3. Download the service account JSON key
4. Set environment variables:
   ```bash
   export FIRESTORE_PROJECT_ID=your-project-id
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
   ```

5. Run the application:
   ```bash
   ./gradlew bootRun
   ```

## Data Structure

The Firestore data is organized as follows:

```
users/{username}
├── username: string
├── passwordSha256: string
└── instances/{instanceId}
    ├── instanceId: string
    ├── instanceName: string
    ├── instanceTokenSha256: string
    ├── clientType: string
    ├── clientVersion: string
    ├── clientTimezoneOffsetSeconds: number
    ├── schedule: string (JSON)
    ├── iconData: string?
    ├── iconType: string?
    ├── deleted: boolean
    ├── createdAt: string
    ├── forcedDeviceState: string?
    ├── deviceStates/{deviceState}
    │   ├── deviceState: string
    │   ├── title: string
    │   ├── icon: string?
    │   ├── description: string?
    │   └── order: number
    └── screenTimes/{day}/apps/{app}
        ├── app: string
        ├── screenTimeSeconds: number
        └── updatedAt: string
```

## Configuration

The application uses the following configuration:

- `database.type`: Set to "firestore" (default)
- `firestore.project-id`: Your GCP project ID
- `firestore.service-account-path`: Path to service account JSON (optional if using ADC)

## Migration Notes

- All existing PostgreSQL data will be lost (as requested)
- The application now uses Firestore's NoSQL structure
- Nested collections are used for better data organization
- The emulator provides a safe testing environment
