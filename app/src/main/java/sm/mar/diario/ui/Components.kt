package sm.mar.diario.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun Label(text: String, color: Color = LocalPalette.current.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun IconBadge(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)) }
}

@Composable
fun Mono(text: String, color: Color = LocalPalette.current.muted, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = color, modifier = modifier)
}

@Composable
fun EmptyState(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun Stat(value: Int, label: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value.toString(), style = MaterialTheme.typography.titleLarge,
            color = if (warn && value > 0) LocalPalette.current.warn else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted, modifier = Modifier.padding(bottom = 3.dp))
    }
}

fun pickDate(ctx: Context, current: String, onPick: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(current) }.getOrDefault(LocalDate.now())
    DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

fun pickTime(ctx: Context, current: String, onPick: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.now())
    TimePickerDialog(ctx, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, true).show()
}
