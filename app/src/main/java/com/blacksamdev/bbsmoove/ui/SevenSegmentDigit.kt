package com.blacksamdev.bbsmoove.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Un chiffre 7-segments, dessiné dans un repère logique 44 x 84 (mêmes
 * coordonnées que la maquette HTML — voir bbs-speed-hud-mockup.html).
 *
 * Règles de géométrie reprises de la maquette :
 *  - Les segments verticaux (f,b,e,c) vont jusqu'aux bords haut/bas (y=0 et y=84),
 *    pas juste jusqu'au bord du segment horizontal -> évite l'effet "chiffre
 *    plus petit" sur le 1, le 4, le 7 (bug corrigé en maquette).
 *  - f/e et b/c se rejoignent en une pointe exacte à y=42 (pas de trou).
 *  - Le chiffre "1" est un cas spécial : centré (pas collé à droite) pour
 *    équilibrer son poids visuel face aux autres chiffres.
 */
private const val W = 44f
private const val H = 84f

private val SEGMENTS: Map<Char, List<Offset>> = mapOf(
    'a' to listOf(Offset(0f, 0f), Offset(44f, 0f), Offset(44f, 10f), Offset(0f, 10f)),
    'g' to listOf(Offset(0f, 37f), Offset(44f, 37f), Offset(44f, 47f), Offset(0f, 47f)),
    'd' to listOf(Offset(0f, 74f), Offset(44f, 74f), Offset(44f, 84f), Offset(0f, 84f)),
    'f' to listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 36f), Offset(5f, 42f), Offset(0f, 36f)),
    'b' to listOf(Offset(34f, 0f), Offset(44f, 0f), Offset(44f, 36f), Offset(39f, 42f), Offset(34f, 36f)),
    'e' to listOf(Offset(0f, 48f), Offset(5f, 42f), Offset(10f, 48f), Offset(10f, 84f), Offset(0f, 84f)),
    'c' to listOf(Offset(34f, 48f), Offset(39f, 42f), Offset(44f, 48f), Offset(44f, 84f), Offset(34f, 84f)),
)

// Cas spécial du "1" : barre verticale centrée plutôt que collée à droite
private val ONE_TOP = listOf(Offset(17f, 0f), Offset(27f, 0f), Offset(27f, 36f), Offset(22f, 42f), Offset(17f, 36f))
private val ONE_BOTTOM = listOf(Offset(17f, 48f), Offset(22f, 42f), Offset(27f, 48f), Offset(27f, 84f), Offset(17f, 84f))

private val DIGIT_SEGMENTS: Map<Int, Set<Char>> = mapOf(
    0 to setOf('a', 'b', 'c', 'd', 'e', 'f'),
    1 to setOf('b', 'c'), // remplacé par le rendu centré ci-dessous
    2 to setOf('a', 'b', 'g', 'e', 'd'),
    3 to setOf('a', 'b', 'g', 'c', 'd'),
    4 to setOf('f', 'g', 'b', 'c'),
    5 to setOf('a', 'f', 'g', 'c', 'd'),
    6 to setOf('a', 'f', 'g', 'e', 'c', 'd'),
    7 to setOf('a', 'b', 'c'),
    8 to setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
    9 to setOf('a', 'b', 'c', 'd', 'f', 'g'),
)

private const val GHOST_ALPHA = 0.045f

private fun polygonPath(points: List<Offset>, scaleX: Float, scaleY: Float): Path =
    Path().apply {
        points.forEachIndexed { i, p ->
            val x = p.x * scaleX
            val y = p.y * scaleY
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

private fun DrawScope.drawDigit(value: Int, color: Color) {
    val scaleX = size.width / W
    val scaleY = size.height / H
    val active = DIGIT_SEGMENTS[value] ?: emptySet()

    if (value == 1) {
        drawPath(polygonPath(ONE_TOP, scaleX, scaleY), color = color)
        drawPath(polygonPath(ONE_BOTTOM, scaleX, scaleY), color = color)
        SEGMENTS.forEach { (_, pts) ->
            drawPath(polygonPath(pts, scaleX, scaleY), color = color.copy(alpha = GHOST_ALPHA))
        }
        return
    }

    SEGMENTS.forEach { (key, pts) ->
        val on = key in active
        val c = if (on) color else color.copy(alpha = GHOST_ALPHA)
        drawPath(polygonPath(pts, scaleX, scaleY), color = c)
    }
}

/** Un seul chiffre, hauteur pilotée par le parent, largeur déduite du ratio W/H. */
@Composable
fun SevenSegmentDigit(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 250),
        label = "digitColor",
    )
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(W / H),
    ) {
        drawDigit(value, animatedColor)
    }
}

/**
 * La rangée complète du compteur (ex: "61" -> ['6','1']), avec l'espacement
 * entre chiffres qu'on a validé dans la maquette ("écarter les chiffres").
 */
@Composable
fun SevenSegmentNumber(
    speedKmh: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val digits = speedKmh.coerceAtLeast(0).toString().map { it - '0' }
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        digits.forEach { d ->
            SevenSegmentDigit(value = d, color = color)
        }
    }
}
