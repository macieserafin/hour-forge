import base from './playwright.config'

/** Local stack with demo data for manual review; see tests/dev/stack.spec.ts. */
export default {
  ...base,
  testDir: './tests/dev',
  timeout: 0,
  use: { ...base.use, navigationTimeout: 60_000, actionTimeout: 30_000 },
  expect: { timeout: 20_000 },
  projects: base.projects?.filter(project => project.name === 'desktop'),
}
