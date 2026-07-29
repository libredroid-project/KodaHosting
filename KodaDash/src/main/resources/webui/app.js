// ============================================
// KodaDash - Web Dashboard
// ============================================

const state = {
  token: null,
  password: null,
  baseUrl: '',
  connected: false,
  currentTab: 'overview',
  currentPath: '',
  consoleLines: [],
  eventSource: null,
  statsInterval: null,
  fileEditorOpen: false,
  editingFile: null,
};

// --- Utilities ---
function formatUptime(ms) {
  const seconds = Math.floor((ms / 1000) % 60);
  const minutes = Math.floor((ms / (1000 * 60)) % 60);
  const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
  const days = Math.floor(ms / (1000 * 60 * 60 * 24));
  return `${days}d ${hours}h ${minutes}m`;
}

function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function parseMinecraftColors(str) {
  const colorMap = {
    '0': 'mc-black', '1': 'mc-dark_blue', '2': 'mc-dark_green', '3': 'mc-dark_aqua',
    '4': 'mc-dark_red', '5': 'mc-dark_purple', '6': 'mc-gold', '7': 'mc-gray',
    '8': 'mc-dark_gray', '9': 'mc-blue', 'a': 'mc-green', 'b': 'mc-aqua',
    'c': 'mc-red', 'd': 'mc-light_purple', 'e': 'mc-yellow', 'f': 'mc-white'
  };
  
  let html = '';
  let inSpan = false;
  
  const parts = str.split('§');
  html += escapeHtml(parts[0]);
  
  for (let i = 1; i < parts.length; i++) {
    const code = parts[i].charAt(0).toLowerCase();
    const text = parts[i].substring(1);
    
    if (colorMap[code]) {
      if (inSpan) html += '</span>';
      html += `<span class="${colorMap[code]}">`;
      inSpan = true;
    } else if (code === 'r') {
      if (inSpan) html += '</span>';
      inSpan = false;
    }
    html += escapeHtml(text);
  }
  
  if (inSpan) html += '</span>';
  return html || escapeHtml(str);
}

// --- API Helper ---
async function api(endpoint, options = {}) {
  const url = `${state.baseUrl}${endpoint}`;
  
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers
  };
  
  if (state.token) {
    headers['Authorization'] = `Bearer ${state.token}`;
  }
  
  if (state.password) {
    headers['X-Dashboard-Password'] = state.password;
  }
  
  const config = {
    ...options,
    headers
  };
  
  try {
    const response = await fetch(url, config);
    if (response.status === 401 || response.status === 403) {
      disconnect();
      throw new Error('Authentication failed');
    }
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `API Error: ${response.status}`);
    }
    // If empty response (e.g. 204 No Content), return empty object
    const text = await response.text();
    return text ? JSON.parse(text) : {};
  } catch (error) {
    console.error('API Error:', error);
    throw error;
  }
}

// --- Toast Notifications ---
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  
  container.appendChild(toast);
  
  setTimeout(() => {
    toast.classList.add('toast-closing');
    setTimeout(() => {
      container.removeChild(toast);
    }, 300);
  }, 4000);
}

// --- Auth ---
function initAuth() {
  // Determine Base URL
  const urlParams = new URLSearchParams(window.location.search);
  const paramServer = urlParams.get('server');
  const paramToken = urlParams.get('token');
  
  if (paramServer) {
    state.baseUrl = paramServer;
  } else {
    state.baseUrl = window.location.origin;
  }
  
  // Create particles
  createParticles();
  
  // Setup login form
  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    await doLogin();
  });
  
  // Check token
  const storedToken = sessionStorage.getItem('kodaDashToken');
  const storedPassword = sessionStorage.getItem('kodaDashPassword');
  
  if (paramToken) {
    document.getElementById('token-input').value = paramToken;
    doLogin();
  } else if (storedToken) {
    document.getElementById('token-input').value = storedToken;
    if (storedPassword) {
      document.getElementById('password-input').value = storedPassword;
    }
    doLogin();
  }
}

function createParticles() {
  const container = document.getElementById('login-particles');
  for (let i = 0; i < 20; i++) {
    const particle = document.createElement('div');
    particle.className = 'particle';
    particle.style.left = `${Math.random() * 100}%`;
    particle.style.top = `${Math.random() * 100}%`;
    particle.style.width = `${Math.random() * 30 + 10}px`;
    particle.style.height = particle.style.width;
    particle.style.animationDuration = `${Math.random() * 10 + 5}s`;
    particle.style.animationDelay = `${Math.random() * 5}s`;
    container.appendChild(particle);
  }
}

async function doLogin() {
  const token = document.getElementById('token-input').value.trim();
  const password = document.getElementById('password-input').value;
  const errorMsg = document.getElementById('login-error');
  const card = document.querySelector('.login-card');
  const btn = document.querySelector('.login-btn');
  
  if (!token) return;
  
  btn.textContent = 'Connecting...';
  btn.disabled = true;
  errorMsg.textContent = '';
  
  state.token = token;
  state.password = password || null;
  
  try {
    const data = await api('/api/auth', { 
      method: 'POST',
      body: JSON.stringify({ token: token, password: password || undefined })
    });
    
    // Auth success
    sessionStorage.setItem('kodaDashToken', token);
    if (password) sessionStorage.setItem('kodaDashPassword', password);
    
    state.connected = true;
    document.getElementById('login-screen').style.display = 'none';
    document.getElementById('dashboard').style.display = 'flex';
    
    // Update sidebar
    document.getElementById('server-name').textContent = data.serverName || 'Minecraft Server';
    
    // Init tabs
    switchTab('overview');
    startStatsPolling();
    initConsole();
    
    showToast('Connected successfully!', 'success');
  } catch (error) {
    state.token = null;
    state.password = null;
    
    if (error.message.includes('password')) {
      document.getElementById('password-group').style.display = 'block';
      errorMsg.textContent = 'A password is required for this server.';
    } else {
      errorMsg.textContent = 'Connection failed: Invalid token or server offline.';
      card.classList.remove('shake');
      void card.offsetWidth; // trigger reflow
      card.classList.add('shake');
    }
  } finally {
    btn.textContent = 'Connect';
    btn.disabled = false;
  }
}

function disconnect() {
  state.connected = false;
  state.token = null;
  state.password = null;
  
  sessionStorage.removeItem('kodaDashToken');
  sessionStorage.removeItem('kodaDashPassword');
  
  if (state.statsInterval) {
    clearInterval(state.statsInterval);
    state.statsInterval = null;
  }
  
  if (state.eventSource) {
    state.eventSource.close();
    state.eventSource = null;
  }
  
  document.getElementById('dashboard').style.display = 'none';
  document.getElementById('login-screen').style.display = 'flex';
  
  const dot = document.querySelector('.status-dot');
  dot.classList.remove('connected');
  dot.classList.add('disconnected');
  document.querySelector('.connection-status span:last-child').textContent = 'Disconnected';
}

// --- Tabs ---
function switchTab(tabName) {
  // Update nav items
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.remove('active');
    if (el.dataset.tab === tabName) el.classList.add('active');
  });
  
  // Update content
  document.querySelectorAll('.tab-content').forEach(el => {
    el.classList.remove('active');
  });
  document.getElementById(`tab-${tabName}`).classList.add('active');
  
  state.currentTab = tabName;
  
  // Close mobile menu
  document.getElementById('sidebar').classList.remove('open');
  
  // Load data based on tab
  if (tabName === 'overview') loadOverview();
  else if (tabName === 'players') loadPlayers();
  else if (tabName === 'files') loadFiles(state.currentPath);
  else if (tabName === 'settings') loadSettings();
}

// --- Overview ---
async function loadOverview() {
  try {
    const data = await api('/api/server');
    updateOverviewStats(data);
  } catch (error) {
    console.error('Failed to load overview:', error);
  }
}

function updateOverviewStats(data) {
  // TPS
  const tpsEl = document.getElementById('stat-tps');
  tpsEl.textContent = data.tps[0].toFixed(1);
  if (data.tps[0] >= 18) tpsEl.style.color = 'var(--success)';
  else if (data.tps[0] >= 15) tpsEl.style.color = 'var(--warning)';
  else tpsEl.style.color = 'var(--error)';
  
  // RAM
  const ramEl = document.getElementById('stat-ram');
  const ramBar = document.getElementById('stat-ram-bar');
  const maxRam = data.ram.max;
  const usedRam = data.ram.allocated - data.ram.free;
  const percent = Math.min(100, Math.max(0, (usedRam / maxRam) * 100));
  
  ramEl.textContent = `${(usedRam/1024).toFixed(1)}GB / ${(maxRam/1024).toFixed(1)}GB`;
  ramBar.style.width = `${percent}%`;
  
  if (percent > 85) ramBar.style.backgroundColor = 'var(--error)';
  else if (percent > 70) ramBar.style.backgroundColor = 'var(--warning)';
  else ramBar.style.backgroundColor = 'var(--accent)';
  
  // Players
  document.getElementById('stat-players').textContent = `${data.players.online} / ${data.players.max}`;
  
  // Uptime
  document.getElementById('stat-uptime').textContent = formatUptime(data.uptime);
  
  // Info
  document.getElementById('info-version').textContent = data.version;
  document.getElementById('info-motd').innerHTML = parseMinecraftColors(data.motd || 'A Minecraft Server');
  document.getElementById('info-world').textContent = 'world'; // static for now
  document.getElementById('info-gamemode').textContent = 'Survival'; // static for now
}

function startStatsPolling() {
  if (state.statsInterval) clearInterval(state.statsInterval);
  loadOverview();
  state.statsInterval = setInterval(() => {
    if (state.currentTab === 'overview') {
      loadOverview();
    }
  }, 3000);
}

// --- Console ---
async function initConsole() {
  const container = document.getElementById('console-output');
  container.innerHTML = '';
  
  try {
    // Initial load
    const data = await api('/api/console');
    if (data.lines) {
      data.lines.forEach(line => appendConsoleLine(line));
    }
    
    // Connect SSE
    let sseUrl = `${state.baseUrl}/api/console/stream?token=${encodeURIComponent(state.token)}`;
    if (state.password) {
      sseUrl += `&password=${encodeURIComponent(state.password)}`;
    }
    
    state.eventSource = new EventSource(sseUrl);
    
    state.eventSource.onmessage = (e) => {
      try {
        const line = JSON.parse(e.data);
        appendConsoleLine(line);
      } catch (err) {
        console.error('Failed to parse console msg:', err);
      }
    };
    
    state.eventSource.onerror = () => {
      console.warn('SSE Connection Error');
      // Attempt reconnect handled by browser, but we could add custom logic
    };
    
  } catch (error) {
    console.error('Failed to init console:', error);
  }
  
  // Command form
  document.getElementById('console-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    await sendCommand();
  });
}

function appendConsoleLine(line) {
  const container = document.getElementById('console-output');
  const el = document.createElement('div');
  el.className = 'console-line';
  
  let content = `[${line.time}] `;
  
  let levelClass = '';
  if (line.level === 'WARN') levelClass = 'log-warn';
  else if (line.level === 'ERROR' || line.level === 'SEVERE') levelClass = 'log-error';
  else if (line.level === 'INFO') levelClass = 'log-info';
  
  content += `<span class="${levelClass}">[${line.level}]</span> `;
  
  // Parse colors
  content += parseMinecraftColors(line.message);
  
  el.innerHTML = content;
  container.appendChild(el);
  
  // Auto-scroll
  container.scrollTop = container.scrollHeight;
}

async function sendCommand() {
  const input = document.getElementById('console-input');
  const cmd = input.value.trim();
  if (!cmd) return;
  
  input.value = '';
  
  try {
    await api('/api/console/command', {
      method: 'POST',
      body: JSON.stringify({ command: cmd })
    });
    
    // Add to console visually
    const container = document.getElementById('console-output');
    const el = document.createElement('div');
    el.className = 'console-line';
    el.style.color = 'var(--accent)';
    el.textContent = `> ${cmd}`;
    container.appendChild(el);
    container.scrollTop = container.scrollHeight;
    
  } catch (error) {
    showToast(`Failed to send command: ${error.message}`, 'error');
  }
}

// --- Players ---
async function loadPlayers() {
  const grid = document.getElementById('players-grid');
  const empty = document.getElementById('players-empty');
  const header = document.getElementById('players-count-header');
  
  try {
    const res = await api('/api/players');
    const players = res.players || [];
    
    header.textContent = `Players (${players.length})`;
    
    if (players.length === 0) {
      grid.style.display = 'none';
      empty.style.display = 'block';
      return;
    }
    
    grid.style.display = 'grid';
    empty.style.display = 'none';
    grid.innerHTML = '';
    
    players.forEach(p => {
      const healthPercent = Math.min(100, (p.health / 20) * 100);
      
      const card = document.createElement('div');
      card.className = 'player-card';
      card.innerHTML = `
        <div class="player-header">
          <img class="player-avatar" src="https://mc-heads.net/avatar/${p.name}/48" alt="${p.name}">
          <div class="player-info">
            <div class="player-name">${escapeHtml(p.name)}</div>
            <div class="player-health">
              <span>HP</span>
              <div class="health-bar-bg">
                <div class="health-bar" style="width: ${healthPercent}%"></div>
              </div>
              <span>${Math.round(p.health)}</span>
            </div>
          </div>
        </div>
        <div class="player-actions">
          <button class="btn-ghost" onclick="kickPlayer('${escapeHtml(p.name)}')">Kick</button>
          <button class="btn-danger" onclick="banPlayer('${escapeHtml(p.name)}')">Ban</button>
        </div>
      `;
      grid.appendChild(card);
    });
    
  } catch (error) {
    console.error('Failed to load players:', error);
    showToast('Failed to load players', 'error');
  }
}

window.kickPlayer = async function(name) {
  if (!confirm(`Are you sure you want to kick ${name}?`)) return;
  try {
    await api('/api/players/kick', {
      method: 'POST',
      body: JSON.stringify({ player: name, reason: 'Kicked by admin' })
    });
    showToast(`Kicked ${name}`, 'success');
    loadPlayers();
  } catch (error) {
    showToast('Failed to kick player', 'error');
  }
};

window.banPlayer = async function(name) {
  const reason = prompt(`Reason for banning ${name}:`, 'Banned by admin');
  if (reason === null) return;
  try {
    await api('/api/players/ban', {
      method: 'POST',
      body: JSON.stringify({ player: name, reason })
    });
    showToast(`Banned ${name}`, 'success');
    loadPlayers();
  } catch (error) {
    showToast('Failed to ban player', 'error');
  }
};

document.getElementById('refresh-players-btn').addEventListener('click', loadPlayers);

// --- Files ---
async function loadFiles(path = '') {
  const list = document.getElementById('files-list');
  const breadcrumb = document.getElementById('files-breadcrumb');
  
  try {
    const encodedPath = encodeURIComponent(path);
    const res = await api(`/api/files${path ? '?path=' + encodedPath : ''}`);
    const files = res.entries || [];
    state.currentPath = path;
    
    // Update breadcrumb
    const parts = path.split('/').filter(p => p);
    let breadcrumbHtml = `<span class="breadcrumb-item" onclick="loadFiles('')">root</span>`;
    
    let currentPathAcc = '';
    parts.forEach(part => {
      currentPathAcc += (currentPathAcc ? '/' : '') + part;
      breadcrumbHtml += `
        <span class="breadcrumb-sep">/</span>
        <span class="breadcrumb-item" onclick="loadFiles('${currentPathAcc}')">${escapeHtml(part)}</span>
      `;
    });
    breadcrumb.innerHTML = breadcrumbHtml;
    
    // Update list
    list.innerHTML = '';
    
    // Add ".." if not root
    if (path) {
      const parentPath = path.substring(0, path.lastIndexOf('/'));
      const item = document.createElement('div');
      item.className = 'file-item';
      item.onclick = () => loadFiles(parentPath);
      item.innerHTML = `
        <span class="file-icon">📁</span>
        <span class="file-name">..</span>
      `;
      list.appendChild(item);
    }
    
    // Sort: directories first, then alphabetically
    files.sort((a, b) => {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;
      return a.name.localeCompare(b.name);
    });
    
    files.forEach(f => {
      const item = document.createElement('div');
      item.className = 'file-item';
      
      const fullPath = path ? `${path}/${f.name}` : f.name;
      
      let icon = '📄';
      if (f.isDirectory) icon = '📁';
      else if (f.name.endsWith('.yml') || f.name.endsWith('.yaml')) icon = '⚙️';
      else if (f.name.endsWith('.json')) icon = '📋';
      else if (f.name.endsWith('.properties')) icon = '🔧';
      else if (f.name.endsWith('.jar')) icon = '☕';
      
      item.innerHTML = `
        <span class="file-icon">${icon}</span>
        <span class="file-name">${escapeHtml(f.name)}</span>
        ${!f.isDirectory ? `<span class="file-size">${formatBytes(f.size)}</span>` : ''}
        <div class="file-actions">
          <button class="btn-danger" onclick="event.stopPropagation(); deleteFile('${escapeHtml(fullPath)}')">Del</button>
        </div>
      `;
      
      item.onclick = () => {
        if (f.isDirectory) {
          loadFiles(fullPath);
        } else {
          openFile(fullPath);
        }
      };
      
      list.appendChild(item);
    });
    
  } catch (error) {
    showToast('Failed to load files', 'error');
  }
}

async function openFile(path) {
  try {
    const encodedPath = encodeURIComponent(path);
    const data = await api(`/api/files/content?path=${encodedPath}`);
    
    state.editingFile = path;
    document.getElementById('editor-filename').textContent = path.split('/').pop();
    document.getElementById('editor-textarea').value = data.content;
    document.getElementById('file-editor-overlay').style.display = 'flex';
    
  } catch (error) {
    showToast('Cannot read this file. It may be too large or binary.', 'warning');
  }
}

async function saveFile() {
  if (!state.editingFile) return;
  
  const content = document.getElementById('editor-textarea').value;
  try {
    const encodedPath = encodeURIComponent(state.editingFile);
    await api(`/api/files/content?path=${encodedPath}`, {
      method: 'POST',
      body: JSON.stringify({ content })
    });
    showToast('File saved successfully', 'success');
    closeEditor();
  } catch (error) {
    showToast('Failed to save file', 'error');
  }
}

function closeEditor() {
  document.getElementById('file-editor-overlay').style.display = 'none';
  state.editingFile = null;
}

window.deleteFile = async function(path) {
  if (!confirm(`Delete ${path}? This cannot be undone.`)) return;
  
  try {
    const encodedPath = encodeURIComponent(path);
    await api(`/api/files?path=${encodedPath}`, { method: 'DELETE' });
    showToast('Deleted', 'success');
    loadFiles(state.currentPath);
  } catch (error) {
    showToast('Failed to delete', 'error');
  }
};

document.getElementById('new-file-btn').addEventListener('click', async () => {
  const name = prompt('Enter file name (e.g. config.yml):');
  if (!name) return;
  
  const fullPath = state.currentPath ? `${state.currentPath}/${name}` : name;
  try {
    const encodedPath = encodeURIComponent(fullPath);
    await api(`/api/files/content?path=${encodedPath}`, {
      method: 'POST',
      body: JSON.stringify({ content: '' })
    });
    loadFiles(state.currentPath);
    openFile(fullPath);
  } catch (error) {
    showToast('Failed to create file', 'error');
  }
});

document.getElementById('new-folder-btn').addEventListener('click', async () => {
  const name = prompt('Enter folder name:');
  if (!name) return;
  
  const fullPath = state.currentPath ? `${state.currentPath}/${name}` : name;
  try {
    const encodedPath = encodeURIComponent(fullPath);
    await api(`/api/files/folder?path=${encodedPath}`, { method: 'POST' });
    loadFiles(state.currentPath);
  } catch (error) {
    showToast('Failed to create folder', 'error');
  }
});

document.getElementById('refresh-files-btn').addEventListener('click', () => loadFiles(state.currentPath));
document.getElementById('editor-save-btn').addEventListener('click', saveFile);
document.getElementById('editor-close-btn').addEventListener('click', closeEditor);

// --- Settings ---
async function loadSettings() {
  const container = document.getElementById('settings-form');
  container.innerHTML = 'Loading...';
  
  try {
    const res = await api('/api/settings');
    const props = res.properties || {};
    container.innerHTML = '';
    
    // Group properties
    const groups = {
      'General': ['server-name', 'motd', 'max-players', 'server-port', 'online-mode'],
      'Gameplay': ['gamemode', 'difficulty', 'pvp', 'allow-flight', 'spawn-monsters', 'spawn-animals'],
      'World': ['level-name', 'level-type', 'level-seed', 'view-distance', 'generate-structures'],
      'Advanced': [] // catch-all
    };
    
    const renderedProps = new Set();
    
    const renderGroup = (title, keys) => {
      const section = document.createElement('div');
      section.className = 'settings-section';
      section.innerHTML = `<h3>${title}</h3>`;
      
      let hasItems = false;
      
      keys.forEach(key => {
        if (props[key] !== undefined) {
          hasItems = true;
          renderedProps.add(key);
          const val = props[key];
          section.appendChild(createSettingRow(key, val));
        }
      });
      
      if (hasItems) container.appendChild(section);
    };
    
    renderGroup('General', groups['General']);
    renderGroup('Gameplay', groups['Gameplay']);
    renderGroup('World', groups['World']);
    
    // Advanced (remaining)
    const advKeys = Object.keys(props).filter(k => !renderedProps.has(k)).sort();
    if (advKeys.length > 0) {
      renderGroup('Advanced', advKeys);
    }
    
  } catch (error) {
    container.innerHTML = 'Failed to load settings.';
  }
}

function createSettingRow(key, value) {
  const row = document.createElement('div');
  row.className = 'setting-row';
  
  let controlHtml = '';
  
  if (value === 'true' || value === 'false' || typeof value === 'boolean') {
    const isChecked = value === 'true' || value === true;
    controlHtml = `
      <label class="toggle-switch">
        <input type="checkbox" data-key="${key}" ${isChecked ? 'checked' : ''}>
        <span class="toggle-slider"></span>
      </label>
    `;
  } else if (!isNaN(value) && value !== '') {
    controlHtml = `<input type="number" data-key="${key}" value="${value}">`;
  } else {
    controlHtml = `<input type="text" data-key="${key}" value="${escapeHtml(value.toString())}">`;
  }
  
  row.innerHTML = `
    <div class="setting-info">
      <div class="setting-name">${key}</div>
    </div>
    <div class="setting-control">${controlHtml}</div>
  `;
  
  return row;
}

document.getElementById('save-settings-btn').addEventListener('click', async () => {
  const data = {};
  
  document.querySelectorAll('#settings-form input').forEach(input => {
    const key = input.dataset.key;
    if (input.type === 'checkbox') {
      data[key] = input.checked ? 'true' : 'false';
    } else {
      data[key] = input.value;
    }
  });
  
  try {
    await api('/api/settings', {
      method: 'POST',
      body: JSON.stringify(data)
    });
    showToast('Settings saved! Restart server to apply.', 'success');
  } catch (error) {
    showToast('Failed to save settings', 'error');
  }
});

// --- Init ---
document.addEventListener('DOMContentLoaded', () => {
  initAuth();
  
  // Navigation
  document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', () => switchTab(item.dataset.tab));
  });
  
  document.getElementById('logout-btn').addEventListener('click', disconnect);
  
  // Mobile Menu
  document.getElementById('mobile-menu-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.add('open');
  });
  
  document.getElementById('mobile-menu-close').addEventListener('click', () => {
    document.getElementById('sidebar').classList.remove('open');
  });
});
