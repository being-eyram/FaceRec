package com.hubtel.facerec

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.hubtel.facerec.ui.theme.FaceRecTheme
import java.io.ByteArrayOutputStream


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceRecTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val permissionState = ContextCompat.checkSelfPermission(
                        LocalContext.current, android.Manifest.permission.CAMERA
                    )

                    var isPermissionGranted by remember(permissionState) {
                        mutableStateOf(permissionState == PackageManager.PERMISSION_GRANTED)
                    }

                    val permissionLauncher =
                        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(),
                            onResult = { isPermissionGranted = it })

                    val faceDimens: SnapshotStateList<FaceDimens> =
                        remember { mutableStateListOf() }

                    HomeScreen {
                        if (isPermissionGranted) {
                            CameraPreview(detectedFaceDimens = faceDimens)
                        } else {
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Launch Camera")
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun HomeScreen(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        content()
    }
}


@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    detectedFaceDimens: SnapshotStateList<FaceDimens>,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    detectedFaceDimens.forEach { faceDimen ->
                        drawRect(
                            alpha = 0.65F,
                            color = Color.White,
                            topLeft = faceDimen.topLeft,
                            size = faceDimen.size,
                        )
                    }
                },

            factory = {
                PreviewView(it).apply {
                    controller = LifecycleCameraController(context).apply {
                        bindToLifecycle(lifecycleOwner)
                        cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        setImageAnalysisAnalyzer(
                            ContextCompat.getMainExecutor(context),
                            getFaceDetectionAnalyzer(context, onDetectFace = { faceDimens ->
                                detectedFaceDimens.clear()
                                detectedFaceDimens.addAll(faceDimens)

                                takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    ImageCaptureHandler
                                )

                            })
                        )
                    }
                }
            },
        )
    }
}


private fun getFaceDetectionAnalyzer(
    context: Context,
    onDetectFace: (List<FaceDimens>) -> Unit
): MlKitAnalyzer {

    val highAccuracyOpts =
        FaceDetectorOptions.Builder().setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//      .enableTracking()
            .build()

    val faceDetector = FaceDetection.getClient(highAccuracyOpts)

    return MlKitAnalyzer(
        listOf(faceDetector),
        COORDINATE_SYSTEM_VIEW_REFERENCED,
        ContextCompat.getMainExecutor(context),
    ) { analyzerResult ->
        val detectedFaces = mutableListOf<FaceDimens>()

        analyzerResult.getValue(faceDetector)?.let { facesData ->
            val facesDimens = facesData.map {
                FaceDimens(it.boundingBox)
            }
            detectedFaces.addAll(facesDimens)
        }

        if (detectedFaces.isNotEmpty()) onDetectFace(detectedFaces)
    }
}


data class FaceDimens(
    private val boundingBox: Rect
) {
    val topLeft = boundingBox.toComposeRect().topLeft
    val size = Size(
        boundingBox.toComposeRect().width,
        boundingBox.toComposeRect().height
    )
}

fun Bitmap.encodeAsBase64String(): String? {
    val outputStream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}


object ImageCaptureHandler : ImageCapture.OnImageCapturedCallback() {
    var encodedBase64: String = ""

    override fun onCaptureSuccess(image: ImageProxy) {
        super.onCaptureSuccess(image)
        encodedBase64 = image.toBitmap().encodeAsBase64String() ?: return
        encodedBase64
        image.close()
    }

    override fun onError(exception: ImageCaptureException) {
        super.onError(exception)
    }
}