import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../styles.css';
import logoUrl from '../assets/bygram-logo.png';
import chatsUrl from '../assets/bygram-chats.png';
import settingsUrl from '../assets/bygram-settings.png';

const fallbackRelease = {
  version: '12.10.1',
  version_code: '70389',
  status: 'Стабильная версия · APK',
  size: '≈ 123 МБ',
  requirements: 'Android 6.0+',
  package: 'com.bygram.mobile',
  apk_name: 'BYgram-12.10.1.apk',
};

function useRelease() {
  const [release, setRelease] = useState(fallbackRelease);
  useEffect(() => {
    fetch('api/release.json', { cache: 'no-store' })
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => data && setRelease(data))
      .catch(() => {});
  }, []);
  return release;
}

function DownloadIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 20h14v-2H5m14-9h-4V3H9v6H5l7 7 7-7Z" /></svg>;
}

function Header({ download = false, homeLabel = 'О продукте' }) {
  return <>
    <a className="skip-link" href="#content">К содержанию</a>
    <header className="topbar"><div className="topbar__inner">
      <a className="brand" href="index.html"><img src={logoUrl} alt="" /><span>BYgram</span></a>
      <nav className="nav" aria-label="Основная навигация">
        {download ? <a href="index.html">{homeLabel}</a> : <><a href="#features">Функции</a><a href="#privacy">Приватность</a></>}
        <a href="download.html" aria-current={download ? 'page' : undefined}>Скачать</a>
      </nav>
    </div></header>
  </>;
}

function Footer({ full = false }) {
  return <footer className="footer"><div className="container footer__inner">
    <p>© {new Date().getFullYear()} BYgram{full ? '. Неофициальный клиент Telegram.' : ''}</p>
    <a href="https://t.me/BYgram_community">Канал</a>
    <a href="https://t.me/BYgram_official">Чат</a>
    <a href="https://t.me/BYgram_manager">Менеджер</a>
  </div></footer>;
}

function HomePage() {
  const release = useRelease();
  return <>
    <Header />
    <main id="content">
      <section className="container hero">
        <div>
          <span className="eyebrow">Версия {release.version} для Android</span>
          <h1>Telegram на ваших условиях</h1>
          <p className="hero__lead">BYgram добавляет к привычному мессенджеру точные настройки приватности, локальную историю изменений и инструменты, которых не хватает в обычном клиенте.</p>
          <div className="actions">
            <a className="button" href="download.html"><DownloadIcon />Скачать для Android</a>
            <a className="button button--text" href="https://t.me/BYgram_community">Канал проекта</a>
          </div>
          <div className="hero__meta"><span>{release.requirements}</span><span>Бесплатная установка</span></div>
        </div>
        <div className="phone-wrap"><div className="phone"><img src={chatsUrl} alt="Список чатов BYgram с текстовым заголовком и историями" /></div></div>
      </section>

      <section className="section section--tonal" id="features"><div className="container">
        <div className="section-heading"><h2>Больше возможностей. Без лишней сложности.</h2><p>Все дополнительные функции собраны в отдельном разделе настроек BYgram и включаются только по вашему выбору.</p></div>
        <div className="feature-grid">
          <article className="card card--wide card--accent"><div className="card__icon">◌</div><h3>Режим призрака</h3><p>Читайте сообщения и истории локально, скрывайте статус «онлайн» и набор текста. Отдельная опция разрешает отправлять прочтение только при осознанных действиях — ответе, реакции или нажатии кнопки.</p></article>
          <article className="card"><div className="card__icon">✦</div><h3>Шпион</h3><p>Сохраняйте удалённые сообщения, историю правок, замеченное время онлайна и локальную дату прочтения.</p></article>
          <article className="card"><div className="card__icon">⌁</div><h3>С кем в чате?</h3><p>Делитесь текущим открытым чатом только с выбранными пользователями BYgram. Данные активности автоматически исчезают вскоре после выхода.</p></article>
          <article className="card card--wide"><div className="card__icon">⚙</div><h3>Знакомый Telegram, настроенный под вас</h3><p>BYgram сохраняет привычную скорость, чаты, звонки и медиа, добавляя гибкие параметры приватности и удобные ссылки сообщества.</p></article>
        </div>
      </div></section>

      <section className="section" id="privacy"><div className="container split">
        <div className="shot"><img src={settingsUrl} alt="Экран настроек BYgram с категориями и ссылками" /></div>
        <div className="split__copy"><span className="eyebrow">Контроль остаётся у вас</span><h2 className="spaced-heading">Приватность — это настройка, а не обещание</h2><p>Режимы BYgram выключаются одним касанием. Локальные данные «Шпиона» можно удалить прямо из приложения, а параметры — экспортировать без сообщений, контактов и ключей.</p>
          <ul className="check-list"><li>Вы сами выбираете, какие события скрывать.</li><li>Активность «С кем в чате?» видят только выбранные пользователи.</li></ul>
        </div>
      </div></section>

      <section className="section"><div className="container cta"><h2>Попробуйте BYgram</h2><a className="button" href="download.html">Перейти к скачиванию</a></div></section>
    </main>
    <Footer full />
  </>;
}

function DownloadPage() {
  const release = useRelease();
  return <>
    <Header download />
    <main id="content" className="container">
      <header className="page-hero"><span className="eyebrow">Актуальный релиз</span><h1>Скачать BYgram</h1><p>Готовый APK для Android. Если BYgram уже установлен, новая версия обновит приложение и сохранит ваши чаты и настройки.</p></header>
      <div className="download-layout">
        <section className="download-card">
          <div className="app"><img src={logoUrl} alt="Логотип BYgram" /><div><h2>BYgram {release.version}</h2><p className="release-status">{release.status}</p></div></div>
          <div className="facts">
            <div className="fact"><span>Версия</span><strong>{release.version} ({release.version_code})</strong></div>
            <div className="fact"><span>Размер</span><strong>{release.size}</strong></div>
            <div className="fact"><span>Требования</span><strong>{release.requirements}</strong></div>
            <div className="fact"><span>Package</span><strong>{release.package}</strong></div>
          </div>
          <a className="button" href={release.apk_name} download><DownloadIcon />Скачать APK</a>
          <p>Нажимая кнопку, вы загружаете установочный файл напрямую с сервера BYgram.</p>
        </section>
        <aside className="side-card"><h2>Как установить</h2><ol><li>Дождитесь окончания загрузки.</li><li>Откройте APK из уведомления или папки «Загрузки».</li><li>Если Android спросит, разрешите установку из этого источника.</li><li>Нажмите «Установить» или «Обновить».</li></ol><p><strong>Важно:</strong> не удаляйте текущую версию перед обновлением, если хотите сохранить локальные данные.</p></aside>
      </div>
    </main>
    <Footer />
  </>;
}

function ThanksPage() {
  const release = useRelease();
  return <>
    <Header download homeLabel="На главную" />
    <main id="content" className="container thanks"><section className="thanks-card">
      <img className="logo" src={logoUrl} alt="Логотип BYgram" /><span className="eyebrow">BYgram для Android</span><h1 className="spaced-heading">Спасибо, что выбрали BYgram</h1><p>Осталось установить приложение. Обычно APK появится в уведомлениях браузера или в папке «Загрузки».</p>
      <div className="steps"><div className="step">Откройте загруженный APK.</div><div className="step">Подтвердите установку или обновление.</div><div className="step">Запустите BYgram и продолжайте общение.</div></div>
      <div className="actions actions--center"><a className="button" href={release.apk_name} download>Скачать APK</a><a className="button button--tonal" href="https://t.me/BYgram_community">Новости проекта</a></div>
      <p>Нужна помощь? Задайте вопрос в <a className="primary-link" href="https://t.me/BYgram_official">официальном чате BYgram</a>.</p>
    </section></main>
    <Footer />
  </>;
}

const page = location.pathname.endsWith('/download.html') ? <DownloadPage /> : location.pathname.endsWith('/thanks.html') ? <ThanksPage /> : <HomePage />;
createRoot(document.getElementById('root')).render(page);
