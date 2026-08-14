package com.wkq.localsignage.feature.app.server

internal fun <T> jsonArray(values: Iterable<T>, transform: (T) -> String): String =
    values.joinToString(prefix = "[", postfix = "]", transform = transform)
