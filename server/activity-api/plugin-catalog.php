<?php
declare(strict_types=1);

function pluginRepository(string $url): string {
    if (!preg_match('~^https://github\.com/([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9_.-]{1,100})/?$~D', trim($url), $parts)
            || in_array($parts[2], ['.', '..'], true) || str_ends_with($parts[2], '.git')) {
        throw new InvalidArgumentException('Укажите публичный репозиторий: https://github.com/owner/plugin');
    }
    return 'https://github.com/' . strtolower($parts[1] . '/' . $parts[2]);
}
function submitPlugin(PDO $pdo, int $userId, string $url): int {
    $repo = pluginRepository($url);
    $pdo->beginTransaction();
    try {
        // Serialize submissions for one author; API identity comes from the signed session.
        $lock = $pdo->prepare('SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE'); $lock->execute([$userId]);
        if (!$lock->fetchColumn()) throw new InvalidArgumentException('Аккаунт не зарегистрирован');
        $query = $pdo->prepare("SELECT id FROM plugin_submissions WHERE repository=? AND status IN ('pending','review') LIMIT 1"); $query->execute([$repo]);
        if ($query->fetchColumn()) throw new InvalidArgumentException('Заявка на этот репозиторий уже ожидает модерации');
        $query = $pdo->prepare('SELECT COUNT(*) FROM plugin_submissions WHERE telegram_id=? AND created_at>UTC_TIMESTAMP()-INTERVAL 1 DAY'); $query->execute([$userId]);
        if ((int) $query->fetchColumn() >= 5) throw new InvalidArgumentException('Можно отправить не более пяти заявок в сутки');
        $pdo->prepare('INSERT INTO plugin_submissions (telegram_id,repository) VALUES (?,?)')->execute([$userId, $repo]);
        $id = (int) $pdo->lastInsertId(); $pdo->commit(); return $id;
    } catch (Throwable $error) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        if ($error instanceof PDOException && $error->getCode() === '23000') throw new InvalidArgumentException('Заявка на этот репозиторий уже ожидает модерации');
        throw $error;
    }
}
function pluginSubmissions(PDO $pdo, int $userId): array {
    $query = $pdo->prepare('SELECT id,repository,status,release_tag,review_note,created_at FROM plugin_submissions WHERE telegram_id=? ORDER BY id DESC LIMIT 30');
    $query->execute([$userId]); return $query->fetchAll(PDO::FETCH_ASSOC);
}
function pluginDownload(string $url, int $limit, bool $asset = false): string {
    $data = ''; $location = '';
    for ($hop = 0; $hop < 4; $hop++) {
        $host = (string) parse_url($url, PHP_URL_HOST);
        if (parse_url($url, PHP_URL_SCHEME) !== 'https' || parse_url($url, PHP_URL_USER) !== null || parse_url($url, PHP_URL_PORT) !== null
                || !in_array($host, $asset ? ['api.github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com'] : ['api.github.com'], true)) {
            throw new InvalidArgumentException('Недопустимый адрес GitHub');
        }
        $data = ''; $location = ''; $curl = curl_init($url);
        curl_setopt_array($curl, [CURLOPT_CONNECTTIMEOUT => 10, CURLOPT_TIMEOUT => 30, CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS, CURLOPT_USERAGENT => 'BYgram-Plugin-Moderation',
            CURLOPT_HTTPHEADER => ['Accept: ' . ($asset ? 'application/octet-stream' : 'application/vnd.github+json'), 'X-GitHub-Api-Version: 2022-11-28'],
            CURLOPT_WRITEFUNCTION => static function ($handle, string $chunk) use (&$data, $limit): int {
                if (strlen($data) + strlen($chunk) > $limit) return 0;
                $data .= $chunk; return strlen($chunk);
            },
            CURLOPT_HEADERFUNCTION => static function ($handle, string $line) use (&$location): int {
                if (stripos($line, 'Location:') === 0) $location = trim(substr($line, 9));
                return strlen($line);
            }]);
        $ok = curl_exec($curl); $code = curl_getinfo($curl, CURLINFO_RESPONSE_CODE); curl_close($curl);
        if ($ok === false) throw new RuntimeException('Не удалось загрузить GitHub Release: timeout или превышен размер');
        if ($code === 200) return $data;
        if ($asset && in_array($code, [301,302,307,308], true) && $location !== '') { $url = $location; continue; }
        throw new RuntimeException('GitHub Release недоступен (HTTP ' . $code . ')');
    }
    throw new RuntimeException('Слишком много перенаправлений GitHub');
}
function pluginReleaseCandidate(string $repository, array $repo, array $release, string $bytes): array {
    if (($repo['private'] ?? true) !== false || !is_int($repo['id'] ?? null) || ($release['draft'] ?? true) !== false
            || ($release['prerelease'] ?? true) !== false || !is_int($release['id'] ?? null)) throw new InvalidArgumentException('Нужен публичный стабильный Release');
    $tag = (string) ($release['tag_name'] ?? '');
    if (!preg_match('/^v?((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))$/D', $tag, $version)) throw new InvalidArgumentException('Тег Release должен быть v1.2.3 или 1.2.3');
    $assets = array_values(array_filter($release['assets'] ?? [], static fn($asset) => str_ends_with((string) ($asset['name'] ?? ''), '.byplugin')));
    if (count($assets) !== 1 || !is_int($assets[0]['id'] ?? null) || ($assets[0]['size'] ?? 0) !== strlen($bytes)
            || strlen($bytes) === 0 || strlen($bytes) > 10 * 1024 * 1024) throw new InvalidArgumentException('Release должен содержать один .byplugin размером до 10 MiB');
    $hash = hash('sha256', $bytes);
    if (!empty($assets[0]['digest']) && !hash_equals('sha256:' . $hash, $assets[0]['digest'])) throw new InvalidArgumentException('SHA-256 GitHub asset не совпал');
    $file = tempnam(sys_get_temp_dir(), 'bygram-plugin-');
    try {
        if (file_put_contents($file, $bytes) !== strlen($bytes)) throw new RuntimeException('Не удалось сохранить пакет');
        $zip = new ZipArchive();
        if ($zip->open($file, ZipArchive::CHECKCONS) !== true) throw new InvalidArgumentException('Повреждённый ZIP');
        try {
            if ($zip->numFiles > 512) throw new InvalidArgumentException('Слишком много файлов');
            $names = []; $pathCases = []; $total = 0;
            for ($i = 0; $i < $zip->numFiles; $i++) {
                $stat = $zip->statIndex($i); $name = (string) $stat['name']; $path = rtrim($name, '/');
                $opsys = 0; $attributes = 0; $zip->getExternalAttributesIndex($i, $opsys, $attributes);
                if ($path === '' || strlen($path) > 512 || str_contains($path, '\\') || str_contains($path, ':') || str_starts_with($path, '/')
                        || count(explode('/', $path)) > 12 || preg_match('~(^|/)(\.|\.\.|)(/|$)~', $path)
                        || isset($names[strtolower($path)]) || (($attributes >> 16) & 0170000) === 0120000
                        || ($stat['encryption_method'] ?? 0) !== 0 || $stat['size'] > 5 * 1024 * 1024
                        || preg_match('/[\x00-\x1f\x7f-\x9f]/u', $path)
                        || preg_match('/\.(so|pyd|dex|jar|class|pyc|pyo|dll|exe|apk|aar)$/iD', $path)) throw new InvalidArgumentException('Недопустимый путь или файл в пакете');
                $prefix = '';
                foreach (explode('/', $path) as $part) {
                    $prefix .= ($prefix === '' ? '' : '/') . $part; $folded = strtolower($prefix);
                    if (isset($pathCases[$folded]) && $pathCases[$folded] !== $prefix) throw new InvalidArgumentException('Регистр путей в пакете конфликтует');
                    $pathCases[$folded] = $prefix;
                }
                $names[strtolower($path)] = true; $total += $stat['size'];
                if ($total > 30 * 1024 * 1024) throw new InvalidArgumentException('Слишком большой распакованный пакет');
                $contents = $zip->getFromIndex($i);
                if ($contents === false || strlen($contents) !== $stat['size'] || sprintf('%u', crc32($contents)) !== sprintf('%u', $stat['crc'])) throw new InvalidArgumentException('CRC пакета не совпал');
                if (str_ends_with($path, '.py') && !mb_check_encoding($contents, 'UTF-8')) throw new InvalidArgumentException('Python должен быть UTF-8');
            }
            $manifestJson = $zip->getFromName('manifest.json');
            if ($manifestJson === false || strlen($manifestJson) > 65536) throw new InvalidArgumentException('Нет manifest.json');
            $manifest = pluginManifest($manifestJson);
            if (!is_array($manifest) || !is_string($manifest['id'] ?? null) || strlen($manifest['id']) > 120
                    || !preg_match('/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/D', $manifest['id'])
                    || ($manifest['version'] ?? '') !== $version[1]) throw new InvalidArgumentException('ID некорректен или version в manifest не совпадает с тегом Release');
            $entry = explode(':', (string) ($manifest['entrypoint'] ?? ''));
            if (count($entry) !== 2 || $zip->locateName($entry[0]) === false) throw new InvalidArgumentException('Нет entrypoint');
            if (isset($manifest['icon'])) {
                $icon = $zip->getFromName($manifest['icon']);
                $image = $icon === false ? false : @getimagesizefromstring($icon);
                if ($icon === false || strlen($icon) > 256 * 1024 || !$image || $image[0] < 1 || $image[1] < 1 || $image[0] > 256 || $image[1] > 256
                        || !in_array($image['mime'], ['image/png','image/webp'], true)) throw new InvalidArgumentException('Недопустимая иконка');
            }
            $authorKey = pluginAuthorSignature($zip);
            if ($authorKey === null) throw new InvalidArgumentException('Для публикации нужен пакет с подписью автора');
        } finally { $zip->close(); }
    } finally { @unlink($file); }
    return ['manifest' => $manifest, 'sha256' => $hash, 'size' => strlen($bytes), 'file' => $hash . '.byplugin', 'author_key' => $authorKey,
        'release' => ['repository' => $repository, 'repository_id' => $repo['id'], 'tag' => $tag,
            'release_id' => $release['id'], 'asset_id' => $assets[0]['id']]];
}
function pluginJson(string $json): array {
    if (strlen($json) > 65536) throw new InvalidArgumentException('Слишком большой manifest');
    $manifest = json_decode($json, true, 8, JSON_THROW_ON_ERROR);
    // json_decode accepts repeated keys; reject them before presenting a candidate for review.
    $stack = [];
    for ($i = 0, $length = strlen($json); $i < $length; $i++) {
        $char = $json[$i];
        if ($char === '{') $stack[] = ['object' => true, 'key' => true, 'keys' => []];
        elseif ($char === '[') $stack[] = ['object' => false];
        elseif ($char === '}' || $char === ']') array_pop($stack);
        elseif ($char === ',' && $stack && $stack[count($stack)-1]['object']) $stack[count($stack)-1]['key'] = true;
        elseif ($char === '"') {
            $start = $i++;
            while ($i < $length && $json[$i] !== '"') { if ($json[$i] === '\\') $i++; $i++; }
            $top = count($stack)-1;
            if ($top >= 0 && $stack[$top]['object'] && $stack[$top]['key']) {
                $key = json_decode(substr($json, $start, $i-$start+1), true, 2, JSON_THROW_ON_ERROR);
                if (isset($stack[$top]['keys'][$key])) throw new InvalidArgumentException('Повторяющийся ключ manifest');
                $stack[$top]['keys'][$key] = true; $stack[$top]['key'] = false;
            }
        }
    }
    if (!is_array($manifest) || array_is_list($manifest)) throw new InvalidArgumentException('Нужен JSON object');
    return $manifest;
}
function pluginManifest(string $json): array {
    $manifest = pluginJson($json);
    $required = ['manifest_version','id','name','description','version','author','entrypoint','sdk','python','capabilities','account_scope','settings_schema'];
    $allowed = array_merge($required, ['icon','bygram','telegram','requires_hook_backend','hook_targets']);
    if (!is_array($manifest) || array_diff($required, array_keys($manifest)) || array_diff(array_keys($manifest), $allowed)
            || $manifest['manifest_version'] !== 1 || $manifest['python'] !== '3.11') throw new InvalidArgumentException('Неверная схема manifest');
    foreach (['id'=>120,'name'=>80,'description'=>1000,'version'=>80,'author'=>120,'entrypoint'=>512] as $key => $limit) {
        if (!is_string($manifest[$key]) || mb_strlen($manifest[$key]) > $limit || preg_match('/[\x00-\x1f\x7f-\x9f]/u', $manifest[$key])) throw new InvalidArgumentException('Недопустимое поле manifest: ' . $key);
    }
    if (trim($manifest['name']) === '' || !preg_match('/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/D', $manifest['id'])
            || !preg_match('/^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/D', $manifest['version'])
            || !preg_match('~^(?!/)(?!.*(?:^|/)\.{1,2}/)(?!.*//)[^\\\\:]+\.py:[A-Za-z_][A-Za-z0-9_]*$~D', $manifest['entrypoint'])
            || !in_array($manifest['account_scope'], ['global','selected_accounts'], true)
            || !is_int($manifest['settings_schema']) || $manifest['settings_schema'] < 1 || $manifest['settings_schema'] > 2147483647) throw new InvalidArgumentException('Неверные ID/version/entrypoint/scope/schema');
    [$entryPath, $entryClass] = explode(':', $manifest['entrypoint']);
    pluginManifestPath($entryPath);
    if (in_array($entryClass, ['False','None','True','and','as','assert','async','await','break','class','continue',
            'def','del','elif','else','except','finally','for','from','global','if','import','in','is','lambda',
            'nonlocal','not','or','pass','raise','return','try','while','with','yield'], true)) {
        throw new InvalidArgumentException('Имя класса не может быть ключевым словом Python');
    }
    foreach (['sdk'=>['min','max'],'bygram'=>['min_build','max_build'],'telegram'=>['min_build','max_build']] as $key => [$min, $max]) {
        if (!array_key_exists($key, $manifest)) { if ($key === 'sdk') throw new InvalidArgumentException('Нужен диапазон SDK'); continue; }
        $range = $manifest[$key];
        if (!is_array($range) || count($range) !== 2 || !is_int($range[$min] ?? null) || !is_int($range[$max] ?? null)
                || $range[$min] < 1 || $range[$max] > 2147483647 || $range[$min] > $range[$max]) throw new InvalidArgumentException('Неверный диапазон manifest');
    }
    $caps = $manifest['capabilities'];
    if (!is_array($caps) || !array_is_list($caps)) throw new InvalidArgumentException('Неверные capabilities');
    foreach ($caps as $cap) if (!is_string($cap) || !in_array($cap, ['ui','theme','messages.observe','messages.send','network','hooks.raw'], true)) throw new InvalidArgumentException('Неизвестная capability');
    if (count($caps) !== count(array_unique($caps))) throw new InvalidArgumentException('Повтор capabilities');
    if (array_key_exists('requires_hook_backend', $manifest) && !is_bool($manifest['requires_hook_backend'])) throw new InvalidArgumentException('Неверный requires_hook_backend');
    if (in_array('hooks.raw', $caps, true)) {
        if (($manifest['requires_hook_backend'] ?? false) !== true || !isset($manifest['bygram'], $manifest['telegram'], $manifest['hook_targets'])
                || !is_array($manifest['hook_targets']) || !array_is_list($manifest['hook_targets']) || !$manifest['hook_targets']) throw new InvalidArgumentException('Нужны декларации hooks');
        foreach ($manifest['hook_targets'] as $target) {
            if (!is_array($target) || array_diff(array_keys($target), ['class','method','constructor','args']) || !is_string($target['class'] ?? null)
                    || strlen($target['class']) > 512 || !preg_match('/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/D', $target['class'])
                    || array_key_exists('method', $target) === array_key_exists('constructor', $target) || !is_array($target['args'] ?? null) || !array_is_list($target['args'])) throw new InvalidArgumentException('Неверный hook target');
            if (array_key_exists('constructor', $target) && $target['constructor'] !== true || array_key_exists('method', $target) && (!is_string($target['method']) || strlen($target['method']) > 256 || !preg_match('/^[A-Za-z_$][A-Za-z0-9_$]*$/D', $target['method']))) throw new InvalidArgumentException('Неверный hook method');
            foreach ($target['args'] as $arg) if (!is_string($arg) || strlen($arg) > 512 || !preg_match('/^(?!void(?:\[\])*$)[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*(\[\])*$/D', $arg)) throw new InvalidArgumentException('Неверный hook argument');
        }
    } elseif (array_key_exists('hook_targets', $manifest)) throw new InvalidArgumentException('hooks.raw не объявлен');
    if (array_key_exists('icon', $manifest)) {
        if (!is_string($manifest['icon']) || !preg_match('~\.(png|webp)$~D', $manifest['icon'])) throw new InvalidArgumentException('Неверный путь icon');
        pluginManifestPath($manifest['icon']);
    }
    return $manifest;
}
function pluginManifestPath(string $path): void {
    $parts = explode('/', $path);
    if (count($parts) > 12 || str_contains($path, '\\') || str_contains($path, ':')
            || preg_match('/[\x00-\x1f\x7f-\x9f]/u', $path)) throw new InvalidArgumentException('Неверный путь manifest');
    foreach ($parts as $part) if (in_array($part, ['', '.', '..'], true)) throw new InvalidArgumentException('Неверный путь manifest');
}
function pluginAuthorSignature(ZipArchive $zip): ?string {
    $json = $zip->getFromName('signature.json');
    if ($json === false) return null;
    $envelope = pluginJson($json);
    if (count($envelope) !== 3 || ($envelope['format'] ?? null) !== 1 || !is_string($envelope['public_key'] ?? null)
            || !is_string($envelope['signature'] ?? null)) throw new InvalidArgumentException('Неверный формат подписи автора');
    $der = base64_decode($envelope['public_key'], true); $signature = base64_decode($envelope['signature'], true);
    if ($der === false || $signature === false) throw new InvalidArgumentException('Неверная подпись автора');
    $key = openssl_pkey_get_public("-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode($der), 64, "\n") . "-----END PUBLIC KEY-----\n");
    $details = $key === false ? false : openssl_pkey_get_details($key);
    if (!$details || $details['type'] !== OPENSSL_KEYTYPE_RSA || $details['bits'] < 2048 || $details['bits'] > 4096) throw new InvalidArgumentException('Нужен RSA-ключ 2048–4096 бит');
    if (base64_decode(preg_replace('/-----[^-]+-----|\s+/', '', $details['key']), true) !== $der) throw new InvalidArgumentException('Нужен канонический DER public key');
    $files = [];
    for ($i = 0; $i < $zip->numFiles; $i++) {
        $name = $zip->getNameIndex($i);
        if ($name === 'signature.json' || str_ends_with($name, '/')) continue;
        $bytes = $zip->getFromIndex($i);
        if ($bytes === false) throw new InvalidArgumentException('Не удалось проверить подпись');
        $files[$name] = hash('sha256', $bytes) . ' ' . strlen($bytes) . ' ' . $name . "\n";
    }
    ksort($files, SORT_STRING);
    if (openssl_verify("BYGRAM-PLUGIN-SIGNATURE-V1\n" . implode('', $files), $signature, $key, OPENSSL_ALGO_SHA256) !== 1) throw new InvalidArgumentException('Подпись содержимого пакета недействительна');
    return hash('sha256', $der);
}
function pluginCandidateDirectory(): string {
    $directory = __DIR__ . '/plugin-candidates';
    if (!is_dir($directory) && !mkdir($directory, 0700, true)) throw new RuntimeException('Не удалось создать staging');
    return $directory;
}
function preparePluginReview(PDO $pdo, int $id): void {
    $query = $pdo->prepare('SELECT * FROM plugin_submissions WHERE id=?'); $query->execute([$id]); $submission = $query->fetch(PDO::FETCH_ASSOC);
    if (!$submission || !in_array($submission['status'], ['pending', 'review'], true)) throw new InvalidArgumentException('Заявка недоступна');
    $repository = pluginRepository($submission['repository']); $slug = substr($repository, strlen('https://github.com/'));
    $repo = json_decode(pluginDownload('https://api.github.com/repos/' . $slug, 1024 * 1024), true, 32, JSON_THROW_ON_ERROR);
    if (strtolower((string) ($repo['full_name'] ?? '')) !== $slug || ($repo['private'] ?? true) !== false) throw new InvalidArgumentException('Нужен публичный репозиторий');
    $release = json_decode(pluginDownload('https://api.github.com/repos/' . $slug . '/releases/latest', 1024 * 1024), true, 32, JSON_THROW_ON_ERROR);
    $assets = array_values(array_filter($release['assets'] ?? [], static fn($asset) => str_ends_with((string) ($asset['name'] ?? ''), '.byplugin')));
    if (count($assets) !== 1 || !is_int($assets[0]['id'] ?? null) || ($assets[0]['size'] ?? 0) > 10 * 1024 * 1024) throw new InvalidArgumentException('Добавьте один .byplugin к стабильному Release');
    $bytes = pluginDownload('https://api.github.com/repos/' . $slug . '/releases/assets/' . $assets[0]['id'], 10 * 1024 * 1024, true);
    $candidate = pluginReleaseCandidate($repository, $repo, $release, $bytes);
    $path = pluginCandidateDirectory() . '/' . $candidate['file'];
    if (file_put_contents($path . '.tmp', $bytes, LOCK_EX) !== strlen($bytes) || !rename($path . '.tmp', $path)) throw new RuntimeException('Не удалось сохранить кандидата');
    $pdo->prepare("UPDATE plugin_submissions SET status='review',release_tag=?,candidate=? WHERE id=? AND status IN ('pending','review')")
        ->execute([$candidate['release']['tag'], json_encode($candidate, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES), $id]);
}
function approvePlugin(PDO $pdo, int $id, int $admin, string $expectedHash): void {
    $directory = dirname(__DIR__) . '/plugins';
    if (!is_dir($directory) && !mkdir($directory, 0755, true)) throw new RuntimeException('Каталог недоступен');
    $lock = fopen($directory . '/.publish.lock', 'c');
    if (!$lock || !flock($lock, LOCK_EX)) throw new RuntimeException('Каталог занят');
    try {
        $query = $pdo->prepare("SELECT * FROM plugin_submissions WHERE id=? AND status='review'"); $query->execute([$id]); $submission = $query->fetch(PDO::FETCH_ASSOC);
        if (!$submission) throw new InvalidArgumentException('Заявка не подготовлена к модерации');
        $candidate = json_decode($submission['candidate'], true, 16, JSON_THROW_ON_ERROR);
        if (!preg_match('/^[a-f0-9]{64}$/D', $expectedHash) || !hash_equals($candidate['sha256'], $expectedHash)) throw new InvalidArgumentException('Кандидат изменился: откройте заявку заново');
        $bytes = file_get_contents(pluginCandidateDirectory() . '/' . $candidate['file']);
        if ($bytes === false || !hash_equals($expectedHash, hash('sha256', $bytes)) || strlen($bytes) !== $candidate['size']) throw new RuntimeException('Кандидат повреждён');
        $indexFile = $directory . '/index.json';
        $index = is_file($indexFile) ? json_decode((string) file_get_contents($indexFile), true, 32, JSON_THROW_ON_ERROR) : ['format' => 1, 'plugins' => []];
        if (($index['format'] ?? null) !== 1 || !is_array($index['plugins'] ?? null)) throw new RuntimeException('Повреждён индекс каталога');
        foreach ($index['plugins'] as $position => $entry) if (($entry['manifest']['id'] ?? '') === $candidate['manifest']['id']) {
            if (($entry['release']['repository_id'] ?? null) !== $candidate['release']['repository_id']) throw new InvalidArgumentException('Этот ID принадлежит другому репозиторию');
            if (isset($entry['author_key']) && ($candidate['author_key'] ?? null) !== $entry['author_key']) throw new InvalidArgumentException('Ключ автора изменился');
            if ($entry == $candidate) {
                $pdo->prepare("UPDATE plugin_submissions SET status='approved',reviewed_by=?,reviewed_at=UTC_TIMESTAMP(),review_note='' WHERE id=?")->execute([$admin, $id]);
                return;
            }
            if (version_compare($candidate['manifest']['version'], $entry['manifest']['version'], '<=')) throw new InvalidArgumentException('Нужна новая версия Release');
            unset($index['plugins'][$position]);
        }
        $index['plugins'][] = $candidate; $index['plugins'] = array_values($index['plugins']);
        foreach ($index['plugins'] as $entry) pluginManifest(json_encode($entry['manifest'], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE));
        $json = json_encode($index, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
        if (count($index['plugins']) > 100 || strlen($json) > 1024 * 1024) throw new RuntimeException('Каталог заполнен');
        $package = $directory . '/' . $candidate['file'];
        if (file_put_contents($package . '.tmp', $bytes, LOCK_EX) !== strlen($bytes) || !rename($package . '.tmp', $package)) throw new RuntimeException('Не удалось опубликовать пакет');
        if (file_put_contents($indexFile . '.tmp', $json, LOCK_EX) !== strlen($json) || !rename($indexFile . '.tmp', $indexFile)) throw new RuntimeException('Не удалось опубликовать индекс');
        $pdo->prepare("UPDATE plugin_submissions SET status='approved',reviewed_by=?,reviewed_at=UTC_TIMESTAMP(),review_note='' WHERE id=?") ->execute([$admin, $id]);
    } finally { flock($lock, LOCK_UN); fclose($lock); }
}
