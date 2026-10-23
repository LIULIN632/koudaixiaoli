package com.pocket.xiaoli.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.pocket.xiaoli.ui.theme.LocalSkin

/** 位置二维码相机扫描弹窗；只负责取景和解码，不直接修改业务状态。 */
@Composable
fun CampusQrScannerDialog(
    onDecoded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫码识别位置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasPermission) {
                    CameraPreview(onQrDecoded = onDecoded)
                    Text(
                        text = "将位置二维码放入画面，识别后会自动填入。",
                        fontSize = 12.sp,
                    )
                } else {
                    Text("需要相机权限才能扫码。", fontSize = 13.sp)
                    Text(
                        text = "也可以复制二维码内容，回到设置页使用「粘贴位置二维码内容」。",
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (hasPermission) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            } else {
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("允许相机权限")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CameraPreview(onQrDecoded: (String) -> Unit) {
    val skin = LocalSkin.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(skin.radiusCard)),
        factory = { context ->
            val previewView = PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                    val raw = runCatching { decodeQr(imageProxy) }.getOrNull()
                                    imageProxy.close()
                                    if (raw != null && handled.compareAndSet(false, true)) {
                                        previewView.post { onQrDecoded(raw) }
                                    }
                                }
                            }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            previewView
        },
    )
}

private fun decodeQr(image: ImageProxy): String? {
    val yPlane = image.planes.firstOrNull() ?: return null
    val buffer = yPlane.buffer
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride.coerceAtLeast(1)
    val data = if (pixelStride == 1 && rowStride == width) {
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } else {
        val output = ByteArray(width * height)
        val row = ByteArray(rowStride.coerceAtLeast(width))
        var offset = 0
        for (y in 0 until height) {
            val readable = minOf(row.size, buffer.remaining())
            if (readable <= 0) break
            buffer.get(row, 0, readable)
            var x = 0
            while (x < width && offset < output.size) {
                val index = x * pixelStride
                output[offset++] = if (index < readable) row[index] else 0
                x++
            }
        }
        output
    }
    if (data.isEmpty()) return null

    return runCatching {
        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        QRCodeReader().decode(
            bitmap,
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            ),
        ).text
    }.getOrNull()
}