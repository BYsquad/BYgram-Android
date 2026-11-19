<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli' || getenv('BYGRAM_TEST_LOCAL') !== '1') exit(1);
$database = 'bygram_plugins_test_' . getmypid();
$testDsn = getenv('BYGRAM_TEST_DB_DSN') ?: 'mysql:host=' . (getenv('BYGRAM_TEST_DB_HOST') ?: 'MySQL') . ';port=3306;charset=utf8mb4';
$admin = new PDO($testDsn, getenv('BYGRAM_TEST_DB_USER') ?: 'root', getenv('BYGRAM_TEST_DB_PASSWORD') ?: 'root', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$admin->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
register_shutdown_function(static fn() => $admin->exec("DROP DATABASE IF EXISTS `$database`"));
$pdo = new PDO($testDsn . ';dbname=' . $database, getenv('BYGRAM_TEST_DB_USER') ?: 'root', getenv('BYGRAM_TEST_DB_PASSWORD') ?: 'root', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
if ((int) ini_get('zend.assertions') !== 1) throw new RuntimeException('Run with -d zend.assertions=1');
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
    require __DIR__ . '/migrations/20261118_plugin_store.php';
    require __DIR__ . '/migrations/20261118_plugin_store.php';
    require __DIR__ . '/plugin-store.php';
    require __DIR__ . '/plugin-store-providers.php';
    require __DIR__ . '/plugin-store-wallet.php';
    $pdo->exec('CREATE TABLE api_rate_limits (rate_key VARCHAR(191) PRIMARY KEY, window_started DATETIME NOT NULL, request_count INT NOT NULL)');
    require __DIR__ . '/migrations/20261118_plugin_store_author_wallets.php';
    require __DIR__ . '/migrations/20261118_plugin_store_author_wallets.php';
    $pdo->exec('INSERT INTO users VALUES (789),(987)');
    require __DIR__ . '/migrations/20261119_plugin_store_author_bots.php';
    require __DIR__ . '/migrations/20261119_plugin_store_author_bots.php';
    $paid = storeSubmit($pdo, 123, $bytes, 120, 400000000);
    $hash = $paid['sha256'];
    $config = [
        'plugin_store_enabled'=>true, 'plugin_store_stars_token'=>'fake', 'plugin_store_stars_secret'=>'fixture',
        'plugin_store_url'=>'https://checkout.example.invalid',
    ];
    $methods = storeMethods($config);
    assert($methods === ['stars','ton']);
    assert(storeMethods(['plugin_store_sales'=>false] + $config) === []);
    assert(storeMethods(['plugin_store_ton_test'=>false, 'plugin_store_stars_test'=>false] + $config) === []);
    assert(storeCatalog($pdo, $methods, '-3') === []);
    assert(storeDownload($pdo, 456, $hash) === null);
    storeReview($pdo, $hash, 789, 'approve', '');
    assert(storeCatalog($pdo, $methods, '-3')[0]['purchase']['nano'] === '0');
    try {
        storeCreateOrder($pdo,789,$hash,'ton',str_repeat('c',32),PLUGIN_STORE_TERMS,$config);
        throw new RuntimeException('TON offered before wallet verification');
    } catch (InvalidArgumentException) { }
    $authorConfig = ['bot_token'=>'fixture-encryption', 'plugin_store_stars_token'=>'', 'plugin_store_stars_secret'=>''] + $config;
    $authorBot = 'stars:test:12345';
    $token = '12345:fixture';
    $encrypted = storeEncryptStarsToken($authorConfig, $authorBot, $token);
    assert($encrypted !== $token);
    assert(storeCatalog($pdo, storeMethods($authorConfig), '-3', $authorConfig)[0]['purchase']['stars'] === 0);
    storeWrite($pdo, 'INSERT INTO plugin_store_author_bots VALUES (?,?,?,?,?,1)', [$authorBot,123,$encrypted,str_repeat('s',64),'TestBot']);
    assert(storeCatalog($pdo, storeMethods($authorConfig), '-3', $authorConfig)[0]['purchase']['stars'] === 120);
    $directStars = storeCreateOrder($pdo,987,$hash,'stars',str_repeat('d',32),PLUGIN_STORE_TERMS,$authorConfig);
    assert($directStars['merchant'] === $authorBot && (int) $directStars['author_bps'] === 0);
    storeWrite($pdo, 'UPDATE plugin_store_author_bots SET active=0 WHERE merchant=?', [$authorBot]);
    assert(storeCatalog($pdo, storeMethods($authorConfig), '-3', $authorConfig)[0]['purchase']['stars'] === 0);
    assert(storeStarsMerchantConfig($pdo,$authorConfig,$directStars['merchant'])['plugin_store_stars_token'] === $token);
    storeWrite($pdo, 'DELETE FROM plugin_store_orders WHERE id=?', [$directStars['id']]);
    echo "PASS: direct Stars appear only for active author bots; existing orders retain decryptable merchant after disable\n";
    $destination = '0:' . str_repeat('a',64);
    $challenge = str_repeat('b',64);
    $domain = 'checkout.example.invalid';
    $now = time();
    $keypair = sodium_crypto_sign_keypair();
    $message = 'ton-proof-item-v2/' . hex2bin(str_repeat('0',8) . str_repeat('a',64))
        . pack('V',strlen($domain)) . $domain . pack('V2',$now,0) . $challenge;
    $proof = ['account'=>['address'=>$destination,'chain'=>'-3'],'proof'=>[
        'domain'=>['value'=>$domain,'lengthBytes'=>strlen($domain)],'timestamp'=>$now,'payload'=>$challenge,
        'signature'=>base64_encode(sodium_crypto_sign_detached(hash('sha256',"\xff\xffton-connect" . hash('sha256',$message,true),true),
            sodium_crypto_sign_secretkey($keypair))),
    ]];
    $resolveKey = static function ($network,$address) use ($keypair,$destination) {
        assert($network === '-3' && $address === $destination);
        return sodium_crypto_sign_publickey($keypair);
    };
    $verified = storeVerifyTonWallet($proof,$challenge,$domain,'-3',$resolveKey,$now);
    assert($verified === $destination);
    foreach ([['-239',$challenge,$domain,$now],['-3','wrong',$domain,$now],['-3',$challenge,'other.invalid',$now],
        ['-3',$challenge,$domain,$now+301]] as [$chain,$nonce,$host,$clock]) {
        try {
            storeVerifyTonWallet($proof,$nonce,$host,$chain,$resolveKey,$clock);
            throw new RuntimeException('wrong wallet challenge accepted');
        } catch (InvalidArgumentException) { }
    }
    try {
        storeVerifyTonWallet($proof,$challenge,$domain,'-3',static fn() => str_repeat('x',32),$now);
        throw new RuntimeException('foreign wallet key accepted');
    } catch (InvalidArgumentException) { }
    storeWrite($pdo,'INSERT INTO plugin_store_author_wallets VALUES (?,?,?,?)',[123,'-3',$verified,$now]);
    assert(storeCatalog($pdo, $methods, '-239')[0]['purchase']['nano'] === '0');
    $entry = storeCatalog($pdo, $methods, '-3')[0];
    assert($entry['sha256'] === $hash && $entry['purchase'] === ['stars'=>120,'nano'=>'400000000']);
    assert(!isset($entry['file']) && !isset($entry['archive']));
    assert(!is_file($root . '/plugins/' . $hash . '.byplugin'));
    $makeOrder = static fn($buyer,$method,$retry) => storeCreateOrder($pdo, $buyer, $hash, $method,
        str_repeat($retry, 32), PLUGIN_STORE_TERMS, $config);
    try { storeCreateOrder($pdo,456,$hash,'stars',str_repeat('e',32),'outdated',$config); throw new RuntimeException('bad terms accepted'); }
    catch (InvalidArgumentException) { }
    $stars = $makeOrder(456,'stars','a');
    assert($makeOrder(456,'stars','a')['id'] === $stars['id']);
    try { $makeOrder(456,'ton','b'); throw new RuntimeException('parallel checkout accepted'); } catch (InvalidArgumentException) { }
    $query = ['id'=>'fake-checkout', 'from'=>['id'=>456], 'currency'=>'XTR','total_amount'=>120,'invoice_payload'=>'bygram:' . $stars['id']];
    $sent = [];
    $bot = static function ($method, $data) use (&$sent): void { $sent[] = [$method,$data]; };
    $merchant = storeMerchant($config, 'stars');
    $starsUpdate = static fn($event) => storeStarsUpdate($pdo, $event, $bot, '', $merchant);
    assert($starsUpdate(['pre_checkout_query'=>['from'=>['id'=>789]] + $query]) === 'rejected');
    assert($starsUpdate(['pre_checkout_query'=>['total_amount'=>1] + $query]) === 'rejected');
    assert($starsUpdate(['pre_checkout_query'=>$query]) === 'accepted');
    assert($starsUpdate(['pre_checkout_query'=>$query]) === 'accepted');
    assert($sent[3] === ['answerPreCheckoutQuery', ['pre_checkout_query_id'=>'fake-checkout','ok'=>true]]);
    assert(storeDownload($pdo, 456, $hash) === null);
    $payment = ['currency'=>'XTR', 'total_amount'=>120, 'invoice_payload'=>$query['invoice_payload'], 'telegram_payment_charge_id'=>'fake-charge'];
    $event = ['message'=>['from'=>['id'=>456], 'date'=>time(), 'successful_payment'=>$payment]];
    assert(storeStarsUpdate($pdo, $event, $bot, '', 'stars:live:fake') === 'unknown');
    assert($starsUpdate($event) === 'paid');
    assert($starsUpdate($event) === 'paid');
    assert(storeDownload($pdo, 456, $hash) === $bytes && storeDownload($pdo, 789, $hash) === null);
    assert((int) $pdo->query("SELECT author_amount FROM plugin_store_payments WHERE charge='fake-charge'")->fetchColumn() === 96);
    assert(storeAuthorBalance($pdo,123,'stars',$merchant) === 96);
    storeRecordPayout($pdo,123,'stars',$merchant,46,'fixture-payout');
    storeRecordPayout($pdo,123,'stars',$merchant,46,'fixture-payout');
    assert(storeAuthorBalance($pdo,123,'stars',$merchant) === 50);
    $refund = ['message'=>['from'=>['id'=>456], 'refunded_payment'=>$payment]];
    assert($starsUpdate($refund) === 'refunded');
    assert($starsUpdate($event) === 'refunded' && storeDownload($pdo, 456, $hash) === null);
    assert(storeAuthorBalance($pdo,123,'stars',$merchant) === -46);
    $outOfOrder = $makeOrder(987,'stars','d');
    $early = ['invoice_payload'=>'bygram:' . $outOfOrder['id'], 'telegram_payment_charge_id'=>'early-refund'] + $payment;
    assert($starsUpdate(['message'=>['from'=>['id'=>987],'refunded_payment'=>$early]]) === 'refunded');
    assert($starsUpdate(['message'=>['from'=>['id'=>987],'successful_payment'=>$early]]) === 'refunded');
    $ton = $makeOrder(789,'ton','c');
    $newDestination = '0:' . str_repeat('d',64);
    storeWrite($pdo,'UPDATE plugin_store_author_wallets SET address=? WHERE author_id=? AND network=?',[$newDestination,123,'-3']);
    $newOrder = $makeOrder(987,'ton','f');
    assert(storeTonRecipient($ton['merchant']) === ['-3',$destination]);
    assert(storeTonRecipient($newOrder['merchant']) === ['-3',$newDestination]);
    assert($makeOrder(789,'ton','c')['merchant'] === $ton['merchant']);
    assert(str_ends_with(storeCheckoutUrl($pdo, $ton, $config), $ton['id']));
    $otherManifest = json_encode(['id'=>'test.second.author'] + $manifest, JSON_THROW_ON_ERROR);
    $otherFiles = ['main.py'=>"class Plugin: pass\n", 'manifest.json'=>$otherManifest];
    $signed = "BYGRAM-PLUGIN-SIGNATURE-V1\n";
    foreach ($otherFiles as $name=>$data) $signed .= hash('sha256',$data) . ' ' . strlen($data) . " $name\n";
    openssl_sign($signed,$otherSignature,$author,OPENSSL_ALGO_SHA256);
    $zip->open($root . '/second.zip', ZipArchive::CREATE);
    foreach ($otherFiles as $name=>$data) $zip->addFromString($name,$data);
    $zip->addFromString('signature.json',json_encode(['format'=>1,'public_key'=>base64_encode($publicDer),'signature'=>base64_encode($otherSignature)]));
    $zip->close();
    $otherRelease = storeSubmit($pdo,456,file_get_contents($root . '/second.zip'),0,200000000);
    $otherDestination = '0:' . str_repeat('e',64);
    storeWrite($pdo,'INSERT INTO plugin_store_author_wallets VALUES (?,?,?,?)',[456,'-3',$otherDestination,$now]);
    storeReview($pdo,$otherRelease['sha256'],123,'approve','');
    $otherOrder = storeCreateOrder($pdo,789,$otherRelease['sha256'],'ton',str_repeat('1',32),PLUGIN_STORE_TERMS,$config);
    assert(storeTonRecipient($otherOrder['merchant']) === ['-3',$otherDestination]);
    storeReview($pdo,$otherRelease['sha256'],123,'revoke','fixture revocation');
    $tx = ['account'=>$destination, 'mc_block_seqno'=>123, 'description'=>['aborted'=>false], 'now'=>time(), 'in_msg'=>[
        'destination'=>strtoupper($destination),'source'=>'0:' . str_repeat('B',64),'bounced'=>false,'opcode'=>'0x00000000',
        'value'=>'400000000','hash'=>base64_encode(str_repeat('c',32)),'message_content'=>[
            'body'=>storeTonPayload($ton['id']), 'hash'=>base64_encode(hash('sha256',storeTonCell($ton['id']),true)),
        ],
    ]];
    assert(storeTonTransaction($pdo, ['mc_block_seqno'=>0] + $tx, $destination, '-3') === 'ignored');
    assert(storeTonTransaction($pdo, ['description'=>['aborted'=>true]] + $tx, $destination, '-3') === 'ignored');
    assert(storeTonTransaction($pdo, ['in_msg'=>['bounced'=>true] + $tx['in_msg']] + $tx, $destination, '-3') === 'ignored');
    assert(storeTonTransaction($pdo, $tx, $destination, '-239') === 'ignored');
    $wrongAuthor = ['account'=>$newDestination,'in_msg'=>['destination'=>$newDestination] + $tx['in_msg']] + $tx;
    assert(storeTonTransaction($pdo, $wrongAuthor, $newDestination, '-3') === 'ignored');
    $noComment = ['opcode'=>null,'message_content'=>[],'hash'=>base64_encode(str_repeat('f',32))] + $tx['in_msg'];
    assert(storeTonTransaction($pdo, ['in_msg'=>$noComment] + $tx, $destination, '-3') === 'ignored');
    assert((int) $pdo->query("SELECT COUNT(*) FROM plugin_store_payments WHERE method='ton'")->fetchColumn() === 0);
    assert(storeTonTransaction($pdo, ['in_msg'=>$noComment] + $tx, $destination, '-3', true) === 'unmatched');
    $underpaid = ['value'=>'399999999','hash'=>base64_encode(str_repeat('d',32))] + $tx['in_msg'];
    assert(storeTonTransaction($pdo, ['in_msg'=>$underpaid] + $tx, $destination, '-3') === 'review');
    assert(storeDownload($pdo, 789, $hash) === null);
    $calls = 0;
    $fetchTon = static function ($url) use ($tx,$destination,&$calls) {
        $calls++;
        assert(str_starts_with($url,'https://testnet.toncenter.com/api/v3/transactions?'));
        assert(str_contains($url,rawurlencode($destination)));
        return ['transactions'=>[['lt'=>'1900'] + $tx]];
    };
    storeSyncTonMerchant($pdo,$ton['merchant'],$config,1,$fetchTon);
    storeSyncTonMerchant($pdo,$ton['merchant'],$config,1,$fetchTon);
    assert($calls === 1 && (string) $pdo->query('SELECT logical_time FROM plugin_store_cursor')->fetchColumn() === '1900');
    assert(storeTonTransaction($pdo, $tx, $destination, '-3') === 'paid');
    assert(storeDownload($pdo, 789, $hash) === $bytes);
    assert(storeAuthorBalance($pdo,123,'ton',$ton['merchant']) === 0);
    $duplicate = ['hash'=>base64_encode(str_repeat('e',32))] + $tx['in_msg'];
    assert(storeTonTransaction($pdo, ['in_msg'=>$duplicate] + $tx, $destination, '-3') === 'review');
    assert((int) $pdo->query("SELECT COUNT(*) FROM plugin_store_entitlements")->fetchColumn() === 1);
    storeReview($pdo, $hash, 123, 'revoke', 'fixture revocation');
    assert(storeDownload($pdo, 789, $hash) === null && storeCatalog($pdo, $methods, '-3') === []);
    echo "PASS: signed TON wallet proof, per-author checkout, immutable recipient, replay and foreign transfer isolation, direct settlement and Stars regressions\n";
} finally {
    $files = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS), RecursiveIteratorIterator::CHILD_FIRST);
    foreach ($files as $file) { if ($file->isDir()) rmdir($file->getPathname()); else unlink($file->getPathname()); }
    rmdir($root);
}
