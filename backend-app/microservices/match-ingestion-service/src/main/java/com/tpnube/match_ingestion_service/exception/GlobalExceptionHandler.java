package com.tpnube.match_ingestion_service.exception;

import com.tpnube.match_ingestion_service.dto.ErrorResponseDTO;
import com.tpnube.match_ingestion_service.service.MatchEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MatchEventService.InvalidEventException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidEvent(
            MatchEventService.InvalidEventException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_EVENT", ex.getMessage(), request);
    }

    @ExceptionHandler(MatchEventService.MatchNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleMatchNotFound(
            MatchEventService.MatchNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", ex.getMessage(), request);
    }

    // JSON malformado o con tipos incorrectos (ej. "minute": "abc") es INVALID_EVENT también.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMalformedRequest(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "El body del request es inválido", request);
    }

    // Red de contención final: respeta el formato único de error igual que los demás.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado atendiendo {}", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error inesperado", request);
    }

    private ResponseEntity<ErrorResponseDTO> buildError(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
