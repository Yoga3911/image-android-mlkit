package com.interbio.precheckimagequality

import MyImageAnalyzer
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.interbio.precheckimagequality.core.FaceDetectAnalyzer
import com.interbio.precheckimagequality.core.RectOverlay
//import com.interbio.precheckimagequality.databinding.ActivityCaptureBinding
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CaptureActivity : AppCompatActivity() {
    private lateinit var faceDetectAnalyzer: FaceDetectAnalyzer
//        val labelViewModel: LabelViewModel by viewModels()

    private lateinit var viewFinder: PreviewView
    private lateinit var rectOverlay: RectOverlay
    private lateinit var textView: TextView
    private lateinit var checkmark: ImageView
    private lateinit var exit: ImageView

    private lateinit var cameraExecutor: ExecutorService
//        private lateinit var viewBinding: ActivityCaptureBinding

    private var lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas

    private var timeoutStarted = false

    private var imageCapture: ImageCapture? = null
    override fun onRestart() {
        super.onRestart()
        //When BACK BUTTON is pressed, the activity on the stack is restarted
        //Do what you want on the refresh procedure here
        finish()
        startActivity(intent);
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
//                Manifest.permission.RECORD_AUDIO
            ).apply {
            }.toTypedArray()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        PassedData.isProcessing = true
        viewFinder.visibility = View.VISIBLE
        rectOverlay.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
            // Preview
            val preview = Preview.Builder()
//                .setTargetResolution(Size(480, 640))
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val cameraSelector = lensFacing

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                val imageAnalyzer = ImageAnalysis.Builder()
//                    .setTargetResolution(Size(640, 480))
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalyzer.setAnalyzer(cameraExecutor, faceDetectAnalyzer)
//                                imageCapture = ImageCapture.Builder()
//                                    .build()

                imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(1080, 1440))
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer, imageCapture)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                PassedData.onError?.invoke(5001)

                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun startTimeout() {
        if(timeoutStarted) {
            return
        }
        timeoutStarted = true

        if(!PassedData.config.timeoutEnabled) {
            return
        }

        val timer = Timer()

        timer.schedule(object : TimerTask() {
            override fun run() {
                if(!PassedData.isProcessing) {
                    return
                }

                runOnUiThread {
                    PassedData.isProcessing = false
                    PassedData.onError?.invoke(5007)
                    finish()
                }
                timer.cancel()
            }
        }, (PassedData.config.timeoutTime * 1000).toLong())
    }


    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val analyzer = MyImageAnalyzer()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(analyzer.defaultTargetResolution).build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), analyzer)

        setContentView(R.layout.activity_capture)
        viewFinder = findViewById(R.id.viewFinder)
        rectOverlay = findViewById(R.id.rect_overlay)
        textView = findViewById(R.id.textView)
        checkmark = findViewById(R.id.checkmark)
        exit = findViewById(R.id.exit)
        exit.setOnClickListener {
            if(!PassedData.isProcessing) {
                return@setOnClickListener
            }

            runOnUiThread {
                PassedData.isProcessing = false
                PassedData.onError?.invoke(5001)
                finish()
            }
        }

        PassedData.isProcessing = true

        faceDetectAnalyzer = FaceDetectAnalyzer(this, PassedData.config,
            onGoodFrame = { message: String ->
                startTimeout()
                runOnUiThread {
                    textView.text = message
                    rectOverlay.setGoodFrame()
                }
            },
            onBadFrame = { message: String ->
                startTimeout()
                runOnUiThread {
                    textView.text = message
                    rectOverlay.setBadFrame()
                }
            },
            onSuccess = { image: Bitmap ->
                if(!PassedData.isProcessing) {
                    return@FaceDetectAnalyzer
                }

                PassedData.isProcessing = false

                runOnUiThread {
                    checkmark.visibility = View.VISIBLE
                }

//                Log.d("MainActivity", "imageCapture")
//                Log.d("MainActivity", imageCapture.toString())

                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(this),
                    object :  ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            //get bitmap from image
                            val matrix = Matrix()

                            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())


                            Log.d("imageCapture",
                                image.imageInfo.rotationDegrees.toFloat().toString()
                            )

//                            image.
                            val bitmap = imageProxyToBitmap(image)

                            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//                            processImage(rotatedBitmap)
                            image.close()

                            val timer = Timer()
                            timer.schedule(object : TimerTask() {
                                override fun run() {

                                    val byteArrayOutputStream = ByteArrayOutputStream()
                                    rotatedBitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        100,
                                        byteArrayOutputStream
                                    )
                                    val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
                                    val imgString = Base64.encodeToString(byteArray, Base64.DEFAULT)
                                    Log.d("CaptureActivity", imgString)
//                PassedData.isProcessing = false

                                    runOnUiThread {
                                        PassedData.onSuccess?.invoke(imgString)
                                        finish()
                                    }

                                    timer.cancel()
                                }
                            }, (PassedData.config.delayCheckmark * 1000).toLong())
//                    processImageAll(bitmap)
                            super.onCaptureSuccess(image)
                        }

                        override fun onError(exception: ImageCaptureException) {
//                            Log.d("MainActivity", "onerror")
//                            Log.d("MainActivity", exception.toString())
                            exception.printStackTrace()
                            super.onError(exception)
                        }

                    }
                )
//


            },
            onFailed = { errorCode: Int ->
                if(!PassedData.isProcessing) {
                    return@FaceDetectAnalyzer
                }


                runOnUiThread {
                    PassedData.isProcessing = false
                    PassedData.onError?.invoke(errorCode)
                    finish()
                }
            }
        );

//                viewBinding = ActivityCaptureBinding.inflate(layoutInflater)
//                setContentView(viewBinding.root)

//                PassedData.webAppInterface.setActivity(this)
        rectOverlay.setBadFrame()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

//                val labelObserver = Observer<String> { x ->
//                        // Update the UI, in this case, a TextView.
//                        textView.text = x
//                        if (x == PassedData.config.messageKeepStill) {
//                                rectOverlay.setGoodFrame()
//
//                                if(PassedData.image != null) {
//                                        val byteArrayOutputStream = ByteArrayOutputStream()
//                                        PassedData.image?.compress(
//                                                Bitmap.CompressFormat.JPEG,
//                                                100,
//                                                byteArrayOutputStream
//                                        )
//                                        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
//                                        val imgString = Base64.encodeToString(byteArray, Base64.DEFAULT)
//                                        PassedData.isProcessing = false
//                                        PassedData.onSuccess?.invoke(imgString)
//                                        finish()
//                                }
//
//                        } else {
//                                rectOverlay.setBadFrame()
//                        }
//                }

//                labelViewModel.label.observe(this, labelObserver)

    }

    override fun onBackPressed() {
        if(!PassedData.isProcessing) {
            return
        }

        runOnUiThread {
            PassedData.isProcessing = false
            PassedData.onError?.invoke(5001)
        }

//                PassedData.webview?.loadUrl("javascript:window.PrecheckInstance.closeStream()");
        super.onBackPressed()
    }
}