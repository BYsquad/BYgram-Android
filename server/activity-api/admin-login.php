<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

if (($_GET['action'] ?? '') === 'logout') {
    startAdminSession();
    $_SESSION = [];
    session_destroy();
    header('Location: admin.php', true, 303);
    exit;
}

try {
    $id = validateTelegramLogin($_GET);
    startAdminSession();
    session_regenerate_id(true);
    $_SESSION['telegram_id'] = $id;
    header('Location: admin.php', true, 303);
} catch (Throwable $error) {
    http_response_code(403);
    $message = $error instanceof AdminAuthenticationException ? $error->getMessage() : 'Не удалось выполнить вход';
    echo '<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>BYgram — вход</title><link rel="stylesheet" href="../assets/admin.css"><main class="login"><section class="card"><div class="mark">BY</div><h1>Ошибка входа</h1><p class="error">' . htmlspecialchars($message, ENT_QUOTES, 'UTF-8') . '</p><a href="admin.php">Вернуться</a></section></main></html>';
}
