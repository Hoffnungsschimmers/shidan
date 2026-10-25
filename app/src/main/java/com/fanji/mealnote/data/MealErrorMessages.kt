package com.fanji.mealnote.data

/**
 * 「数据层失败 → 用户文案」的统一映射。
 *
 * 全项目曾经有 6 份 `MealError→文案` when 和 4 份 `ImportFailure→文案` 副本，
 * 逐字重复，稍有漏改就出现同一错误在不同页面文案不一。这里把**语义相同的上下文**收敛为
 * 单一纯函数 + 穷举测试，防止再次漂移。
 *
 * 注意：**不是所有错误文案都能强行合一**。`RestaurantDetailViewModel` 的删除上下文、
 * `SettingsViewModel` 的下载/上传上下文，它们的失败语义与「保存」不同（比如
 * `PhotoIoFailure` 在删除场景提示「可在设置中清理」，在保存场景提示「请重新选择图片」）。
 * 强行统一会产生错误文案，所以这两处保留各自的私有 when，本文档只收敛 4 个表单的保存文案。
 */

/** 保存失败文案的上下文：新建/编辑餐厅，或新建/编辑用餐。 */
enum class SaveKind {
    /** 餐厅记录：店名/地址/封面。 */
    RESTAURANT,

    /** 用餐记录：评价/餐品/金额/照片。 */
    VISIT,
}

/**
 * 保存（新增或编辑）失败的统一文案。
 *
 * `RestaurantNotFound` / `RecordNotFound` 只在编辑既有记录时可能出现（目标行被他在另一页
 * 删除）；新建时不存在。二者文案随 [SaveKind] 不同：
 * - RESTAURANT：目标餐厅被删 → 提示返回列表；
 * - VISIT：目标用餐记录或所属餐厅被删 → 提示返回。
 *
 * 输出与既有 4 个表单 VM 的私有 `toSaveMessage` 逐一对应（见 MealErrorMessagesTest 锁定）。
 */
fun MealError.toSaveMessage(kind: SaveKind): String = when (this) {
    is MealError.InvalidInput -> reason
    MealError.StorageFull -> "存储空间不足，请清理后重试"
    MealError.PhotoIoFailure -> "图片保存失败，请重新选择图片"
    MealError.RestaurantNotFound -> "该餐厅记录已不存在，请返回列表后重试"
    MealError.RecordNotFound -> "该用餐记录已不存在，请返回后重试"
    is MealError.DatabaseFailure ->
        if (kind == SaveKind.RESTAURANT) "餐厅记录保存失败，请稍后重试"
        else "用餐记录保存失败，请稍后重试"
}

/**
 * 导入失败原因的统一文案。
 *
 * 区分「读不出来」与「不是图片」：前者通常是来源应用的授权问题，换个相册重选即可；
 * 后者是选错了文件类型，用户需要换一张图。4 个表单 VM 曾各存一份逐字相同的副本，
 * 此处收敛为单一纯函数。
 */
fun PhotoStore.ImportFailure?.toImportMessage(): String = when (this) {
    PhotoStore.ImportFailure.NOT_AN_IMAGE -> "所选文件不是有效的图片，请换一张重试"
    // UNREADABLE 与 null（无失败，理论上不会走到）都归入通用导入失败文案。
    else -> "图片导入失败，请重新选择"
}