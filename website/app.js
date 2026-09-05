// KodaHosting site statistics: GitHub API (public) + optional Supabase aggregate RPC.
// Koda maintainer: fill SUPABASE_URL + SUPABASE_ANON_KEY below (Dashboard → Settings → API)
// and run the public_site_stats SQL (see website/README.md). The site degrades
// gracefully to "—" while these are empty.

const GITHUB_REPO = 'libredroid-project/KodaHosting';
const CACHE_KEY = 'koda_stats_cache_v1';
const CACHE_MS = 10 * 60 * 1000; // 10 minutes

// ── OPTIONAL: app statistics ────────────────────────────────────────
const SUPABASE_URL = '';      // e.g. 'https://xxxxxxxx.supabase.co'
const SUPABASE_ANON_KEY = ''; // the project's public anon key (safe to publish)

function set(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}
function fmt(n) {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
  return String(n);
}

async function fetchJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/vnd.github+json' } });
  if (!res.ok) throw new Error(res.status);
  return res.json();
}

function applyGitHub(repo, rel) {
  if (repo) set('st-stars', fmt(repo.stargazers_count || 0));
  if (rel) {
    set('st-version', (rel.tag_name || '').replace(/^v/, '') || 'soon');
    const dl = (rel.assets || []).reduce((s, a) => s + (a.download_count || 0), 0);
    set('st-downloads', fmt(dl));
  }
}

async function loadGitHub() {
  try {
    const [repo, rel] = await Promise.allSettled([
      fetchJson(`https://api.github.com/repos/${GITHUB_REPO}`),
      fetchJson(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`),
    ]);
    const repoV = repo.status === 'fulfilled' ? repo.value : null;
    const relV = rel.status === 'fulfilled' ? rel.value : (rel.reason && String(rel.reason).includes('404') ? { tag_name: 'soon', assets: [] } : null);
    applyGitHub(repoV, relV);
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify({
        t: Date.now(),
        stars: repoV ? repoV.stargazers_count : null,
        version: relV ? relV.tag_name : null,
        downloads: relV ? (relV.assets || []).reduce((s, a) => s + (a.download_count || 0), 0) : null,
      }));
    } catch (e) {}
  } catch (e) { /* keep dashes */ }
}

function withCache() {
  try {
    const c = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
    if (c && Date.now() - c.t < CACHE_MS) {
      if (c.stars !== null) set('st-stars', fmt(c.stars));
      if (c.version) set('st-version', c.version.replace(/^v/, ''));
      if (c.downloads !== null) set('st-downloads', fmt(c.downloads));
      return true;
    }
  } catch (e) {}
  return false;
}

async function loadAppStats() {
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY) return;
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/public_site_stats`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${SUPABASE_ANON_KEY}` },
      body: '{}',
    });
    if (!res.ok) throw new Error('rpc ' + res.status);
    const data = await res.json();
    const row = Array.isArray(data) ? data[0] : data;
    if (row) {
      if (typeof row.servers === 'number') set('st-servers', fmt(row.servers));
      if (typeof row.users === 'number') set('st-users', fmt(row.users));
    }
  } catch (e) {
    const note = document.getElementById('stats-note');
    if (note) note.textContent = 'App statistics are being connected — check back soon.';
  }
}

(async function init() {
  if (withCache()) { loadAppStats(); return; }
  loadGitHub();
  loadAppStats();
})();
