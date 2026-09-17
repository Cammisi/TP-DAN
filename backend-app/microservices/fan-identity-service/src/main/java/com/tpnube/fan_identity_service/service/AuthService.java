package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.FanRole;
import com.tpnube.fan_identity_service.dto.LoginRequestDTO;
import com.tpnube.fan_identity_service.dto.LoginResponseDTO;
import com.tpnube.fan_identity_service.dto.RegisterRequestDTO;
import com.tpnube.fan_identity_service.dto.RegisterResponseDTO;
import com.tpnube.fan_identity_service.repository.FanRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final FanRepository fanRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(FanRepository fanRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.fanRepository = fanRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegisterResponseDTO register(RegisterRequestDTO request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new InvalidRequestException("El email es obligatorio");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new InvalidRequestException("La contraseña es obligatoria");
        }

        if (fanRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Fan fan = new Fan();
        fan.setEmail(request.email());
        fan.setPassword(passwordEncoder.encode(request.password()));
        fan.setRole(FanRole.FAN);

        fan = fanRepository.save(fan);

        return new RegisterResponseDTO(fan.getId().toString(), fan.getEmail());
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        Fan fan = fanRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), fan.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(fan);

        return new LoginResponseDTO(token, fan.getId().toString(), fan.getRole().name());
    }

    // --- Excepciones de negocio (paquete-privadas, las maneja el GlobalExceptionHandler) ---

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("El email '" + email + "' ya está registrado");
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Email o contraseña incorrectos");
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
