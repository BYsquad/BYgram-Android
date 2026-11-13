<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli' || getenv('BYGRAM_TEST_LOCAL') !== '1') exit(1);
$database = 'bygram_plugins_test_' . getmypid();
$admin = new PDO('mysql:host=' . (getenv('BYGRAM_TEST_DB_HOST') ?: 'MySQL') . ';port=3306;charset=utf8mb4', getenv('BYGRAM_TEST_DB_USER') ?: 'root', getenv('BYGRAM_TEST_DB_PASSWORD') ?: 'root', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$admin->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
register_shutdown_function(static fn() => $admin->exec("DROP DATABASE IF EXISTS `$database`"));
$pdo = new PDO('mysql:host=' . (getenv('BYGRAM_TEST_DB_HOST') ?: 'MySQL') . ';port=3306;dbname=' . $database, getenv('BYGRAM_TEST_DB_USER') ?: 'root', getenv('BYGRAM_TEST_DB_PASSWORD') ?: 'root', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$pdo->exec('CREATE TABLE users (telegram_id BIGINT PRIMARY KEY)'); $pdo->exec('INSERT INTO users VALUES (123),(456)');
require __DIR__ . '/migrations/20261101_plugins.php'; require __DIR__ . '/migrations/20261101_plugins.php';
$root = sys_get_temp_dir() . '/bygram-plugin-test-' . bin2hex(random_bytes(8)); mkdir($root); mkdir($root . '/api');
copy(__DIR__ . '/plugin-catalog.php', $root . '/api/plugin-catalog.php'); require $root . '/api/plugin-catalog.php';
try {
    $id = submitPlugin($pdo, 123, 'https://github.com/Example/Plugin');
    assert(count(pluginSubmissions($pdo, 123)) === 1 && pluginSubmissions($pdo, 456) === []);
    foreach (['http://github.com/a/b', 'https://evil.test/a/b', 'https://github.com/a/..', 'https://github.com/a/b.git', 'https://github.com/a/b?x=1'] as $invalid) {
        try { pluginRepository($invalid); throw new RuntimeException('bad repository accepted'); } catch (InvalidArgumentException) { }
    }
    try { submitPlugin($pdo, 123, 'https://github.com/example/plugin'); throw new RuntimeException('duplicate accepted'); } catch (InvalidArgumentException) { }
    $manifest = json_decode((string) file_get_contents(dirname(__DIR__, 2) . '/docs/plugins/examples/hello/manifest.json'), true, 16, JSON_THROW_ON_ERROR);
    $manifest['id'] = 'test.moderated.plugin';
    foreach (['main.py:class', 'main.py:None', str_repeat('a/', 12) . 'main.py:Plugin'] as $entry) {
        $invalid = $manifest; $invalid['entrypoint'] = $entry;
        try { pluginManifest(json_encode($invalid, JSON_THROW_ON_ERROR)); throw new RuntimeException('bad entrypoint accepted'); }
        catch (InvalidArgumentException) { }
    }
    foreach (['icon', 'bygram', 'telegram', 'requires_hook_backend', 'hook_targets'] as $key) {
        $invalid = $manifest; $invalid[$key] = null;
        try { pluginManifest(json_encode($invalid, JSON_THROW_ON_ERROR)); throw new RuntimeException('optional null field accepted'); }
        catch (InvalidArgumentException) { }
    }
    $zip = new ZipArchive(); $zip->open($root . '/test.zip', ZipArchive::CREATE);
    $zip->addFromString('manifest.json', json_encode($manifest)); $zip->addFromString('main.py', "raise RuntimeError('must never run on server')\n"); $zip->close();
    $author = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
    if ($author === false) throw new RuntimeException('RSA test key generation failed; configure OPENSSL_CONF');
    $zip->open($root . '/test.zip');
    $payload = "BYGRAM-PLUGIN-SIGNATURE-V1\n";
    foreach (['main.py','manifest.json'] as $name) { $contents = $zip->getFromName($name); $payload .= hash('sha256', $contents) . ' ' . strlen($contents) . ' ' . $name . "\n"; }
    openssl_sign($payload, $signature, $author, OPENSSL_ALGO_SHA256);
    $publicPem = openssl_pkey_get_details($author)['key'];
    $publicDer = base64_decode(preg_replace('/-----[^-]+-----|\s+/', '', $publicPem), true);
    $zip->addFromString('signature.json', json_encode(['format'=>1,'public_key'=>base64_encode($publicDer),'signature'=>base64_encode($signature)])); $zip->close();
    $bytes = (string) file_get_contents($root . '/test.zip');
    $repo = ['private' => false, 'id' => 321];
    $release = ['draft' => false, 'prerelease' => false, 'id' => 1234, 'tag_name' => 'v' . $manifest['version'],
        'assets' => [['id' => 987, 'name' => 'hello.byplugin', 'size' => strlen($bytes), 'digest' => 'sha256:' . hash('sha256', $bytes)]]];
    $candidate = pluginReleaseCandidate('https://github.com/example/plugin', $repo, $release, $bytes);
    foreach (['{"manifest_version":1,"manifest_version":1}', str_replace('"sdk":', '"unknown":', json_encode($manifest)), str_replace('"sdk":{', '"sdk":null,"discard":{', json_encode($manifest))] as $invalid) {
        try { pluginManifest($invalid); throw new RuntimeException('bad manifest accepted'); } catch (InvalidArgumentException | JsonException) { }
    }
    foreach (['hello','theme','events','accounts','hooks','composer'] as $example) pluginManifest((string) file_get_contents(dirname(__DIR__, 2) . '/docs/plugins/examples/' . $example . '/manifest.json'));
    foreach (['private', 'draft', 'prerelease', 'tag', 'size', 'hash'] as $bad) {
        $r = $release; $p = $repo;
        if ($bad === 'private') $p['private'] = true;
        elseif ($bad === 'tag') $r['tag_name'] = 'v999.0.0';
        elseif ($bad === 'size') $r['assets'][0]['size']++;
        elseif ($bad === 'hash') $r['assets'][0]['digest'] = 'sha256:' . str_repeat('0', 64);
        else $r[$bad] = true;
        try { pluginReleaseCandidate('https://github.com/example/plugin', $p, $r, $bytes); throw new RuntimeException('bad release accepted: ' . $bad); } catch (InvalidArgumentException) { }
    }
    file_put_contents(pluginCandidateDirectory() . '/' . $candidate['file'], $bytes);
    $pdo->prepare("UPDATE plugin_submissions SET status='review',candidate=?,release_tag=? WHERE id=?")->execute([json_encode($candidate), $release['tag_name'], $id]);
    try { approvePlugin($pdo, $id, 456, str_repeat('0', 64)); throw new RuntimeException('changed review accepted'); } catch (InvalidArgumentException) { }
    approvePlugin($pdo, $id, 456, $candidate['sha256']);
    $index = json_decode((string) file_get_contents($root . '/plugins/index.json'), true);
    assert($index['plugins'][0]['sha256'] === hash('sha256', $bytes));
    assert(pluginSubmissions($pdo, 123)[0]['status'] === 'approved');
    assert(file_get_contents($root . '/plugins/' . $candidate['file']) === $bytes);
    $pdo->prepare("UPDATE plugin_submissions SET status='review' WHERE id=?")->execute([$id]);
    approvePlugin($pdo, $id, 456, $candidate['sha256']);
    assert(pluginSubmissions($pdo, 123)[0]['status'] === 'approved');
    $next = submitPlugin($pdo, 123, 'https://github.com/example/plugin');
    $candidate['release']['repository_id'] = 999;
    $pdo->prepare("UPDATE plugin_submissions SET status='review',candidate=? WHERE id=?")->execute([json_encode($candidate), $next]);
    try { approvePlugin($pdo, $next, 456, $candidate['sha256']); throw new RuntimeException('plugin ID takeover accepted'); } catch (InvalidArgumentException) { }
    try { revokePlugin($pdo, $id, 456, str_repeat('0', 64), 'wrong hash'); throw new RuntimeException('wrong hash revoked'); } catch (InvalidArgumentException) { }
    revokePlugin($pdo, $id, 456, $candidate['sha256'], 'fixture revocation');
    assert(pluginRevokedHashes($root . '/plugins') === [$candidate['sha256']]);
    assert(json_decode((string) file_get_contents($root . '/plugins/index.json'), true)['plugins'] === []);
    assert(!is_file($root . '/plugins/' . $candidate['file']));
    assert(pluginSubmissions($pdo, 123)[1]['status'] === 'revoked');
    try { approvePlugin($pdo, $next, 456, $candidate['sha256']); throw new RuntimeException('revoked hash republished'); }
    catch (InvalidArgumentException $error) { assert(str_contains($error->getMessage(), 'отозван')); }
    $changedBytes = $bytes . 'new fixture';
    $candidate['sha256'] = hash('sha256', $changedBytes);
    $candidate['file'] = $candidate['sha256'] . '.byplugin';
    $candidate['size'] = strlen($changedBytes);
    file_put_contents(pluginCandidateDirectory() . '/' . $candidate['file'], $changedBytes);
    $pdo->prepare("UPDATE plugin_submissions SET candidate=? WHERE id=?")->execute([json_encode($candidate), $next]);
    try { approvePlugin($pdo, $next, 456, $candidate['sha256']); throw new RuntimeException('revocation released plugin ID'); }
    catch (InvalidArgumentException $error) { assert(str_contains($error->getMessage(), 'другому репозиторию')); }
    echo "PASS: MySQL migration twice, signed-user ownership boundary, duplicates, GitHub/tag/hash/size validation, exact-candidate publication and repository ID takeover rejection, exact-hash revocation and blocked republication\n";
} finally {
    $files = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS), RecursiveIteratorIterator::CHILD_FIRST);
    foreach ($files as $file) { if ($file->isDir()) rmdir($file->getPathname()); else unlink($file->getPathname()); }
    rmdir($root);
}
