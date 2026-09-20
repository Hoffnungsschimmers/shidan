package com.fanji.mealnote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.displayName

/**
 * Shared three-way verdict selector.
 *
 * AddVisit and EditVisit previously each carried an identical copy.
 * Same choice must feel identical in both places, so one component owns
 * the background fade, border reveal and subtle scale.
 */
@Composable
fun VerdictSelector(
    selected: Verdict,
    onSelect: (Verdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerdictOption(Verdict.GOOD, Icons.Rounded.SentimentSatisfiedAlt, selected == Verdict.GOOD, Modifier.weight(1f)) { onSelect(Verdict.GOOD) }
        VerdictOption(Verdict.MEH, Icons.Rounded.SentimentNeutral, selected == Verdict.MEH, Modifier.weight(1f)) { onSelect(Verdict.MEH) }
        VerdictOption(Verdict.BAD, Icons.Rounded.SentimentDissatisfied, selected == Verdict.BAD, Modifier.weight(1f)) { onSelect(Verdict.BAD) }
    }
}

@Composable
private fun VerdictOption(
    verdict: Verdict,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.primaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.tertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.onTertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.onErrorContainer
    }
    val accent = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.primary
        Verdict.MEH -> MaterialTheme.colorScheme.tertiary
        Verdict.BAD -> MaterialTheme.colorScheme.error
    }
    val background by animateColorAsState(
        targetValue = if (selected) container else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictBg",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictFg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "verdictScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(26.dp))
            Text(verdict.displayName(), style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}
