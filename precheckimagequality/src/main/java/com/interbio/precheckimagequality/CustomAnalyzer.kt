import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class MyImageAnalyzer : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        image.close()
    }
}
