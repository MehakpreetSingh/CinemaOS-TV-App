package com.nuvio.tv.ui.screens.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.home.apple.AppleFocusSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvidersRowViewModel @Inject constructor(
    private val logoRepository: ProviderLogoRepository
) : ViewModel() {
    private val _logos = MutableStateFlow<Map<String, String>>(emptyMap())
    val logos: StateFlow<Map<String, String>> = _logos.asStateFlow()

    init {
        viewModelScope.launch { _logos.value = logoRepository.logos() }
    }
}

/**
 * "Providers" row for the CinemaOS Home: one rounded logo tile per streaming service from the
 * Discover "Streaming on" list. Remembers the tile that opened a Provider page and puts focus back on
 * it when Home is shown again.
 */
@Composable
fun ProvidersHomeRow(
    onProviderClick: (StreamingProvider) -> Unit,
    modifier: Modifier = Modifier,
    rowInset: androidx.compose.ui.unit.Dp = 48.dp,
    viewModel: ProvidersRowViewModel = hiltViewModel()
) {
    val logos by viewModel.logos.collectAsStateWithLifecycle()
    val providers = CinemaosStreamingProviders.all
    val requesters = remember { providers.associate { it.id to FocusRequester() } }
    var openedProviderId by rememberSaveable { mutableStateOf<String?>(null) }

    // Back from a Provider page: focus the tile that opened it (once).
    LaunchedEffect(Unit) {
        val target = openedProviderId ?: return@LaunchedEffect
        requesters[target]?.requestFocusAfterFrames(frames = 4)
        openedProviderId = null
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = rowInset, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFAFAFA))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cinemaos_providers_row_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = Color.White
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = rowInset, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items = providers, key = { "provider_${it.id}" }, contentType = { "providerTile" }) { provider ->
                ProviderLogoTile(
                    provider = provider,
                    logoUrl = logos[provider.id],
                    modifier = Modifier.focusRequester(requesters.getValue(provider.id)),
                    onClick = {
                        openedProviderId = provider.id
                        onProviderClick(provider)
                    }
                )
            }
        }
    }
}

@Composable
internal fun ProviderLogoTile(
    provider: StreamingProvider,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppleFocusSurface(
            onClick = onClick,
            onFocused = onFocused,
            shape = shape,
            containerColor = Color(0xFF1C1C1E),
            focusedContainerColor = Color(0xFF2C2C2E),
            modifier = modifier.width(150.dp).height(84.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.06f),
                            1f to Color.Transparent
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = provider.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(
                        text = provider.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = provider.name,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
