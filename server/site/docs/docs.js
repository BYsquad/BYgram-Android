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
const navigationLinks = [...navigation.querySelectorAll('a')];
const compact = matchMedia('(max-width: 760px)');
const sections = [...document.querySelectorAll('main > section')];
const search = document.querySelector('#search');
const searchButton = document.querySelector('#search-open');
const input = search.querySelector('input');
const results = search.querySelector('#search-results');
const searchStatus = search.querySelector('#search-status');

function updateNavigation() {
  const target = document.getElementById(location.hash.slice(1))?.closest('main > section');
  for (const link of navigationLinks) {
    if (link.hash === `#${target?.id ?? 'intro'}`) link.setAttribute('aria-current', 'location');
    else link.removeAttribute('aria-current');
  }
}

navigation.open = !compact.matches;
compact.addEventListener('change', () => { navigation.open = !compact.matches; });
navigation.addEventListener('click', (event) => {
  if (compact.matches && event.target.closest('a')) navigation.open = false;
});
window.addEventListener('hashchange', updateNavigation);
updateNavigation();

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

loadCatalog();
