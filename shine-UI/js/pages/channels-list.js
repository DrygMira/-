import { renderHeader } from '../components/header.js?v=20260330001044';
import { channels } from '../mock-data.js?v=20260330001044';

export const pageMeta = { id: 'channels-list', title: 'Каналы' };

function openSimpleSubscribeModal(kindLabel) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channels-subscribe-modal">
      <div class="modal-card stack">
        <h3 style="font-size:18px;">${kindLabel}</h3>
        <label class="meta-muted" for="subscribe-input">Введите идентификатор</label>
        <input id="subscribe-input" class="input" placeholder="@login или #канал" />
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
          <button class="secondary-btn" id="sub-cancel">Отмена</button>
          <button class="primary-btn" id="sub-submit">Подписаться</button>
        </div>
      </div>
    </div>
  `;

  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#sub-cancel').addEventListener('click', close);
  root.querySelector('#sub-submit').addEventListener('click', close);
}

function renderChannelRow(channel, navigate) {
  const row = document.createElement('article');
  row.className = 'list-item';
  row.innerHTML = `
    <div class="avatar">${channel.initials}</div>
    <div>
      <strong># ${channel.name}</strong>
      <p class="meta-muted" style="margin-top:4px;">${channel.description}</p>
      <p class="meta-muted" style="margin-top:6px; color:#d8e3ff;">${channel.lastMessage}</p>
      <p class="meta-muted" style="margin-top:6px;">Владелец: ${channel.ownerName}</p>
    </div>
    <div style="display:grid; justify-items:end; gap:6px;">
      <span class="badge alt" style="padding:4px 8px; font-size:10px;">Канал</span>
      <span class="meta-muted">${channel.time}</span>
      <span class="unread">${channel.messagesCount}</span>
    </div>
  `;
  row.addEventListener('click', () => navigate(`channel-view/${channel.id}`));
  return row;
}

function renderSection(title, items, navigate) {
  const wrap = document.createElement('section');
  wrap.className = 'stack';

  const header = document.createElement('h3');
  header.className = 'section-title';
  header.textContent = title;

  wrap.append(header);

  items.forEach((channel) => {
    wrap.append(renderChannelRow(channel, navigate));
  });

  return wrap;
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Каналы',
      rightActions: [
        { label: 'Подписаться на человека', onClick: () => openSimpleSubscribeModal('Подписка на человека') },
        { label: 'Подписаться на канал', onClick: () => openSimpleSubscribeModal('Подписка на канал') },
      ],
    })
  );

  const ownChannels = channels
    .filter((channel) => channel.kind === 'own-personal' || channel.kind === 'own')
    .sort((a, b) => {
      if (a.kind === 'own-personal') return -1;
      if (b.kind === 'own-personal') return 1;
      return a.name.localeCompare(b.name, 'ru');
    });

  const followedUserChannels = channels.filter((channel) => channel.kind === 'followed-user-channel');
  const subscribedChannels = channels.filter((channel) => channel.kind === 'subscribed');

  const listWrap = document.createElement('div');
  listWrap.className = 'channels-scroll-wrap';

  const list = document.createElement('div');
  list.className = 'stack channels-groups';

  list.append(renderSection('Мои каналы', ownChannels, navigate));

  const dividerOne = document.createElement('hr');
  dividerOne.className = 'channels-divider';
  list.append(dividerOne);

  list.append(renderSection('Каналы пользователей, на кого вы подписаны', followedUserChannels, navigate));

  const dividerTwo = document.createElement('hr');
  dividerTwo.className = 'channels-divider';
  list.append(dividerTwo);

  list.append(renderSection('Каналы, на которые вы подписаны', subscribedChannels, navigate));

  const addChannelButton = document.createElement('button');
  addChannelButton.className = 'primary-btn';
  addChannelButton.textContent = 'Добавить канал';
  addChannelButton.addEventListener('click', () => navigate('add-channel-view'));

  list.append(addChannelButton);

  const scrollHint = document.createElement('div');
  scrollHint.className = 'channels-scroll-hint';

  listWrap.append(list, scrollHint);
  screen.append(listWrap);
  return screen;
}
