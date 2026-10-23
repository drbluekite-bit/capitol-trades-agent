package com.adamway.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamway.app.data.SavedAddress

private data class LetterGroup(val label: String, val range: IntRange)

private val LETTER_GROUPS = listOf(
    LetterGroup("A–E", 'A'.code..'E'.code),
    LetterGroup("F–J", 'F'.code..'J'.code),
    LetterGroup("K–O", 'K'.code..'O'.code),
    LetterGroup("P–T", 'P'.code..'T'.code),
    LetterGroup("U–Z", 'U'.code..'Z'.code),
)

/**
 * One-tap favorite chips, with an A–Z group filter above them once there's
 * enough favorites that a single row gets unwieldy to scan — tap a group
 * to narrow the chips to names starting with one of its letters, tap it
 * again to go back to showing all of them. Used identically from the Home
 * and Queue screens, so favorites are findable the same way from either.
 */
@Composable
fun FavoritesQuickAdd(
    favorites: List<SavedAddress>,
    onQuickAdd: (SavedAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) return

    var selectedGroup by remember { mutableStateOf<IntRange?>(null) }

    Column(modifier = modifier) {
        Text(
            "Favorites — tap to add:",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (favorites.size > 5) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(LETTER_GROUPS.size) { i ->
                    val group = LETTER_GROUPS[i]
                    FilterChip(
                        selected = selectedGroup == group.range,
                        onClick = { selectedGroup = if (selectedGroup == group.range) null else group.range },
                        label = { Text(group.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val visible = favorites.filter { favorite ->
            val range = selectedGroup ?: return@filter true
            val firstLetter = favorite.favoriteLabel.orEmpty().trim().firstOrNull()?.uppercaseChar()?.code
            firstLetter != null && firstLetter in range
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible.size) { i ->
                val favorite = visible[i]
                AssistChip(
                    onClick = { onQuickAdd(favorite) },
                    label = { Text(favorite.favoriteLabel.orEmpty()) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                )
            }
        }
    }
}
