<script setup lang="ts">
import { ref, computed } from 'vue'
import type { HeatmapDay } from '@/fixtures/heatmapData'

type ViewMode = 'statut' | 'duree'

const props = defineProps<{
  days: HeatmapDay[]
}>()

const mode = ref<ViewMode>('statut')
const selectedDay = ref<HeatmapDay | null>(null)
const showTable = ref(false)

// ── Filtered days (only with executions) ─────────────────────

const activeDays = computed(() =>
  props.days.filter((d) => d.execCount > 0),
)

const maxDuration = computed(() =>
  Math.max(...activeDays.value.map((d) => d.durationMs), 1),
)

// ── Month labels ─────────────────────────────────────────────

const months = computed(() => {
  const labels: { label: string; index: number }[] = []
  let lastMonth = -1

  for (let i = 0; i < activeDays.value.length; i++) {
    const m = new Date(activeDays.value[i].date).getMonth()
    if (m !== lastMonth) {
      const names = ['Jan', 'Fev', 'Mar', 'Avr', 'Mai', 'Jun', 'Jul', 'Aou', 'Sep', 'Oct', 'Nov', 'Dec']
      labels.push({ label: names[m], index: i })
      lastMonth = m
    }
  }

  return labels
})

// ── Bar color ────────────────────────────────────────────────

function barColor(day: HeatmapDay): string {
  if (mode.value === 'statut') {
    if (day.status === 'PASS') return 'var(--c-green)'
    if (day.status === 'FAIL') return 'var(--c-red)'
    if (day.status === 'ERROR') return '#b71c1c'
    if (day.status === 'SKIP') return 'var(--c-skip)'
    return 'var(--c-orange)'
  }

  // duree
  const ratio = day.durationMs / maxDuration.value
  if (ratio < 0.25) return 'var(--c-green)'
  if (ratio < 0.5) return 'var(--c-orange)'
  if (ratio < 0.75) return 'var(--c-red)'
  return '#b71c1c'
}

// ── Bar height (duree mode = proportional, statut = full) ────

function barHeight(day: HeatmapDay): string {
  if (mode.value === 'statut') return '100%'
  const min = 20
  const ratio = day.durationMs / maxDuration.value
  return `${min + ratio * (100 - min)}%`
}

// ── Selection ────────────────────────────────────────────────

function selectDay(day: HeatmapDay) {
  if (selectedDay.value?.date === day.date) {
    selectedDay.value = null
    showTable.value = false
  } else {
    selectedDay.value = day
    showTable.value = false
  }
}

// ── Format helpers ───────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}

function formatDateShort(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' })
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const s = ms / 1000
  if (s < 60) return `${s.toFixed(1)}s`
  return `${Math.floor(s / 60)}m${String(Math.round(s % 60)).padStart(2, '0')}s`
}

function daysAgo(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000)
  if (days === 0) return "Aujourd'hui"
  if (days === 1) return 'Hier'
  return `Il y a ${days}j`
}

const statusLabels: Record<string, string> = {
  PASS: 'Reussi',
  FAIL: 'Echoue',
  ERROR: 'Erreur',
  SKIP: 'Ignore',
}

const statusClass: Record<string, string> = {
  PASS: 'st-green',
  FAIL: 'st-red',
  ERROR: 'st-orange',
  SKIP: 'st-skip',
}
</script>

<template>
  <div class="activity-timeline">
    <!-- Mode switcher -->
    <div class="tl-modes">
      <button
        v-for="m in (['statut', 'duree'] as ViewMode[])"
        :key="m"
        class="mode-btn"
        :class="{ active: mode === m }"
        @click="mode = m"
      >
        {{ m === 'duree' ? 'Duree' : 'Statut' }}
      </button>
    </div>

    <!-- Timeline bars -->
    <div class="tl-scroll">
      <div class="tl-bars-wrapper">
        <!-- Month labels -->
        <div class="tl-months">
          <span
            v-for="m in months"
            :key="m.index"
            class="tl-month"
            :style="{ left: m.index * 10 + 'px' }"
          >
            {{ m.label }}
          </span>
        </div>

        <!-- Bars area -->
        <div class="tl-bars-area">
          <div
            v-for="day in activeDays"
            :key="day.date"
            class="tl-bar-col"
            :class="{ selected: selectedDay?.date === day.date }"
            @click="selectDay(day)"
          >
            <!-- .ats commit marker -->
            <svg
              v-if="day.hasAtsCommit"
              class="tl-ats-marker"
              viewBox="0 0 8 6"
            >
              <polygon points="0,6 4,0 8,6" fill="var(--c-accent)" />
            </svg>

            <!-- Bar -->
            <div class="tl-bar-track">
              <div
                class="tl-bar"
                :style="{
                  height: barHeight(day),
                  background: barColor(day),
                }"
              />
            </div>

            <!-- Day label (show for Mondays or selected) -->
            <span
              v-if="new Date(day.date).getDay() === 1 || selectedDay?.date === day.date"
              class="tl-day-label"
            >
              {{ formatDateShort(day.date) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Legend -->
    <div class="tl-legend">
      <template v-if="mode === 'statut'">
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-green)" /> Pass</span>
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-red)" /> Fail</span>
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-skip)" /> Skip</span>
        <span class="legend-sep" />
        <span class="legend-item">
          <svg class="legend-tri" viewBox="0 0 8 6"><polygon points="0,6 4,0 8,6" fill="var(--c-accent)" /></svg>
          Commit .ats
        </span>
      </template>
      <template v-else>
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-green)" /> Rapide</span>
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-orange)" /> Moyen</span>
        <span class="legend-item"><span class="legend-dot" style="background: var(--c-red)" /> Lent</span>
        <span class="legend-item"><span class="legend-dot" style="background: #b71c1c" /> Tres lent</span>
      </template>
    </div>

    <!-- Detail panel -->
    <Transition name="slide">
      <div v-if="selectedDay" class="tl-detail">
        <!-- Summary row -->
        <div class="detail-summary">
          <div class="detail-date">
            <span class="detail-date-main">{{ formatDate(selectedDay.date) }}</span>
            <span class="detail-date-ago">{{ daysAgo(selectedDay.date) }}</span>
          </div>

          <div class="detail-chips">
            <span class="detail-chip" :class="statusClass[selectedDay.status ?? '']">
              {{ statusLabels[selectedDay.status ?? ''] ?? '—' }}
            </span>
            <span class="detail-chip chip-neutral">
              {{ selectedDay.execCount }} exec{{ selectedDay.execCount > 1 ? 's' : '' }}
            </span>
            <span class="detail-chip chip-neutral">
              {{ formatDuration(selectedDay.durationMs) }} moy.
            </span>
            <span v-if="selectedDay.hasAtsCommit" class="detail-chip chip-accent">
              Commit .ats
            </span>
          </div>
        </div>

        <!-- Expandable table -->
        <button
          v-if="selectedDay.execCount > 0"
          class="detail-expand-btn"
          @click="showTable = !showTable"
        >
          {{ showTable ? 'Masquer' : 'Voir' }} les executions
          <svg
            class="expand-chevron"
            :class="{ open: showTable }"
            width="12" height="12" viewBox="0 0 12 12"
          >
            <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" />
          </svg>
        </button>

        <Transition name="expand">
          <div v-if="showTable" class="detail-table-wrap">
            <div class="detail-note">
              Donnees agregees pour cette journee — detail par execution a venir avec l'integration Jenkins.
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.activity-timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* ── Mode switcher ─────────────────────────────────────────── */
.tl-modes {
  display: flex;
  gap: 0.25rem;
  background: var(--c-inset);
  border-radius: var(--radius-md);
  padding: 0.1875rem;
  width: fit-content;
}
.mode-btn {
  background: none;
  border: none;
  color: var(--c-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 0.375rem 0.75rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}
.mode-btn:hover {
  color: var(--c-text);
}
.mode-btn.active {
  background: var(--c-surface);
  color: var(--c-accent);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

/* ── Timeline scroll ───────────────────────────────────────── */
.tl-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 0.25rem;
}
.tl-scroll::-webkit-scrollbar {
  height: 4px;
}
.tl-scroll::-webkit-scrollbar-track {
  background: var(--c-inset);
  border-radius: 2px;
}
.tl-scroll::-webkit-scrollbar-thumb {
  background: var(--c-border);
  border-radius: 2px;
}

.tl-bars-wrapper {
  display: flex;
  flex-direction: column;
  min-width: fit-content;
}

/* ── Month labels ──────────────────────────────────────────── */
.tl-months {
  position: relative;
  height: 1.125rem;
  margin-bottom: 0.25rem;
}
.tl-month {
  position: absolute;
  font-size: 0.625rem;
  color: var(--c-text-muted);
  white-space: nowrap;
  font-weight: 500;
}

/* ── Bars ───────────────────────────────────────────────────── */
.tl-bars-area {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 7.5rem;
}

.tl-bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
}

.tl-bar-track {
  width: 8px;
  height: 5.5rem;
  display: flex;
  align-items: flex-end;
  border-radius: 2px 2px 0 0;
  overflow: hidden;
}

.tl-bar {
  width: 100%;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s ease, background 0.2s, opacity 0.2s;
  opacity: 0.8;
}

.tl-bar-col:hover .tl-bar {
  opacity: 1;
}
.tl-bar-col.selected .tl-bar {
  opacity: 1;
  box-shadow: 0 0 6px currentColor;
}
.tl-bar-col.selected {
  z-index: 1;
}
.tl-bar-col.selected::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--c-accent);
}

/* ── .ats marker (triangle above bar) ──────────────────────── */
.tl-ats-marker {
  width: 8px;
  height: 6px;
  flex-shrink: 0;
}

/* ── Day labels ────────────────────────────────────────────── */
.tl-day-label {
  font-size: 0.5625rem;
  color: var(--c-text-dim);
  white-space: nowrap;
  margin-top: 1px;
}
.tl-bar-col.selected .tl-day-label {
  color: var(--c-accent);
  font-weight: 600;
}

/* ── Legend ─────────────────────────────────────────────────── */
.tl-legend {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.6875rem;
  color: var(--c-text-muted);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}
.legend-tri {
  width: 8px;
  height: 6px;
  flex-shrink: 0;
}
.legend-sep {
  width: 1px;
  height: 12px;
  background: var(--c-border-subtle);
}

/* ── Detail panel ──────────────────────────────────────────── */
.tl-detail {
  background: var(--c-inset);
  border: 1px solid var(--c-border-inner);
  border-radius: var(--radius-md);
  padding: 0.875rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.detail-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.detail-date {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.detail-date-main {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--c-text);
  text-transform: capitalize;
}
.detail-date-ago {
  font-size: 0.6875rem;
  color: var(--c-text-dim);
}

.detail-chips {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.detail-chip {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-md);
  font-size: 0.75rem;
  font-weight: 500;
}
.chip-neutral {
  background: rgba(255, 255, 255, 0.06);
  color: var(--c-text-2);
  border: 1px solid var(--c-border-inner);
}
.chip-accent {
  background: rgba(79, 195, 247, 0.1);
  color: var(--c-accent);
  border: 1px solid rgba(79, 195, 247, 0.2);
}

/* ── Expand button ─────────────────────────────────────────── */
.detail-expand-btn {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  background: none;
  border: none;
  color: var(--c-accent);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.2s;
}
.detail-expand-btn:hover {
  opacity: 0.8;
}
.expand-chevron {
  transition: transform 0.2s;
}
.expand-chevron.open {
  transform: rotate(180deg);
}

/* ── Expandable table placeholder ──────────────────────────── */
.detail-table-wrap {
  overflow: hidden;
}
.detail-note {
  font-size: 0.75rem;
  color: var(--c-text-dim);
  font-style: italic;
  padding: 0.5rem 0;
  border-top: 1px solid var(--c-border-row);
}

/* ── Transitions ───────────────────────────────────────────── */
.slide-enter-active,
.slide-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.expand-enter-active,
.expand-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s;
  max-height: 200px;
}
.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
