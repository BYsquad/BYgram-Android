import helloCode from '../../../docs/plugins/examples/hello/main.py?raw';
import helloManifest from '../../../docs/plugins/examples/hello/manifest.json?raw';
import schemaUrl from '../../../docs/plugins/manifest-v1.schema.json?url&no-inline';

document.querySelector('#hello-code').textContent = helloCode.trimEnd();
document.querySelector('#hello-manifest').textContent = JSON.stringify(JSON.parse(helloManifest), null, 2);
const schemaLink = document.querySelector('#schema-download');
schemaLink.href = schemaUrl;
schemaLink.hidden = false;

for (const figure of document.querySelectorAll('main figure')) {
  const caption = figure.querySelector('figcaption');
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = 'Копировать';
  button.setAttribute('aria-label', `Копировать: ${caption.textContent}`);
  button.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(figure.querySelector('code').textContent);
      button.textContent = 'Скопировано';
    } catch {
      button.textContent = 'Выделите текст для копирования';
    }
  });
  caption.append(button);
}

const navigation = document.querySelector('#navigation');
const navigationLinks = [...navigation.querySelectorAll('a[href^="#"]')];
const sections = navigationLinks.map((link) => document.getElementById(link.hash.slice(1)));
const outline = document.querySelector('#outline');
const pagination = document.querySelector('#pagination');
const sidebarOpen = document.querySelector('#sidebar-open');
const sidebarClose = document.querySelector('#sidebar-close');
const theme = document.querySelector('#theme');
const compact = matchMedia('(max-width: 760px)');
const search = document.querySelector('#search');
const searchButton = document.querySelector('#search-open');
const input = search.querySelector('input');
const results = search.querySelector('#search-results');
const searchStatus = search.querySelector('#search-status');
let active;

const sidebarIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><rect x="4" y="5" width="16" height="14" rx="2"/><path d="M9 5v14"/></svg>';
sidebarOpen.innerHTML = sidebarClose.innerHTML = sidebarIcon;
sidebarOpen.hidden = sidebarClose.hidden = theme.hidden = false;

function sidebar(show) {
  document.body.classList.toggle('sidebar-open', compact.matches && show);
  if (!compact.matches) document.body.classList.toggle('sidebar-collapsed', !show);
  sidebarOpen.setAttribute('aria-expanded', String(show));
}

sidebar(!compact.matches);
compact.addEventListener('change', () => sidebar(!compact.matches));
sidebarOpen.addEventListener('click', () => sidebar(sidebarOpen.getAttribute('aria-expanded') !== 'true'));
sidebarClose.addEventListener('click', () => {
  sidebar(false);
  sidebarOpen.focus();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && document.body.classList.contains('sidebar-open')) {
    sidebar(false);
    sidebarOpen.focus();
  }
});

document.documentElement.dataset.theme = localStorage.getItem('bygram-docs-theme') || 'dark';
theme.addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('bygram-docs-theme', next);
});

function makeLink(hash, title) {
  const link = document.createElement('a');
  link.href = hash;
  link.textContent = title;
  return link;
}

function updateOutline() {
  if (!active) return;
  const headings = [...active.querySelectorAll('h2[id], h3[id]')].filter((heading) => heading !== active.firstElementChild);
  let current = headings[0];
  for (const heading of headings) {
    if (heading.getBoundingClientRect().top <= 100) current = heading;
  }
  for (const link of outline.querySelectorAll('a')) {
    if (link.hash === `#${current?.id}`) link.setAttribute('aria-current', 'location');
    else link.removeAttribute('aria-current');
  }
}

function updateNavigation() {
  const target = document.getElementById(location.hash.slice(1));
  const page = target?.closest('main > section') ?? sections[0];
  const changed = page !== active;
  if (changed) {
    active = page;
    const index = sections.indexOf(page);
    for (const section of sections) section.hidden = section !== page;
    for (const link of navigationLinks) {
      if (link.hash === `#${page.id}`) {
        link.setAttribute('aria-current', 'page');
        const group = link.closest('details');
        if (group) group.open = true;
      } else link.removeAttribute('aria-current');
    }
    const title = page.querySelector('h1, h2');
    document.title = `${title.textContent} | Плагины BYgram`;
    const heading = document.createElement('p');
    heading.textContent = '☰  На этой странице';
    outline.replaceChildren(heading);
    for (const item of page.querySelectorAll('h2[id], h3[id]')) {
      if (item !== title) outline.append(makeLink(`#${item.id}`, item.textContent));
    }
    outline.hidden = outline.children.length === 1;
    pagination.replaceChildren();
    for (const offset of [-1, 1]) {
      const adjacent = navigationLinks[index + offset];
      if (!adjacent) continue;
      const labelText = adjacent.querySelector('.nav-icon')?.nextSibling?.textContent ?? adjacent.textContent;
      const link = makeLink(adjacent.hash, labelText.trim());
      link.className = offset < 0 ? 'previous' : 'next';
      const label = document.createElement('small');
      label.textContent = offset < 0 ? '← Предыдущая' : 'Следующая →';
      link.prepend(label);
      pagination.append(link);
    }
    if (page.id === 'catalog' && !page.dataset.loaded) {
      page.dataset.loaded = 'true';
      loadCatalog();
    }
  }
  requestAnimationFrame(() => {
    if (target && target !== page) target.scrollIntoView();
    else if (changed) window.scrollTo(0, 0);
    updateOutline();
  });
}

navigation.addEventListener('click', (event) => {
  const link = event.target.closest('a');
  if (!link) return;
  const group = link.closest('summary')?.parentElement;
  if (group) group.open = true;
  if (compact.matches) sidebar(false);
});
window.addEventListener('hashchange', updateNavigation);
window.addEventListener('scroll', updateOutline, { passive: true });

function renderSearch() {
  results.replaceChildren();
  const terms = input.value.trim().toLocaleLowerCase('ru').split(/\s+/).filter(Boolean);
  const matches = terms.length ? sections.filter((section) => {
    const content = section.textContent.toLocaleLowerCase('ru');
    return terms.every((term) => content.includes(term));
  }) : sections;
  searchStatus.textContent = matches.length ? '' : 'Ничего не найдено.';
  for (const section of matches) {
    const link = document.createElement('a');
    link.href = `#${section.id}`;
    link.textContent = section.querySelector('h1, h2').textContent;
    link.addEventListener('click', () => {
      search.close();
      section.tabIndex = -1;
      section.focus({ preventScroll: true });
    });
    const item = document.createElement('li');
    item.append(link);
    results.append(item);
  }
}

function openSearch() {
  if (search.open) return;
  renderSearch();
  search.showModal();
}

searchButton.hidden = false;
searchButton.addEventListener('click', openSearch);
input.addEventListener('input', renderSearch);
document.addEventListener('keydown', (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    openSearch();
  }
});

const catalogStatus = document.querySelector('#catalog-status');
const packages = document.querySelector('#packages');
const catalogBase = new URL('../plugins/', location.href);

async function getCatalogFile(file) {
  const response = await fetch(new URL(file, catalogBase), { cache: 'no-store', signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function loadCatalog() {
  catalogStatus.textContent = 'Загружаем каталог…';
  try {
    const [index, revoked] = await Promise.all([getCatalogFile('index.json'), getCatalogFile('revoked.json')]);
    if (index.format !== 1 || !Array.isArray(index.plugins) || !Array.isArray(revoked.sha256)) throw new Error('Catalog format');
    const blocked = new Set(revoked.sha256);
    for (const { manifest, file, sha256 } of index.plugins) {
      if (!manifest?.name || !/^[a-f0-9]{64}$/.test(sha256) || file !== `${sha256}.byplugin` || blocked.has(sha256)) continue;
      const card = document.createElement('li');
      const title = document.createElement('h3');
      const author = document.createElement('small');
      const description = document.createElement('p');
      const download = document.createElement('a');
      title.textContent = manifest.name;
      author.textContent = `${manifest.version} · ${manifest.author}`;
      description.textContent = manifest.description;
      download.href = new URL(file, catalogBase);
      download.download = file;
      download.textContent = 'Скачать .byplugin ↓';
      download.setAttribute('aria-label', `Скачать ${manifest.name} (.byplugin)`);
      card.append(title, author, description, download);
      packages.append(card);
    }
    catalogStatus.textContent = packages.children.length ? '' : 'В каталоге пока нет пакетов.';
  } catch {
    packages.replaceChildren();
    catalogStatus.textContent = 'Каталог временно недоступен. Попробуйте обновить страницу.';
  }
}

updateNavigation();
