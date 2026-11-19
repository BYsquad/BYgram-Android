import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { guideGroups } from './english-guides.js';

const guides = guideGroups.flatMap(([, articles]) => articles);
const baseUrl = 'https://byxarek.site/TEST-PROJECT/BYgram/docs/en/';
const css = readFileSync(new URL('./docs.css', import.meta.url), 'utf8') + `
.english-menu { display: none; }
@media (max-width: 760px) {
  .english-menu { display: block; }
  .english-menu nav { position: absolute; inset: var(--bar) 0 auto; background: var(--sidebar);
    max-height: calc(100dvh - var(--bar)); overflow-y: auto; padding: 1rem; }
}
`;
const cssName = `guide-${createHash('sha256').update(css).digest('hex').slice(0, 12)}.css`;
const anchor = (heading) => heading.toLowerCase().replaceAll(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

function render(guide) {
  const [route, title, summary, sections] = guide;
  const root = '../'.repeat(route.split('/').filter(Boolean).length);
  const href = (target) => `${root}${target}` || './';
  const navigation = guideGroups.map(([group, articles]) => `<section class="nav-group"><p>${group}</p>
    ${articles.map(([path, name]) => `<a href="${href(path)}"${path === route ? ' aria-current="page"' : ''}>${path ? name : 'Overview'}</a>`).join('')}</section>`).join('');
  const index = guides.indexOf(guide);
  const previous = guides[index - 1];
  const next = guides[index + 1];
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} | BYgram</title>
  <meta name="description" content="${summary}">
  <link rel="canonical" href="${baseUrl}${route}">
  <link rel="stylesheet" href="${href(cssName)}">
</head>
<body>
  <a class="skip" href="#content">Skip to content</a>
  <article class="layout">
    <aside class="sidebar">
      <header class="sidebar-top"><a class="brand" href="${href('')}">BYgram</a></header>
      <nav id="navigation" aria-label="Guides">${navigation}</nav>
    </aside>
    <header class="top">
      <details class="english-menu"><summary>Guides</summary><nav aria-label="Guides">${navigation}</nav></details>
      <strong>Plugin documentation</strong>
      <a class="tools" href="${href('../')}" lang="ru" hreflang="ru">Русский</a>
    </header>
    <main id="content"><section>
      <h1>${title}</h1>
      <p class="lead">${summary}</p>
      ${sections.map(([heading, body]) => `<h2 id="${anchor(heading)}">${heading}</h2>${body}`).join('')}
      <nav id="pagination" aria-label="Adjacent guides">
        ${previous ? `<a href="${href(previous[0])}">← ${previous[1]}</a>` : ''}
        ${next ? `<a class="next" href="${href(next[0])}">${next[1]} →</a>` : ''}
      </nav>
    </section></main>
    <aside class="outline" aria-label="On this page"><p>On this page</p>
      ${sections.map(([heading]) => `<a href="#${anchor(heading)}">${heading}</a>`).join('')}
    </aside>
  </article>
</body>
</html>`.replace(/^[ \t]+$/gm, '');
}

export function englishPages() {
  const files = new Map([[ `docs/en/${cssName}`, css ], ...guides.map((guide) => [`docs/en/${guide[0]}index.html`, render(guide)])]);
  return {
    name: 'bygram-english-docs',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const path = new URL(request.url, 'http://localhost').pathname.slice(1);
        const name = path.endsWith('/') ? `${path}index.html` : path;
        if (!files.has(name)) return next();
        response.setHeader('Content-Type', name.endsWith('.css') ? 'text/css; charset=utf-8' : 'text/html; charset=utf-8');
        response.end(files.get(name));
      });
    },
    generateBundle() {
      for (const [fileName, source] of files) this.emitFile({ type: 'asset', fileName, source });
    },
  };
}
