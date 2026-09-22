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
 $q=$pdo->prepare("INSERT INTO pf_absence_requests(driver_local_id,type,date_from,date_to,note,status) VALUES(?,?,?,?,?,'Ansøgt')");
 $q->execute([$id,$kind,$from,$to,$note]);
 api_out(200,['ok'=>true,'request'=>['id'=>(int)$pdo->lastInsertId(),'kind'=>$kind,'from'=>$from,'to'=>$to,'note'=>$note,'status'=>'Ansøgt']]);
}catch(Throwable $e){error_log('absence: '.$e->getMessage());api_out(500,['ok'=>false,'message'=>'Kunne ikke gemme forespørgslen.']);}
