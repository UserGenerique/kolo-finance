package com.kolofinance.dto;

import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String phoneNumber;
    private Role role;
    private Boolean active;
    private LocalDateTime createdAt;
    private Boolean hasPassword;

    public static UserResponse of(User user, Role role, Boolean active) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(role)
                .active(active)
                .createdAt(user.getCreatedAt())
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .build();
    }
}
