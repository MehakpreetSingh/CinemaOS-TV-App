package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.components.CinemaGlass
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * CinemaOS details "facts" panel: 16dp radius, black@30% fill, white@10% border,
 * grid of tiny uppercase labels over semibold values with hairline dividers.
 */
@Composable
internal fun DetailFactsPanel(meta: Meta, modifier: Modifier = Modifier) {
    val labelReleased = stringResource(R.string.detail_fact_released)
    val labelRuntime = stringResource(R.string.detail_fact_runtime)
    val labelStatus = stringResource(R.string.detail_fact_status)
    val labelRating = stringResource(R.string.detail_fact_rating)
    val labelCountry = stringResource(R.string.detail_fact_country)
    val labelLanguage = stringResource(R.string.detail_fact_language)
    val labelDirector = stringResource(R.string.detail_fact_director)
    val labelWriter = stringResource(R.string.detail_fact_writer)
    val labelAwards = stringResource(R.string.detail_fact_awards)
    val facts = remember(meta, labelReleased) {
        buildList {
            fun add(label: String, value: String?) {
                value?.trim()?.takeIf { it.isNotEmpty() }?.let { add(label to it) }
            }
            add(labelReleased, meta.released?.take(10) ?: meta.releaseInfo)
            add(labelRuntime, meta.runtime)
            add(labelStatus, meta.status)
            add(labelRating, meta.ageRating)
            add(labelCountry, meta.country)
            add(labelLanguage, meta.language?.uppercase())
            add(labelDirector, meta.director.joinToString(", "))
            add(labelWriter, meta.writer.joinToString(", "))
            add(labelAwards, meta.awards)
        }
    }
    if (facts.isEmpty()) return
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 52.dp, top = 32.dp, bottom = 32.dp)
            .background(Color(0xFF18181B).copy(alpha = 0.62f), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        val rows = facts.chunked(4)
        rows.forEachIndexed { rowIndex, rowFacts ->
            if (rowIndex > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .height(1.dp)
                        .background(CinemaGlass.BorderSubtle)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                rowFacts.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = label.uppercase(),
                            style = NuvioTheme.textStyles.eyebrow,
                            color = Color.White.copy(alpha = 0.4f),
                            maxLines = 1
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(4 - rowFacts.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
