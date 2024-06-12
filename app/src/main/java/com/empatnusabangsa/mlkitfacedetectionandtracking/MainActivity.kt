package com.empatnusabangsa.mlkitfacedetectionandtracking

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.interbio.precheckimagequality.PassedData
import com.interbio.precheckimagequality.PrecheckImageQuality
import com.interbio.precheckimagequality.PrecheckImageQualityConfiguration


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var precheckImageQualityConfiguration = PrecheckImageQualityConfiguration()
        precheckImageQualityConfiguration.license = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2Nzg5NjI0NDgsImV4cCI6MTY4NjczODQ0OCwiYXVkIjoiYWxsX2FwcHMiLCJpc3MiOiJ0cnVzdGxpbmsiLCJzdWIiOiJpb3MsYW5kcm9pZCx3ZWIifQ.iSeAUtx95T5GOVeeSnIytU8XTyn4J22z_aXCo6zzPBzA23T4CGuz-U2GAb9mrw49d8G-uFqLEzSM-ReslhKKgENl8duvUERdLMTZZxMoYwXqi9zJI_QFyQfsVHbDYj3OolSOpuP7ZJAIXXchM0V9kThpg7FUIUL-QimUDBdLW82AGAaNsZgE1qHrGuQ5oirO-hPehdNZmBcX5BwqugoI9-p5rqn7HgiwVY4ZdJ_nhANAaENvK1w90c4LDHiUIx50XCcN8mnztNWoICeOVeoLnkyfWMxasOER9bHvXrtSD7O-J7ddDLcLKfvs4vyUKvBfasw7S12sQFbR_QIyqyxJjA"
        precheckImageQualityConfiguration.messageNoFaceDetected = "Muka tidak terdeteksi"
        precheckImageQualityConfiguration.timeoutTime = 30.0
        precheckImageQualityConfiguration.sensitivityPositionHStart = 0.60
        precheckImageQualityConfiguration.sensitivityPositionHEnd = 0.90
        precheckImageQualityConfiguration.sensitivityMask = 0.80

        PrecheckImageQuality.initialize(this, precheckImageQualityConfiguration)

        findViewById<Button>(R.id.button).setOnClickListener {
            PrecheckImageQuality.startPrecheck(this, onError = {
                Log.d("MainActivity", "onError")
                Log.d("MainActivity", it.toString())
            }, onSuccess = {
//                val valueToShow = (it ?: ",").split(",")[1]
                val decodedString: ByteArray = Base64.decode(it, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                PassedData.image = decodedByte

                Log.d("MainActivity", "width: ${decodedByte.width} height: ${decodedByte.height}")

                startActivity(Intent(this, ResultActivity::class.java))
            })
        }
    }
}