<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { TreeNode } from '@/fixtures/testTree'

const props = defineProps<{
  data: {
    node: TreeNode
    onDrillDown?: () => void
    onSelect?: () => void
    isParent?: boolean
  }
}>()

function handleClick() {
  if (props.data.onDrillDown) {
    props.data.onDrillDown()
  } else if (props.data.onSelect) {
    props.data.onSelect()
  }
}

const counts = computed(() => {
  const scripts: TreeNode[] = []
  function collect(n: TreeNode) {
    if (n.type === 'script') scripts.push(n)
    n.children.forEach(collect)
  }
  collect(props.data.node)
  return {
    total: scripts.length,
    green: scripts.filter((s) => s.confidence === 'green').length,
    orange: scripts.filter((s) => s.confidence === 'orange').length,
    red: scripts.filter((s) => s.confidence === 'red').length,
  }
})
</script>

<template>
  <div class="module-node" :class="{ 'is-parent': data.isParent }" @click="handleClick">
    <Handle type="target" :position="Position.Top" id="target-top" />
    <Handle type="source" :position="Position.Top" id="source-top" />

    <button
      v-if="data.onSelect"
      class="icon-btn stats-btn"
      @click.stop="data.onSelect()"
      title="Statistiques du module"
    >
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <rect x="1" y="8" width="2.5" height="5" rx="0.5" fill="currentColor" opacity="0.6" />
        <rect x="5.25" y="4" width="2.5" height="9" rx="0.5" fill="currentColor" opacity="0.8" />
        <rect x="9.5" y="1" width="2.5" height="12" rx="0.5" fill="currentColor" />
      </svg>
    </button>

    <div class="module-label">{{ data.node.label }}</div>
    <div class="module-counts">
      <span class="count st-green">{{ counts.green }}</span>
      <span class="count st-orange">{{ counts.orange }}</span>
      <span class="count st-red">{{ counts.red }}</span>
    </div>

    <Handle type="source" :position="Position.Bottom" id="source-bottom" />
    <Handle type="target" :position="Position.Bottom" id="target-bottom" />
  </div>
</template>

<style scoped>
.module-node {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: 1.375rem 2.125rem;
  min-width: 16.25rem;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
  text-align: center;
  position: relative;
}
.module-node:hover {
  border-color: var(--c-accent);
  box-shadow: 0 0 14px var(--c-accent-glow);
}

.module-node.is-parent {
  background: var(--c-surface-alt);
  border-color: var(--c-border-strong);
  box-shadow: 0 0 12px var(--c-accent-glow-dim);
  cursor: default;
}

/* stats-btn — positionné, visible au hover */
.stats-btn {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 1.75rem;
  height: 1.75rem;
  opacity: 0;
}
.module-node:hover .stats-btn {
  opacity: 1;
}

.module-label {
  color: var(--c-text);
  font-weight: 600;
  font-size: 1rem;
  margin-bottom: 0.75rem;
}
.module-counts {
  display: flex;
  justify-content: center;
  gap: 0.625rem;
}
.count {
  width: 2rem;
  height: 2rem;
  line-height: 2rem;
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8125rem;
  font-weight: 700;
}
</style>
