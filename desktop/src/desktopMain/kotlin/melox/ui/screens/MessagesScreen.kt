package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import java.awt.Cursor

// ── Data models ──

private data class Conversation(
    val id: String,
    val name: String,
    val avatarColor: Color,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int,
)

private data class ChatMessage(
    val id: String,
    val content: String,
    val isSent: Boolean,
    val timestamp: String,
)

// ── Mock data ──

private val mockConversations = listOf(
    Conversation("1", "音乐推荐助手", Color(0xFFFF2442), "为你推荐了5首新歌", "10:30", 3),
    Conversation("2", "网易云音乐", Color(0xFFFF2442), "你关注的歌手发布了新专辑", "09:15", 1),
    Conversation("3", "小明", Color(0xFF2196F3), "这首歌太好听了！", "昨天", 0),
    Conversation("4", "Spotify Weekly", Color(0xFF1DB954), "Your weekly playlist is ready", "周一", 5),
    Conversation("5", "QQ音乐助手", Color(0xFF12B7F5), "你的年度听歌报告已生成", "周日", 0),
    Conversation("6", "音乐社区", Color(0xFF9C27B0), "你收到了3条新评论", "3天前", 2),
)

private val mockMessages = mapOf(
    "1" to listOf(
        ChatMessage("m1", "你好！我是音乐推荐助手 🎵", false, "10:25"),
        ChatMessage("m2", "根据你最近的听歌习惯，我为你推荐了以下歌曲：", false, "10:25"),
        ChatMessage("m3", "1. 晴天 - 周杰伦", false, "10:26"),
        ChatMessage("m4", "2. 夜曲 - 周杰伦", false, "10:26"),
        ChatMessage("m5", "3. 七里香 - 周杰伦", false, "10:26"),
        ChatMessage("m6", "好的，谢谢！", true, "10:28"),
        ChatMessage("m7", "不客气！如果你需要更多推荐，随时告诉我 😊", false, "10:30"),
    ),
    "2" to listOf(
        ChatMessage("m1", "你关注的歌手周杰伦发布了新专辑《最伟大的作品》", false, "09:10"),
        ChatMessage("m2", "快去听听看吧！", false, "09:10"),
        ChatMessage("m3", "已经去听了，太棒了！", true, "09:15"),
    ),
    "3" to listOf(
        ChatMessage("m1", "嘿，你听过这首歌吗？", false, "昨天 18:30"),
        ChatMessage("m2", "哪首？", true, "昨天 18:32"),
        ChatMessage("m3", "「夜曲」周杰伦的", false, "昨天 18:33"),
        ChatMessage("m4", "当然听过！经典中的经典", true, "昨天 18:35"),
        ChatMessage("m5", "这首歌太好听了！", false, "昨天 18:36"),
    ),
    "4" to listOf(
        ChatMessage("m1", "Your Discover Weekly is ready!", false, "周一 08:00"),
        ChatMessage("m2", "30 songs tailored just for you", false, "周一 08:00"),
        ChatMessage("m3", "Thanks!", true, "周一 10:00"),
        ChatMessage("m4", "Enjoy your music! 🎧", false, "周一 10:05"),
    ),
    "5" to listOf(
        ChatMessage("m1", "你的2024年度听歌报告已生成！", false, "周日 12:00"),
        ChatMessage("m2", "今年你一共听了 2,847 首歌", false, "周日 12:00"),
        ChatMessage("m3", "最常听的歌手是：周杰伦", false, "周日 12:01"),
        ChatMessage("m4", "哇，这么多！", true, "周日 14:00"),
    ),
    "6" to listOf(
        ChatMessage("m1", "你发布的歌单「深夜治愈」收到了3条新评论", false, "3天前 20:00"),
        ChatMessage("m2", "用户A：这个歌单太棒了！", false, "3天前 20:01"),
        ChatMessage("m3", "用户B：收藏了，感谢分享", false, "3天前 20:01"),
        ChatMessage("m4", "用户C：还有类似的歌单推荐吗？", false, "3天前 20:02"),
    ),
)

// ── State holder ──

@Stable
private class MessagesState {
    var selectedConversation by mutableStateOf<Conversation?>(null)
    var inputText by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
    val messages = mutableStateMapOf<String, MutableList<ChatMessage>>()
    val conversationList = mutableStateListOf<Conversation>()

    init {
        conversationList.addAll(mockConversations)
        mockMessages.forEach { (convId, msgs) ->
            messages[convId] = msgs.toMutableList()
        }
    }

    fun sendMessage(convId: String, content: String) {
        val msgList = messages.getOrPut(convId) { mutableListOf() }
        val newMsg = ChatMessage(
            id = "sent_${System.currentTimeMillis()}",
            content = content,
            isSent = true,
            timestamp = "刚刚",
        )
        msgList.add(newMsg)
    }
}

// ── Top bar ──

@Composable
private fun MessagesTopBar(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MeloXColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "消息",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .hoverable(remember { MutableInteractionSource() })
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        ) {
            Text(
                text = "⟳",
                color = MeloXColors.TextSecondary,
                fontSize = 18.sp,
            )
        }
    }
}

// ── Conversation list item ──

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .background(if (isHovered) MeloXColors.CardBackgroundHover else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(conversation.avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = conversation.name.first().toString(),
                color = Color.White,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name + last message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.name,
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = conversation.lastMessage,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp + unread
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = conversation.timestamp,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
            )
            if (conversation.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MeloXColors.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (conversation.unreadCount > 9) "9+" else "${conversation.unreadCount}",
                        color = Color.White,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Chat message bubble ──

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = if (message.isSent) Arrangement.End else Arrangement.Start,
    ) {
        if (!message.isSent) {
            // Received message - left aligned
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = MeloXColors.SurfaceVariant,
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        } else {
            // Sent message - right aligned
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MeloXColors.Primary,
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

// ── Time label ──

@Composable
private fun TimeLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MeloXColors.SurfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
            )
        }
    }
}

// ── Chat detail view ──

@Composable
private fun ChatDetailView(
    conversation: Conversation,
    state: MessagesState,
    onBack: () -> Unit,
) {
    val messages = remember(state.messages[conversation.id]?.size) {
        state.messages[conversation.id] ?: emptyList()
    }
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        // Chat top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .hoverable(remember { MutableInteractionSource() })
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
            ) {
                Text(
                    text = "‹",
                    color = MeloXColors.TextPrimary,
                    fontSize = 22.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(conversation.avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = conversation.name.first().toString(),
                    color = Color.White,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = conversation.name,
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        HorizontalDivider(color = MeloXColors.Outline.copy(alpha = 0.2f), thickness = 0.5.dp)

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            var lastTimestamp = ""
            items(messages) { message ->
                // Show time label if timestamp changed
                if (message.timestamp != lastTimestamp && !message.timestamp.startsWith("刚刚")) {
                    TimeLabel(text = message.timestamp)
                    lastTimestamp = message.timestamp
                }
                MessageBubble(message = message)
            }
        }

        HorizontalDivider(color = MeloXColors.Outline.copy(alpha = 0.2f), thickness = 0.5.dp)

        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "输入消息...",
                        color = MeloXColors.TextTertiary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeloXColors.Primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MeloXColors.Outline,
                    focusedContainerColor = MeloXColors.SurfaceVariant,
                    unfocusedContainerColor = MeloXColors.SurfaceVariant,
                    cursorColor = MeloXColors.Primary,
                    focusedTextColor = MeloXColors.TextPrimary,
                    unfocusedTextColor = MeloXColors.TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(enabled = inputText.isNotBlank()) {
                        state.sendMessage(conversation.id, inputText)
                        inputText = ""
                    }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = CircleShape,
                color = if (inputText.isNotBlank()) MeloXColors.Primary else MeloXColors.SurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↑",
                        color = if (inputText.isNotBlank()) Color.White else MeloXColors.TextTertiary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Empty state ──

@Composable
private fun EmptyMessagesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "💬",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "暂无消息",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
                color = MeloXColors.TextSecondary,
            )
        }
    }
}

// ── Main MessagesScreen composable ──

@Composable
fun MessagesScreen() {
    val state = remember { MessagesState() }
    val scope = rememberCoroutineScope()

    val selectedConversation = state.selectedConversation

    if (selectedConversation != null) {
        // Chat detail view
        ChatDetailView(
            conversation = selectedConversation,
            state = state,
            onBack = { state.selectedConversation = null },
        )
    } else {
        // Conversation list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MeloXColors.Background),
        ) {
            MessagesTopBar(
                onRefresh = {
                    state.isRefreshing = true
                    scope.launch {
                        delay(1000)
                        state.isRefreshing = false
                    }
                },
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MeloXColors.Primary,
                    trackColor = MeloXColors.Background,
                )
            }

            if (state.conversationList.isEmpty()) {
                EmptyMessagesState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.conversationList) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                state.selectedConversation = conversation
                            },
                        )
                    }
                }
            }
        }
    }
}
