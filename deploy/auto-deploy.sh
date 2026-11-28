#!/usr/bin/env bash
# Deploys the newest commit of main once its CI run has passed and its images are published. A systemd timer runs this
# on the server (deploy/systemd); the server asks GitHub, so GitHub has no access to the server.
#   sudo /opt/hourforge/deploy/auto-deploy.sh
# Nothing is deployed unless the switch file exists (default /etc/hourforge/auto-deploy.enabled). Every commit is tried
# once: a commit that deploy.sh has already recorded in history.log (deployed, failed or interrupted) is left alone, so
# a failed deployment is not repeated and a rollback made by hand stays.
# Settings (environment): HOURFORGE_ROOT, DEPLOY_STATE_DIR and DEPLOY_REGISTRY as in deploy.sh, DEPLOY_METRICS_DIR
# (default /var/lib/hourforge-metrics, read by node-exporter), DEPLOY_GITHUB_REPOSITORY (default
# macieserafin/hour-forge), AUTO_DEPLOY_SWITCH, AUTO_DEPLOY_SCRIPT (default deploy.sh next to this file).
# Needs git, curl, jq and docker. Details: docs/operations/deploy.md.
set -euo pipefail

log() { printf '%s auto-deploy: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

# Git as the owner of the repository, so files it writes keep their owner when this runs as root.
repo_git() {
    if [[ $EUID -eq 0 ]]; then
        local owner
        owner=$(stat -c %U "$root")
        if [[ $owner != root ]]; then
            runuser -u "$owner" -- git -C "$root" "$@"
            return
        fi
    fi
    git -C "$root" "$@"
}

read_state() { cat "$state_dir/$1" 2> /dev/null || true; }

write_state() { printf '%s\n' "$2" > "$state_dir/$1.tmp" && mv "$state_dir/$1.tmp" "$state_dir/$1"; }

# Whether deploy.sh has already run this commit to an end, whatever the result.
in_history() { grep -qE "^[^ ]+ [a-z]+ $1 " "$state_dir/history.log" 2> /dev/null; }

# Prints "green", "red" or "waiting" for the CI run of a push of this commit to main; fails when GitHub does not answer.
ci_result() {
    local answer
    answer=$(curl -fsS --max-time 20 -H 'Accept: application/vnd.github+json' -H 'User-Agent: hourforge-auto-deploy' \
        "https://api.github.com/repos/$repository/actions/workflows/ci.yml/runs?branch=main&event=push&head_sha=$1") ||
        return 1
    jq -er --arg sha "$1" '
        if (.workflow_runs | type) != "array" then error("no list of runs") else . end
        | [.workflow_runs[] | select(.head_sha == $sha and .event == "push" and .head_branch == "main")]
        | if length == 0 or .[0].status != "completed" then "waiting"
          elif .[0].conclusion == "success" then "green"
          else "red" end' <<< "$answer" 2> /dev/null
}

images_published() {
    local name
    for name in backend frontend backup; do
        docker manifest inspect "$registry/hourforge-$name:$1" > /dev/null 2>&1 || return 1
    done
}

# Everything the alerts need, rewritten after every run, so a deployment made by hand shows up too.
# shellcheck disable=SC2329
write_metrics() {
    local deployed last_status failed=0 pending=0 since waiting_sha tmp
    deployed=$(read_state deployed-sha)
    last_status=$(tail -n 1 "$state_dir/history.log" 2> /dev/null | awk '{print $2}' || true)
    if [[ -n $last_status && $last_status != deployed ]]; then failed=1; fi
    if [[ -e $switch ]]; then
        read -r waiting_sha since <<< "$(read_state pending)" || true
        if [[ -n ${waiting_sha:-} && $waiting_sha == "$sha" ]] && ! in_history "$sha"; then pending=$since; fi
    fi
    (umask 022 && mkdir -p "$metrics_dir") || return 1
    tmp=$(mktemp "$metrics_dir/.deploy.prom.XXXXXX") || return 1
    {
        if [[ -n $deployed ]]; then
            echo "# HELP hourforge_deploy_last_success_timestamp_seconds Time of the last successful deployment."
            echo "# TYPE hourforge_deploy_last_success_timestamp_seconds gauge"
            echo "hourforge_deploy_last_success_timestamp_seconds $(date -r "$state_dir/deployed-sha" +%s)"
            echo "# HELP hourforge_deploy_info The commit of main that is deployed."
            echo "# TYPE hourforge_deploy_info gauge"
            echo "hourforge_deploy_info{sha=\"$deployed\"} 1"
        fi
        echo "# HELP hourforge_deploy_failed 1 when the last deployment failed and was rolled back."
        echo "# TYPE hourforge_deploy_failed gauge"
        echo "hourforge_deploy_failed $failed"
        echo "# HELP hourforge_deploy_pending_since_timestamp_seconds Since when a green commit waits to be deployed, or 0."
        echo "# TYPE hourforge_deploy_pending_since_timestamp_seconds gauge"
        echo "hourforge_deploy_pending_since_timestamp_seconds $pending"
    } > "$tmp"
    chmod 644 "$tmp" && mv "$tmp" "$metrics_dir/deploy.prom"
}

# Runs on exit (trap in main), whatever happened before.
# shellcheck disable=SC2329
finish() {
    local status=$?
    write_metrics || log "could not write the metrics to $metrics_dir"
    exit "$status"
}

deploy_newest() {
    [[ -e $switch ]] || return 0
    if ! repo_git fetch --quiet origin main; then
        log "git fetch failed; trying again on the next run"
        return 0
    fi
    sha=$(repo_git rev-parse origin/main)
    if [[ $sha == "$(read_state deployed-sha)" || $sha == "$(read_state ci-failed-sha)" ]] || in_history "$sha"; then
        return 0
    fi

    local waiting_sha since result
    read -r waiting_sha since <<< "$(read_state pending)" || true
    # A commit listed as pending has passed CI already; GitHub is not asked about it again.
    if [[ ${waiting_sha:-} != "$sha" ]]; then
        if ! result=$(ci_result "$sha"); then
            log "no usable answer from GitHub about $sha; trying again on the next run"
            return 0
        fi
        case "$result" in
        waiting)
            log "waiting for CI of $sha"
            return 0
            ;;
        red)
            write_state ci-failed-sha "$sha"
            log "CI of $sha did not pass; it will not be deployed"
            return 0
            ;;
        esac
        write_state pending "$sha $(date +%s)"
    fi

    if ! images_published "$sha"; then
        log "CI of $sha passed, but not all of its images can be found in $registry yet"
        return 0
    fi

    log "deploying $sha"
    if "$deploy_script" "$sha"; then
        log "deployed $sha"
        return 0
    fi
    if in_history "$sha"; then
        log "ERROR: the deployment of $sha failed; it will not be tried again (docs/operations/deploy.md)"
    else
        log "ERROR: deploy.sh did not start the deployment of $sha; trying again on the next run"
    fi
    return 1
}

main() {
    root=${HOURFORGE_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}
    state_dir=${DEPLOY_STATE_DIR:-/var/lib/hourforge-deploy}
    metrics_dir=${DEPLOY_METRICS_DIR:-/var/lib/hourforge-metrics}
    registry=${DEPLOY_REGISTRY:-ghcr.io/macieserafin}
    repository=${DEPLOY_GITHUB_REPOSITORY:-macieserafin/hour-forge}
    switch=${AUTO_DEPLOY_SWITCH:-/etc/hourforge/auto-deploy.enabled}
    deploy_script=${AUTO_DEPLOY_SCRIPT:-$root/deploy/deploy.sh}
    sha=""

    (umask 077 && mkdir -p "$state_dir")
    trap finish EXIT
    deploy_newest
}

# The whole script is read before anything runs, so a deployment that replaces this file mid-way is safe.
main "$@"
exit
