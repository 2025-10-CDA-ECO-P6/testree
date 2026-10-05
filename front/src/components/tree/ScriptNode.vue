<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { TreeNode } from '@/fixtures/testTree'

defineProps<{
  data: {
    node: TreeNode
    onSelect?: () => void
  }
}>()
</script>

<template>
  <div class="script-node"
    @click="data.onSelect?.()"
    :class="'confidence-' + (data.node.confidence ?? 'orange')">
    <Handle type="target" :position="Position.Top" id="target-top" />
    <Handle type="target" :position="Position.Bottom" id="target-bottom" />

    <div class="script-content">
      <span class="dot" :class="data.node.confidence ?? 'orange'" />
      <span class="script-label">{{ data.node.label }}</span>
      <span v-if="data.node.flaky" class="flaky-badge">flaky</span>
    </div>
  </div>
</template>

<style scoped>
.script-node {
  border: 1px solid;
  border-radius: var(--radius-lg);
  padding: 0.875rem 1.375rem;
  min-width: 12.5rem;
  cursor: pointer;
  transition: filter 0.2s, box-shadow 0.2s;
}
.script-node:hover {
  filter: brightness(1.2);
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.05);
}

.script-node.confidence-green  { background: var(--c-green-bg);  border-color: var(--c-green-border);  }
.script-node.confidence-orange { background: var(--c-orange-bg); border-color: var(--c-orange-border); }
.script-node.confidence-red    { background: var(--c-red-bg);    border-color: var(--c-red-border);    }

.script-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

.dot {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: var(--radius-pill);
  flex-shrink: 0;
  animation: pulse 1.5s ease-in-out infinite;
  animation-delay: var(--pulse-sync);
}
.dot.green  { background: var(--c-green);  --pulse-color: rgba(102, 187, 106, 0.4); }
.dot.orange { background: var(--c-orange); --pulse-color: rgba(255, 167, 38, 0.4);  }
.dot.red    { background: var(--c-red);    --pulse-color: rgba(239, 83, 80, 0.45);  }

.script-label {
  color: var(--c-text);
  font-size: 0.875rem;
  font-weight: 500;
}

/* .flaky-badge hérite de tree-tokens.css (global) */
</style>
