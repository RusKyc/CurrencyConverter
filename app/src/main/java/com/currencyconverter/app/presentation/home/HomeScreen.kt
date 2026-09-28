package com.currencyconverter.app.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.presentation.common.DataFreshness
import com.currencyconverter.app.presentation.common.updateStatusText
import com.currencyconverter.app.presentation.common.userMessage
import com.currencyconverter.app.presentation.components.AmountField
import com.currencyconverter.app.presentation.components.CurrencyButton
import com.currencyconverter.app.presentation.components.CurrencyPickerSheet
import com.currencyconverter.app.presentation.components.ErrorBanner
import com.currencyconverter.app.presentation.components.MessageCard

private const val STACK_FONT_SCALE = 1.4f

/** Callbacks the stateless [HomeContent] needs; grouped so tests can supply no-ops easily. */
data class HomeActions(
    val onAmountChange: (AmountSide, String) -> Unit = { _, _ -> },
    val sanitizeAmount: (String) -> String = { it },
    val onPickCurrency: (AmountSide) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onFavoriteClick: (CurrencyPair) -> Unit = {},
    val onRefresh: () -> Unit = {},
)

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickerSide by rememberSaveable { mutableStateOf<AmountSide?>(null) }

    val throttledMessage = stringResource(R.string.refresh_throttled)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.RefreshThrottled -> snackbarHostState.showSnackbar(throttledMessage)
            }
        }
    }

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { }
    }

    HomeContent(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = HomeActions(
            onAmountChange = viewModel::onAmountChange,
            sanitizeAmount = viewModel::sanitizeAmount,
            onPickCurrency = { pickerSide = it },
            onSwap = viewModel::onSwap,
            onToggleFavorite = viewModel::onToggleFavorite,
            onFavoriteClick = viewModel::onFavoriteSelected,
            onRefresh = viewModel::onRefresh,
        ),
    )

    pickerSide?.let { side ->
        CurrencyPickerSheet(
            currencies = viewModel.currencies,
            selectedCode = if (side == AmountSide.From) state.from.code else state.to.code,
            search = viewModel::search,
            onSelect = {
                viewModel.onCurrencySelected(side, it.code)
                pickerSide = null
            },
            onDismiss = { pickerSide = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "pair") { PairHeader(state, actions) }
                item(key = "converter") { ConverterCard(state, actions) }
                item(key = "rate") { RateSection(state, actions) }
                item(key = "status") { StatusSection(state, actions) }
                favoritesSection(state, actions)
            }
        }
    }
}

@Composable
private fun PairHeader(state: HomeUiState, actions: HomeActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pair_title, state.from.code, state.to.code),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
                .testTag("pair_title"),
        )
        val description = if (state.isFavorite) {
            stringResource(R.string.remove_from_favorites, state.from.code, state.to.code)
        } else {
            stringResource(R.string.add_to_favorites, state.from.code, state.to.code)
        }
        IconButton(
            onClick = actions.onToggleFavorite,
            modifier = Modifier
                .size(48.dp)
                .testTag("favorite_toggle"),
        ) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = description,
                tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConverterCard(state: HomeUiState, actions: HomeActions) {
    val swapRotation by animateFloatAsState(
        targetValue = if (state.activeSide == AmountSide.From) 0f else 180f,
        label = "swapRotation",
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurrencyAmountRow(
                currency = state.from,
                text = state.fromText,
                side = AmountSide.From,
                actions = actions,
                buttonDescription = stringResource(R.string.select_currency_from, state.from.displayName),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = actions.onSwap,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("swap_button"),
                ) {
                    Icon(
                        Icons.Rounded.SwapVert,
                        contentDescription = stringResource(R.string.swap_currencies),
                        modifier = Modifier.rotate(swapRotation),
                    )
                }
            }
            CurrencyAmountRow(
                currency = state.to,
                text = state.toText,
                side = AmountSide.To,
                actions = actions,
                buttonDescription = stringResource(R.string.select_currency_to, state.to.displayName),
            )
        }
    }
}

@Composable
private fun CurrencyAmountRow(
    currency: com.currencyconverter.app.domain.model.Currency,
    text: String,
    side: AmountSide,
    actions: HomeActions,
    buttonDescription: String,
) {
    val tag = if (side == AmountSide.From) "from" else "to"
    // With large accessibility fonts a side-by-side layout would squeeze the number; stack instead.
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val button: @Composable (Modifier) -> Unit = { modifier ->
        CurrencyButton(
            currency = currency,
            description = buttonDescription,
            onClick = { actions.onPickCurrency(side) },
            modifier = modifier.testTag("currency_button_$tag"),
        )
    }
    val field: @Composable (Modifier) -> Unit = { modifier ->
        AmountField(
            text = text,
            onTextChange = { actions.onAmountChange(side, it) },
            sanitize = actions.sanitizeAmount,
            label = stringResource(R.string.amount_label, currency.code),
            modifier = modifier.testTag("amount_$tag"),
        )
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            button(Modifier.fillMaxWidth())
            field(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            button(Modifier)
            field(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RateSection(state: HomeUiState, actions: HomeActions) {
    Crossfade(
        targetState = state.content,
        label = "rateContent",
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) { content ->
        when (content) {
            RatesContent.Loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .testTag("rates_loading"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.loading_rates), style = MaterialTheme.typography.bodyLarge)
            }

            is RatesContent.Success -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_line, content.rate.fromCode, content.rate.rate, content.rate.toCode),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("rate_text"),
                )
                Text(
                    text = stringResource(R.string.rate_line, content.rate.toCode, content.rate.inverseRate, content.rate.fromCode),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("inverse_rate_text"),
                )
            }

            RatesContent.Empty -> MessageCard(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.empty_rates_title),
                body = stringResource(R.string.empty_rates_body),
                modifier = Modifier.testTag("rates_empty"),
            )

            is RatesContent.Error -> MessageCard(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.error_title),
                body = content.error.userMessage(),
                actionLabel = stringResource(R.string.retry),
                onAction = actions.onRefresh,
                actionTestTag = "retry_button",
                modifier = Modifier.testTag("rates_error"),
            )
        }
    }
}

@Composable
private fun StatusSection(state: HomeUiState, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.updateInfo?.let { info ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (info.freshness == DataFreshness.Live) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = updateStatusText(info.updatedAt, state.now, info.freshness),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("update_status"),
                )
                RefreshButton(isRefreshing = state.isRefreshing, onClick = actions.onRefresh)
            }
        }

        AnimatedVisibility(visible = state.refreshError != null && state.updateInfo != null) {
            state.refreshError?.let { error ->
                ErrorBanner(
                    message = error.userMessage() + " " + stringResource(R.string.error_showing_saved),
                    modifier = Modifier.testTag("error_banner"),
                )
            }
        }
    }
}

@Composable
private fun RefreshButton(isRefreshing: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = !isRefreshing,
        modifier = Modifier
            .size(48.dp)
            .testTag("refresh_button"),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh_rates))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoritesSection(
    state: HomeUiState,
    actions: HomeActions,
) {
    item(key = "favorites_header") {
        Text(
            text = stringResource(R.string.favorite_pairs),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.favorites.isEmpty()) {
        item(key = "favorites_hint") {
            Text(
                text = stringResource(R.string.favorites_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("favorites_hint"),
            )
        }
    } else {
        items(state.favorites, key = { "fav_${it.pair.from}_${it.pair.to}" }) { item ->
            FavoriteCard(item = item, onClick = { actions.onFavoriteClick(item.pair) })
        }
    }
}

@Composable
fun FavoriteCard(item: FavoriteItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (item.rate != null) {
        stringResource(R.string.favorite_item_description, item.from.code, item.to.code, item.rate)
    } else {
        stringResource(R.string.favorite_item_no_rate, item.from.code, item.to.code)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("favorite_${item.pair.from}_${item.pair.to}")
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.from.flag + item.to.flag, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${item.from.code}/${item.to.code}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.rate?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
