import { execFile, spawn, type ChildProcess } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import { createWriteStream } from 'node:fs'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const exec = promisify(execFile)
const backendPath = fileURLToPath(new URL('../../../backend', import.meta.url))

export default async function globalSetup() {
  const container = `hour-forge-e2e-${process.pid}`
  const backendPort = process.env.E2E_BACKEND_PORT ?? '18086'
  let backend: ChildProcess | undefined
  let started = false
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
    if (process.platform === 'win32') {
      await exec('cmd.exe', ['/d', '/s', '/c', 'mvnw.cmd -q -DskipTests package'], { cwd: backendPath, maxBuffer: 8 * 1024 * 1024 })
    } else {
      await exec('./mvnw', ['-q', '-DskipTests', 'package'], { cwd: backendPath, maxBuffer: 8 * 1024 * 1024 })
    }
    backend = spawn('java', ['-jar', 'target/hour-forge-0.0.1-SNAPSHOT.jar', `--server.port=${backendPort}`], {
      cwd: backendPath,
      env: { ...process.env, DB_URL: `jdbc:postgresql://127.0.0.1:${databasePort}/hourforge_e2e`,
        DB_USERNAME: 'hourforge_e2e', DB_PASSWORD: 'e2e-only-password', SEED_USERS: 'true',
        SESSION_COOKIE_SECURE: 'false', FRONTEND_URL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5186' },
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
        if (result.stdout.trim() === '2' && (await fetch(`http://127.0.0.1:${backendPort}/api/auth/csrf`)).ok) break
      } catch { /* Wait for migrations and seed completion, not a fixed startup delay. */ }
      if (Date.now() > deadline) throw new Error(`E2E environment was not ready within 60 seconds. See ${directory}/backend.log`)
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    process.env.HOUR_FORGE_E2E_CONTAINER = container
    return cleanup
  } catch (error) {
    await cleanup()
    throw error
  }
}
