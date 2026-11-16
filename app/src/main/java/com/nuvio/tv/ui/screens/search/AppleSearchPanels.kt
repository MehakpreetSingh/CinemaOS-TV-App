package com.nuvio.tv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.theme.NuvioTheme

private val LETTER_KEYS = ('a'..'z').map { it.toString() }
private val NUMBER_KEYS = ('0'..'9').map { it.toString() } +
    listOf("-", ".", "_", "@", "&", "'", ":", "!", "?", "+", "#", "/")

/** Apple TV style query line: magnifier + big placeholder/query text, divider underneath. */
@Composable
fun AppleSearchHeader(query: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (query.isEmpty()) NuvioTheme.colors.TextTertiary else NuvioTheme.colors.TextPrimary,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = if (query.isEmpty()) "Movies, Shows and More" else query,
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                color = if (query.isEmpty()) NuvioTheme.colors.TextTertiary else NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .androidxFocusChanged { focused = it },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.8f),
            pressedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
            content(if (focused) Color.Black else NuvioTheme.colors.TextSecondary)
        }
    }
}

private fun Modifier.androidxFocusChanged(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }

/** abc|123 toggle, 6-column key grid, SPACE / delete / CLEAR / mic. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoice: () -> Unit,
    firstKeyFocusRequester: FocusRequester,
    voiceFocusRequester: FocusRequester?,
    onVoiceFocused: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var numbers by remember { mutableStateOf(false) }
    val keys = if (numbers) NUMBER_KEYS else LETTER_KEYS
    Column(
        modifier = modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("abc" to false, "123" to true).forEach { (label, isNum) ->
                val active = numbers == isNum
                Surface(
                    onClick = { numbers = isNum },
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (active) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (active) Color.White else NuvioTheme.colors.TextSecondary,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
        Box(Modifier.height(6.dp))
        keys.chunked(6).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { colIndex, key ->
                    val req = if (rowIndex == 0 && colIndex == 0) {
                        Modifier.focusRequester(firstKeyFocusRequester)
                    } else {
                        Modifier
                    }
                    AppleKey(
                        onClick = { onQueryChange(query + key) },
                        modifier = Modifier.weight(1f).then(req)
                    ) { c ->
                        Text(key, style = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center), color = c)
                    }
                }
                repeat(6 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AppleKey(onClick = { if (query.isNotEmpty() && !query.endsWith(" ")) onQueryChange("$query ") },
                modifier = Modifier.weight(1.3f)) { c ->
                Text("SPACE", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = c)
            }
            AppleKey(onClick = { onQueryChange(query.dropLast(1)) }, modifier = Modifier.weight(1f)) { c ->
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Delete", tint = c, modifier = Modifier.size(22.dp))
            }
            AppleKey(onClick = { onQueryChange("") }, modifier = Modifier.weight(1.3f)) { c ->
                Text("CLEAR", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = c)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            AppleKey(
                onClick = onVoice,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (voiceFocusRequester != null) Modifier.focusRequester(voiceFocusRequester) else Modifier)
                    .androidxFocusChanged(onVoiceFocused)
            ) { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = c, modifier = Modifier.size(20.dp))
                    Text("VOICE", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = c)
                }
            }
        }
    }
}

/** "Browse" row of tall rounded poster cards, built from the discover results the screen already loads. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleBrowseRow(
    items: List<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Browse",
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = NuvioTheme.colors.TextSecondary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp, horizontal = 6.dp)
        ) {
            itemsIndexed(items.take(24), key = { i, it -> it.id.ifEmpty { "browse_$i" } }) { _, item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(150.dp).height(225.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        focusedContainerColor = NuvioTheme.colors.BackgroundCard
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                            0.dp,
                            RoundedCornerShape(14.dp)
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.poster).crossfade(false).build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(225.dp)
                    )
                }
            }
        }
    }
}
