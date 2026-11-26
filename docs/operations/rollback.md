# Rolling back

## The new version misbehaves, the migrations were backward compatible

Most releases (see [deploy](deploy.md), expand/contract): go back to the previous version, keep the database. Deploy the
previous commit with the deployment script; it printed that commit after the deployment, and
`/var/lib/hourforge-deploy/history.log` lists every deployment:

```bash
sudo /opt/hourforge/deploy/deploy.sh <previous commit SHA>
```

The script takes a backup first and switches both the images and the repository files back. By hand instead:

```bash
# in .env: BACKEND_IMAGE, FRONTEND_IMAGE, BACKUP_IMAGE = the previous tag; git checkout <previous sha>
docker compose up -d
```

`migrate` does nothing for an older version (its migrations are all applied). The old API starts on the newer schema
and does not use what the new version added.

When a deployment fails its checks, the script rolls back the images and files by itself and prints the backup
snapshot taken before it; continue below only if the previous version does not start on the new schema.

## The migration cannot be undone

When the release notes say a migration is not backward compatible, or the old version does not start on the new schema
(Hibernate stops it with "Schema validation" in `docker compose logs backend`), restore the backup taken just before the
deployment, then start the previous images:

```bash
docker compose stop backend
docker compose run --rm --entrypoint restore.sh backup <snapshot-id>   # the snapshot taken just before the deployment
# in .env: the previous image tags
docker compose up -d
```

Changes made between the deployment and the restore are lost, so decide quickly. List the snapshots with
`docker compose run --rm --entrypoint restic backup snapshots`.
