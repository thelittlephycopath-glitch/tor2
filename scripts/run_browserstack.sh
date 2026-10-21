#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'
BOLD='\033[1m'
TAB="$(printf '\t')"

function quit {
    echo -e "${RED}Task failed. $1 ${NC}"
    exit 1
}

function showtitle {
    echo -e "\n${BOLD}$1 ${NC}"
}

function save_report {
    local session_id="$1"
    local response="$2"
    local dev_name
    dev_name=$(echo "$response" | jq -r '.device // "Device"')
    local report_file="reports/${BUILD_ID}-${session_id}.xml"

    echo "Fetching report for session $session_id ($dev_name) and saving to $report_file"
    curl -s -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/$BUILD_ID/sessions/$session_id/report" > "$report_file"

    if [[ -s "$report_file" ]]; then
        python3 - "$report_file" "$dev_name" << 'EOF'
import sys
import xml.etree.ElementTree as ET

file_path = sys.argv[1]
device_name = sys.argv[2]

try:
    tree = ET.parse(file_path)
    root = tree.getroot()

    if root.tag == 'testsuite':
        suites = [root]
    elif root.tag == 'testsuites':
        suites = root.findall('testsuite')
    else:
        suites = root.findall('.//testsuite')

    for suite in suites:
        if 'name' in suite.attrib and not suite.attrib['name'].startswith(f"[{device_name}]"):
            suite.attrib['name'] = f"[{device_name}] {suite.attrib['name']}"

    for tc in root.findall('.//testcase'):
        if 'classname' in tc.attrib and not tc.attrib['classname'].startswith(f"[{device_name}]"):
            tc.attrib['classname'] = f"[{device_name}] {tc.attrib['classname']}"
        elif 'classname' not in tc.attrib:
            tc.attrib['classname'] = f"[{device_name}]"

    tree.write(file_path, encoding='utf-8', xml_declaration=True)
except Exception as e:
    print(f"Warning: Failed to tag XML with device name: {e}", file=sys.stderr)
EOF
    fi
}


mkdir -p "reports"

if [[ -e secrets.properties ]]; then
  source secrets.properties
else
  echo "Missing secrets.properties file. Make sure you provide \$BROWSER_STACK_USER_NAME and \$BROWSER_STACK_ACCESS_KEY by other means."
fi

if [[ -z $BROWSER_STACK_USER_NAME || -z $BROWSER_STACK_ACCESS_KEY ]]; then
  quit "Missing environment variables BROWSER_STACK_USER_NAME and BROWSER_STACK_ACCESS_KEY."
fi

showtitle "Building APKs"
./gradlew -q clean assembleDebug assembleDebugAndroidTest

showtitle "Uploading Debug APK"
APP_URL=$(curl -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" \
-X POST "https://api-cloud.browserstack.com/app-automate/espresso/v2/app" \
-F "file=@app/build/outputs/apk/debug/app-universal-debug.apk" | jq -r .app_url)
echo "$APP_URL"

showtitle "Uploading Test APK"
TEST_APP_URL=$(curl -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" \
-X POST "https://api-cloud.browserstack.com/app-automate/espresso/v2/test-suite" \
-F "file=@app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" | jq -r .test_suite_url)
echo "$TEST_APP_URL"

showtitle "Submit Espresso test"
BUILD_ID=$(curl -s -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" \
-X POST "https://api-cloud.browserstack.com/app-automate/espresso/v2/build" \
-d "{\"project\" : \"TorVPN\", \"app\": \"${APP_URL}\", \"testSuite\": \"${TEST_APP_URL}\", \"devices\": [\"Samsung Galaxy S20-10.0\", \"Google Pixel 7-13.0\"]}" \
-H "Content-Type: application/json" | jq -r .build_id)
echo "BUILD_ID: $BUILD_ID"

if [[ -z $BUILD_ID ]]; then
    quit "Failed to fetch build ID"
fi

showtitle "Get SESSION_ID"
SESSION_IDS=""
for (( session_attempt=1; session_attempt<=10; session_attempt++ )); do
    SESSION_IDS=$(curl -s -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" \
      -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/$BUILD_ID" | jq -r '.devices[].sessions[].id')
    echo "SESSION_ID (attempt $session_attempt/10): $SESSION_IDS"

    if [[ -n $SESSION_IDS ]]; then
        break
    fi

    if (( session_attempt < 10 )); then
        echo "No session IDs yet; retrying in 10s..."
        sleep 10
    fi
done

if [[ -z $SESSION_IDS ]]; then
    quit "Failed to fetch session IDs after 10 attempts"
fi

showtitle "Wait until Espresso tests finish"
SEEN_TESTS=""
TEST_FAILED=0
ACTIVE_SESSIONS="$SESSION_IDS"

# Loop for a maximum of 180 attempts / 30 minutes
for (( attempt=1; attempt<=180; attempt++ )); do
    [[ -z "$ACTIVE_SESSIONS" ]] && break

    STILL_ACTIVE=""

    # Loop through each SESSION_ID
    for SESSION_ID in $ACTIVE_SESSIONS; do
        # Make the curl request and capture the response
        response=$(curl -s -u "$BROWSER_STACK_USER_NAME:$BROWSER_STACK_ACCESS_KEY" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/$BUILD_ID/sessions/$SESSION_ID")

        if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
            echo "Error fetching build result for session $SESSION_ID: $response"
            continue
        elif echo "$response" | jq -e '.status' > /dev/null 2>&1; then
            # Stream completed test cases and print status updates
            while IFS="$TAB" read -r test_id status dev class log_url name; do
                [[ -z "$test_id" ]] && continue

                # Log each completed test case only once
                if [[ " $SEEN_TESTS " != *" $test_id "* ]]; then
                    SEEN_TESTS="$SEEN_TESTS $test_id"
                    STATUS_UPPER=$(echo "$status" | tr '[:lower:]' '[:upper:]')

                    # Append log URL if available for failed/errored tests
                    log_suffix=""
                    if [[ -n "$log_url" && "$log_url" != "null" ]]; then
                        log_suffix=" (Log: ${log_url})"
                    fi

                    # Print formatted status line with color indicators
                    case "$status" in
                        passed)
                            echo -e "  ${GREEN}✓ [${STATUS_UPPER}]${NC} [${dev}] ${class} > ${name}"
                            ;;
                        failed|error|timedout)
                            echo -e "  ${RED}✗ [${STATUS_UPPER}]${NC} [${dev}] ${class} > ${name}${log_suffix}"
                            ;;
                        *)
                            echo -e "  ${YELLOW}- [${STATUS_UPPER}]${NC} [${dev}] ${class} > ${name}"
                            ;;
                    esac
                fi
            # Parse completed test cases from BrowserStack JSON as tab-separated values (TSV)
            done < <(echo "$response" | jq -r '
                .device as $dev | .testcases.data[]? | .class as $class | .testcases[]? |
                select(.status == "passed" or .status == "failed" or .status == "error" or .status == "timedout" or .status == "skipped") |
                [(.id // ($dev + "#" + $class + "#" + .name)), .status, ($dev // "Device"), $class, (.instrumentation_log // "null"), .name] |
                @tsv
            ' 2>/dev/null)

            STATUS=$(echo "$response" | jq -r '.status // .browserstack_status')
            case "$STATUS" in
                running)
                    echo "$response" | jq -r '
                      .device as $dev |
                      .testcases as $tc |
                      if $tc and ($tc.count // 0) > 0 then
                        ($tc.status.passed // 0) as $p |
                        ($tc.status.failed // 0) as $f |
                        ($tc.status.skipped // 0) as $s |
                        ($tc.status.error // 0) as $err |
                        ($tc.status.timedout // 0) as $to |
                        ($tc.status.running // 0) as $r |
                        ($tc.status.queued // 0) as $q |
                        ($tc.count // 0) as $c |
                        ($p + $f + $s + $err + $to) as $done |
                        "[\($dev // "Device")] Progress: \($done)/\($c) tests completed (Passed: \($p), Failed: \($f), Running: \($r), Queued: \($q))"
                      else
                        "[\($dev // "Device")] Status: running..."
                      end
                    ' 2>/dev/null
                    STILL_ACTIVE="$STILL_ACTIVE $SESSION_ID"
                    ;;
                success|passed)
                    echo -e "${GREEN}SUCCESS${NC}"
                    echo "$response" | jq -r '
                      .device as $dev |
                      .testcases as $tc |
                      if $tc then
                        "[\($dev // "Device")] Total: \($tc.count // 0) | Passed: \($tc.status.passed // 0) | Failed: \($tc.status.failed // 0) | Skipped: \($tc.status.skipped // 0)"
                      else
                        "[\($dev // "Device")] All tests completed successfully."
                      end
                    ' 2>/dev/null
                    save_report "$SESSION_ID" "$response"
                    ;;
                failed)
                    echo -e "${RED}FAILED${NC}"
                    echo "$response" | jq -r '
                      .device as $dev |
                      .testcases as $tc |
                      if $tc then
                        "[\($dev // "Device")] Total: \($tc.count // 0) | Passed: \($tc.status.passed // 0) | Failed: \($tc.status.failed // 0) | Skipped: \($tc.status.skipped // 0)"
                      else
                        "[\($dev // "Device")] Test run failed."
                      end
                    ' 2>/dev/null
                    ((TEST_FAILED++))
                    save_report "$SESSION_ID" "$response"
                    ;;
                *)
                    echo "DONE ($STATUS)"
                    echo "Result: $response"
                    save_report "$SESSION_ID" "$response"
                    ;;
            esac
        else
            echo "Unknown response for session $SESSION_ID: $response"
        fi
    done

    ACTIVE_SESSIONS="$STILL_ACTIVE"
    if [[ -n "$ACTIVE_SESSIONS" ]]; then
        sleep 10
    fi
done

if [[ $TEST_FAILED -gt 0 ]]; then
    quit "At least one test on $TEST_FAILED device(s) failed."
fi
