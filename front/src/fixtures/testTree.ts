// ============================================================
// Types — réutilisés par les composants et le composable
// ============================================================

export type ScriptStatus = 'PASS' | 'FAIL' | 'SKIP' | 'ERROR'

export type ConfidenceLevel = 'green' | 'orange' | 'red'

export interface RunResult {
  date: string // ISO 8601
  status: ScriptStatus
  durationMs: number
  jenkinsRunId: string
}

export interface TreeNode {
  id: string // ex: "GestionPatient.Admission.CreerDossier"
  label: string // ex: "CreerDossier"
  fullPath: string // même que id, sert pour le breadcrumb
  type: 'module' | 'script'
  children: TreeNode[]
  // Présent uniquement sur les scripts (feuilles)
  runs?: RunResult[]
  confidence?: ConfidenceLevel
  flaky?: boolean
  lastAtsModif?: string // date ISO — dernière modif GitLab du .ats
}

// ============================================================
// Helpers
// ============================================================

function computeConfidence(
  runs: RunResult[],
  flaky: boolean,
  lastAtsModif?: string,
): ConfidenceLevel {
  const recent = runs.slice(0, 5) // les 5 derniers, triés du plus récent au plus ancien
  const lastRun = recent[0]
  if (!lastRun) return 'orange'
  const lastStatus = lastRun.status

  if (lastStatus === 'PASS') return 'green'
  if (lastStatus === 'SKIP') return 'orange'

  // Le test est en échec — on détermine rouge ou orange
  const consecutiveFails = recent.filter((r) => r.status === 'FAIL' || r.status === 'ERROR').length
  const recentAtsModif =
    lastAtsModif && Date.now() - new Date(lastAtsModif).getTime() < 7 * 24 * 3600 * 1000

  if (consecutiveFails >= 3 && !flaky && !recentAtsModif) return 'red'
  return 'orange'
}

// ============================================================
// Données mock — ~30 scripts HCM répartis en modules variés
// ============================================================

const now = Date.now()
const day = 24 * 3600 * 1000

function mockRuns(...statuses: ScriptStatus[]): RunResult[] {
  return statuses.map((status, i) => ({
    date: new Date(now - i * day).toISOString(),
    status,
    durationMs: 3000 + Math.floor(Math.random() * 12000),
    jenkinsRunId: `build-${400 - i}`,
  }))
}

function script(
  id: string,
  label: string,
  runs: RunResult[],
  opts: { flaky?: boolean; lastAtsModif?: string } = {},
): TreeNode {
  return {
    id,
    label,
    fullPath: id,
    type: 'script',
    children: [],
    runs,
    flaky: opts.flaky ?? false,
    lastAtsModif: opts.lastAtsModif,
  }
}

function mod(id: string, label: string, children: TreeNode[]): TreeNode {
  return { id, label, fullPath: id, type: 'module', children }
}

export const testTree: TreeNode = mod('root', 'HCM', [
  // ── GestionPatient ──────────────────────────────────────────
  mod('GestionPatient', 'GestionPatient', [
    mod('GestionPatient.Admission', 'Admission', [
      script(
        'GestionPatient.Admission.CreerDossier',
        'CreerDossier',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'FAIL'),
        { lastAtsModif: new Date(now - 30 * day).toISOString() },
      ),
      script(
        'GestionPatient.Admission.RechercherPatient',
        'RechercherPatient',
        mockRuns('FAIL', 'FAIL', 'FAIL', 'FAIL', 'PASS'),
        { lastAtsModif: new Date(now - 45 * day).toISOString() },
      ),
      script(
        'GestionPatient.Admission.ModifierIdentite',
        'ModifierIdentite',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Admission.FusionnerDoublons',
        'FusionnerDoublons',
        mockRuns('FAIL', 'PASS', 'FAIL', 'PASS', 'FAIL'),
        { flaky: true, lastAtsModif: new Date(now - 5 * day).toISOString() },
      ),
    ]),
    mod('GestionPatient.Sortie', 'Sortie', [
      script(
        'GestionPatient.Sortie.ValiderSortie',
        'ValiderSortie',
        mockRuns('PASS', 'PASS', 'FAIL', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Sortie.AnnulerSortie',
        'AnnulerSortie',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Sortie.EditerBulletinSortie',
        'EditerBulletinSortie',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Sortie.ImprimerCompteRendu',
        'ImprimerCompteRendu',
        mockRuns('FAIL', 'FAIL', 'FAIL', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 20 * day).toISOString() },
      ),
      script(
        'GestionPatient.Sortie.NotifierMedecinTraitant',
        'NotifierMedecinTraitant',
        mockRuns('PASS', 'FAIL', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Sortie.CloturerDossier',
        'CloturerDossier',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Sortie.GenererFactureSortie',
        'GenererFactureSortie',
        mockRuns('FAIL', 'PASS', 'FAIL', 'PASS'),
        { flaky: true },
      ),
    ]),
    mod('GestionPatient.Transfert', 'Transfert', [
      script(
        'GestionPatient.Transfert.TransfererService',
        'TransfererService',
        mockRuns('PASS', 'FAIL', 'PASS', 'PASS'),
      ),
      script(
        'GestionPatient.Transfert.TransfererLit',
        'TransfererLit',
        mockRuns('ERROR', 'ERROR', 'FAIL', 'FAIL', 'PASS'),
        { lastAtsModif: new Date(now - 50 * day).toISOString() },
      ),
    ]),
  ]),

  // ── Facturation ─────────────────────────────────────────────
  mod('Facturation', 'Facturation', [
    mod('Facturation.Cotation', 'Cotation', [
      script(
        'Facturation.Cotation.CoterActe',
        'CoterActe',
        mockRuns('FAIL', 'PASS', 'PASS', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 10 * day).toISOString() },
      ),
      script(
        'Facturation.Cotation.ModifierCotation',
        'ModifierCotation',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'Facturation.Cotation.SupprimerCotation',
        'SupprimerCotation',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Facturation.Cotation.ValiderCotation',
        'ValiderCotation',
        mockRuns('PASS', 'PASS', 'FAIL', 'PASS'),
      ),
      script(
        'Facturation.Cotation.ConsulterHistorique',
        'ConsulterHistorique',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'Facturation.Cotation.ExporterCotations',
        'ExporterCotations',
        mockRuns('ERROR', 'FAIL', 'FAIL', 'FAIL', 'PASS'),
        { lastAtsModif: new Date(now - 35 * day).toISOString() },
      ),
    ]),
    mod('Facturation.Remboursement', 'Remboursement', [
      script(
        'Facturation.Remboursement.GenererBordereau',
        'GenererBordereau',
        mockRuns('FAIL', 'PASS', 'FAIL', 'PASS', 'FAIL'),
        { flaky: true, lastAtsModif: new Date(now - 3 * day).toISOString() },
      ),
      script(
        'Facturation.Remboursement.ValiderRemboursement',
        'ValiderRemboursement',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS'),
      ),
    ]),
    mod('Facturation.Tiers', 'Tiers', [
      script(
        'Facturation.Tiers.RechercherTiers',
        'RechercherTiers',
        mockRuns('PASS', 'PASS', 'FAIL', 'PASS'),
      ),
      script(
        'Facturation.Tiers.CreerTiers',
        'CreerTiers',
        mockRuns('FAIL', 'FAIL', 'FAIL', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 40 * day).toISOString() },
      ),
    ]),
  ]),

  // ── Prescription ────────────────────────────────────────────
  mod('Prescription', 'Prescription', [
    mod('Prescription.Medicament', 'Medicament', [
      script(
        'Prescription.Medicament.AjouterPrescription',
        'AjouterPrescription',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Prescription.Medicament.ControlerInteraction',
        'ControlerInteraction',
        mockRuns('ERROR', 'FAIL', 'FAIL', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 60 * day).toISOString() },
      ),
      script(
        'Prescription.Medicament.ModifierPosologie',
        'ModifierPosologie',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
    ]),
    mod('Prescription.Biologie', 'Biologie', [
      script(
        'Prescription.Biologie.PrescrireAnalyse',
        'PrescrireAnalyse',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'Prescription.Biologie.ConsulterResultats',
        'ConsulterResultats',
        mockRuns('FAIL', 'PASS', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 2 * day).toISOString() },
      ),
    ]),
  ]),

  // ── Planning ────────────────────────────────────────────────
  mod('Planning', 'Planning', [
    mod('Planning.RendezVous', 'RendezVous', [
      script(
        'Planning.RendezVous.PrendreRDV',
        'PrendreRDV',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Planning.RendezVous.AnnulerRDV',
        'AnnulerRDV',
        mockRuns('SKIP', 'SKIP', 'PASS', 'PASS'),
      ),
      script(
        'Planning.RendezVous.DeplacerRDV',
        'DeplacerRDV',
        mockRuns('PASS', 'FAIL', 'PASS', 'PASS'),
      ),
    ]),
    mod('Planning.Occupation', 'Occupation', [
      script(
        'Planning.Occupation.ConsulterTauxOccupation',
        'ConsulterTauxOccupation',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Planning.Occupation.GererLits',
        'GererLits',
        mockRuns('FAIL', 'FAIL', 'FAIL', 'FAIL', 'FAIL'),
        { lastAtsModif: new Date(now - 90 * day).toISOString() },
      ),
    ]),
  ]),

  // ── Reporting ───────────────────────────────────────────────
  mod('Reporting', 'Reporting', [
    mod('Reporting.Statistiques', 'Statistiques', [
      script(
        'Reporting.Statistiques.GenererRapportActivite',
        'GenererRapportActivite',
        mockRuns('FAIL', 'FAIL', 'PASS', 'PASS'),
        { lastAtsModif: new Date(now - 1 * day).toISOString() },
      ),
      script('Reporting.Statistiques.ExporterCSV', 'ExporterCSV', mockRuns('PASS', 'PASS', 'PASS')),
      script(
        'Reporting.Statistiques.GenererTableauDeBord',
        'GenererTableauDeBord',
        mockRuns('PASS', 'FAIL', 'PASS', 'PASS'),
      ),
    ]),
    mod('Reporting.Audit', 'Audit', [
      script(
        'Reporting.Audit.ConsulterJournal',
        'ConsulterJournal',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Reporting.Audit.FiltrerEvenements',
        'FiltrerEvenements',
        mockRuns('FAIL', 'PASS', 'FAIL', 'PASS'),
        { flaky: true },
      ),
    ]),
  ]),

  // ── Administration ──────────────────────────────────────────
  mod('Administration', 'Administration', [
    mod('Administration.Utilisateur', 'Utilisateur', [
      script(
        'Administration.Utilisateur.CreerCompte',
        'CreerCompte',
        mockRuns('PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
      ),
      script(
        'Administration.Utilisateur.ModifierDroits',
        'ModifierDroits',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'Administration.Utilisateur.DesactiverCompte',
        'DesactiverCompte',
        mockRuns('PASS', 'FAIL', 'PASS', 'PASS'),
      ),
    ]),
    mod('Administration.Parametrage', 'Parametrage', [
      script(
        'Administration.Parametrage.ConfigurerEtablissement',
        'ConfigurerEtablissement',
        mockRuns('PASS', 'PASS', 'PASS'),
      ),
      script(
        'Administration.Parametrage.GererReferentiels',
        'GererReferentiels',
        mockRuns('FAIL', 'FAIL', 'FAIL', 'FAIL'),
        { lastAtsModif: new Date(now - 25 * day).toISOString() },
      ),
    ]),
  ]),
])

// ============================================================
// Applique computeConfidence à toutes les feuilles
// ============================================================

function applyConfidence(node: TreeNode): void {
  if (node.type === 'script' && node.runs) {
    node.confidence = computeConfidence(node.runs, node.flaky ?? false, node.lastAtsModif)
  }
  node.children.forEach(applyConfidence)
}

applyConfidence(testTree)
