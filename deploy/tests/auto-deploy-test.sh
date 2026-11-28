#!/usr/bin/env bash
# Tests deploy/auto-deploy.sh against a real Git repository (a bare "origin" and a "server" clone) with fake curl,
# docker and deploy.sh (deploy/tests/fakes). Needs bash, git and jq:
#   bash deploy/tests/auto-deploy-test.sh
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
script="$here/../auto-deploy.sh"
fakes="$here/fakes"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

fail() {
    echo "  FAIL: $*"
    failures=$((failures + 1))
}
assert_eq() { [[ $1 == "$2" ]] || fail "$3: expected '$2', got '$1'"; }
assert_contains() { [[ $1 == *"$2"* ]] || fail "expected '$1' to contain '$2'"; }
calls() { cat "$FAKE_DIR/calls" 2> /dev/null || true; }
count() { calls | grep -c -- "$1" || true; }
deploys() { count '^deploy '; }
queries() { count 'api.github.com'; }
metric() { grep -E "^$1(\{| )" "$DEPLOY_METRICS_DIR/deploy.prom" 2> /dev/null | awk '{print $NF}'; }

# Commits the dev clone and pushes it to main; prints the SHA.
commit() {
    echo "$1" > "$dev/file"
    git -C "$dev" add -A
    git -C "$dev" -c user.name=Test -c user.email=test@example.test commit -qm "$1"
    git -C "$dev" push -q origin HEAD:main
    git -C "$dev" rev-parse HEAD
}

# What the CI workflow of a commit says: status and conclusion as the GitHub API returns them.
ci() {
    local conclusion=null
    if [[ -n ${3:-} ]]; then conclusion="\"$3\""; fi
    printf '{"workflow_runs":[{"head_sha":"%s","head_branch":"main","event":"push","status":"%s","conclusion":%s}]}\n' \
        "$1" "$2" "$conclusion" > "$FAKE_DIR/api.json"
}

# A fresh world per test: C1 is deployed, C2 is a newer commit of main, automatic deployment is switched on.
setup() {
    echo "test: $1"
    local t="$work/$1"
    mkdir -p "$t/fake" "$t/state"
    export FAKE_DIR="$t/fake" DEPLOY_STATE_DIR="$t/state" HOURFORGE_ROOT="$t/server"
    export DEPLOY_METRICS_DIR="$t/metrics" DEPLOY_REGISTRY=ghcr.io/example DEPLOY_GITHUB_REPOSITORY=example/hour-forge
    export AUTO_DEPLOY_SWITCH="$t/enabled" AUTO_DEPLOY_SCRIPT="$fakes/deploy.sh"
    touch "$AUTO_DEPLOY_SWITCH"
    git init -q --bare -b main "$t/origin.git"
    git clone -q "$t/origin.git" "$t/dev" 2> /dev/null
    dev="$t/dev"
    C1=$(commit "first")
    git clone -q "$t/origin.git" "$HOURFORGE_ROOT"
    git -C "$HOURFORGE_ROOT" checkout -q --detach "$C1"
    echo "$C1" > "$DEPLOY_STATE_DIR/deployed-sha"
    echo "2026-01-01T00:00:00Z deployed $C1 aaaa" > "$DEPLOY_STATE_DIR/history.log"
    C2=$(commit "second")
}

run() {
    output=$(PATH="$fakes:$PATH" bash "$script" 2>&1)
    status=$?
}

test_does_nothing_when_switched_off() {
    setup switched-off
    ci "$C2" completed success
    rm "$AUTO_DEPLOY_SWITCH"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(calls)" "" "calls"
    assert_eq "$(metric hourforge_deploy_pending_since_timestamp_seconds)" 0 "pending"
}

test_does_nothing_without_a_new_commit() {
    setup no-new-commit
    git -C "$dev" push -qf origin "$C1:main"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(calls)" "" "calls"
}

test_waits_while_ci_runs() {
    setup ci-running
    ci "$C2" in_progress
    run
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(deploys)" 0 "deployments"
    assert_eq "$(queries)" 2 "asks again on the next run"
    assert_eq "$(metric hourforge_deploy_pending_since_timestamp_seconds)" 0 "pending"
}

test_waits_before_ci_has_started() {
    setup ci-not-started
    echo '{"workflow_runs":[]}' > "$FAKE_DIR/api.json"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(deploys)" 0 "deployments"
}

test_ignores_a_run_of_another_commit() {
    setup ci-of-another-commit
    ci "$C1" completed success
    run
    assert_eq "$(deploys)" 0 "deployments"
}

test_gives_up_on_a_red_commit() {
    setup ci-red
    ci "$C2" completed failure
    run
    assert_eq "$status" 0 "exit status"
    ci "$C2" completed success
    run
    assert_eq "$(deploys)" 0 "deployments"
    assert_eq "$(queries)" 1 "API queries"
}

test_waits_for_the_images() {
    setup image-missing
    ci "$C2" completed success
    echo "hourforge-backup:$C2" > "$FAKE_DIR/missing-image"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(deploys)" 0 "deployments"
    local since
    since=$(metric hourforge_deploy_pending_since_timestamp_seconds)
    [[ ${since:-0} -gt 0 ]] || fail "a green commit that waits is not reported"
    sleep 1
    run
    assert_eq "$(metric hourforge_deploy_pending_since_timestamp_seconds)" "$since" "waiting since"
    assert_eq "$(queries)" 1 "a green result is remembered"
    rm "$FAKE_DIR/missing-image"
    run
    assert_eq "$(deploys)" 1 "deployments once the images exist"
    assert_eq "$(metric hourforge_deploy_pending_since_timestamp_seconds)" 0 "pending after the deployment"
}

test_deploys_a_green_commit_once() {
    setup green
    ci "$C2" completed success
    run
    assert_eq "$status" 0 "exit status"
    assert_contains "$(calls)" "deploy $C2"
    assert_contains "$(calls)" "manifest inspect ghcr.io/example/hourforge-backend:$C2"
    assert_contains "$(calls)" "manifest inspect ghcr.io/example/hourforge-frontend:$C2"
    assert_contains "$(calls)" "manifest inspect ghcr.io/example/hourforge-backup:$C2"
    assert_contains "$(calls)" "repos/example/hour-forge/actions/workflows/ci.yml/runs"
    assert_eq "$(metric hourforge_deploy_failed)" 0 "failed"
    assert_contains "$(cat "$DEPLOY_METRICS_DIR/deploy.prom")" "hourforge_deploy_info{sha=\"$C2\"} 1"
    [[ $(metric hourforge_deploy_last_success_timestamp_seconds) -gt 0 ]] || fail "no time of the last success"
    : > "$FAKE_DIR/calls"
    run
    assert_eq "$(calls)" "" "calls of the next run"
}

test_deploys_only_the_newest_commit() {
    setup newest-only
    local c3
    c3=$(commit "third")
    ci "$c3" completed success
    run
    assert_eq "$(deploys)" 1 "deployments"
    assert_contains "$(calls)" "deploy $c3"
}

test_does_not_retry_a_failed_deployment() {
    setup failed
    ci "$C2" completed success
    echo fail > "$FAKE_DIR/deploy-mode"
    run
    [[ $status -ne 0 ]] || fail "reported success for a failed deployment"
    assert_eq "$(metric hourforge_deploy_failed)" 1 "failed"
    run
    run
    assert_eq "$status" 0 "later runs"
    assert_eq "$(deploys)" 1 "deployments"
    assert_eq "$(metric hourforge_deploy_failed)" 1 "failed stays until a deployment succeeds"
    rm "$FAKE_DIR/deploy-mode"
    local c3
    c3=$(commit "third")
    ci "$c3" completed success
    run
    assert_eq "$(deploys)" 2 "a newer commit is deployed"
    assert_eq "$(metric hourforge_deploy_failed)" 0 "failed after a success"
}

test_tries_again_when_nothing_was_started() {
    setup refused
    ci "$C2" completed success
    echo refuse > "$FAKE_DIR/deploy-mode"
    run
    [[ $status -ne 0 ]] || fail "reported success for a refused deployment"
    [[ $(metric hourforge_deploy_pending_since_timestamp_seconds) -gt 0 ]] || fail "the waiting commit is not reported"
    rm "$FAKE_DIR/deploy-mode"
    run
    assert_eq "$status" 0 "second run"
    assert_eq "$(deploys)" 2 "deployments"
}

test_keeps_a_manual_rollback() {
    setup manual-rollback
    # C2 was deployed, then the owner went back to C1 by hand: the history knows both.
    {
        echo "2026-01-02T00:00:00Z deployed $C2 bbbb"
        echo "2026-01-02T01:00:00Z deployed $C1 cccc"
    } >> "$DEPLOY_STATE_DIR/history.log"
    ci "$C2" completed success
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(calls)" "" "calls"
}

test_does_not_deploy_without_the_api() {
    setup api-down
    touch "$FAKE_DIR/api-down"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(deploys)" 0 "deployments"
    assert_contains "$output" "GitHub"
    rm "$FAKE_DIR/api-down"
    ci "$C2" completed success
    run
    assert_eq "$(deploys)" 1 "deployments once the API answers"
}

test_does_not_deploy_on_a_broken_answer() {
    setup api-garbage
    echo '<html>rate limited</html>' > "$FAKE_DIR/api.json"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(deploys)" 0 "deployments"
}

test_does_not_deploy_when_git_cannot_fetch() {
    setup fetch-fails
    git -C "$HOURFORGE_ROOT" remote set-url origin "$work/nowhere.git"
    run
    assert_eq "$status" 0 "exit status"
    assert_eq "$(calls)" "" "calls"
}

for test in $(declare -F | awk '{print $3}' | grep '^test_'); do
    "$test"
done

if ((failures)); then
    echo "$failures assertion(s) failed"
    exit 1
fi
echo "PASS: auto-deploy.sh"
