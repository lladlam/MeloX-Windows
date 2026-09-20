package melox.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private data class PodcastEpisode(
    val id: String,
    val title: String,
    val duration: String,
    val date: String,
    val downloaded: Boolean,
)

private data class Podcast(
    val id: String,
    val name: String,
    val host: String,
    val episodeCount: Int,
    val subscribed: Boolean,
    val gradientColors: List<Color>,
    val episodes: List<PodcastEpisode>,
)

private val mockPodcasts = listOf(
    Podcast(
        id = "1",
        name = "每日音乐推荐",
        host = "音乐频道",
        episodeCount = 320,
        subscribed = true,
        gradientColors = listOf(Color(0xFFFF2442), Color(0xFFFF6B81)),
        episodes = listOf(
            PodcastEpisode("e1", "华语流行精选", "45:20", "2026-09-20", true),
            PodcastEpisode("e2", "古典音乐之夜", "52:10", "2026-09-19", true),
            PodcastEpisode("e3", "独立音乐发现", "38:45", "2026-09-18", false),
        ),
    ),
    Podcast(
        id = "2",
        name = "科技前沿",
        host = "科技早报",
        episodeCount = 156,
        subscribed = true,
        gradientColors = listOf(Color(0xFF03DAC5), Color(0xFF00BFA5)),
        episodes = listOf(
            PodcastEpisode("e4", "AI 新突破", "30:15", "2026-09-20", false),
            PodcastEpisode("e5", "量子计算进展", "42:30", "2026-09-17", false),
        ),
    ),
    Podcast(
        id = "3",
        name = "文化漫谈",
        host = "文化观察",
        episodeCount = 89,
        subscribed = false,
        gradientColors = listOf(Color(0xFFFF9800), Color(0xFFFFB74D)),
        episodes = listOf(
            PodcastEpisode("e6", "城市文化地图", "55:00", "2026-09-18", false),
        ),
    ),
    Podcast(
        id = "4",
        name = "音乐制作人手记",
        host = "音频工坊",
        episodeCount = 67,
        subscribed = false,
        gradientColors = listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
        episodes = listOf(
            PodcastEpisode("e7", "混音技巧分享", "36:20", "2026-09-16", false),
            PodcastEpisode("e8", "录音室探秘", "48:10", "2026-09-15", false),
        ),
    ),
    Podcast(
        id = "5",
        name = "爵士俱乐部",
        host = "爵士之声",
        episodeCount = 210,
        subscribed = true,
        gradientColors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6)),
        episodes = listOf(
            PodcastEpisode("e9", "经典爵士专辑回顾", "60:30", "2026-09-19", true),
            PodcastEpisode("e10", "现代爵士新声", "44:15", "2026-09-17", false),
        ),
    ),
    Podcast(
        id = "6",
        name = "播客访谈录",
        host = "对话栏目",
        episodeCount = 145,
        subscribed = false,
        gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF81C784)),
        episodes = listOf(
            PodcastEpisode("e11", "音乐人专访", "72:40", "2026-09-20", false),
        ),
    ),
)

private val categories = listOf("全部", "推荐", "订阅", "音乐", "科技", "文化")

@Composable
fun PodcastsScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf("全部") }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val subscribedPodcasts = remember { mutableStateListOf<Podcast>() }
    LaunchedEffect(Unit) {
        subscribedPodcasts.clear()
        subscribedPodcasts.addAll(mockPodcasts.filter { it.subscribed })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        PodcastsTopBar(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                isLoading = true
                // Simulate refresh
                isRefreshing = false
                isLoading = false
            },
        )

        if (isLoading) {
            LoadingState(modifier = Modifier.weight(1f))
        } else if (subscribedPodcasts.isEmpty() && selectedCategory == "订阅") {
            EmptySubscriptionsState(modifier = Modifier.weight(1f))
        } else {
            PodcastsContent(
                navState = navState,
                selectedCategory = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                subscribedPodcasts = subscribedPodcasts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PodcastsTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeloXColors.Background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "播客",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
        ) {
            Text(
                text = if (isRefreshing) "⟳" else "↻",
                color = MeloXColors.OnSurfaceVariant,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun PodcastsContent(
    navState: MeloXNavState,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    subscribedPodcasts: List<Podcast>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CategoryFilterBar(
            selectedCategory = selectedCategory,
            onCategoryChange = onCategoryChange,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            FeaturedPodcastsSection()
            Spacer(modifier = Modifier.height(24.dp))
            PodcastGridSection(selectedCategory)
            if (subscribedPodcasts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SubscriptionsSection(subscribedPodcasts)
            }
        }
    }
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            val bgColor by animateColorAsState(
                if (isSelected) MeloXColors.ChipBackgroundSelected else MeloXColors.ChipBackground,
            )
            val textColor by animateColorAsState(
                if (isSelected) MeloXColors.OnPrimary else MeloXColors.OnSurfaceVariant,
            )

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCategoryChange(category) },
                shape = RoundedCornerShape(20.dp),
                color = bgColor,
            ) {
                Text(
                    text = category,
                    color = textColor,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FeaturedPodcastsSection() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = "精选推荐",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            mockPodcasts.take(4).forEach { podcast ->
                FeaturedPodcastCard(podcast)
            }
        }
    }
}

@Composable
private fun FeaturedPodcastCard(podcast: Podcast) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(16.dp),
        color = MeloXColors.CardBackground,
        shadowElevation = if (isHovered) 4.dp else 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.linearGradient(podcast.gradientColors),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "◉",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 40.sp,
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = podcast.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.host,
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${podcast.episodeCount} 集",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PodcastGridSection(selectedCategory: String) {
    val filteredPodcasts = when (selectedCategory) {
        "订阅" -> mockPodcasts.filter { it.subscribed }
        "音乐" -> mockPodcasts.filter { it.id in listOf("1", "5") }
        "科技" -> mockPodcasts.filter { it.id == "2" }
        "文化" -> mockPodcasts.filter { it.id == "3" }
        "推荐" -> mockPodcasts.take(3)
        else -> mockPodcasts
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = when (selectedCategory) {
                "全部" -> "全部播客"
                "推荐" -> "为你推荐"
                "订阅" -> "我的订阅"
                else -> "${selectedCategory}播客"
            },
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (filteredPodcasts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无${selectedCategory}播客",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                )
            }
        } else {
            // Grid layout: 3 columns
            val chunked = filteredPodcasts.chunked(3)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { podcast ->
                        PodcastGridCard(
                            podcast = podcast,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Fill remaining space
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PodcastGridCard(
    podcast: Podcast,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(12.dp),
        color = MeloXColors.CardBackground,
        shadowElevation = if (isHovered) 4.dp else 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.linearGradient(podcast.gradientColors),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "◉",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 36.sp,
                )
                if (podcast.subscribed) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = MeloXColors.Primary,
                    ) {
                        Text(
                            text = "✓",
                            color = MeloXColors.OnPrimary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(10.dp),
            ) {
                Text(
                    text = podcast.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = podcast.host,
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${podcast.episodeCount} 集",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionsSection(podcasts: List<Podcast>) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的订阅",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "查看全部",
                color = MeloXColors.Primary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.clickable { },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        podcasts.forEach { podcast ->
            SubscriptionItem(podcast)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubscriptionItem(podcast: Podcast) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(12.dp),
        color = if (isHovered) MeloXColors.CardBackgroundHover else MeloXColors.CardBackground,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(podcast.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "◉",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${podcast.host} · ${podcast.episodeCount} 集",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (podcast.episodes.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeloXColors.Primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "${podcast.episodes.size} 新",
                        color = MeloXColors.Primary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = MeloXColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "加载中...",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun EmptySubscriptionsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "◉",
                color = MeloXColors.TextTertiary,
                fontSize = 48.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无订阅",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "浏览播客并订阅你喜欢的节目",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
