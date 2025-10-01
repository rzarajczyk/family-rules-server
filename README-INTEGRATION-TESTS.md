# Integration Tests

This document describes the integration tests for the Family Rules Server application.

## Overview

The integration tests cover the basic happy path functionality:
1. **Instance Registration**: Registering a new instance with valid credentials
2. **Report Saving**: Saving screen time and application usage reports
3. **Data Persistence**: Verifying that data is properly stored in Firestore

## Test Structure

### Main Integration Test (`IntegrationTest.kt`)

The main integration test class contains the following test methods:

- `should register instance and save report successfully`: Tests the complete happy path flow
- `should handle invalid credentials during instance registration`: Tests error handling for invalid credentials
- `should handle duplicate instance name`: Tests error handling for duplicate instance names
- `should handle invalid instance credentials during report saving`: Tests error handling for invalid instance credentials

### Test Configuration

- **Test Profile**: Uses `test` profile with Firestore emulator
- **Firestore Emulator**: Tests run against a local Firestore emulator instance
- **Test Data Cleanup**: Each test cleans up data before running

## Running the Tests

### Prerequisites

1. Install Firebase CLI:
   ```bash
   npm install -g firebase-tools
   ```

2. Make sure you have the Firebase project configuration:
   ```bash
   firebase use demo-family-rules
   ```

### Running Tests

#### Option 1: Using the Test Runner Script (Recommended)

```bash
./run-integration-tests.sh
```

This script will:
1. Start the Firestore emulator
2. Run the integration tests
3. Stop the emulator when done

#### Option 2: Manual Setup

1. Start the Firestore emulator:
   ```bash
   firebase emulators:start --only firestore --project demo-family-rules
   ```

2. In another terminal, run the tests:
   ```bash
   export FIRESTORE_EMULATOR_HOST=localhost:8080
   ./gradlew test --tests "pl.zarajczyk.familyrules.IntegrationTest"
   ```

3. Stop the emulator when done

## Test Flow

### Happy Path Test

1. **Setup**: Clear any existing test data
2. **Register Instance**: 
   - Send POST request to `/api/v2/register-instance`
   - Use admin credentials (admin:admin)
   - Verify successful response with instance ID and token
   - Verify instance is created in Firestore
3. **Save Report**:
   - Send POST request to `/api/v2/report`
   - Use instance credentials (instanceId:token)
   - Include screen time and application usage data
   - Verify successful response
4. **Verify Data**:
   - Query Firestore to verify report data was saved
   - Check screen time, application times, and timestamp

### Error Handling Tests

- **Invalid Credentials**: Tests authentication failure scenarios
- **Duplicate Instance**: Tests business logic validation
- **Invalid Instance Token**: Tests instance authentication

## Test Data

The tests use the following test data:
- **Username**: `admin`
- **Password**: `admin`
- **Instance Name**: `test-instance-{timestamp}` (unique per test run)
- **Client Type**: `TEST`
- **Screen Time**: `600` seconds
- **Applications**: `{"app1": 400, "app2": 200}` seconds

## Configuration

### Test Configuration (`application-test.yaml`)

```yaml
spring:
  profiles:
    active: test
database:
  type: firestore
firestore:
  project-id: demo-family-rules
database-initialization:
  enabled: true
  username: admin
  password: admin
server:
  port: 0  # Random port for tests
```

### Test Configuration Class (`TestConfiguration.kt`)

Provides a test-specific Firestore bean that always connects to the emulator.

## Troubleshooting

### Common Issues

1. **Firestore Emulator Not Running**: Make sure the emulator is started before running tests
2. **Port Conflicts**: Ensure port 8080 is available for the Firestore emulator
3. **Firebase CLI Not Installed**: Install Firebase CLI with `npm install -g firebase-tools`
4. **Project Configuration**: Make sure Firebase project is configured with `firebase use demo-family-rules`

### Debug Mode

To run tests with more verbose output:
```bash
./gradlew test --tests "pl.zarajczyk.familyrules.IntegrationTest" --info --debug
```

## Extending the Tests

To add new integration tests:

1. Add new test methods to `IntegrationTest.kt`
2. Follow the existing pattern of setup, execution, and verification
3. Use the `clearTestData()` method to ensure clean test environment
4. Update this README with new test descriptions
