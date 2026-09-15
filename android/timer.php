<?php
session_start();

if(!isset($_SESSION['user_id'])){
    echo "<script>alert('請先登入'); location.href='index.html';</script>";
    exit;
}

$conn = new mysqli("localhost", "root", "Rev_on1028", "revon");
$conn->set_charset("utf8mb4");

$track_id = intval($_GET['track_id'] ?? 1);
$source = ($_GET['source'] ?? 'official') === 'custom' ? 'custom' : 'official';

// 根據來源查詢對應的資料表
if ($source === 'custom') {
    $stmt = $conn->prepare("SELECT * FROM custom_tracks WHERE id=? AND status='approved'");
    $stmt->bind_param("i", $track_id);
} else {
    $stmt = $conn->prepare("SELECT * FROM tracks WHERE id=?");
    $stmt->bind_param("i", $track_id);
}

$stmt->execute();
$res = $stmt->get_result();

if(!$res || $res->num_rows == 0){
    die("<script>alert('找不到賽道，可能尚未審核通過'); location.href='tracks.php';</script>");
}

$track = $res->fetch_assoc();

function safeFloat($v){
    return is_numeric($v) ? floatval($v) : 0;
}

$mid1 = (is_numeric($track['mid1_lat']) && is_numeric($track['mid1_lng']))
? ['lat'=>floatval($track['mid1_lat']), 'lng'=>floatval($track['mid1_lng'])]
: null;

$mid2 = (is_numeric($track['mid2_lat']) && is_numeric($track['mid2_lng']))
? ['lat'=>floatval($track['mid2_lat']), 'lng'=>floatval($track['mid2_lng'])]
: null;
?>

<!DOCTYPE html>
<html lang="zh-Hant">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>REV-ON RACE SYSTEM</title>
    <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Orbitron:wght@500;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --neon-blue: #00A3FF;
            --neon-red: #e10600;
            --neon-green: #0f0;
            --neon-yellow: #ffea00;
        }

        body { margin:0; background:#000; color:#fff; font-family:'Bebas Neue',sans-serif; overflow:hidden; }
        
        #overlay { position:fixed; inset:0; background:radial-gradient(circle, #222 0%, #000 100%); z-index:9999; display:flex; flex-direction:column; align-items:center; justify-content:center; }
        #start-btn { background:var(--neon-red); color:#fff; border:none; padding:18px 50px; border-radius:50px; font-size:24px; cursor:pointer; box-shadow: 0 0 25px rgba(225,6,0,0.6); transition: 0.3s; }

        #map { width:100%; height:70vh; }

        #recenter-btn {
            position: absolute; right: 15px; bottom: 30vh;
            width: 50px; height: 50px; background: rgba(0,0,0,0.7);
            border: 2px solid var(--neon-blue); border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
            z-index: 100;
        }

        #countdown-overlay {
            position: fixed;
            inset: 0;
            display: none;
            align-items: center;
            justify-content: center;
            font-size: 18vw;
            font-family: 'Orbitron', sans-serif;
            color: var(--neon-red);
            background: rgba(0,0,0,0.4);
            z-index: 500;
            pointer-events: none;
            text-shadow: 0 0 25px currentColor;
        }

        #hud { 
            height:22vh; background: linear-gradient(to bottom, #111, #000); 
            padding:10px 15px; box-sizing: border-box; 
            border-top: 2px solid #333; display: flex; flex-direction: column;
        }
        
        #hud-top { display: flex; align-items: center; justify-content: space-between; flex: 1; }

        #timer { 
            font-size: 52px; color: var(--neon-green); 
            font-family: 'Orbitron', sans-serif;
            text-shadow: 0 0 10px rgba(0,255,0,0.5);
            letter-spacing: -2px;
        }

        #btn-group { display: flex; flex-direction: column; gap: 6px; }
        
        .race-btn { 
            background: rgba(40,40,40,0.9); color:#fff; border: 1px solid #555; 
            padding: 8px 15px; border-radius: 6px; font-size: 14px; cursor:pointer; 
            font-family: sans-serif; font-weight: bold;
        }
        .race-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .race-btn.abort { border-color: var(--neon-red); color: var(--neon-red); }

        #hud-bottom { display: flex; justify-content: space-between; align-items: flex-end; padding-top: 5px; border-top: 1px solid #222; }

        #speed-section { display: flex; align-items: center; gap: 12px; }
        #speed-box { font-family: 'Orbitron', sans-serif; color: var(--neon-blue); line-height: 1; }
        #speed-val { font-size: 36px; font-weight: 700; }
        #speed-unit { font-size: 12px; margin-left: 3px; opacity: 0.7; }

        #gps-box { display: flex; flex-direction: column; align-items: center; gap: 2px; }
        #gps-icon { width: 14px; height: 14px; border-radius: 50%; background: #333; border: 1px solid #555; transition: 0.3s; }
        #gps-txt { font-size: 10px; font-family: 'Orbitron', sans-serif; color: #555; }
        
        .gps-excellent { background: var(--neon-green) !important; box-shadow: 0 0 8px var(--neon-green); border-color: #fff !important; }
        .gps-good { background: var(--neon-yellow) !important; box-shadow: 0 0 8px var(--neon-yellow); border-color: #fff !important; }
        .gps-weak { background: var(--neon-red) !important; box-shadow: 0 0 8px var(--neon-red); border-color: #fff !important; }

        #info-box { text-align: right; }
        #info { font-size: 16px; color: var(--neon-blue); font-family: sans-serif; font-weight: bold; margin-bottom: 4px; }
        #status-dots { display: flex; gap: 6px; justify-content: flex-end; }
        .dot-ind { width: 8px; height: 8px; border-radius: 50%; background: #333; border: 1px solid #555; }
        .dot-ind.active { background: var(--neon-green); box-shadow: 0 0 8px var(--neon-green); border-color: #fff; }

        #nav-hint { 
            position: fixed; bottom: 25vh; left: 15px; 
            background: rgba(0,0,0,0.85); border: 2px solid var(--neon-blue);
            padding: 12px 16px; border-radius: 10px; font-size: 14px;
            z-index: 99; max-width: 220px; display: none;
            font-family: sans-serif;
            line-height: 1.6;
        }
        #nav-hint .nav-direction {
            font-size: 18px; font-weight: bold; color: var(--neon-blue);
            margin-bottom: 4px;
        }
        #nav-hint .nav-distance {
            font-size: 13px; color: #aaa;
        }

        /* ===== 強制教學引導 ===== */
        #tutorial-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.75);
            z-index: 9000;
            display: none;
            pointer-events: all;
        }
        #tutorial-overlay.show { display: block; }
        
        .tutorial-tooltip {
            position: absolute;
            background: #111;
            border: 2px solid var(--neon-blue);
            border-radius: 14px;
            padding: 16px 20px;
            max-width: 280px;
            font-family: sans-serif;
            box-shadow: 0 0 30px rgba(0,163,255,0.3);
            animation: tutorialPulse 2s infinite;
        }
        @keyframes tutorialPulse {
            0%, 100% { box-shadow: 0 0 20px rgba(0,163,255,0.3); }
            50% { box-shadow: 0 0 40px rgba(0,163,255,0.6); }
        }
        .tutorial-tooltip .step-badge {
            display: inline-block;
            background: var(--neon-blue);
            color: #000;
            font-size: 11px;
            font-weight: bold;
            padding: 2px 8px;
            border-radius: 10px;
            margin-bottom: 8px;
        }
        .tutorial-tooltip .step-title {
            font-size: 16px;
            font-weight: bold;
            color: #fff;
            margin-bottom: 6px;
        }
        .tutorial-tooltip .step-desc {
            font-size: 13px;
            color: #aaa;
            line-height: 1.5;
        }
        .tutorial-tooltip .step-arrow {
            font-size: 24px;
            color: var(--neon-blue);
            margin-top: 6px;
            animation: arrowBounce 1s infinite;
        }
        @keyframes arrowBounce {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(5px); }
        }

        .tutorial-highlight {
            position: relative;
            z-index: 9001 !important;
            box-shadow: 0 0 0 4px var(--neon-blue), 0 0 20px rgba(0,163,255,0.5);
            border-radius: 8px;
        }
    </style>
</head>
<body>

<div id="overlay">
    <h1 style="font-size:60px; margin:0; letter-spacing:5px;">REV-ON</h1>
    <h2 style="color:var(--neon-red); font-family:sans-serif; font-size:18px; margin-bottom:30px;"><?= htmlspecialchars($track['name']) ?></h2>
    <div id="vehicle-selector" style="margin-bottom:25px; display:flex; gap:15px;">
        <div class="v-type" onclick="selectVehicle('car')" id="v-car" style="border:2px solid #555; padding:10px 20px; border-radius:10px; cursor:pointer; transition:0.3s;">
            <div style="font-size:24px;">🚗</div>
            <div style="font-size:12px; margin-top:5px;">汽車 CAR</div>
        </div>
        <div class="v-type" onclick="selectVehicle('motor')" id="v-motor" style="border:2px solid #555; padding:10px 20px; border-radius:10px; cursor:pointer; transition:0.3s;">
            <div style="font-size:24px;">🏍️</div>
            <div style="font-size:12px; margin-top:5px;">機車 MOTOR</div>
        </div>
        <div class="v-type" onclick="selectVehicle('other')" id="v-other" style="border:2px solid #555; padding:10px 20px; border-radius:10px; cursor:pointer; transition:0.3s;">
            <div style="font-size:24px;">🏎️</div>
            <div style="font-size:12px; margin-top:5px;">其他 OTHER</div>
        </div>
    </div>
    <button id="start-btn" onclick="startEngine()" disabled style="opacity:0.3;">START ENGINE</button>
    <p id="v-hint" style="color:var(--neon-red); font-size:14px; margin-top:10px;">* 請先選擇載具類別以解鎖啟動按鈕</p>
</div>

<div id="tutorial-overlay"></div>
<div id="countdown-overlay">3</div>
<div id="nav-hint"></div>
<div id="map"></div>

<div id="recenter-btn" onclick="recenterMap()">
    <svg viewBox="0 0 24 24" width="26" height="26" fill="var(--neon-blue)"><path d="M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/></svg>
</div>

<div id="hud">
    <div id="hud-top">
        <div id="timer">00:00:00</div>
        <div id="btn-group">
            <button id="nav-btn" class="race-btn" onclick="drawToStart()">📍 導航起點</button>
            <button class="race-btn abort" onclick="abortRace()">🛑 終止</button>
        </div>
    </div>

    <div id="hud-bottom">
        <div id="speed-section">
            <div id="speed-box">
                <span id="speed-val">0</span><span id="speed-unit">KM/H</span>
            </div>
            <div id="gps-box">
                <div id="gps-icon"></div>
                <div id="gps-txt">GPS</div>
            </div>
        </div>
        <div id="info-box">
            <div id="info">GPS READY</div>
            <div id="status-dots">
                <div id="dot-s" class="dot-ind" title="Start"></div>
                <div id="dot-1" class="dot-ind" title="CP1"></div>
                <div id="dot-2" class="dot-ind" title="CP2"></div>
                <div id="dot-e" class="dot-ind" title="End"></div>
            </div>
        </div>
    </div>
</div>

<script>
// 全域防呆：捕獲 Google Maps 授權失敗 (例如 API Key 遭參照限制)
window.gm_authFailure = function() {
    alert("⚠️ Google Maps 授權驗證失敗！\n請確認 API Key 的域名限制 (HTTP Referrer) 與 API 服務狀態。");
    document.getElementById("info").textContent = "地圖授權失敗";
};
</script>

<script src="https://maps.googleapis.com/maps/api/js?key=AIzaSyBcIlEGGp-DDakBjGE6ODOK_eNwOZK40mU&libraries=geometry,directions"></script>

<script>
const trackData = {
    start: { lat: <?=safeFloat($track['start_lat'])?>, lng: <?=safeFloat($track['start_lng'])?> },
    mid1: <?= $mid1 ? json_encode($mid1) : 'null' ?>,
    mid2: <?= $mid2 ? json_encode($mid2) : 'null' ?>,
    end: { lat: <?=safeFloat($track['end_lat'])?>, lng: <?=safeFloat($track['end_lng'])?> }
};

let map, playerMarker;
let directionsService, directionsRenderer;
let lastPos = null;
let raceState = 0; 
let startTime;
let raceAnimationFrame;
let finalTimeMs = 0;
let selectedVehicle = null;
let passedMid1 = false;
let passedMid2 = false;
let trigger500 = false;
let trigger300 = false;
let trigger100 = false;
let triggerGO = false;
let wakeLock = null;
let screenLock = false;
let watchId = null;

	// 請求螢幕恆亮
	async function requestWakeLock() {
	    try {
	        if ('wakeLock' in navigator) {
	            wakeLock = await navigator.wakeLock.request('screen');
	            console.log('Wake Lock is active');
	        }
	    } catch (err) {
	        console.error(`${err.name}, ${err.message}`);
	    }
	}

// ===== 碰點半徑設定（根據 GPS 精度動態調整）=====
const BASE_TRIGGER_RADIUS = 40;       // 基礎半徑 40m
const MAX_TRIGGER_RADIUS = 80;        // 最大半徑 80m（GPS 差時放寬）
let currentAccuracy = 999;

function getDynamicRadius() {
    if (currentAccuracy < 10) return BASE_TRIGGER_RADIUS;
    if (currentAccuracy < 20) return BASE_TRIGGER_RADIUS + 10;
    if (currentAccuracy < 35) return BASE_TRIGGER_RADIUS + 20;
    return MAX_TRIGGER_RADIUS;
}

// ===== 路線與導航更新 =====
let currentRoute = null;
let currentSteps = [];           // 導航步驟
let currentStepIndex = 0;        // 目前導航步驟索引
let offRouteThreshold = 50;      // 偏離路線閾值
let lastRouteRecalcTime = 0;
const RECALC_INTERVAL = 5000;    // 路線重算間隔（5 秒）

// ===== 語音控制 =====
let speechQueue = [];
let isSpeaking = false;
let lastSpokenEvent = '';        // 上一次播報的事件 ID
let lastSpokenTime = 0;         // 上一次播報時間
const SPEECH_COOLDOWN = 4000;   // 同類型語音冷卻 4 秒
let lastNavInstruction = '';     // 上一次導航指令

function speak(text, eventId) {
    if (!window.speechSynthesis) return;
    
    if (eventId && eventId === lastSpokenEvent) return;
    
    const now = Date.now();
    if (now - lastSpokenTime < SPEECH_COOLDOWN && !eventId) return;
    
    window.speechSynthesis.cancel();
    speechQueue = [];
    
    const msg = new SpeechSynthesisUtterance();
    msg.text = text;
    msg.lang = 'zh-TW'; 
    msg.volume = 1;
    msg.rate = 1.1;
    msg.pitch = 1.0;
    msg.onend = () => { isSpeaking = false; };
    msg.onerror = () => { isSpeaking = false; };
    
    isSpeaking = true;
    lastSpokenTime = now;
    if (eventId) lastSpokenEvent = eventId;
    
    window.speechSynthesis.speak(msg);
}

function speakNav(text) {
    if (text === lastNavInstruction) return;
    lastNavInstruction = text;
    speak(text, null);
}

// GPS 優化與穩定器
const GPS_UPDATE_INTERVAL = 200;
let lastGPSUpdate = 0;

let lastSmoothedPos = null;
let velocity = { lat: 0, lng: 0 };

function getSmoothedPosition(lat, lng, accuracy) {
    const newPos = { lat, lng };
    if (!lastSmoothedPos) {
        lastSmoothedPos = newPos;
        return newPos;
    }

    const weight = accuracy < 10 ? 0.7 : (accuracy < 25 ? 0.4 : 0.2);
    
    const smoothed = {
        lat: lastSmoothedPos.lat * (1 - weight) + newPos.lat * weight,
        lng: lastSmoothedPos.lng * (1 - weight) + newPos.lng * weight
    };
    
    velocity = {
        lat: smoothed.lat - lastSmoothedPos.lat,
        lng: smoothed.lng - lastSmoothedPos.lng
    };
    
    lastSmoothedPos = smoothed;
    return smoothed;
}

function isLineIntersectingCircle(p1, p2, center, radius) {
    const dist1 = google.maps.geometry.spherical.computeDistanceBetween(p1, center);
    const dist2 = google.maps.geometry.spherical.computeDistanceBetween(p2, center);
    
    if (dist1 <= radius || dist2 <= radius) return true;
    
    const lineHeading = google.maps.geometry.spherical.computeHeading(p1, p2);
    const centerHeading = google.maps.geometry.spherical.computeHeading(p1, center);
    const angle = Math.abs(centerHeading - lineHeading);
    
    if (angle > 90 && angle < 270) return false;
    
    const distToCenter = dist1 * Math.sin(angle * Math.PI / 180);
    const projectionDist = dist1 * Math.cos(angle * Math.PI / 180);
    const lineLen = google.maps.geometry.spherical.computeDistanceBetween(p1, p2);
    
    return (projectionDist > 0 && projectionDist < lineLen && Math.abs(distToCenter) <= radius);
}

let checkpointPassLog = {
    mid1: [],
    mid2: [],
    end: []
};

function isCheckpointPassed(checkpointKey, currentPos, targetPos) {
    const dist = google.maps.geometry.spherical.computeDistanceBetween(currentPos, targetPos);
    const dynamicRadius = getDynamicRadius();
    
    const speedBonus = velocity ? Math.sqrt(velocity.lat**2 + velocity.lng**2) * 100000 : 0;
    const finalRadius = dynamicRadius + Math.min(15, speedBonus);

    if (dist <= finalRadius) return true;
    
    if (lastPos && isLineIntersectingCircle(lastPos, currentPos, targetPos, finalRadius)) {
        return true;
    }
    
    checkpointPassLog[checkpointKey].push(dist);
    if (checkpointPassLog[checkpointKey].length > 5) checkpointPassLog[checkpointKey].shift();
    
    const log = checkpointPassLog[checkpointKey];
    if (log.length >= 3) {
        const minDist = Math.min(...log);
        const lastDist = log[log.length - 1];
        const prevDist = log[log.length - 2];
        if (minDist <= finalRadius * 1.5 && lastDist > prevDist) return true;
    }
    
    return false;
}

function selectVehicle(type) {
    selectedVehicle = type;
    document.querySelectorAll('.v-type').forEach(el => {
        el.style.borderColor = '#555';
        el.style.background = 'transparent';
        el.style.boxShadow = 'none';
    });
    const selected = document.getElementById('v-' + type);
    selected.style.borderColor = 'var(--neon-red)';
    selected.style.background = 'rgba(225,6,0,0.1)';
    selected.style.boxShadow = '0 0 15px rgba(225,6,0,0.3)';
    
    const startBtn = document.getElementById('start-btn');
    startBtn.disabled = false;
    startBtn.style.opacity = '1';
    startBtn.style.background = 'var(--neon-red)';
    document.getElementById('v-hint').style.display = 'none';
}

function startEngine() {
    if(!selectedVehicle) return;

    // 🔓 解鎖 iOS Safari 語音自動播放權限
    if (window.speechSynthesis) {
        const unlockUtterance = new SpeechSynthesisUtterance('');
        window.speechSynthesis.speak(unlockUtterance);
    }

    const startBtn = document.getElementById("start-btn");
    startBtn.disabled = true;
    startBtn.style.background = "#555";
    startBtn.innerHTML = "準備中 (3)...";
    
    const warnDiv = document.createElement("div");
    warnDiv.id = "force-warn-overlay";
    warnDiv.style.cssText = "position:fixed;inset:0;background:rgba(225,6,0,0.9);z-index:10000;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:30px;text-align:center;font-family:sans-serif;";
    warnDiv.innerHTML = `
        <h1 style="font-size:40px;color:#fff;margin-bottom:20px;font-weight:900;">⚠️ 重要提示</h1>
        <p style="font-size:24px;color:#fff;line-height:1.5;margin-bottom:30px;font-weight:bold;">
            請務必讓螢幕<span style="text-decoration:underline;color:#ffea00;">保持恆亮</span><br>
            <span style="font-size:28px;">請勿跳出此網頁</span><br>
            否則計時器將會失效！
        </p>
        <div id="warn-timer" style="font-size:60px;color:#ffea00;font-family:'Orbitron';">3</div>
    `;
    document.body.appendChild(warnDiv);

    let count = 3;
    const timer = setInterval(() => {
        count--;
        document.getElementById("warn-timer").innerText = count;
        startBtn.innerHTML = `準備中 (${count})...`;
        if (count <= 0) {
            clearInterval(timer);
            warnDiv.remove();
            
            document.getElementById("overlay").style.display = "none";
            speak("系統上線", "system_online");
            
            requestWakeLock();
            startMonitor();
            initMap();
            
            const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.get('tutorial') === '1') {
                setTimeout(() => startTutorial(), 1200);
            }
        }
    }, 1000);
}

function initMap() {
    const darkStyle = [
        { "elementType": "geometry", "stylers": [{ "color": "#212121" }] },
        { "elementType": "labels.icon", "stylers": [{ "visibility": "off" }] },
        { "featureType": "road", "elementType": "geometry.fill", "stylers": [{ "color": "#2c2c2c" }] }
    ];

    map = new google.maps.Map(document.getElementById("map"), {
        zoom: 18,
        center: trackData.start,
        mapTypeId: 'roadmap',
        disableDefaultUI: true,
        gestureHandling: 'greedy',
        styles: darkStyle
    });

    directionsService = new google.maps.DirectionsService();
    directionsRenderer = new google.maps.DirectionsRenderer({
        suppressMarkers: true,
        preserveViewport: false
    });
    directionsRenderer.setMap(map);

    playerMarker = new google.maps.Marker({
        map,
        zIndex: 100,
        icon: {
            path: google.maps.SymbolPath.CIRCLE,
            scale: 9,
            fillColor: "#e10600",
            fillOpacity: 1,
            strokeWeight: 2,
            strokeColor: "#ffffff"
        }
    });

    const createCheckpointMarker = (pos, color, label) => {
        if(!pos) return null;
        new google.maps.Circle({
            center: pos,
            radius: getDynamicRadius(),
            map: map,
            fillColor: color,
            fillOpacity: 0.08,
            strokeColor: color,
            strokeOpacity: 0.3,
            strokeWeight: 1
        });
        return new google.maps.Marker({
            position: pos,
            map,
            icon: { path: google.maps.SymbolPath.CIRCLE, scale: 12, fillColor: color, fillOpacity: 0.9, strokeWeight: 3, strokeColor: "#fff" },
            label: { text: label, color: '#fff', fontSize: '10px', fontWeight: 'bold' }
        });
    };
    
    createCheckpointMarker(trackData.start, "#e10600", "S");
    if(trackData.mid1) createCheckpointMarker(trackData.mid1, "#ffaa00", "1");
    if(trackData.mid2) createCheckpointMarker(trackData.mid2, "#ffaa00", "2");
    createCheckpointMarker(trackData.end, "#00ff00", "E");

    startTracking();
}

function startTracking() {
    if (watchId !== null) {
        navigator.geolocation.clearWatch(watchId);
    }

    watchId = navigator.geolocation.watchPosition(pos => {
        const now = performance.now();
        if (now - lastGPSUpdate < GPS_UPDATE_INTERVAL) return;
        lastGPSUpdate = now;

        const rawLat = pos.coords.latitude;
        const rawLng = pos.coords.longitude;
        currentAccuracy = pos.coords.accuracy || 999;
        
        if (currentAccuracy > 60) return;

        const speedKmh = pos.coords.speed ? Math.round(pos.coords.speed * 3.6) : 0;
        
        const smoothed = getSmoothedPosition(rawLat, rawLng, currentAccuracy);
        const lat = smoothed.lat;
        const lng = smoothed.lng;
        
        document.getElementById("speed-val").textContent = speedKmh;

        const gpsIcon = document.getElementById("gps-icon");
        const gpsTxt = document.getElementById("gps-txt");
        gpsIcon.className = "";
        
        if (currentAccuracy < 10) {
            gpsIcon.classList.add("gps-excellent");
            gpsTxt.style.color = "var(--neon-green)";
            gpsTxt.textContent = "GPS";
        } else if (currentAccuracy < 25) {
            gpsIcon.classList.add("gps-good");
            gpsTxt.style.color = "var(--neon-yellow)";
            gpsTxt.textContent = "GPS";
        } else {
            gpsIcon.classList.add("gps-weak");
            gpsTxt.style.color = "var(--neon-red)";
            gpsTxt.textContent = Math.round(currentAccuracy) + "m";
        }

        lastPos = new google.maps.LatLng(lat, lng);
        playerMarker.setPosition(lastPos);
        
        if(raceState === 2) {
            map.panTo(lastPos);
            map.setHeading(pos.coords.heading || 0);
            checkRaceProgress(lastPos);
            
            if (now - lastRouteRecalcTime > RECALC_INTERVAL) {
                checkAndRecalculateRoute(lastPos);
                lastRouteRecalcTime = now;
            }
            
            updateNavHint(lastPos);
            
        } else if (raceState < 1.5) {
            const d = google.maps.geometry.spherical.computeDistanceBetween(lastPos, trackData.start);
            document.getElementById("info").textContent = `距起點: ${Math.floor(d)}m`;

            if (d <= 500 && !trigger500) {
                trigger500 = true;
                speak("距離起點五百公尺", "dist_500");
            }

            if (d <= 300 && !trigger300) {
                trigger300 = true;
                showFullScreen("300m", "#ffea00");
                speak("三百公尺", "dist_300");
            }

            if (d <= 100 && !trigger100) {
                trigger100 = true;
                showFullScreen("100m", "#ff6600");
                speak("即將到達起點", "dist_100");
            }

            const dynamicRadius = getDynamicRadius();
            const speedMs = pos.coords.speed || 0;
            const speedBonus = Math.min(20, speedMs * 0.5); 
            const finalRadius = dynamicRadius + speedBonus;

            let isPassed = (d <= finalRadius);
            
            if (!isPassed && lastPos) {
                const currentLatLng = new google.maps.LatLng(lat, lng);
                const startLatLng = new google.maps.LatLng(trackData.start.lat, trackData.start.lng);
                isPassed = isLineIntersectingCircle(lastPos, currentLatLng, startLatLng, finalRadius);
            }

            if (isPassed && !triggerGO) {
                triggerGO = true;
                showFullScreen("GO!", "#00ff00");
                speak("比賽開始", "race_start");
                runActualRaceStart();
            }
        }
    }, err => {
        document.getElementById("gps-txt").textContent = "OFFLINE";
        document.getElementById("gps-icon").className = "gps-weak";
    }, { 
        enableHighAccuracy: true,
        maximumAge: 0,
        timeout: 5000
    });
}

function drawToStart() {
    if (!lastPos || raceState >= 1.5) return; 
    raceState = 1;
    speak("導航到起點", "nav_to_start");
    
    directionsService.route({
        origin: lastPos,
        destination: trackData.start,
        travelMode: "DRIVING"
    }, (result, status) => {
        if (status === "OK") {
            directionsRenderer.setOptions({ 
                polylineOptions: { strokeColor: "#00A3FF", strokeWeight: 5, geodesic: true } 
            });
            directionsRenderer.setDirections(result);
            
            const route = result.routes[0];
            const distance = route.legs.reduce((sum, leg) => sum + leg.distance.value, 0);
            const duration = route.legs.reduce((sum, leg) => sum + leg.duration.value, 0);
            
            const hint = document.getElementById("nav-hint");
            hint.innerHTML = `
                <div class="nav-direction">前往起點</div>
                <div class="nav-distance">${(distance/1000).toFixed(1)}km · 約${Math.ceil(duration/60)}分鐘</div>
            `;
            hint.style.display = "block";
            
            currentSteps = route.legs[0].steps || [];
            currentStepIndex = 0;
        }
    });
}

function runActualRaceStart() {
    raceState = 2;
    startTime = performance.now();
    document.getElementById("nav-btn").disabled = true;
    document.getElementById("dot-s").classList.add("active");
    
    document.getElementById("nav-hint").style.display = "none";
    
    let waypoints = [];
    if (trackData.mid1) waypoints.push({ location: trackData.mid1, stopover: true });
    if (trackData.mid2) waypoints.push({ location: trackData.mid2, stopover: true });

    directionsService.route({
        origin: trackData.start,
        destination: trackData.end,
        waypoints: waypoints,
        travelMode: "DRIVING"
    }, (result, status) => {
        if (status === "OK") {
            currentRoute = result;
            directionsRenderer.setOptions({ 
                polylineOptions: { strokeColor: "#0f0", strokeWeight: 6, geodesic: true } 
            });
            directionsRenderer.setDirections(result);
            
            currentSteps = [];
            result.routes[0].legs.forEach(leg => {
                leg.steps.forEach(step => currentSteps.push(step));
            });
            currentStepIndex = 0;
            
            updateNavHint(lastPos);
        }
    });
    updateTimerLoop();
}

function updateTimerLoop() {
    if (raceState !== 2) return;
    const now = performance.now();
    finalTimeMs = now - startTime;
    const m = String(Math.floor(finalTimeMs / 60000)).padStart(2, '0');
    const s = String(Math.floor((finalTimeMs % 60000) / 1000)).padStart(2, '0');
    const ms = String(Math.floor(finalTimeMs % 1000)).padStart(3, '0').substring(0,2);
    document.getElementById("timer").textContent = `${m}:${s}:${ms}`;
    raceAnimationFrame = requestAnimationFrame(updateTimerLoop);
}

function updateNavHint(currentPos) {
    if (!currentSteps || currentSteps.length === 0) return;
    
    const hint = document.getElementById("nav-hint");
    
    while (currentStepIndex < currentSteps.length - 1) {
        const stepEnd = currentSteps[currentStepIndex].end_location;
        const distToStepEnd = google.maps.geometry.spherical.computeDistanceBetween(
            currentPos, stepEnd
        );
        
        if (distToStepEnd < 30) {
            currentStepIndex++;
        } else {
            break;
        }
    }
    
    if (currentStepIndex >= currentSteps.length) {
        hint.style.display = "none";
        return;
    }
    
    const step = currentSteps[currentStepIndex];
    const distToNext = google.maps.geometry.spherical.computeDistanceBetween(
        currentPos, step.end_location
    );
    
    const instruction = step.instructions ? 
        step.instructions.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ') : '直行';
    
    let distText = distToNext >= 1000 ? 
        (distToNext/1000).toFixed(1) + 'km' : 
        Math.round(distToNext) + 'm';
    
    hint.innerHTML = `
        <div class="nav-direction">${instruction}</div>
        <div class="nav-distance">${distText}</div>
    `;
    hint.style.display = "block";
    
    if (distToNext <= 100 && distToNext > 30) {
        const navEventId = `nav_step_${currentStepIndex}_100`;
        if (lastSpokenEvent !== navEventId) {
            let voiceText = simplifyNavInstruction(instruction, distToNext);
            speak(voiceText, navEventId);
        }
    }
}

function simplifyNavInstruction(instruction, distance) {
    if (instruction.includes('左轉') || instruction.includes('向左')) return '前方左轉';
    if (instruction.includes('右轉') || instruction.includes('向右')) return '前方右轉';
    if (instruction.includes('迴轉') || instruction.includes('掉頭')) return '前方迴轉';
    if (instruction.includes('圓環')) return '進入圓環';
    if (instruction.includes('匝道') || instruction.includes('交流道')) return '前方匝道';
    if (instruction.includes('直行') || instruction.includes('繼續')) return '繼續直行';
    return '繼續前進';
}

function checkAndRecalculateRoute(currentPos) {
    if (!currentRoute || raceState !== 2) return;
    
    let minDistance = Infinity;
    const route = currentRoute.routes[0];
    if (route && route.overview_path) {
        for (let i = 0; i < route.overview_path.length - 1; i++) {
            const p1 = route.overview_path[i];
            const dist = google.maps.geometry.spherical.computeDistanceBetween(currentPos, p1);
            minDistance = Math.min(minDistance, dist);
        }
    }
    
    if (minDistance > offRouteThreshold) {
        document.getElementById("info").textContent = "路線更新中...";
        speak("路線已更新", "reroute_" + Math.floor(Date.now() / 10000));
        
        let waypoints = [];
        if (trackData.mid1 && !passedMid1) waypoints.push({ location: trackData.mid1, stopover: true });
        if (trackData.mid2 && !passedMid2) waypoints.push({ location: trackData.mid2, stopover: true });
        
        directionsService.route({
            origin: currentPos,
            destination: trackData.end,
            waypoints: waypoints,
            travelMode: "DRIVING"
        }, (result, status) => {
            if (status === "OK") {
                currentRoute = result;
                directionsRenderer.setDirections(result);
                
                currentSteps = [];
                result.routes[0].legs.forEach(leg => {
                    leg.steps.forEach(step => currentSteps.push(step));
                });
                currentStepIndex = 0;
            }
        });
    }
}

function checkRaceProgress(currentPos) {
    if (trackData.mid1 && !passedMid1) {
        if (isCheckpointPassed('mid1', currentPos, new google.maps.LatLng(trackData.mid1.lat, trackData.mid1.lng))) {
            passedMid1 = true;
            document.getElementById("dot-1").classList.add("active");
            showFullScreen("CP1 ✓", "#ffaa00");
            speak("通過檢查點一", "cp1_pass");
        }
    }
    
    if (trackData.mid2 && !passedMid2) {
        if (isCheckpointPassed('mid2', currentPos, new google.maps.LatLng(trackData.mid2.lat, trackData.mid2.lng))) {
            passedMid2 = true;
            document.getElementById("dot-2").classList.add("active");
            showFullScreen("CP2 ✓", "#ffaa00");
            speak("通過檢查點二", "cp2_pass");
        }
    }

    const distEnd = google.maps.geometry.spherical.computeDistanceBetween(currentPos, trackData.end);
    const mid1Ready = trackData.mid1 ? passedMid1 : true;
    const mid2Ready = trackData.mid2 ? passedMid2 : true;

    if (isCheckpointPassed('end', currentPos, new google.maps.LatLng(trackData.end.lat, trackData.end.lng))) {
        if (mid1Ready && mid2Ready) {
            finishRace();
        } else {
            if (!mid1Ready && !mid2Ready) {
                document.getElementById("info").textContent = "⚠️ 缺少檢查點 1 和 2";
            } else if (!mid1Ready) {
                document.getElementById("info").textContent = "⚠️ 缺少檢查點 1";
            } else {
                document.getElementById("info").textContent = "⚠️ 缺少檢查點 2";
            }
        }
    } else {
        if (!mid1Ready) {
            const d = google.maps.geometry.spherical.computeDistanceBetween(currentPos, new google.maps.LatLng(trackData.mid1.lat, trackData.mid1.lng));
            document.getElementById("info").textContent = `→ 檢查點1 (${Math.floor(d)}m)`;
        } else if (!mid2Ready) {
            const d = google.maps.geometry.spherical.computeDistanceBetween(currentPos, new google.maps.LatLng(trackData.mid2.lat, trackData.mid2.lng));
            document.getElementById("info").textContent = `→ 檢查點2 (${Math.floor(d)}m)`;
        } else {
            document.getElementById("info").textContent = `→ 終點 (${Math.floor(distEnd)}m)`;
        }
    }
}

function finishRace() {
    if (raceState === 3) return;
    raceState = 3;
    cancelAnimationFrame(raceAnimationFrame);
    reportStatus('finish');
    document.getElementById("dot-e").classList.add("active");
    document.getElementById("nav-hint").style.display = "none";
    showFullScreen("FINISH!", "#00ff00");
    speak("恭喜完成比賽", "race_finish");
    document.getElementById("info").textContent = "上傳中...";

    const timeStr = document.getElementById("timer").textContent;
    let resolved = false;

    const safetyTimer = setTimeout(() => {
        if (resolved) return;
        resolved = true;
        finishWithResult(timeStr, null, '⚠️ 網路較慢，成績可能已經記錄，請稍後到「個人成績」確認');
    }, 12000);

    const controller = new AbortController();
    const fetchTimeout = setTimeout(() => controller.abort(), 10000);

    const formData = new FormData();
    formData.append('track_id', '<?= $track_id ?>');
    formData.append('score_ms', Math.floor(finalTimeMs));
    formData.append('vehicle_type', selectedVehicle);

    fetch('upload_score.php', { method: 'POST', body: formData, signal: controller.signal })
    .then(res => res.json())
    .then(data => {
        clearTimeout(fetchTimeout);
        if (data.status !== 'success') {
            if (resolved) return;
            resolved = true;
            clearTimeout(safetyTimer);
            finishWithResult(timeStr, null, '⚠️ 成績上傳失敗：' + (data.message || '未知錯誤'));
            return;
        }

        const hofData = new FormData();
        hofData.append('track_id', '<?= $track_id ?>');
        hofData.append('time_ms', Math.floor(finalTimeMs));
        hofData.append('vehicle_type', selectedVehicle);

        fetch('check_hall_of_fame.php', { method: 'POST', body: hofData })
        .then(res2 => res2.json())
        .then(hof => {
            if (resolved) return;
            resolved = true;
            clearTimeout(safetyTimer);
            finishWithResult(timeStr, hof && hof.success ? !!hof.is_hall_of_fame : null, null);
        })
        .catch(() => {
            if (resolved) return;
            resolved = true;
            clearTimeout(safetyTimer);
            finishWithResult(timeStr, null, null);
        });
    })
    .catch(err => {
        clearTimeout(fetchTimeout);
        if (resolved) return;
        resolved = true;
        clearTimeout(safetyTimer);
        finishWithResult(timeStr, null, '⚠️ 網路較慢，成績可能已經記錄，請稍後到「個人成績」確認');
    });
}

function finishWithResult(timeStr, isHof, warningMsg) {
    document.getElementById("info").textContent = "✅ " + timeStr;

    setTimeout(() => {
        let msg = "完成! 成績: " + timeStr;
        if (isHof === true) {
            msg += "\n🏆 恭喜！這次成績已進入名人堂！";
        } else if (isHof === false) {
            msg += "\n再接再厲，這次還沒進入名人堂";
        }
        if (warningMsg) {
            msg = warningMsg + "\n\n" + msg;
        }
        alert(msg);
        window.location.href = "tracks.php";
    }, 1500);
}

function showFullScreen(text, color) {
    if (screenLock) return;
    screenLock = true;
    const el = document.getElementById("countdown-overlay");
    el.style.display = "flex";
    el.textContent = text;
    el.style.color = color;
    setTimeout(() => {
        el.style.display = "none";
        screenLock = false;
    }, 800);
}

function recenterMap() { if (lastPos) map.panTo(lastPos); }
function abortRace() {
    if (confirm("確定要終止?")) {
        reportStatus('leave');
        window.location.href = "tracks.php";
    }
}

// ══════════════════════════════════════════════════════
// 玩家狀態監控回報
// ══════════════════════════════════════════════════════
const monitorTrackId = <?= $track_id ?>;
const monitorSource = <?= json_encode($source) ?>;

function reportStatus(action, lat, lng) {
    const body = {
        action: action,
        track_id: monitorTrackId,
        source: monitorSource
    };
    if (lat !== undefined && lng !== undefined) {
        body.lat = lat;
        body.lng = lng;
    }
    navigator.sendBeacon('player_heartbeat.php', JSON.stringify(body));
}

let monitorStarted = false;
function startMonitor() {
    if (monitorStarted) return;
    monitorStarted = true;
    const lat = lastPos ? lastPos.lat() : null;
    const lng = lastPos ? lastPos.lng() : null;
    reportStatus('enter', lat, lng);
}

setInterval(() => {
    if (monitorStarted && raceState <= 2) {
        const lat = lastPos ? lastPos.lat() : null;
        const lng = lastPos ? lastPos.lng() : null;
        reportStatus('heartbeat', lat, lng);
    }
}, 8000);

window.addEventListener('beforeunload', function() {
    if (monitorStarted && raceState <= 2) {
        reportStatus('leave');
    }
});

// ══════════════════════════════════════════════════════
// 強制教學引導系統
// ══════════════════════════════════════════════════════
let tutorialStep = 0;
let tutorialActive = false;

function startTutorial() {
    tutorialActive = true;
    tutorialStep = 1;
    showTutorialStep1();
}

function showTutorialStep1() {
    const overlay = document.getElementById('tutorial-overlay');
    const navBtn = document.getElementById('nav-btn');
    
    overlay.classList.add('show');
    navBtn.classList.add('tutorial-highlight');
    
    const rect = navBtn.getBoundingClientRect();
    
    overlay.innerHTML = `
        <div class="tutorial-tooltip" style="top:${rect.top - 120}px; left:${Math.max(10, rect.left - 100)}px;">
            <div class="step-badge">STEP 1 / 3</div>
            <div class="step-title">點擊「📍 導航起點」</div>
            <div class="step-desc">系統將為您規劃前往起點的路線，請沿著導航前進</div>
            <div class="step-arrow">↓</div>
        </div>
    `;
    
    const originalDrawToStart = drawToStart;
    navBtn.onclick = function() {
        originalDrawToStart();
        navBtn.classList.remove('tutorial-highlight');
        tutorialStep = 2;
        setTimeout(() => showTutorialStep2(), 800);
    };
}

function showTutorialStep2() {
    const overlay = document.getElementById('tutorial-overlay');
    const recenterBtn = document.getElementById('recenter-btn');
    
    recenterBtn.classList.add('tutorial-highlight');
    
    const rect = recenterBtn.getBoundingClientRect();
    
    overlay.innerHTML = `
        <div class="tutorial-tooltip" style="top:${rect.top - 130}px; right:20px;">
            <div class="step-badge">STEP 2 / 3</div>
            <div class="step-title">點擊「返回目前位置」</div>
            <div class="step-desc">此按鈕可以隨時將地圖居中到您的位置，迷路時非常實用</div>
            <div class="step-arrow">→</div>
        </div>
    `;
    
    recenterBtn.onclick = function() {
        recenterMap();
        recenterBtn.classList.remove('tutorial-highlight');
        tutorialStep = 3;
        setTimeout(() => showTutorialStep3(), 600);
    };
}

function showTutorialStep3() {
    const overlay = document.getElementById('tutorial-overlay');
    const gpsBox = document.getElementById('gps-box');
    
    gpsBox.classList.add('tutorial-highlight');
    
    const rect = gpsBox.getBoundingClientRect();
    
    overlay.innerHTML = `
        <div class="tutorial-tooltip" style="bottom:${window.innerHeight - rect.top + 15}px; left:${Math.max(10, rect.left - 80)}px;">
            <div class="step-badge">STEP 3 / 3</div>
            <div class="step-title">確認 GPS 為綠色狀態</div>
            <div class="step-desc">綠色 = 訊號良好，可以開始比賽<br>黃色 = 訊號普通，建議到空曠處<br>紅色 = 訊號弱，請稍候</div>
            <div style="margin-top:12px;">
                <button onclick="finishTutorial()" style="width:100%;padding:12px;border:none;border-radius:10px;background:var(--neon-blue);color:#fff;font-size:15px;font-weight:bold;cursor:pointer;">✅ 教學完畢，祝您愉快！</button>
            </div>
        </div>
    `;
}

function finishTutorial() {
    const overlay = document.getElementById('tutorial-overlay');
    overlay.classList.remove('show');
    overlay.innerHTML = '';
    
    document.querySelectorAll('.tutorial-highlight').forEach(el => {
        el.classList.remove('tutorial-highlight');
    });
    
    tutorialActive = false;
    
    document.getElementById('nav-btn').onclick = function() { drawToStart(); };
    document.getElementById('recenter-btn').onclick = function() { recenterMap(); };
    
    speak("教學完畢，祝您愉快", "tutorial_done");
}
</script>
</body>
</html>
