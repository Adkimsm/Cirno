package nep.timeline.cirno.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs

private val presetAdjs = intArrayOf(0, 100, 200, 500, 800, 900, 999)

fun backgroundOomAdjSelectedIndex(adj: Int): Int {
    if (adj == AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT) {
        return 0
    }
    val presetIndex = presetAdjs.indexOf(adj)
    return if (presetIndex >= 0) presetIndex + 1 else presetAdjs.size + 1
}

fun backgroundOomAdjForPresetIndex(index: Int): Int? {
    return when (index) {
        0 -> AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT
        in 1..presetAdjs.size -> presetAdjs[index - 1]
        else -> null
    }
}

@Composable
fun backgroundOomAdjItems(currentAdj: Int): List<String> {
    val customText = if (AppConfigs.isValidBackgroundOomAdj(currentAdj) && currentAdj !in presetAdjs) {
        stringResource(R.string.background_oom_level_custom_value, currentAdj)
    } else {
        stringResource(R.string.background_oom_level_custom)
    }
    return listOf(
        stringResource(R.string.background_oom_level_default),
        stringResource(R.string.background_oom_level_keep_alive),
        stringResource(R.string.background_oom_level_visible),
        stringResource(R.string.background_oom_level_perceptible),
        stringResource(R.string.background_oom_level_service),
        stringResource(R.string.background_oom_level_service_b),
        stringResource(R.string.background_oom_level_cached),
        stringResource(R.string.background_oom_level_low),
        customText,
    )
}

fun backgroundOomAdjSummary(adj: Int): String? {
    return if (AppConfigs.isValidBackgroundOomAdj(adj)) adj.toString() else null
}

@Composable
fun BackgroundOomAdjCustomDialog(
    initialAdj: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by remember(initialAdj) {
        mutableStateOf(if (AppConfigs.isValidBackgroundOomAdj(initialAdj)) initialAdj.toString() else "")
    }
    val adj = input.toIntOrNull()
    val valid = adj != null && AppConfigs.isValidBackgroundOomAdj(adj)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.background_oom_level_custom)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() }.take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.background_oom_level_custom_hint)) },
                    supportingText = { Text(stringResource(R.string.background_oom_level_custom_range)) },
                    isError = input.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    if (adj != null) {
                        onConfirm(adj)
                    }
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
