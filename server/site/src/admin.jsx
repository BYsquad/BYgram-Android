import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../../activity-api/admin.css';

const sections = [
  ['overview', 'Обзор', 'grid'],
  ['badges', 'Значки', 'badge'],
  ['licenses', 'Лицензии', 'key'],
  ['release', 'Релиз', 'download'],
  ['settings', 'Настройки', 'settings'],
];
const paths = {
  grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  badge: <><path d="m12 2 2.4 2.5 3.5-.1.8 3.4 3 1.8-1.2 3.3 1.2 3.3-3 1.8-.8 3.4-3.5-.1L12 22l-2.4-2.5-3.5.1-.8-3.4-3-1.8 1.2-3.3-1.2-3.3 3-1.8.8-3.4 3.5.1z"/><path d="m8.5 12 2.3 2.3 4.8-4.8"/></>,
  key: <><circle cx="8" cy="15" r="5"/><path d="m11.5 11.5 8-8 2 2-2 2 1.5 1.5-2.5 2.5-1.5-1.5-2 2"/></>,
  download: <><path d="M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1 1.4 1.1-2 3.5-1.7-.7a8 8 0 0 1-2.6 1.5l-.3 1.8h-4l-.3-1.8A8 8 0 0 1 7.4 19l-1.7.7-2-3.5 1.4-1.1a8 8 0 0 1 0-3l-1.4-1.1 2-3.5 1.7.7A8 8 0 0 1 10 6.7L10.3 5h4l.3 1.7a8 8 0 0 1 2.6 1.5l1.7-.7 2 3.5-1.4 1.1a8 8 0 0 1-.1 2.9Z"/></>,
  users: <><circle cx="9" cy="8" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5"/></>,
  arrow: <path d="M5 12h14m-6-6 6 6-6 6"/>,
  refresh: <><path d="M20 11a8 8 0 1 0-2 6"/><path d="M20 4v7h-7"/></>,
  music: <><path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/></>,
};
function Icon({ name, size = 20 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
const values = (form) => Object.fromEntries(new FormData(form));

async function api(body) {
  const response = await fetch('admin-api.php', {
    method: body ? 'POST' : 'GET', credentials: 'same-origin',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  if (response.status === 401) { location.reload(); throw new Error('Войдите через Telegram'); }
  const result = await response.json();
  if (!result.ok) throw new Error(result.error || 'Не удалось выполнить запрос');
  return result.data;
}

function Overview({ data, go }) {
  const counts = [
    ['Значки', Object.entries(data.badges).filter(([, value]) => value === true || value?.bygram).length, 'badge', 'badges'],
    ['Лицензии', data.licenses.filter((item) => item.status === 'active').length, 'key', 'licenses'],
    ['Устройства', data.devices.length, 'users', 'licenses'],
  ];
  return <div className="overview">
    <div className="hero"><div className="hero-copy"><span className="eyebrow">ПАНЕЛЬ УПРАВЛЕНИЯ / BYGRAM</span><h2>Всё важное<br/><em>в одном месте.</em></h2><p>Значки, Full-доступ и публикация APK. Выберите задачу и переходите к делу.</p><button className="hero-action" onClick={() => go('badges')}>Управлять значками <Icon name="arrow"/></button></div><div className="hero-art" aria-hidden="true"><span>BY</span><i className="orbit orbit-one"/><i className="orbit orbit-two"/></div></div>
    <div className="stat-grid">{counts.map(([label, value, icon, page]) => <button className="stat" key={label} onClick={() => go(page)}><span className="stat-icon"><Icon name={icon}/></span><span className="stat-value">{value}</span><span className="stat-label">{label}</span><Icon name="arrow" size={17}/></button>)}</div>
    <div className="card release-summary"><span className="mini-label">ТЕКУЩИЙ РЕЛИЗ</span><div className="row"><div><h3>BYgram {data.release.version}</h3><p>{data.release.status} · {data.release.size}</p></div><button className="button secondary" onClick={() => go('release')}>Открыть релиз <Icon name="arrow" size={17}/></button></div></div>
  </div>;
}

function Badges({ items, mutate }) {
  const badges = Object.entries(items).filter(([, value]) => value === true || value?.bygram);
  return <div className="content-grid"><form className="card form-card" onSubmit={(event) => { event.preventDefault(); mutate({ ...values(event.currentTarget), action: 'badge.save' }); }}>
    <span className="mini-label">НОВОЕ НАЗНАЧЕНИЕ</span><h3>Выдать значок</h3><p className="muted">Значок появится рядом с именем после обновления данных в приложении.</p>
    <label>Кому<select name="peer_type"><option value="user">Пользователю</option><option value="chat">Каналу или группе</option></select></label>
    <label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="-?[0-9]{1,20}" placeholder="Например, 123456789" required /></label>
    <p className="hint">Для канала или группы можно использовать ID с префиксом −100.</p><button className="button">Выдать значок <Icon name="arrow" size={17}/></button>
  </form><section className="card list-card"><div className="section-heading"><div><span className="mini-label">АКТИВНЫЕ</span><h3>Назначенные значки</h3></div><span className="count">{badges.length}</span></div>
    {badges.length ? <div className="item-list">{badges.map(([key]) => { const [type, id] = key.includes(':') ? key.split(':') : ['user', key]; return <div className="list-item" key={key}><span className="item-icon"><Icon name="badge"/></span><div><strong>{type === 'chat' ? 'Канал или группа' : 'Пользователь'}</strong><small>ID {id}</small></div><button className="text-button danger" aria-label={`Удалить значок ${id}`} onClick={() => confirm(`Удалить значок у ${id}?`) && mutate({ action: 'badge.delete', peer_type: type, telegram_id: id })}>Удалить</button></div>; })}</div> : <p className="empty">Пока нет назначенных значков.</p>}
  </section></div>;
}

function Licenses({ licenses, devices, mutate }) {
  const [permanent, setPermanent] = useState(true);
  return <div className="stack"><form className="card form-card" onSubmit={(event) => { event.preventDefault(); mutate({ ...values(event.currentTarget), action: 'license.save', permanent }); }}><span className="mini-label">FULL-ДОСТУП</span><h3>Назначить лицензию</h3><div className="form-row"><label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="[1-9][0-9]{4,19}" required /></label><label>Статус<select name="status"><option value="active">Активна</option><option value="blocked">Заблокирована</option><option value="refunded">Возврат</option></select></label></div><label>Действует до<input name="valid_until" type="datetime-local" disabled={permanent} /></label><label className="check"><input type="checkbox" checked={permanent} onChange={(event) => setPermanent(event.target.checked)}/> Бессрочно</label><button className="button">Сохранить <Icon name="arrow" size={17}/></button></form>
    <section className="card list-card"><div className="section-heading"><div><span className="mini-label">ДОСТУПЫ</span><h3>Лицензии</h3></div><span className="count">{licenses.length}</span></div>{licenses.length ? <div className="item-list">{licenses.map((item) => <div className="list-item" key={item.telegram_id}><span className="item-icon"><Icon name="key"/></span><div><strong>{item.username ? `@${item.username}` : item.first_name || `ID ${item.telegram_id}`}</strong><small>ID {item.telegram_id} · {item.valid_until || 'бессрочно'} · устройств: {item.device_count}</small></div><span className={`pill ${item.status === 'active' ? 'good' : ''}`}>{item.status}</span>{item.status === 'active' && <button className="text-button danger" onClick={() => confirm(`Отозвать лицензию ${item.telegram_id}?`) && mutate({ action: 'license.revoke', telegram_id: item.telegram_id })}>Отозвать</button>}</div>)}</div> : <p className="empty">Лицензий пока нет.</p>}</section>
    <section className="card list-card"><div className="section-heading"><div><span className="mini-label">ПРИВЯЗКИ</span><h3>Активные устройства</h3></div><span className="count">{devices.length}</span></div>{devices.length ? <div className="item-list">{devices.map((item) => <div className="list-item" key={item.id}><span className="item-icon"><Icon name="users"/></span><div><strong>{item.device_name || 'Устройство'}</strong><small>ID {item.telegram_id} · было в сети {item.last_seen_at || 'неизвестно'}</small></div><button className="text-button danger" onClick={() => confirm('Отозвать устройство?') && mutate({ action: 'device.revoke', device_id: item.id })}>Отозвать</button></div>)}</div> : <p className="empty">Активных устройств нет.</p>}</section>
  </div>;
}

function Release({ release, mutate, setNotice }) {
  const [progress, setProgress] = useState(0);
  async function upload(file, name) {
    const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))].map((byte) => byte.toString(16).padStart(2, '0')).join('');
    const chunkSize = 4 * 1024 * 1024;
    for (let offset = 0; offset < file.size; offset += chunkSize) {
      const end = Math.min(file.size, offset + chunkSize);
      const form = new FormData();
      form.append('name', name); form.append('offset', offset); form.append('total', file.size);
      form.append('chunk', file.slice(offset, end), 'chunk.apk');
      if (end === file.size) { form.append('finish', '1'); form.append('sha256', hash); }
      const response = await fetch('admin-upload.php', { method: 'POST', credentials: 'same-origin', body: form });
      if (response.status === 401) { location.reload(); throw new Error('Войдите через Telegram'); }
      const result = await response.json();
      if (!result.ok) throw new Error(result.error || 'Не удалось загрузить APK');
      setProgress(Math.round(end / file.size * 100));
    }
  }
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = values(form);
    const file = form.elements.apk.files[0];
    delete data.apk;
    try {
      if (file) { setNotice('Загрузка APK…'); await upload(file, data.apk_name); data.size = `≈ ${Math.round(file.size / 1048576)} МБ`; }
      await mutate({ ...data, action: 'release.save' });
    } catch (error) { setNotice(error.message, true); }
  }
  return <form className="card form-card release-form" onSubmit={submit}><span className="mini-label">ПУБЛИКАЦИЯ</span><h3>APK и страница скачивания</h3><p className="muted">После загрузки APK данные релиза обновятся на странице скачивания.</p>
    <label className="file-drop"><Icon name="download" size={25}/><span>Выбрать APK</span><small>Необязательно, если меняете только описание релиза</small><input name="apk" type="file" accept=".apk" /></label>
    <div className="form-row"><label>Версия<input name="version" required pattern="[0-9]+(\.[0-9]+){1,3}" defaultValue={release.version}/></label><label>Version code<input name="version_code" required inputMode="numeric" defaultValue={release.version_code}/></label></div>
    <div className="form-row"><label>Имя APK<input name="apk_name" required pattern="BYgram-[0-9A-Za-z._-]+\.apk" defaultValue={release.apk_name}/></label><label>Размер<input name="size" defaultValue={release.size}/></label></div>
    <div className="form-row"><label>Подпись релиза<input name="status" defaultValue={release.status}/></label><label>Требования<input name="requirements" defaultValue={release.requirements}/></label></div>
    <label>Package<input name="package" readOnly value="com.bygram.mobile"/></label>{progress > 0 && <progress value={progress} max="100" aria-label="Загрузка APK"/>}<button className="button">Опубликовать релиз <Icon name="arrow" size={17}/></button>
  </form>;
}

function Settings({ settings, mutate }) {
  return <form className="card form-card" onSubmit={(event) => { event.preventDefault(); mutate({ ...values(event.currentTarget), action: 'settings.save' }); }}><span className="mini-label">ИНТЕГРАЦИЯ</span><h3>Telegram-бот</h3><p className="muted">Используется для входа в панель и подтверждения Full-доступа.</p><label>Username бота<input name="bot_username" required minLength="5" maxLength="32" pattern="@?[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]" defaultValue={settings.bot_username}/></label><button className="button">Сохранить <Icon name="arrow" size={17}/></button></form>;
}

function Admins({ items, mutate }) {
  return <div className="content-grid"><form className="card form-card" onSubmit={(event) => { event.preventDefault(); mutate({ ...values(event.currentTarget), action: 'admin.add' }); }}><span className="mini-label">КОМАНДА</span><h3>Добавить администратора</h3><label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="[1-9][0-9]{4,19}" required/></label><button className="button">Добавить <Icon name="arrow" size={17}/></button></form><section className="card list-card"><div className="section-heading"><div><span className="mini-label">ДОСТУП К ПАНЕЛИ</span><h3>Администраторы</h3></div><span className="count">{items.length}</span></div><div className="item-list">{items.map((item) => <div className="list-item" key={item.telegram_id}><span className="item-icon"><Icon name="users"/></span><div><strong>{item.username ? `@${item.username}` : item.first_name || `ID ${item.telegram_id}`}</strong><small>ID {item.telegram_id}</small></div><button className="text-button danger" onClick={() => confirm(`Удалить администратора ${item.telegram_id}?`) && mutate({ action: 'admin.remove', telegram_id: item.telegram_id })}>Удалить</button></div>)}</div></section></div>;
}

function App() {
  const [data, setData] = useState(null);
  const [active, setActive] = useState('overview');
  const [notice, setNoticeState] = useState({ text: 'Загрузка данных…', bad: false });
  const setNotice = (text, bad = false) => setNoticeState({ text, bad });
  async function reload() { setData(await api()); }
  useEffect(() => { reload().then(() => setNotice('Данные актуальны')).catch((error) => setNotice(error.message, true)); }, []);
  async function mutate(body) {
    try { setNotice('Сохранение…'); await api(body); await reload(); setNotice('Изменения сохранены'); }
    catch (error) { setNotice(error.message, true); }
  }
  if (!data) return <main className="login"><section className="card"><div className="mark">BY</div><p className={notice.bad ? 'error' : ''}>{notice.text}</p></section></main>;
  const nav = data.is_owner ? [...sections, ['admins', 'Команда', 'users']] : sections;
  const current = nav.find(([id]) => id === active) || nav[0];
  return <div className="shell"><aside className="sidebar"><div className="brand"><div className="mark">BY</div><div><strong>BYgram</strong><small>CONTROL ROOM</small></div></div><div className="nav-label">РАЗДЕЛЫ</div><nav className="nav" aria-label="Разделы панели">{nav.map(([id, label, icon]) => <button key={id} className={active === id ? 'active' : ''} aria-label={label} aria-current={active === id ? 'page' : undefined} onClick={() => setActive(id)}><Icon name={icon}/><span>{label}</span></button>)}</nav><div className="sidebar-foot"><span className="pulse"/> Панель активна</div></aside>
    <div className="workspace"><header className="topbar"><div><span className="breadcrumb">BYGRAM / УПРАВЛЕНИЕ</span><h1>{current[1]}</h1></div><button className="refresh" aria-label="Обновить данные" title="Обновить данные" onClick={() => reload().then(() => setNotice('Данные обновлены')).catch((error) => setNotice(error.message, true))}><Icon name="refresh"/></button></header><div className={`notice ${notice.bad ? 'error' : ''}`} role="status" aria-live="polite"><span className="notice-dot"/>{notice.text}</div><main key={active} className="page">
      {active === 'overview' && <Overview data={data} go={setActive}/>}
      {active === 'badges' && <Badges items={data.badges} mutate={mutate}/>}
      {active === 'licenses' && <Licenses licenses={data.licenses} devices={data.devices} mutate={mutate}/>}
      {active === 'release' && <Release key={JSON.stringify(data.release)} release={data.release} mutate={mutate} setNotice={setNotice}/>}
      {active === 'settings' && <Settings settings={data.settings} mutate={mutate}/>}
      {active === 'admins' && data.is_owner && <Admins items={data.admins} mutate={mutate}/>}
    </main></div></div>;
}

createRoot(document.getElementById('admin-root')).render(<App/>);
