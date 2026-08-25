package com.macrosaurus.shared.web

import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NotFoundException
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

@RestControllerAdvice
internal class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(error: NotFoundException) = problem(HttpStatus.NOT_FOUND, error.message ?: "Not found")

    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(error: ForbiddenException) = problem(HttpStatus.FORBIDDEN, error.message ?: "Forbidden")

    @ExceptionHandler(InvalidOperationException::class, ConstraintViolationException::class)
    fun invalid(error: Exception) = problem(HttpStatus.UNPROCESSABLE_CONTENT, error.message ?: "Invalid operation")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(error: MethodArgumentNotValidException): ProblemDetail {
        val detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed")
        detail.setProperty(
            "errors",
            error.bindingResult.fieldErrors
                .groupBy { it.field }
                .mapValues { (_, errors) -> errors.map { it.defaultMessage ?: "invalid" }.distinct() },
        )
        return detail
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun malformedRequest(error: Exception) = problem(HttpStatus.BAD_REQUEST, "Request contains an invalid value")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(error: DataIntegrityViolationException) = problem(HttpStatus.CONFLICT, "The request conflicts with existing data")

    @ExceptionHandler(ExternalServiceException::class)
    fun external(error: ExternalServiceException) = problem(HttpStatus.BAD_GATEWAY, error.message ?: "External service failed")

    private fun problem(
        status: HttpStatus,
        detail: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
            type = URI.create("https://api.macrosaurus.app/problems/${status.name.lowercase()}")
        }
}
