package com.pocket.xiaoli.ui.shower

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * 扫热水器机身二维码，取出设备 MAC。
 *
 * ## 为什么要有这个页面（而不是只做蓝牙）
 *
 * 蓝牙是这次接入里**唯一无法在电脑上验证**的部分：广播名（参考实现用 `KLCXKJ-Water`）、
 * Android 12 以下还要定位权限、各厂商 ROM 的扫描差异。
 * 而二维码这条路是纯确定性的：格式已知、解析可单测、拿到的 MAC 直接换 `snCode`。
 * 把「设备身份」的来源放在二维码上，就把最不确定的一环换掉了。
 *
 * ## 配色为什么写死深色
 *
 * 它永远压在相机画面上，不参与深浅模式 —— 微信/支付宝的扫码页都是这个路子。
 * 跟着主题走的话，浅色主题下白色遮罩压在取景框上会看不清。
 *
 * ## 只建一次识别器
 *
 * `MultiFormatReader` 在 `remember` 里建一次并复用。参考实现记过这个坑：
 * 早期版本**每一帧**都新建一次识别模型，白烧 CPU 还掉帧。
 */
@Composable
fun QzxyQrScanDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 已识别到就停止处理后续帧，避免重复回调
    val done = remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    // ⚠️ 只建一次，不要放进分析回调里
                    val reader = MultiFormatReader().apply {
                        setHints(
                            mapOf(
                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                DecodeHintType.TRY_HARDER to true,
                            )
                        )
                    }

                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { ia ->
                                ia.setAnalyzer(executor) { image ->
                                    if (done.value) {
                                        image.close()
                                        return@setAnalyzer
                                    }
                                    val text = runCatching { decodeQr(reader, image) }.getOrNull()
                                    image.close()
                                    if (!text.isNullOrBlank()) {
                                        done.value = true
                                        onScanned(text)
                                    }
                                }
                            }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                            )
                        }.onFailure { error = "相机启动失败：${it.message}" }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
            )

            // 取景框 + 提示（纯黑遮罩，不跟主题）
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(220.dp)
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "对准热水器机身二维码",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "二维码形如 KLCXKJ-Water,M,××××××××××××",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("需要相机权限才能扫码", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Filled.Close, "关闭", tint = Color.White)
        }
    }

    // 离开页面时释放相机，否则相机灯会一直亮着
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}

/** 把相机帧交给 ZXing 识别。YUV_420_888 → 亮度平面即可（二维码只用亮度信息）。 */
private fun decodeQr(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        data,
        plane.rowStride,
        image.height,
        0,
        0,
        plane.rowStride,
        image.height,
        false,
    )
    return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
}
