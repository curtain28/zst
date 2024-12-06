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
import android.content.Context
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

// 修改消息数据类以支持不同类型的消息
sealed class ChatMessage {
    data class TextMessage(
        val content: String,
        val isFromMe: Boolean = true
    ) : ChatMessage()
    
    data class ImageMessage(
        val imageUrl: String,
        val isFromMe: Boolean = true
    ) : ChatMessage()
    
    data class AudioMessage(
        val audioFile: String, 
        val duration: Long, // 使用实际的音频时长，单位为毫秒
        val isFromMe: Boolean = true
    ) : ChatMessage()
}

// 在 ChatScreen 函数外部添加全局 MediaPlayer 管理
private var currentPlayer: MediaPlayer? = null

// 修改播放语音的函数
private fun playVoice(audioFile: String) {
    // 停止当前正在播放的语音
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
        setOnCompletionListener { mp ->
            mp.release()
            currentPlayer = null
        }
    }
}

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
        // 清理 MediaPlayer
        currentPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        currentPlayer = null
    }
}

@Composable
fun ChatScreen() {
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    
    // 监听键盘状态
    var keyboardHeight by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val density = LocalDensity.current
    
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(view)
            val imeHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            keyboardHeight = with(density) { imeHeight.toDp().value }  // 转换为 dp
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
            messages = messages + ChatMessage.TextMessage(message)
            message = ""  // 清空输入框
        }
    }
    
    // 添加图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            messages = messages + ChatMessage.ImageMessage(it.toString())
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
    
    // 添加状态
    var showImagePicker by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<Uri?>(null) }
    val selectedImages = remember { mutableStateListOf<Uri>() }
    
    // 拍照URI
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // 图片选择器
    val multipleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
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
    
    // 获取 context
    val context = LocalContext.current
    
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
    
    // 添加更多面板显示状态
    var showMorePanel by remember { mutableStateOf(false) }
    
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
                                    contentDescription = if (isVoiceMode) "切换到键盘" else "切换到语音",
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
                                onClick = { showMorePanel = !showMorePanel },  // 切换更多面板的显示状态
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_more),
                                    contentDescription = "更多功能",
                                    tint = Color.Gray
                                )
                            }
                            
                            // 发送按钮
                            IconButton(
                                onClick = { sendMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = Color(0xFF95EC69)
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 册按钮
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 24.dp)
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
                reverseLayout = true  // 新消息显示在底部
            ) {
                items(messages.asReversed()) { chatMessage ->
                    MessageItem(chatMessage)
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
}

@Composable
fun MessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (when(message) {
            is ChatMessage.TextMessage -> message.isFromMe
            is ChatMessage.ImageMessage -> message.isFromMe
            is ChatMessage.AudioMessage -> message.isFromMe
        }) Arrangement.End else Arrangement.Start
    ) {
        when (message) {
            is ChatMessage.TextMessage -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF95EC69) else Color.LightGray,
                    modifier = Modifier.widthIn(max = 250.dp)
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.Black
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
                                text = "图片加载失败",
                                color = Color.Red
                            )
                        }
                    }
                )
            }
            is ChatMessage.AudioMessage -> {
                val minWidth = 60.dp  // 最小宽度
                val maxWidth = 180.dp  // 最大宽度
                val maxDuration = 60000L  // 最大时长（60秒）
                 
                // 修正计算实际宽度的方式
                val widthPercent = (message.duration.toFloat() / maxDuration).coerceIn(0f, 1f)
                val width = minWidth + ((maxWidth - minWidth) * widthPercent)
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF95EC69) else Color.LightGray,
                    modifier = Modifier
                        .width(width)  // 设置动态宽度
                        .clickable {
                            playVoice(message.audioFile)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween  // 让时长显示靠右
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放语音",
                                tint = Color.Black
                            )
                        }
                        Text(
                            text = "${message.duration / 1000}\"",
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

// 录音相关的辅助函数保持在外部
private fun startRecording(context: Context, recorder: MediaRecorder?, startTime: Long, onStart: (MediaRecorder, File) -> Unit) {
    val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
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

private fun stopRecording(recorder: MediaRecorder?, startTime: Long, onStop: (Long) -> Unit) {
    val duration = try {
        recorder?.apply {
            stop()
            release()
        }
        // 计算实际录音时长（毫秒）
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