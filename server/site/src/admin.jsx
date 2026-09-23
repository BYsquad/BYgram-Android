import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../../activity-api/admin.css';
import './admin-extra.css';

const pages = [
  ['settings', 'Настройки'], ['release', 'Релиз'], ['prices', 'Цены'], ['referrals', 'Рефералы'],
  ['users', 'Пользователи'], ['promos', 'Промокоды'], ['licenses', 'Лицензии'],
  ['badges', 'Значки'], ['auth', 'Авторизации'], ['requisites', 'Реквизиты'], ['orders', 'Платежи'],
];
const formData = (form) => Object.fromEntries(new FormData(form));

async function request(body, query = '') {
  const response = await fetch(`admin-api.php${query}`, {
    method: body ? 'POST' : 'GET',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null,
  });
  if (response.status === 401) { window.location.reload(); throw new Error('Требуется вход через Telegram'); }
  const result = await response.json();
  if (!result.ok) throw new Error(result.error || 'Ошибка');
  return result.data;
}

function Release({ release, reload, setNotice }) {
  const [progress, setProgress] = useState(0);
  async function upload(file, name) {
    const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))].map((x) => x.toString(16).padStart(2, '0')).join('');
    const chunkSize = 4 * 1024 * 1024;
    for (let offset = 0; offset < file.size; offset += chunkSize) {
      const end = Math.min(file.size, offset + chunkSize);
      const data = new FormData();
      data.append('name', name);
      data.append('offset', offset);
      data.append('total', file.size);
      data.append('chunk', file.slice(offset, end), 'chunk.apk');
      if (end === file.size) {
        data.append('finish', '1');
        data.append('sha256', hash);
      }
      const response = await fetch('admin-upload.php', { method: 'POST', credentials: 'same-origin', body: data });
      if (response.status === 401) { window.location.reload(); throw new Error('Требуется вход через Telegram'); }
      const result = await response.json();
      if (!result.ok) throw new Error(result.error || 'Ошибка загрузки APK');
      setProgress(Math.round(end / file.size * 100));
    }
  }
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = formData(form);
    const file = form.elements.apk.files[0];
    delete data.apk;
    try {
      if (file) {
        data.size = `≈ ${Math.round(file.size / 1048576)} МБ`;
        setNotice('Подготовка APK…');
        await upload(file, data.apk_name);
      }
      setNotice('Сохранение релиза…');
      await request({ ...data, action: 'release.save' });
      await reload();
    } catch (error) {
      setNotice(error.message, true);
    }
  }
  return <form className="card grid" onSubmit={submit}>
    <strong>APK и страница скачивания</strong>
    <label>Новый APK<input name="apk" type="file" accept=".apk" /></label>
    <div className="two grid">
      <label>Версия<input name="version" required pattern="[0-9]+(\.[0-9]+){1,3}" defaultValue={release.version} /></label>
      <label>Version code<input name="version_code" required inputMode="numeric" defaultValue={release.version_code} /></label>
      <label>Имя APK<input name="apk_name" required pattern="BYgram-[0-9A-Za-z._-]+\.apk" defaultValue={release.apk_name} /></label>
      <label>Подпись релиза<input name="status" defaultValue={release.status} /></label>
      <label>Размер<input name="size" defaultValue={release.size} /></label>
      <label>Требования<input name="requirements" defaultValue={release.requirements} /></label>
      <label>Package<input name="package" defaultValue={release.package} /></label>
    </div>
    {progress > 0 && <progress value={progress} max="100" />}
    <button>Опубликовать</button>
  </form>;
}

function Settings({ settings, mutate }) {
  return <form className="card grid" onSubmit={(event) => {
    event.preventDefault();
    mutate({ ...formData(event.currentTarget), action: 'settings.save' });
  }}>
    <strong>Telegram-бот</strong>
    <label>Username бота<input name="bot_username" required minLength="5" maxLength="32" pattern="@?[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]" defaultValue={settings.bot_username} placeholder="BYgram_example_bot" /></label>
    <div className="meta">Можно указать с символом @ или без него. Используется кнопкой «Авторизовать Full».</div>
    <button>Сохранить</button>
  </form>;
}

function Prices({ methods, mutate }) {
  return <div className="grid">{methods.map((method) => <form className="card grid" key={method.code} onSubmit={(event) => {
    event.preventDefault(); const form = event.currentTarget; mutate({ ...formData(form), action: 'method.save', enabled: form.elements.enabled.checked });
  }}>
    <input type="hidden" name="code" value={method.code} />
    <div className="row"><strong>{method.title}</strong><span className="chip">{method.currency}</span></div>
    <label>Цена<input name="price" type="number" min="0" step={method.code === 'stars' ? '1' : '0.01'} defaultValue={method.price} /></label>
    <label className="check"><input name="enabled" type="checkbox" defaultChecked={Boolean(+method.enabled)} /> Способ оплаты включён</label>
    <button>Сохранить</button>
  </form>)}</div>;
}

function Referrals({ settings, referrals, mutate }) {
  return <>
    <form className="card grid" onSubmit={(event) => { event.preventDefault(); const form = event.currentTarget; mutate({ ...formData(form), action: 'referral.save', enabled: form.elements.enabled.checked }); }}>
      <strong>Автоматические начисления</strong>
      <label>Процент от каждой оплаченной лицензии<input name="percent" type="number" min="0" max="100" step="0.01" defaultValue={settings.percent} /></label>
      <label className="check"><input name="enabled" type="checkbox" defaultChecked={Boolean(+settings.enabled)} /> Автоматически начислять вознаграждение</label>
      <button>Сохранить</button>
    </form>
    <form className="card grid" onSubmit={(event) => { event.preventDefault(); mutate({ ...formData(event.currentTarget), action: 'referral.grant' }); }}>
      <strong>Начислить вручную</strong><div className="two grid">
        <label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="[1-9][0-9]{4,19}" required /></label>
        <label>Сумма<input name="amount" type="number" min="0.01" step="0.01" required /></label>
        <label>Валюта<input name="currency" defaultValue="BYN" pattern="[A-Za-z0-9]{2,8}" required /></label>
      </div><label>Комментарий<input name="note" maxLength="255" /></label><button>Начислить</button>
    </form>
    {referrals.map((item, index) => <div className="card" key={`${item.source}-${item.order_id}-${index}`}><div className="row"><strong>{item.username ? `@${item.username}` : item.referrer_id}</strong><span className="chip">+{item.commission} {item.currency}</span></div><div className="meta">{item.source === 'manual' ? `Ручное начисление${item.note ? ` · ${item.note}` : ''}` : `Приглашённый: ${item.referred_id} · заказ #${item.order_id}`} · {item.created_at}</div></div>)}
  </>;
}

function Users({ data, reload }) {
  return <>{data.users.length ? data.users.map((user) => <div className="card row user" key={user.telegram_id}>
    <div><div className="row"><strong>{[user.first_name, user.last_name].filter(Boolean).join(' ') || 'Без имени'}</strong><span className="chip">{+user.has_license ? (user.valid_until ? `до ${user.valid_until}` : 'бессрочная лицензия') : 'без лицензии'}</span></div><div>{user.username ? `@${user.username}` : 'Без ника'} · ID {user.telegram_id}</div><div className="meta">Пригласил: {user.referrer_id ? `${user.inviter_username ? `@${user.inviter_username} · ` : ''}${user.referrer_id}` : 'никто'} · регистрация {user.created_at} · был {user.last_seen_at}</div></div>
  </div>) : <div className="empty">Пользователей пока нет</div>}
  {data.users_pages > 1 && <div className="pager"><button className="tonal" disabled={data.users_page <= 1} onClick={() => reload(data.users_page - 1)}>Назад</button><span>{data.users_page} из {data.users_pages} · всего {data.users_total}</span><button className="tonal" disabled={data.users_page >= data.users_pages} onClick={() => reload(data.users_page + 1)}>Вперёд</button></div>}
  </>;
}

function PromoForm({ promo, mutate }) {
  const [unlimited, setUnlimited] = useState(promo ? promo.usage_limit === null : true);
  const [neverExpires, setNeverExpires] = useState(promo ? promo.expires_at === null : true);
  return <form className="card grid" onSubmit={(event) => {
    event.preventDefault(); const form = event.currentTarget; mutate({ ...formData(form), action: 'promo.save', active: form.elements.active?.checked ?? true, unlimited_uses: unlimited, never_expires: neverExpires });
  }}>
    <strong>{promo ? `${promo.code} · использовано ${promo.used_count}` : 'Новый промокод'}</strong>
    {promo && <input type="hidden" name="id" value={promo.id} />}
    <div className="two grid">
      <label>Код<input name="code" required pattern="[A-Za-z0-9_-]{3,40}" defaultValue={promo?.code} /></label>
      <label>Скидка, %<input name="discount_percent" type="number" min="1" max="100" required defaultValue={promo?.discount_percent} /></label>
      <label>Лимит использований<input name="usage_limit" type="number" min="1" disabled={unlimited} defaultValue={promo?.usage_limit ?? ''} /><span className="check"><input type="checkbox" checked={unlimited} onChange={(event) => setUnlimited(event.target.checked)} /> Без лимита использований</span></label>
      <label>Действует до<input name="expires_at" type="datetime-local" disabled={neverExpires} defaultValue={promo?.expires_at ? String(promo.expires_at).replace(' ', 'T').slice(0, 16) : ''} /><span className="check"><input type="checkbox" checked={neverExpires} onChange={(event) => setNeverExpires(event.target.checked)} /> Без срока действия</span></label>
    </div>
    {promo && <label className="check"><input name="active" type="checkbox" defaultChecked={Boolean(+promo.active)} /> Промокод активен</label>}
    <button>{promo ? 'Сохранить' : 'Добавить'}</button>
  </form>;
}

function Licenses({ licenses, mutate }) {
  const [showForm, setShowForm] = useState(false);
  const [permanent, setPermanent] = useState(true);
  return <>
    {!showForm && <button onClick={() => setShowForm(true)}>Создать лицензию</button>}
    {showForm && <form className="card grid" onSubmit={(event) => { event.preventDefault(); mutate({ ...formData(event.currentTarget), action: 'license.save', permanent }); }}>
      <strong>Создать лицензию вручную</strong><div className="two grid">
        <label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="[1-9][0-9]{4,19}" required /></label>
        <label>Статус<select name="status"><option value="active">Активна</option><option value="blocked">Заблокирована</option><option value="refunded">Возврат</option></select></label>
        <label>Действует до<input name="valid_until" type="datetime-local" disabled={permanent} /><span className="check"><input type="checkbox" checked={permanent} onChange={(event) => setPermanent(event.target.checked)} /> Бессрочная лицензия</span></label>
      </div><div className="row"><button>Сохранить</button><button type="button" className="tonal" onClick={() => setShowForm(false)}>Отмена</button></div>
    </form>}
    {licenses.map((license) => <div className="card" key={license.telegram_id}><div className="row"><strong>{license.telegram_id}</strong><span className="chip">{license.status}</span></div><div className="meta">{license.username ? `@${license.username}` : license.first_name || 'Без профиля'} · заказов: {license.order_count} · до: {license.valid_until || 'бессрочно'}</div>{license.status === 'active' && <button className="danger" onClick={() => confirm('Отозвать лицензию?') && mutate({ action: 'license.revoke', telegram_id: license.telegram_id })}>Отозвать</button>}</div>)}
  </>;
}

function Requisites({ items, mutate }) {
  return <><form className="card grid" onSubmit={(event) => { event.preventDefault(); mutate({ ...formData(event.currentTarget), action: 'requisite.save' }); }}><strong>Добавить реквизиты</strong><div className="two grid"><label>Банк<select name="bank"><option value="belarusbank">Беларусбанк</option><option value="mtbank">МТБанк</option></select></label><label>Номер карты<input name="card_number" required /></label><label>Имя на карте<input name="holder_name" /></label></div><button>Добавить</button></form>
  {items.map((item) => <div className="card" key={item.id}><div className="row"><strong>{item.bank === 'belarusbank' ? 'Беларусбанк' : 'МТБанк'}</strong><span className="chip">{+item.active ? 'активны' : 'выключены'}</span></div><div>{item.card_number}</div><div className="meta">{item.holder_name || 'Без имени'} · выдавались: {item.last_used_at || 'никогда'}</div></div>)}</>;
}

function AuthChallenges({ items, mutate }) {
  return items.length ? items.map((item) => <div className="card row" key={item.id}><div><strong>ID {item.expected_user_id}</strong><div className="meta">Код {item.token_hint} · до {item.expires_at}</div></div><button onClick={() => confirm(`Подтвердить Telegram ID ${item.expected_user_id}?`) && mutate({ action: 'auth.confirm', id: item.id })}>Подтвердить</button></div>) : <div className="empty">Ожидающих подтверждений нет</div>;
}

function BadgeForm({ peerId = '', peerType = 'user', mutate }) {
  return <form className="card grid" onSubmit={(event) => {
    event.preventDefault(); const form = event.currentTarget;
    mutate({ ...formData(form), action: 'badge.save' });
  }}>
    <strong>{peerId ? `${peerType === 'chat' ? 'Канал / группа' : 'Пользователь'} ID ${peerId}` : 'Выдать значок'}</strong>
    <label>Тип<select name="peer_type" defaultValue={peerType} disabled={Boolean(peerId)}><option value="user">Пользователь · logo-new.png</option><option value="chat">Канал или группа · bygram-logo.png</option></select></label>
    {peerId && <input type="hidden" name="peer_type" value={peerType} />}
    <label>Telegram ID (для чата можно -100… или -…)<input name="telegram_id" inputMode="numeric" pattern="-?[0-9]{1,20}" required readOnly={Boolean(peerId)} defaultValue={peerId} /></label>
    <div className="row"><button>Сохранить</button>{peerId && <button type="button" className="danger" onClick={() => confirm(`Удалить значок у ${peerId}?`) && mutate({ action: 'badge.delete', peer_type: peerType, telegram_id: peerId })}>Удалить</button>}</div>
  </form>;
}

function Badges({ items, mutate }) {
  return <div className="grid"><BadgeForm mutate={mutate} />{Object.entries(items).filter(([, badge]) => badge === true || badge?.bygram).map(([key]) => { const [type, id] = key.includes(':') ? key.split(':') : ['user', key]; return <BadgeForm key={key} peerId={id} peerType={type} mutate={mutate} />; })}</div>;
}

function Orders({ items }) {
  return items.length ? items.map((item) => <div className="card" key={item.id}><div className="row"><strong>#{item.id} · {item.payment_method}</strong><span className="chip">{item.status}</span></div><div>{item.amount} {item.currency}</div><div className="meta">Покупатель {item.buyer_id} → лицензия {item.beneficiary_id} · {item.created_at}</div></div>) : <div className="empty">Платежей пока нет</div>;
}

function Admins({ items, mutate }) {
  return <><form className="card grid" onSubmit={(event) => { event.preventDefault(); mutate({ ...formData(event.currentTarget), action: 'admin.add' }); }}><strong>Добавить администратора</strong><label>Telegram ID<input name="telegram_id" inputMode="numeric" pattern="[1-9][0-9]{4,19}" required /></label><button>Добавить</button></form>{items.map((item) => <div className="card row" key={item.telegram_id}><div><strong>{item.telegram_id}</strong><div className="meta">{item.username ? `@${item.username}` : item.first_name || 'Профиль не сохранён'}</div></div><button className="danger" onClick={() => mutate({ action: 'admin.remove', telegram_id: item.telegram_id })}>Удалить</button></div>)}</>;
}

function AdminApp() {
  const [data, setData] = useState(null);
  const [active, setActive] = useState('prices');
  const [notice, setNoticeState] = useState({ text: 'Загрузка данных…', bad: false });
  const setNotice = (text, bad = false) => setNoticeState({ text, bad });
  async function reload(page = data?.users_page || 1) {
    try {
      setData(await request(null, `?users_page=${page}`));
      setNotice('Данные обновлены');
    } catch (error) {
      setNotice(error.message, true);
    }
  }
  useEffect(() => { reload(1); }, []);
  async function mutate(body) {
    try {
      setNotice('Сохранение…');
      await request(body);
      await reload();
    } catch (error) {
      setNotice(error.message, true);
    }
  }
  if (!data) return <main className="login"><section className="card"><div className="mark">BY</div><p className={notice.bad ? 'error' : ''}>{notice.text}</p></section></main>;
  const allPages = data.is_owner ? [...pages, ['admins', 'Администраторы']] : pages;
  const content = {
    settings: <Settings settings={data.settings} mutate={mutate} />,
    release: <Release key={JSON.stringify(data.release)} release={data.release} reload={reload} setNotice={setNotice} />,
    prices: <Prices methods={data.methods} mutate={mutate} />,
    referrals: <Referrals settings={data.referral_settings} referrals={data.referrals} mutate={mutate} />,
    users: <Users data={data} reload={reload} />,
    promos: <><PromoForm mutate={mutate} />{data.promos.map((promo) => <PromoForm key={promo.id} promo={promo} mutate={mutate} />)}</>,
    licenses: <Licenses licenses={data.licenses} mutate={mutate} />,
    badges: <Badges items={data.badges} mutate={mutate} />,
    auth: <AuthChallenges items={data.auth_challenges} mutate={mutate} />,
    requisites: <Requisites items={data.requisites} mutate={mutate} />,
    orders: <Orders items={data.orders} />,
    admins: <Admins items={data.admins} mutate={mutate} />,
  };
  return <div className="shell">
    <header className="topbar"><div className="brand"><div className="mark">BY</div><div><h1>Управление BYgram</h1><div className={`support ${notice.bad ? 'error' : 'ok'}`}>{notice.text}</div></div></div></header>
    <div className="layout"><nav className="nav" aria-label="Разделы панели">{allPages.map(([id, label]) => <button key={id} className={active === id ? 'active' : ''} onClick={() => setActive(id)}>{label}</button>)}</nav><main><section className="page active"><h2>{allPages.find(([id]) => id === active)?.[1]}</h2>{content[active]}</section></main></div>
  </div>;
}

const root = document.getElementById('admin-root');
createRoot(root).render(<AdminApp />);
