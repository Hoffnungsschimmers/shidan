package com.fanji.mealnote.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 餐厅的两种业务状态。
 *
 * 持久化时以 [name] 字符串写入数据库（见 [Converters]），因此**枚举常量名即持久化编码**：
 * 重命名或删除常量等同于修改 schema，必须同时提供 Migration，否则历史数据无法反序列化。
 */
enum class RestaurantStatus {
    /** 听说了、计划之后去吃。 */
    WANT_TO_EAT,

    /** 已经吃过，至少存在一条用餐记录。 */
    EATEN,
}

/**
 * 三级用餐评价。
 *
 * 与 [RestaurantStatus] 一样，常量名即持久化编码，禁止直接重命名。
 */
enum class Verdict {
    /** 推荐。 */
    GOOD,

    /** 一般。 */
    MEH,

    /** 不推荐。 */
    BAD,
}

/**
 * 餐厅基础资料。
 *
 * 产品定位为“想吃清单”，因此除 [name] 外全部字段可选。历史版本曾包含城市、菜系、
 * 标签、价格、来源等字段，产品重构后已从交互中移除；这些列仍保留在数据库中以避免
 * 破坏性迁移，并通过 [legacy] 标记为只读，新增代码不得依赖。
 */
@Entity(
    tableName = "restaurants",
    indices = [Index("status"), Index("name")]
)
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 店名。唯一必填字段，写入前由 Repository 统一 trim。 */
    val name: String,

    /** 地址，选填。用于列表展示与关键字搜索。 */
    val address: String = "",

    /** 推荐 / 封面图片的本地绝对路径，空字符串表示未设置。 */
    val recommendationPhotoPath: String = "",

    val status: RestaurantStatus = RestaurantStatus.WANT_TO_EAT,

    /** 首次创建时间（epoch millis）。 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最近一次修改时间（epoch millis），列表按此字段倒序。 */
    val updatedAt: Long = System.currentTimeMillis(),

    // ---------------------------------------------------------------- v1 遗留列
    // 以下字段在产品重构后已从交互中移除，但对应的数据库列必须保留，否则 Room 的
    // schema 校验会失败（等于一次未经 Migration 的破坏性变更）。新增代码不得读写它们；
    // 如需彻底清理，请在后续版本通过正式 Migration 重建表。

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val city: String = "",

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val cuisine: String = "",

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val tags: String = "",

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val priceHint: Int? = null,

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val sourceUrl: String = "",

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val sourceNote: String = "",
)

/**
 * 单次用餐记录。
 *
 * 一次用餐对应一条记录，一家餐厅可拥有多条记录（[restaurantId] 外键，级联删除）。
 */
@Entity(
    tableName = "dining_records",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("restaurantId")]
)
data class DiningRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val restaurantId: Long,

    /** 用餐日期（epoch millis），由用户在表单中选择，默认为当天。 */
    val eatenAt: Long = System.currentTimeMillis(),

    val verdict: Verdict = Verdict.GOOD,

    /** 本次用餐的餐品名称，自由文本，选填。 */
    val dishes: String = "",

    /**
     * 本次用餐的花费，自由文本，选填。
     *
     * 刻意使用文本而非数值：真实场景中花费经常无法确定或不必精确，
     * 「人均 60 左右」「约 200」「两个人 158」这类表述无法用整数表达。
     * 界面不做数值校验，仅做 trim 与长度限制（见 `MealRepository`）。
     *
     * v3 新增列。[ColumnInfo.defaultValue] 必须与 `DatabaseModule.Migration2To3`
     * 里的 `DEFAULT ''` **逐字一致**：迁移后的表与全新安装的表必须完全等价，
     * 否则 Room 的 schema 校验会在老用户升级时报 `Migration didn't properly handle`。
     */
    @ColumnInfo(defaultValue = "''")
    val priceText: String = "",

    /**
     * 结构化金额（单位：分），v4 新增，可空。
     *
     * 与 [priceText] 的关系：自由文本永远是用户写下的原文（展示用），
     * 本列是**账本口径**——整数分，避免浮点误差进入备份与报表。
     * `null` 表示「未记金额」，与「这顿 0 元」严格区分（免费餐请写进备注）；
     * 报表在 [priceText] 有值但本列为空时，归入「写的是文字、无法换算」。
     *
     * 迁移 [DatabaseModule.Migration3To4] 中该列**不带 DEFAULT**（可空列默认即 NULL），
     * 与全新安装生成的表结构等价，因此这里**不写** `@ColumnInfo(defaultValue)`。
     */
    val amountMinorUnits: Long? = null,

    /**
     * 就餐人数，v4 新增。默认 1。
     *
     * 用于把「人均 X」换算成桌价入账（见 `data.parseLedgerAmountMinor`）。
     * 迁移 SQL 的 `DEFAULT 1` 必须与下面的 [ColumnInfo.defaultValue] 逐字一致。
     */
    @ColumnInfo(defaultValue = "1")
    val personCount: Int = 1,

    /** 附录：环境、服务或其他补充感受，选填。 */
    val note: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    // v1 遗留列：仅用于维持数据库 schema，请勿在业务代码中使用。

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val star: Int = 0,

    @Deprecated("历史兼容字段，仅用于维持数据库 schema，请勿在业务代码中使用")
    val perPersonCost: Int? = null,
)

/**
 * 用餐照片。文件实体存放于应用私有目录 `files/photos/`，此处只记录绝对路径。
 *
 * 删除用餐记录时数据库行会级联删除，但**磁盘文件不会自动清理**，必须由
 * `MealRepository.deleteDiningRecord` 显式回收。
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = DiningRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["diningRecordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("diningRecordId")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val diningRecordId: Long,

    /** 应用私有目录内照片文件的绝对路径。 */
    val filePath: String,

    /** 展示顺序，从 0 开始递增。 */
    val sortOrder: Int = 0,
)

/** 一条用餐记录及其照片，用于餐厅详情列表。 */
data class DiningRecordWithPhotos(
    @Embedded
    val record: DiningRecordEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "diningRecordId",
    )
    val photos: List<PhotoEntity>,
)

/** 一家餐厅及其全部用餐记录，用于餐厅详情页。 */
data class RestaurantWithRecords(
    @Embedded
    val restaurant: RestaurantEntity,

    @Relation(
        entity = DiningRecordEntity::class,
        parentColumn = "id",
        entityColumn = "restaurantId",
    )
    val records: List<DiningRecordWithPhotos>,
)
