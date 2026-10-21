#!/bin/bash
set -euo pipefail

URL="https://gitlab.torproject.org/tpo/applications/tor-browser-build/-/raw/main/projects/tor-expert-bundle/pt_config.json"
OBFS4_FILE="app/src/main/assets/obfs4.txt"
SNOWFLAKE_FILE="app/src/main/assets/snowflake.txt"


# Fetch the JSON and capture the HTTP status code and response body
response=$(curl -s -w "%{http_code}" "$URL")

HTTP_STATUS="${response: -3}"
response="${response:0:${#response}-3}"

# Check if the HTTP status is 200
if [ "$HTTP_STATUS" -ne 200 ]; then
    echo "Error: $HTTP_STATUS. Failed to fetch $URL."
    exit 1
fi

# Parse the JSON and extract obfs4 and snowflake entries
echo "$response" | jq -r '.bridges.obfs4[]' > "$OBFS4_FILE"
echo "$response" | jq -r '.bridges.snowflake[]' > "$SNOWFLAKE_FILE"

# Check if the files were created successfully
if [ -f "$OBFS4_FILE" ] && [ -f "$SNOWFLAKE_FILE" ]; then
    echo "Bridges fetched and written to assets."
else
    echo "Error writing to files."
    exit 1
fi