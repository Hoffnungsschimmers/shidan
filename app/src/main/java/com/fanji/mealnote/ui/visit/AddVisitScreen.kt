package com.fanji.mealnote.ui.visit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.ui.components.FormBottomGap
import com.fanji.mealnote.ui.components.FormErrorBanner
import com.fanji.mealnote.ui.components.AmountLedgerSummary
import com.fanji.mealnote.ui.components.MiuixChip
import com.fanji.mealnote.ui.businessMillisToPickerUtcMillis
import com.fanji.mealnote.ui.pickerUtcMillisToBusinessMillis
import java.time.ZoneId
import com.fanji.mealnote.ui.components.MealDateRow
import com.fanji.mealnote.ui.components.MealPhotoSection
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.AmountLedgerSection
import com.fanji.mealnote.ui.components.MiuixTopBar
import com.fanji.mealnote.ui.components.PhotoViewerHost
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.VerdictSelector
import com.fanji.mealnote.ui.components.rememberGalleryPicker
import com.fanji.mealnote.ui.components.rememberPhotoViewerState
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
    /** 熟店快记的来源记录；为 null 表示空白表单。 */
    copyFromRecordId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddVisitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restaurantName by viewModel.restaurantName.collectAsStateWithLifecycle()
    val recentDishes by viewModel.recentDishes.collectAsStateWithLifecycle()
    val photoViewer = rememberPhotoViewerState()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // 编辑器可折叠，但入账摘要始终可见；照片和补充只在需要时展开。
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showPhotos by rememberSaveable { mutableStateOf(false) }
    // 保存相机目标文件路径而非 File 对象：拍照期间进程可能被回收，
    // 普通 remember 无法保存不可序列化的 File，重建后会丢失拍摄结果的关联。
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    // 顺序不能颠倒：setCopyFrom 需要先知道目标餐厅，才能校验来源记录是否属于同一家店。
    LaunchedEffect(restaurantId, copyFromRecordId) {
        viewModel.setRestaurantId(restaurantId)
        viewModel.setCopyFrom(copyFromRecordId)
    }
    LaunchedEffect(uiState.savedRecordId) { uiState.savedRecordId?.let(onSaved) }

    val galleryPicker = rememberGalleryPicker(maxItems = MAX_VISIT_PHOTOS) { uris ->
        viewModel.onPhotosPicked(uris)
    }
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
            .statusBarsPadding()
            .imePadding(),
    ) {
        MiuixTopBar(
            title = "记录一餐",
            subtitle = restaurantName.ifBlank { null },
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
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
                MealDateRow(
                    dateMillis = uiState.dateMillis,
                    onClick = { showDatePicker = true },
                )
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
                        if (recentDishes.isNotEmpty()) {
                            Text(
                                "最近吃过 · 点选带入餐品",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentDishes, key = { it }) { dish ->
                                    MiuixChip(
                                        label = dish,
                                        onClick = { viewModel.onRecentDish(dish) },
                                        modifier = Modifier.widthIn(max = 220.dp),
                                    )
                                }
                            }
                        }
                        MiuixTextField(
                            value = uiState.priceText,
                            onValueChange = viewModel::onPriceChange,
                            label = "花费（选填）",
                            placeholder = "128、人均60、约200 都行",
                            singleLine = true,
                            leadingIcon = Icons.Rounded.Payments,
                            supportingText = "本次整桌花费；识别结果会显示在下方",
                        )
                        if (uiState.referencePriceText.isNotBlank()) {
                            Text(
                                text = "上次花费（仅供参考）：${uiState.referencePriceText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            MiuixButton(
                                label = "本次相同，带入花费",
                                onClick = viewModel::useReferencePrice,
                                style = MiuixButtonStyle.Text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (showPhotos || uiState.photoPaths.isNotEmpty() || uiState.isImportingPhotos) {
                item {
                    MealPhotoSection(
                        title = "照片",
                        subtitle = "选填，最多 $MAX_VISIT_PHOTOS 张",
                        photoPaths = uiState.photoPaths,
                        photoDescription = "用餐照片（选填）",
                        isImporting = uiState.isImportingPhotos,
                        canAddPhoto = uiState.canSave && uiState.canAddPhoto,
                        onGallery = { galleryPicker.launch() },
                        onCamera = {
                            val (file, uri) = viewModel.createCameraTarget()
                            pendingCameraPath = file.absolutePath
                            cameraPicker.launch(uri)
                        },
                        onRemove = viewModel::removePhoto,
                        onPreview = { index -> photoViewer.open(uiState.photoPaths, index) },
                    )
                }
            } else {
                item {
                    MiuixButton(
                        label = "添加照片（选填）",
                        onClick = { showPhotos = true },
                        style = MiuixButtonStyle.Text,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showMore || uiState.note.isNotBlank()) {
                item {
                    AmountLedgerSection(
                        amountMinor = uiState.effectiveAmountMinor,
                        personCount = uiState.personCount,
                        overridden = uiState.amountOverridden,
                        onPersonCountChange = viewModel::onPersonCountChange,
                        onAmountManualSet = viewModel::onAmountManualSet,
                        onAmountAutoRestore = viewModel::onAmountAutoRestore,
                    )
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
            } else {
                item {
                    AmountLedgerSummary(
                        amountMinor = uiState.effectiveAmountMinor,
                        personCount = uiState.personCount,
                        overridden = uiState.amountOverridden,
                        onEdit = { showMore = true },
                    )
                }
                item {
                    MiuixButton(
                        label = "就餐人数、金额、补充记录",
                        onClick = { showMore = true },
                        style = MiuixButtonStyle.Text,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { FormBottomGap() }
        }

        FormErrorBanner(
            message = uiState.errorMessage,
            onDismiss = viewModel::dismissError,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
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
        val zone = remember { ZoneId.systemDefault() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = businessMillisToPickerUtcMillis(uiState.dateMillis, zone),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.onDateChange(pickerUtcMillisToBusinessMillis(it, zone)) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.extraLarge,
        ) { DatePicker(state = state) }
    }

    PhotoViewerHost(photoViewer)
}
