import { avatarUrl, teamLogoUrl, type Team, type User } from '../lib/api'
import { ProjectDot } from './ProjectDot'

export function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toLocaleUpperCase('pl-PL') || 'U'
}

/** The profile picture, or the initials when there is none. */
export function Avatar({ user, small }: { user: Pick<User, 'id' | 'displayName' | 'avatarUpdatedAt'>; small?: boolean }) {
  const className = `avatar${small ? ' avatar-small' : ''}`
  return user.avatarUpdatedAt
    ? <img className={`${className} avatar-picture`} src={avatarUrl(user.id, user.avatarUpdatedAt)} alt={user.displayName} />
    : <span className={className} aria-label={small ? user.displayName : undefined}>{initials(user.displayName)}</span>
}

/** The team logo next to its name, or its colour dot when it has no logo. */
export function TeamMark({ team }: { team: Pick<Team, 'id' | 'color' | 'logoUpdatedAt'> }) {
  return team.logoUpdatedAt
    ? <img className="team-logo" src={teamLogoUrl(team.id, team.logoUpdatedAt)} alt="" />
    : <ProjectDot color={team.color} />
}
