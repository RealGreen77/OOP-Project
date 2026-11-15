package com.itsm.security;

import com.itsm.domain.user.Role;
import com.itsm.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security view of an authenticated user.
 * Authorities = {@code ROLE_<role>} + every {@link com.itsm.domain.user.Permission} of the user's concrete type,
 * so {@code @PreAuthorize("hasAuthority('MANAGE_USERS')")} is driven by the polymorphic domain model.
 */
public final class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private UserPrincipal(Long id, String email, String passwordHash, String fullName, Role role, boolean enabled,
                          List<GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
    }

    public static UserPrincipal from(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(),
                user.getRole(), user.isActive(), authorities);
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public String getFullName() { return fullName; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonLocked() { return enabled; }
}
