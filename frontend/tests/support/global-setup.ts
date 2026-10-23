import { execFile, spawn, type ChildProcess } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import { createWriteStream } from 'node:fs'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomBytes } from 'node:crypto'
import { startFakeAtlassian, type FakeAtlassian } from './fake-atlassian'

const exec = promisify(execFile)
const backendPath = fileURLToPath(new URL('../../../backend', import.meta.url))

export default async function globalSetup() {
  const container = `hour-forge-e2e-${process.pid}`
  const mailContainer = `hour-forge-e2e-mail-${process.pid}`
  const backendPort = process.env.E2E_BACKEND_PORT ?? '18086'
  let backend: ChildProcess | undefined
  let atlassian: FakeAtlassian | undefined
  let started = false
  let mailStarted = false
  const directory = await mkdtemp(join(tmpdir(), 'hour-forge-e2e-'))
  const output = createWriteStream(join(directory, 'backend.log'))
  const cleanup = async () => {
    if (backend?.pid && backend.exitCode === null) {
      const exited = new Promise<void>(resolve => backend!.once('close', () => resolve()))
      backend.kill('SIGTERM')
      const stopped = await Promise.race([exited.then(() => true), new Promise<false>(resolve => {
        const timer = setTimeout(() => resolve(false), 5000)
        timer.unref()
      })])
      if (!stopped) { backend.kill('SIGKILL'); await exited }
    }
    output.end()
    await atlassian?.close()
    if (mailStarted) await exec('docker', ['rm', '-f', mailContainer])
    if (started) await exec('docker', ['rm', '-f', container])
  }
  try {
    console.log(`E2E backend log: ${join(directory, 'backend.log')}`)
    await exec('docker', ['run', '--rm', '-d', '--name', container, '--tmpfs', '/var/lib/postgresql',
      '-e', 'POSTGRES_USER=hourforge_e2e', '-e', 'POSTGRES_PASSWORD=e2e-only-password', '-e', 'POSTGRES_DB=hourforge_e2e',
      '-p', '127.0.0.1::5432', 'postgres:18-alpine'])
    started = true
    const { stdout } = await exec('docker', ['port', container, '5432'])
    const databasePort = stdout.trim().split(':').at(-1)!
    // The backend requires a mail server; Mailpit keeps the messages so scenarios can follow the real links.
    await exec('docker', ['run', '--rm', '-d', '--name', mailContainer, '-p', '127.0.0.1::1025', '-p', '127.0.0.1::8025', 'axllent/mailpit'])
    mailStarted = true
    const mapped = async (port: string) => (await exec('docker', ['port', mailContainer, port])).stdout.trim().split(/\r?\n/)[0]!.split(':').at(-1)!
    const smtpPort = await mapped('1025')
    const mailApiPort = await mapped('8025')
    if (process.platform === 'win32') {
      await exec('cmd.exe', ['/d', '/s', '/c', 'mvnw.cmd -q -DskipTests package'], { cwd: backendPath, maxBuffer: 8 * 1024 * 1024 })
    } else {
      await exec('./mvnw', ['-q', '-DskipTests', 'package'], { cwd: backendPath, maxBuffer: 8 * 1024 * 1024 })
    }
    // Jira is switched on against a local stand-in for Atlassian, so scenarios can follow the whole consent flow.
    const redirectUri = `http://127.0.0.1:${backendPort}/api/integrations/jira/callback`
    atlassian = await startFakeAtlassian(redirectUri)
    backend = spawn('java', ['-jar', 'target/hour-forge-0.0.1-SNAPSHOT.jar', `--server.port=${backendPort}`], {
      cwd: backendPath,
      env: { ...process.env, DB_URL: `jdbc:postgresql://127.0.0.1:${databasePort}/hourforge_e2e`,
        DB_USERNAME: 'hourforge_e2e', DB_PASSWORD: 'e2e-only-password',
        SESSION_COOKIE_SECURE: 'false', SPRING_MAIL_HOST: '127.0.0.1', SPRING_MAIL_PORT: smtpPort, MAIL_STARTTLS: 'false', FRONTEND_URL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5186',
        // Every scenario registers its accounts from 127.0.0.1; limits are verified by backend tests.
        APP_RATELIMIT_REGISTRATION_MAX: '100000', APP_RATELIMIT_TEAMCREATION_MAX: '100000',
        APP_RATELIMIT_INVITATIONCREATION_MAX: '100000', APP_RATELIMIT_PROJECTCREATION_MAX: '100000',
        // Background jobs have their own tests; a browser run must not be disturbed by them.
        JOBS_ENABLED: 'false',
        JIRA_INTEGRATION_ENABLED: 'true', JIRA_CLIENT_ID: 'e2e-client', JIRA_CLIENT_SECRET: 'e2e-client-secret-only',
        JIRA_REDIRECT_URI: redirectUri, JIRA_TOKEN_ENCRYPTION_KEY: randomBytes(32).toString('base64'), TWO_FACTOR_ENCRYPTION_KEY: randomBytes(32).toString('base64'),
        // The seeded owner also runs the instance, so the operator panel can be exercised.
        OPERATOR_EMAILS: 'owner@hourforge-e2e.test',
        APP_JIRA_AUTHBASEURL: atlassian.url, APP_JIRA_APIBASEURL: atlassian.url },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    backend.stdout!.pipe(output, { end: false })
    backend.stderr!.pipe(output, { end: false })
    let spawnError: Error | undefined
    backend.on('error', error => { spawnError = error })
    const deadline = Date.now() + 60000
    while (true) {
      if (spawnError) throw spawnError
      if (backend.exitCode !== null) throw new Error(`E2E backend exited: ${backend.exitCode}. See ${directory}/backend.log`)
      try {
        const result = await exec('docker', ['exec', container, 'psql', '-U', 'hourforge_e2e', '-d', 'hourforge_e2e', '-tAc', 'SELECT count(*) FROM user_credentials'])
        if (result.stdout.trim() === '0' && (await fetch(`http://127.0.0.1:${backendPort}/api/auth/csrf`)).ok) break
      } catch { /* Wait for migrations and HTTP readiness. */ }
      if (Date.now() > deadline) throw new Error(`E2E environment was not ready within 60 seconds. See ${directory}/backend.log`)
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    process.env.HOUR_FORGE_E2E_CONTAINER = container
    process.env.HOUR_FORGE_E2E_MAIL_API = `http://127.0.0.1:${mailApiPort}`
    return cleanup
  } catch (error) {
    await cleanup()
    throw error
  }
}
