<?php
declare(strict_types=1);

return [
    'license_checks_enabled' => false,
    'account_confirmation_enabled' => false,
    'request_signature_checks_enabled' => false,
    'device_revocation_checks_enabled' => false,
    'db_dsn' => 'mysql:host=127.0.0.1;port=3306;dbname=bygram;charset=utf8mb4',
    'db_user' => 'bygram',
    'db_password' => 'replace-me',
    'bot_token' => 'replace-me',
    'bot_username' => 'BYgram_unofficial_client_bot',
    'bot_webhook_secret' => 'replace-with-a-random-secret',
    'owner_id' => 123456789,
    'manager_username' => 'BYgram_manager',
    'admin_url' => 'https://example.com/activity-api/admin.php',
    'terms_url' => 'https://example.com/terms',
    'support_url' => 'https://t.me/BYgram_manager',
];
