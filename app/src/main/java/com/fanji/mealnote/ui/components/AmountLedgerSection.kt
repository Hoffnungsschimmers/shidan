package com.fanji.mealnote.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.formatLedgerAmount
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 「入账金额」确认条。
 *
 * 记账的输入原则：**默认零操作**。绝大多数情况下金额能从花费文本自动识别
 * （`128` → ¥128、`人均60` × 3 人 → ¥180），这里只是把结果亮出来给用户过目；
 * 识别不出、或识别错了，才展开手动输入。用户手动改过之后显示「已手动」，
 * 并提供恢复自动的出口——绝不让用户对着一个空数字框发呆。
 *
 * 人数是金额的换算因子（人均 × 人数 = 桌价），因此与金额放在同一张卡里。
 */
@Composable
fun AmountLedgerSection(
    amountMinor: Long?,
    personCount: Int,
    overridden: Boolean,
    onPersonCountChange: (Int) -> Unit,
    onAmountManualSet: (Long?) -> Unit,
    onAmountAutoRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftError by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "入账金额",
            subtitle = if (overridden) "已手动指定，不随花费文本变化"
            else "从花费文本自动识别，识别不出可手动输入",
        )
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            PersonCountRow(count = personCount, onChange = onPersonCountChange)

            if (!editing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = amountMinor?.formatLedgerAmount() ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (amountMinor != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (amountMinor != null && personCount > 1) {
                            Text(
                                text = "人均 ${perCapitaText(amountMinor, personCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (amountMinor != null) "修改" else "手动输入",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current,
                                role = Role.Button,
                            ) {
                                draft = amountMinor
                                    ?.let { minor ->
                                        minor.toBigDecimal()
                                            .movePointLeft(2)
                                            .stripTrailingZeros()
                                            .toPlainString()
                                    }
                                    .orEmpty()
                                draftError = false
                                editing = true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            } else {
                AmountEditor(
                    draft = draft,
                    hasError = draftError,
                    showClear = overridden || amountMinor != null,
                    showRestoreAuto = overridden,
                    onDraftChange = {
                        draft = it
                        draftError = false
                    },
                    onConfirm = {
                        val cents = parseDraftToMinor(draft)
                        if (draft.isNotBlank() && cents == null) {
                            draftError = true
                        } else {
                            onAmountManualSet(cents)
                            editing = false
                        }
                    },
                    onClear = {
                        onAmountManualSet(null)
                        editing = false
                    },
                    onRestoreAuto = {
                        onAmountAutoRestore()
                        editing = false
                    },
                    onCancel = { editing = false },
                )
            }
        }
    }
}

/** 人数步进行：-/+ 各 48dp 触摸区，中间显示当前人数与「人均」提示。 */
@Composable
private fun PersonCountRow(count: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("就餐人数", style = MaterialTheme.typography.titleMedium)
            Text(
                "写「人均60」时按人数换算桌价",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepperButton(
            icon = Icons.Rounded.Remove,
            description = "减少人数",
            enabled = count > 1,
            onClick = { onChange(count - 1) },
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp),
        )
        StepperButton(
            icon = Icons.Rounded.Add,
            description = "增加人数",
            enabled = count < 20,
            onClick = { onChange(count + 1) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 手动金额编辑器：数字输入 + 确认/清除/恢复自动/取消。 */
@Composable
private fun AmountEditor(
    draft: String,
    hasError: Boolean,
    showClear: Boolean,
    showRestoreAuto: Boolean,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onRestoreAuto: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiuixTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = "金额（元）",
            placeholder = "158 或 158.5",
            singleLine = true,
            isError = hasError,
            supportingText = if (hasError) "请输入数字金额" else null,
            keyboardType = KeyboardType.Decimal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showClear) {
                MiuixButton(
                    label = "清除",
                    onClick = onClear,
                    style = MiuixButtonStyle.Outlined,
                    height = 46.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showRestoreAuto) {
                MiuixButton(
                    label = "恢复自动",
                    onClick = onRestoreAuto,
                    style = MiuixButtonStyle.Text,
                    height = 46.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            MiuixButton(
                label = "确定",
                onClick = onConfirm,
                height = 46.dp,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "取消",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** 「人均 ¥52.67」；人数非法时退化为桌价文本。 */
private fun perCapitaText(amountMinor: Long, personCount: Int): String {
    if (personCount <= 0) return amountMinor.formatLedgerAmount()
    val per = BigDecimal(amountMinor)
        .divide(BigDecimal(personCount), 0, RoundingMode.HALF_UP)
    return per.movePointLeft(2).stripTrailingZeros().toPlainString().let { "¥$it" }
}

/** 用户输入（元）→ 分。非正数或无法解析返回 null。容忍 ¥、逗号与全角数字间隔。 */
private fun parseDraftToMinor(draft: String): Long? =
    draft.trim()
        .removePrefix("¥")
        .replace(",", "")
        .replace("，", "")
        .toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.multiply(BigDecimal(100))
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.longValueExact()
