package com.currencyconverter.app.presentation.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.presentation.common.label
import com.currencyconverter.app.presentation.components.CurrencyPickerSheet

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBasePicker by rememberSaveable { mutableStateOf(false) }

    SettingsContent(
        state = state,
        refreshIntervals = viewModel.refreshIntervals,
        decimalOptions = viewModel.decimalOptions,
        onPickBaseCurrency = { showBasePicker = true },
        onThemeSelected = viewModel::onThemeSelected,
        onRefreshIntervalSelected = viewModel::onRefreshIntervalSelected,
        onDecimalPlacesSelected = viewModel::onDecimalPlacesSelected,
    )

    if (showBasePicker) {
        CurrencyPickerSheet(
            currencies = viewModel.currencies,
            selectedCode = state.settings.defaultBase,
            search = viewModel::search,
            onSelect = {
                viewModel.onDefaultBaseSelected(it)
                showBasePicker = false
            },
            onDismiss = { showBasePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    refreshIntervals: List<RefreshInterval>,
    decimalOptions: List<DecimalPlaces>,
    onPickBaseCurrency: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onRefreshIntervalSelected: (RefreshInterval) -> Unit,
    onDecimalPlacesSelected: (DecimalPlaces) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_general))

            SettingBlock(
                title = stringResource(R.string.settings_default_base),
                summary = stringResource(R.string.settings_default_base_summary),
            ) {
                BaseCurrencyRow(state.baseCurrency, onPickBaseCurrency)
            }

            SettingBlock(title = stringResource(R.string.settings_theme)) {
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.settings.themeMode == mode,
                            onClick = { onThemeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("theme_${mode.name}"),
                        ) { Text(mode.label()) }
                    }
                }
            }

            SettingBlock(
                title = stringResource(R.string.settings_refresh_interval),
                summary = stringResource(R.string.settings_refresh_interval_summary),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    refreshIntervals.forEach { interval ->
                        FilterChip(
                            selected = state.settings.refreshInterval == interval,
                            onClick = { onRefreshIntervalSelected(interval) },
                            label = { Text(interval.label()) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("interval_${interval.name}"),
                        )
                    }
                }
            }

            SettingBlock(
                title = stringResource(R.string.settings_decimals),
                summary = stringResource(R.string.settings_decimals_summary),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    decimalOptions.forEach { option ->
                        FilterChip(
                            selected = state.settings.decimalPlaces == option,
                            onClick = { onDecimalPlacesSelected(option) },
                            label = { Text(option.label()) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(
                                    "decimals_" + when (option) {
                                        DecimalPlaces.Auto -> "auto"
                                        is DecimalPlaces.Fixed -> option.count.toString()
                                    },
                                ),
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_about))

            SettingBlock(title = stringResource(R.string.app_name)) {
                Text(
                    text = stringResource(R.string.settings_version) + ": " + state.appInfo.versionName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("app_version"),
                )
            }

            SettingBlock(title = stringResource(R.string.settings_data_source)) {
                Text(
                    text = state.appInfo.ratesProviderName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("data_source"),
                )
                Text(
                    text = state.appInfo.ratesProviderUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun BaseCurrencyRow(currency: Currency, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("default_base_row")
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(currency.flag, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(currency.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    currency.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
        }
    }
}
