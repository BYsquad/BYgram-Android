<?php
declare(strict_types=1);

function profileBackgroundImage(string $encoded): array {
    if (strlen($encoded) > 5592408 || ($data = base64_decode($encoded, true)) === false || strlen($data) > 4194304) {
        throw new InvalidArgumentException('Image must be at most 4 MiB');
    }
    $image = @getimagesizefromstring($data);
    if (!$image || !in_array($image['mime'], ['image/jpeg', 'image/png', 'image/gif', 'image/webp'], true)
            || $image[0] < 1 || $image[1] < 1 || $image[0] > 4096 || $image[1] > 4096 || $image[0] * $image[1] > 16777216) {
        throw new InvalidArgumentException('Unsupported image');
    }
    return [$data, $image['mime'], hash('sha256', $data)];
}
