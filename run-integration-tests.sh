#!/bin/bash

# Script to run integration tests with Firestore emulator

set -e

echo "Starting Firestore emulator..."
firebase emulators:start --only firestore --project demo-family-rules &
EMULATOR_PID=$!

# Wait for emulator to start
echo "Waiting for Firestore emulator to start..."
sleep 10

# Set environment variable for tests
export FIRESTORE_EMULATOR_HOST=localhost:8080

echo "Running integration tests..."
./gradlew test --tests "pl.zarajczyk.familyrules.IntegrationTest" --info

# Clean up
echo "Stopping Firestore emulator..."
kill $EMULATOR_PID

echo "Integration tests completed!"
