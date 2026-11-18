<?php
declare(strict_types=1);

function storeTonWalletKey(array $config, string $network, string $address): string {
    $host = storeTonHost($network);
    $headers = storeTonHeaders($config, $network);
    $query = http_build_query(['address'=>$address,'include_boc'=>'false']);
    $state = storeHttp("$host/api/v3/accountStates?$query", null, $headers)['accounts'][0] ?? [];
    $hash = base64_decode(strtr((string) ($state['code_hash'] ?? ''), '-_', '+/'), true);
    // Official Wallet V3R2, V4R2 and V5R1 implementations with get_public_key.
    $walletCodes = [
        '84dafa449f98a6987789ba232358072bc0f76dc4524002a5d0918b9a75d2d599',
        'feb5ff6820e2ff0d9483e7e0d62c817d846789fb4ae580c878866d959dabd5c0',
        '20834b7b72b112147e1b2fb457b84e74d1a30f04f737d4f62a668e9552d2b72f',
    ];
    if (strtolower((string) ($state['address'] ?? '')) !== $address || ($state['status'] ?? null) !== 'active'
        || ($state['suspended'] ?? false) || $hash === false || !in_array(bin2hex($hash), $walletCodes, true))
        throw new InvalidArgumentException('Нужен активный TON-кошелёк V3R2, V4R2 или V5R1.');
    if ($headers === []) usleep(1100000);
    $result = storeHttp("$host/api/v2/runGetMethod", ['address'=>$address,'method'=>'get_public_key','stack'=>[]], $headers);
    $entry = $result['result']['stack'][0] ?? [];
    if (($result['ok'] ?? false) !== true || ($result['result']['exit_code'] ?? null) !== 0 || ($entry[0] ?? null) !== 'num'
        || !preg_match('/^0x([0-9a-f]{1,64})$/Di', $entry[1] ?? '', $match))
        throw new InvalidArgumentException('Не удалось проверить публичный ключ кошелька.');
    return hex2bin(str_pad($match[1], 64, '0', STR_PAD_LEFT));
}

function storeVerifyTonWallet(array $data, string $challenge, string $domain, string $network, callable $key, int $now): string {
    $account = $data['account'] ?? [];
    $proof = $data['proof'] ?? [];
    $address = is_string($account['address'] ?? null) ? strtolower($account['address']) : '';
    $timestamp = filter_var($proof['timestamp'] ?? null, FILTER_VALIDATE_INT);
    $signature = is_string($proof['signature'] ?? null) ? base64_decode($proof['signature'], true) : false;
    if (!preg_match('/^0:[0-9a-f]{64}$/D', $address) || ($account['chain'] ?? null) !== $network
        || $domain === '' || ($proof['domain']['value'] ?? null) !== $domain || ($proof['domain']['lengthBytes'] ?? null) !== strlen($domain)
        || $challenge === '' || ($proof['payload'] ?? null) !== $challenge || !$timestamp || abs($now - $timestamp) > 300
        || $signature === false || strlen($signature) !== 64)
        throw new InvalidArgumentException('Подтверждение кошелька недействительно. Проверьте сеть и подключите кошелёк заново.');
    $message = 'ton-proof-item-v2/' . pack('N', 0) . hex2bin(substr($address, 2))
        . pack('V', strlen($domain)) . $domain . pack('P', $timestamp) . $challenge;
    $digest = hash('sha256', "\xff\xff" . 'ton-connect' . hash('sha256', $message, true), true);
    $publicKey = $key($network, $address);
    if (!is_string($publicKey) || strlen($publicKey) !== 32 || !storeTonSignatureValid($signature, $digest, $publicKey))
        throw new InvalidArgumentException('Подпись кошелька недействительна.');
    return $address;
}

function storeTonSignatureValid(string $signature, string $message, string $key): bool {
    if (function_exists('sodium_crypto_sign_verify_detached'))
        return sodium_crypto_sign_verify_detached($signature, $message, $key);
    require_once __DIR__ . '/sodium-compat.phar';
    try {
        return ParagonIE_Sodium_Compat::crypto_sign_verify_detached($signature, $message, $key);
    } catch (SodiumException) {
        return false;
    }
}
