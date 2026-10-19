import type { ReportBucket, ReportContext, ReportEntry, ReportFilters, ReportMember, ReportProject } from '../../src/lib/api'
import { entry, json, project, team, user } from './api'

export const reportFilters: ReportFilters = { from: '2026-10-19', to: '2026-10-25', userId: '', projectId: '', entryType: '' }
export const reportContext: ReportContext = { teamId: team.id, teamName: team.name, timezone: team.timezone,
  filter: { from: reportFilters.from, to: reportFilters.to, userIds: [], projectIds: [], entryType: null } }
export const reportMember: ReportMember = { userId: 'worker', displayName: 'Anna Kowalska', role: 'MEMBER',
  joinedAt: team.createdAt, leftAt: team.createdAt }
export const reportProject: ReportProject = { projectId: project.id, name: project.name, description: null,
  createdAt: project.createdAt, archivedAt: team.createdAt }
export const reportEntry: ReportEntry = { ...entry, userId: reportMember.userId, displayName: reportMember.displayName,
  projectArchivedAt: reportProject.archivedAt, pausedAt: null, pausedDurationMicros: 0, createdAt: team.createdAt, updatedAt: team.createdAt }
export const reportBucket: ReportBucket = { workDate: null, userId: null, displayName: null, projectId: null,
  projectName: null, projectArchivedAt: null, durationSeconds: 900, entryCount: 1 }

export const reportPage = <T,>(content: T[], page = 0, hasNext = false) => ({ context: reportContext, data: { content, page, size: 20, hasNext } })

export function reportHandlers(target = team) {
  const base = `GET /api/teams/${target.id}/reports`
  return {
    [`${base}/summary`]: (url: URL) => json({ context: { ...reportContext, teamId: target.id, teamName: target.name,
      filter: { ...reportContext.filter, from: url.searchParams.get('from'), to: url.searchParams.get('to') } }, totals: { durationSeconds: 900, entryCount: 1 } }),
    [`${base}/breakdown`]: (url: URL) => json({ ...reportPage([{ ...reportBucket,
      ...(url.searchParams.get('groupBy') === 'MEMBER' ? { userId: reportMember.userId, displayName: reportMember.displayName }
        : { projectId: reportProject.projectId, projectName: reportProject.name, projectArchivedAt: reportProject.archivedAt }) }]), groupBy: url.searchParams.get('groupBy') }),
    [`${base}/entries`]: () => json(reportPage([reportEntry])),
    [`${base}/members`]: () => json(reportPage([{ ...reportMember }, { ...reportMember, userId: user.id, displayName: user.displayName, role: 'OWNER', leftAt: null }])),
    [`${base}/projects`]: () => json(reportPage([reportProject])),
  }
}
