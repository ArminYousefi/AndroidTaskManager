package com.google.mytaskmanager.presentation.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.presentation.logic.ActivityFormatter
import com.google.mytaskmanager.ui.components.Avatar
import com.google.mytaskmanager.ui.components.PriorityGlyph
import com.google.mytaskmanager.ui.components.Tag
import com.google.mytaskmanager.ui.components.TypeBadge
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme

/** Due-date chip text and colour: overdue (red), today/tomorrow (amber), later (neutral). Null when no due date. */
@Composable
fun DueChip(task: Task, modifier: Modifier = Modifier) {
    val days = task.daysUntilDue() ?: return
    val date = ActivityFormatter.date(task.dueDate.toString())
    val (text, color) = when {
        days < 0 -> "Overdue · $date" to AppTheme.extra.danger
        days == 0L -> "Today" to AppTheme.extra.warning
        days == 1L -> "Tomorrow" to AppTheme.extra.warning
        else -> date to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Tag(text, color, modifier, AppIcons.Calendar)
}

/**
 * A Jira-style issue card: title, labels, then type, key, due date, priority and assignee.
 *
 * @param assignee the assignee's member entry (for the avatar), if any.
 * @param dimmed shown faded in the column while its floating copy is being dragged (the card keeps its space so
 *   nothing jumps around under the finger).
 * @param lineAbove / [lineBelow] draw the blue insertion line that shows where the dragged card will land.
 */
@Composable
fun TaskCard(task: Task, assignee: Member?, modifier: Modifier = Modifier, dimmed: Boolean = false, lineAbove: Boolean = false, lineBelow: Boolean = false) {
    val line = MaterialTheme.colorScheme.primary
    val border = if (task.isOverdue()) AppTheme.extra.danger.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column(
        modifier
            .alpha(if (dimmed) 0.28f else 1f)
            .drawWithContent {
                drawContent()
                val h = 3.dp.toPx(); val gap = 5.dp.toPx()
                if (lineAbove) drawRoundRect(line, Offset(0f, -gap - h / 2), Size(size.width, h), CornerRadius(h))
                if (lineBelow) drawRoundRect(line, Offset(0f, size.height + gap - h / 2), Size(size.width, h), CornerRadius(h))
            }
            .fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (task.labels.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                task.labels.take(2).forEach { Tag(it, MaterialTheme.colorScheme.primary) }
                if (task.labels.size > 2) Tag("+${task.labels.size - 2}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeBadge(task.type)
            Text(task.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DueChip(task)
            Spacer(Modifier.weight(1f))
            PriorityGlyph(task.priority)
            if (assignee != null) Avatar(assignee.displayName, size = 24.dp)
        }
    }
}
