import { renderHeader } from '../components/header.js?v=20260403081123';
import { profile } from '../mock-data.js?v=20260403081123';
import { state } from '../state.js?v=20260403081123';
import { loadProfileParams, profileFieldDefs, saveProfileParams } from '../services/user-profile-params.js?v=20260403081123';

export const pageMeta = { id: 'profile-view', title: 'Профиль' };

function formatDateTime(timeMs) {
  if (!timeMs) return 'ещё не заполнено';
  return new Date(timeMs).toLocaleString('ru-RU');
}

function getDisplayName(fieldMap) {
  const firstName = fieldMap.get('first_name')?.value?.trim() || '';
  const lastName = fieldMap.get('last_name')?.value?.trim() || '';
  const fullName = `${firstName} ${lastName}`.trim();
  return fullName || profile.name;
}

export function render({ navigate }) {
  const login = state.session.login || profile.login;

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Профиль',
      rightActions: [
        { label: 'Кошелёк', onClick: () => navigate('wallet-view') },
        { label: 'Настройки', onClick: () => navigate('settings-view') },
      ],
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';

  const topRow = document.createElement('div');
  topRow.className = 'row';
  topRow.innerHTML = `
    <div class="row" style="gap:12px; align-items:center;">
      <div class="avatar large">${profile.avatarInitials}</div>
      <div>
        <h2 style="font-size:22px; margin-bottom:2px;" data-profile-name="true">${profile.name}</h2>
        <p class="meta-muted">${login}</p>
      </div>
    </div>
    <button class="primary-btn" type="button" data-open-edit="true">Обновить</button>
  `;

  const hint = document.createElement('div');
  hint.className = 'card profile-data-help';
  hint.innerHTML = `
    <div class="meta-muted">Личные данные пользователя</div>
    <p>Поля ниже читаются из реальных пользовательских параметров сервера (ListUserParams). Кнопка «Обновить» отправляет UpsertUserParam, что добавляет новую запись в блокчейн.</p>
  `;

  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка параметров...';

  const listWrap = document.createElement('div');
  listWrap.className = 'stack profile-param-list';

  const editModal = document.createElement('div');
  editModal.className = 'profile-help-modal';
  editModal.hidden = true;
  editModal.innerHTML = `
    <div class="profile-help-backdrop" data-close="true"></div>
    <div class="profile-help-dialog card" role="dialog" aria-modal="true" aria-labelledby="profile-edit-title" tabindex="-1">
      <div class="row" style="align-items:flex-start;">
        <div>
          <div class="meta-muted" style="margin-bottom:4px;">Обновление личных данных</div>
          <h3 id="profile-edit-title" style="font-size:18px;">Редактирование профиля</h3>
        </div>
        <button class="icon-btn profile-help-close" type="button" aria-label="Закрыть">✕</button>
      </div>
      <p class="profile-help-text">После сохранения по каждому полю отправляется `UpsertUserParam`. Сервер хранит историю, а на экране показывается самое свежее значение по времени.</p>
      <form class="stack" data-profile-form="true"></form>
      <div class="row">
        <button class="ghost-btn" type="button" data-cancel-edit="true">Отмена</button>
        <button class="primary-btn" type="button" data-save-profile="true">Сохранить</button>
      </div>
    </div>
  `;

  const profileNameEl = topRow.querySelector('[data-profile-name="true"]');
  const openEditBtn = topRow.querySelector('[data-open-edit="true"]');
  const formEl = editModal.querySelector('[data-profile-form="true"]');
  const dialogEl = editModal.querySelector('.profile-help-dialog');
  const saveBtn = editModal.querySelector('[data-save-profile="true"]');

  let currentFields = profileFieldDefs.map((field) => ({ ...field, value: '', timeMs: 0 }));

  function renderParams(fields) {
    const fieldMap = new Map(fields.map((field) => [field.key, field]));
    profileNameEl.textContent = getDisplayName(fieldMap);

    listWrap.innerHTML = '';
    fields.forEach((field) => {
      const row = document.createElement('div');
      row.className = 'card profile-param-item';
      row.innerHTML = `
        <div class="profile-param-head">
          <span class="meta-muted">${field.label}</span>
          <span class="meta-muted">${field.key}</span>
        </div>
        <div class="profile-param-value">${field.value || '—'}</div>
        <div class="meta-muted profile-param-time">Обновлено: ${formatDateTime(field.timeMs)}</div>
      `;
      listWrap.append(row);
    });
  }

  async function refreshParams() {
    status.className = 'status-line';
    status.textContent = 'Загрузка параметров...';
    openEditBtn.disabled = true;

    try {
      const fields = await loadProfileParams(login);
      currentFields = fields;
      renderParams(fields);
      status.className = 'status-line is-available';
      status.textContent = 'Актуальные параметры загружены с сервера.';
    } catch (error) {
      renderParams(currentFields);
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось загрузить параметры: ${error.message || 'ошибка сети'}`;
    } finally {
      openEditBtn.disabled = false;
    }
  }

  function closeEditModal() {
    editModal.hidden = true;
  }

  function openEditModal() {
    formEl.innerHTML = '';

    currentFields.forEach((field) => {
      const fieldWrap = document.createElement('label');
      fieldWrap.className = 'stack';
      fieldWrap.innerHTML = `
        <span class="field-label">${field.label}</span>
        <input class="input" type="text" name="${field.key}" placeholder="${field.placeholder || ''}" value="${field.value || ''}" />
      `;
      formEl.append(fieldWrap);
    });

    editModal.hidden = false;
    dialogEl.focus();
  }

  async function saveChanges() {
    const valuesByKey = {};
    currentFields.forEach((field) => {
      const input = formEl.querySelector(`input[name="${field.key}"]`);
      valuesByKey[field.key] = input instanceof HTMLInputElement ? input.value : '';
    });

    saveBtn.disabled = true;
    try {
      await saveProfileParams(login, valuesByKey);
      closeEditModal();
      await refreshParams();
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось сохранить: ${error.message || 'ошибка сети'}`;
    } finally {
      saveBtn.disabled = false;
    }
  }

  openEditBtn.addEventListener('click', openEditModal);
  saveBtn.addEventListener('click', saveChanges);
  editModal.querySelector('[data-cancel-edit="true"]').addEventListener('click', closeEditModal);

  editModal.addEventListener('click', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && (target.dataset.close === 'true' || target.classList.contains('profile-help-close'))) {
      closeEditModal();
    }
  });

  editModal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeEditModal();
  });

  card.append(topRow, hint, status, listWrap);
  screen.append(card, editModal);

  refreshParams();

  return screen;
}
