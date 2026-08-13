package com.wkq.localsignage.feature.app.security

object CommandRevisionPolicy {
    fun canAccept(current: Long, requested: Long?): Boolean = requested == null || requested > current
}
