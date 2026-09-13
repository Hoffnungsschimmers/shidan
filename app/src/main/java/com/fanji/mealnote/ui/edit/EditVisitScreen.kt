package com.fanji.mealnote.ui.edit

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
import androidx.compose.material3.CircularProgressIndicator
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
import com.fanji.mealnote.ui.components.PhotoViewerHost
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.SelectedPhoto
import com.fanji.mealnote.ui.components.rememberPhotoViewerState
import com.fanji.mealnote.ui.displayName
import com.fanji.mealnote.ui.formatMealDate
import java.io.File

/** 编辑一条已有的用餐记录。字段与新增页一致，额外处理加载态与「记录已被删除」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVisitScreen(
    recordId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditVisitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoViewer = rememberPhotoViewerState()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(recordId) { viewModel.setRecordId(recordId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = com.fanji.mealnote.ui.visit.MAX_VISIT_PHOTOS),
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
            title = "编辑用餐记录",
            subtitle = uiState.restaurantName.ifBlank { null },
            onBack = onBack,
        )

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            uiState.notFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这条记录已经不存在了",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).imePadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(title = "这一顿怎么样", subtitle = "选一个最接近的感受")
                            VerdictSelector(uiState.verdict, viewModel::onVerdictChange)
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
                                subtitle = "选填，最多 ${com.fanji.mealnote.ui.visit.MAX_VISIT_PHOTOS} 张",
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
                                if (uiState.canAddPhoto) {
                                    item {
                                        GalleryPhotoAction(onClick = {
                                            galleryPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                            )
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
                                    SelectedPhoto(
                                        path = path,
                                        contentDescription = "用餐照片",
                                        onRemove = { viewModel.removePhoto(index) },
                                        onPreview = { photoViewer.open(uiState.photoPaths, index) },
                                    )
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
                    label = "保存修改",
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
        }
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

    PhotoViewerHost(photoViewer)
}

/**
 * 三选一评价选择器。
 *
 * 与新增页共用同一套交互：底色渐变 + 边框渐显 + 轻微放大。
 * 保持两页一致很重要 —— 同一个「推荐」在两处的选中反馈不同会让人怀疑自己选错了。
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
