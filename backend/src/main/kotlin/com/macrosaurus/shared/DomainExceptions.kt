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
) : RuntimeException(message, cause)
