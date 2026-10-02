package com.smartspend.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.Category
import com.smartspend.ui.common.EmojiAvatar
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.theme.CategoryPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onCategoryClick: (Long) -> Unit = {},
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, "Add category")
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No categories yet")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryGridItem(category, onClick = { editingCategory = category })
                }
            }
        }
    }

    if (showAddSheet) {
        AddCategorySheet(
            onDismiss = { showAddSheet = false },
            onSave = { name, emoji, color, budget, notes ->
                viewModel.addCategory(name, emoji, color, budget, notes)
                showAddSheet = false
            }
        )
    }

    editingCategory?.let { category ->
        EditCategorySheet(
            category = category,
            otherCategories = categories.filter { it.id != category.id },
            onDismiss = { editingCategory = null },
            onSave = { updated -> viewModel.updateCategory(updated); editingCategory = null },
            onArchive = { viewModel.archiveCategory(category.id); editingCategory = null },
            onDelete = { viewModel.deleteCategory(category); editingCategory = null },
            onMerge = { targetId -> viewModel.mergeCategories(category.id, targetId); editingCategory = null }
        )
    }
}

@Composable
private fun CategoryGridItem(category: Category, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiAvatar(category.emoji, category.colorHex, sizeDp = 48)
            Spacer(Modifier.height(8.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (category.monthlyBudget != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    formatCurrency(category.monthlyBudget, compact = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, colorHex: String, budget: Double?, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📌") }
    var selectedColor by remember { mutableStateOf(CategoryPalette.first()) }
    var budget by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("New Category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                label = { Text("Emoji") }, modifier = Modifier.width(100.dp)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Text("Color", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            ColorPickerRow(selectedColor) { selectedColor = it }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = budget, onValueChange = { budget = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monthly Budget (optional)") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val colorHex = "#%06X".format(0xFFFFFF and selectedColor.toArgb())
                    onSave(name, emoji, colorHex, budget.toDoubleOrNull(), notes.ifBlank { null })
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create Category") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCategorySheet(
    category: Category,
    otherCategories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMerge: (targetId: Long) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var emoji by remember { mutableStateOf(category.emoji) }
    var selectedColor by remember {
        mutableStateOf(
            CategoryPalette.firstOrNull { "#%06X".format(0xFFFFFF and it.toArgb()) == category.colorHex }
                ?: CategoryPalette.first()
        )
    }
    var budget by remember { mutableStateOf(category.monthlyBudget?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var notes by remember { mutableStateOf(category.notes ?: "") }
    var showMergeMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Edit Category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                label = { Text("Emoji") }, modifier = Modifier.width(100.dp),
                enabled = category.isCustom
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth(),
                enabled = category.isCustom
            )
            Spacer(Modifier.height(12.dp))

            Text("Color", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            ColorPickerRow(selectedColor) { selectedColor = it }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = budget, onValueChange = { budget = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monthly Budget (optional)") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (!category.isCustom) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Default categories can't be renamed, but budget and notes can be updated.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val colorHex = "#%06X".format(0xFFFFFF and selectedColor.toArgb())
                    onSave(
                        category.copy(
                            name = name, emoji = emoji, colorHex = colorHex,
                            monthlyBudget = budget.toDoubleOrNull(), notes = notes.ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Changes") }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                    Text(if (category.isArchived) "Unarchive" else "Archive")
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showMergeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = otherCategories.isNotEmpty()
                    ) { Text("Merge Into…") }
                    DropdownMenu(expanded = showMergeMenu, onDismissRequest = { showMergeMenu = false }) {
                        otherCategories.forEach { target ->
                            DropdownMenuItem(
                                text = { Text("${target.emoji} ${target.name}") },
                                onClick = { showMergeMenu = false; onMerge(target.id) }
                            )
                        }
                    }
                }
            }

            if (category.isCustom) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Category") }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(selected: Color, onSelect: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CategoryPalette.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(color) }
            )
        }
    }
}
