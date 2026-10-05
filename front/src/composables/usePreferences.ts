import { ref, watch } from 'vue'

const STORAGE_KEY = 'testree-prefs'

function load(): Record<string, unknown> {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

function save(prefs: Record<string, unknown>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

/**
 * Préférence persistée en localStorage.
 * Chaque appel retourne un ref réactif lié à une clé.
 * Quand le ref change, la valeur est sauvegardée automatiquement.
 *
 * Usage : const locked = usePreference('lock', true)
 */
export function usePreference<T>(key: string, defaultValue: T) {
  const prefs = load()
  const value = ref<T>(key in prefs ? (prefs[key] as T) : defaultValue) as ReturnType<typeof ref<T>>

  watch(value, (v) => {
    const current = load()
    current[key] = v
    save(current)
  }, { deep: true })

  return value
}
