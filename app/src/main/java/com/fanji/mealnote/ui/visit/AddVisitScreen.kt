package com.fanji.mealnote.ui.visit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.components.CameraPhotoAction
import com.fanji.mealnote.ui.components.GalleryPhotoAction
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.MiuixTopBar
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.SelectedPhoto
import com.fanji.mealnote.ui.displayName
import com.fanji.mealnote.ui.formatMealDate
import java.io.File

/**
 * 新增用餐记录。
 *
 * 字段顺序按**用户真实的回忆顺序**排列：先说好不好吃（评价），再说什么时候（日期），
 * 然后是吃了什么、花了多少，最后才是照片和补充。评价放最前面是因为它承载的信息量最大，
 * 且是唯一一个几乎人人都会填的字段；如果放在最后，用户很容易在填完照片后就退出了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVisitScreen(
    restaurantId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddVisitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restaurantName by viewModel.restaurantName.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // 保存相机目标文件路径而非 File 对象：拍照期间进程可能被回收，
    // 普通 remember 无法保存不可序列化的 File，重建后会丢失拍摄结果的关联。
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(restaurantId) { viewModel.setRestaurantId(restaurantId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_VISIT_PHOTOS),
    ) { viewModel.onPhotosPicked(it) }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        if (path != null) {
            if (success) viewModel.onCameraPhoto(File(path)) else viewModel.onCameraCancelled(File(path))
        }
        pendingCameraPath = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        MiuixTopBar(
            title = "记录一餐",
            subtitle = restaurantName.ifBlank { null },
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "这一顿怎么样",
                        subtitle = "选一个最接近的感受",
                    )
                    VerdictSelector(selected = uiState.verdict, onSelect = viewModel::onVerdictChange)
                }
            }

            item {
                MiuixCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("用餐日期", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                uiState.dateMillis.formatMealDate(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "修改",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        MiuixTextField(
                            value = uiState.dishes,
                            onValueChange = viewModel::onDishesChange,
                            label = "吃了什么（选填）",
                            placeholder = "例如：牛肉面、小笼包",
                            minLines = 2,
                            maxLines = 4,
                            leadingIcon = Icons.Rounded.RestaurantMenu,
                        )
                        MiuixTextField(
                            value = uiState.priceText,
                            onValueChange = viewModel::onPriceChange,
                            label = "花费（选填）",
                            placeholder = "128、人均60、约200 都行",
                            singleLine = true,
                            leadingIcon = Icons.Rounded.Payments,
                            supportingText = "想到多少写多少，不确定就写个大概",
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "照片",
                        subtitle = "选填，最多 $MAX_VISIT_PHOTOS 张",
                        trailing = {
                            if (uiState.photoPaths.isNotEmpty()) {
                                Text(
                                    "${uiState.photoPaths.size} 张",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    if (uiState.isImportingPhotos) {
                        Text(
                            text = "正在导入图片…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 达到上限后隐藏添加入口，避免用户继续选择却无法保存。
                        if (uiState.canAddPhoto) {
                            item {
                                GalleryPhotoAction(onClick = {
                                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                            }
                            item {
                                CameraPhotoAction(onClick = {
                                    val (file, uri) = viewModel.createCameraTarget()
                                    pendingCameraPath = file.absolutePath
                                    cameraPicker.launch(uri)
                                })
                            }
                        }
                        itemsIndexed(uiState.photoPaths, key = { _, path -> path }) { index, path ->
                            SelectedPhoto(path, "用餐照片（选填）", onRemove = { viewModel.removePhoto(index) })
                        }
                    }
                }
            }

            item {
                MiuixTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChange,
                    label = "补充记录（选填）",
                    placeholder = "环境、服务、下次想点什么，随便写",
                    minLines = 3,
                    maxLines = 6,
                )
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }

        MiuixButton(
            label = "保存记录",
            onClick = viewModel::save,
            enabled = uiState.canSave,
            loading = uiState.isSaving,
            icon = Icons.Rounded.Check,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiState.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(viewModel::onDateChange)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.extraLarge,
        ) { DatePicker(state = state) }
    }
}

/**
 * 三选一评价选择器。
 *
 * 选中项的变化有三个同时发生的动画：底色渐变、边框渐显、以及轻微的放大。
 * 之所以不用「选中就打勾」，是因为评价是一个**程度**而非开关 ——
 * 三张卡片并排、选中者浮起，能更直观地传达「在三档里选了这一档」。
 */
@Composable
private fun VerdictSelector(selected: Verdict, onSelect: (Verdict) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerdictOption(Verdict.GOOD, Icons.Rounded.SentimentSatisfiedAlt, selected == Verdict.GOOD, Modifier.weight(1f)) { onSelect(Verdict.GOOD) }
        VerdictOption(Verdict.MEH, Icons.Rounded.SentimentNeutral, selected == Verdict.MEH, Modifier.weight(1f)) { onSelect(Verdict.MEH) }
        VerdictOption(Verdict.BAD, Icons.Rounded.SentimentDissatisfied, selected == Verdict.BAD, Modifier.weight(1f)) { onSelect(Verdict.BAD) }
    }
}

@Composable
private fun VerdictOption(
    verdict: Verdict,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.primaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.tertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.onTertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.onErrorContainer
    }
    val accent = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.primary
        Verdict.MEH -> MaterialTheme.colorScheme.tertiary
        Verdict.BAD -> MaterialTheme.colorScheme.error
    }

    val background by animateColorAsState(
        targetValue = if (selected) container else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictBg",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictFg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "verdictBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "verdictScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(26.dp))
            Text(verdict.displayName(), style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}
