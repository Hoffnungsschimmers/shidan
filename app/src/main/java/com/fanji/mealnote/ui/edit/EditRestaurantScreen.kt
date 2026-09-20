package com.fanji.mealnote.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fanji.mealnote.ui.components.FormBottomGap
import com.fanji.mealnote.ui.components.FormErrorLine
import com.fanji.mealnote.ui.components.LoadingBlock
import com.fanji.mealnote.ui.components.MealPhotoSection
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.MiuixTopBar
import com.fanji.mealnote.ui.components.PhotoViewerHost
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.rememberGalleryPicker
import com.fanji.mealnote.ui.components.rememberPhotoViewerState
import java.io.File

/**
 * 「从用餐照片选封面」的候选项。
 *
 * 选中态用**主色描边 + 右上角勾选**双重表达，不只依赖颜色 ——
 * 与评价徽章、状态徽章遵守同一条无障碍约定。
 */
@Composable
private fun CoverCandidate(path: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = if (selected) "当前封面" else "设为封面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            )
        }
    }
}

/** 编辑餐厅资料。布局与新建页保持一致，差异只在于进入时已有数据、以及按钮文案。 */
@Composable
fun EditRestaurantScreen(
    restaurantId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditRestaurantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoViewer = rememberPhotoViewerState()
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(restaurantId) { viewModel.setRestaurantId(restaurantId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    val galleryPicker = rememberGalleryPicker(maxItems = 1) { uris ->
        uris.firstOrNull()?.let { viewModel.onPhotosPicked(listOf(it)) }
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
            .statusBarsPadding(),
    ) {
        MiuixTopBar(title = "编辑店铺信息", onBack = onBack)

        when {
            uiState.isLoading -> LoadingBlock(label = "正在加载店铺…")

            uiState.notFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这家店已经不存在了",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).imePadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(18.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                MiuixTextField(
                                    value = uiState.name,
                                    onValueChange = viewModel::onNameChange,
                                    label = "店名",
                                    placeholder = "例如：老王面馆",
                                    singleLine = true,
                                    isError = uiState.showNameError,
                                    supportingText = if (uiState.showNameError) "店名不能为空" else null,
                                    leadingIcon = Icons.Rounded.Restaurant,
                                )
                                MiuixTextField(
                                    value = uiState.address,
                                    onValueChange = viewModel::onAddressChange,
                                    label = "地址（选填）",
                                    placeholder = "商场、街道或任何能帮你找到它的描述",
                                    minLines = 2,
                                    maxLines = 3,
                                    leadingIcon = Icons.Rounded.LocationOn,
                                )
                            }
                        }
                    }

                    item {
                        MealPhotoSection(
                            title = "封面图",
                            subtitle = "只保留一张；移除后不会立刻删原图，保存时才结算",
                            photoPaths = uiState.photoPaths,
                            photoDescription = "餐厅封面图片",
                            isImporting = uiState.isImportingPhotos,
                            canAddPhoto = uiState.photoPaths.isEmpty(),
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

                    // 「从用餐照片里选」：照片已经在应用里了，不该逼用户再导入一次。
                    // 只在本店确实有用餐照片时出现，否则整段是空的。
                    if (uiState.recordPhotoPaths.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeader(
                                    title = "或从用餐照片里选",
                                    subtitle = "用吃过的照片当封面，不用重新导入",
                                )
                                CoverCandidateRow(
                                    candidates = uiState.recordPhotoPaths,
                                    selected = uiState.photoPaths.firstOrNull(),
                                    onSelect = viewModel::useRecordPhotoAsCover,
                                )
                            }
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

    PhotoViewerHost(photoViewer)
}

/**
 * Cover candidates in a horizontal row.
 *
 * Extracted from the EditRestaurantScreen body so the screen reads as
 * sections rather than nested LazyRow builders. Selection renders with
 * primary stroke plus check badge, never color alone.
 */
@Composable
private fun CoverCandidateRow(
    candidates: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            count = candidates.size,
            key = { index -> candidates[index] },
        ) { index ->
            val path = candidates[index]
            CoverCandidate(
                path = path,
                selected = selected == path,
                onClick = { onSelect(path) },
            )
        }
    }
}
