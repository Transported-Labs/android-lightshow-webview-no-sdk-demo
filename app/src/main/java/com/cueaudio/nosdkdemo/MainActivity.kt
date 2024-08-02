package com.cueaudio.nosdkdemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var urlEditText: EditText
    private lateinit var navigateButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEditText = findViewById(R.id.urlEditText)
        navigateButton = findViewById(R.id.navigateButton)
        navigateButton.setOnClickListener {
            val url = urlEditText.text.toString()
            if (url == "") {
                println("Empty URL is not allowed")
                return@setOnClickListener
            }
            try {
                navigateTo(url)
            } catch (e: InvalidUrlError) {
                // Show invalid URL error message
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
        val openFileButton = findViewById<Button>(R.id.openFileButton)
        openFileButton.setOnClickListener {
            navigateTo("file:///android_asset/index.html")
        }
    }

    ///Checks validity of passed URL, starts new activity, navigates to the url in embedded WebView-object
    @Throws(InvalidUrlError::class)
    fun navigateTo(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val context = this
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("isExitButtonHidden", false)
            context.startActivity(intent)
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }
}
