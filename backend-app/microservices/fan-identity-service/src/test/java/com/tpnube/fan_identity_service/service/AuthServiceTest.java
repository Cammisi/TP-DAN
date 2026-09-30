package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.FanRole;
import com.tpnube.fan_identity_service.dto.LoginRequestDTO;
import com.tpnube.fan_identity_service.dto.LoginResponseDTO;
import com.tpnube.fan_identity_service.dto.RegisterRequestDTO;
import com.tpnube.fan_identity_service.dto.RegisterResponseDTO;
import com.tpnube.fan_identity_service.repository.FanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FanRepository fanRepository;

    @Mock
    private JwtService jwtService;

    // BCrypt real (no mockeado): el test de login depende de que el hash generado
    // en el registro y la verificación en el login sean consistentes de verdad.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(fanRepository, passwordEncoder, jwtService);
    }

    @Test
    void registraUnFanNuevo() {
        when(fanRepository.findByEmail("fan@quienjuega.com")).thenReturn(Optional.empty());
        when(fanRepository.save(any(Fan.class))).thenAnswer(invocation -> {
            Fan fan = invocation.getArgument(0);
            fan.setId(1L);
            return fan;
        });

        RegisterResponseDTO response = authService.register(new RegisterRequestDTO("fan@quienjuega.com", "fan123"));

        assertThat(response.id()).isEqualTo("1");
        assertThat(response.email()).isEqualTo("fan@quienjuega.com");
    }

    @Test
    void noPermiteRegistrarUnEmailYaExistente() {
        when(fanRepository.findByEmail("fan@quienjuega.com"))
                .thenReturn(Optional.of(new Fan(1L, "fan@quienjuega.com", "hash", FanRole.FAN)));

        assertThatThrownBy(() -> authService.register(new RegisterRequestDTO("fan@quienjuega.com", "fan123")))
                .isInstanceOf(AuthService.EmailAlreadyExistsException.class);
    }

    @Test
    void loguearConCredencialesCorrectasDevuelveElToken() {
        Fan fan = new Fan(1L, "fan@quienjuega.com", passwordEncoder.encode("fan123"), FanRole.FAN);
        when(fanRepository.findByEmail("fan@quienjuega.com")).thenReturn(Optional.of(fan));
        when(jwtService.generateToken(fan)).thenReturn("un-token");

        LoginResponseDTO response = authService.login(new LoginRequestDTO("fan@quienjuega.com", "fan123"));

        assertThat(response.token()).isEqualTo("un-token");
        assertThat(response.fanId()).isEqualTo("1");
        assertThat(response.role()).isEqualTo("FAN");
    }

    @Test
    void loguearConContraseñaIncorrectaRechaza() {
        Fan fan = new Fan(1L, "fan@quienjuega.com", passwordEncoder.encode("fan123"), FanRole.FAN);
        when(fanRepository.findByEmail("fan@quienjuega.com")).thenReturn(Optional.of(fan));

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("fan@quienjuega.com", "mala")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }

    @Test
    void loguearConEmailInexistenteRechaza() {
        when(fanRepository.findByEmail("nadie@quienjuega.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("nadie@quienjuega.com", "fan123")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }
}
