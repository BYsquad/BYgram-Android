<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

function botReply(int $chatId, string $text): void {
    $token = (string) (appConfig()['bot_token'] ?? '');
    $body = http_build_query(['chat_id' => $chatId, 'text' => $text]);
    $context = stream_context_create(['http' => ['method' => 'POST', 'header' => "Content-Type: application/x-www-form-urlencoded\r\n", 'content' => $body, 'timeout' => 8]]);
    @file_get_contents('https://api.telegram.org/bot' . $token . '/sendMessage', false, $context);
}

$expectedSecret = (string) (appConfig()['bot_webhook_secret'] ?? appConfig()['webhook_secret'] ?? '');
$receivedSecret = (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');
if ($expectedSecret === '' || !hash_equals($expectedSecret, $receivedSecret)) { http_response_code(403); exit; }
$update = json_decode((string) file_get_contents('php://input'), true);
$message = is_array($update) ? ($update['message'] ?? null) : null;
$fromId = (int) ($message['from']['id'] ?? 0); $chatId = (int) ($message['chat']['id'] ?? 0); $text = trim((string) ($message['text'] ?? ''));
if ($fromId < 1 || $chatId < 1) { http_response_code(204); exit; }
if (preg_match('/^\/start(?:@\w+)?\s+typing_([A-Za-z0-9_-]{43})$/D', $text, $typingMatch)) {
    $pdo = db();
    $query = $pdo->prepare('SELECT telegram_id,public_key FROM typing_activations WHERE token_hash=? AND expires_at>UTC_TIMESTAMP() AND confirmed_at IS NULL');
    $query->execute([hash('sha256', $typingMatch[1])]); $attempt = $query->fetch();
    if (!$attempt || (int) $attempt['telegram_id'] !== $fromId) { botReply($chatId, 'Ссылка подтверждения недействительна для этого аккаунта.'); http_response_code(204); exit; }
    $fingerprint = hash('sha256', base64_decode((string) $attempt['public_key'], true));
    $pdo->prepare('INSERT IGNORE INTO typing_devices (telegram_id,key_fingerprint,public_key) VALUES (?,?,?)')->execute([$fromId,$fingerprint,$attempt['public_key']]);
    $pdo->prepare('UPDATE typing_activations SET confirmed_at=UTC_TIMESTAMP() WHERE token_hash=?')->execute([hash('sha256', $typingMatch[1])]);
    botReply($chatId, 'BYgram подтверждён. Вернитесь в настройки кастомных статусов и нажмите «Сохранить».');
    http_response_code(204); exit;
}
if (!preg_match('/^\/start(?:@\w+)?\s+bind_([A-Za-z0-9_-]{43})$/D', $text, $match)) { http_response_code(204); exit; }
$value = $match[1]; $pdo = db();
$query = $pdo->prepare('SELECT id,expected_user_id,proof_at FROM device_activations WHERE token_hash=? AND expires_at>UTC_TIMESTAMP()');
$query->execute([hash('sha256', $value)]); $attempt = $query->fetch();
if (!$attempt || (int) $attempt['expected_user_id'] !== $fromId) { botReply($chatId, 'Эта ссылка активации недействительна или создана для другого Telegram-аккаунта.'); http_response_code(204); exit; }
if (!licensed($fromId)) { botReply($chatId, 'Для этого Telegram ID нет активной лицензии BYgram Full.'); http_response_code(204); exit; }
$user = $message['from'];
$pdo->prepare('INSERT INTO users (telegram_id,username,first_name,last_name,last_seen_at) VALUES (?,?,?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE username=VALUES(username),first_name=VALUES(first_name),last_name=VALUES(last_name),last_seen_at=UTC_TIMESTAMP()')
    ->execute([$fromId, $user['username'] ?? null, $user['first_name'] ?? null, $user['last_name'] ?? null]);
$pdo->prepare('UPDATE device_activations SET telegram_confirmed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$attempt['id']]);
botReply($chatId, empty($attempt['proof_at']) ? 'Аккаунт подтверждён. Вернитесь в BYgram, чтобы завершить привязку устройства.' : 'Аккаунт подтверждён. Вернитесь в BYgram — активация завершится автоматически.');
http_response_code(204);
