<?php
require __DIR__.'/auth.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='POST') api_out(405,['ok'=>false,'message'=>'Kun POST er tilladt.']);

/*
 * Kontor-endpoint til godkendelse/afvisning.
 * Beskyt denne fil med jeres eksisterende kontor/admin-login før den tages i brug offentligt.
 */
$id=(int)($_POST['id']??0);
$status=trim((string)($_POST['status']??''));
if($id<1 || !in_array($status,['Godkendt','Afvist'],true)) api_out(400,['ok'=>false,'message'=>'Ugyldig forespørgsel.']);
try{
 $q=db()->prepare("UPDATE pf_absence_requests SET status=?,decided_at=NOW() WHERE id=?");
 $q->execute([$status,$id]);
 if($q->rowCount()<1) api_out(404,['ok'=>false,'message'=>'Fraværsansøgningen blev ikke fundet eller var allerede opdateret.']);
 api_out(200,['ok'=>true,'id'=>$id,'status'=>$status]);
}catch(Throwable $e){error_log('absence decision: '.$e->getMessage());api_out(500,['ok'=>false,'message'=>'Kunne ikke opdatere fraværet.']);}
