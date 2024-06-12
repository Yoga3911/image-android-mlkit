package com.interbio.precheckimagequality.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.interbio.precheckimagequality.PassedData
import com.interbio.precheckimagequality.PrecheckImageQualityConfiguration
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*


class FaceDetectAnalyzer
    : ImageAnalysis.Analyzer {
//    private lateinit var lensFacing: CameraSelector = facing
//    private lateinit var viewModel: LabelViewModel

    private val config: PrecheckImageQualityConfiguration
    val onGoodFrame: (message: String) -> Unit
    val onBadFrame: (message: String) -> Unit
    val onSuccess: (image: Bitmap) -> Unit
    val onFailed: (errorCode: Int) -> Unit

    private var label = ""
    private var imageWidth = 0f
    private var imageHeight = 0f

    private var lastImage: ImageProxy? = null
    private var lastImageBitmap: Bitmap? = null

    private var goodFrameTime: Long = 0L

    private val MODEL_PATH = "glass_mask_model.tflite"
    private val MODEL_PATH_HAT = "hat_model.tflite"
    private val LABELS_PATH = "glass_mask_labels.txt"
    private val imageConverter = ImageConverter()



    private var tflite: Interpreter? = null
    private var tfliteHat: Interpreter? = null

    private var tfImageProcessor: ImageProcessor? = null
    private val tfImageBuffer = TensorImage(DataType.FLOAT32)

    constructor(
        context: Context,
        config: PrecheckImageQualityConfiguration,
        onGoodFrame: (message: String) -> Unit,
        onBadFrame: (message: String) -> Unit,
        onSuccess: (image: Bitmap) -> Unit,
        onFailed: (errorCode: Int) -> Unit
    ) {
        this.config = config
        this.onGoodFrame = onGoodFrame
        this.onBadFrame = onBadFrame
        this.onSuccess = onSuccess
        this.onFailed = onFailed


        val thread = Thread {
            val compatList = CompatibilityList()
            val options = Interpreter.Options().apply{
                if(compatList.isDelegateSupportedOnThisDevice){
                    // if the device has a supported GPU, add the GPU delegate
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    this.addDelegate(GpuDelegate(delegateOptions))
                } else {
                    // if the GPU is not supported, run on 4 threads
                    this.setNumThreads(4)
                }
            }

            tflite = Interpreter(
                FileUtil.loadMappedFile(context, MODEL_PATH),
                options
            )

            val inputIndex = 0
            val inputShape = tflite!!.getInputTensor(inputIndex).shape()

            val tfInputSize = Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}

            tfImageProcessor = ImageProcessor.Builder()
//            .add(ResizeWithCropOrPadOp(224, 224))
                .add(
                    ResizeOp(
                        tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)
                )
//            .add(Rot90Op(imageRotationDegrees / 90))
                .add(NormalizeOp(0f, 1f))
                .build()

            tfliteHat = Interpreter(
                FileUtil.loadMappedFile(context, MODEL_PATH_HAT),
                options
            )
        }
        thread.start()
//        val cropSize = minOf(bitmapBuffer.width, bitmapBuffer.height)

    }

//    public fun setViewModel(viewModel: LabelViewModel) {
//        this.viewModel = viewModel
//    }


    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (PassedData.isProcessing) {
            lastImage = imageProxy
//            lastImageBitmap = imageProxyToBitmap(imageProxy)

            var currentTimestamp = System.currentTimeMillis()
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                imageWidth = mediaImage.width.toFloat()
                imageHeight = mediaImage.height.toFloat()
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.15f)
                    .build()

                val detector = FaceDetection.getClient(options);
                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        processFaceList(faces)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FaceDetectActivity", e.message.toString())
                        onFailedInternal(5010)
                    }.addOnCompleteListener {
//                        imageProxy.close()
//                        mediaImage.close()
                    }
            }
        }

    }

    private fun onGoodFrameInternal(message: String) {
        if(goodFrameTime == 0L) {
            goodFrameTime = System.currentTimeMillis()
        }
        if((goodFrameTime + (config.delayAutoCapture * 1000)) < System.currentTimeMillis()) {
            onSuccessInternal()
        } else {
            onGoodFrame(message)
        }
        lastImage?.close()
    }
    private fun onBadFrameInternal(message: String) {
        goodFrameTime = 0L
        lastImage?.close()
        onBadFrame(message)
    }

    private fun onFailedInternal(errorCode: Int) {
        lastImage?.close()
        onFailed(errorCode)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun onSuccessInternal() {

        if(lastImage == null) {
            return
        }


        onSuccess(imageConverter.getBitmap(lastImage!!)!!)

//        lastImage?.let { lastImage ->
////            onSuccess(rotateBitmap(lastImage.image!!.toBitmap(), lastImage.imageInfo.rotationDegrees.toFloat(), true))
//        }
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun processFaceList(faces: List<Face>): Boolean {
//        Log.e("FaceDetectActivity:", "processFaceList " + faces.toString())
        if (faces.size < 1) {
            onBadFrameInternal(config.messageNoFaceDetected)
            return false
        }
        if (faces.size > 1) {
            onBadFrameInternal(config.messageMultipleFaceDetected)
            return false
        }
        // expect only 1
        for (face in faces) {
            val bounds = face.boundingBox

            val x = bounds.left.toFloat() / imageWidth
            val y = bounds.top.toFloat() / imageHeight
            val pitch = face.headEulerAngleY // Head is rotated to the right rotY degrees
            val roll = face.headEulerAngleX // Head is tilted sideways rotZ degrees
            val yaw = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

            val rightEyeOpenProb = face.rightEyeOpenProbability
            val leftEyeOpenProb = face.leftEyeOpenProbability

//            Log.d("face position", "x: $x y: $y")
            Log.d("face position", "r: $rightEyeOpenProb l: $leftEyeOpenProb")
//                Log.d("processFaceList Left",pitch.toString() + " , " + roll.toString() + " , " + yaw.toString())
//                Log.d("processFaceList Config",config.sensitivityPitchStart.toString() + "," +
//                        config.sensitivityPitchEnd.toString() + " - " +
//                        config.sensitivityYawStart.toString() + "," +
//                        config.sensitivityYawEnd.toString() + " - " +
//                        config.sensitivityRollStart.toString() + "," +
//                        config.sensitivityRollEnd.toString())
            if(x < config.sensitivityPositionXStart) {
                onBadFrameInternal(config.messageCenterYourFace)
                return false
            }
            if(x > config.sensitivityPositionXEnd) {
                onBadFrameInternal(config.messageCenterYourFace)
                return false
            }
            if(y < config.sensitivityPositionYStart) {
                onBadFrameInternal(config.messageCenterYourFace)
                return false
            }
            if(y > config.sensitivityPositionYEnd) {
                onBadFrameInternal(config.messageCenterYourFace)
                return false
            }

            if (bounds.height() / imageHeight > config.sensitivityPositionHEnd) {
                onBadFrameInternal(config.messageMoveFaceAway)
                return false
            } else if (bounds.height() / imageHeight < config.sensitivityPositionHStart) {
                onBadFrameInternal(config.messageMoveFaceCloser)
                return false
            } else if (pitch < config.sensitivityPitchStart || pitch > config.sensitivityPitchEnd) {
                onBadFrameInternal(config.messageLookStraight)
                return false
            } else if (yaw < config.sensitivityYawStart || yaw > config.sensitivityYawEnd) {
                onBadFrameInternal(config.messageLookStraight)
                return false
            } else if (roll < config.sensitivityRollStart || roll > config.sensitivityRollEnd) {
                onBadFrameInternal(config.messageLookStraight)
                return false
            } else if (rightEyeOpenProb!! < 0.2f || leftEyeOpenProb!! < 0.2f) {
                onBadFrameInternal(config.messageEyesClosed)
                return false
            }

            val thread = Thread {
                if(tfImageProcessor == null || tflite == null) {
                    onGoodFrameInternal(config.messageKeepStill)
                    return@Thread
                }

                val tfImage =  tfImageProcessor!!.process(tfImageBuffer.apply { load(imageConverter.getBitmap(lastImage!!)!!) })

                val outputBufferGlassMask = mapOf(
                    0 to arrayOf(FloatArray(3)),
                )

                tflite!!.runForMultipleInputsOutputs(arrayOf(tfImage.buffer), outputBufferGlassMask)

                // eyeglass
                if((outputBufferGlassMask[0]?.get(0)?.get(1) ?: 0.0F) > config.sensitivityGlasses) {
                    onBadFrameInternal(config.messageGlassesDetected)
                    return@Thread
                }

                // mask
                if((outputBufferGlassMask[0]?.get(0)?.get(2) ?: 0.0F) > config.sensitivityMask) {
                    onBadFrameInternal(config.messageMaskDetected)
                    return@Thread
                }


                val outputBufferHat = mapOf(
                    0 to arrayOf(FloatArray(2)),
                )

                if(tfliteHat == null) {
                    onGoodFrameInternal(config.messageKeepStill)
                    return@Thread
                }

                tfliteHat!!.runForMultipleInputsOutputs(arrayOf(tfImage.buffer), outputBufferHat)

                Log.d("face_detect_yo", "clean " + (outputBufferHat[0]?.get(0)?.get(1) ?: 0.0F).toString())
                Log.d("face_detect_yo", "hat " + (outputBufferHat[0]?.get(0)?.get(0) ?: 0.0F).toString())

                // eyeglass
                if((outputBufferHat[0]?.get(0)?.get(0) ?: 0.0F) > config.sensitivityHat) {
                    onBadFrameInternal(config.messageHatDetected)
                    return@Thread
                }

                onGoodFrameInternal(config.messageKeepStill)
            }
            thread.start()


            return true
        }

        return true
    }


}

