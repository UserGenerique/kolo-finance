package com.kolofinance.dto;

import com.kolofinance.model.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthOrganization {
    private Long id;
    private String name;
    private Role role;
}
