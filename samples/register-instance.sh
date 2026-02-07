#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_NAME="test-instance"
CLIENT_TYPE="TEST"
USER="admin"
PASS="Kaloryfer456"

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$USER:$PASS" \
  --data "{\"instanceName\":\"$INSTANCE_NAME\",\"clientType\":\"$CLIENT_TYPE\"}" \
  "$HOST/api/v2/register-instance"