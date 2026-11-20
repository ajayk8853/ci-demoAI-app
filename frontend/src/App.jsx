import { useEffect, useMemo, useState } from 'react'
import { api } from './services/api'
import './App.css'

const demoRuns = [
  { id: 1, runId: 'tekton-build-1', workflowName: 'guardian-api-build', repositoryOwner: 'guardian-ai', repositoryName: 'guardian-ai', branchName: 'main', conclusion: 'success', status: 'completed', durationSeconds: 184, createdAt: '2026-11-20T10:42:00Z' },
  { id: 2, runId: 'tekton-deploy-2', workflowName: 'deploy-production', repositoryOwner: 'guardian-ai', repositoryName: 'guardian-ai', branchName: 'release/aws', conclusion: 'failure', status: 'completed', durationSeconds: 96, createdAt: '2026-11-20T09:18:00Z' },
  { id: 3, runId: 'tekton-test-3', workflowName: 'integration-tests', repositoryOwner: 'guardian-ai', repositoryName: 'guardian-ai', branchName: 'feature/tekton', conclusion: null, status: 'in_progress', durationSeconds: null, createdAt: '2026-11-20T08:55:00Z' },
  { id: 4, runId: 'tekton-lint-4', workflowName: 'frontend-quality', repositoryOwner: 'guardian-ai', repositoryName: 'frontend', branchName: 'main', conclusion: 'success', status: 'completed', durationSeconds: 58, createdAt: '2026-11-19T18:21:00Z' },
]

const demoIssues = [
  { id: 1, errorMessage: 'Docker build exited with code 1', rootCause: 'Application dependency resolution failed during the container build.', occurrences: 3, lastSeen: '2026-11-20T09:18:00Z' },
  { id: 2, errorMessage: 'Kubernetes rollout timed out', rootCause: 'The Deployment did not reach the available replica count before the timeout.', occurrences: 2, lastSeen: '2026-11-19T15:36:00Z' },
]

const demoDashboard = {
  totalRuns: 24,
  successfulRuns: 18,
  failedRuns: 6,
  reliabilityScore: 75,
  recurringIssues: 2,
  recentRuns: demoRuns,
}

const navItems = [
  { id: 'overview', label: 'Overview', icon: 'grid' },
  { id: 'pipelines', label: 'Pipeline Runs', icon: 'pipeline' },
  { id: 'issues', label: 'Issue Patterns', icon: 'warning' },
]

function Icon({ name, size = 18 }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    pipeline: <><circle cx="6" cy="12" r="3"/><circle cx="18" cy="6" r="3"/><circle cx="18" cy="18" r="3"/><path d="M9 12h3M12 12v6h3M12 12V6h3"/></>,
    warning: <><path d="M10.3 3.7 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 3.7a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/></>,
    refresh: <><path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/></>,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    x: <><path d="m6 6 12 12M18 6 6 18"/></>,
    bolt: <path d="m13 2-9 12h7l-1 8 9-12h-7l1-8Z"/>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(Array.isArray(value) ? Date.UTC(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0) : value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function duration(seconds) {
  if (seconds == null) return '—'
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

function StatusBadge({ conclusion, status }) {
  const normalized = (conclusion || status || '').toLowerCase()
  const success = normalized === 'success' || normalized === 'passed'
  const failure = normalized === 'failure' || normalized === 'failed'
  const className = success ? 'success' : failure ? 'failure' : 'running'
  return <span className={`status-badge ${className}`}><span className="status-dot" />{success ? 'Success' : failure ? 'Failed' : 'Running'}</span>
}

function StatCard({ label, value, detail, tone }) {
  return <article className="stat-card">
    <div className={`stat-icon ${tone}`}><Icon name={tone === 'success' ? 'check' : tone === 'warning' ? 'warning' : 'bolt'} /></div>
    <div><p>{label}</p><strong>{value}</strong><span>{detail}</span></div>
  </article>
}

function AnalysisResult({ analysis, onClose }) {
  const comparison = analysis.comparison
  const evidence = Array.isArray(analysis.evidence) ? analysis.evidence : []
  const differences = comparison?.differences || []

  return <article className="analysis-result">
    <div className="analysis-header">
      <div><p>AI FAILURE ANALYSIS</p><h3>{analysis.rootCause || 'Root cause not determined'}</h3></div>
      <button className="close-analysis" onClick={onClose} aria-label="Close analysis"><Icon name="x" /></button>
    </div>

    <div className="analysis-summary">
      <div className="confidence-block">
        <span>Confidence</span>
        <strong>{Math.round(analysis.confidence || 0)}%</strong>
      </div>
      <div className="analysis-meta">
        <span><b>Run</b> #{analysis.runId}</span>
        <span><b>Source</b> {analysis.source || 'local-rule-engine'}</span>
        <span className={analysis.recurringIssue ? 'recurring yes' : 'recurring'}>
          {analysis.recurringIssue ? `Recurring · ${analysis.previousOccurrences} previous` : 'First occurrence'}
        </span>
      </div>
    </div>

    <div className="analysis-grid">
      <section>
        <h4>Evidence</h4>
        {evidence.length ? <ul>{evidence.map((item, index) => <li key={index}>{item}</li>)}</ul> : <p className="muted-text">No detailed evidence was returned.</p>}
      </section>
      <section>
        <h4>Recommended fix</h4>
        <div className="recommendation"><Icon name="bolt" /><p>{analysis.recommendation || 'Reproduce the failure locally and inspect the pipeline logs.'}</p></div>
      </section>
    </div>

    {comparison && <section className="comparison-section">
      <h4>Failure vs. previous success</h4>
      <div className="comparison-grid">
        <div><span>Successful run</span><strong>{comparison.successfulRunId || 'Unavailable'}</strong></div>
        <div><span>Branch</span><strong>{comparison.branchName || '—'}</strong></div>
        <div><span>Duration difference</span><strong>{comparison.durationDifference == null ? '—' : `${comparison.durationDifference >= 0 ? '+' : ''}${comparison.durationDifference}s`}</strong></div>
      </div>
      {differences.length > 0 && <div className="difference-list">{differences.map((item, index) => <span key={index}>{item}</span>)}</div>}
    </section>}
  </article>
}

function App() {
  const [activeNav, setActiveNav] = useState('overview')
  const [dashboard, setDashboard] = useState(null)
  const [runs, setRuns] = useState([])
  const [issues, setIssues] = useState([])
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [analyzing, setAnalyzing] = useState(null)
  const [selectedAnalysis, setSelectedAnalysis] = useState(null)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')

  const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true'

  // Initial load effect with async callback to avoid React data-fetch lint warnings
  useEffect(() => {
    let cancelled = false

    Promise.resolve().then(async () => {
    try {
      if (DEMO_MODE) {
        if (!cancelled) {
          setDashboard(demoDashboard)
          setRuns(demoRuns)
          setIssues(demoIssues)
          setLoading(false)
        }
        return
      }

      const [dashboardRes, runsRes, issuesRes] = await Promise.all([
        api.getDashboard(), api.getRuns(), api.getIssues(),
      ])

      if (!cancelled) {
        setDashboard(dashboardRes)
        setRuns(runsRes)
        setIssues(issuesRes)
        setLoading(false)
      }
    } catch (err) {
      if (!cancelled) {
        setError(err.message)
        setLoading(false)
      }
    }
    })

    return () => {
    cancelled = true
    }
  }, [DEMO_MODE])

  async function loadData() {
    if (DEMO_MODE) {
      setDashboard(demoDashboard)
      setRuns(demoRuns)
      setIssues(demoIssues)
      return
    }

    const [dashboardRes, runsRes, issuesRes] = await Promise.all([
      api.getDashboard(), api.getRuns(), api.getIssues(),
    ])
    setDashboard(dashboardRes)
    setRuns(runsRes)
    setIssues(issuesRes)
  }

  async function syncRuns() {
    setSyncing(true)
    setError('')
    try {
      if (DEMO_MODE) {
        await new Promise(resolve => setTimeout(resolve, 700))
        await loadData()
        return
      }
      await api.syncRuns()
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSyncing(false)
    }
  }

  async function analyzeRun(runId) {
    setAnalyzing(runId)
    setError('')
    try {
      if (DEMO_MODE) {
        await new Promise(resolve => setTimeout(resolve, 700))
        setSelectedAnalysis({
          runId,
          rootCause: 'Application dependency resolution failed during the container build',
          confidence: 85,
          evidence: [
            'Matched log patterns: Dependency or Package Installation, Docker Configuration',
            'Docker build exited while restoring application dependencies',
            'The failed run used a different dependency state than the earlier successful run'
          ],
          recommendation: 'Pin dependency versions, clear stale caches, and use a lockfile. Retry the container build after verifying registry access.',
          source: 'local-rule-engine',
          comparison: {
            successfulRunId: 'tekton-build-1',
            durationDifference: -88,
            branchName: 'release/aws',
            differences: ['Branch changed between runs', 'Build duration changed'],
            observedPatterns: ['Dependency or Package Installation', 'Docker Configuration']
          },
          recurringIssue: true,
          previousOccurrences: 2
        })
        setActiveNav('issues')
        return
      }
      const result = await api.analyzeRun(runId)
      setSelectedAnalysis(result)
      await loadData()
      setActiveNav('issues')
    } catch (err) {
      setError(err.message)
    } finally {
      setAnalyzing(null)
    }
  }

  const filteredRuns = useMemo(() => runs.filter(run => {
    const haystack = `${run.workflowName || ''} ${run.repositoryName || ''} ${run.branchName || ''}`.toLowerCase()
    const matchesSearch = haystack.includes(search.toLowerCase())
    const conclusion = (run.conclusion || run.status || '').toLowerCase()
    const matchesStatus = statusFilter === 'all'
      || (statusFilter === 'success' && conclusion === 'success')
      || (statusFilter === 'failure' && conclusion === 'failure')
      || (statusFilter === 'running' && conclusion !== 'success' && conclusion !== 'failure')
    return matchesSearch && matchesStatus
  }), [runs, search, statusFilter])


  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Icon name="bolt" size={20} /></div><span>Guardian<span>AI</span></span></div>
      {DEMO_MODE && <div className="demo-badge"><span />Demo mode</div>}
      <nav>{navItems.map(item => <button key={item.id} className={activeNav === item.id ? 'active' : ''} onClick={() => setActiveNav(item.id)}><Icon name={item.icon} /><span>{item.label}</span>{item.id === 'pipelines' && <b>{runs.length}</b>}</button>)}</nav>
      <div className="sidebar-footer"><span className="avatar">AK</span><div><strong>Ajay Kumar</strong><small>Administrator</small></div></div>
    </aside>

    <main>
      <header className="topbar"><div><p>CI/CD Intelligence</p><h1>{activeNav === 'overview' ? 'Pipeline Overview' : activeNav === 'pipelines' ? 'Pipeline Runs' : 'Issue Patterns'}</h1></div><button className="sync-button" onClick={syncRuns} disabled={syncing}><Icon name="refresh" />{syncing ? 'Syncing…' : 'Sync Pipeline'}</button></header>

      {error && <div className="error-banner"><Icon name="warning" /><span>{error}</span><button onClick={() => setError('')}>×</button></div>}

      {loading ? <div className="loading"><span /><p>Loading pipeline data…</p></div> : <>
        {activeNav === 'overview' && dashboard && <>
          <section className="stats-grid">
            <StatCard label="Total Runs" value={dashboard.totalRuns} detail="All recorded pipelines" tone="blue" />
            <StatCard label="Successful" value={dashboard.successfulRuns} detail={`${dashboard.reliabilityScore?.toFixed(1) || 0}% of completed runs`} tone="success" />
            <StatCard label="Failed Runs" value={dashboard.failedRuns} detail="Require attention" tone="warning" />
            <StatCard label="Recurring Issues" value={dashboard.recurringIssues} detail="Detected patterns" tone="purple" />
          </section>

          <section className="panel"><div className="panel-heading"><div><h2>Recent Pipeline Runs</h2><p>Latest CI/CD activity</p></div><button className="text-button" onClick={() => setActiveNav('pipelines')}>View all →</button></div>
            <RunsTable runs={(dashboard.recentRuns || []).slice(0, 8)} analyzing={analyzing} onAnalyze={analyzeRun} />
          </section>
        </>}

        {activeNav === 'pipelines' && <section className="panel full-panel">
          <div className="panel-heading"><div><h2>All Pipeline Runs</h2><p>{filteredRuns.length} runs matching filters</p></div></div>
          <div className="filters"><label><Icon name="search" /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search workflows, repositories, branches…" /></label><select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="all">All statuses</option><option value="success">Success</option><option value="failure">Failed</option><option value="running">Running</option></select></div>
          <RunsTable runs={filteredRuns} analyzing={analyzing} onAnalyze={analyzeRun} />
        </section>}

        {activeNav === 'issues' && <section className="panel full-panel"><div className="panel-heading"><div><h2>Detected Issue Patterns</h2><p>Recurring failures identified by log analysis</p></div></div>
          {selectedAnalysis && <AnalysisResult analysis={selectedAnalysis} onClose={() => setSelectedAnalysis(null)} />}
          {issues.length === 0 ? <div className="empty-state"><div><Icon name="check" size={28} /></div><h3>No issue patterns yet</h3><p>Analyze a failed pipeline run to discover recurring problems.</p></div> : <div className="issue-list">{issues.map(issue => <article className="issue-card" key={issue.id}><div className="issue-icon"><Icon name="warning" /></div><div className="issue-content"><div><h3>{issue.errorMessage || 'Unknown failure'}</h3><span>{issue.occurrences || 1} occurrences</span></div><p>{issue.rootCause || 'Root cause has not been determined.'}</p><small>Last seen {formatDate(issue.lastSeen)}</small></div></article>)}</div>}
        </section>}
      </>}
    </main>
  </div>
}

function RunsTable({ runs, analyzing, onAnalyze }) {
  if (!runs.length) return <div className="empty-state"><div><Icon name="pipeline" size={28} /></div><h3>No pipeline runs found</h3><p>Sync your provider or adjust your filters.</p></div>
  return <div className="table-wrap"><table><thead><tr><th>Pipeline</th><th>Status</th><th>Branch</th><th>Duration</th><th>Started</th><th></th></tr></thead><tbody>{runs.map(run => <tr key={run.id || run.runId}><td><div className="pipeline-name"><div className="pipeline-logo">{(run.workflowName || 'P').charAt(0).toUpperCase()}</div><div><strong>{run.workflowName || 'Unnamed pipeline'}</strong><span>{run.repositoryOwner && run.repositoryName ? `${run.repositoryOwner}/${run.repositoryName}` : 'Tekton / Kubernetes'}</span></div></div></td><td><StatusBadge conclusion={run.conclusion} status={run.status} /></td><td><span className="branch">⌁ {run.branchName || '—'}</span></td><td>{duration(run.durationSeconds)}</td><td>{formatDate(run.createdAt)}</td><td>{run.conclusion && !['success'].includes(run.conclusion.toLowerCase()) ? <button className="analyze-button" disabled={analyzing === run.runId} onClick={() => onAnalyze(run.runId)}>{analyzing === run.runId ? 'Analyzing…' : 'Analyze'}</button> : null}</td></tr>)}</tbody></table></div>
}

export default App