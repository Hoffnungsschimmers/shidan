package com.fanji.mealnote.data

import com.fanji.mealnote.data.MealError.DatabaseFailure
import com.fanji.mealnote.data.MealError.InvalidInput
import com.fanji.mealnote.data.MealError.PhotoIoFailure
import com.fanji.mealnote.data.MealError.RecordNotFound
import com.fanji.mealnote.data.MealError.RestaurantNotFound
import com.fanji.mealnote.data.MealError.StorageFull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「数据层失败 → 用户文案」统一映射的穷举护栏。
 *
 * 全项目原有多份逐字重复的 `MealError→文案` when。收敛到 [toSaveMessage] / [toImportMessage]
 * 后，这里把**全部枚举 case × 两种保存上下文的输出文案锁死**：
 * - 改动任何文案必须连带改测试，否则立即红灯，防止同一错误在不同页面文案不一；
 * - 新增 MealError case 会因 when 非穷举而编译失败，强制先决定新文案。
 */
class MealErrorMessagesTest {

    // ─────────────────────────── toSaveMessage：餐厅上下文 ───────────────────────────

    @Test
    fun `餐厅保存_InvalidInput透传原因`() {
        assertEquals("名称不能为空", InvalidInput("名称不能为空").toSaveMessage(SaveKind.RESTAURANT))
    }

    @Test
    fun `餐厅保存_StorageFull给清理提示`() {
        assertEquals("存储空间不足，请清理后重试", StorageFull.toSaveMessage(SaveKind.RESTAURANT))
    }

    @Test
    fun `餐厅保存_PhotoIoFailure让重选图片`() {
        assertEquals("图片保存失败，请重新选择图片", PhotoIoFailure.toSaveMessage(SaveKind.RESTAURANT))
    }

    @Test
    fun `餐厅保存_RestaurantNotFound提示返回列表`() {
        assertEquals(
            "该餐厅记录已不存在，请返回列表后重试",
            RestaurantNotFound.toSaveMessage(SaveKind.RESTAURANT),
        )
    }

    @Test
    fun `餐厅保存_RecordNotFound不会出现在餐厅上下文`() {
        // 餐厅表单不存在"用餐记录被删"场景，但函数必须仍给出确定输出（由当值穷举保证不崩）。
        assertEquals(
            "该用餐记录已不存在，请返回后重试",
            RecordNotFound.toSaveMessage(SaveKind.RESTAURANT),
        )
    }

    @Test
    fun `餐厅保存_DatabaseFailure给餐厅兜底`() {
        assertEquals(
            "餐厅记录保存失败，请稍后重试",
            DatabaseFailure(IllegalStateException("boom")).toSaveMessage(SaveKind.RESTAURANT),
        )
    }

    // ─────────────────────────── toSaveMessage：用餐上下文 ───────────────────────────

    @Test
    fun `用餐保存_InvalidInput透传原因`() {
        assertEquals("请至少填写一个餐品", InvalidInput("请至少填写一个餐品").toSaveMessage(SaveKind.VISIT))
    }

    @Test
    fun `用餐保存_StorageFull给清理提示`() {
        assertEquals("存储空间不足，请清理后重试", StorageFull.toSaveMessage(SaveKind.VISIT))
    }

    @Test
    fun `用餐保存_PhotoIoFailure让重选图片`() {
        assertEquals("图片保存失败，请重新选择图片", PhotoIoFailure.toSaveMessage(SaveKind.VISIT))
    }

    @Test
    fun `用餐保存_RestaurantNotFound提示返回列表`() {
        assertEquals(
            "该餐厅记录已不存在，请返回列表后重试",
            RestaurantNotFound.toSaveMessage(SaveKind.VISIT),
        )
    }

    @Test
    fun `用餐保存_RecordNotFound提示返回`() {
        assertEquals("该用餐记录已不存在，请返回后重试", RecordNotFound.toSaveMessage(SaveKind.VISIT))
    }

    @Test
    fun `用餐保存_DatabaseFailure给用餐兜底`() {
        assertEquals(
            "用餐记录保存失败，请稍后重试",
            DatabaseFailure(IllegalStateException("boom")).toSaveMessage(SaveKind.VISIT),
        )
    }

    // ─────────────────────────── toImportMessage：导入失败 ───────────────────────────

    @Test
    fun `导入_不是图片提示换一张`() {
        assertEquals("所选文件不是有效的图片，请换一张重试", PhotoStore.ImportFailure.NOT_AN_IMAGE.toImportMessage())
    }

    @Test
    fun `导入_读不出来提示重新选择`() {
        assertEquals("图片导入失败，请重新选择", PhotoStore.ImportFailure.UNREADABLE.toImportMessage())
    }

    @Test
    fun `导入_无失败也归入通用文案`() {
        assertEquals("图片导入失败，请重新选择", null.toImportMessage())
    }
}