package com.example.zst

import android.os.Bundle
import android.view.ViewTreeObserver
import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.zst.ui.theme.ZstTheme
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ContentResolver
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.TimeUnit
import android.text.format.DateUtils
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.BasicAlertDialog

// 修改消息数据类以支持不同类型的消息
sealed class ChatMessage {
    data class TextMessage(
        val content: String,
        val isFromMe: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
    
    data class ImageMessage(
        val imageUrl: String,
        val isFromMe: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
    
    data class AudioMessage(
        val audioFile: String,
        val duration: Long,
        val isFromMe: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
    
    data class TimestampMessage(
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
    
    data class VideoMessage(
        val videoUri: String,
        val thumbnailUri: String? = null,  // 视频缩略图
        val duration: Long = 0L,           // 视频时长（毫秒）
        val isFromMe: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
    
    data class FileMessage(
        val fileName: String,
        val fileUri: String,
        val fileSize: Long,
        val mimeType: String,
        val isFromMe: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
}

// 将全局状态改为可观察的状态
private var currentPlayer: MediaPlayer? = null
private var _currentPlayingFile = mutableStateOf<String?>(null)
private var _isPlayerPlaying = mutableStateOf(false)

// 修改播放语音的函数
private fun playVoice(audioFile: String) {
    try {
        if (audioFile == _currentPlayingFile.value && currentPlayer != null) {
            // 如果点击的是当前正在播放的语音
            if (_isPlayerPlaying.value) {
                // 如果正在播放，则暂停
                currentPlayer?.pause()
                _isPlayerPlaying.value = false
            } else {
                // 如果已暂停，则继续播放
                currentPlayer?.start()
                _isPlayerPlaying.value = true
            }
        } else {
            // 如果点击的是新的语音
            // 停止并释放当前播放的语音
            currentPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            
            // 创建并播放新的语音
            currentPlayer = MediaPlayer().apply {
                setDataSource(audioFile)
                prepare()
                start()
                _isPlayerPlaying.value = true
                _currentPlayingFile.value = audioFile
                setOnCompletionListener { mp ->
                    _isPlayerPlaying.value = false
                    _currentPlayingFile.value = null
                    mp.release()
                    currentPlayer = null
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // 重置所有状态
        currentPlayer?.release()
        currentPlayer = null
        _currentPlayingFile.value = null
        _isPlayerPlaying.value = false
    }
}

// 将枚举类移到函数外部
private enum class StorageAction {
    PICK_FILE
}

// 将这些变量移到类外部作为顶层属性
private var showStoragePermissionDialog by mutableStateOf(false)
private var currentDialogAction: (() -> Unit)? = null
private var currentDialogDismiss: (() -> Unit)? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZstTheme {
                ChatScreen()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        currentPlayer = null
        _currentPlayingFile.value = null
        _isPlayerPlaying.value = false
    }
    
    // 将权限检查函数改为公开的
    fun checkStoragePermissions(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 使用 Compose AlertDialog
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                showStoragePermissionDialog(
                    onConfirm = {
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            onResult(false)
                        }
                    },
                    onDismiss = {
                        onResult(false)
                    }
                )
            } else {
                onResult(true)
            }
        } else {
            onResult(true)
        }
    }
}

// 修改显示对话框的函数为顶层函数
private fun showStoragePermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    showStoragePermissionDialog = true
    currentDialogAction = onConfirm
    currentDialogDismiss = onDismiss
}

// 在 ChatScreen 中添加对话框组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragePermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 图标
                Icon(
                    painter = painterResource(id = R.drawable.ic_file),
                    contentDescription = null,
                    tint = Color(0xFF2196F3),  // Material Blue
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
                
                // 标题
                Text(
                    text = "存储权限",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF2196F3),  // Material Blue
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
                
                // 内容
                Text(
                    text = """
                        需要存储权限才能保存文件到手机存储。
                        
                        请在接下来的系统设置页面中，点击"允许访问所有文件"开关以授予权限。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF424242),  // 深灰色文本
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // 按钮行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            showStoragePermissionDialog = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF2196F3)  // Material Blue
                        )
                    ) {
                        Text(text = "取消")
                    }
                    
                    Button(
                        onClick = {
                            showStoragePermissionDialog = false
                            onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3),  // Material Blue
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Text(text = "去设置")
                    }
                }
            }
        }
    }
}

// 在 ChatScreen 函数中添加对话框的显示逻辑
@Composable
fun ChatScreen() {
    // 状态变量
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var currentStorageAction by remember { mutableStateOf<StorageAction?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<Uri?>(null) }
    var showMorePanel by remember { mutableStateOf(false) }
    val selectedImages = remember { mutableStateListOf<Uri>() }
    
    // 获取 context
    val context = LocalContext.current
    
    // 添加图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            messages = messages + ChatMessage.ImageMessage(it.toString())
        }
    }
    
    // 添加视频选择器
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { videoUri ->
            // 在协程中处理视频
            CoroutineScope(Dispatchers.IO).launch {
                // 获取视频缩略图
                val thumbnail = try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val bitmap = retriever.getFrameAtTime(0)
                    // 保存缩略图到缓存目录
                    val thumbnailFile = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, thumbnailFile.outputStream())
                    thumbnailFile.absolutePath
                } catch (e: Exception) {
                    null
                }
                
                // 获取视频时长
                val duration = try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                } catch (e: Exception) {
                    0L
                }
                
                // 在主线程中更新UI
                withContext(Dispatchers.Main) {
                    val currentTime = System.currentTimeMillis()
                    if (messages.isEmpty() || shouldAddTimestamp(messages, messages.last(), currentTime)) {
                        messages = messages + ChatMessage.TimestampMessage(currentTime)
                    }
                    messages = messages + ChatMessage.VideoMessage(
                        videoUri = videoUri.toString(),
                        thumbnailUri = thumbnail,
                        duration = duration
                    )
                }
            }
        }
    }
    
    // 添加文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val activity = context as? MainActivity
            activity?.checkStoragePermissions { granted ->
                if (granted) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val contentResolver = context.contentResolver
                        val fileName = getFileName(contentResolver, fileUri)
                        val fileSize = getFileSize(contentResolver, fileUri)
                        val mimeType = getMimeType(contentResolver, fileUri)
                        
                        // 将文件复制到外部存储
                        val directory = Environment.getExternalStorageDirectory()
                        val destFile = File(directory, "ZST/Files/$fileName")
                        destFile.parentFile?.mkdirs()
                        
                        try {
                            contentResolver.openInputStream(fileUri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            withContext(Dispatchers.Main) {
                                val currentTime = System.currentTimeMillis()
                                if (messages.isEmpty() || shouldAddTimestamp(messages, messages.last(), currentTime)) {
                                    messages = messages + ChatMessage.TimestampMessage(currentTime)
                                }
                                messages = messages + ChatMessage.FileMessage(
                                    fileName = fileName ?: "未知文件",
                                    fileUri = destFile.absolutePath,
                                    fileSize = fileSize ?: destFile.length(),
                                    mimeType = mimeType ?: "*/*"
                                )
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "文件保存失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 修改存储权限请求的处理
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 基本权限获取成功后，检查并请求完全的文件访问权限
            val activity = context as? MainActivity
            activity?.checkStoragePermissions { granted ->
                if (granted) {
                    when (currentStorageAction) {
                        StorageAction.PICK_FILE -> filePickerLauncher.launch("*/*")
                        else -> {} // 处理其他情况
                    }
                }
            }
        } else {
            // 使用 Activity 的 AlertDialog
            val activity = context as? MainActivity
            activity?.let {
                android.app.AlertDialog.Builder(it)
                    .setTitle("存储权限")
                    .setMessage("需要存储权限才能选择文件")
                    .setPositiveButton("去设置") { _, _ ->
                        try {
                            // 打开应用设页
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", it.packageName, null)
                            }
                            it.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    // 监听键盘状态
    var keyboardHeight by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val density = LocalDensity.current
    
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(view)
            val imeHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            keyboardHeight = with(density) { imeHeight.toDp().value }
            
            // 当键盘弹起时，自动收起更多面板
            if (imeHeight > 0 && showMorePanel) {
                showMorePanel = false
            }
        }
        
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    
    val animatedHeight by animateFloatAsState(
        targetValue = keyboardHeight,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = 0
        )
    )
    
    // 添加发送消息的函数
    fun sendMessage() {
        if (message.isNotBlank()) {
            val currentTime = System.currentTimeMillis()
            
            // 检查是否需要添加时间戳
            if (messages.isEmpty() || shouldAddTimestamp(messages, messages.last(), currentTime)) {
                messages = messages + ChatMessage.TimestampMessage(currentTime)
            }
            
            messages = messages + ChatMessage.TextMessage(message)
            message = ""
        }
    }
    
    // 添加权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    // 拍照URI
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // 图片选择器
    val multipleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val currentTime = System.currentTimeMillis()
            if (messages.isEmpty() || shouldAddTimestamp(messages, messages.last(), currentTime)) {
                messages = messages + ChatMessage.TimestampMessage(currentTime)
            }
            messages = messages + ChatMessage.ImageMessage(uri.toString())
        }
    }
    
    // 拍照启动器
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                messages = messages + ChatMessage.ImageMessage(uri.toString())
            }
        }
    }
    
    // 添加相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限获取成功后创建图片文件并启动相机
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            photoUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            photoUri?.let { takePictureLauncher.launch(it) }
        }
    }
    
    // 录音相关状态
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordStartTime by remember { mutableStateOf(0L) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    // 在 ChatScreen 函数中添加计时器相关的状态
    var recordingTimer: Job? by remember { mutableStateOf(null) }
    
    // 添加剩余时间状态
    var remainingTime by remember { mutableStateOf(60) }  // 初始60秒
    
    // 添加停止录音的函数
    fun stopRecordingAndSend() {
        if (isRecording) {
            recordingTimer?.cancel()
            recordingTimer = null
            remainingTime = 60  // 重置剩余时间
            
            stopRecording(recorder, recordStartTime) { duration ->
                audioFile?.let { file ->
                    val currentTime = System.currentTimeMillis()
                    // 添加时间戳检查
                    if (messages.isEmpty() || shouldAddTimestamp(messages, messages.last(), currentTime)) {
                        messages = messages + ChatMessage.TimestampMessage(currentTime)
                    }
                    messages = messages + ChatMessage.AudioMessage(
                        audioFile = file.absolutePath,
                        duration = duration.coerceAtMost(60000), // 确保时长不超过60秒
                        isFromMe = true
                    )
                }
                isRecording = false
                recorder = null
                audioFile = null
            }
        }
    }
    
    // 修改录音权限请求的处理函数
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val currentTime = System.currentTimeMillis()
            recordStartTime = currentTime
            remainingTime = 60  // 重置剩余时间
            startRecording(context, recorder, currentTime) { newRecorder, file ->
                recorder = newRecorder
                audioFile = file
                isRecording = true
                
                // 启动60秒计时器，同时更新剩余时间显示
                recordingTimer = CoroutineScope(Dispatchers.Main).launch {
                    repeat(60) {
                        delay(1000)  // 每秒更新一次
                        remainingTime = 59 - it
                    }
                    if (isRecording) {
                        stopRecordingAndSend()
                    }
                }
            }
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 添加语音模式状态
    var isVoiceMode by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = -animatedHeight.dp)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.White,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                        )
                        .padding(vertical = 8.dp),
                    color = Color.White,
                    shadowElevation = 0.dp,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 输入框和发送按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 切换按钮 - 移到最左侧
                            IconButton(
                                onClick = { isVoiceMode = !isVoiceMode },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isVoiceMode) R.drawable.ic_keyboard else R.drawable.ic_voice
                                    ),
                                    contentDescription = if (isVoiceMode) "切换到键" else "切换语音",
                                    tint = Color.Gray
                                )
                            }
                            
                            if (isVoiceMode) {
                                // 语音输入模式
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(
                                            minHeight = 16.dp,
                                            minWidth = 1.dp
                                        )
                                        .padding(horizontal = 8.dp)
                                        .background(
                                            color = Color(0xFFF7F7F7),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    // 长按开始录音
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                },
                                                onPress = {
                                                    // 等待释放
                                                    try {
                                                        awaitRelease()
                                                    } finally {
                                                        // 释放时停止录音
                                                        if (isRecording) {
                                                            stopRecordingAndSend()
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isRecording) "松开发送" else "按住说话",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            color = Color.Gray,
                                            lineHeight = 16.sp
                                        )
                                    )
                                }
                            } else {
                                // 文本输入模式
                                BasicTextField(
                                    value = message,
                                    onValueChange = { newText ->
                                        if (newText.endsWith('\n') && !newText.endsWith("\n\n")) {
                                            message = newText.trimEnd()
                                            sendMessage()
                                        } else {
                                            message = newText
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(
                                            minHeight = 16.dp,
                                            minWidth = 1.dp
                                        )
                                        .padding(horizontal = 8.dp)
                                        .background(
                                            color = Color(0xFFF7F7F7),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        color = Color.Black,
                                        lineHeight = 16.sp
                                    ),
                                    singleLine = false,
                                    decorationBox = { innerTextField ->
                                        Box {
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                            
                            // 更多按钮
                            IconButton(
                                onClick = { showMorePanel = !showMorePanel },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (showMorePanel) {
                                            R.drawable.ic_more_horizontal
                                        } else {
                                            R.drawable.ic_more_vertical
                                        }
                                    ),
                                    contentDescription = "更多功能"
                                )
                            }
                            
                            // 发送按钮
                            IconButton(
                                onClick = { sendMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_send_message),
                                    contentDescription = "发送"
                                )
                            }
                        }
                        
                        // 更多功能面板
                        AnimatedVisibility(
                            visible = showMorePanel,
                            enter = slideInVertically() + expandVertically(),
                            exit = slideOutVertically() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                // 在更多面板中的图标布局
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 16.dp),  // 调整整体边距
                                    horizontalArrangement = Arrangement.SpaceEvenly,  // 改为均匀分布
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 相册按钮
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { multipleImagePickerLauncher.launch("image/*") },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    color = Color(0xFFF7F7F7),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_photo),
                                                contentDescription = "相册",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "相册",
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        )
                                    }
                                    
                                    // 相机按钮
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    color = Color(0xFFF7F7F7),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_camera),
                                                contentDescription = "相机",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "相机",
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        )
                                    }
                                    
                                    // 视频按钮
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { videoPickerLauncher.launch("video/*") },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    color = Color(0xFFF7F7F7),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_video),
                                                contentDescription = "视频",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "视频",
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        )
                                    }
                                    
                                    // 文件按钮
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { 
                                                currentStorageAction = StorageAction.PICK_FILE
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                                } else {
                                                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    color = Color(0xFFF7F7F7),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_file),
                                                contentDescription = "文件",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "文件",
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                items(messages.asReversed()) { chatMessage ->
                    when (chatMessage) {
                        is ChatMessage.TimestampMessage -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatTimestamp(chatMessage.timestamp),
                                    modifier = Modifier
                                        .background(
                                            color = Color(0x1F000000),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color(0x99000000),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        else -> MessageItem(chatMessage)
                    }
                }
            }
        }
        
        // 录音悬浮指示器
        AnimatedVisibility(
            visible = isRecording,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                modifier = Modifier
                    .size(130.dp)  // 增加一点高度
                    .animateContentSize(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x88000000)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly  // 改为均匀分布
                ) {
                    // 麦克风图标
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "录音中",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(42.dp)
                    )
                    
                    // 音量动画效果
                    Row(
                        modifier = Modifier
                            .height(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            var animationState by remember { mutableStateOf(true) }
                            val animatedHeight by animateFloatAsState(
                                targetValue = if (animationState) 14f else 4f,
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "volume_bar_$index"
                            )
                            
                            LaunchedEffect(Unit) {
                                while(true) {
                                    delay(index * 50L)
                                    animationState = !animationState
                                    delay(300L)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(with(LocalDensity.current) { animatedHeight.toDp() })
                                    .background(
                                        color = Color.White.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(0.75.dp)
                                    )
                            )
                        }
                    }
                    
                    // 提示文本和剩余时间
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "松开发送",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${remainingTime}s",  // 简化显示
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // 对话框显示逻辑
    if (showStoragePermissionDialog) {
        StoragePermissionDialog(
            onConfirm = {
                showStoragePermissionDialog = false
                currentDialogAction?.invoke()
                currentDialogAction = null
            },
            onDismiss = {
                showStoragePermissionDialog = false
                currentDialogDismiss?.invoke()
                currentDialogDismiss = null
            }
        )
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (when(message) {
            is ChatMessage.TextMessage -> message.isFromMe
            is ChatMessage.ImageMessage -> message.isFromMe
            is ChatMessage.AudioMessage -> message.isFromMe
            is ChatMessage.TimestampMessage -> false
            is ChatMessage.VideoMessage -> message.isFromMe
            is ChatMessage.FileMessage -> message.isFromMe
        }) Arrangement.End else Arrangement.Start
    ) {
        when (message) {
            is ChatMessage.TextMessage -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF2196F3) else Color.LightGray,
                    modifier = Modifier.widthIn(max = 250.dp)
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (message.isFromMe) Color.White else Color.Black
                    )
                }
            }
            is ChatMessage.ImageMessage -> {
                // 图片消息不使用 Surface，直接显示图片
                GlideImage(
                    imageModel = { message.imageUrl },
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp)),  // 只保留圆角
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    failure = {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(
                                    color = Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "图加载失败",
                                color = Color.Red
                            )
                        }
                    }
                )
            }
            is ChatMessage.AudioMessage -> {
                val minWidth = 80.dp
                val maxWidth = 200.dp
                val maxDuration = 60000L
                
                val widthPercent = (message.duration.toFloat() / maxDuration).coerceIn(0f, 1f)
                val width = minWidth + ((maxWidth - minWidth) * widthPercent)
                
                val isPlaying = _currentPlayingFile.value == message.audioFile && _isPlayerPlaying.value
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF2196F3) else Color.LightGray,
                    modifier = Modifier
                        .width(width)
                        .clickable {
                            playVoice(message.audioFile)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPlaying) {
                                // 播放时显示动态音量条
                                Row(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(4) { index ->
                                        var animationState by remember { mutableStateOf(true) }
                                        val animatedHeight by animateFloatAsState(
                                            targetValue = if (animationState) 14f else 4f,
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            ),
                                            label = "volume_bar_$index"
                                        )
                                        
                                        LaunchedEffect(Unit) {
                                            while(true) {
                                                delay(index * 50L)
                                                animationState = !animationState
                                                delay(300L)
                                            }
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(with(LocalDensity.current) { animatedHeight.toDp() })
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(0.75.dp)
                                                )
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_play_audio),
                                    contentDescription = "播放语音",
                                    tint = if (message.isFromMe) Color.White else Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = "${message.duration / 1000}\"",
                            color = if (message.isFromMe) Color.White else Color.Black
                        )
                    }
                }
            }
            is ChatMessage.TimestampMessage -> {
                // 时间戳消息不需要在这里处理，因为已经在外层处理了
            }
            is ChatMessage.VideoMessage -> {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // 点击播放视频
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(message.videoUri), "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                ) {
                    // 显示视频缩略图
                    if (message.thumbnailUri != null) {
                        GlideImage(
                            imageModel = { message.thumbnailUri },
                            modifier = Modifier.fillMaxSize(),
                            imageOptions = ImageOptions(
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center
                            )
                        )
                    } else {
                        // 如果没有缩略图，显示默认背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                    
                    // 播放标
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_video),
                        contentDescription = "播放视频",
                        tint = Color.White,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                    
                    // 视频时长
                    if (message.duration > 0) {
                        Text(
                            text = formatDuration(message.duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            is ChatMessage.FileMessage -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(message.fileUri), message.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开此类型的文件", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 使用新的 FileIcon 组件
                        FileIcon(
                            mimeType = message.mimeType,
                            fileUri = message.fileUri,
                            modifier = Modifier
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = message.fileName,
                                color = Color.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatFileSize(message.fileSize),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// 录音相关的辅助函数保持在外部
private fun startRecording(context: Context, recorder: MediaRecorder?, startTime: Long, onStart: (MediaRecorder, File) -> Unit) {
    val activity = context as? MainActivity
    activity?.checkStoragePermissions { granted ->
        if (granted) {
            val directory = Environment.getExternalStorageDirectory()
            val file = File(directory, "ZST/Audio/audio_${System.currentTimeMillis()}.mp3")
            file.parentFile?.mkdirs()
            
            val newRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            onStart(newRecorder, file)
        }
    }
}

private fun stopRecording(recorder: MediaRecorder?, startTime: Long, onStop: (Long) -> Unit) {
    val duration = try {
        recorder?.apply {
            stop()
            release()
        }
        // 计算实际录时长毫秒）
        System.currentTimeMillis() - startTime
    } catch (e: Exception) {
        1000L // 发生错误时的默认值为1秒
    }
    onStop(duration)
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ZstTheme {
        ChatScreen()
    }
}

// 修改辅助函数
private fun shouldAddTimestamp(messages: List<ChatMessage>, lastMessage: ChatMessage, currentTime: Long): Boolean {
    // 如果上一条是时间戳，不添加新的时间戳
    if (lastMessage is ChatMessage.TimestampMessage) {
        return false
    }
    
    // 获取最后一个时间戳的时间
    val lastTimestampTime = messages.findLast { it is ChatMessage.TimestampMessage }
        ?.let { (it as ChatMessage.TimestampMessage).timestamp }
        ?: 0L
    
    // 获取上一条消息的时间
    val lastMessageTime = when (lastMessage) {
        is ChatMessage.TextMessage -> lastMessage.timestamp
        is ChatMessage.ImageMessage -> lastMessage.timestamp
        is ChatMessage.AudioMessage -> lastMessage.timestamp
        is ChatMessage.VideoMessage -> lastMessage.timestamp
        is ChatMessage.FileMessage -> lastMessage.timestamp
        is ChatMessage.TimestampMessage -> return false
    }
    
    // 修改为3分钟的间隔
    val THREE_MINUTES = TimeUnit.MINUTES.toMillis(3)
    
    // 添加时间戳的条件:
    // 1. 是第一条消息
    // 2. 距离上一个时间戳超过3分钟
    return messages.isEmpty() || 
           (lastTimestampTime == 0L && currentTime - lastMessageTime >= THREE_MINUTES) ||
           (lastTimestampTime > 0L && currentTime - lastTimestampTime >= THREE_MINUTES)
}

private fun formatTimestamp(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    calendar.timeInMillis = timestamp
    
    return when {
        // 今天的消息只显示时间
        DateUtils.isToday(timestamp) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp)
        }
        // 昨天的消息显示"昨天 HH:mm"
        DateUtils.isToday(timestamp + TimeUnit.DAYS.toMillis(1)) -> {
            "昨 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp)}"
        }
        // 今年的消息显示"MM-dd HH:mm"
        calendar.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) -> {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(timestamp)
        }
        // 其他时间显示完整日期"yyyy-MM-dd HH:mm"
        else -> {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timestamp)
        }
    }
}

// 添加辅助函数
private fun formatDuration(duration: Long): String {
    val seconds = (duration / 1000) % 60
    val minutes = (duration / (1000 * 60)) % 60
    val hours = duration / (1000 * 60 * 60)
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

// 添加辅助函数
private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}

private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long? {
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()
        cursor.getLong(sizeIndex)
    }
}

private fun getMimeType(contentResolver: ContentResolver, uri: Uri): String? {
    return contentResolver.getType(uri)
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun FileIcon(mimeType: String, fileUri: String? = null, modifier: Modifier = Modifier) {
    val iconRes = when {
        mimeType.startsWith("image/") -> R.drawable.ic_image
        mimeType.startsWith("video/") -> R.drawable.ic_video
        mimeType.startsWith("audio/") -> R.drawable.ic_audio
        mimeType.startsWith("text/") -> R.drawable.ic_text
        mimeType.startsWith("application/pdf") -> R.drawable.ic_pdf
        mimeType.startsWith("application/msword") || 
        mimeType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") -> R.drawable.ic_doc
        mimeType.startsWith("application/vnd.ms-excel") || 
        mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") -> R.drawable.ic_excel
        mimeType.startsWith("application/vnd.ms-powerpoint") || 
        mimeType.startsWith("application/vnd.openxmlformats-officedocument.presentationml") -> R.drawable.ic_ppt
        else -> R.drawable.ic_file
    }
    
    when {
        mimeType.startsWith("image/") && fileUri != null -> {
            GlideImage(
                imageModel = { fileUri },
                modifier = modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp)),
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                ),
                failure = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = "文件类型",
                        tint = Color(0xFF2196F3),
                        modifier = modifier.size(36.dp)
                    )
                }
            )
        }
        else -> {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "文件类型",
                tint = Color(0xFF2196F3),
                modifier = modifier.size(36.dp)
            )
        }
    }
}

