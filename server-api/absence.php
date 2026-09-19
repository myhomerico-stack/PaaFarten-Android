<?php
require __DIR__.'/auth.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='POST') api_out(405,['ok'=>false,'message'=>'Kun POST er tilladt.']);
$d=api_driver(); $id=(int)$d['local_id'];
$kind=trim((string)($_POST['kind']??''));
$from=trim((string)($_POST['from']??''));
$to=trim((string)($_POST['to']??''));
$note=trim((string)($_POST['note']??''));
if(!in_array($kind,['Fri','Ferie'],true)) api_out(400,['ok'=>false,'message'=>'Ugyldig type.']);
if(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$from)||!preg_match('/^\d{4}-\d{2}-\d{2}$/',$to)||$to<$from) api_out(400,['ok'=>false,'message'=>'Vælg gyldige datoer.']);
try{
 $pdo=db();
 $pdo->exec("CREATE TABLE IF NOT EXISTS pf_absence_requests (id INT AUTO_INCREMENT PRIMARY KEY,driver_local_id INT NOT NULL,kind VARCHAR(20) NOT NULL,date_from DATE NOT NULL,date_to DATE NOT NULL,note TEXT NULL,status VARCHAR(30) NOT NULL DEFAULT 'Ansøgt',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,INDEX(driver_local_id),INDEX(date_from),INDEX(date_to)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
 $q=$pdo->prepare("INSERT INTO pf_absence_requests(driver_local_id,kind,date_from,date_to,note,status) VALUES(?,?,?,?,?,'Ansøgt')");
 $q->execute([$id,$kind,$from,$to,$note]);
 $driver=(string)($d['name']??'Chauffør'); $email=(string)($d['email']??'');
 $subject="$kind-forespørgsel fra $driver";
 $body="Chauffør: $driver\nE-mail: $email\nType: $kind\nFra: $from\nTil: $to\nBemærkning: $note\nDato/tid: ".date('d-m-Y H:i');
 @mail('info@barbussen-fyn.dk',$subject,$body,"From: info@barbussen-fyn.dk\r\nContent-Type: text/plain; charset=UTF-8");
 api_out(200,['ok'=>true,'request'=>['id'=>(int)$pdo->lastInsertId(),'kind'=>$kind,'from'=>$from,'to'=>$to,'note'=>$note,'status'=>'Ansøgt']]);
}catch(Throwable $e){error_log('absence: '.$e->getMessage());api_out(500,['ok'=>false,'message'=>'Kunne ikke gemme forespørgslen.']);}
