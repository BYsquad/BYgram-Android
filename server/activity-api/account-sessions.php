<?php
declare(strict_types=1);

function accountSessionSignature(string $payload): string {
    $secret = (string) (appConfig()['bot_token'] ?? '');
    if ($secret === '') throw new RuntimeException('Telegram sign-in is unavailable');
    return hash_hmac('sha256', 'BYgram account session v1:' . $payload, $secret);
}

function newAccountSession(int $userId): string {
    $payload = $userId . '.' . (time() + 90 * 86400) . '.' . bin2hex(random_bytes(16));
    return $payload . '.' . accountSessionSignature($payload);
}

function validAccountSession(string $credential, int $userId): bool {
    if (!preg_match('/^([1-9][0-9]{0,18})\.([0-9]{10})\.[a-f0-9]{32}\.[a-f0-9]{64}$/D', $credential, $matches)
        || $matches[1] !== (string) $userId || (int) $matches[2] <= time()) return false;
    $payload = substr($credential, 0, -65);
    return hash_equals(accountSessionSignature($payload), substr($credential, -64));
}

function accountLoginAttempt(PDO $pdo, int $userId, string $browserToken, string $exchangeToken): void {
    $pdo->exec('DELETE FROM account_login_attempts WHERE expires_at<UTC_TIMESTAMP()');
    $pdo->prepare('INSERT INTO account_login_attempts (login_hash,exchange_hash,telegram_id,expires_at)
        VALUES (?,?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)')
        ->execute([hash('sha256', $browserToken), hash('sha256', $exchangeToken), $userId]);
}

function exchangeAccountLogin(PDO $pdo, int $userId, string $exchangeToken): array {
    $pdo->beginTransaction();
    $lookup = $pdo->prepare('SELECT confirmed_at FROM account_login_attempts
        WHERE exchange_hash=? AND telegram_id=? AND expires_at>UTC_TIMESTAMP() FOR UPDATE');
    $key = hash('sha256', $exchangeToken);
    $lookup->execute([$key, $userId]);
    $attempt = $lookup->fetch();
    if (!$attempt || !$attempt['confirmed_at']) {
        $pdo->rollBack();
        return ['ok' => (bool) $attempt, 'expired' => !$attempt];
    }
    $credential = newAccountSession($userId);
    $pdo->prepare('DELETE FROM account_login_attempts WHERE exchange_hash=?')->execute([$key]);
    $pdo->commit();
    return ['ok' => true, 'account_token' => $credential];
}
