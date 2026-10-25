package com.fanji.mealnote.data

/** 数据库引用与实时表单保护都必须放行，文件才可进入回收集合。 */
internal fun unreferencedPhotoCandidates(
    candidates: Collection<String>,
    referenced: Set<String>,
    isProtected: (String) -> Boolean,
): List<String> = candidates.filter { it !in referenced && !isProtected(it) }
