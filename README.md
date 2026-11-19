# Guardian AI

## Run locally

Start PostgreSQL and the application:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

The Compose database uses a persistent named volume and the local development
credentials from `src/main/resources/application.yaml`. Stop it with
`docker compose down`; the data volume is retained.

The API is available at `http://localhost:8080/api/pipelines`. Port 5432
speaks the PostgreSQL protocol, not HTTP, so it cannot be opened in a browser.

The container restores PostgreSQL's legacy `Asia/Calcutta` time-zone alias
for JVMs that still use it. It is equivalent to `Asia/Kolkata`.

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

This is a local development API with no authentication; protect it before
exposing it on a network. GitHub API errors, including rate limits and invalid
tokens, cause sync to fail without saving a partial history.

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
If you set `OPENAI_API_KEY`, it will also call OpenAI and persist AI analysis output.

## Environment variables

```powershell
$env:GITHUB_OWNER = "ajayk8853"
$env:GITHUB_REPOSITORY = "ci-demoAI-app"
$env:GITHUB_TOKEN = ""   # optional for public repos
$env:OPENAI_API_KEY = "" # optional
```