import { CandidateMatch, Job, MatchStatus } from './models';

/** "Lead · 10+ yrs", "Senior · 5–12 yrs", "5+ yrs", or "Level not stated" */
export function seniorityLabel(job?: Job | null): string {
  if (!job) return '';
  const min = job.minExperienceYears;
  const max = job.maxExperienceYears;
  const years = min == null && max == null ? null
    : max == null ? `${min}+ yrs`
    : min === max || min == null ? `${max} yrs` : `${min}–${max} yrs`;
  if (job.seniority && years) return `${job.seniority} · ${years}`;
  return job.seniority ?? years ?? 'Level not stated';
}

/** "Skills 90% · Experience 100% · Location 75%", leaving out unknown parts */
export function scoreParts(m: CandidateMatch): string {
  const part = (label: string, v?: number | null) => (v == null ? null : `${label} ${Math.round(v * 100)}%`);
  return [part('Skills', m.skillScore), part('Experience', m.experienceScore), part('Location', m.locationScore)]
    .filter(Boolean).join(' · ');
}

/** What HR calls each stage; MATCHED is a match nobody has reviewed yet */
export const STATUS_LABELS: Record<MatchStatus, string> = {
  PENDING: 'New', MATCHED: 'New', SHORTLISTED: 'Shortlisted', REJECTED: 'Rejected', HIRED: 'Hired',
};

export function statusChip(s: MatchStatus): string {
  return s === 'SHORTLISTED' || s === 'HIRED' ? 'chip chip-green' : s === 'REJECTED' ? 'chip chip-red' : 'chip chip-blue';
}
