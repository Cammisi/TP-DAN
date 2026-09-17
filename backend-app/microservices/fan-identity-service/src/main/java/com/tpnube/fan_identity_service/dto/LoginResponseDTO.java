package com.tpnube.fan_identity_service.dto;

public record LoginResponseDTO(String token, String fanId, String role) {
}
