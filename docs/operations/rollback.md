# Rolling back

## The new version misbehaves, the migrations were backward compatible

Most releases (see [deploy](deploy.md), expand/contract): go back to the previous image, keep the database.

```bash
# in .env: BACKEND_IMAGE, FRONTEND_IMAGE, BACKUP_IMAGE = the previous tag
docker compose up -d backend frontend
```

`migrate` does nothing for an older version (its migrations are all applied). The old API starts on the newer schema
and does not use what the new version added.

## The migration cannot be undone

When the release notes say a migration is not backward compatible, or the old version does not start on the new schema
(Hibernate stops it with "Schema validation" in `docker compose logs backend`), restore the backup taken just before the
deployment, then start the previous images:

```bash
docker compose stop backend
docker compose run --rm --entrypoint restore.sh backup <snapshot-id>   # the snapshot from step 2 of the deployment
# in .env: the previous image tags
docker compose up -d
```

Changes made between the deployment and the restore are lost, so decide quickly. List the snapshots with
`docker compose run --rm --entrypoint restic backup snapshots`.
