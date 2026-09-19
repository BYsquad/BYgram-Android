<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

$currentUserId = adminSessionUser();
$botUsername = appSetting('bot_username');
$adminUrl = (string) (appConfig()['admin_url'] ?? 'https://byxarek.site/TEST-PROJECT/BYgram/api/admin.php');
$loginUrl = preg_replace('~/[^/]+$~', '/admin-login.php', $adminUrl);
?>
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="dark">
<title>BYgram — управление</title>
<link rel="stylesheet" href="../assets/admin.css">
</head>
<body>
<?php if ($currentUserId < 1): ?>
<main class="login"><section class="card">
<div class="mark">BY</div><h1>Управление BYgram</h1><p>Войдите через Telegram с аккаунта администратора.</p>
<script async src="https://telegram.org/js/telegram-widget.js?22" data-telegram-login="<?= htmlspecialchars($botUsername, ENT_QUOTES, 'UTF-8') ?>" data-size="large" data-radius="12" data-userpic="false" data-auth-url="<?= htmlspecialchars((string) $loginUrl, ENT_QUOTES, 'UTF-8') ?>"></script>
</section></main>
<?php else: ?>
<div id="admin-root"></div>
<a class="admin-logout" href="admin-login.php?action=logout">Выйти</a>
<script type="module" src="../assets/admin.js"></script>
<?php endif; ?>
</body></html>
