# syntax=docker/dockerfile:1
# Backup service: pg_dump of the application database into an encrypted restic repository, on a schedule.
# The PostgreSQL client and server of the same major version as the database, so a restore test can run inside it.
FROM postgres:18-alpine@sha256:9ba53c7d41ea093baf5c1ba635ba5434c4f979880527d49bb7d9237d3193085b
# The digest fixes the base; system packages are brought up to date at every build so known fixes are not left out.
# gosu belongs to the PostgreSQL entrypoint, which this image never runs (it starts as uid 70 directly).
RUN apk upgrade --no-cache && apk add --no-cache restic && rm -f /usr/local/bin/gosu
COPY backup.sh restore.sh restore-test.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/restore-test.sh
# Mount points owned by the service user: a new named volume takes over this owner.
RUN mkdir -p /repository /scratch /status && chown 70:70 /repository /scratch /status
# The image's "postgres" user (uid 70): initdb for the restore test refuses to run as root.
USER 70:70
ENTRYPOINT ["/usr/local/bin/backup.sh"]
