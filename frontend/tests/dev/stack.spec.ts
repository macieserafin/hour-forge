import { test } from '../support/e2e'
import { prepareEdgeCases, prepareLargeTeam, prepareNewcomer, prepareSolo, prepareTeams } from '../support/demo-sets'

/**
 * Local stack for looking at the app: managed backend, database and stand-ins, one demo set, then it waits until stopped.
 * HOURFORGE_DEV_SET=A|B|C|D|E (default C). Run with playwright.dev.config.ts; stopping the run removes the containers.
 */
test('local stack with demo data', async ({ page, browser }) => {
  test.setTimeout(0)
  const set = process.env.HOURFORGE_DEV_SET ?? 'C'
  if (set === 'A') await prepareNewcomer(page)
  else if (set === 'B') await prepareSolo(page)
  else if (set === 'D') await prepareLargeTeam(page)
  else if (set === 'E') await prepareEdgeCases(page, browser)
  else await prepareTeams(page, browser)
  console.log(`HOURFORGE_DEV_READY set=${set} url=${process.env.E2E_BASE_URL}`)
  await new Promise(() => undefined)
})
