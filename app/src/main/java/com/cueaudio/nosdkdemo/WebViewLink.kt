package com.cueaudio.nosdkdemo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONException
import java.util.*
import kotlin.math.roundToInt


object PermissionConstant {
    const val ASK_MICROPHONE_REQUEST = 1001
    const val ASK_CAMERA_REQUEST = 1002
    const val ASK_SAVE_PHOTO_REQUEST = 1003
}
class WebViewLink (private val mContext: Context, private val webView: WebView) {

    private val torchServiceName = "torch"
    private val vibrationServiceName = "vibration"
    private val permissionsServiceName = "permissions"
    private val onMethodName = "on"
    private val offMethodName = "off"
    private val checkIsOnMethodName = "isOn"
    private val vibrateMethodName = "vibrate"
    private val sparkleMethodName = "sparkle"
    private val advancedSparkleMethodName = "advancedSparkle"
    private val askMicMethodName = "getMicPermission"
    private val askCamMethodName = "getCameraPermission"
    private val askSavePhotoMethodName = "getSavePhotoPermission"
    private val hasMicMethodName = "hasMicPermission"
    private val hasCamMethodName = "hasCameraPermission"
    private val hasSavePhotoMethodName = "hasSavePhotoPermission"
    private val testErrorMethodName = "testError"

    private val prefsName = "preference"

    private var curRequestId: Int? = null
    private var isFlashlightOn = false

    private val cameraManager: CameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchCallback: TorchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isFlashlightOn = enabled
        }
    }

    init {
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    /// Post message from the web page
    @JavascriptInterface
    fun postMessage(message: String) {
        println("Received message from Javascript: $message")
        if (message != "") {
            val params = convertToParamsArray(message)
            processParams(params)
        }
    }

    private fun turnTorchToLevel(level: Float, isJavaScriptCallbackNeeded: Boolean = true) {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val supportedMaxLevel =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                // Check if camera supports Torch Strength Control
                if ((supportedMaxLevel != null) && (supportedMaxLevel > 1)) {
                    val strengthLevel = (supportedMaxLevel * level).roundToInt()
                    if (strengthLevel > 0) {
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel)
                    }
                    if (isJavaScriptCallbackNeeded) {
                        sendToJavaScript(null)
                    }
                } else {
                    //Simply turn torch on
                    turnTorch(true, isJavaScriptCallbackNeeded)
                }
            } else {
                //Simply turn torch on
                turnTorch(true, isJavaScriptCallbackNeeded)
            }
        } catch (e: CameraAccessException) {
            errorToJavaScript("Method turnTorchToLevel - Camera access denied: " + e.localizedMessage)
        }
    }

    private fun turnTorch(isOn: Boolean, isJavaScriptCallbackNeeded: Boolean = true) {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, isOn)
            if (isJavaScriptCallbackNeeded) {
                sendToJavaScript(null)
            }
        } catch (e: CameraAccessException) {
            errorToJavaScript("Method turnTorch - Camera access denied")
        }
    }

    private fun adjustedIntenseLevel(level: Float): Float {
        val minLevel = 0.001F
        val maxLevel = 1.0F
        return if (level < minLevel) minLevel else (if (level > maxLevel) maxLevel else level)
    }

    private fun nowMs(): Long {
        return Calendar.getInstance().timeInMillis
    }
    private fun advancedSparkle(rampUpMs: Int?, sustainMs: Int?, rampDownMs: Int?, intensity: Double?) {
        val blinkDelayMs: Long = 50
        if ((rampUpMs != null) && (sustainMs != null) && (rampDownMs != null) && (intensity != null)) {
            val totalDuration = rampUpMs + sustainMs + rampDownMs
            val intenseLevel = adjustedIntenseLevel(intensity.toFloat())
            val flashThread = Thread {
                try {
                    val rampUpStart = nowMs()
                    var currentRampUpTime: Long = 0
                    while (currentRampUpTime < rampUpMs) {
                        val upIntensity: Float = (currentRampUpTime.toFloat() / rampUpMs.toFloat()) * intenseLevel
                        turnTorchToLevel(adjustedIntenseLevel(upIntensity), false)
                        Thread.sleep(blinkDelayMs)
                        currentRampUpTime = nowMs() - rampUpStart
                    }
                    if (sustainMs > 0) {
                        turnTorchToLevel(adjustedIntenseLevel(intenseLevel), false)
                        Thread.sleep(sustainMs.toLong())
                    }
                    val rampDownStart = nowMs()
                    var currentRampDownTime: Long = 0
                    while (currentRampDownTime < rampDownMs){
                        val downIntensity = (1.0 - currentRampDownTime.toFloat() / rampDownMs.toFloat()) * intenseLevel
                        turnTorchToLevel(adjustedIntenseLevel(downIntensity.toFloat()), false)
                        Thread.sleep(blinkDelayMs)
                        currentRampDownTime = nowMs() - rampDownStart
                    }
                } catch (e: InterruptedException) {
                    print("interrupted by time: $totalDuration ms")
                }
                turnTorch(false, false)
                sendToJavaScript(null)
            }
            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
            }, totalDuration.toLong())
        } else {
            errorToJavaScript("Cannot be null rampUpMs: $rampUpMs, sustainMs: $sustainMs, rampDownMs: $rampDownMs, intensity: $intensity")
        }
    }

    private fun sparkle(duration: Int?) {
        if (duration != null) {
            val flashThread = Thread {
                var isOn = false
                var isSparkling = true
                val blinkDelay: Long = 50
                while (isSparkling) {
                    isOn = !isOn
                    turnTorch(isOn, false)
                    try {
                        Thread.sleep(blinkDelay)
                    } catch (e: InterruptedException) {
                        turnTorch(false, false)
                        isSparkling = false
                    }
                }
            }

            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
                sendToJavaScript(null)
            }, duration.toLong())
        } else {
            errorToJavaScript("Duration: $duration is not valid value")
        }
    }

    @Suppress("DEPRECATION")
    private fun makeVibration(duration: Int?) {
        if (duration != null) {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(duration.toLong())
            sendToJavaScript(null)
        } else {
            errorToJavaScript("Duration: $duration is not valid value")
        }
    }
    private fun hasPermission(requestCode: Int) {
        var permissionType = ""
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST -> {
                permissionType = Manifest.permission.CAMERA
            }
            PermissionConstant.ASK_MICROPHONE_REQUEST -> {
                permissionType = Manifest.permission.RECORD_AUDIO
            }
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Not ask for permission for Android 11+
                    sendToJavaScript(true)
                    return
                } else {
                    permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
        }
        if (permissionType != "") {
            val permission: Int =
                ContextCompat.checkSelfPermission(mContext, permissionType)
            val result = (permission == PackageManager.PERMISSION_GRANTED)
            sendToJavaScript(result)
        } else {
            errorToJavaScript("PermissionID can not be empty")
        }
    }

    private fun askForPermission(requestCode: Int) {
        var permissionType = ""
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST -> {
                permissionType = Manifest.permission.CAMERA
            }
            PermissionConstant.ASK_MICROPHONE_REQUEST -> {
                permissionType = Manifest.permission.RECORD_AUDIO
            }
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Not ask for permission for Android 11+
                    sendToJavaScript(true)
                    return
                } else {
                    permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
        }
        if (permissionType != "") {
            val permission: Int =
                ContextCompat.checkSelfPermission(mContext, permissionType)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // For CAMERA permission show custom confirmation dialog
                if (permissionType == Manifest.permission.CAMERA) {
                    if (isFirstTimeAskingPermission(permissionType) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(mContext as Activity, permissionType)
                        ) {
                        saveFirstTimeAskingPermission(permissionType, false)
                        askCameraConfirmation(permissionType, requestCode)
                    } else {
                        sendToJavaScript(false)
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        mContext as Activity, arrayOf<String>(permissionType), requestCode
                    )
                }
            } else {
                sendToJavaScript(true)
            }
        } else {
            errorToJavaScript("PermissionID can not be empty")
        }
    }

    private fun askCameraConfirmation(permissionType: String, requestCode: Int) {
        val dialogTitle: String = mContext.getString(R.string.camera_permission_title)
        val dialogMessage: String = mContext.getString(R.string.camera_permission_message)
        val buttonContinue: String = mContext.getString(R.string.button_continue)
        AlertDialog.Builder(mContext)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton(buttonContinue) { dialog, _ ->
                ActivityCompat.requestPermissions(
                    mContext as Activity, arrayOf<String>(permissionType), requestCode
                )
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun saveFirstTimeAskingPermission(permission: String, isFirstTime: Boolean) {
        val sharedPreference = mContext.getSharedPreferences(prefsName, MODE_PRIVATE)
        sharedPreference.edit().putBoolean(permission, isFirstTime).apply()
    }

    private fun isFirstTimeAskingPermission(permission: String): Boolean {
        val sharedPreference = mContext.getSharedPreferences(prefsName, MODE_PRIVATE)
        return sharedPreference.getBoolean(permission, true)
    }

    fun callCurPermissionRequestGranted(granted: Boolean) {
        sendToJavaScript(granted)
    }

    private fun checkIsTorchOn() {
        sendToJavaScript(isFlashlightOn)
    }

    private fun convertToParamsArray(source: String): JSONArray? {
        return try {
            JSONArray(source)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    // Params like 1.00 come from JSON.stringify as Int and give null as? Double
    private fun getAsDouble(param: Any?): Double? {
        var result = param as? Double
        if (result == null) {
            result = (param as? Int)?.toDouble()
        }
        return result
    }

    private fun processParams(params: JSONArray?) {
        params?.let {
            val requestId = params[0] as? Int
            if (requestId != null) {
                curRequestId = requestId
                val serviceName = params[1] as? String
                val methodName = params[2] as? String
                if ((serviceName != null) && (methodName != null)) {
                    if (serviceName == torchServiceName) {
                        when(methodName){
                            onMethodName -> {
                                if (params.length() > 3) {
                                    val level = params[3] as? Double
                                    if (level != null) {
                                        turnTorchToLevel(level.toFloat())
                                    } else {
                                        val levelInt = params[3] as? Int
                                        if (levelInt != null) {
                                            turnTorchToLevel(levelInt.toFloat())
                                        } else {
                                            errorToJavaScript("Level cannot be null")
                                        }
                                    }
                                } else {
                                    turnTorch(true)
                                }
                            }
                            offMethodName -> turnTorch(false)
                            checkIsOnMethodName -> checkIsTorchOn()
                            sparkleMethodName -> {
                                val duration = params[3] as? Int
                                sparkle(duration)
                            }
                            advancedSparkleMethodName -> {
                                if (params.length() > 6) {
                                    val rampUpMs = params[3] as? Int
                                    val sustainMs = params[4] as? Int
                                    val rampDownMs = params[5] as? Int
                                    val intensity = getAsDouble(params[6])
                                    advancedSparkle(rampUpMs, sustainMs, rampDownMs, intensity)
                                } else {
                                    errorToJavaScript("Needed more params for advancedSparkle: rampUpMs: Int, sustainMs: Int, rampDownMs: Int, intensity: Float")
                                }
                            }
                            testErrorMethodName -> errorToJavaScript("This is the test error message")
                        }
                    } else if (serviceName == vibrationServiceName) {
                        when (methodName) {
                            vibrateMethodName ->  {
                                val duration = params[3] as? Int
                                makeVibration(duration)
                            }
                        }
                    } else if (serviceName == permissionsServiceName) {
                        when (methodName) {
                            askMicMethodName ->  {
                                askForPermission(PermissionConstant.ASK_MICROPHONE_REQUEST)
                            }
                            askCamMethodName ->  {
                                askForPermission(PermissionConstant.ASK_CAMERA_REQUEST)
                            }
                            askSavePhotoMethodName ->  {
                                askForPermission(PermissionConstant.ASK_SAVE_PHOTO_REQUEST)
                            }
                            hasMicMethodName ->  {
                                hasPermission(PermissionConstant.ASK_MICROPHONE_REQUEST)
                            }
                            hasCamMethodName ->  {
                                hasPermission(PermissionConstant.ASK_CAMERA_REQUEST)
                            }
                            hasSavePhotoMethodName ->  {
                                hasPermission(PermissionConstant.ASK_SAVE_PHOTO_REQUEST)
                            }
                        }
                    } else {
                        errorToJavaScript("Only services '$torchServiceName', '$vibrationServiceName', '$permissionsServiceName' are supported")
                    }
                }
            } else {
                errorToJavaScript("No correct serviceName or/and methodName were passed")
            }
        }
    }

    private fun errorToJavaScript(errorMessage: String) {
        println(errorMessage)
        sendToJavaScript(null, errorMessage)
    }

    private fun sendToJavaScript(result: Any?, errorMessage: String = "") {
        if (curRequestId != null) {
            val params = JSONArray()
            params.put(curRequestId)
            if (result != null) {
                params.put(result)
            } else if (errorMessage != "") {
                params.put(null)
                params.put(errorMessage)
            }
            val paramData = params.toString()
            webView.post {
                val js2 = "cueSDKCallback(JSON.stringify($paramData))"
                println("Sent to Javascript: $js2")
                webView.evaluateJavascript(js2) { returnValue ->
                    println(returnValue)
                }
            }
        } else {
            println("curRequestId is null")
        }
    }
}