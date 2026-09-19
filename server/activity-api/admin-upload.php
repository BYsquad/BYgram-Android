<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

try {
    requireAdminSession();
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') throw new InvalidArgumentException('Method not allowed');
    $name = (string) ($_POST['name'] ?? ''); $offset = (int) ($_POST['offset'] ?? -1); $total = (int) ($_POST['total'] ?? 0);
    if (!preg_match('/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/', $name) || $offset < 0 || $total < 1024 || $total > 536870912 || !isset($_FILES['chunk']) || $_FILES['chunk']['error'] !== UPLOAD_ERR_OK || $_FILES['chunk']['size'] > 5 * 1024 * 1024) throw new InvalidArgumentException('Неверный файл APK');
    $target = dirname(__DIR__) . '/' . $name; $part = $target . '.upload'; $input = fopen($_FILES['chunk']['tmp_name'], 'rb'); $output = fopen($part, $offset === 0 ? 'wb' : 'c+b');
    if (!$input || !$output || !flock($output, LOCK_EX)) throw new RuntimeException('Не удалось открыть файл на сервере');
    clearstatcache(true, $part); $size = is_file($part) ? filesize($part) : 0;
    if ($size !== $offset || fseek($output, $offset) !== 0) throw new RuntimeException('Позиция загрузки не совпала');
    if ($offset === 0 && fread($input, 2) !== "PK") throw new InvalidArgumentException('Выбранный файл не является APK');
    rewind($input); $written = stream_copy_to_stream($input, $output); fflush($output); flock($output, LOCK_UN); fclose($input); fclose($output);
    if ($written === false) throw new RuntimeException('Не удалось записать APK');
    $size = $offset + $written;
    if (!empty($_POST['finish'])) {
        $hash = strtolower((string) ($_POST['sha256'] ?? ''));
        if ($size !== $total || !preg_match('/^[a-f0-9]{64}$/', $hash) || !hash_equals($hash, hash_file('sha256', $part))) throw new RuntimeException('APK загружен не полностью');
        if (!rename($part, $target)) throw new RuntimeException('Не удалось опубликовать APK');
    }
    jsonResponse(['ok' => true, 'data' => ['size' => $size]]);
} catch (Throwable $e) {
    jsonResponse(['ok' => false, 'error' => $e->getMessage()], $e instanceof InvalidArgumentException ? 422 : ($e instanceof AdminAuthenticationException ? 401 : 403));
}
