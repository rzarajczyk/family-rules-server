# API flow

## Registering instance
```shell
HOST="http://localhost:8080"
INSTANCE_NAME="new-instance"
CLIENT_TYPE="custom"

curl --header "Content-Type: application/json" \
  --request POST \
  --data "{\"instanceName\":\"$INSTANCE_NAME\",\"clientType\":\"$CLIENT_TYPE\"}" \
  "$HOST/api/v1/register-instance"
```