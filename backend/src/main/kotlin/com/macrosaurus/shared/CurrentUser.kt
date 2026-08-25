package com.macrosaurus.shared

/** Resolves the authenticated user for the current request. */
fun interface CurrentUser {
    fun userId(): String
}
