package com.kolofinance.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String userType;
    private Long userId;
    private String name;
    private String phoneNumber;
    private List<AuthOrganization> organizations;
}
