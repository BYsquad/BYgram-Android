<?php
declare(strict_types=1);

function appConfig(): array { static $config; return $config ??= require __DIR__ . '/config.php'; }
function db(): PDO {
    static $pdo; $c = appConfig();
    return $pdo ??= new PDO($c['db_dsn'], $c['db_user'], $c['db_password'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
}
function appSetting(string $key, string $default = ''): string {
    $query = db()->prepare('SELECT setting_value FROM app_settings WHERE setting_key=?');
    $query->execute([$key]);
    $value = $query->fetchColumn();
    return $value === false ? $default : (string) $value;
}
function saveAppSetting(string $key, string $value): void {
    db()->prepare('INSERT INTO app_settings (setting_key,setting_value) VALUES (?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)')->execute([$key, $value]);
}
function licensed(int $telegramId): bool {
    $q = db()->prepare("SELECT 1 FROM licenses WHERE telegram_id=? AND status='active' AND (valid_until IS NULL OR valid_until>UTC_TIMESTAMP())"); $q->execute([$telegramId]); return (bool) $q->fetchColumn();
}
function typingPublicKeys(PDO $pdo, int $telegramId): array {
    $query = $pdo->prepare("SELECT public_key FROM typing_devices WHERE telegram_id=?
        UNION ALL
        SELECT d.public_key FROM licensed_devices d
        JOIN licenses l ON l.telegram_id=d.telegram_id AND l.status='active' AND (l.valid_until IS NULL OR l.valid_until>UTC_TIMESTAMP())
        WHERE d.telegram_id=? AND d.revoked_at IS NULL");
    $query->execute([$telegramId, $telegramId]);
    return $query->fetchAll(PDO::FETCH_COLUMN);
}
function grantLicense(PDO $pdo, int $telegramId): void { $pdo->prepare("INSERT INTO licenses (telegram_id,status,valid_until) VALUES (?,'active',NULL) ON DUPLICATE KEY UPDATE status='active',valid_until=NULL")->execute([$telegramId]); }
function addReferralToOrder(PDO $pdo, array &$order): void {
    $q=$pdo->prepare('SELECT r.referrer_id,s.percent FROM referrals r JOIN referral_settings s ON s.id=1 AND s.enabled=1 WHERE r.referred_id=?');$q->execute([$order['buyer_id']]);$ref=$q->fetch();
    if(!$ref||(float)$order['amount']<=0)return;
    $commission=round((float)$order['amount']*(float)$ref['percent']/100,2);
    if($commission<=0)return;
    $q=$pdo->prepare('INSERT IGNORE INTO referral_earnings (order_id,referrer_id,referred_id,amount,percent,commission,currency) VALUES (?,?,?,?,?,?,?)');
    $q->execute([$order['id'],$ref['referrer_id'],$order['buyer_id'],$order['amount'],$ref['percent'],$commission,$order['currency']]);
    if($q->rowCount()){$order['_referrer_id']=(int)$ref['referrer_id'];$order['_commission']=$commission;}
}
function isOwner(int $id): bool { return $id === (int) (appConfig()['owner_id'] ?? 0); }
function isAdmin(int $id): bool {
    if (isOwner($id)) return true;
    try { $q=db()->prepare('SELECT 1 FROM admins WHERE telegram_id=?'); $q->execute([$id]); return (bool)$q->fetchColumn(); }
    catch (Throwable) { return false; }
}
final class AdminAuthenticationException extends RuntimeException {}
function telegramLoginHash(array $values, string $token): string {
    unset($values['hash']);
    ksort($values);
    $pairs = [];
    foreach ($values as $key => $value) {
        if (!is_scalar($value)) throw new AdminAuthenticationException('Недействительная сессия Telegram');
        $pairs[] = $key . '=' . $value;
    }
    return hash_hmac('sha256', implode("\n", $pairs), hash('sha256', $token, true));
}
function validateTelegramLogin(array $values, ?string $token = null): int {
    $hash = (string) ($values['hash'] ?? '');
    $authDate = (int) ($values['auth_date'] ?? 0);
    $id = (int) ($values['id'] ?? 0);
    $token ??= (string) (appConfig()['bot_token'] ?? '');
    if ($hash === '' || $token === '' || $id < 1 || abs(time() - $authDate) > 600
            || !hash_equals(telegramLoginHash($values, $token), $hash) || !isAdmin($id)) {
        throw new AdminAuthenticationException('Недействительная сессия Telegram');
    }
    return $id;
}
function startAdminSession(): void {
    if (session_status() === PHP_SESSION_ACTIVE) return;
    $path = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/'))), '/') . '/';
    session_name('bygram_admin');
    session_set_cookie_params(['lifetime' => 0, 'path' => $path, 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
    ini_set('session.use_strict_mode', '1');
    session_start();
}
function adminSessionUser(): int {
    startAdminSession();
    $id = (int) ($_SESSION['telegram_id'] ?? 0);
    if ($id > 0 && isAdmin($id)) return $id;
    unset($_SESSION['telegram_id']);
    return 0;
}
function requireAdminSession(): int {
    $id = adminSessionUser();
    if ($id < 1) throw new AdminAuthenticationException('Требуется вход через Telegram');
    return $id;
}
function jsonResponse(mixed $data, int $status = 200): never { http_response_code($status); header('Content-Type: application/json; charset=utf-8'); echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function validateMiniApp(string $initData): int {
    parse_str($initData, $values); $hash = (string) ($values['hash'] ?? ''); unset($values['hash']);
    if ($hash === '' || abs(time() - (int) ($values['auth_date'] ?? 0)) > 3600) throw new RuntimeException('Недействительная сессия Telegram');
    ksort($values); $pairs = []; foreach ($values as $key => $value) $pairs[] = $key . '=' . $value;
    $token = (string) (appConfig()['bot_token'] ?? '');
    if ($token === '') throw new RuntimeException('Telegram-авторизация не настроена');
    $secret = hash_hmac('sha256', $token, 'WebAppData', true);
    if (!hash_equals(hash_hmac('sha256', implode("\n", $pairs), $secret), $hash)) throw new RuntimeException('Неверная подпись Telegram');
    $user = json_decode((string) ($values['user'] ?? ''), true); $id = (int) ($user['id'] ?? 0);
    if ($id < 1 || !isAdmin($id)) throw new RuntimeException('Нет доступа');
    return $id;
}
function hasExteraBadge(int $userId): bool {
    $context = stream_context_create(['http' => ['timeout' => 6, 'ignore_errors' => true], 'ssl' => ['verify_peer' => true, 'verify_peer_name' => true]]);
    $body = @file_get_contents('https://api.exteragram.app/api/v1/profiles/' . $userId, false, $context);
    if ($body === false) throw new RuntimeException('Не удалось проверить значок ExteraGram');
    $profile = json_decode($body, true);
    return is_array($profile) && ($profile['type'] ?? '') === 'USER' && (int) ($profile['id'] ?? 0) === $userId
            && (int) ($profile['badge']['documentId'] ?? 0) > 0;
}
function loadBadges(): array {
    $badges = json_decode((string) @file_get_contents(__DIR__ . '/badges.json'), true);
    return is_array($badges) ? $badges : [];
}
function badgeKey(string $type, int $id): string {
    if (!in_array($type, ['user', 'chat'], true) || $id < 1) throw new InvalidArgumentException('Неверный тип или ID');
    return $type . ':' . $id;
}
function badgeId(string $type, mixed $value): int {
    $text = trim((string) $value);
    if ($type === 'chat' && str_starts_with($text, '-100')) $text = substr($text, 4);
    elseif ($type === 'chat' && str_starts_with($text, '-')) $text = substr($text, 1);
    if (!preg_match('/^[1-9][0-9]{0,18}$/D', $text)) throw new InvalidArgumentException('Неверный Telegram ID');
    return (int) $text;
}
function badgeFor(array $badges, string $type, int $id): bool {
    return !empty($badges[badgeKey($type, $id)]) || ($type === 'user' && !empty($badges[(string) $id]['bygram']));
}
function saveBadges(array $badges): void {
    ksort($badges, SORT_STRING);
    $json = json_encode($badges, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    if ($json === false || file_put_contents(__DIR__ . '/badges.json.tmp', $json . "\n", LOCK_EX) === false
            || !rename(__DIR__ . '/badges.json.tmp', __DIR__ . '/badges.json')) {
        throw new RuntimeException('Не удалось сохранить значки');
    }
}
