// ============================================================
// Heatmap — types + générateur de données mock
// ============================================================

import type { ScriptStatus, TreeNode } from '@/fixtures/testTree'

export interface GitlabCommit {
  hash: string        // short hash (7 chars)
  message: string     // message du commit
  author: string      // auteur
  date: string        // ISO datetime
  filesChanged: number
}

export interface HeatmapDay {
  date: string // YYYY-MM-DD
  status: ScriptStatus | null // statut dominant du jour
  durationMs: number // durée moyenne du jour (0 si pas d'exécution)
  execCount: number // nombre d'exécutions ce jour-là
  hasAtsCommit: boolean // commit GitLab .ats ce jour-là
  gitlabCommits: GitlabCommit[] // commits GitLab du jour
}

// ── Génère ~180 jours de données mock pour un script ────────

const DAY_MS = 24 * 3600 * 1000

/** Pseudo-random déterministe à partir d'une seed string */
function seededRand(seed: string): () => number {
  let h = 0
  for (let i = 0; i < seed.length; i++) {
    h = (Math.imul(31, h) + seed.charCodeAt(i)) | 0
  }
  return () => {
    h ^= h << 13
    h ^= h >> 17
    h ^= h << 5
    return ((h >>> 0) / 4294967296)
  }
}

const COMMIT_MESSAGES = [
  'fix: update login page selector after redesign',
  'feat: add product search validation step',
  'fix: increase timeout for checkout flow',
  'refactor: extract shared login helper',
  'fix: handle new cookie consent banner',
  'feat: add mobile viewport test coverage',
  'fix: update API endpoint URL after migration',
  'chore: clean up deprecated selectors',
  'fix: stabilize flaky search autocomplete test',
  'feat: add error screenshot on failure',
  'fix: adjust wait strategy for dynamic content',
  'refactor: simplify assertion chain',
  'fix: correct xpath for updated nav menu',
  'feat: add retry logic for network errors',
  'fix: update expected text after i18n change',
]

const COMMIT_AUTHORS = ['Raph', 'Marie L.', 'Thomas D.', 'CI Bot']

function pick<T>(items: readonly T[], rand: () => number): T {
  const item = items[Math.floor(rand() * items.length)]
  if (item === undefined) throw new Error('pick() appelé sur un tableau vide')
  return item
}

function generateHash(rand: () => number): string {
  const chars = '0123456789abcdef'
  let h = ''
  for (let i = 0; i < 7; i++) h += chars[Math.floor(rand() * 16)]
  return h
}

export function generateHeatmapData(
  scriptId: string,
  opts: {
    lastAtsModif?: string
    flaky?: boolean
    baseFailRate?: number
  } = {},
): HeatmapDay[] {
  const rand = seededRand(scriptId)
  const days: HeatmapDay[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const failRate = opts.baseFailRate ?? 0.15
  const isFlaky = opts.flaky ?? false

  // Dates de commits .ats (distribués aléatoirement, ~1 toutes les 2-3 semaines)
  const commitDates = new Set<string>()
  if (opts.lastAtsModif) {
    commitDates.add(opts.lastAtsModif.slice(0, 10))
  }
  for (let i = 0; i < 8; i++) {
    const offset = Math.floor(rand() * 180)
    const d = new Date(today.getTime() - offset * DAY_MS)
    commitDates.add(d.toISOString().slice(0, 10))
  }

  // Dates de commits GitLab (plus fréquents, ~2-3 par semaine)
  const gitlabDates = new Set<string>()
  for (let i = 0; i < 50; i++) {
    const offset = Math.floor(rand() * 180)
    const d = new Date(today.getTime() - offset * DAY_MS)
    gitlabDates.add(d.toISOString().slice(0, 10))
  }
  // Les jours .ats sont aussi des jours gitlab
  for (const d of commitDates) gitlabDates.add(d)

  for (let i = 179; i >= 0; i--) {
    const d = new Date(today.getTime() - i * DAY_MS)
    const dateStr = d.toISOString().slice(0, 10)
    const dow = d.getDay() // 0=dim, 6=sam

    // Week-end : 50% de chance de skip (tests tournent aussi le WE)
    const isWeekend = dow === 0 || dow === 6
    const skipChance = isWeekend ? 0.5 : 0.15

    // Commits GitLab du jour (même sans exécution)
    const commits: GitlabCommit[] = []
    if (gitlabDates.has(dateStr)) {
      const count = rand() < 0.6 ? 1 : rand() < 0.85 ? 2 : 3
      for (let c = 0; c < count; c++) {
        commits.push({
          hash: generateHash(rand),
          message: pick(COMMIT_MESSAGES, rand),
          author: pick(COMMIT_AUTHORS, rand),
          date: `${dateStr}T${String(8 + Math.floor(rand() * 10)).padStart(2, '0')}:${String(Math.floor(rand() * 60)).padStart(2, '0')}:00`,
          filesChanged: 1 + Math.floor(rand() * 5),
        })
      }
    }

    if (rand() < skipChance) {
      days.push({
        date: dateStr,
        status: null,
        durationMs: 0,
        execCount: 0,
        hasAtsCommit: commitDates.has(dateStr),
        gitlabCommits: commits,
      })
      continue
    }

    // Nombre d'exécutions (1-4, rarement plus)
    const execCount = rand() < 0.6 ? 1 : rand() < 0.7 ? 2 : rand() < 0.9 ? 3 : 4

    // Statut dominant
    let status: ScriptStatus
    const r = rand()
    if (isFlaky) {
      status = r < 0.45 ? 'PASS' : r < 0.75 ? 'FAIL' : r < 0.9 ? 'PASS' : 'ERROR'
    } else {
      status = r < (1 - failRate) ? 'PASS' : r < (1 - failRate * 0.15) ? 'FAIL' : 'ERROR'
    }

    // Durée moyenne (3-15s, avec variance)
    const baseDuration = 3000 + rand() * 12000
    const durationMs = Math.round(baseDuration * (status === 'ERROR' ? 0.3 : 1))

    days.push({
      date: dateStr,
      status,
      durationMs,
      execCount,
      hasAtsCommit: commitDates.has(dateStr),
      gitlabCommits: commits,
    })
  }

  return days
}

// ── Helper : collecte les données heatmap pour un node ──────

export function getHeatmapForNode(node: TreeNode): HeatmapDay[] {
  if (node.type === 'script') {
    return generateHeatmapData(node.id, {
      lastAtsModif: node.lastAtsModif,
      flaky: node.flaky,
      baseFailRate: node.confidence === 'red' ? 0.6 : node.confidence === 'orange' ? 0.3 : 0.08,
    })
  }

  // Module : agrège tous les scripts enfants par jour
  const scripts: TreeNode[] = []
  function collect(n: TreeNode) {
    if (n.type === 'script') scripts.push(n)
    n.children.forEach(collect)
  }
  collect(node)

  const allDays = scripts.map((s) =>
    generateHeatmapData(s.id, {
      lastAtsModif: s.lastAtsModif,
      flaky: s.flaky,
      baseFailRate: s.confidence === 'red' ? 0.6 : s.confidence === 'orange' ? 0.3 : 0.08,
    }),
  )

  // Merge par date
  const byDate = new Map<string, HeatmapDay>()
  for (const scriptDays of allDays) {
    for (const day of scriptDays) {
      const existing = byDate.get(day.date)
      if (!existing) {
        byDate.set(day.date, { ...day, gitlabCommits: [...day.gitlabCommits] })
      } else {
        existing.execCount += day.execCount
        existing.hasAtsCommit = existing.hasAtsCommit || day.hasAtsCommit
        existing.gitlabCommits.push(...day.gitlabCommits)
        if (day.durationMs > 0) {
          existing.durationMs = existing.durationMs > 0
            ? Math.round((existing.durationMs + day.durationMs) / 2)
            : day.durationMs
        }
        // Statut dominant : FAIL/ERROR > SKIP > PASS
        if (day.status === 'FAIL' || day.status === 'ERROR') {
          existing.status = day.status
        } else if (day.status === 'SKIP' && existing.status === 'PASS') {
          existing.status = 'SKIP'
        }
      }
    }
  }

  return Array.from(byDate.values()).sort((a, b) => a.date.localeCompare(b.date))
}
