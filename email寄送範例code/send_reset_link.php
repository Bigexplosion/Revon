<?php
ini_set('display_errors', 1);
error_reporting(E_ALL);

/* ===== 建立資料庫連線 ===== */
$host = "localhost";
$db   = "revon";
$user = "root";
$pass = "Rev_on1028";

$conn = new mysqli($host, $user, $pass, $db);
if($conn->connect_error){
    die("資料庫連線失敗: " . $conn->connect_error);
}
$conn->set_charset("utf8mb4");

/* ===== 取得 Email ===== */
$email = $_POST['email'] ?? '';
if(!$email){
    die("Email錯誤");
}

/* ===== 檢查是否存在 ===== */
$stmt = $conn->prepare("SELECT id FROM users WHERE email=?");
$stmt->bind_param("s",$email);
$stmt->execute();
$result = $stmt->get_result();

if($result->num_rows==0){
    die("Email不存在");
}

$user = $result->fetch_assoc();

/* ===== 產生 token ===== */
$token = bin2hex(random_bytes(32));
$expire = date("Y-m-d H:i:s", time()+3600); // 1 小時有效

/* ===== 存入資料庫 ===== */
$stmt = $conn->prepare("UPDATE users SET reset_token=?, token_expire=? WHERE id=?");
$stmt->bind_param("ssi", $token, $expire, $user['id']);
$stmt->execute();

/* ===== 建立連結 ===== */
$link = "https://revon.tw/reset_password.php?token=".$token;

/* ===== 寄信 ===== */
$subject = "重設密碼";
$message = "點下面連結重設密碼：\n\n$link\n\n有效時間 1 小時";
$headers = "From: noreply@revon.tw";

if(mail($email,$subject,$message,$headers)){
    echo "已寄送重設信件";
}else{
    echo "寄信失敗，請檢查 NAS SMTP 設定";
}
?>