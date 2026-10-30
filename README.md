# Playwright + BrowserStack: GitHub Actions with Hourly & Nightly Runs

This repo runs [Playwright](https://playwright.dev/docs/intro) tests on [BrowserStack Automate](https://www.browserstack.com/automate) using the `browserstack-node-sdk`. A GitHub Actions workflow runs the tests on every push/PR, **hourly**, **nightly**, and on demand.

## Repository layout

| Path | Purpose |
|---|---|
| `.github/workflows/browserstack-tests.yml` | CI workflow (push, PR, schedules, manual) |
| `browserstack.yml` | BrowserStack config: platforms, parallels, build name, credentials |
| `playwright.config.js` | Playwright config (tests in `module*/**/*.js`) |
| `moduleA/`, `moduleB/`, `moduleC/` | Test files |

## Step 1: Create your own repository

1. Create a new **private** repository on GitHub (e.g. `my-org/playwright-browserstack`).
2. Push this code to the `main` branch:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-org>/<your-repo>.git
   git push -u origin main
   ```

## Step 2: Add BrowserStack credentials as GitHub secrets

1. Get your **Username** and **Access Key** from [BrowserStack Account Settings](https://www.browserstack.com/accounts/settings).
2. In GitHub, go to **Settings → Secrets and variables → Actions → New repository secret**.
3. Add two secrets:
   - `BROWSERSTACK_USERNAME`
   - `BROWSERSTACK_ACCESS_KEY`

Credentials are never stored in the code: `browserstack.yml` reads them from environment variables.

## Step 3: Enable GitHub Actions

Open the **Actions** tab. If prompted, click **"I understand my workflows, go ahead and enable them"**.

## Step 4: Run the workflow

### Triggers

| Trigger | When | Build name on BrowserStack |
|---|---|---|
| Push / Pull request | On changes to `main` | `ci-run` |
| Hourly schedule | `0 * * * *` (minute 0 of every hour, UTC) | `hourly-run` |
| Nightly schedule | `0 0 * * *` (00:00 UTC daily) | `nightly-run` |
| Manual | **Actions → BrowserStack Playwright Tests → Run workflow** | `manual-run` / `hourly-run` / `nightly-run` (chosen input) |

Build names appear on the [Automate dashboard](https://www.browserstack.com/automate) next to `projectName: BrowserStack Samples`, with an incrementing `#<build number>`. Filter by build name to separate hourly, nightly, and CI results.

### Changing the schedule

Edit the `cron` entries under `on.schedule` in `.github/workflows/browserstack-tests.yml`. Cron times are **UTC**. Examples:
- Every 30 minutes: `*/30 * * * *`
- Nightly at 02:30 UTC: `30 2 * * *`
- Weekdays only, hourly: `0 * * * 1-5`

If you change a cron expression, also update the matching string in the `BROWSERSTACK_BUILD_NAME` expression so the build label stays correct.

> **Notes on scheduled workflows**
> - Schedules only run from the **default branch** (`main`).
> - GitHub may delay scheduled runs by several minutes during peak load; they are not guaranteed to start exactly on the hour.
> - In public repos, schedules are disabled after 60 days of repo inactivity; re-enable them from the Actions tab.

## Step 5: View results

- **GitHub:** Actions tab → select a run → logs and the uploaded `test-artifacts-<run id>` artifact.
- **BrowserStack:** [Automate dashboard](https://www.browserstack.com/automate) and [Test Observability](https://observability.browserstack.com) (enabled via `testObservability: true`) for videos, logs, and screenshots.

## Customizing

- **Browsers/OS:** edit the `platforms` list in `browserstack.yml` ([full list](https://www.browserstack.com/list-of-browsers-and-platforms/automate)).
- **Parallelism:** set `parallelsPerPlatform` in `browserstack.yml`. Total parallels = platforms × `parallelsPerPlatform`. Make sure this fits your BrowserStack plan ([calculator](https://www.browserstack.com/automate/parallel-calculator?ref=github)).
- **Your own tests:** add specs under `moduleA/`, `moduleB/`, `moduleC/` (or change `testMatch` in `playwright.config.js`).
- **Local/private sites:** set `browserstackLocal: true` in `browserstack.yml` ([docs](https://www.browserstack.com/docs/automate/selenium/local-testing-introduction)).

## Running locally

```bash
export BROWSERSTACK_USERNAME=<your-username>
export BROWSERSTACK_ACCESS_KEY=<your-access-key>
export BROWSERSTACK_BUILD_NAME=local-run
npm install
npm run sample-test
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Authentication failed / 401 | Re-check the two secrets' names and values |
| Scheduled run not starting | Confirm workflow is on `main` and Actions are enabled |
| Tests queued on BrowserStack | Reduce `parallelsPerPlatform`/platforms or upgrade parallels |
| Hourly and nightly overlap at 00:00 | Expected; both run, using separate builds |
