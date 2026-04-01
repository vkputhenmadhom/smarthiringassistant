#!/usr/bin/env node
/*
 * Reusable HR Admin click-path smoke validation.
 *
 * Requirements:
 * - Running gateway + auth + hr-admin frontend
 * - playwright installed in the environment where this script runs
 */

const { chromium } = require('playwright');

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8000';
const HR_FRONTEND_URL = process.env.HR_FRONTEND_URL || 'http://localhost:4200';
const LOGIN_PASSWORD = process.env.HR_SMOKE_PASSWORD || 'ClickPass123!';
const HEADLESS = (process.env.HR_SMOKE_HEADLESS || 'true').toLowerCase() !== 'false';

async function registerUser(baseUrl, suffix) {
  const payload = {
    username: `hrclick_${suffix}`,
    email: `hrclick_${suffix}@example.com`,
    password: LOGIN_PASSWORD,
    confirmPassword: LOGIN_PASSWORD,
    firstName: 'Hr',
    lastName: 'Click',
    role: 'RECRUITER',
  };

  const resp = await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const body = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    throw new Error(`register failed ${resp.status} ${JSON.stringify(body)}`);
  }

  return { ...payload, token: body.accessToken || '' };
}

function printResult(results) {
  console.log('--- HR Click Validation ---');
  for (const r of results) {
    const icon = r.status === 'PASS' ? 'PASS' : 'FAIL';
    console.log(`[${icon}] ${r.page} :: ${r.detail}`);
  }

  const failed = results.filter(r => r.status === 'FAIL');
  console.log(`Summary: ${results.length - failed.length}/${results.length} checks passed`);
  return failed.length === 0;
}

(async () => {
  const suffix = Date.now().toString();
  const user = await registerUser(GATEWAY_URL, suffix);

  const browser = await chromium.launch({ headless: HEADLESS });
  const page = await browser.newPage();
  const results = [];

  async function validateNavClick(label, expectedPath, expectedHeader) {
    try {
      const navLink = page.locator('mat-sidenav a', { hasText: label }).first();
      await navLink.waitFor({ state: 'visible', timeout: 10000 });
      await navLink.click();
      await page.waitForURL(`**${expectedPath}`, { timeout: 10000 });
      const url = page.url();
      const header = await page.locator('h1').first().innerText();
      const ok = url.includes(expectedPath) && header.includes(expectedHeader);
      results.push({ page: `${label} (nav)`, status: ok ? 'PASS' : 'FAIL', detail: `url=${url} header=${header}` });
    } catch (error) {
      results.push({ page: `${label} (nav)`, status: 'FAIL', detail: String(error) });
    }
  }

  async function validateDirectRoute(label, path, expectedHeader) {
    try {
      await page.goto(`${HR_FRONTEND_URL}${path}`, { waitUntil: 'networkidle' });
      const url = page.url();
      const header = await page.locator('h1').first().innerText();
      const ok = url.includes(path) && header.includes(expectedHeader);
      results.push({ page: `${label} (direct)`, status: ok ? 'PASS' : 'FAIL', detail: `url=${url} header=${header}` });
    } catch (error) {
      results.push({ page: `${label} (direct)`, status: 'FAIL', detail: String(error) });
    }
  }

  try {
    await page.goto(`${HR_FRONTEND_URL}/auth/login`, { waitUntil: 'networkidle' });
    await page.getByLabel('Username').fill(user.username);
    await page.getByLabel('Password').fill(user.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await page.waitForURL('**/app/dashboard', { timeout: 15000 });
    await page.waitForSelector('h1');

    const dashboardHeader = await page.locator('h1').first().innerText();
    results.push({
      page: 'Login -> Dashboard',
      status: 'PASS',
      detail: `url=${page.url()} header=${dashboardHeader}`,
    });

    await validateNavClick('Jobs', '/app/jobs', 'Jobs');
    await validateNavClick('Screening', '/app/screening', 'Screening Sessions');
    await validateNavClick('Analytics', '/app/analytics', 'Analytics');

    await validateDirectRoute('Jobs', '/app/jobs', 'Jobs');
    await validateDirectRoute('Screening', '/app/screening', 'Screening Sessions');
    await validateDirectRoute('Analytics', '/app/analytics', 'Analytics');

    const dashboardResp = await fetch(`${GATEWAY_URL}/graphql`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${user.token}`,
      },
      body: JSON.stringify({ query: 'query { dashboardMetrics { totalJobs openJobs } }' }),
    });

    const dashboardJson = await dashboardResp.json().catch(() => ({}));
    results.push({
      page: 'Analytics backend check',
      status: dashboardJson && dashboardJson.data && dashboardJson.data.dashboardMetrics ? 'PASS' : 'FAIL',
      detail: JSON.stringify(dashboardJson),
    });

    const ok = printResult(results);
    process.exit(ok ? 0 : 1);
  } finally {
    await browser.close();
  }
})().catch(err => {
  console.error('[FAIL] hr-click-validate crashed:', err);
  process.exit(1);
});

