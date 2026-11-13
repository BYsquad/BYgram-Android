import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../styles.css';
import logoUrl from '../assets/bygram-logo.png';
import settingsUrl from '../assets/settings-current.png';
import ghostUrl from '../assets/ghost.png';
import spyUrl from '../assets/spy-current.png';
import customizationUrl from '../assets/customization-current.png';
import profileUrl from '../assets/profile-current.png';
import fallbackRelease from '../../activity-api/release.json';

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

function Header({ download = false, active = download ? 'download' : '' }) {
  return <>
    <a className="skip-link" href="#content">К содержанию</a>
    <header className="topbar"><div className="topbar__inner">
      <a className="brand" href="index.html"><img src={logoUrl} alt="" /><span>BYgram</span></a>
      <nav className="nav" aria-label="Основная навигация">
        <a href="features.html" aria-current={active === 'features' ? 'page' : undefined}>Функции</a><a href="changelog.html" aria-current={active === 'changelog' ? 'page' : undefined}>Что нового</a>
        <a href="download.html" aria-current={download ? 'page' : undefined}>Скачать</a>
      </nav>
    </div></header>
  </>;
}

function Footer() {
  return <footer className="footer"><div className="container footer__inner">
    <p>© {new Date().getFullYear()} BYgram. Неофициальный клиент Telegram.</p>
    <a href="https://t.me/BYgram_community">Канал</a>
    <a href="https://t.me/BYgram_official">Чат</a>
    <a href={managerUrl}>Менеджер</a>
  </div></footer>;
}

function HomePage() {
  const release = useRelease();
  return <>
    <a className="skip-link" href="#content">К содержанию</a>
    <main id="content">
      <section className="container hero">
        <div>
          <span className="eyebrow">Версия {release.version} для Android</span>
          <h1>Telegram с вашими настройками приватности.</h1>
          <p className="hero__lead">Режим призрака, история удалённых сообщений, скрытые чаты и своё оформление. BYgram собирает дополнительные возможности в одном разделе настроек.</p>
          <div className="actions">
            <a className="button" href="download.html"><DownloadIcon />Скачать BYgram</a>
            <a className="button button--text" href="features.html">Все функции →</a>
          </div>
          <div className="hero__meta"><span>{release.requirements}</span><a href="features.html">Возможности BYgram →</a></div>
        </div>
        <div className="phone-wrap"><div className="phone"><img src={settingsUrl} width="1080" height="2400" alt="Настройки BYgram" /></div></div>
      </section>

      <section className="section section--tonal" id="features"><div className="container">
        <div className="section-heading"><h2>Больше возможностей. Без лишней сложности.</h2><p>Все дополнительные функции собраны в отдельном разделе настроек BYgram и включаются только по вашему выбору.</p></div>
        <div className="feature-grid">
          <article className="card card--wide card--accent"><div className="card__icon">◌</div><h3>Режим призрака</h3><p>Читайте сообщения и истории локально, скрывайте статус «онлайн» и набор текста. Отдельная опция разрешает отправлять прочтение только при осознанных действиях — ответе, реакции или нажатии кнопки.</p></article>
          <article className="card"><h3>Шпион</h3><p>Сохраняйте удалённые сообщения, историю правок, замеченное время онлайна и локальную дату прочтения. Получайте отдельные уведомления об удалениях.</p></article>
          <article className="card"><div className="card__icon">♫</div><h3>Профиль и статусы</h3><p>Добавьте музыку в профиль и выберите свои фразы для действий в чате.</p></article>
          <article className="card card--wide"><h3>Видеообои и фон профиля</h3><p>Поставьте локальное видео на фон чата, опубликуйте изображение или GIF для профиля и растяните фон на всю страницу.</p></article>
        </div><a className="inline-link" href="features.html">Подробно о функциях и скриншоты →</a>
      </div></section>

      <section className="section" id="privacy"><div className="container split">
        <div className="shot"><img src={customizationUrl} alt="Настройки оформления BYgram" /></div>
        <div className="split__copy"><span className="eyebrow">Контроль остаётся у вас</span><h2 className="spaced-heading">Приватность — это настройка, а не обещание</h2><p>Режимы BYgram выключаются одним касанием. Локальные данные «Шпиона» можно удалить прямо из приложения, а параметры — экспортировать без сообщений, контактов и ключей.</p>
          <ul className="check-list"><li>Вы сами выбираете, какие события скрывать.</li><li>Доступ к вашей активности получают только выбранные вами пользователи.</li></ul>
        </div>
      </div></section>

      <section className="section"><div className="container cta"><div><h2>Настройте BYgram под себя</h2><p>Выбирайте параметры приватности, сохраняйте историю сообщений и меняйте оформление чатов и профиля.</p></div><a className="button" href="features.html">Смотреть функции</a></div></section>
    </main>
    <Footer />
  </>;
}

function DownloadPage() {
  const release = useRelease();
  return <>
    <Header download />
    <main id="content" className="container">
      <div className="download-layout">
        <section className="download-card">
          <div className="app"><img src={logoUrl} alt="Логотип BYgram" /><div><h2>BYgram {release.version}</h2><p className="release-status">APK для Android</p></div></div>
          <div className="facts">
            <div className="fact"><span>Версия</span><strong>{release.version}</strong></div>
            <div className="fact"><span>Размер</span><strong>{release.size}</strong></div>
            <div className="fact"><span>Требования</span><strong>{release.requirements}</strong></div>
          </div>
          <a className="button" href={release.apk_name} download><DownloadIcon />Скачать APK</a>
          <p>Нажимая кнопку, вы загружаете установочный файл напрямую с сервера BYgram.</p>
          <h3 id="full-debug">BYgram Full Debug</h3>
          <p>Тестовая сборка 13.2.1 (72919) для Android 7.0 и новее. Размер — 172,3 МиБ.</p>
          <a className="button button--tonal" href="BYgram-full-debug-13.2.1-72919.apk" download><DownloadIcon />Скачать Full Debug APK</a>
          <p><a className="inline-link" href="docs/">Документация плагинов BYgram</a></p>
        </section>
        <aside className="side-card"><h2>Как установить</h2><ol><li>Дождитесь окончания загрузки.</li><li>Откройте APK из уведомления или папки «Загрузки».</li><li>Если Android спросит, разрешите установку из этого источника.</li><li>Нажмите «Установить» или «Обновить».</li></ol><p><strong>Важно:</strong> не удаляйте текущую версию перед обновлением, если хотите сохранить локальные данные.</p><p>В актуальной версии доступны видеообои, фон всего профиля и уведомления об удалениях в личных чатах.</p><p><a className="inline-link" href="features.html">Возможности BYgram →</a></p></aside>
      </div>
    </main>
    <Footer />
  </>;
}

function ThanksPage() {
  const release = useRelease();
  return <>
    <Header download />
    <main id="content" className="container thanks"><section className="thanks-card">
      <img className="logo" src={logoUrl} alt="Логотип BYgram" /><span className="eyebrow">BYgram для Android</span><h1 className="spaced-heading">Спасибо, что выбрали BYgram</h1><p>Осталось установить приложение. Обычно APK появится в уведомлениях браузера или в папке «Загрузки».</p>
      <div className="steps"><div className="step">Откройте загруженный APK.</div><div className="step">Подтвердите установку или обновление.</div><div className="step">Запустите BYgram и продолжайте общение.</div></div>
      <div className="actions actions--center"><a className="button" href={release.apk_name} download>Скачать APK</a><a className="button button--tonal" href="https://t.me/BYgram_community">Новости проекта</a></div>
      <p>Нужна помощь? Задайте вопрос в <a className="primary-link" href="https://t.me/BYgram_official">официальном чате BYgram</a>.</p>
    </section></main>
    <Footer />
  </>;
}

const featureGroups = [
  {
    id: 'privacy', title: 'Приватность и защита чатов',
    description: 'Выбирайте, чем делиться с собеседниками и как открывать личные диалоги.',
    image: ghostUrl, alt: 'Настройки режима призрака BYgram',
    items: [
      ['Режим призрака', 'Управляйте отправкой прочтения сообщений и историй, видимостью онлайна и набора текста. Прочтение можно отправлять после ответа, реакции или явного действия.'],
      ['Скрытые чаты', 'Убирайте выбранные диалоги из обычного списка и поиска. Свой числовой код открывает отдельную вкладку со скрытыми чатами.'],
      ['Пароли для отдельных чатов', 'Назначайте диалогам собственные пароли. Уведомления скрытых и запароленных чатов не раскрывают текст и вложения.'],
    ],
  },
  {
    id: 'history', title: 'История сообщений',
    description: '«Шпион» сохраняет доступную устройству историю и помогает заметить изменения в переписке.',
    image: spyUrl, alt: 'Настройки сохранения истории и уведомлений об удалениях',
    items: [
      ['Удаления и правки', 'Сохраняйте удалённые сообщения, прежние версии текста и историю удалённого чата с временем замеченного удаления.'],
      ['Уведомления и время событий', 'Отдельно включайте уведомления об удалениях в личных чатах, сохранение замеченного онлайна и локальной даты прочтения.'],
      ['Контроль над данными', 'Историю можно очистить в настройках. Неизвестные устройству сообщения восстановить нельзя, а Telegram не сообщает, кто именно удалил сообщение.'],
    ],
  },
  {
    id: 'appearance', title: 'Оформление и удобство',
    description: 'Меняйте внешний вид профиля, фон чатов и поведение привычного интерфейса.',
    image: profileUrl, alt: 'Настройки фона и оформления профиля BYgram',
    items: [
      ['Профиль', 'Публикуйте изображение или GIF для своего профиля, растягивайте фон на всю страницу и выбирайте вид музыкальной карточки. Чужие фоны можно отключить отдельно.'],
      ['Видеообои', 'Ставьте локальное видео на фон отдельного чата, группы, канала или «Избранного». Оно зацикливается без звука и не отправляется собеседникам.'],
      ['Интерфейс', 'Выбирайте иконку приложения, форму аватаров, выравнивание заголовков и доступные стеклянные эффекты. Папки и нижнюю навигацию можно независимо сворачивать при прокрутке.'],
      ['Свои настройки', 'Управляйте поиском, отображением ID и вибрацией. Экспортируйте параметры или возвращайте их к исходным значениям.'],
    ],
  },
  {
    id: 'activity', title: 'Активность и свои статусы',
    description: 'Дополнительные возможности общения между пользователями BYgram с выбором того, что показывать другим.',
    items: [
      ['Обмен активностью', 'Разрешайте выбранным людям видеть список чатов, просмотр профиля или открытый чат. Для каждого зрителя виды активности настраиваются отдельно.'],
      ['Действия в общих группах', 'В личном чате можно увидеть полученное устройством действие собеседника в общей группе и её название. Telegram не гарантирует доставку каждого события.'],
      ['Свои фразы', 'Заменяйте стандартные статусы набора текста, записи голоса и отправки медиа своими. Их видят пользователи BYgram; обычный Telegram показывает стандартные статусы.'],
      ['Просмотры профиля', 'Следите за просмотрами через возможности BYgram. Это не полный список посетителей из всех клиентов Telegram.'],
    ],
  },
];

function FeaturesPage() {
  return <><Header active="features" /><main id="content">
    <section className="container page-hero"><h1>Больше контроля над общением</h1><p>BYgram сохраняет привычные чаты, звонки и медиа Telegram и добавляет настройки приватности, историю сообщений и своё оформление. Дополнительные возможности собраны в настройках клиента.</p></section>
    <div className="container feature-stories">{featureGroups.map(({ id, title, description, image, alt, items }) => <section id={id} className={`feature-story${image ? '' : ' feature-story--text'}`} key={id}>
      <div><h2>{title}</h2><p>{description}</p><dl className="feature-points">{items.map(([name, text]) => <div key={name}><dt>{name}</dt><dd>{text}</dd></div>)}</dl></div>
      {image && <figure className="feature-story__image"><img src={image} alt={alt} loading="lazy" width="1080" height="2400" /></figure>}
    </section>)}</div>
    <section className="section"><div className="container cta"><div><h2>Попробуйте BYgram</h2><p>Скачайте клиент и выберите нужные вам возможности.</p></div><a className="button" href="download.html"><DownloadIcon />Скачать BYgram</a></div></section>
  </main><Footer /></>;
}

function ChangelogPage() {
  return <><Header active="changelog" /><main id="content" className="container changelog-page"><header className="page-hero"><h1>Что нового</h1></header></main><Footer /></>;
}

const page = location.pathname.endsWith('/changelog.html') ? <ChangelogPage /> : location.pathname.endsWith('/features.html') || location.pathname.endsWith('/editions.html') ? <FeaturesPage /> : location.pathname.endsWith('/download.html') ? <DownloadPage /> : location.pathname.endsWith('/thanks.html') ? <ThanksPage /> : <HomePage />;
createRoot(document.getElementById('root')).render(page);
