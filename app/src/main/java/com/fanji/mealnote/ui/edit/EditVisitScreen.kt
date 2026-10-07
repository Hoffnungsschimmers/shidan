package com.fanji.mealnote.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.ui.components.FormBottomGap
import com.fanji.mealnote.ui.components.FormErrorLine
import com.fanji.mealnote.ui.components.LoadingBlock
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
    // 与新增页一致:记账/备注默认折叠。但编辑已有记录时,若这条本来就填过备注、
    // 手动改过金额或人数>1,则加载后自动展开一次,免得用户以为数据丢了(此后尊重手动开合)。
    var showMore by rememberSaveable { mutableStateOf(false) }
    var autoExpandApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(recordId) { viewModel.setRecordId(recordId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    LaunchedEffect(
        uiState.isLoading,
        uiState.notFound,
        uiState.note,
        uiState.amountOverridden,
        uiState.personCount,
    ) {
        if (!autoExpandApplied && !uiState.isLoading && !uiState.notFound) {
            if (uiState.note.isNotBlank() || uiState.amountOverridden || uiState.personCount > 1) {
                showMore = true
            }
            autoExpandApplied = true
        }
    }

    val galleryPicker = rememberGalleryPicker(
        maxItems = com.fanji.mealnote.ui.visit.MAX_VISIT_PHOTOS,
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
            uiState.isLoading -> LoadingBlock(label = "正在加载记录…")

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
                            VerdictSelector(
                                selected = uiState.verdict,
                                onSelect = viewModel::onVerdictChange,
                            )
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
                        MealPhotoSection(
                            title = "照片",
                            subtitle = "选填，最多 ${com.fanji.mealnote.ui.visit.MAX_VISIT_PHOTOS} 张",
                            photoPaths = uiState.photoPaths,
                            photoDescription = "用餐照片（选填）",
                            isImporting = uiState.isImportingPhotos,
                            canAddPhoto = uiState.canAddPhoto,
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

                    if (showMore) {
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
                            MiuixButton(
                                label = "记账金额、就餐人数、备注（选填）",
                                onClick = { showMore = true },
                                style = MiuixButtonStyle.Text,
                                icon = Icons.Rounded.Add,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item { FormErrorLine(message = uiState.errorMessage) }

                    item { FormBottomGap() }
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
