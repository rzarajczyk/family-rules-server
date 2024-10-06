# Docs:
See [https://rzarajczyk.github.io/projects-family-rules/](https://rzarajczyk.github.io/projects-family-rules/)





# API flow

## Registering instance
```shell
export HOST="http://localhost:8080"
export INSTANCE_NAME="new-instance"
export CLIENT_TYPE="custom"
export USERNAME="username"
export PASSWORD="password" 

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$USERNAME:$PASSWORD" \
  --data "{\"instanceName\":\"$INSTANCE_NAME\",\"clientType\":\"$CLIENT_TYPE\"}" \
  "$HOST/api/v1/register-instance"
```

Sample response:
```json
{
  "status":"SUCCESS",
  "instanceId":"e1e41fa9-cc82-4764-9d01-686e1a1ea934",
  "token":"6eaa1c63-9576-4ee7-91c6-63477912dff7"
}
```


## Sending report
```shell
export HOST="http://localhost:8080"
export USERNAME="username"
export INSTANCE_ID="e1e41fa9-cc82-4764-9d01-686e1a1ea934"
export TOKEN="6eaa1c63-9576-4ee7-91c6-63477912dff7" 

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$USERNAME:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":5, \"applications\": {\"YouTube\": 5}}" \
  "$HOST/api/v1/report"
```