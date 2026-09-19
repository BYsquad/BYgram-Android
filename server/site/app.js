fetch('api/release.json', { cache: 'no-store' }).then(response => response.ok ? response.json() : null).then(release => {
  if (!release) return;
  document.querySelectorAll('[data-release-version]').forEach(node => node.textContent = release.version);
  document.querySelectorAll('[data-release-code]').forEach(node => node.textContent = release.version_code);
  document.querySelectorAll('[data-release-status]').forEach(node => node.textContent = release.status);
  document.querySelectorAll('[data-release-size]').forEach(node => node.textContent = release.size);
  document.querySelectorAll('[data-release-requirements]').forEach(node => node.textContent = release.requirements);
  document.querySelectorAll('[data-release-package]').forEach(node => node.textContent = release.package);
  document.querySelectorAll('[data-download]').forEach(link => link.href = release.apk_name);
}).catch(() => {});

document.querySelectorAll('[data-download]').forEach(link => link.addEventListener('click', () => {
  setTimeout(() => location.href = 'thanks.html', 900);
}));

document.querySelectorAll('[data-year]').forEach(node => {
  node.textContent = new Date().getFullYear();
});
