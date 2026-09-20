package com.macrosaurus.shared

class NotFoundException(
    message: String,
) : RuntimeException(message)

class ForbiddenException(
    message: String,
) : RuntimeException(message)

class InvalidOperationException(
    message: String,
) : RuntimeException(message)

class ExternalServiceException(
    message: String,
    cause: Throwable? = null,
    val failureCategory: String = "external_service",
) : RuntimeException(message, cause)

class ServiceUnavailableException(
    message: String,
) : RuntimeException(message)
