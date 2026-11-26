#!/usr/bin/env bash
# Tests deploy/deploy.sh against a real Git repository (a bare "origin" and a "server" clone) with fake docker and curl
# (deploy/tests/fakes). Needs bash, git, flock and GNU coreutils (Linux, as in CI):
#   bash deploy/tests/deploy-test.sh
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
script="$here/../deploy.sh"
fakes="$here/fakes"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0
current=""

fail() {
    echo "  FAIL: $*"
    failures=$((failures + 1))
}
assert_eq() { [[ $1 == "$2" ]] || fail "$3: expected '$2', got '$1'"; }
assert_contains() { [[ $1 == *"$2"* ]] || fail "expected output to contain '$2'"; }
assert_not_contains() { [[ $1 != *"$2"* ]] || fail "expected output not to contain '$2'"; }
calls() { cat "$FAKE_DIR/calls" 2> /dev/null || true; }
env_value() { grep -E "^$1=" "$HOURFORGE_ROOT/deploy/.env" | cut -d= -f2-; }
server_head() { git -C "$HOURFORGE_ROOT" rev-parse HEAD; }

# Commits the dev clone and pushes it to main (or to the branch given as $2); prints the SHA.
commit() {
    git -C "$dev" add -A
    git -C "$dev" -c user.name=Test -c user.email=test@example.test commit -qm "$1"
    git -C "$dev" push -q origin "HEAD:${2:-main}"
    git -C "$dev" rev-parse HEAD
}

# A fresh world per test: origin with commits C1 and C2 on main, the server checked out at C1 and running it.
setup() {
    current=$1
    echo "test: $current"
    local t="$work/$current"
    mkdir -p "$t/fake" "$t/state"
    export FAKE_DIR="$t/fake" DEPLOY_STATE_DIR="$t/state" HOURFORGE_ROOT="$t/server"
    export DEPLOY_HEALTH_TIMEOUT=2 DEPLOY_POLL_SECONDS=0.1 DEPLOY_REGISTRY=ghcr.io/example
    git init -q --bare -b main "$t/origin.git"
    git clone -q "$t/origin.git" "$t/dev" 2> /dev/null
    dev="$t/dev"
    mkdir -p "$dev/deploy/monitoring"
    printf '.env\n' > "$dev/deploy/.gitignore"
    echo 'services: {}' > "$dev/deploy/compose.yaml"
    echo 'site v1' > "$dev/deploy/Caddyfile"
    echo 'scrape v1' > "$dev/deploy/monitoring/prometheus.yml"
    C1=$(commit "first")
    echo 'site v2' > "$dev/deploy/Caddyfile"
    C2=$(commit "second")
    git clone -q "$t/origin.git" "$HOURFORGE_ROOT"
    git -C "$HOURFORGE_ROOT" reset -q --hard "$C1"
    {
        echo "# Settings of the stack"
        echo "HOURFORGE_DOMAIN=example.test"
        echo "DB_PASSWORD=do-not-print-me"
        echo "BACKEND_IMAGE=ghcr.io/example/hourforge-backend:$C1"
        echo "FRONTEND_IMAGE=ghcr.io/example/hourforge-frontend:$C1"
        echo "BACKUP_IMAGE=ghcr.io/example/hourforge-backup:$C1"
    } > "$HOURFORGE_ROOT/deploy/.env"
    chmod 600 "$HOURFORGE_ROOT/deploy/.env"
    (cd "$HOURFORGE_ROOT/deploy" && "$fakes/docker" compose up -d)
    rm -f "$FAKE_DIR/calls"
}

deploy() {
    output=$(PATH="$fakes:$PATH" "$script" "$@" 2>&1)
    status=$?
}

test_rejects_bad_arguments() {
    setup rejects-bad-arguments
    local before
    before=$(cat "$HOURFORGE_ROOT/deploy/.env")
    for argument in "" "abc" "${C2^^}" "${C2:0:39}" "${C2}0" "\$(touch $work/pwned)" "$C2 extra" "--help"; do
        deploy "$argument"
        [[ $status -ne 0 ]] || fail "accepted '$argument'"
    done
    deploy "$C2" "$C2"
    [[ $status -ne 0 ]] || fail "accepted two arguments"
    deploy
    [[ $status -ne 0 ]] || fail "accepted no argument"
    [[ ! -e $work/pwned ]] || fail "an argument was executed"
    assert_eq "$(calls)" "" "docker or curl calls"
    assert_eq "$(cat "$HOURFORGE_ROOT/deploy/.env")" "$before" ".env"
    assert_eq "$(server_head)" "$C1" "server checkout"
}

test_deploys_a_commit_of_main() {
    setup deploys-a-commit-of-main
    deploy "$C2"
    assert_eq "$status" 0 "exit status"
    assert_eq "$(server_head)" "$C2" "server checkout"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C2" "BACKEND_IMAGE"
    assert_eq "$(env_value FRONTEND_IMAGE)" "ghcr.io/example/hourforge-frontend:$C2" "FRONTEND_IMAGE"
    assert_eq "$(env_value BACKUP_IMAGE)" "ghcr.io/example/hourforge-backup:$C2" "BACKUP_IMAGE"
    assert_eq "$(env_value DB_PASSWORD)" "do-not-print-me" "other settings"
    assert_eq "$(head -n 1 "$HOURFORGE_ROOT/deploy/.env")" "# Settings of the stack" "comment line"
    assert_eq "$(stat -c %a "$HOURFORGE_ROOT/deploy/.env")" 600 ".env mode"
    assert_eq "$(cat "$DEPLOY_STATE_DIR/deployed-sha")" "$C2" "state file"
    assert_contains "$output" "snapshot 4f3e2d1c"
    assert_contains "$output" "previous version: $C1"
    assert_not_contains "$output" "do-not-print-me"
    # Backup before anything changes, the configuration is checked, then pull and start.
    local order
    order=$(calls | grep -oE 'once|caddy validate|pull|up -d$|force-recreate caddy|curl' | paste -sd ' ')
    assert_eq "$order" "once caddy validate pull up -d force-recreate caddy curl curl" "order of steps"
    assert_contains "$(calls)" "https://example.test/api/config"
}

test_refuses_a_commit_outside_main() {
    setup refuses-a-commit-outside-main
    echo 'site experiment' > "$dev/deploy/Caddyfile"
    local other
    other=$(commit "experiment" experiment)
    deploy "$other"
    [[ $status -ne 0 ]] || fail "deployed a commit outside main"
    assert_contains "$output" "not on main"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(calls)" "" "docker or curl calls"
}

test_stops_when_the_backup_fails() {
    setup stops-when-the-backup-fails
    touch "$FAKE_DIR/backup-fails"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "deployed without a backup"
    assert_contains "$output" "backup failed"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C1" "BACKEND_IMAGE"
    assert_not_contains "$(calls)" "pull"
    assert_not_contains "$(calls)" "up -d"
}

test_stops_on_an_invalid_caddyfile() {
    setup stops-on-an-invalid-caddyfile
    echo 'INVALID' > "$dev/deploy/Caddyfile"
    local broken
    broken=$(commit "broken proxy configuration")
    deploy "$broken"
    [[ $status -ne 0 ]] || fail "deployed an invalid Caddyfile"
    assert_contains "$output" "Caddyfile"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(cat "$HOURFORGE_ROOT/deploy/Caddyfile")" "site v1" "Caddyfile on the server"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C1" "BACKEND_IMAGE"
    assert_not_contains "$(calls)" "up -d"
}

test_rolls_back_an_unhealthy_version() {
    setup rolls-back-an-unhealthy-version
    echo "$C2" > "$FAKE_DIR/unhealthy"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "reported success for an unhealthy version"
    assert_contains "$output" "rolling back to $C1"
    assert_contains "$output" "4f3e2d1c"
    assert_contains "$output" "rollback.md"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(cat "$HOURFORGE_ROOT/deploy/Caddyfile")" "site v1" "Caddyfile on the server"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C1" "BACKEND_IMAGE"
    assert_eq "$(env_value BACKUP_IMAGE)" "ghcr.io/example/hourforge-backup:$C1" "BACKUP_IMAGE"
    assert_eq "$(grep -c BACKEND_IMAGE "$FAKE_DIR/running")" 1 "running images"
    grep -q "$C1" "$FAKE_DIR/running" || fail "the previous images are not running again"
    [[ ! -e $DEPLOY_STATE_DIR/deployed-sha ]] || fail "state file written for a failed deployment"
    assert_eq "$(calls | grep -c 'force-recreate caddy')" 2 "caddy recreated on deploy and on rollback"
}

test_rolls_back_when_the_site_does_not_answer() {
    setup rolls-back-when-the-site-does-not-answer
    touch "$FAKE_DIR/http-fails"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "reported success while the site does not answer"
    assert_contains "$output" "rolling back to $C1"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(env_value FRONTEND_IMAGE)" "ghcr.io/example/hourforge-frontend:$C1" "FRONTEND_IMAGE"
}

test_rolls_back_when_the_pull_fails() {
    setup rolls-back-when-the-pull-fails
    touch "$FAKE_DIR/pull-fails"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "reported success without the images"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C1" "BACKEND_IMAGE"
}

test_restores_image_settings_that_were_not_set() {
    setup restores-image-settings-that-were-not-set
    sed -i '/_IMAGE=/d' "$HOURFORGE_ROOT/deploy/.env"
    (cd "$HOURFORGE_ROOT/deploy" && "$fakes/docker" compose up -d)
    echo "$C2" > "$FAKE_DIR/unhealthy"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "reported success for an unhealthy version"
    if grep -q '_IMAGE=' "$HOURFORGE_ROOT/deploy/.env"; then fail "image settings left in .env after the rollback"; fi
    assert_eq "$(env_value DB_PASSWORD)" "do-not-print-me" "other settings"
}

test_refuses_a_second_deployment_at_the_same_time() {
    setup refuses-a-second-deployment-at-the-same-time
    flock "$DEPLOY_STATE_DIR/deploy.lock" sleep 3 &
    local holder=$!
    sleep 0.5
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "ran while another deployment holds the lock"
    assert_contains "$output" "another deployment is running"
    assert_eq "$(server_head)" "$C1" "server checkout"
    assert_eq "$(calls)" "" "docker or curl calls"
    kill "$holder" 2> /dev/null
    wait "$holder" 2> /dev/null
}

test_deploys_the_same_commit_again() {
    setup deploys-the-same-commit-again
    deploy "$C2"
    assert_eq "$status" 0 "first run"
    deploy "$C2"
    assert_eq "$status" 0 "second run"
    assert_eq "$(server_head)" "$C2" "server checkout"
    assert_eq "$(env_value BACKEND_IMAGE)" "ghcr.io/example/hourforge-backend:$C2" "BACKEND_IMAGE"
    assert_eq "$(grep -c '^BACKEND_IMAGE=' "$HOURFORGE_ROOT/deploy/.env")" 1 "one BACKEND_IMAGE line"
}

test_refuses_local_changes_on_the_server() {
    setup refuses-local-changes-on-the-server
    echo 'site edited by hand' > "$HOURFORGE_ROOT/deploy/Caddyfile"
    deploy "$C2"
    [[ $status -ne 0 ]] || fail "deployed over a local change"
    assert_contains "$output" "local changes"
    assert_eq "$(cat "$HOURFORGE_ROOT/deploy/Caddyfile")" "site edited by hand" "local change kept"
    assert_eq "$(calls)" "" "docker or curl calls"
}

test_restarts_monitoring_when_its_files_change() {
    setup restarts-monitoring-when-its-files-change
    touch "$FAKE_DIR/monitoring"
    echo 'scrape v2' > "$dev/deploy/monitoring/prometheus.yml"
    local c3
    c3=$(commit "scrape more")
    deploy "$c3"
    assert_eq "$status" 0 "exit status"
    assert_contains "$(calls)" "--profile monitoring up -d"
    assert_contains "$(calls)" "force-recreate prometheus"
}

for test in $(declare -F | awk '{print $3}' | grep '^test_'); do
    "$test"
done

if ((failures)); then
    echo "$failures assertion(s) failed"
    exit 1
fi
echo "PASS: deploy.sh"
