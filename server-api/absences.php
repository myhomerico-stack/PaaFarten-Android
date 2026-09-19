<?php
require __DIR__.'/auth.php';
$d=api_driver(); $id=(int)$d['local_id'];
$from=trim((string)($_GET['from']??date('Y-m-01'))); $to=trim((string)($_GET['to']??date('Y-m-t')));
try{
 $q=db()->prepare("SELECT id,kind,date_from AS `from`,date_to AS `to`,note,status,created_at FROM pf_absence_requests WHERE driver_local_id=? AND date_from<=? AND date_to>=? ORDER BY date_from");
 $q->execute([$id,$to,$from]);
 api_out(200,['ok'=>true,'requests'=>$q->fetchAll()]);
}catch(Throwable $e){error_log('absences: '.$e->getMessage());api_out(500,['ok'=>false,'message'=>'Kunne ikke hente fravær.']);}
