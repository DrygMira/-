import { renderHeader } from '../components/header.js?v=20260327192619';
import {
  authorizeSession,
  refreshSessions,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js?v=20260327192619';

export const pageMeta = { id: 'registration-keys-view', title: 'Сохранение ключей', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const normalizedLogin = (state.registrationDraft.login || '').trim();
  const displayLogin = normalizedLogin || '@new.user';

  const card = document.createElement('div');
  card.className = 'card stack';

  const title = document.createElement('p');
  title.className = 'auth-copy';
  title.textContent = `Поздравляю, логин ${displayLogin} зарегистрирован.`;

  const question = document.createElement('p');
  question.className = 'auth-copy';
  question.textContent = 'Ключи считаются из пароля (SHA-256 + суффиксы root.key/dev.key/bch.key). В IndexedDB сохраняются только выбранные ключи и всегда device key.';

  const rootRow = document.createElement('label');
  rootRow.className = 'checkbox-row';
  rootRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveRoot ? 'checked' : ''} disabled /> <span>root key</span>`;

  const blockchainRow = document.createElement('label');
  blockchainRow.className = 'checkbox-row';
  blockchainRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveBlockchain ? 'checked' : ''} disabled /> <span>blockchain key</span>`;

  const deviceRow = document.createElement('label');
  deviceRow.className = 'checkbox-row';
  deviceRow.innerHTML = '<input type="checkbox" checked disabled /> <span>device key</span>';

  card.append(title, question, rootRow, deviceRow, blockchainRow);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const cancelButton = document.createElement('button');
  cancelButton.className = 'ghost-btn';
  cancelButton.type = 'button';
  cancelButton.textContent = 'Отмена';
  cancelButton.addEventListener('click', () => navigate('start-view'));

  const okButton = document.createElement('button');
  okButton.className = 'primary-btn';
  okButton.type = 'button';
  okButton.textContent = 'OK';
  okButton.addEventListener('click', async () => {
    try {
      authorizeSession({
        login: state.registrationDraft.login,
        sessionId: state.registrationDraft.sessionId,
        storagePwd: state.registrationDraft.storagePwd,
      });
      await refreshSessions();
      setAuthInfo('Регистрация завершена, список сессий загружен.');
      navigate('profile-view');
    } catch (error) {
      setAuthError(error.message);
      window.alert(error.message);
    }
  });

  actions.append(cancelButton, okButton);

  screen.append(
    renderHeader({
      title: 'Сохранение ключей',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    card,
    actions,
  );

  return screen;
}
