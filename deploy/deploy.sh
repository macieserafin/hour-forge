#!/usr/bin/env bash
# Deploys one commit of main to this server and rolls the images and files back when the new version does not come up.
#   sudo /opt/hourforge/deploy/deploy.sh <40-character commit SHA>
# Steps (docs/operations/deploy.md): back up the database; switch the repository files (compose.yaml, Caddyfile,
# monitoring) to the commit and check the Caddyfile; set the three image tags in .env to the commit; pull; start; wait
# until every service is healthy, runs the new images and the site answers. A rollback restores the previous files and
# image tags, never the database: the backup snapshot printed here is for docs/operations/rollback.md.
# Settings (environment): HOURFORGE_ROOT (the repository, default: the parent of this directory), DEPLOY_STATE_DIR
# (default /var/lib/hourforge-deploy), DEPLOY_REGISTRY (default ghcr.io/macieserafin), DEPLOY_HEALTH_TIMEOUT (seconds,
# default 300), DEPLOY_POLL_SECONDS (default 5).
set -euo pipefail

readonly IMAGE_KEYS=(BACKEND_IMAGE FRONTEND_IMAGE BACKUP_IMAGE)

log() { printf '%s deploy: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() {
    log "ERROR: $*" >&2
    exit 1
}

# Git as the owner of the repository, so files it writes keep their owner when this runs as root.
repo_git() {
    if [[ $EUID -eq 0 && $repo_owner != root ]]; then
        runuser -u "$repo_owner" -- git -C "$root" "$@"
    else
        git -C "$root" "$@"
    fi
}

compose() { docker compose "${profiles[@]}" "$@"; }

env_value() { grep -E "^$1=" "$env_file" | tail -n 1 | cut -d= -f2- || true; }

# Sets BACKEND_IMAGE, FRONTEND_IMAGE and BACKUP_IMAGE to the three values given (an empty value removes the line).
# Every other line stays as it is; the file keeps its owner and mode and is replaced in one step.
write_image_keys() {
    local tmp
    tmp=$(mktemp "$deploy_dir/.env.XXXXXX") || return 1
    awk -v backend="$1" -v frontend="$2" -v backup="$3" '
        BEGIN {
            order[1] = "BACKEND_IMAGE"; order[2] = "FRONTEND_IMAGE"; order[3] = "BACKUP_IMAGE"
            value["BACKEND_IMAGE"] = backend; value["FRONTEND_IMAGE"] = frontend; value["BACKUP_IMAGE"] = backup
        }
        {
            key = $0; sub(/=.*/, "", key)
            if ((key in value) && index($0, key "=") == 1) {
                if (!(key in done) && value[key] != "") print key "=" value[key]
                done[key] = 1
                next
            }
            print
        }
        END { for (i = 1; i <= 3; i++) if (!(order[i] in done) && value[order[i]] != "") print order[i] "=" value[order[i]] }
    ' "$env_file" > "$tmp" || {
        rm -f "$tmp"
        return 1
    }
    if ! chown --reference="$env_file" "$tmp" || ! chmod --reference="$env_file" "$tmp" || ! mv "$tmp" "$env_file"; then
        rm -f "$tmp"
        return 1
    fi
}

# Containers do not see a new version of a single bind-mounted file until they are recreated: recreate the running
# services whose files differ between the two commits.
recreate_changed_mounts() {
    local changed service
    changed=$(repo_git diff --name-only "$1" "$2" -- deploy/Caddyfile deploy/monitoring) || return 1
    for service in caddy prometheus; do
        case "$service" in
        caddy) grep -qx 'deploy/Caddyfile' <<< "$changed" || continue ;;
        prometheus) grep -q '^deploy/monitoring/' <<< "$changed" || continue ;;
        esac
        is_running "$service" || continue
        log "recreating $service for its changed configuration"
        compose up -d --no-deps --force-recreate "$service" || return 1
    done
}

is_running() {
    [[ -n $(docker ps -q --filter "label=com.docker.compose.project=$project" \
        --filter "label=com.docker.compose.service=$1") ]]
}

# Every running service is healthy (or has no health check) and runs the image .env names for it.
services_ready() {
    local ids service status health image expected seen=0
    mapfile -t ids < <(compose ps -q)
    ((${#ids[@]})) || return 1
    while IFS='|' read -r service status health image; do
        [[ $status == running ]] || return 1
        [[ -z $health || $health == healthy ]] || return 1
        case "$service" in
        backend) expected=$(env_value BACKEND_IMAGE) ;;
        frontend) expected=$(env_value FRONTEND_IMAGE) ;;
        backup) expected=$(env_value BACKUP_IMAGE) ;;
        *) continue ;;
        esac
        seen=$((seen + 1))
        [[ -z $expected || $image == "$expected" ]] || return 1
    done < <(docker inspect --format \
        '{{index .Config.Labels "com.docker.compose.service"}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{.Config.Image}}' \
        "${ids[@]}")
    ((seen == 3))
}

site_answers() {
    local path insecure=()
    if [[ $domain == localhost ]]; then insecure=(--insecure); fi
    for path in / /api/config; do
        curl -fsS -o /dev/null --max-time 10 "${insecure[@]}" "https://$domain$path" || return 1
    done
}

wait_until_ready() {
    local deadline=$((SECONDS + timeout))
    until services_ready && site_answers; do
        if ((SECONDS >= deadline)); then return 1; fi
        sleep "$poll"
    done
}

# The steps that change the server; each failure returns 1, and the caller rolls back.
apply() {
    log "switching the repository files to $sha"
    repo_git checkout --quiet --detach "$sha" || return 1
    changed_files=1
    log "checking the Caddyfile"
    compose run --rm --no-deps -T caddy caddy validate --config /etc/caddy/Caddyfile || {
        log "the Caddyfile of $sha is not valid"
        return 1
    }
    write_image_keys "$DEPLOY_REGISTRY/hourforge-backend:$sha" "$DEPLOY_REGISTRY/hourforge-frontend:$sha" \
        "$DEPLOY_REGISTRY/hourforge-backup:$sha" || return 1
    changed_images=1
    log "pulling the images"
    compose pull --quiet || return 1
    log "migrating and starting"
    compose up -d || return 1
    recreate_changed_mounts "$previous_sha" "$sha" || return 1
    log "waiting up to ${timeout}s for healthy services and an answering site"
    wait_until_ready || {
        log "the new version did not become healthy in ${timeout}s"
        compose ps || true
        return 1
    }
}

roll_back() {
    log "rolling back to $previous_sha"
    if ((changed_images)); then
        write_image_keys "${previous_images[@]}" || log "could not restore the image tags in .env"
    fi
    if ((changed_files)); then
        repo_git checkout --quiet --detach "$previous_sha" || log "could not switch the files back to $previous_sha"
    fi
    if ((changed_images)); then
        compose up -d || log "could not start the previous version"
        recreate_changed_mounts "$sha" "$previous_sha" || log "could not recreate services for their configuration"
        if wait_until_ready; then
            log "the previous version is running and healthy"
        else
            log "the previous version is not healthy either; check docker compose ps and docker compose logs"
        fi
    fi
    log "The database was not restored. Backup taken before this deployment: snapshot ${snapshot:-unknown}."
    log "If the previous version fails on the new schema, follow docs/operations/rollback.md with that snapshot."
}

record() {
    printf '%s %s %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$sha" "${snapshot:-none}" >> "$state_dir/history.log"
}

main() {
    if [[ $# -ne 1 || ! $1 =~ ^[0-9a-f]{40}$ ]]; then
        die "usage: deploy.sh <40-character commit SHA of main, lowercase>"
    fi
    sha=$1
    root=${HOURFORGE_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}
    deploy_dir="$root/deploy"
    env_file="$deploy_dir/.env"
    state_dir=${DEPLOY_STATE_DIR:-/var/lib/hourforge-deploy}
    timeout=${DEPLOY_HEALTH_TIMEOUT:-300}
    poll=${DEPLOY_POLL_SECONDS:-5}
    DEPLOY_REGISTRY=${DEPLOY_REGISTRY:-ghcr.io/macieserafin}
    changed_files=0
    changed_images=0
    snapshot=""
    profiles=()

    [[ -f $env_file ]] || die "$env_file is missing"
    (umask 077 && mkdir -p "$state_dir")
    exec 9>> "$state_dir/deploy.lock"
    flock --nonblock 9 || die "another deployment is running"

    repo_owner=$(stat -c %U "$root")
    cd "$deploy_dir"
    domain=$(env_value HOURFORGE_DOMAIN)
    [[ -n $domain ]] || die "HOURFORGE_DOMAIN is not set in .env"
    project=$(env_value COMPOSE_PROJECT_NAME)
    project=${project:-hourforge}

    repo_git fetch --quiet origin main || die "git fetch failed"
    # Only main is fetched, so a commit of another branch is unknown here; both cases are refused the same way.
    if ! repo_git cat-file -e "$sha^{commit}" 2> /dev/null ||
        ! repo_git merge-base --is-ancestor "$sha" origin/main; then
        die "$sha is not on main"
    fi
    if [[ -n $(repo_git status --porcelain --untracked-files=no) ]]; then
        die "the repository on this server has local changes (git status); commit them to main or discard them first"
    fi
    previous_sha=$(repo_git rev-parse HEAD)
    previous_images=()
    local key
    for key in "${IMAGE_KEYS[@]}"; do previous_images+=("$(env_value "$key")"); done
    if is_running prometheus; then profiles=(--profile monitoring); fi

    log "deploying $sha (previous version: $previous_sha)"
    log "backing up the database"
    local backup_output
    if ! backup_output=$(compose run --rm -T backup once 2>&1); then
        printf '%s\n' "$backup_output"
        record failed
        die "the backup failed; nothing was changed"
    fi
    printf '%s\n' "$backup_output"
    snapshot=$(grep -oE 'snapshot [0-9a-f]+ done' <<< "$backup_output" | tail -n 1 | cut -d' ' -f2 || true)

    trap 'log "interrupted"; roll_back; record interrupted; exit 130' INT TERM
    if ! apply; then
        roll_back
        record failed
        die "deployment of $sha failed and was rolled back to $previous_sha"
    fi
    trap - INT TERM

    printf '%s\n' "$sha" > "$state_dir/deployed-sha.tmp" && mv "$state_dir/deployed-sha.tmp" "$state_dir/deployed-sha"
    record deployed
    log "deployed $sha (backup snapshot ${snapshot:-unknown}); previous version: $previous_sha"
    log "to go back: sudo $deploy_dir/deploy.sh $previous_sha"
}

# The whole script is read before anything runs, so switching the repository (and this file) mid-way is safe.
main "$@"
exit
