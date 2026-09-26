<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

try{
    $currentUserId=requireAdminSession();
    $pdo=db();$method=$_SERVER['REQUEST_METHOD']??'GET';$input=json_decode((string)file_get_contents('php://input'),true)?:[];
    if($method==='POST'){
        $action=(string)($input['action']??'');
        if($action==='settings.save'){
            $username=ltrim(trim((string)($input['bot_username']??'')),'@');
            if(!preg_match('/^[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]$/D',$username))throw new InvalidArgumentException('Username бота: от 5 до 32 символов, латиница, цифры и подчёркивание');
            saveAppSetting('bot_username',$username);
        }elseif($action==='admin.add'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может добавлять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1||isOwner($id))throw new InvalidArgumentException('Укажите Telegram ID нового администратора');
            $pdo->prepare('INSERT IGNORE INTO admins (telegram_id,added_by) VALUES (?,?)')->execute([$id,$currentUserId]);
        }elseif($action==='admin.remove'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может удалять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1)throw new InvalidArgumentException('Неверный Telegram ID');
            $pdo->prepare('DELETE FROM admins WHERE telegram_id=?')->execute([$id]);
        }elseif($action==='license.save'){
            $id=(int)($input['telegram_id']??0);$status=(string)($input['status']??'');$validRaw=trim((string)($input['valid_until']??''));$validUntil=!empty($input['permanent'])?null:str_replace('T',' ',$validRaw);
            if($id<1||!in_array($status,['active','blocked','refunded'],true)||($validUntil!==null&&!preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/',$validUntil)))throw new InvalidArgumentException('Проверьте Telegram ID, статус и срок лицензии');
            $pdo->prepare('INSERT IGNORE INTO users (telegram_id) VALUES (?)')->execute([$id]);
            $pdo->prepare('INSERT INTO licenses (telegram_id,status,valid_until) VALUES (?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),valid_until=VALUES(valid_until)')->execute([$id,$status,$validUntil]);
            if($status!=='active'){$pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);$pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);}
        }elseif($action==='license.revoke'){
            $id=(int)($input['telegram_id']??0);if($id<1)throw new InvalidArgumentException('Неверный Telegram ID');
            $q=$pdo->prepare("UPDATE licenses SET status='blocked' WHERE telegram_id=? AND status='active'");$q->execute([$id]);if(!$q->rowCount())throw new InvalidArgumentException('Активная лицензия не найдена');
            $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);
            $pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);
        }elseif($action==='device.revoke'){
            $deviceId=(string)($input['device_id']??'');if(!preg_match('/^[a-f0-9]{32}$/D',$deviceId))throw new InvalidArgumentException('Неверное устройство');
            $q=$pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE id=? AND revoked_at IS NULL');$q->execute([$deviceId]);if(!$q->rowCount())throw new InvalidArgumentException('Активное устройство не найдено');
            $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE device_id=? AND revoked_at IS NULL')->execute([$deviceId]);
        }elseif($action==='badge.save'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();$badges[$key]=true;saveBadges($badges);
        }elseif($action==='badge.delete'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();unset($badges[$key]);if($type==='user')unset($badges[(string)$id]);saveBadges($badges);
        }elseif($action==='release.save'){
            $release=['version'=>trim((string)($input['version']??'')),'version_code'=>trim((string)($input['version_code']??'')),'status'=>trim((string)($input['status']??'')),'size'=>trim((string)($input['size']??'')),'requirements'=>trim((string)($input['requirements']??'')),'package'=>trim((string)($input['package']??'')),'apk_name'=>trim((string)($input['apk_name']??''))];
            if(!preg_match('/^[0-9]+(?:\.[0-9]+){1,3}$/',$release['version'])||!preg_match('/^[0-9]{1,12}$/',$release['version_code'])||!preg_match('/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/',$release['apk_name'])||$release['package']!=='com.bygram.mobile'||array_filter($release,fn($value)=>mb_strlen($value)>120))throw new InvalidArgumentException('Проверьте данные релиза');
            $json=json_encode($release,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_PRETTY_PRINT);if(file_put_contents(__DIR__.'/release.json.tmp',$json,LOCK_EX)===false||!rename(__DIR__.'/release.json.tmp',__DIR__.'/release.json'))throw new RuntimeException('Не удалось сохранить данные релиза');
        }else throw new InvalidArgumentException('Неизвестное действие');
    }elseif($method!=='GET'){jsonResponse(['ok'=>false,'error'=>'Method not allowed'],405);}
    $data=[];
    $data['is_owner']=isOwner($currentUserId);
    $data['settings']=['bot_username'=>appSetting('bot_username')];
    $data['release']=json_decode((string)@file_get_contents(__DIR__.'/release.json'),true)?:['version'=>'12.10.1','version_code'=>'70389','status'=>'Стабильная версия · APK','size'=>'≈ 123 МБ','requirements'=>'Android 6.0+','package'=>'com.bygram.mobile','apk_name'=>'BYgram-12.10.1.apk'];
    $data['badges']=loadBadges();
    $data['licenses']=$pdo->query("SELECT l.*,u.username,u.first_name,(SELECT COUNT(*) FROM licensed_devices d WHERE d.telegram_id=l.telegram_id AND d.revoked_at IS NULL) device_count FROM licenses l LEFT JOIN users u ON u.telegram_id=l.telegram_id ORDER BY l.updated_at DESC LIMIT 200")->fetchAll();
    $data['devices']=$pdo->query('SELECT id,telegram_id,device_name,created_at,last_seen_at FROM licensed_devices WHERE revoked_at IS NULL ORDER BY last_seen_at DESC LIMIT 400')->fetchAll();
    $data['admins']=$data['is_owner']?$pdo->query('SELECT a.*,u.username,u.first_name FROM admins a LEFT JOIN users u ON u.telegram_id=a.telegram_id ORDER BY a.created_at DESC')->fetchAll():[];
    jsonResponse(['ok'=>true,'data'=>$data]);
}catch(Throwable $e){if(isset($pdo)&&$pdo->inTransaction())$pdo->rollBack();jsonResponse(['ok'=>false,'error'=>$e->getMessage()],$e instanceof InvalidArgumentException?422:($e instanceof AdminAuthenticationException?401:403));}
