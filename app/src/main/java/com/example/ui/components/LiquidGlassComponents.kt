package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLiquidGlass

/**
 * Animated ambient liquid mesh background with slowly breathing translucent aurora orbs.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_mesh")
    val drift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift1"
    )
    val drift2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(glass.backdropStart, glass.backdropEnd)
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                val orb1Center = Offset(w * (0.18f + 0.14f * drift1), h * (0.14f + 0.08f * drift2))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.ambientOrbOne, Color.Transparent),
                        center = orb1Center,
                        radius = w * 0.72f
                    ),
                    center = orb1Center,
                    radius = w * 0.72f
                )

                val orb2Center = Offset(w * (0.82f - 0.14f * drift2), h * (0.45f + 0.12f * drift1))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.ambientOrbTwo, Color.Transparent),
                        center = orb2Center,
                        radius = w * 0.78f
                    ),
                    center = orb2Center,
                    radius = w * 0.78f
                )

                val orb3Center = Offset(w * (0.35f + 0.18f * drift1), h * (0.84f - 0.08f * drift2))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.ambientOrbThree, Color.Transparent),
                        center = orb3Center,
                        radius = w * 0.65f
                    ),
                    center = orb3Center,
                    radius = w * 0.65f
                )
            },
        content = content
    )
}

/**
 * Frosted Liquid Glass card with specular gradient border, subtle top highlight sheen,
 * and smooth spring press feedback when clickable.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    highlightAccent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glass_press_scale"
    )

    val fillBrush = Brush.linearGradient(
        colors = listOf(
            highlightAccent?.copy(alpha = if (glass.isDark) 0.18f else 0.14f) ?: glass.glassFillTop,
            glass.glassFillBottom
        ),
        start = Offset.Zero,
        end = Offset(600f, 600f)
    )

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            glass.specularHighlight,
            highlightAccent?.copy(alpha = 0.45f) ?: glass.specularShadow,
            glass.specularHighlight.copy(alpha = 0.25f)
        ),
        start = Offset.Zero,
        end = Offset(800f, 800f)
    )

    val clickableModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (glass.isDark) 12.dp else 10.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.1.dp, brush = borderBrush, shape = shape)
            .then(clickableModifier)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

@Composable
fun GlassStatusPill(
    text: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val glass = LocalLiquidGlass.current
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
            } else Modifier
        ),
        shape = CircleShape,
        color = tint.copy(alpha = if (glass.isDark) 0.16f else 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
fun GlassIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    size: Dp = 42.dp
) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = if (glass.isDark) 0.28f else 0.20f),
                        tint.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}
