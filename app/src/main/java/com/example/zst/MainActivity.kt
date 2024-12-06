package com.example.zst

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.zst.ui.theme.ZstTheme

// 消息数据类
data class ChatMessage(
    val content: String,
    val isFromMe: Boolean = true
)

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
            messages = messages + ChatMessage(message)
            message = ""  // 清空输入框
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
                        ),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        
                        // 添加发送按钮
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
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
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
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ZstTheme {
        ChatScreen()
    }
}