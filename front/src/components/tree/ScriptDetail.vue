<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TreeNode, ScriptStatus } from '@/fixtures/testTree'
import type { HeatmapDay, GitlabCommit } from '@/fixtures/heatmapData'
import { getHeatmapForNode } from '@/fixtures/heatmapData'

const props = defineProps<{
  node: TreeNode
}>()

const emit = defineEmits<{
  close: []
}>()

// ── Data ───────────────────────────────────────────────────
const runs = computed(() => props.node.runs ?? [])

const allScriptRuns = computed(() => {
  if (props.node.type !== 'module') return []
  const scripts: TreeNode[] = []
  function collect(n: TreeNode) {
    if (n.type === 'script') scripts.push(n)
    n.children.forEach(collect)
  }
  collect(props.node)
  return scripts.flatMap((s) => s.runs ?? [])
})

const effectiveRuns = computed(() =>
  props.node.type === 'module' ? allScriptRuns.value : runs.value,
)

const heatmapDays = computed(() => getHeatmapForNode(props.node))

const heatmapMap = computed(() => {
  const map = new Map<string, HeatmapDay>()
  for (const d of heatmapDays.value) map.set(d.date, d)
  return map
})

// ── Week carousel ─────────────────────────────────────────
const weekOffset = ref(0) // 0 = cette semaine, -1 = sem. dernière…
const MAX_WEEKS_BACK = 6

type DayCell = HeatmapDay & { future: boolean; isToday: boolean }

const currentWeekDays = computed<DayCell[]>(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayDow = today.getDay()
  const mondayOffset = todayDow === 0 ? 6 : todayDow - 1
  const thisMonday = new Date(today)
  thisMonday.setDate(thisMonday.getDate() - mondayOffset)

  const targetMonday = new Date(thisMonday)
  targetMonday.setDate(targetMonday.getDate() + weekOffset.value * 7)

  const days: DayCell[] = []
  for (let d = 0; d < 7; d++) {
    const date = new Date(targetMonday)
    date.setDate(date.getDate() + d)
    const key = date.toISOString().slice(0, 10)
    const isFuture = date > today
    const isToday = key === today.toISOString().slice(0, 10)
    const existing = heatmapMap.value.get(key)
    days.push({
      date: key,
      status: existing?.status ?? null,
      durationMs: existing?.durationMs ?? 0,
      execCount: existing?.execCount ?? 0,
      hasAtsCommit: existing?.hasAtsCommit ?? false,
      gitlabCommits: existing?.gitlabCommits ?? [],
      future: isFuture,
      isToday,
    })
  }
  return days
})

const weekLabel = computed(() => {
  if (weekOffset.value === 0) return 'Cette semaine'
  if (weekOffset.value === -1) return 'Semaine derniere'
  const first = currentWeekDays.value[0]
  const last = currentWeekDays.value[6]
  const fmt = (iso: string) =>
    new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' })
  return `${fmt(first.date)} — ${fmt(last.date)}`
})

function prevWeek() {
  if (weekOffset.value > -MAX_WEEKS_BACK) {
    weekOffset.value--
    selectedDay.value = null
  }
}
function nextWeek() {
  if (weekOffset.value < 0) {
    weekOffset.value++
    selectedDay.value = null
  }
}

// ── Selected day ──────────────────────────────────────────
const selectedDay = ref<DayCell | null>(null)

function selectDay(day: DayCell) {
  if (day.future) return
  if (selectedDay.value?.date === day.date) {
    selectedDay.value = null
  } else {
    selectedDay.value = day
  }
}

// ── Sub-tabs (bottom) ─────────────────────────────────────
type SubTab = 'resume' | 'executions' | 'modifications' | 'infos'
const activeSubTab = ref<SubTab>('resume')

const subTabs: { key: SubTab; label: string }[] = [
  { key: 'resume', label: 'Resume' },
  { key: 'executions', label: 'Executions' },
  { key: 'modifications', label: 'Modifications' },
  { key: 'infos', label: 'Infos' },
]

// Runs filtered by selected day
const dayRuns = computed(() => {
  if (!selectedDay.value) return effectiveRuns.value
  const dayStr = selectedDay.value.date
  return effectiveRuns.value.filter((r) => r.date.startsWith(dayStr))
})

// ── Stats ─────────────────────────────────────────────────
const successRate = computed(() => {
  if (effectiveRuns.value.length === 0) return 0
  return Math.round(
    (effectiveRuns.value.filter((r) => r.status === 'PASS').length /
      effectiveRuns.value.length) *
      100,
  )
})

const streak = computed(() => {
  if (runs.value.length === 0) return null
  const first = runs.value[0].status
  let count = 1
  for (let i = 1; i < runs.value.length; i++) {
    if (runs.value[i].status === first) count++
    else break
  }
  return { status: first, count }
})

const avgDuration = computed(() => {
  if (effectiveRuns.value.length === 0) return 0
  return Math.round(
    effectiveRuns.value.reduce((s, r) => s + r.durationMs, 0) /
      effectiveRuns.value.length,
  )
})

const dayVsAvg = computed(() => {
  if (!selectedDay.value || selectedDay.value.durationMs === 0 || avgDuration.value === 0) return null
  const diff = ((selectedDay.value.durationMs - avgDuration.value) / avgDuration.value) * 100
  return Math.round(diff)
})

// Module-specific
const scriptCounts = computed(() => {
  if (props.node.type !== 'module') return null
  const scripts: TreeNode[] = []
  function collect(n: TreeNode) {
    if (n.type === 'script') scripts.push(n)
    n.children.forEach(collect)
  }
  collect(props.node)
  return {
    total: scripts.length,
    green: scripts.filter((s) => s.confidence === 'green').length,
    orange: scripts.filter((s) => s.confidence === 'orange').length,
    red: scripts.filter((s) => s.confidence === 'red').length,
    flaky: scripts.filter((s) => s.flaky).length,
    scripts,
  }
})

// ── Formatting ────────────────────────────────────────────
function statusUtility(status: ScriptStatus): string {
  switch (status) {
    case 'PASS': return 'st-green'
    case 'FAIL': return 'st-red'
    case 'SKIP': return 'st-skip'
    case 'ERROR': return 'st-orange'
  }
}

const statusLabel: Record<ScriptStatus, string> = {
  PASS: 'Succes',
  FAIL: 'Echec',
  SKIP: 'Ignore',
  ERROR: 'Erreur',
}

const statusColorVar: Record<ScriptStatus, string> = {
  PASS: 'var(--c-green)',
  FAIL: 'var(--c-red)',
  SKIP: 'var(--c-skip)',
  ERROR: 'var(--c-orange)',
}

const confidenceLabel: Record<string, string> = {
  green: 'Stable',
  orange: 'Instable',
  red: 'En echec',
}
const confidenceDesc: Record<string, string> = {
  green: 'Les dernieres executions passent systematiquement.',
  orange: 'Resultats mixtes ou donnees insuffisantes.',
  red: 'Plusieurs echecs consecutifs sans correction recente.',
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

function formatDuration(ms: number): string {
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s`
  return `${Math.floor(s / 60)}m${String(s % 60).padStart(2, '0')}s`
}

function daysAgo(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000)
  if (days === 0) return "Aujourd'hui"
  if (days === 1) return 'Hier'
  return `Il y a ${days} j`
}

function formatFullDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long',
  })
}

function formatDayLetter(iso: string): string {
  const letters = ['D', 'L', 'M', 'M', 'J', 'V', 'S']
  return letters[new Date(iso).getDay()]
}

function formatDayNum(iso: string): string {
  return new Date(iso).getDate().toString()
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}

function cellColor(day: DayCell): string {
  if (day.future) return 'var(--c-border-row)'
  if (day.execCount === 0) return 'var(--c-inset)'
  if (day.status === 'PASS') return 'var(--c-green)'
  if (day.status === 'FAIL') return 'var(--c-red)'
  if (day.status === 'ERROR') return '#b71c1c'
  if (day.status === 'SKIP') return 'var(--c-skip)'
  return 'var(--c-orange)'
}

const rateColor = computed(() => {
  if (successRate.value >= 80) return 'var(--c-green)'
  if (successRate.value >= 50) return 'var(--c-orange)'
  return 'var(--c-red)'
})

const circlePath =
  'M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831'
</script>

<template>
  <div class="detail-panel">
    <button class="close-btn" @click="emit('close')" title="Fermer (Echap)">&#x2715;</button>

    <!-- ── TOP: Rate circle + Week carousel ─────────────── -->
    <div class="top-section">
      <div class="rate-block">
        <svg viewBox="0 0 36 36" class="rate-circle">
          <path :d="circlePath" fill="none" stroke="var(--c-border-inner)" stroke-width="3" />
          <path
            :d="circlePath"
            fill="none"
            :stroke="rateColor"
            stroke-width="3"
            stroke-linecap="round"
            :stroke-dasharray="`${successRate}, 100`"
          />
          <text
            x="18" y="19"
            text-anchor="middle"
            dominant-baseline="middle"
            class="rate-number"
            :fill="rateColor"
          >
            {{ successRate }}
          </text>
          <text x="18" y="25" text-anchor="middle" class="rate-pct" fill="var(--c-text-muted)">%</text>
        </svg>
        <span class="rate-label">Taux de reussite</span>
      </div>

      <div class="week-carousel">
        <!-- Navigation -->
        <div class="week-nav">
          <button
            class="week-arrow"
            :class="{ disabled: weekOffset >= 0 }"
            @click="nextWeek"
          >
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M9 3L5 7L9 11" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" /></svg>
          </button>
          <span class="week-label-text">{{ weekLabel }}</span>
          <button
            class="week-arrow"
            :class="{ disabled: weekOffset <= -MAX_WEEKS_BACK }"
            @click="prevWeek"
          >
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M5 3L9 7L5 11" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" /></svg>
          </button>
        </div>

        <!-- 7 day cells -->
        <div class="week-days">
          <div
            v-for="day in currentWeekDays"
            :key="day.date"
            class="day-col"
            @click="selectDay(day)"
          >
            <span class="day-letter">{{ formatDayLetter(day.date) }}</span>
            <div
              class="day-cell"
              :class="{
                selected: selectedDay?.date === day.date,
                future: day.future,
                empty: !day.future && day.execCount === 0,
                today: day.isToday,
              }"
              :style="{ background: cellColor(day) }"
            >
              <span class="day-num">{{ formatDayNum(day.date) }}</span>
              <!-- Git activity dot (commits or .ats) -->
              <span v-if="day.hasAtsCommit || day.gitlabCommits.length > 0" class="cell-git-dot" />
            </div>
          </div>
        </div>

        <!-- Mini legend -->
        <div class="week-legend">
          <span class="legend-item"><span class="legend-dot" style="background: var(--c-green)" /> Pass</span>
          <span class="legend-item"><span class="legend-dot" style="background: var(--c-red)" /> Fail</span>
          <span class="legend-item"><span class="legend-dot" style="background: var(--c-inset)" /> Aucune</span>
          <span class="legend-sep" />
          <span class="legend-item"><span class="legend-dot legend-dot-git" /> Git</span>
        </div>
      </div>
    </div>

    <!-- ── DIVIDER ──────────────────────────────────────── -->
    <div class="section-divider" />

    <!-- ── BOTTOM: Sub-tabs + Content ───────────────────── -->
    <div class="bottom-section">
      <!-- Sub-tab bar (segmented control) -->
      <div class="sub-tab-bar">
        <button
          v-for="tab in subTabs"
          :key="tab.key"
          class="sub-tab"
          :class="{ active: activeSubTab === tab.key }"
          @click="activeSubTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- Day context banner -->
      <div v-if="selectedDay" class="day-banner">
        <div class="day-banner-left">
          <span class="day-banner-date">{{ formatFullDate(selectedDay.date) }}</span>
          <span class="day-banner-ago">{{ daysAgo(selectedDay.date) }}</span>
        </div>
        <button class="day-banner-clear" @click="selectedDay = null" title="Voir tout">
          Tout afficher &#x2715;
        </button>
      </div>

      <!-- Sub-tab content -->
      <div class="sub-content">

        <!-- ── RESUME ─────────────────────────────────── -->
        <div v-if="activeSubTab === 'resume'" class="resume-content">
          <!-- Day selected -->
          <template v-if="selectedDay">
            <div class="resume-grid">
              <div class="resume-card">
                <div class="resume-card-value" :class="selectedDay.status ? statusUtility(selectedDay.status as ScriptStatus) : ''">
                  {{ selectedDay.status ? statusLabel[selectedDay.status as ScriptStatus] : 'Aucune' }}
                </div>
                <div class="resume-card-label">Statut</div>
              </div>
              <div class="resume-card">
                <div class="resume-card-value">{{ selectedDay.execCount }}</div>
                <div class="resume-card-label">Execution{{ selectedDay.execCount > 1 ? 's' : '' }}</div>
              </div>
              <div class="resume-card">
                <div class="resume-card-value">{{ selectedDay.durationMs > 0 ? formatDuration(selectedDay.durationMs) : '—' }}</div>
                <div class="resume-card-label">Duree moy.</div>
              </div>
              <div class="resume-card">
                <div class="resume-card-value">{{ selectedDay.gitlabCommits.length }}</div>
                <div class="resume-card-label">Commit{{ selectedDay.gitlabCommits.length > 1 ? 's' : '' }} Git</div>
              </div>
            </div>

            <div v-if="dayVsAvg !== null" class="resume-comparison">
              <span :class="dayVsAvg > 0 ? 'cmp-slower' : 'cmp-faster'">
                {{ dayVsAvg > 0 ? '+' : '' }}{{ dayVsAvg }}% {{ dayVsAvg > 0 ? 'plus lent' : 'plus rapide' }}
              </span>
              que la moyenne ({{ formatDuration(avgDuration) }})
            </div>

            <div v-if="selectedDay.hasAtsCommit" class="resume-ats-badge">
              Modification .ats ce jour
            </div>
          </template>

          <!-- No day selected: general overview -->
          <template v-else>
            <div class="resume-grid">
              <div class="resume-card">
                <div class="resume-card-value" :style="{ color: rateColor }">{{ successRate }}%</div>
                <div class="resume-card-label">Taux de reussite</div>
              </div>
              <div class="resume-card">
                <div class="resume-card-value">{{ effectiveRuns.length }}</div>
                <div class="resume-card-label">Executions totales</div>
              </div>
              <div class="resume-card">
                <div class="resume-card-value">{{ formatDuration(avgDuration) }}</div>
                <div class="resume-card-label">Duree moyenne</div>
              </div>
              <div v-if="streak && node.type === 'script'" class="resume-card">
                <div class="resume-card-value" :style="{ color: statusColorVar[streak.status] }">{{ streak.count }}</div>
                <div class="resume-card-label">{{ statusLabel[streak.status] }}{{ streak.count > 1 ? 's' : '' }} consec.</div>
              </div>
              <div v-if="scriptCounts" class="resume-card">
                <div class="resume-card-value">{{ scriptCounts.total }}</div>
                <div class="resume-card-label">Scripts</div>
              </div>
            </div>

            <!-- Module breakdown -->
            <div v-if="scriptCounts" class="module-breakdown">
              <div class="breakdown-row">
                <span class="breakdown-dot" style="background: var(--c-green)" />
                <span class="breakdown-label">Stables</span>
                <span class="breakdown-value">{{ scriptCounts.green }}</span>
              </div>
              <div class="breakdown-row">
                <span class="breakdown-dot" style="background: var(--c-orange)" />
                <span class="breakdown-label">Instables</span>
                <span class="breakdown-value">{{ scriptCounts.orange }}</span>
              </div>
              <div class="breakdown-row">
                <span class="breakdown-dot" style="background: var(--c-red)" />
                <span class="breakdown-label">En echec</span>
                <span class="breakdown-value">{{ scriptCounts.red }}</span>
              </div>
              <div class="breakdown-row">
                <span class="breakdown-dot" style="background: var(--c-skip)" />
                <span class="breakdown-label">Flaky</span>
                <span class="breakdown-value">{{ scriptCounts.flaky }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- ── EXECUTIONS ─────────────────────────────── -->
        <div v-if="activeSubTab === 'executions'" class="executions-content">
          <table v-if="dayRuns.length > 0" class="runs-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Statut</th>
                <th>Duree</th>
                <th>Build</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="run in dayRuns" :key="run.jenkinsRunId">
                <td>
                  <div class="date-cell">
                    <span class="date-main">{{ formatDate(run.date) }}</span>
                    <span class="date-ago">{{ daysAgo(run.date) }}</span>
                  </div>
                </td>
                <td>
                  <span class="status-badge" :class="statusUtility(run.status)">
                    {{ statusLabel[run.status] }}
                  </span>
                </td>
                <td class="duration-cell">{{ formatDuration(run.durationMs) }}</td>
                <td class="build-cell">{{ run.jenkinsRunId }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">
            {{ selectedDay ? 'Aucune execution ce jour.' : 'Aucune execution enregistree.' }}
          </div>
        </div>

        <!-- ── MODIFICATIONS ──────────────────────────── -->
        <div v-if="activeSubTab === 'modifications'" class="modifications-content">
          <template v-if="selectedDay">
            <div v-if="selectedDay.gitlabCommits.length > 0" class="commit-list">
              <div v-for="(commit, i) in selectedDay.gitlabCommits" :key="i" class="commit-card">
                <div class="commit-header">
                  <span class="commit-hash">{{ commit.hash }}</span>
                  <span class="commit-time">{{ formatTime(commit.date) }}</span>
                </div>
                <div class="commit-message">{{ commit.message }}</div>
                <div class="commit-meta">
                  <span class="commit-author">{{ commit.author }}</span>
                  <span class="commit-files">{{ commit.filesChanged }} fichier{{ commit.filesChanged > 1 ? 's' : '' }}</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">Aucun commit ce jour.</div>
          </template>
          <template v-else>
            <div class="empty-state">Selectionnez un jour pour voir les modifications GitLab.</div>
          </template>
        </div>

        <!-- ── INFOS ──────────────────────────────────── -->
        <div v-if="activeSubTab === 'infos'" class="infos-content">
          <div class="info-row">
            <span class="info-label">Chemin complet</span>
            <span class="info-value mono">{{ node.fullPath }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Type</span>
            <span class="info-value">{{ node.type === 'module' ? 'Module' : 'Script' }}</span>
          </div>
          <div v-if="node.type === 'script'" class="info-row">
            <span class="info-label">Confiance</span>
            <span class="info-value">
              <span class="confidence-badge" :class="'st-' + (node.confidence ?? 'orange')">
                {{ confidenceLabel[node.confidence ?? 'orange'] }}
              </span>
              <span class="confidence-desc">{{ confidenceDesc[node.confidence ?? 'orange'] }}</span>
            </span>
          </div>
          <div v-if="node.type === 'script'" class="info-row">
            <span class="info-label">Flaky</span>
            <span class="info-value">{{ node.flaky ? 'Oui — resultats inconstants' : 'Non' }}</span>
          </div>
          <div v-if="node.type === 'module' && scriptCounts" class="info-row">
            <span class="info-label">Scripts</span>
            <span class="info-value">{{ scriptCounts.total }} script{{ scriptCounts.total > 1 ? 's' : '' }}</span>
          </div>
          <div v-if="node.lastAtsModif" class="info-row">
            <span class="info-label">Derniere modif .ats</span>
            <span class="info-value">
              {{ formatDate(node.lastAtsModif) }}
              <span class="date-ago">{{ daysAgo(node.lastAtsModif) }}</span>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">Executions</span>
            <span class="info-value">{{ effectiveRuns.length }} enregistree{{ effectiveRuns.length > 1 ? 's' : '' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Duree moyenne</span>
            <span class="info-value">{{ formatDuration(avgDuration) }}</span>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Panel ──────────────────────────────────────────────────── */
.detail-panel {
  width: 90%;
  max-width: 1200px;
  height: 100%;
  max-height: 560px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  box-shadow: 0 0.5rem 2.5rem rgba(0, 0, 0, 0.4), 0 0 1.25rem var(--c-accent-glow-sub);
}

/* ── Close button ───────────────────────────────────────────── */
.close-btn {
  position: absolute;
  top: 0.625rem;
  right: 0.875rem;
  z-index: 2;
  background: none;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
  color: var(--c-text-muted);
  font-size: 0.875rem;
  width: 1.875rem;
  height: 1.875rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}
.close-btn:hover {
  color: var(--c-text);
  border-color: var(--c-border-strong);
  background: rgba(255, 255, 255, 0.04);
}

/* ── TOP SECTION ────────────────────────────────────────────── */
.top-section {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
  padding: 1.25rem 1.5rem 1rem;
  flex-shrink: 0;
}

/* Rate circle */
.rate-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.375rem;
  flex-shrink: 0;
}
.rate-circle {
  width: 5.25rem;
  height: 5.25rem;
}
.rate-number {
  font-size: 10px;
  font-weight: 700;
}
.rate-pct {
  font-size: 5px;
}
.rate-label {
  color: var(--c-text-muted);
  font-size: 0.75rem;
}

/* Week carousel */
.week-carousel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
}

.week-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}
.week-arrow {
  background: none;
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-sm);
  color: var(--c-text-muted);
  width: 1.625rem;
  height: 1.625rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}
.week-arrow:hover:not(.disabled) {
  color: var(--c-text);
  border-color: var(--c-border-strong);
  background: rgba(255, 255, 255, 0.04);
}
.week-arrow.disabled {
  opacity: 0.3;
  cursor: default;
}
.week-label-text {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--c-text-2);
  min-width: 10rem;
  text-align: center;
}

/* Day cells */
.week-days {
  display: flex;
  gap: 0.375rem;
}
.day-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  cursor: pointer;
}
.day-letter {
  font-size: 0.6875rem;
  color: var(--c-text-dim);
  font-weight: 500;
}
.day-cell {
  width: 100%;
  aspect-ratio: 1;
  max-height: 3rem;
  border-radius: var(--radius-sm);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: outline 0.15s, box-shadow 0.15s, transform 0.1s;
}
.day-cell:hover:not(.future) {
  outline: 1.5px solid var(--c-text-muted);
  outline-offset: 1px;
  transform: scale(1.04);
}
.day-cell.selected {
  outline: 2px solid var(--c-accent);
  outline-offset: 2px;
  box-shadow: 0 0 10px var(--c-accent-glow);
}
.day-cell.today:not(.selected) {
  outline: 1.5px solid var(--c-text-dim);
  outline-offset: 1px;
}
.day-cell.future {
  cursor: default;
  opacity: 0.25;
}
.day-cell.empty {
  opacity: 0.45;
}
.day-num {
  font-size: 0.6875rem;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.5);
  pointer-events: none;
}
.day-cell.empty .day-num,
.day-cell.future .day-num {
  color: var(--c-text-dim);
}

/* Cell markers */
.cell-git-dot {
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}

/* Legend */
.week-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.625rem;
  color: var(--c-text-dim);
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}
.legend-dot-git {
  background: #fff;
  border: 1px solid var(--c-text-dim);
  border-radius: 50%;
  width: 5px;
  height: 5px;
}
.legend-sep {
  width: 1px;
  height: 10px;
  background: var(--c-border-subtle);
}

/* ── DIVIDER ────────────────────────────────────────────────── */
.section-divider {
  height: 1px;
  background: var(--c-border-inner);
  margin: 0 1.25rem;
  flex-shrink: 0;
}

/* ── BOTTOM SECTION ─────────────────────────────────────────── */
.bottom-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0.75rem 1.5rem 1rem;
  gap: 0.625rem;
}

/* Sub-tab bar (segmented control) */
.sub-tab-bar {
  display: flex;
  gap: 2px;
  padding: 3px;
  background: var(--c-inset);
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.sub-tab {
  flex: 1;
  background: none;
  border: none;
  color: var(--c-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 0.375rem 0.5rem;
  border-radius: calc(var(--radius-md) - 2px);
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
  white-space: nowrap;
}
.sub-tab:hover {
  color: var(--c-text-2);
}
.sub-tab.active {
  background: var(--c-surface);
  color: var(--c-accent);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

/* Day context banner */
.day-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  background: rgba(79, 195, 247, 0.06);
  border: 1px solid rgba(79, 195, 247, 0.12);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.day-banner-left {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}
.day-banner-date {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--c-text);
  text-transform: capitalize;
}
.day-banner-ago {
  font-size: 0.6875rem;
  color: var(--c-text-dim);
}
.day-banner-clear {
  background: none;
  border: none;
  color: var(--c-text-dim);
  font-size: 0.6875rem;
  cursor: pointer;
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
  transition: color 0.2s, background 0.2s;
}
.day-banner-clear:hover {
  color: var(--c-text);
  background: rgba(255, 255, 255, 0.06);
}

/* Sub-content */
.sub-content {
  flex: 1;
  overflow-y: auto;
}

/* ── RESUME ─────────────────────────────────────────────────── */
.resume-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  gap: 0.5rem;
}
.resume-card {
  background: var(--c-inset);
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-md);
  padding: 0.75rem;
  text-align: center;
}
.resume-card-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--c-text);
}
.resume-card-label {
  color: var(--c-text-muted);
  font-size: 0.6875rem;
  margin-top: 0.125rem;
}

.resume-comparison {
  font-size: 0.8125rem;
  color: var(--c-text-muted);
  padding: 0.5rem 0.75rem;
  background: var(--c-inset);
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-sm);
}
.cmp-slower { color: var(--c-red); font-weight: 600; }
.cmp-faster { color: var(--c-green); font-weight: 600; }

.resume-ats-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background: rgba(79, 195, 247, 0.08);
  border: 1px solid rgba(79, 195, 247, 0.15);
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  color: var(--c-accent);
  align-self: flex-start;
}

/* Module breakdown */
.module-breakdown {
  background: var(--c-inset);
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-md);
  padding: 0.25rem 0.75rem;
}
.breakdown-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--c-border-row);
}
.breakdown-row:last-child { border-bottom: none; }
.breakdown-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}
.breakdown-label {
  flex: 1;
  font-size: 0.8125rem;
  color: var(--c-text-muted);
}
.breakdown-value {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--c-text);
}

/* ── EXECUTIONS ─────────────────────────────────────────────── */
.runs-table {
  width: 100%;
  border-collapse: collapse;
  text-align: center;
}
.runs-table th {
  color: var(--c-text-muted);
  font-size: 0.6875rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 0 0.75rem 0.625rem;
  border-bottom: 1px solid var(--c-border-inner);
}
.runs-table td {
  padding: 0.625rem 0.75rem;
  color: var(--c-text-2);
  font-size: 0.8125rem;
  border-bottom: 1px solid var(--c-border-row);
}
.date-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.date-main {
  color: var(--c-text);
}
.date-ago {
  color: var(--c-text-dim);
  font-size: 0.75rem;
}
.status-badge {
  display: inline-block;
  padding: 0.1875rem 0.625rem;
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  font-weight: 500;
}
.duration-cell {
  font-variant-numeric: tabular-nums;
}
.build-cell {
  color: var(--c-text-dim);
  font-family: monospace;
  font-size: 0.8125rem;
}

/* ── MODIFICATIONS ──────────────────────────────────────────── */
.commit-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.commit-card {
  background: var(--c-inset);
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-md);
  padding: 0.625rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}
.commit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.commit-hash {
  font-family: monospace;
  font-size: 0.75rem;
  color: var(--c-accent);
  background: rgba(79, 195, 247, 0.08);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}
.commit-time {
  font-size: 0.6875rem;
  color: var(--c-text-dim);
}
.commit-message {
  font-size: 0.8125rem;
  color: var(--c-text);
  line-height: 1.4;
}
.commit-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.6875rem;
  color: var(--c-text-muted);
}
.commit-author { font-weight: 500; }

/* ── INFOS ──────────────────────────────────────────────────── */
.infos-content {
  display: flex;
  flex-direction: column;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--c-border-row);
}
.info-label {
  color: var(--c-text-muted);
  font-size: 0.8125rem;
  flex-shrink: 0;
  width: 10rem;
}
.info-value {
  color: var(--c-text);
  font-size: 0.8125rem;
  text-align: right;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
}
.info-value.mono {
  font-family: monospace;
  font-size: 0.75rem;
  color: var(--c-accent);
}
.confidence-badge {
  display: inline-block;
  padding: 0.1875rem 0.625rem;
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  font-weight: 500;
}
.confidence-desc {
  color: var(--c-text-dim);
  font-size: 0.75rem;
  max-width: 17.5rem;
}

/* ── Empty state ────────────────────────────────────────────── */
.empty-state {
  color: var(--c-text-dim);
  font-size: 0.8125rem;
  text-align: center;
  padding: 2rem 0;
  font-style: italic;
}
</style>
