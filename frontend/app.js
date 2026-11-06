console.info('PulsePass app.js loaded: seat-config build v3');
const API_BASE = 'http://localhost:8080';
const BOOKING_API_BASE = API_BASE;
const USER_API_BASE = API_BASE;

let allEvents = [];
let selectedEvent = null;
let selectedSeats = [];
let currentUser = null;
let adminEvents = [];
let editingEventId = null;
let authMode = 'login';
let ownedSeats = new Map();

const EVENTS_PER_PAGE = 8;
let currentPage = 1;

const seatsBeingPaid = new Map();



function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c]);
}

const $ = (id) => document.getElementById(id);

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }).toUpperCase();
}

function formatLongDate(dateString) {
  return new Date(dateString).toLocaleString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit'
  });
}

function getStartingPrice(seats = []) {
  return seats.length ? Math.min(...seats.map((s) => Number(s.price || 0))) : 0;
}

function getCityLabel(location = '') {
  return location.split(',').slice(-1)[0].trim();
}

function coverClass(event) {
  const index = Math.max(allEvents.findIndex((e) => String(e.id) === String(event.id)), 0);
  return `cover-${(index % 4) + 1}`;
}

function getSeatKey(row, seatNumber) {
  return `${String(row || '').trim().toUpperCase()}${Number(seatNumber)}`;
}



function showPaymentNotification(success, titleText = null, detailText = null) {
  $('paymentNotification')?.remove();

  const title = escapeHtml(titleText || (success ? 'Платёж успешно завершён' : 'Платёж не прошёл'));
  const text = escapeHtml(detailText || (success ? 'Билет подтверждён.' : 'Проверьте данные карты и попробуйте ещё раз.'));

  const notification = document.createElement('div');
  notification.id = 'paymentNotification';
  notification.className = `payment-notification ${success ? 'success' : 'error'}`;
  notification.innerHTML = `
    <div class="notification-icon">${success ? '✓' : '☹'}</div>
    <div class="notification-content">
      <div class="notification-title">${title}</div>
      <div class="notification-text">${text}</div>
    </div>
    <button type="button" class="notification-close" aria-label="Close">×</button>`;

  document.body.appendChild(notification);
  notification.querySelector('.notification-close').addEventListener('click', () => hidePaymentNotification(notification));
  requestAnimationFrame(() => notification.classList.add('show'));
  setTimeout(() => hidePaymentNotification(notification), 5000);
}

function hidePaymentNotification(notification) {
  if (!notification) return;
  notification.classList.remove('show');
  setTimeout(() => notification.remove(), 350);
}



function isSeatBeingPaid(eventId, row, seatNumber) {
  return seatsBeingPaid.get(String(eventId))?.has(getSeatKey(row, seatNumber)) || false;
}

function getBackendSeatStatus(seat) {
  if (!seat) return 'AVAILABLE';
  if (seat.booked === true || seat.available === false) return 'BOOKED';

  const status = String(seat.status || seat.bookingStatus || seat.state || '').toUpperCase();
  if (['BOOKED', 'PAID', 'RESERVED', 'SOLD'].includes(status)) return 'BOOKED';
  if (['PENDING', 'PAYMENT', 'PAYING', 'PROCESSING', 'HOLD', 'HELD'].includes(status)) return 'PAYING';
  return 'AVAILABLE';
}

async function hydrateSeatStatusesForEvents(events) {
  if (!Array.isArray(events)) return events;

  await Promise.all(events.map(async (event) => {
    if (!event?.id) return;
    try {
      const response = await fetch(`${BOOKING_API_BASE}/api/bookings/events/${event.id}/seat-status`);
      if (!response.ok) return;

      const statuses = await response.json();
      const lookup = new Map((statuses || []).map((s) => [`${s.row}-${s.seatNumber}`, s.status]));

      (event.seats || []).forEach((seat) => {
        const backendStatus = lookup.get(`${seat.row}-${seat.seatNumber}`);
        if (!backendStatus) return;
        seat.status = backendStatus;
        seat.available = backendStatus === 'AVAILABLE';
        seat.booked = ['RESERVED', 'SOLD'].includes(backendStatus);
      });
    } catch (error) {
      console.warn('Seat status sync failed for event', event.id, error);
    }
  }));

  return events;
}


function compareRows(a, b) {
  return a.length - b.length || a.localeCompare(b);
}

function getSeatMap(event) {
  const seats = event.seats || [];
  const seatLookup = new Map(seats.map((seat) => [`${seat.row}-${seat.seatNumber}`, seat]));
  const rows = [...new Set(seats.map((seat) => seat.row))].sort(compareRows);
  const maxSeatNumber = Math.max(0, ...seats.map((seat) => Number(seat.seatNumber)));
  const seatNumbers = Array.from({ length: maxSeatNumber }, (_, i) => i + 1);

  return rows.map((row) => ({
    row,
    seats: seatNumbers.map((seatNumber) => {
      const key = `${row}-${seatNumber}`;
      const seat = seatLookup.get(key);
      let status = seat ? getBackendSeatStatus(seat) : 'EMPTY';

      if (seat && isSeatBeingPaid(event.id, row, seatNumber)) status = 'PAYING';

      return {
        key, row, seatNumber, status,
        available: status === 'AVAILABLE',
        booked: status === 'BOOKED',
        paying: status === 'PAYING',
        price: seat ? Number(seat.price || 0) : 0,
        isSeat: Boolean(seat)
      };
    })
  }));
}



function renderEvents(events, emptyMessage = 'Мероприятий пока нет.') {
  const grid = document.querySelector('.event-grid');
  if (!grid) return;

  if (!events.length) {
    grid.innerHTML = `<p class="empty-state">${escapeHtml(emptyMessage)}</p>`;
    return;
  }

  grid.innerHTML = events.map((event) => `
    <article class="event-card" data-open-event="${escapeHtml(event.id)}">
      <div class="event-image ${coverClass(event)}"${event.imageUrl ? ` style="background-image:url('${escapeHtml(event.imageUrl)}')"` : ''}></div>
      <div class="event-body">
        <div class="event-meta-row">
          <span class="date-badge">${escapeHtml(formatDate(event.eventDate))}</span>
          <span class="location">${escapeHtml(getCityLabel(event.location))}</span>
        </div>
        <h3>${escapeHtml(event.name)}</h3>
        <p>${escapeHtml(event.location)}</p>
        <div class="event-footer">
          <span class="tag">${escapeHtml(event.artist || 'Live')}</span>
          <strong>From $${getStartingPrice(event.seats)}</strong>
        </div>
        <button class="card-buy-btn" type="button">Купить билеты</button>
      </div>
    </article>`).join('');
}

function paginationItems(current, total) {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }

  const items = [1];
  const start = Math.max(2, current - 2);
  const end = Math.min(total - 1, current + 2);

  if (start > 2) items.push('…');
  for (let page = start; page <= end; page++) items.push(page);
  if (end < total - 1) items.push('…');
  items.push(total);
  return items;
}

function renderPagination(totalPages) {
  const nav = $('eventPagination');
  if (!nav) return;

  if (!allEvents.length || totalPages <= 1) {
    nav.innerHTML = '';
    nav.classList.add('hidden');
    return;
  }

  nav.classList.remove('hidden');
  nav.innerHTML = `
    <button type="button" data-page="${currentPage - 1}" ${currentPage === 1 ? 'disabled' : ''} aria-label="Previous page">←</button>
    ${paginationItems(currentPage, totalPages).map((item) => item === '…'
        ? '<span class="pagination-ellipsis">…</span>'
        : `<button type="button" data-page="${item}" class="${item === currentPage ? 'active' : ''}" ${item === currentPage ? 'aria-current="page"' : ''}>${item}</button>`).join('')}
    <button type="button" data-page="${currentPage + 1}" ${currentPage === totalPages ? 'disabled' : ''} aria-label="Next page">→</button>`;
}

function sortedByDate(events) {
  return [...events].sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate));
}

function renderSpotlight() {
  const section = $('spotlightSection');
  const box = $('spotlight');
  if (!section || !box) return;

  const sorted = sortedByDate(allEvents);
  if (!sorted.length) {
    section.classList.add('hidden');
    return;
  }

  const next = sorted[0];
  const freeSeats = allEvents.reduce(
      (sum, e) => sum + (e.seats || []).filter((s) => getBackendSeatStatus(s) === 'AVAILABLE').length, 0);
  const cities = new Set(allEvents.map((e) => getCityLabel(e.location))).size;

  box.innerHTML = `
    <div class="spotlight-copy">
      <span class="eyebrow light">Next event</span>
      <h2>${escapeHtml(next.name)}</h2>
      <p>${escapeHtml(next.artist || '')} · ${escapeHtml(next.location)}<br>${escapeHtml(formatLongDate(next.eventDate))}</p>
      <div class="spotlight-actions">
        <button class="primary-btn" type="button" data-open-event="${escapeHtml(next.id)}">Choose seats</button>
      </div>
    </div>
    <div class="spotlight-stats">
      <div><strong>${allEvents.length}</strong><span>Events</span></div>
      <div><strong>${freeSeats}</strong><span>Free seats</span></div>
      <div><strong>${cities}</strong><span>Cities</span></div>
    </div>`;
  section.classList.remove('hidden');
}

function renderUpcoming() {
  const section = $('upcomingSection');
  const list = $('upcoming');
  if (!section || !list) return;

  const items = sortedByDate(allEvents).slice(1, 4);
  if (!items.length) {
    section.classList.add('hidden');
    return;
  }

  list.innerHTML = items.map((event) => `
    <article class="small-card" data-open-event="${escapeHtml(event.id)}">
      <div class="small-thumb ${coverClass(event)}"${event.imageUrl ? ` style="background-image:url('${escapeHtml(event.imageUrl)}')"` : ''}></div>
      <div>
        <span>${escapeHtml(formatLongDate(event.eventDate))}</span>
        <h3>${escapeHtml(event.name)}</h3>
        <p>${escapeHtml(event.location)}</p>
      </div>
    </article>`).join('');
  section.classList.remove('hidden');
}

function refreshView(emptyMessage) {
  const totalPages = Math.max(1, Math.ceil(allEvents.length / EVENTS_PER_PAGE));
  currentPage = Math.min(Math.max(currentPage, 1), totalPages);

  const start = (currentPage - 1) * EVENTS_PER_PAGE;
  renderEvents(allEvents.slice(start, start + EVENTS_PER_PAGE), emptyMessage);
  renderPagination(totalPages);
  renderSpotlight();
  renderUpcoming();
}

/* ---------- Event modal ---------- */

function updateBuyButton() {
  const buyTicketBtn = $('buyTicketBtn');
  if (!buyTicketBtn) return;
  buyTicketBtn.disabled = selectedSeats.length === 0;
  buyTicketBtn.textContent = selectedSeats.length > 0 ? `Buy ticket(s) (${selectedSeats.length})` : 'Buy ticket';
}

function selectedLabelFor(event) {
  const names = selectedSeats.filter((s) => s.eventId === event.id).map((s) => `${s.row}${s.seatNumber}`);
  return names.length ? names.join(', ') : 'No seat selected';
}

function openEventModal(event) {
  selectedEvent = event;

  const seatMap = $('seatMap');
  seatMap.innerHTML = '';

  const hero = $('modalHero');
  hero.className = `event-modal-hero ${coverClass(event)}`;
  hero.style.backgroundImage = event.imageUrl ? `url("${event.imageUrl}")` : '';
  $('modalTitle').textContent = event.name;
  $('modalMeta').textContent = `${event.location} • ${formatLongDate(event.eventDate)}`;
  $('modalDescription').textContent = event.description
      || `${event.artist || 'Featured artist'} live at ${event.location}. Choose your seat below.`;
  $('modalTag').textContent = event.artist || 'Live';

  const source = $('modalSource');
  if (source) {
    source.href = event.sourceUrl || '';
    source.classList.toggle('hidden', !event.sourceUrl);
  }
  $('selectedSeatLabel').textContent = selectedLabelFor(event);
  updateBuyButton();

  const owned = ownedSeats.get(String(event.id)) || new Set();

  getSeatMap(event).forEach((row) => {
    const rowEl = document.createElement('div');
    rowEl.className = 'seat-row';

    const rowLabel = document.createElement('span');
    rowLabel.className = 'seat-row-label';
    rowLabel.textContent = row.row;
    rowEl.appendChild(rowLabel);

    row.seats.forEach((seat) => {
      if (!seat.isSeat) {
        const empty = document.createElement('div');
        empty.className = 'seat-empty';
        rowEl.appendChild(empty);
        return;
      }

      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = String(seat.seatNumber);
      button.dataset.row = seat.row;
      button.dataset.seatNumber = String(seat.seatNumber);

      const label = `${seat.row}${seat.seatNumber}`;
      const isOwned = owned.has(getSeatKey(seat.row, seat.seatNumber));
      const isBooked = seat.booked || isOwned;

      if (isBooked) {
        button.className = 'seat-btn booked';
        button.disabled = true;
        button.title = `${label} • ${isOwned ? 'Ваш билет' : 'Забронировано'}`;
        rowEl.appendChild(button);
        return;
      }

      if (seat.paying) {
        button.className = 'seat-btn paying';
        button.disabled = true;
        button.title = `${label} • Оплата в процессе`;
        rowEl.appendChild(button);
        return;
      }

      button.className = 'seat-btn available';
      button.title = `${label} • $${seat.price}`;

      if (selectedSeats.some((s) => s.eventId === event.id && s.row === seat.row && s.seatNumber === seat.seatNumber)) {
        button.classList.add('selected');
      }

      button.addEventListener('click', () => {
        const same = (s) => s.eventId === event.id && s.row === seat.row && s.seatNumber === seat.seatNumber;

        if (selectedSeats.some(same)) {
          selectedSeats = selectedSeats.filter((s) => !same(s));
        } else {
          selectedSeats.push({ eventId: event.id, row: seat.row, seatNumber: seat.seatNumber, price: Number(seat.price || 0), label });
        }

        $('selectedSeatLabel').textContent = selectedLabelFor(event);
        seatMap.querySelectorAll('.seat-btn').forEach((btn) => {
          const selected = selectedSeats.some((s) =>
              s.eventId === event.id && s.row === btn.dataset.row && s.seatNumber === Number(btn.dataset.seatNumber));
          btn.classList.toggle('selected', selected);
        });
        updateBuyButton();
      });

      rowEl.appendChild(button);
    });

    seatMap.appendChild(rowEl);
  });

  document.querySelector('.seat-legend')?.remove();
  const legend = document.createElement('div');
  legend.className = 'seat-legend';
  legend.innerHTML = `
    <div class="seat-legend-item"><span class="seat-legend-color free"></span><span>Свободно</span></div>
    <div class="seat-legend-item"><span class="seat-legend-color selected"></span><span>Выбрано</span></div>
    <div class="seat-legend-item"><span class="seat-legend-color booked"></span><span>Забронировано / оплачивается</span></div>`;
  seatMap.parentElement.appendChild(legend);

  $('eventModal').classList.remove('hidden');
  $('eventModal').setAttribute('aria-hidden', 'false');
}

function closeModal(modalId) {
  const modal = $(modalId);
  if (modal) {
    modal.classList.add('hidden');
    modal.setAttribute('aria-hidden', 'true');
  }
}

/* ---------- Session / JWT ---------- */

function readCurrentUser() {
  try {
    const raw = localStorage.getItem('pulsepass-user');
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    console.warn('Failed to read user session:', error);
    return null;
  }
}

function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')));
  } catch (error) {
    return null;
  }
}

function isAccessTokenExpired(token) {
  const payload = decodeJwtPayload(token);
  if (!payload || !payload.exp) return false;
  return Date.now() >= payload.exp * 1000 - 30000;
}

async function ensureValidAccessToken() {
  if (!currentUser?.accessToken) throw new Error('Authentication required');
  if (!isAccessTokenExpired(currentUser.accessToken)) return currentUser.accessToken;
  if (!currentUser.refreshToken) throw new Error('Session expired');

  const response = await fetch(`${USER_API_BASE}/api/users/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: currentUser.refreshToken })
  });

  if (!response.ok) {
    logoutUser();
    throw new Error('Session expired. Please sign in again.');
  }

  const payload = await response.json();
  currentUser.accessToken = payload.accessToken;
  persistCurrentUser(currentUser);
  return payload.accessToken;
}

function updateAuthButton() {
  const btn = $('authToggleBtn');
  if (!btn) return;
  if (currentUser) {
    btn.textContent = `Profile: ${currentUser.fullName || currentUser.email || 'User'}`;
    btn.classList.add('is-user');
  } else {
    btn.textContent = 'Sign in';
    btn.classList.remove('is-user');
  }
}

function persistCurrentUser(user) {
  if (user) {
    user.role = decodeJwtPayload(user.accessToken)?.role || user.role || 'USER';
  }
  currentUser = user;

  if (user) {
    localStorage.setItem('pulsepass-user', JSON.stringify(user));
  } else {
    localStorage.removeItem('pulsepass-user');
  }

  updateAuthButton();
  updateAdminPanelVisibility();
}

function logoutUser() {
  currentUser = null;
  ownedSeats = new Map();
  localStorage.removeItem('pulsepass-user');
  updateAuthButton();

  const authStatus = $('authStatus');
  if (authStatus) {
    authStatus.textContent = 'Вы вышли из системы.';
    authStatus.classList.add('visible');
  }

  closeModal('profileModal');
  updateAdminPanelVisibility();
}

function updateAdminPanelVisibility() {
  const isAdmin = currentUser?.role === 'ADMIN';
  $('adminPanelToggle')?.classList.toggle('hidden', !isAdmin);
  if (!isAdmin) $('adminPanel')?.classList.add('hidden');
}

/* ---------- Admin panel ---------- */

function formatDateTimeInput(value) {
  const date = value ? new Date(value) : new Date(Date.now() + 30 * 86400000);
  if (!value) date.setHours(19, 30, 0, 0);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function showAdminStatus(message, isError = false) {
  const status = $('adminFormStatus');
  if (!status) return;
  status.textContent = message;
  status.classList.toggle('error', isError);
}

function openAdminEventForm(eventToEdit = null) {
  const form = $('adminEventForm');
  if (!form) return;

  editingEventId = eventToEdit?.id || null;
  form.reset();
  form.elements.namedItem('name').value = eventToEdit?.name || '';
  form.elements.namedItem('artist').value = eventToEdit?.artist || '';
  form.elements.namedItem('location').value = eventToEdit?.location || '';
  form.elements.namedItem('eventDate').value = formatDateTimeInput(eventToEdit?.eventDate);
  setSeatConfigVisible(!eventToEdit);
  updateSeatSummary();
  $('adminFormTitle').textContent = eventToEdit ? 'Edit event' : 'New event';
  $('adminSaveEvent').textContent = eventToEdit ? 'Save changes' : 'Create event';
  showAdminStatus('');
  form.classList.remove('hidden');
  form.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function closeAdminEventForm() {
  editingEventId = null;
  $('adminEventForm')?.classList.add('hidden');
  setSeatConfigVisible(true);
  showAdminStatus('');
}

function renderAdminEvents(events) {
  const list = $('adminEventList');
  if (!list) return;

  list.innerHTML = events.length
      ? events.map((event) => `
        <article class="admin-event-row">
          <div class="admin-event-summary">
            <strong>${escapeHtml(event.name)}</strong>
            <span>${escapeHtml(event.artist)} · ${escapeHtml(event.location)} · ${escapeHtml(new Date(event.eventDate).toLocaleString())}</span>
            <span>${event.seats?.length || 0} seats</span>
          </div>
          <div class="admin-event-actions">
            <button type="button" data-admin-edit="${escapeHtml(event.id)}">Edit</button>
            <button type="button" data-admin-delete="${escapeHtml(event.id)}">Unpublish</button>
          </div>
        </article>`).join('')
      : '<p class="empty-state">No events to manage.</p>';
}

async function loadAiUsage() {
  const box = $('aiUsage');
  if (!box || currentUser?.role !== 'ADMIN') return;

  box.textContent = 'Загружаю статистику AI…';
  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/ai/stats`, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const stats = await response.json();
    box.innerHTML = `AI сегодня (UTC <strong>${escapeHtml(stats.dateUtc)}</strong>):
      <strong>${stats.globalUsed}/${stats.globalLimit}</strong> запросов всего,
      вы: <strong>${stats.userUsed}/${stats.userLimit}</strong> ·
      LLM: ${stats.llmCalls} · кэш: ${stats.cacheHits} · fallback: ${stats.fallbacks} ·
      ошибки: ${stats.errors} · ~<strong>${stats.estimatedNeurons}</strong> neurons ·
      embeddings: ${stats.embeddingCalls} (~${stats.embeddingNeurons} neurons)`;
  } catch (error) {
    box.textContent = 'Статистика AI недоступна.';
  }
}

async function loadAdminEvents() {
  if (currentUser?.role !== 'ADMIN') return;

  const list = $('adminEventList');
  if (list) list.innerHTML = '<p class="empty-state">Loading events…</p>';

  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/events`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (!response.ok) throw new Error(`Could not load events (HTTP ${response.status})`);
    adminEvents = await response.json();
    renderAdminEvents(adminEvents);
  } catch (error) {
    if (list) list.innerHTML = `<p class="empty-state">${escapeHtml(error.message || 'Could not load events.')}</p>`;
  }
}

const MAX_SEATS = 500;


function toRowLabel(rowIndex) {
  let label = '';
  let value = rowIndex;
  do {
    label = String.fromCharCode(65 + (value % 26)) + label;
    value = Math.floor(value / 26) - 1;
  } while (value >= 0);
  return label;
}

function readSeatConfig() {
  const f = $('adminEventForm').elements;
  return {
    rows: Number(f.namedItem('seatRows').value),
    perRow: Number(f.namedItem('seatsPerRow').value),
    base: Number(f.namedItem('seatPrice').value),
    step: Number(f.namedItem('rowPriceStep').value) || 0
  };
}

function seatConfigError({ rows, perRow, base, step }) {
  if (!Number.isInteger(rows) || rows < 1 || !Number.isInteger(perRow) || perRow < 1) {
    return 'Укажите целое число рядов и мест в ряду';
  }
  if (rows * perRow > MAX_SEATS) return `Максимум ${MAX_SEATS} мест (сейчас ${rows * perRow})`;
  if (!(base > 0) || step < 0) return 'Цена должна быть больше 0, шаг цены — не меньше 0';
  return '';
}

function buildSeats({ rows, perRow, base, step }) {
  const seats = [];
  for (let r = 0; r < rows; r++) {
    const price = Math.round((base + r * step) * 100) / 100;
    for (let n = 1; n <= perRow; n++) {
      seats.push({ row: toRowLabel(r), seatNumber: n, price });
    }
  }
  return seats;
}

function setSeatConfigVisible(visible) {
  const box = $('adminSeatConfig');
  if (!box) return;
  box.classList.toggle('hidden', !visible);
  box.querySelectorAll('input').forEach((input) => { input.disabled = !visible; });
}

function updateSeatSummary() {
  const summary = $('adminSeatSummary');
  if (!summary) return;
  const config = readSeatConfig();
  const error = seatConfigError(config);
  summary.classList.toggle('error', Boolean(error));
  if (error) {
    summary.textContent = error;
    return;
  }
  const last = toRowLabel(config.rows - 1);
  const max = config.base + (config.rows - 1) * config.step;
  summary.textContent =
      `${config.rows * config.perRow} мест · ряды A–${last} · цена $${config.base.toFixed(2)}–$${max.toFixed(2)}`;
}

async function saveAdminEvent(form) {
  const formData = new FormData(form);
  const payload = {
    name: formData.get('name').trim(),
    artist: formData.get('artist').trim(),
    location: formData.get('location').trim(),
    eventDate: formData.get('eventDate')
  };

  if (!editingEventId) {
    const config = readSeatConfig();
    const error = seatConfigError(config);
    if (error) throw new Error(error);
    payload.seats = buildSeats(config);
  }

  const isEditing = Boolean(editingEventId);
  const endpoint = isEditing ? `${API_BASE}/api/events/${editingEventId}` : `${API_BASE}/api/events`;
  const accessToken = await ensureValidAccessToken();
  const response = await fetch(endpoint, {
    method: isEditing ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorPayload = await response.json().catch(() => ({}));
    throw new Error(errorPayload.message || errorPayload.error || `Save failed (HTTP ${response.status})`);
  }

  console.info('Event saved. Seats sent:', payload.seats ? payload.seats.length : 'unchanged');
  closeAdminEventForm();
  await Promise.all([loadEvents(), loadAdminEvents()]);
}

/* ---------- Profile & orders ---------- */

async function loadUserProfile() {
  if (!currentUser?.id) return;

  const profileOrders = $('profileOrders');

  try {
    const authToken = await ensureValidAccessToken();
    const headers = { Authorization: `Bearer ${authToken}` };

    const [profileResponse, ordersResponse] = await Promise.all([
      fetch(`${USER_API_BASE}/api/users/profile/${currentUser.id}`, { headers }),
      fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, { headers })
    ]);

    if (!profileResponse.ok) throw new Error('Failed to load user profile');

    const profile = await profileResponse.json();
    const orders = ordersResponse.ok ? await ordersResponse.json() : [];

    $('profileName').textContent = profile.fullName || profile.email || 'User';
    $('profileEmail').textContent = profile.email || '—';
    $('profileSince').textContent = profile.createdAt ? new Date(profile.createdAt).toLocaleDateString() : '—';

    if (!profileOrders) return;

    if (!orders.length) {
      profileOrders.innerHTML = '<div class="empty-orders">У вас пока нет заказов. После покупки билеты появятся здесь.</div>';
      return;
    }

    profileOrders.innerHTML = orders.map((order) => {
      const seatLabel = String(order.seatLabel || 'Место');
      const row = seatLabel.replace(/\d+$/, '');
      const seatNumber = Number(seatLabel.replace(/\D/g, '')) || 0;
      const eventId = order.eventId || '';

      return `
        <div class="order-item">
          <div>
            <strong>${escapeHtml(order.eventName || 'Мероприятие')}</strong>
            <small>${escapeHtml(seatLabel)} • ${escapeHtml(new Date(order.purchasedAt).toLocaleDateString())}</small>
          </div>
          <div class="order-actions">
            <div class="ticket-pill">$${Number(order.totalPrice || 0).toFixed(2)}</div>
            <button class="return-order-btn" type="button"
                    data-order-event-id="${escapeHtml(eventId)}"
                    data-order-seat-row="${escapeHtml(row)}"
                    data-order-seat-number="${seatNumber}">Вернуть</button>
          </div>
        </div>`;
    }).join('');

    profileOrders.querySelectorAll('.return-order-btn').forEach((button) => {
      button.addEventListener('click', async () => {
        const eventId = button.dataset.orderEventId;
        const row = button.dataset.orderSeatRow;
        const seatNumber = Number(button.dataset.orderSeatNumber || 0);
        if (!eventId || !row || !seatNumber) return;

        const event = allEvents.find((item) => String(item.id) === String(eventId));
        if (!event) {
          showPaymentNotification(false, 'Мероприятие не найдено', 'Не удалось найти событие для возврата.');
          return;
        }

        const returned = await ticketReturnClicked(event, row, seatNumber);
        if (returned) {
          await waitForOrderRemoval(eventId, `${row}${seatNumber}`);
        }
        await loadUserProfile();
      });
    });
  } catch (error) {
    console.error('Failed to load profile:', error);
    if (profileOrders) profileOrders.innerHTML = '<div class="empty-orders">Не удалось загрузить ваши заказы.</div>';
  }
}

function openProfileModal() {
  if (!currentUser) {
    openAuthModal('login');
    return;
  }
  loadUserProfile();
  $('profileModal').classList.remove('hidden');
  $('profileModal').setAttribute('aria-hidden', 'false');
}

function openAuthModal(mode = 'login') {
  authMode = mode;
  const modal = $('authModal');
  modal.classList.remove('hidden');
  modal.setAttribute('aria-hidden', 'false');

  $('registerNameField')?.classList.toggle('hidden', mode !== 'register');
  $('authSubmitBtn').textContent = mode === 'register' ? 'Create account' : 'Sign in';
  document.querySelectorAll('.auth-tab').forEach((tab) => {
    tab.classList.toggle('active', tab.dataset.authView === mode);
  });
}

/* ---------- Owned seats (source of truth: /api/users/{id}/orders) ---------- */

async function loadOwnedSeats() {
  if (!currentUser?.id) {
    ownedSeats = new Map();
    return;
  }

  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const orders = await response.json();
    const next = new Map();
    orders.forEach((order) => {
      const key = String(order.eventId);
      if (!next.has(key)) next.set(key, new Set());
      next.get(key).add(String(order.seatLabel || '').trim().toUpperCase());
    });
    ownedSeats = next;
  } catch (error) {
    console.warn('Could not load owned seats:', error);
  }
}

function isUserOwnedSeat(eventId, row, seatNumber) {
  return ownedSeats.get(String(eventId))?.has(getSeatKey(row, seatNumber)) || false;
}

function removeUserOwnedSeat(eventId, row, seatNumber) {
  const key = String(eventId);
  const seats = ownedSeats.get(key);
  if (!seats) return;
  seats.delete(getSeatKey(row, seatNumber));
  if (!seats.size) ownedSeats.delete(key);
}

async function orderStillExists(eventId, seatLabel) {
  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    if (!response.ok) return true;

    const orders = await response.json();
    return orders.some((order) =>
        String(order.eventId) === String(eventId) && String(order.seatLabel) === String(seatLabel));
  } catch (error) {
    return true;
  }
}

async function waitForOrderRemoval(eventId, seatLabel, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (!(await orderStillExists(eventId, seatLabel))) return true;
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  return false;
}

async function ticketReturnClicked(event, row, seatNumber) {
  if (!isUserOwnedSeat(event.id, row, seatNumber) || !currentUser?.id) return false;

  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${BOOKING_API_BASE}/api/bookings/return`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ eventId: event.id, seatRow: row, seatNumber: Number(seatNumber) })
    });

    if (!response.ok) throw new Error((await response.text()) || 'Ticket return failed');

    removeUserOwnedSeat(event.id, row, seatNumber);
    showPaymentNotification(true, 'Билет возвращён', `${getSeatKey(row, seatNumber)} снова доступен.`);
    await loadEvents();
    const refreshed = allEvents.find((e) => String(e.id) === String(event.id));
    if (refreshed && !$('eventModal').classList.contains('hidden')) openEventModal(refreshed);
    return true;
  } catch (error) {
    console.error('Ticket return failed:', error);
    showPaymentNotification(false, 'Возврат не выполнен', 'Билет не удалось вернуть сейчас.');
    return false;
  }
}

/* ---------- Payment ---------- */

function openPaymentModal() {
  if (!selectedEvent || selectedSeats.length === 0) {
    showPaymentNotification(false);
    return;
  }

  if (!currentUser) {
    openAuthModal('login');
    const authStatus = $('authStatus');
    if (authStatus) {
      authStatus.textContent = 'Please sign in before purchasing tickets.';
      authStatus.classList.add('visible');
    }
    return;
  }

  const seats = selectedSeats.filter((s) => s.eventId === selectedEvent.id);
  const total = seats.reduce((sum, s) => sum + Number(s.price || 0), 0);
  $('paymentSummary').textContent = `${seats.map((s) => s.label).join(', ')} • $${total}`;
  $('paymentModal').classList.remove('hidden');
}

function markSeatAsPaying(eventId, row, seatNumber) {
  const id = String(eventId);
  if (!seatsBeingPaid.has(id)) seatsBeingPaid.set(id, new Set());
  seatsBeingPaid.get(id).add(getSeatKey(row, seatNumber));
}

function unmarkSeatAsPaying(eventId, row, seatNumber) {
  const id = String(eventId);
  const eventSeats = seatsBeingPaid.get(id);
  if (!eventSeats) return;
  eventSeats.delete(getSeatKey(row, seatNumber));
  if (eventSeats.size === 0) seatsBeingPaid.delete(id);
}

async function pollBookingStatus(bookingId) {
  const deadline = Date.now() + 20000;

  while (Date.now() < deadline) {
    try {
      const accessToken = await ensureValidAccessToken();
      const response = await fetch(`${BOOKING_API_BASE}/api/bookings/${bookingId}/status`, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      if (!response.ok) return 'PENDING';

      const payload = await response.json();
      if (payload.status === 'PAID' || payload.status === 'CANCELLED') return payload.status;
    } catch (error) {
      console.error('Status poll error:', error);
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  return 'PENDING';
}

async function handlePaymentSubmit(paymentForm) {
  if (!selectedEvent || selectedSeats.length === 0) {
    showPaymentNotification(false);
    return;
  }

  const paymentEvent = selectedEvent;
  const paymentSeats = selectedSeats.filter((s) => s.eventId === paymentEvent.id).map((s) => ({ ...s }));
  const submitButton = paymentForm.querySelector('button[type="submit"]');
  const originalText = submitButton.textContent;

  submitButton.disabled = true;
  submitButton.textContent = 'Processing...';

  paymentSeats.forEach((s) => markSeatAsPaying(paymentEvent.id, s.row, s.seatNumber));
  openEventModal(paymentEvent);

  try {
    const results = [];

    for (const seat of paymentSeats) {
      const accessToken = await ensureValidAccessToken();
      const bookingResponse = await fetch(`${BOOKING_API_BASE}/api/bookings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
        body: JSON.stringify({ eventId: paymentEvent.id, seatRow: seat.row, seatNumber: seat.seatNumber })
      });

      if (!bookingResponse.ok) throw new Error((await bookingResponse.text()) || 'Booking failed');

      const bookingData = await bookingResponse.json();
      results.push({ seat, status: await pollBookingStatus(bookingData.bookingId) });
    }

    const paid = results.filter((r) => r.status === 'PAID');

    if (paid.length > 0) {
      for (const { seat } of paid) {
        try {
          const authToken = await ensureValidAccessToken();
          await fetch(`${USER_API_BASE}/api/users/orders`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
            body: JSON.stringify({
              eventId: paymentEvent.id,
              eventName: paymentEvent.name,
              seatLabel: `${seat.row}${seat.seatNumber}`,
              totalPrice: Number(seat.price || 0)
            })
          });
        } catch (orderError) {
          console.warn('Could not store order history:', orderError);
        }
      }
      await loadOwnedSeats();

      showPaymentNotification(true, 'Платёж успешно завершён', 'Билет подтверждён.');
      closeModal('paymentModal');
      selectedSeats = selectedSeats.filter((s) => s.eventId !== paymentEvent.id);
      selectedEvent = null;
      updateBuyButton();
      return;
    }

    showPaymentNotification(false);
  } catch (error) {
    console.error('Booking/payment error:', error);
    showPaymentNotification(false);
  } finally {
    paymentSeats.forEach((s) => unmarkSeatAsPaying(paymentEvent.id, s.row, s.seatNumber));
    submitButton.disabled = false;
    submitButton.textContent = originalText;
    await loadEvents();
    if (selectedEvent && !$('eventModal').classList.contains('hidden')) {
      const refreshed = allEvents.find((e) => String(e.id) === String(selectedEvent.id));
      if (refreshed) openEventModal(refreshed);
    }
  }
}

/* ---------- Loading & search ---------- */

async function searchEvents(event) {
  event.preventDefault();

  const params = new URLSearchParams();
  const artist = $('artistFilter').value.trim();
  const location = $('locationFilter').value.trim();
  const date = $('dateFilter').value;

  if (artist) params.set('artist', artist);
  if (location) params.set('location', location);
  if (date) params.set('date', date);

  try {
    const response = await fetch(`${API_BASE}/api/events?${params.toString()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    allEvents = await response.json();
    await hydrateSeatStatusesForEvents(allEvents);
    currentPage = 1;
    refreshView('Ничего не найдено.');
  } catch (error) {
    console.error('Search failed:', error);
    allEvents = [];
    refreshView('Не удалось загрузить мероприятия. Проверьте доступность API.');
  }
}

async function loadEvents() {
  try {
    const response = await fetch(`${API_BASE}/api/events`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    allEvents = await response.json();
    await hydrateSeatStatusesForEvents(allEvents);
    currentPage = 1;
    refreshView();
  } catch (error) {
    console.error('Failed to load events:', error);
    allEvents = [];
    refreshView('Не удалось загрузить мероприятия. Проверьте доступность API.');
  }
}

/* ---------- AI assistant ---------- */

function appendAiMessage(role, text) {
  const box = $('aiChatMessages');
  if (!box) return null;

  const element = document.createElement('div');
  element.className = `ai-message ${role}`;
  element.textContent = text;
  box.appendChild(element);
  box.scrollTop = box.scrollHeight;
  return element;
}

function appendAiAnswer(answer, events) {
  const box = $('aiChatMessages');
  if (!box) return;

  const bubble = document.createElement('div');
  bubble.className = 'ai-message assistant';

  if (answer) {
    const intro = document.createElement('p');
    intro.className = 'ai-answer-intro';
    intro.textContent = answer;
    bubble.appendChild(intro);
  }

  if (events.length) {
    const list = document.createElement('ul');
    list.className = 'ai-answer-list';

    events.forEach((event) => {
      const item = document.createElement('li');
      item.className = 'ai-answer-item';
      item.dataset.aiEvent = event.id;
      item.tabIndex = 0;

      const thumb = document.createElement('div');
      thumb.className = 'ai-answer-thumb';
      if (event.imageUrl) {
        thumb.style.backgroundImage = `url("${event.imageUrl}")`;
      }

      const body = document.createElement('div');
      body.className = 'ai-answer-body';

      const name = document.createElement('strong');
      name.textContent = event.name;

      const meta = document.createElement('span');
      meta.textContent = `${event.location} · ${formatLongDate(event.eventDate)}`;

      const price = document.createElement('span');
      price.textContent = `от $${Number(event.priceFrom || 0).toFixed(2)}`;

      body.append(name, meta, price);
      item.append(thumb, body);
      list.appendChild(item);
    });

    bubble.appendChild(list);
  }

  box.appendChild(bubble);
  box.scrollTop = box.scrollHeight;
}

async function openEventById(eventId) {
  let event = allEvents.find((item) => String(item.id) === String(eventId));
  if (!event) {
    try {
      const response = await fetch(`${API_BASE}/api/events`);
      if (response.ok) {
        const events = await response.json();
        event = events.find((item) => String(item.id) === String(eventId));
      }
    } catch (error) {
      console.warn('Could not load event for AI card:', error);
    }
  }
  if (event) openEventModal(event);
}

async function askAi(message) {
  const submit = $('aiChatSubmit');
  appendAiMessage('user', message);

  if (!currentUser) {
    appendAiMessage('assistant', 'Чтобы пользоваться AI-ассистентом, войдите в аккаунт.');
    openAuthModal('login');
    return;
  }

  const pending = appendAiMessage('assistant', 'Ищу события…');
  if (submit) submit.disabled = true;

  try {
    const accessToken = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ message })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.message || `AI request failed (HTTP ${response.status})`);
    }

    pending.remove();
    appendAiAnswer(payload.answer || 'Не удалось сформировать ответ.', payload.events || []);
  } catch (error) {
    pending.remove();
    appendAiMessage('error', error.message || 'Ассистент сейчас недоступен.');
  } finally {
    if (submit) submit.disabled = false;
  }
}

/* ---------- Init ---------- */

document.addEventListener('DOMContentLoaded', () => {
  currentUser = readCurrentUser();
  persistCurrentUser(currentUser);


  document.addEventListener('click', (e) => {
    const trigger = e.target.closest('[data-open-event]');
    if (!trigger) return;
    const found = allEvents.find((item) => String(item.id) === trigger.dataset.openEvent);
    if (found) openEventModal(found);
  });

  document.addEventListener('click', (e) => {
    const aiCard = e.target.closest('[data-ai-event]');
    if (aiCard) openEventById(aiCard.dataset.aiEvent);
  });

  $('aiChatForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = $('aiChatInput');
    const message = input.value.trim();
    if (!message) return;
    input.value = '';
    await askAi(message);
  });


  $('eventPagination')?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-page]');
    if (!button || button.disabled) return;
    currentPage = Number(button.dataset.page);
    refreshView();
    $('eventsSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });


  ['auth', 'profile', 'event', 'payment'].forEach((name) => {
    document.querySelectorAll(`[data-close="${name}"]`).forEach((button) => {
      button.addEventListener('click', () => closeModal(`${name}Modal`));
    });
  });

  $('profileLogoutBtn')?.addEventListener('click', () => {
    logoutUser();
    closeModal('profileModal');
  });

  $('authToggleBtn')?.addEventListener('click', () => {
    if (currentUser) openProfileModal();
    else openAuthModal('login');
  });

  document.querySelectorAll('.auth-tab').forEach((tab) => {
    tab.addEventListener('click', () => openAuthModal(tab.dataset.authView || 'login'));
  });

  $('authForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();

    const email = $('authEmail')?.value?.trim();
    const password = $('authPassword')?.value || '';
    const fullName = $('registerName')?.value?.trim() || '';
    const endpoint = authMode === 'register' ? 'register' : 'login';
    const authStatus = $('authStatus');

    try {
      const response = await fetch(`${USER_API_BASE}/api/users/${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, fullName, password })
      });

      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.message || payload.error || 'Authentication failed');

      persistCurrentUser(payload);

      if (authStatus) {
        authStatus.textContent = authMode === 'register'
            ? 'Регистрация успешна. Теперь можно покупать билеты.'
            : 'С возвращением!';
        authStatus.classList.add('visible');
      }

      await loadOwnedSeats();
      closeModal('authModal');
      if (selectedEvent && selectedSeats.length > 0) openPaymentModal();
    } catch (error) {
      console.error('Auth failed:', error);
      if (authStatus) {
        authStatus.textContent = error.message || 'Не удалось выполнить вход';
        authStatus.classList.add('visible');
      }
    }
  });


  const adminPanel = $('adminPanel');
  $('adminPanelToggle')?.addEventListener('click', async () => {
    const opening = adminPanel.classList.contains('hidden');
    adminPanel.classList.toggle('hidden', !opening);
    if (opening) {
      await Promise.all([loadAdminEvents(), loadAiUsage()]);
      adminPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });

  $('adminCreateEvent')?.addEventListener('click', () => {
    $('adminImportForm')?.classList.add('hidden');
    openAdminEventForm();
  });
  $('adminCancelEdit')?.addEventListener('click', closeAdminEventForm);

  $('adminImportToggle')?.addEventListener('click', () => {
    const form = $('adminImportForm');
    if (!form) return;
    const opening = form.classList.contains('hidden');
    form.classList.toggle('hidden', !opening);
    if (opening) {
      closeAdminEventForm();
      const status = $('adminImportStatus');
      if (status) status.textContent = '';
      form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });

  $('adminImportCancel')?.addEventListener('click', () => $('adminImportForm')?.classList.add('hidden'));

  $('adminImportForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = $('adminImportSubmit');
    const status = $('adminImportStatus');
    const params = new URLSearchParams();

    const keyword = form.elements.namedItem('keyword').value.trim();
    const countryCode = form.elements.namedItem('countryCode').value.trim().toUpperCase();
    const city = form.elements.namedItem('city').value.trim();
    const classificationName = form.elements.namedItem('classificationName').value.trim();
    const sort = form.elements.namedItem('sort').value;
    const size = Number(form.elements.namedItem('size').value) || 20;
    const pages = Number(form.elements.namedItem('pages').value) || 1;

    if (keyword) params.set('keyword', keyword);
    if (countryCode) params.set('countryCode', countryCode);
    if (city) params.set('city', city);
    if (classificationName) params.set('classificationName', classificationName);
    if (sort) params.set('sort', sort);
    params.set('size', size);
    params.set('pages', pages);

    submit.disabled = true;
    status.classList.remove('error');
    status.textContent = 'Importing events from Ticketmaster…';

    try {
      const accessToken = await ensureValidAccessToken();
      const response = await fetch(`${API_BASE}/api/events/import/ticketmaster?${params.toString()}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.message || `Import failed (HTTP ${response.status})`);

      status.textContent = `Imported ${payload.imported} of ${payload.fetched} events. `
          + `Already imported: ${payload.skippedExisting}, skipped without confirmed date: ${payload.skippedInvalid}.`;
      await Promise.all([loadEvents(), loadAdminEvents()]);
    } catch (error) {
      status.textContent = error.message || 'Could not import events.';
      status.classList.add('error');
    } finally {
      submit.disabled = false;
    }
  });
  $('adminSeatConfig')?.addEventListener('input', updateSeatSummary);
  updateSeatSummary();

  $('adminEventForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const saveButton = $('adminSaveEvent');
    saveButton.disabled = true;
    showAdminStatus('Saving event…');
    try {
      await saveAdminEvent(event.currentTarget);
    } catch (error) {
      showAdminStatus(error.message || 'Could not save event.', true);
    } finally {
      saveButton.disabled = false;
    }
  });

  $('adminEventList')?.addEventListener('click', async (event) => {
    const editButton = event.target.closest('[data-admin-edit]');
    if (editButton) {
      const item = adminEvents.find((e) => String(e.id) === editButton.dataset.adminEdit);
      if (item) openAdminEventForm(item);
      return;
    }

    const deleteButton = event.target.closest('[data-admin-delete]');
    if (!deleteButton || !window.confirm('Unpublish this event?')) return;

    deleteButton.disabled = true;
    try {
      const accessToken = await ensureValidAccessToken();
      const response = await fetch(`${API_BASE}/api/events/${deleteButton.dataset.adminDelete}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) {
        const errorPayload = await response.json().catch(() => ({}));
        throw new Error(errorPayload.message || errorPayload.error || `Unpublish failed (HTTP ${response.status})`);
      }
      await Promise.all([loadEvents(), loadAdminEvents()]);
    } catch (error) {
      window.alert(error.message || 'Could not unpublish event.');
      deleteButton.disabled = false;
    }
  });

  $('searchForm')?.addEventListener('submit', searchEvents);

  const buyButton = $('buyTicketBtn');
  if (buyButton) {
    buyButton.disabled = true;
    buyButton.addEventListener('click', openPaymentModal);
  }

  const paymentForm = $('paymentForm');
  paymentForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    handlePaymentSubmit(paymentForm);
  });

  $('cardNumber')?.addEventListener('input', (event) => {
    const value = event.target.value.replace(/\D/g, '').slice(0, 16);
    event.target.value = value.replace(/(.{4})/g, '$1 ').trim();
  });

  $('expiry')?.addEventListener('input', (event) => {
    let value = event.target.value.replace(/\D/g, '').slice(0, 4);
    if (value.length > 2) value = `${value.slice(0, 2)}/${value.slice(2)}`;
    event.target.value = value;
  });

  const modals = document.querySelectorAll('.modal');
  const modalObserver = new MutationObserver(() => {
    const anyOpen = [...modals].some((modal) => !modal.classList.contains('hidden'));
    document.body.classList.toggle('modal-open', anyOpen);
  });
  modals.forEach((modal) => modalObserver.observe(modal, { attributes: true, attributeFilter: ['class'] }));

  loadOwnedSeats();
  loadEvents();
});