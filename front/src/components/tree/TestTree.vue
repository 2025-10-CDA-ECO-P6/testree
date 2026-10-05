<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@/assets/tree-tokens.css'

import ModuleNode from '@/components/tree/ModuleNode.vue'
import ScriptNode from '@/components/tree/ScriptNode.vue'
import ScriptDetail from '@/components/tree/ScriptDetail.vue'
import { testTree } from '@/fixtures/testTree'
import type { TreeNode } from '@/fixtures/testTree'
import { useTreeNavigation } from '@/composables/useTreeNavigation'

// ── Types ───────────────────────────────────────────────────
type VfNode = {
  id: string
  type: string
  data: {
    node: TreeNode
    onDrillDown?: () => void
    onSelect?: () => void
    isParent?: boolean
  }
  position: { x: number; y: number }
  class?: string
}
type VfEdge = {
  id: string
  source: string
  target: string
  type: string
  style: Record<string, string>
  sourceHandle?: string
  targetHandle?: string
}

// ── State ───────────────────────────────────────────────────
const { currentNode, breadcrumb, visibleNodes, drillDown, navigateTo } =
  useTreeNavigation(testTree)

const nodes = ref<VfNode[]>([])
const edges = ref<VfEdge[]>([])
const isAnimating = ref(false)

// Detail panel (works for both scripts and modules)
const selectedScript = ref<TreeNode | null>(null)
const showDetail = ref(false)
const savedBeforeDetail = ref<{ nodes: VfNode[]; edges: VfEdge[] } | null>(null)

// Sync pulse
const pulseSync = `${-(Date.now() % 1500)}ms`

const { fitView, setCenter } = useVueFlow()

// ── Base helpers ───────────────────────────────────────────
function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms))
}

const edgeStyle: Record<string, string> = { stroke: 'var(--c-edge)', strokeWidth: '2' }

function findNode(id: string, node: TreeNode = testTree): TreeNode | null {
  if (node.id === id) return node
  for (const child of node.children) {
    const found = findNode(id, child)
    if (found) return found
  }
  return null
}

// ── Layout haut / bas — symétrique autour du parent ────────
const MODULE_W = 280
const MODULE_H = 100
const SCRIPT_W = 260
const SCRIPT_H = 70
const GAP_X = 50
const GAP_Y = 90

const aboveChildIds = new Set<string>()

function nodeW(n: VfNode) { return n.type === 'module' ? MODULE_W : SCRIPT_W }
function nodeH(n: VfNode) { return n.type === 'module' ? MODULE_H : SCRIPT_H }

function centerRow(row: VfNode[], y: number) {
  const totalW = row.reduce((s, c) => s + nodeW(c), 0) + (row.length - 1) * GAP_X
  let x = -totalW / 2
  for (const c of row) {
    c.position = { x, y }
    x += nodeW(c) + GAP_X
  }
}

function layoutNodes(vfNodes: VfNode[]) {
  aboveChildIds.clear()
  const [parent, ...children] = vfNodes
  if (!parent) return
  if (children.length === 0) {
    parent.position = { x: 0, y: 0 }
    return
  }

  const n = children.length

  if (n <= 3) {
    centerRow(children, MODULE_H + GAP_Y)
    parent.position = { x: -MODULE_W / 2, y: 0 }
  } else {
    const aboveCount = Math.floor(n / 2)
    const aboveChildren = children.slice(0, aboveCount)
    const belowChildren = children.slice(aboveCount)

    for (const c of aboveChildren) aboveChildIds.add(c.id)

    const aboveH = Math.max(...aboveChildren.map(nodeH))
    centerRow(aboveChildren, -(aboveH + GAP_Y))
    centerRow(belowChildren, MODULE_H + GAP_Y)

    parent.position = { x: -MODULE_W / 2, y: 0 }
  }
}

// ── Skip : saute les modules à enfant unique ──────────────
function resolveTarget(node: TreeNode): TreeNode {
  let target = node
  while (target.type === 'module' && target.children.length === 1) {
    const onlyChild = target.children[0]
    if (!onlyChild || onlyChild.type !== 'module' || onlyChild.children.length === 0) break
    target = onlyChild
  }
  return target
}

// ═══════════════════════════════════════════════════════════
// Animation atomics — chaque helper fait UNE chose
// ═══════════════════════════════════════════════════════════

/** Applique une classe CSS à un nœud */
function setNodeClass(id: string, cls: string) {
  nodes.value = nodes.value.map((n) =>
    n.id === id ? { ...n, class: cls } : n,
  )
}

/** Rend un edge visible (avec transition) */
function showEdge(targetId: string) {
  edges.value = edges.value.map((e) =>
    e.target === targetId
      ? { ...e, style: { ...edgeStyle, opacity: '1', transition: 'opacity 0.3s ease' } }
      : e,
  )
}

/** Cache tous les edges (avec transition) */
function hideAllEdges() {
  edges.value = edges.value.map((e) => ({
    ...e,
    style: { ...edgeStyle, opacity: '0', transition: 'opacity 0.3s ease' },
  }))
}

/** Fade out tout sauf keepId (si fourni), cache les edges */
async function fadeOutSiblings(keepId?: string, delay = 350) {
  nodes.value = nodes.value.map((n) => ({
    ...n,
    class: keepId && n.id === keepId ? '' : 'node-exit',
  }))
  hideAllEdges()
  await sleep(delay)
}

/** Cascade in : révèle des nœuds un par un avec leurs edges */
async function cascadeIn(nodeIds: string[], delay = 100) {
  for (const id of nodeIds) {
    setNodeClass(id, 'node-enter')
    showEdge(id)
    await sleep(delay)
  }
}

/** Cascade out : cache des nœuds un par un en ordre inverse */
async function cascadeOut(nodeIds: string[], delay = 80) {
  for (const id of [...nodeIds].reverse()) {
    setNodeClass(id, 'node-exit')
    edges.value = edges.value.map((e) =>
      e.target === id
        ? { ...e, style: { ...edgeStyle, opacity: '0', transition: 'opacity 0.2s ease' } }
        : e,
    )
    await sleep(delay)
  }
}

/** Place parent + enfants en hidden, edges invisibles (prêt pour cascade) */
function setupHiddenLevel(parent: VfNode, children: VfNode[], levelEdges: VfEdge[]) {
  nodes.value = [
    { ...parent, class: 'node-hidden' },
    ...children.map((n) => ({ ...n, class: 'node-hidden' })),
  ]
  edges.value = levelEdges.map((e) => ({ ...e, style: { ...edgeStyle, opacity: '0' } }))
}

/** Construit un niveau complet : parent + enfants + layout + edges */
function buildLevel(target: TreeNode, children: TreeNode[]) {
  const parent = makeParentNode(target)
  const childNodes = children.map((c) => makeChildNode(c))
  layoutNodes([parent, ...childNodes])
  const levelEdges = children.map((c) => makeEdge(target.id, c.id))
  return { parent, childNodes, levelEdges }
}

/** Fin d'animation : fitView, reset classes, débloque, pushState */
async function finishAnimation(pushNodeId?: string) {
  await sleep(200)
  fitView({ duration: 300 })
  await sleep(300)
  nodes.value = nodes.value.map((n) => ({ ...n, class: '' }))
  isAnimating.value = false
  if (pushNodeId) history.pushState({ nodeId: pushNodeId }, '')
}

// ═══════════════════════════════════════════════════════════
// Node factories
// ═══════════════════════════════════════════════════════════

function makeChildNode(child: TreeNode): VfNode {
  const isModule = child.type === 'module' && child.children.length > 0
  return {
    id: child.id,
    type: child.type === 'script' ? 'script' : 'module',
    data: {
      node: child,
      isParent: false,
      ...(isModule ? { onDrillDown: () => animatedDrillDown(child) } : {}),
      onSelect: () => animatedSelectNode(child),
    },
    position: { x: 0, y: 0 },
  }
}

function makeParentNode(node: TreeNode): VfNode {
  return {
    id: node.id,
    type: 'module',
    data: { node, isParent: true, onSelect: () => animatedSelectNode(node) },
    position: { x: 0, y: 0 },
  }
}

function makeEdge(parentId: string, childId: string): VfEdge {
  const above = aboveChildIds.has(childId)
  return {
    id: `${parentId}->${childId}`,
    source: parentId,
    target: childId,
    type: 'smoothstep',
    style: { ...edgeStyle },
    sourceHandle: above ? 'source-top' : 'source-bottom',
    targetHandle: above ? 'target-bottom' : 'target-top',
  }
}

// ═══════════════════════════════════════════════════════════
// Animations — orchestration via les atomics ci-dessus
// ═══════════════════════════════════════════════════════════

// ── Drill-down ─────────────────────────────────────────────
async function animatedDrillDown(child: TreeNode) {
  if (isAnimating.value) return
  isAnimating.value = true

  const target = resolveTarget(child)
  const clickedVf = nodes.value.find((n) => n.id === child.id)!
  const savedPos = { x: clickedVf.position.x, y: clickedVf.position.y }

  // Phase 1 — Fade out frères (le cliqué reste)
  await fadeOutSiblings(child.id)

  // Phase 2 — Changer de niveau, positionner depuis le nœud cliqué
  drillDown(target)
  const { parent: layoutParent, childNodes: layoutChildren, levelEdges } =
    buildLevel(target, visibleNodes.value)

  const dx = savedPos.x - layoutParent.position.x
  const dy = savedPos.y - layoutParent.position.y

  nodes.value = [
    { ...layoutParent, position: savedPos, class: '' },
    ...layoutChildren.map((c) => ({
      ...c,
      position: { x: c.position.x + dx, y: c.position.y + dy },
      class: 'node-hidden',
    })),
  ]
  edges.value = levelEdges.map((e) => ({ ...e, style: { ...edgeStyle, opacity: '0' } }))

  await nextTick()
  await sleep(50)

  // Phase 3 — fitView puis cascade
  fitView({ duration: 400 })
  await sleep(500)
  await cascadeIn(visibleNodes.value.map((c) => c.id))

  await finishAnimation(currentNode.value.id)
}

// ── Retour — le parent ne disparaît JAMAIS, il glisse ──────
async function animatedNavigateTo(target: TreeNode, pushHistory = true) {
  if (isAnimating.value) return
  const resolved = resolveTarget(target)
  if (resolved.id === currentNode.value.id) return
  isAnimating.value = true

  const oldParentId = currentNode.value.id
  const oldParentNode = nodes.value.find((n) => n.id === oldParentId)!

  // Phase 1 — Cascade OUT des enfants
  const childIds = nodes.value.filter((n) => n.id !== oldParentId).map((n) => n.id)
  await cascadeOut(childIds)
  await sleep(250)

  // Phase 2 — Changer de niveau
  const savedOldPos = { x: oldParentNode.position.x, y: oldParentNode.position.y }
  navigateTo(resolved)
  const newChildren = visibleNodes.value
  const oldParentIsChild = newChildren.some((c) => c.id === oldParentId)

  const { parent: newParent, childNodes: newChildNodes, levelEdges: newEdges } =
    buildLevel(resolved, newChildren)

  if (oldParentIsChild) {
    // L'ancien parent devient enfant — il glisse vers sa nouvelle position
    const oldParentTarget = newChildNodes.find((n) => n.id === oldParentId)!

    nodes.value = [
      { ...newParent, class: 'node-hidden' },
      ...newChildNodes.map((c) =>
        c.id === oldParentId
          ? { ...c, position: { ...savedOldPos }, class: '' }
          : { ...c, class: 'node-hidden' },
      ),
    ]
    edges.value = newEdges.map((e) => ({ ...e, style: { ...edgeStyle, opacity: '0' } }))

    await nextTick()
    await sleep(50)

    // Glisser l'ancien parent + révéler le nouveau
    setNodeClass(oldParentId, 'node-transition')
    await nextTick()

    nodes.value = nodes.value.map((n) => {
      if (n.id === oldParentId)
        return { ...n, position: { ...oldParentTarget.position }, class: 'node-transition' }
      if (n.id === resolved.id)
        return { ...n, class: 'node-enter' }
      return n
    })
    showEdge(oldParentId)

    fitView({ duration: 500 })
    await sleep(550)

    // Cascade in des frères restants
    const siblingIds = newChildren.filter((c) => c.id !== oldParentId).map((c) => c.id)
    await cascadeIn(siblingIds)
  } else {
    // Pas de lien — vanish + fresh cascade
    nodes.value = [{ ...oldParentNode, class: 'node-exit' }]
    edges.value = []
    await sleep(300)

    setupHiddenLevel(newParent, newChildNodes, newEdges)
    await nextTick()
    await sleep(50)
    fitView({ duration: 400 })
    await sleep(500)

    setNodeClass(resolved.id, 'node-enter')
    await sleep(120)
    await cascadeIn(newChildren.map((c) => c.id))
  }

  await finishAnimation(pushHistory ? resolved.id : undefined)
}

// ── Sélection node — zoom viewport 1.5x centré sur le node ──
async function animatedSelectNode(targetNode: TreeNode) {
  if (isAnimating.value || showDetail.value) return
  isAnimating.value = true

  // Sauvegarder l'état pour restauration
  savedBeforeDetail.value = {
    nodes: nodes.value.map((n) => ({ ...n, position: { ...n.position } })),
    edges: edges.value.map((e) => ({ ...e, style: { ...e.style } })),
  }

  const clickedId = targetNode.id
  const clickedVf = nodes.value.find((n) => n.id === clickedId)!
  const nw = targetNode.type === 'script' ? SCRIPT_W : MODULE_W
  const nh = targetNode.type === 'script' ? SCRIPT_H : MODULE_H

  // Phase 1 — Fade out frères (le cliqué reste)
  await fadeOutSiblings(clickedId)

  // Phase 2 — Isoler le nœud (pas de class spéciale, pas de déplacement)
  nodes.value = nodes.value
    .filter((n) => n.id === clickedId)
    .map((n) => ({ ...n, class: '' }))
  edges.value = []

  await nextTick()
  await sleep(50)

  // Phase 3 — Zoom 1.5x, décalé vers le haut pour laisser place à la popup
  const cx = clickedVf.position.x + nw / 2
  const cy = clickedVf.position.y + nh / 2 + 180
  setCenter(cx, cy, { zoom: 1.5, duration: 500 })
  await sleep(550)

  // Phase 4 — Afficher le panel
  selectedScript.value = targetNode
  showDetail.value = true

  await sleep(400)
  isAnimating.value = false
}

// ── Fermer le panel — dé-scale + cascade retour ───────────
async function closeDetail() {
  if (isAnimating.value || !showDetail.value) return
  isAnimating.value = true

  // Phase 1 — Masquer le panel
  showDetail.value = false
  await sleep(400)
  selectedScript.value = null

  if (savedBeforeDetail.value) {
    const { nodes: savedNodes, edges: savedEdges } = savedBeforeDetail.value
    const clickedId = nodes.value[0]?.id

    if (clickedId) {
      // Phase 2 — Restaurer tous les nœuds : cliqué visible, reste caché
      nodes.value = savedNodes.map((n) =>
        n.id === clickedId
          ? { ...n, class: '' }
          : { ...n, class: 'node-hidden' },
      )
      edges.value = savedEdges.map((e) => ({
        ...e,
        style: { ...edgeStyle, opacity: '0' },
      }))

      await nextTick()
      await sleep(50)

      // Phase 3 — fitView pour tout afficher
      fitView({ duration: 500 })
      await sleep(550)

      // Phase 4 — Cascade in : parent d'abord (sauf si c'est le cliqué), puis frères
      const parent = savedNodes.find((n) => n.data.isParent)
      if (parent && parent.id !== clickedId) {
        setNodeClass(parent.id, 'node-enter')
        showEdge(clickedId)
        await sleep(120)
      }

      const siblingIds = savedNodes
        .filter((n) => n.id !== clickedId && !n.data.isParent)
        .map((n) => n.id)
      await cascadeIn(siblingIds, 80)
    }

    savedBeforeDetail.value = null
  }

  await finishAnimation()
}

// ── Breadcrumb avec panel ouvert — vanish + navigation ──────
async function closeDetailAndNavigate(target: TreeNode) {
  if (isAnimating.value) return
  isAnimating.value = true

  // Vanish tout : panel + nœuds
  showDetail.value = false
  await fadeOutSiblings(undefined, 400)

  selectedScript.value = null
  savedBeforeDetail.value = null

  // Construire le nouveau niveau
  const resolved = resolveTarget(target)
  navigateTo(resolved)
  const { parent: newParent, childNodes, levelEdges } =
    buildLevel(resolved, visibleNodes.value)

  setupHiddenLevel(newParent, childNodes, levelEdges)
  await nextTick()
  fitView({ duration: 400 })
  await sleep(500)

  // Cascade in : parent d'abord, puis enfants
  setNodeClass(newParent.id, 'node-enter')
  await sleep(120)
  await cascadeIn(visibleNodes.value.map((c) => c.id))

  await finishAnimation(resolved.id)
}

// ── Bouton retour navigateur + Escape ───────────────────────
function onPopState(event: PopStateEvent) {
  const nodeId = event.state?.nodeId ?? testTree.id
  const target = findNode(nodeId)
  if (target && target.id !== currentNode.value.id) {
    if (showDetail.value) {
      closeDetailAndNavigate(target)
    } else {
      animatedNavigateTo(target, false)
    }
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && showDetail.value) {
    closeDetail()
  }
}

onMounted(() => {
  history.replaceState({ nodeId: currentNode.value.id }, '')
  window.addEventListener('popstate', onPopState)
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  window.removeEventListener('popstate', onPopState)
  window.removeEventListener('keydown', onKeydown)
})

// ── Init ────────────────────────────────────────────────────
const children = visibleNodes.value
const initParent = makeParentNode(currentNode.value)
const initChildren = children.map((c) => makeChildNode(c))
layoutNodes([initParent, ...initChildren])
const initEdges = children.map((c) => makeEdge(currentNode.value.id, c.id))
nodes.value = [initParent, ...initChildren]
edges.value = initEdges
</script>

<template>
  <div class="tree-wrapper" :class="{ 'popup-active': showDetail }" :style="{ '--pulse-sync': pulseSync }">
    <nav class="breadcrumb">
      <span
        v-for="(crumb, i) in breadcrumb"
        :key="crumb.id"
        class="crumb"
        :class="{
          active: i === breadcrumb.length - 1,
          skipped: i < breadcrumb.length - 1 && resolveTarget(crumb).id !== crumb.id,
        }"
        @click="
          i < breadcrumb.length - 1 &&
            resolveTarget(crumb).id === crumb.id &&
            (showDetail
              ? closeDetailAndNavigate(crumb)
              : animatedNavigateTo(crumb))
        "
      >
        {{ crumb.label }}
        <span v-if="i < breadcrumb.length - 1" class="separator">/</span>
      </span>
    </nav>

    <div class="tree-container">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :fit-view-on-init="true"
        :default-viewport="{ zoom: 1 }"
        :min-zoom="0.2"
        :max-zoom="2"
        :nodes-draggable="false"
        :zoom-on-scroll="!showDetail && !isAnimating"
        :zoom-on-pinch="!showDetail && !isAnimating"
        :zoom-on-double-click="!showDetail && !isAnimating"
        :pan-on-drag="!showDetail && !isAnimating"
        :pan-on-scroll="!showDetail && !isAnimating"
      >
        <template #node-module="moduleProps">
          <ModuleNode v-bind="moduleProps" />
        </template>
        <template #node-script="scriptProps">
          <ScriptNode v-bind="scriptProps" />
        </template>

        <Background :gap="18" :size="1.5" pattern-color="#252b3d" />
        <Controls v-show="!showDetail" position="bottom-left" />
      </VueFlow>
    </div>

    <!-- Panel détail — popup flottante (script ou module) -->
    <Transition name="slide-up">
      <div v-if="showDetail && selectedScript" class="detail-overlay">
        <ScriptDetail :node="selectedScript" @close="closeDetail" />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.tree-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  background: var(--c-bg);
  position: relative;
}

.breadcrumb {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.25rem;
  padding: 0.875rem 1.5rem;
  background: var(--c-nav);
  border-bottom: 1px solid var(--c-border-subtle);
  flex-shrink: 0;
  z-index: 15;
}
.crumb {
  color: var(--c-text-muted);
  font-size: 0.875rem;
  cursor: pointer;
  transition: color 0.2s;
}
.crumb:hover { color: var(--c-accent); }
.crumb.active { color: var(--c-text); cursor: default; }
.crumb.skipped { color: var(--c-text-faint); cursor: default; }
.crumb.skipped:hover { color: var(--c-text-faint); }
.separator { color: var(--c-border); margin: 0 0.375rem; }

.tree-container { flex: 1; width: 100%; }

/* Cacher les handles */
:deep(.vue-flow__handle) {
  opacity: 0 !important;
  pointer-events: none;
}

/* ── Controls — style custom ────────────────────────────── */
:deep(.vue-flow__controls) {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}
:deep(.vue-flow__controls-button) {
  background: var(--c-surface);
  border: none;
  border-bottom: 1px solid var(--c-border-subtle);
  color: var(--c-text-muted);
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}
:deep(.vue-flow__controls-button:last-child) {
  border-bottom: none;
}
:deep(.vue-flow__controls-button:hover) {
  color: var(--c-accent);
  background: var(--c-accent-glow-sub);
}
:deep(.vue-flow__controls-button svg) {
  fill: currentColor;
}
:deep(.vue-flow__controls-interactive) {
  display: none !important;
}

/* ── Animations VueFlow ──────────────────────────────────── */
:deep(.vue-flow__node.node-exit) {
  opacity: 0 !important;
  transition: opacity 0.3s ease !important;
  pointer-events: none;
}
:deep(.vue-flow__node.node-hidden) {
  opacity: 0 !important;
  pointer-events: none;
}
:deep(.vue-flow__node.node-enter) {
  animation: cascadeIn 0.35s ease forwards !important;
}
:deep(.vue-flow__node.node-transition) {
  transition: transform 0.5s ease !important;
}
/* Popup active : cacher le bouton stats sur les nœuds */
.popup-active :deep(.stats-btn) {
  display: none !important;
}

@keyframes cascadeIn {
  from { opacity: 0; filter: blur(3px); }
  to { opacity: 1; filter: blur(0); }
}

/* ── Popup flottante ─────────────────────────────────────── */
.detail-overlay {
  position: absolute;
  inset: 0;
  top: 3rem; /* sous le breadcrumb */
  z-index: 10;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 0 1.75rem 1.75rem;
  pointer-events: none;
}
.detail-overlay > * {
  pointer-events: auto;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.3s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(1.875rem);
  opacity: 0;
}
</style>
