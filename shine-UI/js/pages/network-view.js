import { renderHeader } from '../components/header.js?v=20260403081123';
import { authService, state } from '../state.js?v=20260403081123';

export const pageMeta = { id: 'network-view', title: 'Связи' };

function makeNode(name, cls = '') {
  const n = document.createElement('div');
  n.className = `node ${cls}`.trim();
  n.innerHTML = `<div class="node-dot">${(name[0] || '?').toUpperCase()}</div><div class="node-label">${name}</div>`;
  return n;
}

export function render() {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const board = document.createElement('div');
  board.className = 'network-board';
  board.style.height = 'calc(100dvh - 170px)';

  const note = document.createElement('p');
  note.className = 'meta-muted';
  note.textContent = 'Загрузка связей...';

  const load = async (centerLogin) => {
    try {
      const graph = await authService.getUserConnectionsGraph(centerLogin || state.session.login);
      board.innerHTML = '';
      const center = makeNode(graph.login || state.session.login, 'center');
      center.style.left = '50%';
      center.style.top = '50%';
      board.append(center);

      const all = [...new Set([...(graph.outFriends || []), ...(graph.inFriends || [])])];
      const left = all.slice(0, Math.ceil(all.length / 2));
      const right = all.slice(Math.ceil(all.length / 2));

      const mk = (name, side, idx, total) => {
        const node = makeNode(name);
        const y = 15 + ((idx + 1) * 70) / (Math.max(total, 1) + 1);
        node.style.left = side === 'left' ? '20%' : '80%';
        node.style.top = `${y}%`;
        node.addEventListener('click', () => load(name));
        board.append(node);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', '50');
        line.setAttribute('y1', '50');
        line.setAttribute('x2', side === 'left' ? '20' : '80');
        line.setAttribute('y2', String(y));
        line.setAttribute('stroke', 'rgba(125,170,255,0.6)');
        line.setAttribute('stroke-width', '1.5');
        return line;
      };

      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('class', 'network-svg');
      svg.setAttribute('viewBox', '0 0 100 100');
      svg.setAttribute('preserveAspectRatio', 'none');

      left.forEach((name, i) => svg.append(mk(name, 'left', i, left.length)));
      right.forEach((name, i) => svg.append(mk(name, 'right', i, right.length)));
      board.prepend(svg);

      note.textContent = 'Нажмите на любой узел, чтобы построить связи выбранного пользователя.';
    } catch (e) {
      note.textContent = `Ошибка загрузки связей: ${e.message || 'unknown'}`;
    }
  };

  load();

  screen.append(renderHeader({ title: 'Связи' }), board, note);
  return screen;
}
