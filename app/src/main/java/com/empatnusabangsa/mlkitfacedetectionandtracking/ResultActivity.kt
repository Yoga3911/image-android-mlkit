package com.empatnusabangsa.mlkitfacedetectionandtracking

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.interbio.precheckimagequality.PassedData

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        findViewById<ImageView>(R.id.result).setImageBitmap(PassedData.image)
    }
}