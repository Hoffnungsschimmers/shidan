package com.fanji.mealnote.data

/**
 * 数据层可预期的业务失败。
 *
 * 之所以不使用异常表达这些场景：它们都是**用户可理解、可恢复**的正常分支（记录被删除、
 * 磁盘写满等），而非程序缺陷。用密封类型返回可以让 UI 层穷举处理并给出准确文案，
 * 避免把 `IllegalArgumentException` 当作通用错误吞掉。
 */
sealed interface MealError {
    /** 目标餐厅已不存在，通常是被用户在另一页面删除。 */
    data object RestaurantNotFound : MealError

    /** 目标用餐记录已不存在。 */
    data object RecordNotFound : MealError

    /** 用户输入不满足业务约束（如名称为空），附带可直接展示给用户的原因。 */
    data class InvalidInput(val reason: String) : MealError

    /** 图片文件读写失败（源已失效、权限被回收、磁盘写入异常等）。 */
    data object PhotoIoFailure : MealError

    /** 设备存储空间不足。 */
    data object StorageFull : MealError

    /** 其他未归类的数据库错误。 */
    data class DatabaseFailure(val cause: Throwable) : MealError
}

/**
 * 数据层操作结果。
 *
 * 约定：**任何可能因业务原因失败的操作都必须返回本类型**，禁止用“抛异常 + 上层 runCatching”
 * 的方式掩盖失败，也禁止在失败时返回一个看似成功的空值。
 */
sealed interface MealResult<out T> {
    data class Success<T>(val value: T) : MealResult<T>
    data class Failure(val error: MealError) : MealResult<Nothing>
}

/** 成功时执行 [block]，失败时原样返回，便于串联多个依赖前一步结果的操作。 */
inline fun <T, R> MealResult<T>.map(block: (T) -> R): MealResult<R> = when (this) {
    is MealResult.Success -> MealResult.Success(block(value))
    is MealResult.Failure -> this
}

/** 取值，失败时返回 null。仅用于调用方确实不关心失败原因的场景。 */
fun <T> MealResult<T>.getOrNull(): T? = (this as? MealResult.Success)?.value
