package com.itsm.security;

import com.itsm.domain.user.User;
import com.itsm.exception.UnauthorizedOperationException;
import com.itsm.exception.UserNotFoundException;
import com.itsm.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the authenticated user's domain entity for the current request. */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedOperationException("Authentication required");
        }
        return principal.getId();
    }

    public User currentUser() {
        Long id = currentUserId();
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
