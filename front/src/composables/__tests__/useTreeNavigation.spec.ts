import { describe, it, expect } from 'vitest'
import type { TreeNode } from '@/fixtures/testTree'
import { useTreeNavigation } from '../useTreeNavigation'

// Vue enveloppe les nœuds dans un Proxy réactif : on compare donc les identifiants,
// pas les références d'objets.
const ids = (nodes: TreeNode[]) => nodes.map((n) => n.id)

function scriptNode(id: string): TreeNode {
  return { id, label: id.split('.').pop() ?? id, fullPath: id, type: 'script', children: [] }
}

function moduleNode(id: string, children: TreeNode[]): TreeNode {
  return { id, label: id.split('.').pop() ?? id, fullPath: id, type: 'module', children }
}

// projet
// ├── visionneuse
// │   ├── visionneuse.Ouverture
// │   └── visionneuse.zoom
// │       └── visionneuse.zoom.ZoomAvant
// └── publipostage
//     └── publipostage.Envoi
function buildTree() {
  const zoom = moduleNode('visionneuse.zoom', [scriptNode('visionneuse.zoom.ZoomAvant')])
  const visionneuse = moduleNode('visionneuse', [scriptNode('visionneuse.Ouverture'), zoom])
  const publipostage = moduleNode('publipostage', [scriptNode('publipostage.Envoi')])
  const root = moduleNode('projet', [visionneuse, publipostage])
  return { root, visionneuse, zoom }
}

describe('useTreeNavigation', () => {
  it('démarre à la racine et affiche ses enfants directs', () => {
    const { root } = buildTree()
    const nav = useTreeNavigation(root)

    expect(nav.currentNode.value.id).toBe('projet')
    expect(ids(nav.visibleNodes.value)).toEqual(['visionneuse', 'publipostage'])
    expect(ids(nav.breadcrumb.value)).toEqual(['projet'])
  })

  it('descend dans un module et met à jour le fil d’Ariane', () => {
    const { root, visionneuse, zoom } = buildTree()
    const nav = useTreeNavigation(root)

    nav.drillDown(visionneuse)
    nav.drillDown(zoom)

    expect(nav.currentNode.value.id).toBe('visionneuse.zoom')
    expect(ids(nav.breadcrumb.value)).toEqual(['projet', 'visionneuse', 'visionneuse.zoom'])
  })

  it('ne descend pas dans un script ni dans un module vide', () => {
    const { root } = buildTree()
    const nav = useTreeNavigation(root)

    nav.drillDown(scriptNode('visionneuse.Ouverture'))
    nav.drillDown(moduleNode('vide', []))

    expect(nav.currentNode.value.id).toBe('projet')
  })

  it('remonte d’un niveau avec goUp, et reste à la racine si on y est déjà', () => {
    const { root, visionneuse, zoom } = buildTree()
    const nav = useTreeNavigation(root)

    nav.goUp()
    expect(nav.currentNode.value.id).toBe('projet')

    nav.drillDown(visionneuse)
    nav.drillDown(zoom)
    nav.goUp()
    expect(nav.currentNode.value.id).toBe('visionneuse')
  })

  it('saute directement à un ancêtre via le fil d’Ariane', () => {
    const { root, visionneuse, zoom } = buildTree()
    const nav = useTreeNavigation(root)

    nav.drillDown(visionneuse)
    nav.drillDown(zoom)
    nav.navigateTo(root)

    expect(nav.currentNode.value.id).toBe('projet')
    expect(ids(nav.breadcrumb.value)).toEqual(['projet'])
  })
})
