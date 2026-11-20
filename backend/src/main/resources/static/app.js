const API = '/api';
const NS = 'http://www.w3.org/2000/svg';
const PRIORITY_COLOR = { LOW: '--ok', MEDIUM: '--warn', HIGH: '--warn', CRITICAL: '--crit' };
const STATUS_COLOR = { NOMINAL: '--ok', DEGRADED: '--warn', CRITICAL: '--crit', OFFLINE: '--off' };
const COMMS_COLOR = { FULL: '--ok', MINIMAL: '--warn', OFF: '--off' };
const MIN_DOMAIN = 600;

let simulation = null;
let timer = null;

const $ = id => document.getElementById(id);

async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.detail || res.statusText);
  return data;
}

function svgEl(tag, attrs, parent) {
  const e = document.createElementNS(NS, tag);
  for (const k in attrs) e.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(e);
  return e;
}

async function load() {
  const last = simulation.currentTick - 1;
  const range = `fromTick=${Math.max(0, last - 4999)}&toTick=${Math.max(0, last)}`;
  const [power, oxygen, comms, incidents, modules] = await Promise.all([
    call('GET', `/simulations/${simulation.id}/modules/POWER/readings?${range}`),
    call('GET', `/simulations/${simulation.id}/modules/OXYGEN/readings?${range}`),
    call('GET', `/simulations/${simulation.id}/modules/COMMS/readings?${range}`),
    call('GET', `/simulations/${simulation.id}/incidents`),
    call('GET', `/simulations/${simulation.id}/modules`),
  ]);
  const rows = power.map((p, i) => ({
    tick: p.tick, sunlit: p.values.sunlit, gen: p.values.generationKw, demand: p.values.demandKw,
    served: p.values.servedKw, battery: p.values.batteryPercent,
    o2: oxygen[i].values.levelPercent, comms: comms[i].values.mode,
  }));
  render(rows, incidents, modules);
}

function render(rows, incidents, modules) {
  $('meta').innerHTML = `Escenario <b>${simulation.scenario}</b> · semilla <b>${simulation.seed}</b> · ` +
    `<b>${simulation.openIncidents}</b> incidencias abiertas`;
  const t = simulation.currentTick;
  $('clock').textContent = `t=${t} · T+${Math.floor(t / 60)}h${String(t % 60).padStart(2, '0')}m`;
  renderCards(modules);
  renderCharts(rows, incidents);
  renderLog(incidents);
}

function renderCards(modules) {
  const fmt = (v, unit, digits = 2) => (typeof v === 'number' ? v.toFixed(digits) : '—') + unit;
  const details = {
    POWER: v => [['Generación', fmt(v.generationKw, ' kW')], ['Demanda', fmt(v.demandKw, ' kW', 1)],
      ['Servido', fmt(v.servedKw, ' kW')], ['Batería', fmt(v.batteryPercent, ' %', 1)]],
    OXYGEN: v => [['Nivel', fmt(v.levelPercent, ' %')],
      ['Generador', v.generatorPowered === undefined ? '—' : v.generatorPowered ? 'alimentado' : 'sin energía']],
    COMMS: v => [['Modo', v.mode || '—']],
  };
  $('cards').innerHTML = modules.map(m =>
    `<div class="card" style="--state:var(${STATUS_COLOR[m.status]})">` +
    `<div class="top"><span class="name">${m.module}</span><span class="pill">${m.status}</span></div>` +
    `<dl>${details[m.module](m.values).map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}</dl></div>`
  ).join('');
}

function renderCharts(rows, incidents) {
  const charts = $('charts');
  charts.innerHTML = '';
  if (!rows.length) return;
  const first = rows[0].tick;
  const domain = Math.max(MIN_DOMAIN, rows.length);
  const W = 1000, PAD_L = 40, PAD_R = 8;
  const x = tick => PAD_L + ((tick - first) / (domain - 1)) * (W - PAD_L - PAD_R);
  const step = (W - PAD_L - PAD_R) / (domain - 1);

  function chart(title, legend, height, yMin, yMax, ticks, draw) {
    const box = document.createElement('div');
    box.className = 'chart';
    box.innerHTML = `<div class="label"><strong>${title}</strong>` +
      legend.map(([c, l]) => `<span><i style="background:var(${c})"></i>${l}</span>`).join('') + '</div>';
    const svg = svgEl('svg', { viewBox: `0 0 ${W} ${height + 18}`, role: 'img', 'aria-label': title });
    const y = v => 4 + (1 - (v - yMin) / (yMax - yMin)) * (height - 8);
    for (const r of rows) {
      if (!r.sunlit) svgEl('rect', { x: x(r.tick), y: 0, width: step + .5, height, fill: 'var(--eclipse)' }, svg);
    }
    for (const v of ticks) {
      svgEl('line', { x1: PAD_L, x2: W - PAD_R, y1: y(v), y2: y(v), stroke: 'var(--grid)' }, svg);
      svgEl('text', { x: PAD_L - 6, y: y(v) + 3, 'text-anchor': 'end' }, svg).textContent = v;
    }
    for (const inc of incidents) {
      if (inc.openedAtTick < first) continue;
      svgEl('line', { x1: x(inc.openedAtTick), x2: x(inc.openedAtTick), y1: 0, y2: height,
        stroke: `var(${PRIORITY_COLOR[inc.priority]})`, 'stroke-dasharray': '3 3' }, svg);
    }
    draw(svg, y);
    const every = domain > 1500 ? 500 : 100;
    for (let tick = Math.ceil(first / every) * every; tick < first + domain; tick += every) {
      svgEl('text', { x: x(tick), y: height + 14, 'text-anchor': 'middle' }, svg).textContent = tick;
    }
    box.appendChild(svg);
    charts.appendChild(box);
  }

  const line = (key, y) => rows.map((r, i) => `${i ? 'L' : 'M'}${x(r.tick).toFixed(1)} ${y(r[key]).toFixed(1)}`).join('');
  const lastX = x(rows[rows.length - 1].tick);

  chart('Energía (kW)', [['--accent', 'generación'], ['--ink', 'demanda'], ['--crit', 'sin servir']], 120, 0, 13, [0, 4, 8, 12], (svg, y) => {
    svgEl('path', { d: line('gen', y), fill: 'none', stroke: 'var(--accent)', 'stroke-width': 1.5 }, svg);
    svgEl('path', { d: line('demand', y), fill: 'none', stroke: 'var(--ink)', 'stroke-width': 1.2 }, svg);
    const gap = rows.map((r, i) => `${i ? 'L' : 'M'}${x(r.tick).toFixed(1)} ${y(r.demand - r.served).toFixed(1)}`).join('');
    svgEl('path', { d: `${gap}L${lastX} ${y(0)}L${x(first)} ${y(0)}Z`, fill: 'var(--crit)', 'fill-opacity': .25 }, svg);
  });

  chart('Batería (%)', [['--accent', 'carga'], ['--warn', '30 / 20 / 15'], ['--crit', '5']], 110, 0, 100, [0, 25, 50, 75, 100], (svg, y) => {
    for (const [v, c] of [[30, '--warn'], [20, '--warn'], [15, '--warn'], [5, '--crit']]) {
      svgEl('line', { x1: PAD_L, x2: W - PAD_R, y1: y(v), y2: y(v), stroke: `var(${c})`, 'stroke-opacity': .6 }, svg);
    }
    svgEl('path', { d: `${line('battery', y)}L${lastX} ${y(0)}L${x(first)} ${y(0)}Z`, fill: 'var(--accent)', 'fill-opacity': .12 }, svg);
    svgEl('path', { d: line('battery', y), fill: 'none', stroke: 'var(--accent)', 'stroke-width': 1.8 }, svg);
  });

  chart('Oxígeno (% del nominal)', [['--ok', 'nivel'], ['--crit', 'umbral 95']], 90, 0, 100, [0, 50, 100], (svg, y) => {
    svgEl('line', { x1: PAD_L, x2: W - PAD_R, y1: y(95), y2: y(95), stroke: 'var(--crit)', 'stroke-opacity': .6 }, svg);
    svgEl('path', { d: line('o2', y), fill: 'none', stroke: 'var(--ok)', 'stroke-width': 1.8 }, svg);
  });

  chart('Comunicaciones', [['--ok', 'FULL'], ['--warn', 'MINIMAL'], ['--off', 'OFF']], 22, 0, 1, [], svg => {
    let start = 0;
    for (let i = 1; i <= rows.length; i++) {
      if (i === rows.length || rows[i].comms !== rows[start].comms) {
        svgEl('rect', { x: x(rows[start].tick), y: 3, width: x(rows[i - 1].tick) - x(rows[start].tick) + step,
          height: 16, fill: `var(${COMMS_COLOR[rows[start].comms]})` }, svg);
        start = i;
      }
    }
  });
}

function renderLog(incidents) {
  $('log').innerHTML = incidents.length ? incidents.map(i =>
    `<tr><td>${i.openedAtTick}</td><td>${i.ruleCode}</td><td>${i.module}</td>` +
    `<td class="prio" style="--state:var(${PRIORITY_COLOR[i.priority]})">${i.priority}</td>` +
    `<td>${i.status === 'OPEN' ? 'abierta' : 'resuelta en ' + i.resolvedAtTick}</td>` +
    `<td>${i.affectedModules.join(', ') || '—'}</td></tr>`
  ).join('') : '<tr><td colspan="6" class="empty">Sin incidencias.</td></tr>';
}

async function advance(ticks) {
  try {
    simulation = await call('POST', `/simulations/${simulation.id}/advance?ticks=${ticks}`);
    await load();
  } catch (e) {
    showError(e);
  }
}

function showError(e) {
  $('error').textContent = e.message;
  $('error').hidden = false;
  stop();
}

function stop() {
  clearInterval(timer);
  timer = null;
  $('run').textContent = '▶ Ejecutar';
}

$('run').addEventListener('click', () => {
  if (timer) return stop();
  $('run').textContent = '❚❚ Pausa';
  let busy = false;
  timer = setInterval(async () => {
    if (busy) return;
    busy = true;
    await advance(5);
    busy = false;
  }, 250);
});
$('step').addEventListener('click', () => advance(10));
$('jump').addEventListener('click', () => advance(100));

$('setup').addEventListener('submit', async e => {
  e.preventDefault();
  stop();
  $('error').hidden = true;
  try {
    simulation = await call('POST', '/simulations', { seed: Number($('seed').value), scenario: $('scenario').value });
    for (const id of ['run', 'step', 'jump']) $(id).disabled = false;
    await load();
  } catch (err) {
    showError(err);
  }
});

call('GET', '/scenarios')
  .then(names => {
    $('scenario').innerHTML = names.map(n => `<option>${n}</option>`).join('');
    $('scenario').value = names.includes('solar-degradation') ? 'solar-degradation' : names[0];
  })
  .catch(showError);
