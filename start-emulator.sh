#!/bin/bash

# Start Firestore emulator for local development
echo "Starting Firestore emulator..."

# Check if firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "Firebase CLI not found. Installing..."
    npm install -g firebase-tools
fi

# Start the emulator
firebase emulators:start --only firestore --project demo-family-rules

echo "Emulator started!"
echo "Firestore emulator: http://localhost:8080"
echo "Emulator UI: http://localhost:4000"
echo ""
echo "To run the application:"
echo "export FIRESTORE_EMULATOR_HOST=localhost:8080"
echo "./gradlew bootRun"
