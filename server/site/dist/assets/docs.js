import"./modulepreload-polyfill.js";var e=`from bygram import Plugin
from bygram.settings import Switch, TextInput, Action


class HelloPlugin(Plugin):
    def on_enable(self):
        self.context.ui.add_menu("chat.actions", "hello", "Hello BYgram", self.show)

    def show(self, event=None):
        if self.context.settings.get("enabled", True):
            self.context.ui.bulletin(self.context.settings.get("text", "Hello BYgram!"))

    def create_settings(self):
        return [Switch("enabled", "Show greeting", True),
                TextInput("text", "Greeting", "Hello BYgram!", max_length=120),
                Action("Preview", self.show)]
`,t=`{\r
  "manifest_version": 1, "id": "dev.bygram.hello", "name": "Hello BYgram",\r
  "description": "A greeting in the chat menu with native settings", "version": "1.0.1",\r
  "author": "BYgram SDK example", "entrypoint": "main.py:HelloPlugin",\r
  "sdk": {"min": 1, "max": 1}, "python": "3.11", "capabilities": ["ui"],\r
  "account_scope": "global", "settings_schema": 1\r
}\r
`,n=``+new URL(`manifest-v1.schema.json`,import.meta.url).href;document.querySelector(`#hello-code`).textContent=e.trimEnd(),document.querySelector(`#hello-manifest`).textContent=JSON.stringify(JSON.parse(t),null,2);var r=document.querySelector(`#schema-download`);r.href=n,r.hidden=!1;for(let e of document.querySelectorAll(`main figure`)){let t=e.querySelector(`figcaption`),n=document.createElement(`button`);n.type=`button`,n.textContent=`Копировать`,n.setAttribute(`aria-label`,`Копировать: ${t.textContent}`),n.addEventListener(`click`,async()=>{try{await navigator.clipboard.writeText(e.querySelector(`code`).textContent),n.textContent=`Скопировано`}catch{n.textContent=`Выделите текст для копирования`}}),t.append(n)}var i=document.querySelector(`#navigation`),a=[...i.querySelectorAll(`a`)],o=matchMedia(`(max-width: 760px)`),s=[...document.querySelectorAll(`main > section`)],c=document.querySelector(`#search`),l=document.querySelector(`#search-open`),u=c.querySelector(`input`),d=c.querySelector(`#search-results`),f=c.querySelector(`#search-status`);function p(){let e=document.getElementById(location.hash.slice(1))?.closest(`main > section`);for(let t of a)t.hash===`#${e?.id??`intro`}`?t.setAttribute(`aria-current`,`location`):t.removeAttribute(`aria-current`)}i.open=!o.matches,o.addEventListener(`change`,()=>{i.open=!o.matches}),i.addEventListener(`click`,e=>{o.matches&&e.target.closest(`a`)&&(i.open=!1)}),window.addEventListener(`hashchange`,p),p();function m(){d.replaceChildren();let e=u.value.trim().toLocaleLowerCase(`ru`).split(/\s+/).filter(Boolean),t=e.length?s.filter(t=>{let n=t.textContent.toLocaleLowerCase(`ru`);return e.every(e=>n.includes(e))}):s;f.textContent=t.length?``:`Ничего не найдено.`;for(let e of t){let t=document.createElement(`a`);t.href=`#${e.id}`,t.textContent=e.querySelector(`h1, h2`).textContent,t.addEventListener(`click`,()=>{c.close(),e.tabIndex=-1,e.focus({preventScroll:!0})});let n=document.createElement(`li`);n.append(t),d.append(n)}}function h(){c.open||(m(),c.showModal())}l.hidden=!1,l.addEventListener(`click`,h),u.addEventListener(`input`,m),document.addEventListener(`keydown`,e=>{(e.ctrlKey||e.metaKey)&&e.key.toLowerCase()===`k`&&(e.preventDefault(),h())});var g=document.querySelector(`#catalog-status`),_=document.querySelector(`#packages`),v=new URL(`../plugins/`,location.href);async function y(e){let t=await fetch(new URL(e,v),{cache:`no-store`,signal:AbortSignal.timeout(1e4)});if(!t.ok)throw Error(`HTTP ${t.status}`);return t.json()}async function b(){g.textContent=`Загружаем каталог…`;try{let[e,t]=await Promise.all([y(`index.json`),y(`revoked.json`)]);if(e.format!==1||!Array.isArray(e.plugins)||!Array.isArray(t.sha256))throw Error(`Catalog format`);let n=new Set(t.sha256);for(let{manifest:t,file:r,sha256:i}of e.plugins){if(!t?.name||!/^[a-f0-9]{64}$/.test(i)||r!==`${i}.byplugin`||n.has(i))continue;let e=document.createElement(`li`),a=document.createElement(`h3`),o=document.createElement(`small`),s=document.createElement(`p`),c=document.createElement(`a`);a.textContent=t.name,o.textContent=`${t.version} · ${t.author}`,s.textContent=t.description,c.href=new URL(r,v),c.download=r,c.textContent=`Скачать .byplugin ↓`,c.setAttribute(`aria-label`,`Скачать ${t.name} (.byplugin)`),e.append(a,o,s,c),_.append(e)}g.textContent=_.children.length?``:`В каталоге пока нет пакетов.`}catch{_.replaceChildren(),g.textContent=`Каталог временно недоступен. Попробуйте обновить страницу.`}}b();