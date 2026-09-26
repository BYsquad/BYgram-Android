import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../styles.css';
import logoUrl from '../assets/bygram-logo.png';
import settingsUrl from '../assets/settings.png';
import ghostUrl from '../assets/ghost.png';
import spyUrl from '../assets/spy.png';
import customizationUrl from '../assets/customization.png';
import profileUrl from '../assets/profile.png';
import activityUrl from '../assets/activity.png';
import customMenuUrl from '../assets/custom-menu.png';

const fallbackRelease = {
  version: '12.10.3',
  version_code: '70899',
  status: 'Lite · бесплатно',
  size: '110,3 МиБ',
  requirements: 'Android 6.0+',
  package: 'com.bygram.mobile',
  apk_name: 'BYgram-lite-12.10.3.apk',
};
const managerUrl = 'api/manager.php';

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
        <a href="features.html">Функции</a><a href="editions.html">Lite и Full</a>
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
    <a href={managerUrl}>Менеджер</a>
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
          <h1>Ваш Telegram. Ваши правила.</h1>
          <p className="hero__lead">BYgram добавляет к привычному мессенджеру точные настройки приватности, локальную историю изменений и инструменты, которых не хватает в обычном клиенте.</p>
          <div className="actions">
            <a className="button" href="download.html"><DownloadIcon />Скачать Lite бесплатно</a>
            <a className="button button--text" href="features.html">Все функции →</a>
          </div>
          <div className="hero__meta"><span>{release.requirements}</span><span>Бесплатная Lite</span></div>
        </div>
        <div className="phone-wrap"><div className="phone"><img src={settingsUrl} alt="Новый раздел настроек BYgram, версия 12.10.3" /></div></div>
      </section>

      <section className="section section--tonal" id="features"><div className="container">
        <div className="section-heading"><h2>Больше возможностей. Без лишней сложности.</h2><p>Все дополнительные функции собраны в отдельном разделе настроек BYgram и включаются только по вашему выбору.</p></div>
        <div className="feature-grid">
          <article className="card card--wide card--accent"><div className="card__icon">◌</div><h3>Режим призрака</h3><p>Читайте сообщения и истории локально, скрывайте статус «онлайн» и набор текста. Отдельная опция разрешает отправлять прочтение только при осознанных действиях — ответе, реакции или нажатии кнопки.</p></article>
          <article className="card"><div className="card__icon">✦</div><h3>Шпион</h3><p>Сохраняйте удалённые сообщения, историю правок, замеченное время онлайна и локальную дату прочтения.</p></article>
          <article className="card"><div className="card__icon">♫</div><h3>Профиль и статусы</h3><p>Добавьте музыку в профиль и выберите свои фразы для действий в чате.</p></article>
          <article className="card card--wide"><div className="card__icon">⚙</div><h3>Знакомый Telegram, настроенный под вас</h3><p>BYgram сохраняет привычную скорость, чаты, звонки и медиа, добавляя гибкие параметры приватности и удобные ссылки сообщества.</p></article>
        </div><a className="inline-link" href="features.html">Подробно о функциях и скриншоты →</a>
      </div></section>

      <section className="section" id="privacy"><div className="container split">
        <div className="shot"><img src={customizationUrl} alt="Настройки оформления BYgram" /></div>
        <div className="split__copy"><span className="eyebrow">Контроль остаётся у вас</span><h2 className="spaced-heading">Приватность — это настройка, а не обещание</h2><p>Режимы BYgram выключаются одним касанием. Локальные данные «Шпиона» можно удалить прямо из приложения, а параметры — экспортировать без сообщений, контактов и ключей.</p>
          <ul className="check-list"><li>Вы сами выбираете, какие события скрывать.</li><li>В Full доступ к вашей активности получают только выбранные вами пользователи.</li></ul>
        </div>
      </div></section>

      <section className="section"><div className="container cta"><div><h2>Lite бесплатно. Full с дополнительной функцией.</h2><p>Сравните редакции и выберите подходящую.</p></div><a className="button" href="editions.html">Lite и Full</a></div></section>
    </main>
    <Footer full />
  </>;
}

function DownloadPage() {
  const release = useRelease();
  return <>
    <Header download />
    <main id="content" className="container">
      <header className="page-hero"><span className="eyebrow">Актуальный релиз</span><h1>Скачать BYgram Lite</h1><p>Бесплатный APK для Android. Если BYgram уже установлен, обновление сохранит ваши чаты и настройки.</p></header>
      <div className="download-layout">
        <section className="download-card">
          <div className="app"><img src={logoUrl} alt="Логотип BYgram" /><div><h2>BYgram {release.version} Lite</h2><p className="release-status">{release.status}</p></div></div>
          <div className="facts">
            <div className="fact"><span>Версия</span><strong>{release.version} ({release.version_code})</strong></div>
            <div className="fact"><span>Размер</span><strong>{release.size}</strong></div>
            <div className="fact"><span>Требования</span><strong>{release.requirements}</strong></div>
            <div className="fact"><span>Package</span><strong>{release.package}</strong></div>
          </div>
          <a className="button" href={release.apk_name} download><DownloadIcon />Скачать Lite APK</a>
          <p>Нажимая кнопку, вы загружаете установочный файл напрямую с сервера BYgram.</p>
        </section>
        <aside className="side-card"><h2>Как установить</h2><ol><li>Дождитесь окончания загрузки.</li><li>Откройте APK из уведомления или папки «Загрузки».</li><li>Если Android спросит, разрешите установку из этого источника.</li><li>Нажмите «Установить» или «Обновить».</li></ol><p><strong>Важно:</strong> не удаляйте текущую версию перед обновлением, если хотите сохранить локальные данные.</p><p><a className="inline-link" href="editions.html">Чем Full отличается от Lite →</a></p></aside>
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

const featureDetails = [
  ['Режим призрака', 'Настраивайте, когда собеседники видят прочтение сообщений и историй, ваш онлайн и набор текста. Если хотите, отметка о прочтении отправится после ответа, реакции или явного действия.', ghostUrl, 'Экран настроек режима призрака'],
  ['Шпион', 'Сохраняйте удалённые сообщения и открывайте прежние версии отредактированного текста. BYgram также может запоминать замеченный онлайн и дату прочтения ваших сообщений. Историю правок, чтения и онлайна можно очистить.', spyUrl, 'Экран настроек истории сообщений'],
  ['Оформление', 'Выберите иконку приложения, форму аватаров, оформление переключателей и индикаторов. «Жидкое стекло» можно включить только в тех местах, где оно вам нравится.', customizationUrl, 'Экран настройки оформления'],
  ['Профиль', 'Покажите музыку крупной карточкой в профиле или оставьте обычный компактный вид. При необходимости включите отображение ID пользователя, группы или канала.', profileUrl, 'Экран настройки профиля'],
  ['Свои статусы действий', 'Замените стандартные фразы о наборе текста, записи голосового и отправке медиа своими. Для каждого действия можно выбрать отдельную фразу. После публикации пользователи BYgram увидят её, а в обычном Telegram останется стандартный статус.', customMenuUrl, 'Раздел кастомного статуса печати'],
  ['Кто с кем общается? · Full', 'Делитесь тем, что вы смотрите список чатов, открыли профиль или находитесь в чате. Для каждого выбранного человека можно отдельно разрешить нужные виды активности. После выхода она исчезает.', activityUrl, 'Экран настройки активности Full'],
];

function FeaturesPage() {
  return <><Header /><main id="content"><section className="container page-hero"><span className="eyebrow">Возможности BYgram</span><h1>Настройте общение под себя</h1><p>Все дополнительные функции собраны в настройках BYgram. На этой странице показаны экраны приложения и то, как возможности помогают в обычном общении.</p></section><section className="container feature-stories">{featureDetails.map(([title, description, image, alt], index) => <article className="feature-story" key={title}><div><span className="eyebrow">{index === 5 ? 'Full' : 'Lite и Full'}</span><h2>{title}</h2><p>{description}</p>{index === 5 && <a className="inline-link" href="editions.html">О редакции Full →</a>}</div><div className="feature-story__image"><img src={image} alt={alt} loading="lazy" /></div></article>)}</section></main><Footer full /></>;
}

function EditionsPage() {
  return <><Header /><main id="content"><section className="container page-hero"><span className="eyebrow">Редакции</span><h1>Lite и Full</h1><p>В Lite доступны основные возможности BYgram бесплатно. Full добавляет функцию активности с выбором зрителей.</p></section><section className="container edition-grid"><article className="edition-card"><span className="eyebrow">Бесплатно</span><h2>Lite</h2><p>Приватность, локальная история и личный интерфейс.</p><ul className="check-list"><li>Режим призрака</li><li>Удалённые сообщения и история правок</li><li>Оформление и музыка в профиле</li><li>Свои статусы действий в чате</li></ul><a className="button" href="download.html">Скачать Lite</a></article><article className="edition-card edition-card--full"><span className="eyebrow">Полная версия</span><h2>Full</h2><p>Всё из Lite и расширенная активность.</p><ul className="check-list"><li>Все функции Lite</li><li>«Кто с кем общается?» — список чатов, профиль и открытый чат</li><li>Выбор людей и видов активности для каждого</li><li>Доступ на двух ваших устройствах</li></ul><a className="button button--tonal" href={managerUrl}>Написать менеджеру о покупке</a></article></section><section className="container edition-note"><h2>Как перейти на Full</h2><p>Напишите менеджеру, чтобы узнать условия покупки. После получения доступа установите Full поверх Lite и активируйте через официальный бот BYgram. При обновлении на том же устройстве данные приложения сохраняются.</p><p>Lite и Full используют одно приложение и не устанавливаются одновременно.</p></section></main><Footer full /></>;
}

const page = location.pathname.endsWith('/features.html') ? <FeaturesPage /> : location.pathname.endsWith('/editions.html') ? <EditionsPage /> : location.pathname.endsWith('/download.html') ? <DownloadPage /> : location.pathname.endsWith('/thanks.html') ? <ThanksPage /> : <HomePage />;
createRoot(document.getElementById('root')).render(page);
