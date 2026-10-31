package io.github.swampus.controller.advice;

import io.github.swampus.controller.OracleController;
import io.github.swampus.oracle.OracleFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OracleController.class)
public class OracleExceptionHandler {
    @ExceptionHandler(OracleFailure.class)
    public ResponseEntity<GlobalRestExceptionHandler.ApiError> handle(OracleFailure error, HttpServletRequest request) {
        HttpStatus status = switch (error.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE, BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case BACKEND -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(GlobalRestExceptionHandler.ApiError.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString()).status(status.value())
                .error(status.getReasonPhrase()).message(error.getMessage()).path(request.getRequestURI())
                .exceptionClass(error.getClass().getName()).errorId(UUID.randomUUID().toString()).build());
    }
}
