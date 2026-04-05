import { authService, state } from '../state.js?v=20260403081123';

export const profileFieldDefs = [
  { key: 'first_name', readKeys: ['first_name', 'name'], label: 'First name', placeholder: 'Введите имя' },
  { key: 'last_name', readKeys: ['last_name'], label: 'Last name', placeholder: 'Введите фамилию' },
  { key: 'address_physical', readKeys: ['address_physical'], label: 'Address physical', placeholder: 'Город, улица, дом' },
  { key: 'address_web', readKeys: ['address_web'], label: 'Address web', placeholder: 'Сайт или профиль' },
  { key: 'phone', readKeys: ['phone'], label: 'Phone', placeholder: '+7 ...' },
];

function normalizeItems(responsePayload) {
  const params = responsePayload?.params;
  if (!Array.isArray(params)) return [];
  return params
    .map((item) => ({
      param: String(item?.param || '').trim(),
      value: String(item?.value || ''),
      timeMs: Number(item?.time_ms || 0),
    }))
    .filter((item) => item.param);
}

function getLatestByAliases(items, aliases) {
  let latest = null;
  items.forEach((item) => {
    if (!aliases.includes(item.param)) return;
    if (!latest || item.timeMs >= latest.timeMs) {
      latest = item;
    }
  });
  return latest;
}

export async function loadProfileParams(login) {
  const payload = await authService.listUserParams(login);
  const items = normalizeItems(payload);

  return profileFieldDefs.map((field) => {
    const latest = getLatestByAliases(items, field.readKeys);
    return {
      key: field.key,
      label: field.label,
      placeholder: field.placeholder,
      value: latest?.value || '',
      timeMs: latest?.timeMs || 0,
    };
  });
}

export async function saveProfileParams(login, valuesByKey) {
  const storagePwd = state.session.storagePwdInMemory;
  if (!storagePwd) {
    throw new Error('Нет storagePwd в памяти сессии. Выполните вход заново.');
  }

  const baseTime = Date.now();

  for (let i = 0; i < profileFieldDefs.length; i += 1) {
    const field = profileFieldDefs[i];
    await authService.upsertUserParam({
      login,
      param: field.key,
      value: String(valuesByKey[field.key] || '').trim(),
      timeMs: baseTime + i,
      storagePwd,
    });
  }
}
