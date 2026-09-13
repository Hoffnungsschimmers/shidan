package com.fanji.mealnote.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.fanji.mealnote.ui.components.CameraPhotoAction
import com.fanji.mealnote.ui.components.GalleryPhotoAction
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.MiuixTopBar
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.SelectedPhoto
import java.io.File

/** 编辑餐厅资料。布局与新建页保持一致，差异只在于进入时已有数据、以及按钮文案。 */
@Composable
fun EditRestaurantScreen(
    restaurantId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditRestaurantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(restaurantId) { viewModel.setRestaurantId(restaurantId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotosPicked(listOf(it)) }
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
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "封面图",
                                subtitle = "只保留一张；移除后不会立刻删原图，保存时才结算",
                            )
                            if (uiState.isImportingPhotos) {
                                Text(
                                    text = "正在导入图片…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                itemsIndexed(uiState.photoPaths, key = { _, path -> path }) { index, path ->
                                    SelectedPhoto(path, "餐厅封面图片", onRemove = { viewModel.removePhoto(index) })
                                }
                            }
                        }
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
}
