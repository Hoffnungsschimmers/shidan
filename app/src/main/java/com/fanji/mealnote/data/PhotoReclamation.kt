package com.fanji.mealnote.data

import java.io.File
import java.io.IOException

/** 引用集合必须完整：无法确认文件身份时，中止回收而非猜测它没有引用。 */
private fun canonicalPhotoReferences(
    paths: Collection<String>,
    canonicalize: (String) -> String,
): Set<String> = paths.filter(String::isNotBlank).distinct().map { path ->
    canonicalPhotoPathOrNull(path, canonicalize)
        ?: throw IOException("Photo reference resolution failed; cleanup aborted")
}.toSet()

private fun canonicalPhotoPathOrNull(
    path: String,
    canonicalize: (String) -> String,
): String? = try {
    canonicalize(path)
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
}

/** 数据库引用与实时表单保护都必须放行，文件才可进入回收集合。 */
internal fun unreferencedPhotoCandidates(
    candidates: Collection<String>,
    referenced: Set<String>,
    isProtected: (String) -> Boolean,
): List<String> = unreferencedPhotoCandidates(
    candidates, referenced, { File(it).canonicalPath }, isProtected,
)

internal fun unreferencedPhotoCandidates(
    candidates: Collection<String>,
    referenced: Set<String>,
    canonicalize: (String) -> String,
    isProtected: (String) -> Boolean,
): List<String> {
    val canonicalReferences = canonicalPhotoReferences(referenced, canonicalize)
    return candidates.filter { path ->
        val canonical = canonicalPhotoPathOrNull(path, canonicalize)
        canonical != null && canonical !in canonicalReferences && !isProtected(path)
    }
}

/** PhotoStore 的磁盘清理接缝；时钟由调用方提供，JVM 回归直接操作真实临时文件。 */
internal fun deleteOrphanPhotoFiles(
    directory: File,
    referencedPaths: Collection<String>,
    cutoffMillis: Long,
    canonicalize: (String) -> String = { File(it).canonicalPath },
): Int {
    // 在删除第一张图之前完成全部引用解析，防止发现坏路径时已部分清理。
    val referenced = canonicalPhotoReferences(referencedPaths, canonicalize)
    val canonicalDirectory = canonicalPhotoPathOrNull(directory.path, canonicalize)
        ?: throw IOException("Photo directory resolution failed; cleanup aborted")
    val files = directory.listFiles() ?: return 0
    var removed = 0
    files.forEach { file ->
        if (!file.isFile || file.lastModified() > cutoffMillis) return@forEach
        val canonical = canonicalPhotoPathOrNull(file.path, canonicalize) ?: return@forEach
        // canonical 校验拒绝指向目录外或子目录的链接，与单张删除的所有权边界一致。
        if (File(canonical).parent != canonicalDirectory) return@forEach
        if (canonical !in referenced && file.delete()) removed++
    }
    return removed
}
