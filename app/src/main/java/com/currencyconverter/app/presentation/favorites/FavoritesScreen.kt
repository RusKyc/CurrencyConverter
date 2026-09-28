package com.currencyconverter.app.presentation.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.presentation.components.CurrencyButton
import com.currencyconverter.app.presentation.components.CurrencyPickerSheet
import com.currencyconverter.app.presentation.components.MessageCard

data class FavoritesActions(
    val onOpen: (CurrencyPair) -> Unit = {},
    val onMoveUp: (CurrencyPair) -> Unit = {},
    val onMoveDown: (CurrencyPair) -> Unit = {},
    val onRemove: (CurrencyPair) -> Unit = {},
    val onAdd: (Currency, Currency) -> Unit = { _, _ -> },
    val search: (String) -> List<Currency> = { emptyList() },
)

@Composable
fun FavoritesScreen(
    onOpenConverter: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FavoritesContent(
        state = state,
        currencies = viewModel.currencies,
        actions = FavoritesActions(
            onOpen = { pair -> viewModel.onOpen(pair, onOpenConverter) },
            onMoveUp = viewModel::onMoveUp,
            onMoveDown = viewModel::onMoveDown,
            onRemove = viewModel::onRemove,
            onAdd = viewModel::onAdd,
            search = viewModel::search,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesContent(
    state: FavoritesUiState,
    currencies: List<Currency>,
    actions: FavoritesActions,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_pair)) },
                modifier = Modifier.testTag("add_pair_fab"),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.isLoaded -> Unit
                state.rows.isEmpty() -> MessageCard(
                    icon = Icons.Rounded.StarBorder,
                    title = stringResource(R.string.favorites_empty_title),
                    body = stringResource(R.string.favorites_empty_body),
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .testTag("favorites_empty"),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("favorites_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.rows, key = { "${it.pair.from}_${it.pair.to}" }) { row ->
                        FavoriteRowCard(row, actions, Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPairDialog(
            currencies = currencies,
            search = actions.search,
            onConfirm = { from, to ->
                actions.onAdd(from, to)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun FavoriteRowCard(row: FavoriteRow, actions: FavoritesActions, modifier: Modifier = Modifier) {
    val name = "${row.from.code}/${row.to.code}"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag("favorite_${row.pair.from}_${row.pair.to}")
                    .heightIn(min = 72.dp)
                    .clickable { actions.onOpen(row.pair) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (row.rate != null) {
                            "$name, ${row.rate}"
                        } else {
                            name
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(row.from.flag + row.to.flag, style = MaterialTheme.typography.titleLarge)
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    row.rate?.let {
                        Text(
                            text = stringResource(R.string.rate_line, row.from.code, it, row.to.code),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(
                onClick = { actions.onMoveUp(row.pair) },
                enabled = row.canMoveUp,
                modifier = Modifier.size(48.dp).testTag("move_up_${row.pair.from}_${row.pair.to}"),
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up, name))
            }
            IconButton(
                onClick = { actions.onMoveDown(row.pair) },
                enabled = row.canMoveDown,
                modifier = Modifier.size(48.dp).testTag("move_down_${row.pair.from}_${row.pair.to}"),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down, name))
            }
            IconButton(
                onClick = { actions.onRemove(row.pair) },
                modifier = Modifier.size(48.dp).testTag("remove_${row.pair.from}_${row.pair.to}"),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.remove_favorite, name))
            }
        }
    }
}

private enum class PickTarget { From, To }

/** Dialog with two currency selectors. While a currency is being picked the dialog is replaced by the picker sheet. */
@Composable
private fun AddPairDialog(
    currencies: List<Currency>,
    search: (String) -> List<Currency>,
    onConfirm: (Currency, Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromCode by rememberSaveable { mutableStateOf(currencies.firstOrNull()?.code) }
    var toCode by rememberSaveable { mutableStateOf(currencies.getOrNull(1)?.code) }
    var picking by rememberSaveable { mutableStateOf<PickTarget?>(null) }

    val from = currencies.firstOrNull { it.code == fromCode }
    val to = currencies.firstOrNull { it.code == toCode }

    val target = picking
    if (target != null) {
        CurrencyPickerSheet(
            currencies = currencies,
            selectedCode = if (target == PickTarget.From) fromCode else toCode,
            search = search,
            onSelect = { chosen ->
                if (target == PickTarget.From) {
                    fromCode = chosen.code
                    if (toCode == chosen.code) toCode = currencies.firstOrNull { it.code != chosen.code }?.code
                } else {
                    toCode = chosen.code
                    if (fromCode == chosen.code) fromCode = currencies.firstOrNull { it.code != chosen.code }?.code
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (from != null) {
                    Text(stringResource(R.string.add_pair_from), style = MaterialTheme.typography.labelLarge)
                    CurrencyButton(
                        currency = from,
                        description = "${stringResource(R.string.add_pair_from)}: ${from.displayName}",
                        onClick = { picking = PickTarget.From },
                        modifier = Modifier.fillMaxWidth().testTag("add_pair_from"),
                    )
                }
                if (to != null) {
                    Text(stringResource(R.string.add_pair_to), style = MaterialTheme.typography.labelLarge)
                    CurrencyButton(
                        currency = to,
                        description = "${stringResource(R.string.add_pair_to)}: ${to.displayName}",
                        onClick = { picking = PickTarget.To },
                        modifier = Modifier.fillMaxWidth().testTag("add_pair_to"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (from != null && to != null) onConfirm(from, to) },
                enabled = from != null && to != null && from.code != to.code,
                modifier = Modifier.testTag("add_pair_confirm"),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
