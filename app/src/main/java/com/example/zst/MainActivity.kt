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
import androidx.compose.animation.core.tween
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
    
    data class VoiceMessage(
        val audioFile: String, 
        val duration: Int, // 语音时长（秒）
        val isFromMe: Boolean = true
    ) : ChatMessage()
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
    var recordingStartTime by remember { mutableStateOf(0L) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    // 添加停止录音的函数
    fun stopRecordingAndSend() {
        if (isRecording) {
            stopRecording(recorder) { duration ->
                audioFile?.let { file ->
                    messages = messages + ChatMessage.VoiceMessage(
                        audioFile = file.absolutePath,
                        duration = duration,
                        isFromMe = true
                    )
                }
                isRecording = false
                recorder = null
                audioFile = null
            }
        }
    }
    
    // 录音权限请求
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限获取成功后开始录音
            startRecording(context, recorder) { newRecorder, file ->
                recorder = newRecorder
                audioFile = file
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
            }
        } else {
            // 可以添加权限被拒绝时的提示
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
                                                onPress = {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    tryAwaitRelease()
                                                    if (isRecording) {
                                                        stopRecordingAndSend()
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
                                    onValueChange = { message = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(
                                            minHeight = 16.dp,
                                            minWidth = 1.dp
                                        )
                                        .padding(horizontal = 8.dp)  // 添加水平内边距
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
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (message.isEmpty()) {
                                                Text(
                                                    "请输入消息",
                                                    style = TextStyle(
                                                        fontSize = 16.sp,
                                                        color = Color.Gray,
                                                        lineHeight = 16.sp
                                                    )
                                                )
                                            }
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
                                    // 相册按钮
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
            is ChatMessage.VoiceMessage -> message.isFromMe
        }) Arrangement.End else Arrangement.Start
    ) {
        when (message) {
            is ChatMessage.TextMessage -> {
                // 文本消息保持原有的气泡样式
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF95EC69) else Color.LightGray
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
            is ChatMessage.VoiceMessage -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.isFromMe) Color(0xFF95EC69) else Color.LightGray,
                    modifier = Modifier.clickable {
                        // 点击播放语音
                        playVoice(message.audioFile)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放语音",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${message.duration}\"",
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

// 录音相关的辅助函数保持在外部
private fun startRecording(context: Context, recorder: MediaRecorder?, onStart: (MediaRecorder, File) -> Unit) {
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

private fun stopRecording(recorder: MediaRecorder?, onStop: (Int) -> Unit) {
    recorder?.apply {
        stop()
        release()
    }
    onStop(3) // 这里可以计算实际的录音时长
}

// 添加语音播放功能
private fun playVoice(audioFile: String) {
    MediaPlayer().apply {
        setDataSource(audioFile)
        prepare()
        start()
        setOnCompletionListener { mp ->
            mp.release()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ZstTheme {
        ChatScreen()
    }
}