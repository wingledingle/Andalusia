package com.silpa.feature.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import com.silpa.core.design.EmptyState
import com.silpa.core.design.Spacing

/** Categories the on-device sorter files images into. */
val BankCategories = listOf(
    "All", "Faces", "Eyes", "Noses", "Mouths", "Ears", "Hands", "Hair", "Characters", "Poses",
    "Houses", "Interiors", "Landscapes", "Plants", "Animals", "Vehicles", "Props", "Textures", "Patterns",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("All") }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Image Bank") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                placeholder = { Text("Search eyes, houses, colors…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(BankCategories) { c ->
                    FilterChip(selected = c == selected, onClick = { selected = c }, label = { Text(c) })
                }
            }
            EmptyState(
                icon = Icons.Outlined.Collections,
                title = if (selected == "All") "Your Image Bank is empty" else "No $selected yet",
                body = "Share images to Silpa from any app. They'll be sorted into categories automatically.",
            )
        }
    }
}
