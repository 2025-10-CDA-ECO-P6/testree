import { ref, computed } from 'vue'
import type { TreeNode } from '@/fixtures/testTree'

export function useTreeNavigation(root: TreeNode) {
  // Le nœud "courant" = celui dans lequel on a zoomé
  const currentNode = ref<TreeNode>(root)

  // Pile de navigation : chaque entrée est un ancêtre
  const breadcrumb = computed<TreeNode[]>(() => {
    const path: TreeNode[] = []
    function findPath(node: TreeNode, target: string, trail: TreeNode[]): boolean {
      trail.push(node)
      if (node.id === target) return true
      for (const child of node.children) {
        if (findPath(child, target, trail)) return true
      }
      trail.pop()
      return false
    }
    findPath(root, currentNode.value.id, path)
    return path
  })

  // Les enfants directs du nœud courant = ce qu'on affiche
  const visibleNodes = computed<TreeNode[]>(() => {
    return currentNode.value.children
  })

  // Drill-down : clic sur un module → on zoome dedans
  function drillDown(node: TreeNode) {
    if (node.type === 'module' && node.children.length > 0) {
      currentNode.value = node
    }
  }

  // Remonter à un ancêtre via le breadcrumb
  function navigateTo(node: TreeNode) {
    currentNode.value = node
  }

  // Retour au niveau parent (un cran en arrière)
  function goUp() {
    const bc = breadcrumb.value
    const parent = bc[bc.length - 2]
    if (parent) {
      currentNode.value = parent
    }
  }

  return {
    currentNode,
    breadcrumb,
    visibleNodes,
    drillDown,
    navigateTo,
    goUp,
  }
}
