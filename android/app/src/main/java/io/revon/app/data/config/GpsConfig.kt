package io.revon.app.data.config

import android.content.Context
import android.graphics.Color

object GpsConfig {
    const val MODE_STANDARD = 0
    const val MODE_TEST = 1

    const val GATE_ALGO_SEGMENT = 0
    const val GATE_ALGO_BEARING = 1
    const val GATE_ALGO_GYRO_DR = 2

    private const val PREF_NAME = "gps_config"
    private const val KEY_MODE = "gps_mode"
    private const val KEY_COLOR = "track_color"
    private const val KEY_ACCIDENTAL = "accidental_save"
    private const val KEY_HIGH_FPS = "high_fps"
    private const val KEY_MAP_STYLE = "map_style"
    private const val KEY_SHOW_DOTS = "show_dots"
    private const val KEY_SENSOR_ASSIST = "sensor_assist_gps"
    private const val KEY_INTERPOLATE = "inter_mode"
    private const val KEY_INTER_TYPE = "inter_type"
    private const val KEY_AUDIO_CUES = "audio_cues"
    private const val KEY_VEHICLE_MODEL = "vehicle_model"
    private const val KEY_VEHICLE_TYPE = "vehicle_type" // 0: Bike, 1: Car
    private const val KEY_TIRE_FRONT = "tire_front"
    private const val KEY_TIRE_REAR = "tire_rear"
    private const val KEY_GRAVITY_X = "gravity_x"
    private const val KEY_GRAVITY_Y = "gravity_y"
    private const val KEY_GRAVITY_Z = "gravity_z"
    private const val KEY_TRIGGER_DEBUG = "trigger_debug"
    private const val KEY_FULL_SENSOR_LOG = "full_sensor_log"
    private const val KEY_TRIGGER_MODE = "trigger_mode" // 觸發模式
    private const val KEY_PREDICT_UNLOCK = "sensor_predict_unlock"
    private const val KEY_SPEED_COLOR_MODE = "speed_color_mode"
    private const val KEY_MAP_ENGINE = "map_engine"
    private const val KEY_3D_MAP_GYRO = "3d_map_gyro"

    const val MAP_ENGINE_OSMDROID = 0
    const val MAP_ENGINE_GOOGLE = 1
    const val MAP_ENGINE_GOOGLE_3D = 2

    fun setMapEngine(context: Context, engine: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MAP_ENGINE, engine).apply()
    }
    fun getMapEngine(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_MAP_ENGINE, MAP_ENGINE_GOOGLE)

    fun set3DMapGyroEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_3D_MAP_GYRO, enabled).apply()
    }
    fun is3DMapGyroEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_3D_MAP_GYRO, false)

    const val VEHICLE_BIKE = 0
    const val VEHICLE_CAR = 1

    const val TRIGGER_MODE_RADIUS = 0
    const val TRIGGER_MODE_VECTOR = 1

    const val SPEED_COLOR_FIXED = 0   // 固定速域：以 120 km/h 為滿格
    const val SPEED_COLOR_DYNAMIC = 1 // 動態速域：以本次紀錄的 min/max 自動縮放

    const val MAP_STYLE_DARK = 0
    const val MAP_STYLE_MINIMAL = 1

    const val INTER_OFF = 0
    const val INTER_2PLUS1 = 1
    const val INTER_2PLUS2 = 2

    const val INTER_TYPE_LINEAR = 0
    const val INTER_TYPE_CURVE = 1

    var gravityBaseline: FloatArray? = null
    var isBaselineFresh: Boolean = false // 紀錄是否為本次 Session 產生的校正

    // 數據 Session 指標
    var sessionMaxG = 0.0      // 總 G 力最大值
    var maxAccelG = 0.0       // 縱向最大加速度 (負 Z)
    var maxBrakingG = 0.0     // 縱向最大減速度 (正 Z)

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, mode).apply()
    }
    fun getMode(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_MODE, MODE_STANDARD)

    fun setTrackColor(context: Context, color: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_COLOR, color).apply()
    }
    fun getTrackColor(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_COLOR, Color.WHITE)

    fun setAccidentalSaveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACCIDENTAL, enabled).apply()
    }
    fun isAccidentalSaveEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ACCIDENTAL, true)

    fun setHighFpsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIGH_FPS, enabled).apply()
    }
    fun isHighFpsEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HIGH_FPS, false)

    fun setMapStyle(context: Context, style: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MAP_STYLE, style).apply()
    }
    fun getMapStyle(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_MAP_STYLE, MAP_STYLE_DARK)

    fun setShowTrackDots(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_DOTS, enabled).apply()
    }
    fun isShowTrackDots(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_DOTS, false)

    fun setSensorAssistGpsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SENSOR_ASSIST, enabled).apply()
    }
    fun isSensorAssistGpsEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SENSOR_ASSIST, true)

    fun setInterpolationMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_INTERPOLATE, mode).apply()
    }
    fun getInterpolationMode(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_INTERPOLATE, INTER_OFF)

    fun setInterpolationType(context: Context, type: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_INTER_TYPE, type).apply()
    }
    fun getInterpolationType(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_INTER_TYPE, INTER_TYPE_LINEAR)

    fun setAudioCuesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUDIO_CUES, enabled).apply()
    }
    fun isAudioCuesEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUDIO_CUES, true)

    fun setVehicleModel(context: Context, model: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_VEHICLE_MODEL, model).apply()
    }
    fun getVehicleModel(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_VEHICLE_MODEL, "") ?: ""

    fun setVehicleType(context: Context, type: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_VEHICLE_TYPE, type).apply()
    }
    fun getVehicleType(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_VEHICLE_TYPE, VEHICLE_BIKE)

    fun setTireFront(context: Context, model: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TIRE_FRONT, model).apply()
    }
    fun getTireFront(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_TIRE_FRONT, "") ?: ""

    fun setTireRear(context: Context, model: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TIRE_REAR, model).apply()
    }
    fun getTireRear(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_TIRE_REAR, "") ?: ""

    fun setTriggerDebugEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TRIGGER_DEBUG, enabled).apply()
    }
    fun isTriggerDebugEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TRIGGER_DEBUG, false)

    fun setFullSensorLogEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_FULL_SENSOR_LOG, enabled).apply()
    }
    fun isFullSensorLogEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FULL_SENSOR_LOG, false)

    fun setTriggerMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_TRIGGER_MODE, mode).apply()
    }
    fun getTriggerMode(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_TRIGGER_MODE, TRIGGER_MODE_VECTOR)

    fun setSensorPredictUnlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PREDICT_UNLOCK, enabled).apply()
    }
    fun isSensorPredictUnlockEnabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PREDICT_UNLOCK, false)

    fun setSpeedColorMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_SPEED_COLOR_MODE, mode).apply()
    }
    fun getSpeedColorMode(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_SPEED_COLOR_MODE, SPEED_COLOR_FIXED)

    fun saveGravityBaseline(context: Context, baseline: FloatArray) {
        gravityBaseline = baseline
        isBaselineFresh = true
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_GRAVITY_X, baseline[0])
            .putFloat(KEY_GRAVITY_Y, baseline[1])
            .putFloat(KEY_GRAVITY_Z, baseline[2])
            .apply()
    }

    fun loadGravityBaseline(context: Context): FloatArray? {
        if (gravityBaseline != null) return gravityBaseline
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_GRAVITY_X)) return null

        val x = prefs.getFloat(KEY_GRAVITY_X, 0f)
        val y = prefs.getFloat(KEY_GRAVITY_Y, 0f)
        val z = prefs.getFloat(KEY_GRAVITY_Z, 0f)

        gravityBaseline = floatArrayOf(x, y, z)
        isBaselineFresh = false
        return gravityBaseline
    }
}
