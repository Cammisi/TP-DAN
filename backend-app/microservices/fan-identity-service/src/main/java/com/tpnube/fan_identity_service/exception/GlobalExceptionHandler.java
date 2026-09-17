package com.tpnube.fan_identity_service.exception;

import com.tpnube.fan_identity_service.dto.ErrorResponseDTO;
import com.tpnube.fan_identity_service.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthService.EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailAlreadyExists(
            AuthService.EmailAlreadyExistsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(
            AuthService.InvalidCredentialsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthService.InvalidRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidRequest(
            AuthService.InvalidRequestException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(com.tpnube.fan_identity_service.service.SubscriptionService.TeamNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTeamNotFound(
            com.tpnube.fan_identity_service.service.SubscriptionService.TeamNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(com.tpnube.fan_identity_service.service.NotificationService.FanNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleFanNotFound(
            com.tpnube.fan_identity_service.service.NotificationService.FanNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "FAN_NOT_FOUND", ex.getMessage(), request);
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
