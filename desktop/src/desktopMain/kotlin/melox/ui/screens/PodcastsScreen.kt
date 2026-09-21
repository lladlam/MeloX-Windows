package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

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
        id = "1", name = "每日音乐推荐", host = "音乐频道", episodeCount = 320, subscribed = true,
        gradientColors = listOf(Color(0xFFFF2442), Color(0xFFFF6B81)),
        episodes = listOf(
            PodcastEpisode("e1", "华语流行精选", "45:20", "2026-09-20", true),
            PodcastEpisode("e2", "古典音乐之夜", "52:10", "2026-09-19", true),
            PodcastEpisode("e3", "独立音乐发现", "38:45", "2026-09-18", false),
        ),
    ),
    Podcast(
        id = "2", name = "科技前沿", host = "科技早报", episodeCount = 156, subscribed = true,
        gradientColors = listOf(Color(0xFF03DAC5), Color(0xFF00BFA5)),
        episodes = listOf(
            PodcastEpisode("e4", "AI 新突破", "30:15", "2026-09-20", false),
            PodcastEpisode("e5", "量子计算进展", "42:30", "2026-09-17", false),
        ),
    ),
    Podcast(
        id = "3", name = "文化漫谈", host = "文化观察", episodeCount = 89, subscribed = false,
        gradientColors = listOf(Color(0xFFFF9800), Color(0xFFFFB74D)),
        episodes = listOf(PodcastEpisode("e6", "城市文化地图", "55:00", "2026-09-18", false)),
    ),
    Podcast(
        id = "4", name = "音乐制作人手记", host = "音频工坊", episodeCount = 67, subscribed = false,
        gradientColors = listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
        episodes = listOf(
            PodcastEpisode("e7", "混音技巧分享", "36:20", "2026-09-16", false),
            PodcastEpisode("e8", "录音室探秘", "48:10", "2026-09-15", false),
        ),
    ),
    Podcast(
        id = "5", name = "爵士俱乐部", host = "爵士之声", episodeCount = 210, subscribed = true,
        gradientColors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6)),
        episodes = listOf(
            PodcastEpisode("e9", "经典爵士专辑回顾", "60:30", "2026-09-19", true),
            PodcastEpisode("e10", "现代爵士新声", "44:15", "2026-09-17", false),
        ),
    ),
    Podcast(
        id = "6", name = "播客访谈录", host = "对话栏目", episodeCount = 145, subscribed = false,
        gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF81C784)),
        episodes = listOf(PodcastEpisode("e11", "音乐人专访", "72:40", "2026-09-20", false)),
    ),
)

private val categories = listOf("全部", "推荐", "订阅", "音乐", "科技", "文化")

@Composable
fun PodcastsScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf("全部") }
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
        MeloXIosTopBar(title = "播客")

        Column(modifier = Modifier.weight(1f)) {
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                onCategoryChange = { selectedCategory = it },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
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
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .background(if (isSelected) MeloXColors.Primary else MeloXColors.SurfaceVariant)
                    .clickable { onCategoryChange(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category,
                    style = MeloXTypography.caption,
                    color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
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
            style = MeloXTypography.headline,
            color = MeloXColors.OnBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
    Box(
        modifier = Modifier
            .width(200.dp)
            .clip(MeloXGlass.cardShape)
            .background(MeloXColors.SurfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.linearGradient(podcast.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Podcast,
                    color = Color.White.copy(alpha = 0.8f),
                    size = 40,
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.name,
                    style = MeloXTypography.body.copy(),
                    color = MeloXColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.host,
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${podcast.episodeCount} 集",
                    style = MeloXTypography.caption,
                    color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
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

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = when (selectedCategory) {
                "全部" -> "全部播客"
                "推荐" -> "为你推荐"
                "订阅" -> "我的订阅"
                else -> "${selectedCategory}播客"
            },
            style = MeloXTypography.headline,
            color = MeloXColors.OnBackground,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (filteredPodcasts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无${selectedCategory}播客",
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurfaceVariant,
                )
            }
        } else {
            val chunked = filteredPodcasts.chunked(3)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { podcast ->
                        PodcastGridCard(podcast = podcast, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PodcastGridCard(podcast: Podcast, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MeloXGlass.cardShape)
            .background(MeloXColors.SurfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Brush.linearGradient(podcast.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Podcast,
                    color = Color.White.copy(alpha = 0.8f),
                    size = 36,
                )
                if (podcast.subscribed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MeloXColors.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Checkmark,
                            color = Color.White,
                            size = 12,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = podcast.name,
                    style = MeloXTypography.subheadline.copy(),
                    color = MeloXColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = podcast.host,
                    style = MeloXTypography.caption,
                    color = MeloXColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionsSection(podcasts: List<Podcast>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的订阅",
                style = MeloXTypography.headline,
                color = MeloXColors.OnBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "查看全部",
                style = MeloXTypography.subheadline,
                color = MeloXColors.Primary,
                modifier = Modifier.clickable { },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MeloXGlass.largeCardShape)
                .background(MeloXColors.SurfaceVariant),
        ) {
            podcasts.forEachIndexed { index, podcast ->
                SubscriptionItem(podcast)
                if (index < podcasts.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MeloXGlass.separatorColor)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionItem(podcast: Podcast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(if (podcast.episodes.isNotEmpty()) 68.dp else 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MeloXGlass.compactShape)
                .background(Brush.linearGradient(podcast.gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Podcast,
                color = Color.White.copy(alpha = 0.8f),
                size = 22,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.name,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${podcast.host} · ${podcast.episodeCount} 集",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (podcast.episodes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .background(MeloXColors.Primary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${podcast.episodes.size} 新",
                    style = MeloXTypography.caption,
                    color = MeloXColors.Primary,
                )
            }
        }
    }
}
