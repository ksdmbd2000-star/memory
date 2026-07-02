package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate

private const val TAG = "CameraOverlayScreen"

@Composable
fun CameraOverlayScreen() {
    val context = LocalContext.current

    // State to check camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "カメラ権限が拒否されました。オーバーレイ機能を利用できません。", Toast.LENGTH_LONG).show()
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionRequestView(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        })
    } else {
        CameraOverlayViewContent()
    }
}

@Composable
fun CameraPermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0xFFFFDAD4), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera Permission",
                tint = Color(0xFF410002),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "カメラへのアクセスが必要です",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF201A19),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "片付けや元に戻す作業を簡単にするために、作業前の写真を撮影し、現在のカメラ映像にうっすら重ね合わせて位置を調整する機能です。この機能を利用するにはカメラ権限が必要です。",
            fontSize = 14.sp,
            color = Color(0xFF534341),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F4C38)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("request_camera_permission_button")
        ) {
            Text("権限を許可する", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun CustomSlotChip(
    text: String,
    isSelected: Boolean,
    hasPhoto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF8F4C38) else Color(0xFFF4E0D9).copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF8F4C38) else Color(0xFFF4E0D9),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasPhoto) Icons.Default.Visibility else Icons.Default.CameraAlt,
            contentDescription = null,
            tint = if (isSelected) Color.White else Color(0xFF8F4C38),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF534341)
        )
    }
}

@Composable
fun CameraOverlayViewContent() {
    val context = LocalContext.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Selected Slot: "temporary" or "preset_1" to "preset_8"
    var selectedSlot by remember { mutableStateOf("temporary") }

    // Overlay state
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var opacity by remember { mutableFloatStateOf(0.4f) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }

    // Tilt persistence states
    val tiltPrefs = remember { context.getSharedPreferences("camera_tilt_values", Context.MODE_PRIVATE) }
    var targetPitch by remember { mutableStateOf<Float?>(null) }
    var targetRoll by remember { mutableStateOf<Float?>(null) }

    // Toggle for tilt indicator
    var isTiltIndicatorEnabled by remember { mutableStateOf(true) }

    // Real-time sensor states
    var currentPitch by remember { mutableFloatStateOf(0f) }
    var currentRoll by remember { mutableFloatStateOf(0f) }

    // Sensor registration
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]

                    // Roll (rotation in screen plane)
                    val rollRad = kotlin.math.atan2(x.toDouble(), y.toDouble())
                    val rollDeg = -Math.toDegrees(rollRad).toFloat()

                    // Pitch (inclination forward/backward)
                    val pitchRad = kotlin.math.atan2(z.toDouble(), kotlin.math.sqrt((x * x + y * y).toDouble()))
                    val pitchDeg = Math.toDegrees(pitchRad).toFloat()

                    currentRoll = rollDeg
                    currentPitch = pitchDeg
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Preset name persistence
    val sharedPrefs = remember { context.getSharedPreferences("preset_names", Context.MODE_PRIVATE) }
    val presetNames = remember {
        mutableStateMapOf<String, String>().apply {
            for (i in 1..8) {
                val name = sharedPrefs.getString("preset_$i", "プリセット $i") ?: "プリセット $i"
                put("preset_$i", name)
            }
        }
    }

    // Existing photos map
    val hasPhotoMap = remember { mutableStateMapOf<String, Boolean>() }

    // Helper to scan existing photos in cache
    val updatePhotoExistence = {
        hasPhotoMap["temporary"] = getPhotoFileForSlot(context, "temporary").exists()
        for (i in 1..8) {
            hasPhotoMap["preset_$i"] = getPhotoFileForSlot(context, "preset_$i").exists()
        }
    }

    // Initialize photo existence map
    LaunchedEffect(Unit) {
        updatePhotoExistence()
    }

    // Reload photo and tilt values whenever selectedSlot changes
    LaunchedEffect(selectedSlot) {
        try {
            val photoFile = getPhotoFileForSlot(context, selectedSlot)
            if (photoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bitmap != null) {
                    capturedBitmap = rotateBitmapIfRequired(bitmap, photoFile.absolutePath)
                } else {
                    capturedBitmap = null
                }
            } else {
                capturedBitmap = null
            }

            // Load target tilt
            if (tiltPrefs.contains("${selectedSlot}_pitch")) {
                targetPitch = tiltPrefs.getFloat("${selectedSlot}_pitch", 0f)
                targetRoll = tiltPrefs.getFloat("${selectedSlot}_roll", 0f)
            } else {
                targetPitch = null
                targetRoll = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading photo/tilt for slot $selectedSlot", e)
            capturedBitmap = null
            targetPitch = null
            targetRoll = null
        }
    }

    // Rename dialog state
    var renamePresetTarget by remember { mutableStateOf<String?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upper Description Banner - Very compact
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4E0D9).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color(0xFF8F4C38),
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "片付け・配置復元アシスタント",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF201A19)
                    )
                    Text(
                        text = "「今回だけ」か「登録プリセット」を選び、写真を重ね合わせてぴったり戻しましょう！",
                        fontSize = 10.sp,
                        color = Color(0xFF534341)
                    )
                }
            }
        }

        // Slot Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomSlotChip(
                text = "今回だけ",
                isSelected = selectedSlot == "temporary",
                hasPhoto = hasPhotoMap["temporary"] == true,
                onClick = { selectedSlot = "temporary" },
                modifier = Modifier.testTag("slot_chip_temporary")
            )

            for (i in 1..8) {
                val slotId = "preset_$i"
                val name = presetNames[slotId] ?: "プリセット $i"
                CustomSlotChip(
                    text = name,
                    isSelected = selectedSlot == slotId,
                    hasPhoto = hasPhotoMap[slotId] == true,
                    onClick = { selectedSlot = slotId },
                    modifier = Modifier.testTag("slot_chip_preset_$i")
                )
            }
        }

        // Camera viewfinder and overlay box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Color(0xFFF4E0D9), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Live Camera Viewfinder
            CameraPreview(
                imageCapture = imageCapture,
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid Image
            val currentBitmap = capturedBitmap
            if (currentBitmap != null && isOverlayVisible) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Alignment Overlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(opacity)
                )
            }

            // Tilt Level Indicator Overlay
            if (isTiltIndicatorEnabled) {
                TiltIndicator(
                    currentRoll = currentRoll,
                    currentPitch = currentPitch,
                    targetRoll = targetRoll,
                    targetPitch = targetPitch,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .testTag("tilt_indicator_hud")
                )
            }

            // Top-Left Glass Toggles
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { isTiltIndicatorEnabled = !isTiltIndicatorEnabled }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("toggle_tilt_indicator_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isTiltIndicatorEnabled) Color(0xFF00E676) else Color.Gray)
                        )
                        Text(
                            text = "水準器 ${if (isTiltIndicatorEnabled) "ON" else "OFF"}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Capture indicator / Loading Spinner
            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Controls Area
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentBitmap = capturedBitmap
                val slotDisplayName = if (selectedSlot == "temporary") "今回だけ" else (presetNames[selectedSlot] ?: "プリセット")

                if (currentBitmap == null) {
                    // Stage 1: No photo captured yet for the selected slot
                    Text(
                        text = "「$slotDisplayName」の配置写真を撮影してください",
                        fontSize = 12.sp,
                        color = Color(0xFF534341),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedSlot != "temporary") {
                            OutlinedButton(
                                onClick = {
                                    renamePresetTarget = selectedSlot
                                    renameInputText = presetNames[selectedSlot] ?: ""
                                },
                                border = BorderStroke(1.dp, Color(0xFF8F4C38)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8F4C38)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("rename_preset_button_empty")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                    Text("名前変更", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isCapturing = true
                                capturePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    selectedSlot = selectedSlot,
                                    onImageCaptured = { bitmap ->
                                        capturedBitmap = bitmap
                                        isCapturing = false
                                        updatePhotoExistence()

                                        // Save tilt values
                                        tiltPrefs.edit().apply {
                                            putFloat("${selectedSlot}_pitch", currentPitch)
                                            putFloat("${selectedSlot}_roll", currentRoll)
                                            apply()
                                        }
                                        targetPitch = currentPitch
                                        targetRoll = currentRoll
                                    },
                                    onError = { exc ->
                                        isCapturing = false
                                        Toast.makeText(context, "撮影エラー: ${exc.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isCapturing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F4C38)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(if (selectedSlot != "temporary") 1.2f else 2f)
                                .height(44.dp)
                                .testTag("take_before_photo_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("写真を撮影", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    // Stage 2: Photo captured, show slider and options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Opacity Icon",
                                tint = Color(0xFF8F4C38),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "「$slotDisplayName」重ね合わせ: ${(opacity * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF201A19)
                            )
                        }

                        // Toggle Overlay Visibility
                        IconButton(
                            onClick = { isOverlayVisible = !isOverlayVisible },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("toggle_overlay_visibility")
                        ) {
                            Icon(
                                imageVector = if (isOverlayVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = Color(0xFF8F4C38),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Opacity Slider
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8F4C38),
                            activeTrackColor = Color(0xFF8F4C38),
                            inactiveTrackColor = Color(0xFFF4E0D9)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("opacity_slider")
                    )

                    // Reset, rename, and compare buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clear/Delete photo
                        OutlinedButton(
                            onClick = {
                                try {
                                    val photoFile = getPhotoFileForSlot(context, selectedSlot)
                                    if (photoFile.exists()) {
                                        photoFile.delete()
                                    }
                                    capturedBitmap = null
                                    updatePhotoExistence()

                                    // Clear tilt values
                                    tiltPrefs.edit().apply {
                                        remove("${selectedSlot}_pitch")
                                        remove("${selectedSlot}_roll")
                                        apply()
                                    }
                                    targetPitch = null
                                    targetRoll = null
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error deleting photo", e)
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF8F4C38)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8F4C38)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("clear_alignment_photo")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                Text("クリア", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Rename button (for preset slots)
                        if (selectedSlot != "temporary") {
                            OutlinedButton(
                                onClick = {
                                    renamePresetTarget = selectedSlot
                                    renameInputText = presetNames[selectedSlot] ?: ""
                                },
                                border = BorderStroke(1.dp, Color(0xFF8F4C38)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8F4C38)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("rename_preset_button_filled")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                    Text("名前変更", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Retake photo
                        Button(
                            onClick = {
                                isCapturing = true
                                capturePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    selectedSlot = selectedSlot,
                                    onImageCaptured = { bitmap ->
                                        capturedBitmap = bitmap
                                        isCapturing = false
                                        updatePhotoExistence()

                                        // Save tilt values
                                        tiltPrefs.edit().apply {
                                            putFloat("${selectedSlot}_pitch", currentPitch)
                                            putFloat("${selectedSlot}_roll", currentRoll)
                                            apply()
                                        }
                                        targetPitch = currentPitch
                                        targetRoll = currentRoll
                                    },
                                    onError = { exc ->
                                        isCapturing = false
                                        Toast.makeText(context, "撮影エラー: ${exc.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isCapturing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F4C38)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(if (selectedSlot != "temporary") 1.1f else 1.5f)
                                .height(44.dp)
                                .testTag("retake_alignment_photo")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.size(16.dp))
                                Text("再撮影", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (renamePresetTarget != null) {
        val target = renamePresetTarget!!
        AlertDialog(
            onDismissRequest = { renamePresetTarget = null },
            title = { Text("プリセット名の変更", fontWeight = FontWeight.Bold, color = Color(0xFF201A19)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "このプリセットに登録する場所や物の名前を入力してください（例：食器棚、本棚、冷蔵庫など）",
                        fontSize = 13.sp,
                        color = Color(0xFF534341)
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("場所・物の名前") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8F4C38),
                            focusedLabelColor = Color(0xFF8F4C38),
                            cursorColor = Color(0xFF8F4C38)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_preset_text_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInputText.trim()
                        if (trimmed.isNotEmpty()) {
                            sharedPrefs.edit().putString(target, trimmed).apply()
                            presetNames[target] = trimmed
                        }
                        renamePresetTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F4C38))
                ) {
                    Text("保存", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamePresetTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8F4C38))
                ) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}

private fun getPhotoFileForSlot(context: Context, slot: String): File {
    val fileName = when (slot) {
        "temporary" -> "temporary_photo.jpg"
        else -> "${slot}_photo.jpg" // e.g. preset_1_photo.jpg
    }
    return File(context.cacheDir, fileName)
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    selectedSlot: String,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = getPhotoFileForSlot(context, selectedSlot)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        val rotatedBitmap = rotateBitmapIfRequired(bitmap, photoFile.absolutePath)
                        onImageCaptured(rotatedBitmap)
                    } else {
                        onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Failed to decode bitmap", null))
                    }
                } catch (e: Exception) {
                    onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Failed to decode captured photo: ${e.message}", e))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

private fun rotateBitmapIfRequired(img: Bitmap, path: String): Bitmap {
    val ei = ExifInterface(path)
    val orientation = ei.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
        else -> img
    }
}

private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degree)
    val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    img.recycle()
    return rotatedImg
}

@Composable
fun TiltIndicator(
    currentRoll: Float,
    currentPitch: Float,
    targetRoll: Float?,
    targetPitch: Float?,
    modifier: Modifier = Modifier
) {
    val refRoll = targetRoll ?: 0f
    val refPitch = targetPitch ?: 0f
    val hasTarget = targetRoll != null && targetPitch != null

    val rollDiff = currentRoll - refRoll
    val pitchDiff = currentPitch - refPitch

    val isMatched = if (hasTarget) {
        kotlin.math.abs(rollDiff) < 1.5f && kotlin.math.abs(pitchDiff) < 1.5f
    } else {
        // Without a target, matching means phone is perfectly level (horizontal/upright)
        kotlin.math.abs(currentRoll) < 1.5f && kotlin.math.abs(currentPitch) < 1.5f
    }

    Box(
        modifier = modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .border(
                width = 2.dp,
                color = if (isMatched) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val lineLength = size.width * 0.75f

            // 1. Vertical reference axis (black/grey line)
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(center.x, 12f),
                end = Offset(center.x, size.height - 12f),
                strokeWidth = 2f
            )

            // 2. Horizontal reference axis
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(12f, center.y),
                end = Offset(size.width - 12f, center.y),
                strokeWidth = 1.5f
            )

            // 3. Draw Target line (Red) if target exists
            if (hasTarget) {
                // Target is fixed at the center of our relative HUD
                rotate(degrees = refRoll, pivot = center) {
                    drawLine(
                        color = Color(0xFFE53935), // Target Red
                        start = Offset(center.x - lineLength / 2, center.y),
                        end = Offset(center.x + lineLength / 2, center.y),
                        strokeWidth = 3f
                    )
                }
            }

            // 4. Draw Current line (Green)
            // Pitch offset determines vertical position of pivot
            // Let's scale pitch: 1 degree = 1.5 pixels. Max offset clamped to 30 pixels.
            val rawPitchOffset = (currentPitch - refPitch) * 1.5f
            val pitchOffset = rawPitchOffset.coerceIn(-30f, 30f)
            val currentPivot = Offset(center.x, center.y + pitchOffset)

            rotate(degrees = currentRoll, pivot = currentPivot) {
                drawLine(
                    color = if (isMatched) Color(0xFF00E676) else Color(0xFF4CAF50), // Neon Green if matched, standard green otherwise
                    start = Offset(currentPivot.x - lineLength / 2, currentPivot.y),
                    end = Offset(currentPivot.x + lineLength / 2, currentPivot.y),
                    strokeWidth = 4f
                )
            }

            // Draw a small center dot
            drawCircle(
                color = if (isMatched) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                radius = 4f,
                center = center
            )
        }

        // Overlay tiny text to guide user
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isMatched) "ピッタリ！" else if (hasTarget) "角度合わせ" else "水平・垂直",
                color = if (isMatched) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasTarget) {
                    "Roll: ${rollDiff.toInt()}° P: ${pitchDiff.toInt()}°"
                } else {
                    "R: ${currentRoll.toInt()}° P: ${currentPitch.toInt()}°"
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 7.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
