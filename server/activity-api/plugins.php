<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-catalog.php';
try { $admin = requireAdminSession(); } catch (AdminAuthenticationException) { header('Location: admin.php'); exit; }
header('Cache-Control: no-store');
header("Content-Security-Policy: default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
$_SESSION['plugins_csrf'] ??= bin2hex(random_bytes(32));
$message = '';
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') try {
    if (!hash_equals($_SESSION['plugins_csrf'], (string) ($_POST['csrf'] ?? ''))) throw new InvalidArgumentException('Обновите страницу');
    $id = filter_var($_POST['id'] ?? 0, FILTER_VALIDATE_INT); if (!$id || $id < 1) throw new InvalidArgumentException('Неверная заявка');
    if (($_POST['action'] ?? '') === 'prepare') preparePluginReview(db(), $id);
    elseif (($_POST['action'] ?? '') === 'approve' && isset($_POST['reviewed'])) approvePlugin(db(), $id, $admin, (string) ($_POST['hash'] ?? ''));
    elseif (($_POST['action'] ?? '') === 'reject') {
        $note = trim((string) ($_POST['note'] ?? '')); if ($note === '' || mb_strlen($note) > 1000) throw new InvalidArgumentException('Укажите причину, до 1000 символов');
        db()->prepare("UPDATE plugin_submissions SET status='rejected',review_note=?,reviewed_by=?,reviewed_at=UTC_TIMESTAMP() WHERE id=? AND status IN ('pending','review')")->execute([$note, $admin, $id]);
    } else throw new InvalidArgumentException('Подтвердите проверку кода и пакета');
    $message = 'Сохранено';
} catch (Throwable $error) { $message = $error->getMessage(); }
if (isset($_GET['download'])) {
    $id = filter_var($_GET['download'], FILTER_VALIDATE_INT);
    $q = db()->prepare("SELECT candidate FROM plugin_submissions WHERE id=? AND status='review'"); $q->execute([$id ?: 0]); $candidate = json_decode((string) $q->fetchColumn(), true);
    $file = $candidate['file'] ?? '';
    if (!preg_match('/^[a-f0-9]{64}\.byplugin$/D', $file)) { http_response_code(404); exit; }
    header('Content-Type: application/octet-stream'); header('Content-Disposition: attachment; filename="' . $file . '"'); header('X-Content-Type-Options: nosniff');
    readfile(pluginCandidateDirectory() . '/' . $file); exit;
}
$rows = db()->query('SELECT * FROM plugin_submissions ORDER BY id DESC LIMIT 100')->fetchAll(PDO::FETCH_ASSOC);
function pluginEscape(mixed $value): string { return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
?>
<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Модерация плагинов BYgram</title><link rel="stylesheet" href="../assets/admin.css">
<main><h1>Заявки плагинов</h1><p><a href="admin.php">Админ-панель</a></p><p><?= pluginEscape($message) ?></p>
<p>Каждый Release проверяется отдельно. Скачайте кандидат, проверьте исходники и поведение; публикация закрепляет конкретные байты и SHA-256. Новые версии требуют новой заявки из приложения.</p>
<?php foreach ($rows as $row): $candidate = json_decode((string) $row['candidate'], true); ?>
<section class="card"><h2>#<?= (int) $row['id'] ?> — <?= pluginEscape($row['repository']) ?></h2>
<p>Автор заявки: <?= (int) $row['telegram_id'] ?> · <?= pluginEscape($row['status']) ?> · <?= pluginEscape($row['release_tag']) ?></p>
<p><a target="_blank" rel="noopener noreferrer" href="<?= pluginEscape($row['repository']) ?>/releases">Публичные Releases и исходники</a></p>
<?php if ($candidate): ?><pre><?= pluginEscape(json_encode($candidate, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES)) ?></pre><?php endif; ?>
<?php if (in_array($row['status'], ['pending','review'], true)): ?>
<form method="post"><input type="hidden" name="csrf" value="<?= pluginEscape($_SESSION['plugins_csrf']) ?>"><input type="hidden" name="id" value="<?= (int) $row['id'] ?>">
<button name="action" value="prepare">Получить последний стабильный Release</button>
<?php if ($row['status'] === 'review'): ?><p><a href="?download=<?= (int) $row['id'] ?>">Скачать точный кандидат для проверки</a></p><input type="hidden" name="hash" value="<?= pluginEscape($candidate['sha256']) ?>"><label><input type="checkbox" name="reviewed" value="1"> Я проверил исходники и этот пакет</label><button name="action" value="approve">Одобрить и опубликовать</button><?php endif; ?>
<label>Причина отказа <input name="note" maxlength="1000"></label><button name="action" value="reject">Отклонить</button></form>
<?php else: ?><p><?= pluginEscape($row['review_note']) ?></p><?php endif; ?></section><?php endforeach; ?></main></html>
