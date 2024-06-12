package com.interbio.precheckimagequality

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView

@SuppressLint("StaticFieldLeak")
object PassedData {
    var base64Image: String? = null
    var config: PrecheckImageQualityConfiguration = PrecheckImageQualityConfiguration()

    var onSuccess: ((image: String) -> Unit)? = null
    var onError: ((errorCode: Int) -> Unit)? = null

    var image: Bitmap? = null

    var isProcessing = false
}
