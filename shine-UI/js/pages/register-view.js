import { renderHeader } from '../components/header.js?v=20260327192619';
import {
  authService,
  clearAuthMessages,
  setAuthBusy,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js?v=20260327192619';

export const pageMeta = { id: 'register-view', title: 'Зарегистрироваться', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  clearAuthMessages();

  const form = document.createElement('div');
  form.className = 'card stack';

  const loginInput = document.createElement('input');
  loginInput.className = 'input';
  loginInput.type = 'text';
  loginInput.value = state.registrationDraft.login;
  loginInput.placeholder = 'Введите логин';

  const passwordInput = document.createElement('input');
  passwordInput.className = 'input';
  passwordInput.type = 'password';
  passwordInput.value = state.registrationDraft.password;
  passwordInput.placeholder = 'Введите пароль';

  const statusText = document.createElement('p');
  statusText.className = 'meta-muted';
  statusText.textContent = 'Проверка логина: не выполнена';

  const checkButton = document.createElement('button');
  checkButton.className = 'ghost-btn';
  checkButton.type = 'button';
  checkButton.textContent = 'Проверить логин';

  const saveRootRow = document.createElement('label');
  saveRootRow.className = 'checkbox-row';
  saveRootRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveRoot ? 'checked' : ''} /> <span>Сохранить root key</span>`;
  const saveRootInput = saveRootRow.querySelector('input');

  const saveBchRow = document.createElement('label');
  saveBchRow.className = 'checkbox-row';
  saveBchRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveBlockchain ? 'checked' : ''} /> <span>Сохранить blockchain key</span>`;
  const saveBchInput = saveBchRow.querySelector('input');

  const saveDevRow = document.createElement('label');
  saveDevRow.className = 'checkbox-row';
  saveDevRow.innerHTML = '<input type="checkbox" checked disabled /> <span>device key сохраняется всегда</span>';

  async function runAvailabilityCheck() {
    const login = loginInput.value.trim();
    if (!login) {
      statusText.textContent = 'Введите логин';
      return false;
    }

    checkButton.disabled = true;
    checkButton.textContent = 'Проверка...';
    try {
      await authService.reconnect(state.entrySettings.shineServer);
      const isFree = await authService.ensureLoginFree(login);
      statusText.textContent = isFree ? 'Логин свободен ✅' : 'Логин уже занят ❌';
      statusText.className = isFree ? 'is-available' : 'is-unavailable';
      return isFree;
    } catch (error) {
      statusText.textContent = error.message;
      statusText.className = 'is-unavailable';
      return false;
    } finally {
      checkButton.disabled = false;
      checkButton.textContent = 'Проверить логин';
    }
  }

  checkButton.addEventListener('click', runAvailabilityCheck);

  form.innerHTML = `
    <label class="stack"><span class="field-label">Логин</span></label>
    <label class="stack"><span class="field-label">Пароль</span></label>
  `;
  form.children[0].append(loginInput);
  form.children[1].append(passwordInput);
  form.append(checkButton, statusText, saveRootRow, saveBchRow, saveDevRow);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('start-view'));

  const nextButton = document.createElement('button');
  nextButton.className = 'primary-btn';
  nextButton.type = 'button';
  nextButton.textContent = 'Далее';
  nextButton.addEventListener('click', async () => {
    const isFree = await runAvailabilityCheck();
    if (!isFree) {
      setAuthError('Выберите свободный логин');
      return;
    }

    state.registrationDraft.login = loginInput.value.trim();
    state.registrationDraft.password = passwordInput.value;
    state.keyStorage.saveRoot = saveRootInput.checked;
    state.keyStorage.saveBlockchain = saveBchInput.checked;
    state.keyStorage.saveDevice = true;

    if (!state.registrationDraft.password) {
      window.alert('Введите пароль');
      return;
    }

    setAuthBusy(true);
    nextButton.disabled = true;
    nextButton.textContent = 'Создание...';
    setAuthError('');

    try {
      await authService.reconnect(state.entrySettings.shineServer);
      const result = await authService.register(
        state.registrationDraft.login,
        state.registrationDraft.password,
        {
          saveRoot: state.keyStorage.saveRoot,
          saveBlockchain: state.keyStorage.saveBlockchain,
          saveDevice: true,
        },
      );
      state.registrationDraft.sessionId = result.sessionId;
      state.registrationDraft.storagePwd = result.storagePwd;
      setAuthInfo(`Пользователь ${result.login} зарегистрирован`);
      navigate('registration-keys-view');
    } catch (error) {
      setAuthError(error.message);
      window.alert(error.message);
    } finally {
      setAuthBusy(false);
      nextButton.disabled = false;
      nextButton.textContent = 'Далее';
    }
  });

  actions.append(backButton, nextButton);

  screen.append(
    renderHeader({
      title: 'Зарегистрироваться',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    form,
    actions,
  );

  return screen;
}
