package com.example.qstart

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import java.util.Locale
import kotlin.math.acos
import kotlin.math.sqrt

class DragRaceFragment : Fragment(), SensorEventListener {

    enum class Mode(val displayName: String) {
        SPEED_0_100("0-100 km/h"),
        DIST_400M("400m"),
        CUSTOM("自訂模式")
    }

    enum class CustomUnit { SPEED, DISTANCE }

    private lateinit var dragRaceRoot: ConstraintLayout
    private lateinit var speedText: TextView
    private lateinit var lastTimeText: TextView
    private lateinit var txtCurrentDistance: TextView
    private lateinit var startBtn: Button
    private lateinit var modeTitle: TextView
    private lateinit var sessionLabel: TextView
    private lateinit var speedMeterContainer: View
    private lateinit var resultTable: TableLayout
    private lateinit var resultTableScroll: View
    private lateinit var header1: TextView
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var trackManager: TrackManager
    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
    
    private var currentMode = Mode.SPEED_0_100
    private var isRacing = false
    private var startTime = 0L
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    
    // 自訂模式參數
    private var customTarget = 60
    private var customUnit = CustomUnit.SPEED
    
    private val handler = Handler(Looper.getMainLooper())
    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val distanceMilestones = mutableListOf<Int>()
    private val speedMilestones = mutableListOf<Int>()
    private val capturedMilestones = mutableMapOf<Int, Pair<Double, Int>>() 
    private var lastAutoSaveTime = 0L 

    private var gravityBaseline: FloatArray? = null
    private var currentLeanAngle = 0.0
    private val alpha = 0.1f
    private var filteredG = floatArrayOf(0f, 0f, 0f)
    private var currentGForce = 0.0
    private var currentAccelG = 0.0
    private var currentBrakingG = 0.0
    private var lastRawAccel = floatArrayOf(0f, 0f, 0f)

    // Sensor Fusion 變數
    private var gyroSensor: Sensor? = null
    private var lastGyroTime = 0L
    private var dynamicPitch = 0.0
    private var lastGpsSpeedKmh = 0.0
    private var lastGpsTime = 0L
    private var fusedLongG = 0.0
    private var lastGpsAccel = 0.0
    private var currentImuG = 0.0

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRacing) {
                val now = System.currentTimeMillis()
                val elapsed = (now - startTime) / 1000.0
                lastTimeText.text = String.format(Locale.getDefault(), "%.3fs", elapsed)
                
                // --- 實作高頻距離推算 ---
                var displayDistance = totalDistance.toDouble()
                val loc = lastLocation
                if (loc != null && GpsConfig.isSensorAssistGpsEnabled(requireContext())) {
                    val dt = (now - loc.time) / 1000.0
                    if (dt > 0 && dt < 1.5) {
                        // a = (加速G - 煞車G) * 9.80665
                        val a = (currentAccelG - currentBrakingG) * 9.80665
                        val v0 = loc.speed.toDouble()
                        val extraDisplacement = v0 * dt + 0.5 * a * dt * dt
                        displayDistance += extraDisplacement
                    }
                }
                txtCurrentDistance.text = String.format(Locale.getDefault(), "%dm", displayDistance.toInt())
                
                sessionLabel.text = when (currentMode) {
                    Mode.DIST_400M -> String.format(Locale.getDefault(), "RACING... %.1fm / 400m", displayDistance)
                    Mode.CUSTOM -> {
                        val unitStr = if (customUnit == CustomUnit.SPEED) "km/h" else "m"
                        "RACING... 目標: $customTarget $unitStr"
                    }
                    else -> "RACING..."
                }
                handler.postDelayed(this, 30)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_drag_race, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        GpsConfig.loadGravityBaseline(requireContext())
        
        dragRaceRoot = view.findViewById(R.id.dragRaceRoot)
        speedText = view.findViewById(R.id.bigSpeedText)
        lastTimeText = view.findViewById(R.id.lastTimeText)
        txtCurrentDistance = view.findViewById(R.id.txtCurrentDistance)
        startBtn = view.findViewById(R.id.startBtn)
        modeTitle = view.findViewById(R.id.txtSelectedMode)
        sessionLabel = view.findViewById(R.id.txtSessionLabel)
        speedMeterContainer = view.findViewById(R.id.speedMeterContainer)
        resultTable = view.findViewById(R.id.resultTable)
        resultTableScroll = view.findViewById(R.id.resultTableScroll)
        header1 = view.findViewById(R.id.header1)

        trackManager = TrackManager(requireContext())
        checkForRecovery()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        sensorManager = requireContext().getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        setupModeButtons(view)
        requestLocationUpdates()

        startBtn.setOnClickListener {
            when (startBtn.text) {
                "GO", "RETRY" -> prepareRace()
                else -> resetRace()
            }
        }
        
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun setupModeButtons(view: View) {
        view.findViewById<Button>(R.id.btn0100).setOnClickListener { setMode(Mode.SPEED_0_100) }
        view.findViewById<Button>(R.id.btn400m).setOnClickListener { setMode(Mode.DIST_400M) }
        view.findViewById<Button>(R.id.btnCustom).setOnClickListener { showCustomConfigDialog() }
    }

    private fun showCustomConfigDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        
        val input = EditText(requireContext()).apply {
            hint = "輸入目標數值"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(customTarget.toString())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        
        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }
        val rbSpeed = RadioButton(requireContext()).apply { text = "km/h"; id = View.generateViewId(); setTextColor(Color.WHITE) }
        val rbDist = RadioButton(requireContext()).apply { text = "m"; id = View.generateViewId(); setTextColor(Color.WHITE) }
        radioGroup.addView(rbSpeed); radioGroup.addView(rbDist)
        
        if (customUnit == CustomUnit.SPEED) rbSpeed.isChecked = true else rbDist.isChecked = true
        
        container.addView(input)
        container.addView(radioGroup)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("自訂測試目標")
            .setView(container)
            .setPositiveButton("設定") { _, _ ->
                var value = input.text.toString().toIntOrNull() ?: 60
                if (value <= 0) {
                    Toast.makeText(requireContext(), "自訂數值不可為 0，已重置為預設", Toast.LENGTH_SHORT).show()
                    value = 60
                }
                customTarget = value
                customUnit = if (rbSpeed.isChecked) CustomUnit.SPEED else CustomUnit.DISTANCE
                setMode(Mode.CUSTOM)
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#00E5FF"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#8E8E93"))
    }

    private fun setMode(mode: Mode) {
        currentMode = mode
        if (mode == Mode.CUSTOM) {
            val unitStr = if (customUnit == CustomUnit.SPEED) "km/h" else "m"
            modeTitle.text = "自訂: $customTarget $unitStr"
        } else {
            modeTitle.text = mode.displayName
        }
        
        isRacing = false
        handler.removeCallbacks(timerRunnable)
        
        speedText.text = "0"
        speedText.textSize = 140f
        speedText.setTextColor(Color.WHITE)
        sessionLabel.text = "WAITING FOR START"
        startBtn.text = "GO"
        resultTableScroll.visibility = View.GONE
        speedMeterContainer.visibility = View.VISIBLE
        
        val constraintSet = ConstraintSet()
        constraintSet.clone(dragRaceRoot)
        constraintSet.connect(R.id.speedMeterContainer, ConstraintSet.TOP, R.id.txtSelectedMode, ConstraintSet.BOTTOM)
        constraintSet.connect(R.id.speedMeterContainer, ConstraintSet.BOTTOM, R.id.resultCard, ConstraintSet.TOP)
        constraintSet.setVerticalBias(R.id.speedMeterContainer, 0.5f)
        constraintSet.applyTo(dragRaceRoot)
    }

    private fun prepareRace() {
        // --- 核心修正：靜止啟動檢查 ---
        val currentSpeedKmh = (lastLocation?.speed ?: 0f) * 3.6f
        if (currentSpeedKmh > 1.5) {
            Toast.makeText(context ?: return, "請先完全停止車輛再啟動測試", Toast.LENGTH_SHORT).show()
            return
        }

        TransitionManager.beginDelayedTransition(dragRaceRoot)
        val constraintSet = ConstraintSet()
        constraintSet.clone(dragRaceRoot)
        constraintSet.connect(R.id.speedMeterContainer, ConstraintSet.TOP, R.id.txtSelectedMode, ConstraintSet.BOTTOM)
        constraintSet.connect(R.id.speedMeterContainer, ConstraintSet.BOTTOM, R.id.resultCard, ConstraintSet.TOP)
        constraintSet.setVerticalBias(R.id.speedMeterContainer, 0.5f)
        constraintSet.applyTo(dragRaceRoot)

        speedMeterContainer.visibility = View.VISIBLE
        speedMeterContainer.scaleX = 1f; speedMeterContainer.scaleY = 1f; resultTableScroll.visibility = View.GONE
        speedText.text = "READY"; speedText.textSize = 80f; speedText.setTextColor(Color.YELLOW)
        sessionLabel.text = "WAITING FOR START..."; lastTimeText.text = "0.00s"; txtCurrentDistance.text = "0m"
        totalDistance = 0f; lastLocation = null; currentTrackPoints.clear(); capturedMilestones.clear(); distanceMilestones.clear(); speedMilestones.clear()
        
        when (currentMode) {
            Mode.DIST_400M -> {
                header1.text = "距離(m)"
                for (i in (50..400 step 50)) distanceMilestones.add(i)
            }
            Mode.SPEED_0_100 -> {
                header1.text = "速度(km)"
                speedMilestones.addAll(listOf(50, 60, 70, 80, 90, 100))
            }
            Mode.CUSTOM -> {
                if (customUnit == CustomUnit.DISTANCE) {
                    header1.text = "距離(m)"
                    val step = customTarget / 5
                    if (step > 0) for (i in (step..customTarget step step)) distanceMilestones.add(i)
                } else {
                    header1.text = "速度(km)"
                    speedMilestones.add(customTarget)
                }
            }
        }
        startBtn.text = "CANCEL"
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { processLocation(it) }
            }
        }, Looper.getMainLooper())
    }

    private var lastToastTime = 0L

    private fun processLocation(location: Location) {
        // --- GPS 精度過濾 (Risk A) ---
        if (location.hasAccuracy() && location.accuracy > 20.0f) {
            val now = System.currentTimeMillis()
            if (now - lastToastTime > 5000) {
                val acc = location.accuracy.toInt()
                // Toast.makeText(requireContext(), "GPS 訊號微弱 (誤差 ${acc}m)，等待穩定...", Toast.LENGTH_SHORT).show()
                lastToastTime = now
            }
            if (!isRacing) return
            if (isRacing && location.accuracy > 50.0f) return
        }

        val speedDouble = location.speed * 3.6
        val speedKmhInt = speedDouble.toInt()
        
        // Sensor Fusion: GPS G-force calculation
        val currentGpsSpeedKmh = speedDouble
        val currentGpsTime = location.time
        if (lastGpsTime > 0L && currentGpsTime > lastGpsTime) {
            val dtGps = (currentGpsTime - lastGpsTime) / 1000.0
            if (dtGps > 0) {
                val gpsAccel = ((currentGpsSpeedKmh - lastGpsSpeedKmh) / 3.6) / dtGps
                // 儲存真正的絕對加速度，供高頻 IMU 迴圈計算絕對仰角
                lastGpsAccel = gpsAccel
            }
        }
        lastGpsSpeedKmh = currentGpsSpeedKmh
        lastGpsTime = currentGpsTime

        if (!isRacing && speedText.text == "READY" && speedDouble > 1.0) startRace()
        if (isRacing) {
            speedText.text = speedKmhInt.toString(); speedText.textSize = 140f
            lastLocation?.let { totalDistance += location.distanceTo(it) }; lastLocation = location
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            
            val distIter = distanceMilestones.iterator()
            while (distIter.hasNext()) {
                val m = distIter.next(); if (totalDistance >= m) { capturedMilestones[m] = Pair(elapsed, speedKmhInt); distIter.remove() }
            }
            val speedIter = speedMilestones.iterator()
            while (speedIter.hasNext()) {
                val s = speedIter.next(); if (speedDouble >= s) { capturedMilestones[s] = Pair(elapsed, speedKmhInt); speedIter.remove() }
            }
            
            val fullLog = GpsConfig.isFullSensorLogEnabled(requireContext())
            currentTrackPoints.add(TrackPoint(
                lat = location.latitude,
                lng = location.longitude,
                speed = speedDouble,
                timestamp = System.currentTimeMillis(),
                leanAngle = currentLeanAngle,
                gForce = currentGForce,
                accelG = currentAccelG,
                brakingG = currentBrakingG,
                distance = totalDistance.toDouble(),
                fx = if(fullLog) filteredG[0] else null,
                fy = if(fullLog) filteredG[1] else null,
                fz = if(fullLog) filteredG[2] else null,
                rx = if(fullLog) lastRawAccel[0] else null,
                ry = if(fullLog) lastRawAccel[1] else null,
                rz = if(fullLog) lastRawAccel[2] else null,
                gx = if(fullLog) gravityBaseline?.get(0) else null,
                gy = if(fullLog) gravityBaseline?.get(1) else null,
                gz = if(fullLog) gravityBaseline?.get(2) else null
            ))

            // 每隔 10 秒進行一次背景暫存，預防直線測試時異常斷電
            val nowTime = System.currentTimeMillis()
            if (nowTime - lastAutoSaveTime > 10000) {
                lastAutoSaveTime = nowTime
                trackManager.saveTempRecovery(ArrayList(currentTrackPoints), "drag", currentMode.displayName)
            }
            
            when (currentMode) {
                Mode.SPEED_0_100 -> if (speedDouble >= 100) finishRace()
                Mode.DIST_400M -> if (totalDistance >= 400) finishRace()
                Mode.CUSTOM -> {
                    if (customUnit == CustomUnit.SPEED && speedDouble >= customTarget) finishRace()
                    if (customUnit == CustomUnit.DISTANCE && totalDistance >= customTarget) finishRace()
                }
            }
        } else if (speedText.text != "READY") {
            speedText.text = speedKmhInt.toString(); speedText.textSize = 140f; speedText.setTextColor(Color.WHITE)
        }
    }

    private fun startRace() {
        isRacing = true; startTime = System.currentTimeMillis(); speedText.setTextColor(Color.WHITE)
        sessionLabel.text = "RACING..."; startBtn.text = "ABORT"; handler.post(timerRunnable)
        isPredictingFinish = false // 重置預測標記
    }

    private fun finishRace() {
        isRacing = false; handler.removeCallbacks(timerRunnable)
        val finalTime = (System.currentTimeMillis() - startTime) / 1000.0
        lastTimeText.text = String.format(Locale.getDefault(), "%.3fs", finalTime)
        
        if (currentMode == Mode.DIST_400M) {
            txtCurrentDistance.text = "400m"
        } else if (currentMode == Mode.CUSTOM && customUnit == CustomUnit.DISTANCE) {
            txtCurrentDistance.text = "${customTarget}m"
        } else {
            txtCurrentDistance.text = String.format(Locale.getDefault(), "%dm", totalDistance.toInt())
        }
        
        val lastKey = if (currentMode == Mode.SPEED_0_100) 100 else if (currentMode == Mode.DIST_400M) 400 else customTarget
        if (!capturedMilestones.containsKey(lastKey)) {
             capturedMilestones[lastKey] = Pair(finalTime, speedText.text.toString().toIntOrNull() ?: 0)
        }

        showResultTable()
        
        // 修正：使用更具辨識度的模式名稱作為存檔前綴
        val savePrefix = when(currentMode) {
            Mode.SPEED_0_100 -> "直線加速_0-100kmh"
            Mode.DIST_400M -> "直線加速_400m"
            Mode.CUSTOM -> {
                val unitStr = if (customUnit == CustomUnit.SPEED) "kmh" else "m"
                "直線加速_自訂_${customTarget}${unitStr}"
            }
        }
        trackManager.saveTrack(ArrayList(currentTrackPoints), savePrefix)
        trackManager.clearTempRecovery()
        
        Toast.makeText(context ?: return, "紀錄已存檔", Toast.LENGTH_SHORT).show()
        startBtn.text = "RETRY"; sessionLabel.text = "FINISHED"
    }

    private fun showResultTable() {
        val constraintSet = ConstraintSet()
        constraintSet.clone(dragRaceRoot)
        constraintSet.setVisibility(R.id.speedMeterContainer, View.GONE)
        constraintSet.connect(R.id.resultTableScroll, ConstraintSet.TOP, R.id.txtSelectedMode, ConstraintSet.BOTTOM)
        constraintSet.setMargin(R.id.resultTableScroll, ConstraintSet.TOP, 16)
        
        TransitionManager.beginDelayedTransition(dragRaceRoot)
        constraintSet.applyTo(dragRaceRoot)
        
        speedMeterContainer.visibility = View.GONE
        resultTableScroll.visibility = View.VISIBLE
        
        resultTable.removeViews(1, resultTable.childCount - 1)
        
        val thresholds = when (currentMode) {
            Mode.DIST_400M -> (50..400 step 50).toList()
            Mode.SPEED_0_100 -> listOf(50, 60, 70, 80, 90, 100)
            else -> capturedMilestones.keys.sorted()
        }

        var prevTime = 0.0
        for (t in thresholds) {
            val data = capturedMilestones[t]; val currentTime = data?.first ?: 0.0
            val row = TableRow(requireContext()); row.setPadding(0, 16, 0, 16)
            val txtTarget = TextView(requireContext()).apply { text = t.toString(); setTextColor(Color.WHITE); textSize = 14f; layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f) }
            val txtSpeed = TextView(requireContext()).apply { text = String.format(Locale.getDefault(), "%d", data?.second ?: 0); setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER; textSize = 14f; layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f) }
            val timeContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL; layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f) }
            if (prevTime > 0 && currentTime > 0) {
                val delta = currentTime - prevTime
                val txtDelta = TextView(requireContext()).apply { text = String.format(Locale.getDefault(), "(+%.3f)", delta); setTextColor(Color.CYAN); textSize = 10f; setPadding(0, 0, 8, 0) }
                timeContainer.addView(txtDelta)
            }
            val txtTime = TextView(requireContext()).apply { text = String.format(Locale.getDefault(), "%.3f", currentTime); setTextColor(Color.GREEN); textSize = 14f }
            timeContainer.addView(txtTime); row.addView(txtTarget); row.addView(txtSpeed); row.addView(timeContainer); resultTable.addView(row)
            if (currentTime > 0) prevTime = currentTime
        }
        resultTableScroll.visibility = View.VISIBLE
    }

    private fun resetRace() {
        isRacing = false
        handler.removeCallbacks(timerRunnable)
        setMode(currentMode)
        trackManager.clearTempRecovery()
    }

    private var isPredictingFinish = false

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_GRAVITY -> {
                val gravity = event.values
                if (!isRacing && gravityBaseline == null) gravityBaseline = gravity.clone()
                val baseline = gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)
                
                // 升級至相對 Roll 角演算法，隔離前後仰
                val cRoll = kotlin.math.atan2(gravity[0].toDouble(), sqrt((gravity[1]*gravity[1] + gravity[2]*gravity[2]).toDouble()))
                val bRoll = kotlin.math.atan2(baseline[0].toDouble(), sqrt((baseline[1]*baseline[1] + baseline[2]*baseline[2]).toDouble()))
                currentLeanAngle = Math.abs(Math.toDegrees(cRoll - bRoll))
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (lastGyroTime > 0) {
                    val dt = (event.timestamp - lastGyroTime) / 1000000000.0f
                    val gyroPitchRate = event.values[0] 
                    dynamicPitch += gyroPitchRate * dt
                }
                lastGyroTime = event.timestamp
            }
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) lastRawAccel = event.values.clone()
                for (i in 0..2) filteredG[i] = filteredG[i] + alpha * (event.values[i] - filteredG[i])
                
                val baseline = gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)
                
                // --- 即時重力剝離 + 虛擬軸投影算法 ---
                // 1. 使用即時重力向量定義「下方」
                val grav = gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f) // 確保有值
                // 這裡我們直接利用最近一次校準過的 gravity，在 DragRaceFragment 中，
                // 因為可能需要精確的起始姿態，我們會稍微做些修改，
                // 為了與 CircuitFragment 統一且解決 gb 異常，這裡改用真正的 real-time 姿態處理
                // 但 DragRaceFragment 並沒有存儲即時的 lastGravity，所以我用 gravityBaseline
                // 等等，DragRaceFragment 有存 lastGravity 嗎？
                // 讓我們先保留舊的 gravityBaseline，但採用 Circuit 的作法：
                val baseNorm = Math.sqrt((baseline[0] * baseline[0] + baseline[1] * baseline[1] + baseline[2] * baseline[2]).toDouble())
                val downW = doubleArrayOf(baseline[0]/baseNorm, baseline[1]/baseNorm, baseline[2]/baseNorm)
                
                val yDotDown = 0 * downW[0] + 1 * downW[1] + 0 * downW[2]
                var forwardW = doubleArrayOf(0 - yDotDown * downW[0], 1 - yDotDown * downW[1], 0 - yDotDown * downW[2])
                val fNorm = Math.sqrt(forwardW[0]*forwardW[0] + forwardW[1]*forwardW[1] + forwardW[2]*forwardW[2])
                if (fNorm > 1e-6) {
                    forwardW = doubleArrayOf(forwardW[0]/fNorm, forwardW[1]/fNorm, forwardW[2]/fNorm)
                }
                
                val rightW = doubleArrayOf(
                    forwardW[1]*downW[2] - forwardW[2]*downW[1],
                    forwardW[2]*downW[0] - forwardW[0]*downW[2],
                    forwardW[0]*downW[1] - forwardW[1]*downW[0]
                )
                
                // 從加速度計減去靜態重力基準 (因為 DragRaceFragment 依賴起步瞬間的絕對水平基準)
                val linAccX = filteredG[0] - baseline[0]
                val linAccY = filteredG[1] - baseline[1]
                val linAccZ = filteredG[2] - baseline[2]
                
                val longAccel = linAccX*forwardW[0] + linAccY*forwardW[1] + linAccZ*forwardW[2]
                
                currentImuG = longAccel / 9.80665
                fusedLongG = currentImuG
                
                // 物理判定：正值為加速，負值為煞車
                currentAccelG = if (fusedLongG > 0) fusedLongG else 0.0
                currentBrakingG = if (fusedLongG < 0) Math.abs(fusedLongG) else 0.0
                currentGForce = Math.sqrt((filteredG[0]*filteredG[0] + filteredG[1]*filteredG[1] + filteredG[2]*filteredG[2]).toDouble()) / 9.80665

                // --- 核心升級：感測器輔助 GPS 衝線預測 (針對直線加速測試) ---
                val loc = lastLocation
                if (isRacing && loc != null && GpsConfig.isSensorAssistGpsEnabled(requireContext())) {
                    val targetDistance = when(currentMode) {
                        Mode.DIST_400M -> 400.0
                        Mode.CUSTOM -> if (customUnit == CustomUnit.DISTANCE) customTarget.toDouble() else 0.0
                        else -> 0.0
                    }
                    
                    if (targetDistance > 0) {
                        val remainingDist = targetDistance - totalDistance
                        // 僅在最後 60 公尺內啟動預測
                        if (remainingDist in 0.1..60.0) {
                            val dt = (System.currentTimeMillis() - loc.time) / 1000.0
                            if (dt > 0 && dt < 1.5) {
                                val a = (currentAccelG - currentBrakingG) * 9.80665
                                val v0 = loc.speed.toDouble()
                                val displacement = v0 * dt + 0.5 * a * dt * dt
                                
                                if (displacement >= remainingDist && !isPredictingFinish) {
                                    isPredictingFinish = true
                                    finishRace()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onPause() { 
        super.onPause()
        sensorManager.unregisterListener(this) 
    }

    private fun checkForRecovery() {
        val recoveryJson = trackManager.getTempRecovery() ?: return
        val type = recoveryJson.optString("type")
        if (type != "drag") return // CircuitFragment handles circuit recovery
        val extraInfo = recoveryJson.optString("extraInfo")
        val pointsArray = recoveryJson.optJSONArray("points") ?: return
        if (pointsArray.length() == 0) return

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("偵測到未正常結束的紀錄")
            .setMessage("系統偵測到一筆直線加速紀錄（$extraInfo）因意外中斷未儲存，是否嘗試復原？")
            .setPositiveButton("復原") { _, _ ->
                val recoveredPoints = mutableListOf<TrackPoint>()
                for (i in 0 until pointsArray.length()) {
                    val obj = pointsArray.getJSONObject(i)
                    recoveredPoints.add(TrackPoint(
                        lat = obj.getDouble("lt"),
                        lng = obj.getDouble("lg"),
                        speed = obj.getDouble("s"),
                        timestamp = obj.getLong("t"),
                        leanAngle = obj.optDouble("l", 0.0),
                        latG = obj.optDouble("gl", 0.0),
                        gForce = obj.optDouble("g", 0.0),
                        accelG = obj.optDouble("ga", 0.0),
                        brakingG = obj.optDouble("gb", 0.0),
                        distance = obj.optDouble("d", 0.0),
                        fx = if (obj.has("fx")) obj.getDouble("fx").toFloat() else null,
                        fy = if (obj.has("fy")) obj.getDouble("fy").toFloat() else null,
                        fz = if (obj.has("fz")) obj.getDouble("fz").toFloat() else null,
                        rx = if (obj.has("rx")) obj.getDouble("rx").toFloat() else null,
                        ry = if (obj.has("ry")) obj.getDouble("ry").toFloat() else null,
                        rz = if (obj.has("rz")) obj.getDouble("rz").toFloat() else null
                    ))
                }
                
                trackManager.saveTrack(recoveredPoints, "復原_直線加速_${extraInfo}")
                trackManager.clearTempRecovery()
                Toast.makeText(requireContext(), "復原成功，已儲存至歷史紀錄！", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("捨棄") { _, _ ->
                trackManager.clearTempRecovery()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#00E5FF"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF5252"))
    }
}
