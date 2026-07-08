package com.memreg.net.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.memreg.net.data.DatabaseHelper
import com.memreg.net.data.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val cityOptions = listOf("All", "ATD", "HVN", "HRP")
private val cityMap = mapOf("ATD" to "Abbottabad", "HVN" to "Havelian", "HRP" to "Haripur")

@Composable
fun SearchScreen(db: DatabaseHelper) {
    fun autoSectionLabel(q: String, cityCode: String): String {
        val first = q.firstOrNull()?.uppercase() ?: return cityCode
        val letter = if (first in "ABCDEFGHIJKLMNOPQRSTUVWXYZ") first else ""
        return if (letter.isEmpty()) cityCode else "$cityCode-$letter"
    }

    fun loadRecent(prefs: SharedPreferences, cityCode: String): List<String> {
        val raw = prefs.getString("recent_$cityCode", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|||")
    }

    var query by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("All") }
    var cityExpanded by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Record>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("memreg_recent", Context.MODE_PRIVATE) }
    var recentSearches by remember { mutableStateOf(loadRecent(prefs, "global")) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun saveRecent(q: String) {
        if (q.isBlank()) return
        val list = recentSearches.toMutableList()
        list.remove(q)
        list.add(0, q)
        if (list.size > 5) list.removeAt(5)
        recentSearches = list
        prefs.edit().putString("recent_global", list.joinToString("|||")).apply()
    }

    fun performSearch() {
        keyboardController?.hide()
        scope.launch(Dispatchers.IO) {
            try {
                val searchCity = cityMap[selectedCity]
                val res = db.search(searchCity, query, limit = 500)
                launch(Dispatchers.Main) {
                    if (query.isNotBlank() && res.isNotEmpty()) saveRecent(query)
                    results = res
                    currentIndex = 0
                    direction = 1
                }
            } catch (e: Exception) {
                Log.e("MemReg", "Search failed", e)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedTextField(
                        value = autoSectionLabel(query, selectedCity),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                                modifier = Modifier.clickable { cityExpanded = true })
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier.width(120.dp)
                    )
                    DropdownMenu(
                        expanded = cityExpanded,
                        onDismissRequest = { cityExpanded = false }
                    ) {
                        cityOptions.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    selectedCity = c
                                    cityExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = {
                        IconButton(onClick = { performSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    modifier = Modifier.weight(1f)
                )
            }

            if (results.isEmpty()) {
                if (query.isBlank() && recentSearches.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        recentSearches.forEach { q ->
                            Text(
                                text = q,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = q
                                        performSearch()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                } else if (query.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentIndex,
                        transitionSpec = {
                            val dir = direction
                            (slideInHorizontally(
                                animationSpec = tween(250),
                                initialOffsetX = { if (dir > 0) it else -it }
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(250),
                                targetOffsetX = { if (dir > 0) -it else it }
                            ))
                        },
                        label = "card",
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        if (index < results.size) {
                            RecordCard(record = results[index])
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { direction = -1; if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFBDBDBD),
                                disabledContentColor = Color(0xFFFFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Previous") }
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            text = "${currentIndex + 1} of ${results.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Button(
                            onClick = { direction = 1; if (currentIndex < results.size - 1) currentIndex++ },
                            enabled = currentIndex < results.size - 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFBDBDBD),
                                disabledContentColor = Color(0xFFFFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: Record) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            record.displayFields.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
