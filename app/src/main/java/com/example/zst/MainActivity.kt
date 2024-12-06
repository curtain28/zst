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
                            BasicTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(
                                        minHeight = 16.dp,
                                        minWidth = 1.dp
                                    )
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
                            
                            // 发送按钮
                            IconButton(
                                onClick = { sendMessage() },
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = Color(0xFF95EC69)
                                )
                            }
                        }
                        
                        // 工具栏行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 相册选择按钮
                            IconButton(
                                onClick = { 
                                    multipleImagePickerLauncher.launch("image/*")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_photo),
                                    contentDescription = "从相册选择",
                                    tint = Color.Gray
                                )
                            }
                            
                            // 拍照按钮
                            IconButton(
                                onClick = {
                                    // 先请求相机权限
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(start = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_camera),
                                    contentDescription = "拍照",
                                    tint = Color.Gray
                                )
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