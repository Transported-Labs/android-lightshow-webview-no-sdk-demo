package com.cueaudio.nosdkdemo

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class InvalidUrlError(message: String): Exception(message)

class WebViewActivity : AppCompatActivity() {
    private val cueSDKName = "cueSDK"
    private lateinit var exitButton: ImageButton
    private lateinit var webView: WebView
    private lateinit var webViewLink: WebViewLink

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_web)
        exitButton = findViewById(R.id.exitButton)
        webView = findViewById(R.id.webView)
        webViewLink = WebViewLink(this, webView)
        webView.addJavascriptInterface(webViewLink, cueSDKName)
        attachEventHandlers(webView)

        val url = intent.getStringExtra("url")
        if (url != null) {
            webView.loadUrl(url)
        }
        val isExitButtonHidden = intent.getBooleanExtra("isExitButtonHidden", false)
        exitButton.visibility = if (isExitButtonHidden) View.GONE else View.VISIBLE
        exitButton.setOnClickListener {
            webView.loadUrl("about:blank")
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // To stop the audio / video playback
        webView.loadUrl("javascript:document.location=document.location")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST,
            PermissionConstant.ASK_MICROPHONE_REQUEST,
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                val granted = (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                webViewLink.callCurPermissionRequestGranted(granted)
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun attachEventHandlers(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse != null) {
                    println("Received Http Error: ${errorResponse.reasonPhrase}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }
    }
}

