package com.kolofinance.dto;

import com.kolofinance.model.AuthSession;
import com.kolofinance.model.Organization;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.PlatformAdmin;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.model.enums.SessionUserType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthPrincipal {
    private AuthSession session;
    private SessionUserType userType;
    private PlatformAdmin platformAdmin;
    private User user;
    private Organization organization;
    private OrganizationMembership membership;
    private Role role;

    public boolean isPlatformAdmin() {
        return userType == SessionUserType.PLATFORM_ADMIN;
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }
}
