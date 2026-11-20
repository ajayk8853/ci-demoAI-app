# Guardian AI

## Run locally

Start PostgreSQL and the application:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

## Run backend with Docker Compose

Build the JAR and run backend + PostgreSQL in containers:

```powershell
.\gradlew.bat bootJar
docker compose up -d --build
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Stop containers:

```powershell
docker compose down
```

The Compose database uses a persistent named volume and the local development
credentials from `src/main/resources/application.yaml`. Stop it with
`docker compose down`; the data volume is retained.

The API is available at `http://localhost:8080/api/pipelines`. Port 5432
speaks the PostgreSQL protocol, not HTTP, so it cannot be opened in a browser.

The container restores PostgreSQL's legacy `Asia/Calcutta` time-zone alias
for JVMs that still use it. It is equivalent to `Asia/Kolkata`.

## Run the frontend

The React dashboard lives in `frontend/` and proxies `/api` requests to the local backend:

```powershell
Push-Location frontend
npm ci
npm run dev
Pop-Location
```

By default, the frontend uses demo data (`VITE_DEMO_MODE=true` in
`frontend/.env.local`). Copy `frontend/.env.example` to that file if needed,
then set the value to `false` to use the backend API. The frontend
build and lint checks run in GitHub Actions.

## Import GitHub Actions history

Set the repository before starting the application. A token is optional for
public repositories; private repositories require a token with Actions read
access. Keep tokens out of source control.

```powershell
$env:GITHUB_OWNER = "your-owner"
$env:GITHUB_REPOSITORY = "your-repository"
$env:GITHUB_TOKEN = "your-token"
.\gradlew.bat bootRun
```

In another PowerShell window, import the history and read the stored runs:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/pipelines/sync
Invoke-RestMethod http://localhost:8080/api/pipelines
```

Sync retrieves all pages and updates existing records by GitHub run ID. It
keeps the latest state of a rerun rather than a separate record per attempt.
`status` is the run's lifecycle state; `conclusion` identifies failures and
other completed outcomes. Timestamps are stored in UTC. For completed runs,
`durationSeconds` is an estimate based on GitHub's `run_started_at` and
`updated_at`; it is unavailable while a run is in progress. Existing records
from before GitHub integration may have no repository or conclusion.

The reliability percentage uses completed successful and failed runs only;
in-progress, cancelled, and other conclusions do not affect the denominator.
Failure analysis compares a run with the previous successful run for the same
repository and workflow, including failure-only log patterns when the earlier
run's evidence is available. GitHub analysis includes log patterns and parses
JUnit-style XML reports included in the downloaded log archive. Issue
occurrences are counted once per distinct analyzed pipeline run, even if that
run is analyzed more than once.

This is a local development API with no authentication; protect it before
exposing it on a network. GitHub API errors, including rate limits and invalid
tokens, cause sync to fail without saving a partial history.

## Import Tekton PipelineRuns

Guardian can poll Tekton `PipelineRun` resources from Kubernetes/EKS instead of GitHub Actions.
Set `PIPELINE_PROVIDER=tekton`. When Guardian runs inside the cluster, it detects the Kubernetes API
address and service-account token. Set `TEKTON_NAMESPACE` to scope sync to a namespace; leave it
blank to list PipelineRuns across all namespaces.

```powershell
$env:PIPELINE_PROVIDER = "tekton"
$env:TEKTON_NAMESPACE = "tekton-pipelines" # or blank for all namespaces
.\gradlew.bat bootRun

Invoke-RestMethod -Method Post http://localhost:8080/api/pipelines/sync
Invoke-RestMethod http://localhost:8080/api/pipelines
```

When running outside the cluster, set `TEKTON_API_URL` and `TEKTON_TOKEN`; for a development-only
self-signed API server set `TEKTON_INSECURE_SKIP_TLS_VERIFY=true`. The service account needs read
access to `tekton.dev/pipelineruns`:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: guardian-ai-pipelineruns-reader
rules:
  - apiGroups: ["tekton.dev"]
    resources: ["pipelineruns"]
    verbs: ["list"]
  - apiGroups: ["tekton.dev"]
    resources: ["taskruns"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["pods/log"]
    verbs: ["get"]
```

Bind this role to the Kubernetes ServiceAccount used by the Guardian backend. The screenshot's
Tekton dashboard has no rows because it only shows runs stored in Kubernetes; Guardian synchronization
will independently import those resources into PostgreSQL.

Tekton analysis reads the PipelineRun condition and TaskRun step logs through the Kubernetes API.
The RBAC rules above are required for that evidence collection. Tekton test reports are not yet
collected from workspace/artifact storage; JUnit XML parsing currently applies to XML files in
downloaded GitHub Actions log archives.

## Current backend API

After starting the service, these endpoints are available:

```powershell
Invoke-RestMethod http://localhost:8080/api/pipelines
Invoke-RestMethod -Method Post http://localhost:8080/api/pipelines/sync
Invoke-RestMethod http://localhost:8080/api/pipelines/reliability
Invoke-RestMethod http://localhost:8080/api/dashboard
Invoke-RestMethod http://localhost:8080/api/issues
Invoke-RestMethod -Method Post http://localhost:8080/api/pipelines/analyze/{runId}
```

`/api/pipelines/analyze/{runId}` uses deterministic pattern analysis by default.
If you set `OPENAI_API_KEY`, it also sends summaries of failed and previously
successful pipeline logs to OpenAI after redacting common credential patterns,
then persists its analysis. Redaction is best-effort; review your organization's
log-sharing policy before enabling the key.

## Environment variables

```powershell
$env:GITHUB_OWNER = "ajayk8853"
$env:GITHUB_REPOSITORY = "ci-demoAI-app"
$env:GITHUB_TOKEN = ""   # optional for public repos
$env:OPENAI_API_KEY = "" # optional
```

## Frontend status

The dashboard supports overview metrics, pipeline run search/status filters, issue patterns,
failure analysis, and manual synchronization. Set `VITE_DEMO_MODE=false` for live API data.