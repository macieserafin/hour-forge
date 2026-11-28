#!/usr/bin/env bash
# Fake deploy.sh for auto-deploy-test.sh: records the call and leaves the state files the real script would leave.
#   deploy-mode "fail"     the deployment fails and is rolled back (recorded in history.log)
#   deploy-mode "refuse"   the script stops before it starts anything (nothing recorded)
set -euo pipefail
echo "deploy $*" >> "$FAKE_DIR/calls"
case "$(cat "$FAKE_DIR/deploy-mode" 2> /dev/null || true)" in
refuse) exit 1 ;;
fail)
    echo "2026-01-03T00:00:00Z failed $1 dddd" >> "$DEPLOY_STATE_DIR/history.log"
    exit 1
    ;;
esac
echo "$1" > "$DEPLOY_STATE_DIR/deployed-sha"
echo "2026-01-03T00:00:00Z deployed $1 eeee" >> "$DEPLOY_STATE_DIR/history.log"
