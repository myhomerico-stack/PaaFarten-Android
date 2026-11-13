<?php
declare(strict_types=1);

// Upload as /api/hours.php alongside the existing auth.php and common.php.
// Uses the authenticated driver's ID; no ID supplied by the client is trusted.
require __DIR__.'/auth.php';
$driver=api_driver();
$driverId=(int)$driver['local_id'];
$offset=max(-24,min(12,(int)($_GET['offset']??0)));

date_default_timezone_set('Europe/Copenhagen');
$today=new DateTimeImmutable('today');
if((int)$today->format('d')>=20){
    $start=new DateTimeImmutable($today->format('Y-m-20'));
}else{
    $start=$today->modify('first day of previous month')->setDate(
        (int)$today->modify('first day of previous month')->format('Y'),
        (int)$today->modify('first day of previous month')->format('m'),20
    );
}
$start=$start->modify(($offset>=0?'+':'').$offset.' months');
$end=$start->modify('+1 month -1 day');

try{
    $pdo=db();
    $from=$start->format('Y-m-d');
    $to=$end->format('Y-m-d');
    $daily=[];

    // Only NAV workdays explicitly approved by the administrator.
    $q=$pdo->prepare("SELECT w.work_date, a.approved_minutes
      FROM pf_nav_workdays w
      INNER JOIN pf_nav_workday_approvals a ON a.workday_id=w.id
      WHERE w.driver_local_id=? AND w.work_date BETWEEN ? AND ?
        AND w.ended_at IS NOT NULL AND a.status='approved'");
    $q->execute([$driverId,$from,$to]);
    foreach($q->fetchAll(PDO::FETCH_ASSOC) as $w){
        $date=(string)$w['work_date'];
        $daily[$date]=($daily[$date]??0)+max(0,(int)$w['approved_minutes']);
    }

    // Approved manual time entries are counted independently, not via pf_hours:
    // pf_hours may already contain NAV+manual totals and would double count.
    $q=$pdo->prepare("SELECT work_date,started_at,ended_at,pause_minutes
      FROM pf_admin_manual_hours WHERE driver_local_id=? AND work_date BETWEEN ? AND ?
        AND status='approved'");
    $q->execute([$driverId,$from,$to]);
    foreach($q->fetchAll(PDO::FETCH_ASSOC) as $m){
        $gross=max(0,strtotime((string)$m['ended_at'])-strtotime((string)$m['started_at']));
        $pause=max(0,(int)$m['pause_minutes']);
        $unpaid=$pause>=60?$pause*60:0;
        $minutes=(int)floor(max(0,$gross-$unpaid)/60);
        $date=(string)$m['work_date'];
        $daily[$date]=($daily[$date]??0)+$minutes;
    }

    ksort($daily);
    $rows=[];$total=0;
    foreach($daily as $date=>$minutes){
        $rows[]=['work_date'=>$date,'actual_minutes'=>$minutes,'status'=>'approved'];
        $total+=$minutes;
    }

    api_out(200,[
        'ok'=>true,
        'period'=>[
            'from'=>$from,'to'=>$to,
            'planned_minutes'=>0,'actual_minutes'=>$total,
            'approved_minutes'=>$total
        ],
        'hours'=>$rows
    ]);
}catch(Throwable $e){
    error_log('PaaFarten approved hours: '.$e->getMessage());
    api_out(500,['ok'=>false,'message'=>'Godkendte timer kunne ikke hentes. Kontroller at admin-timernes tabeller er installeret.']);
}
