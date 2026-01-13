package net.javahippie.fitpub.security;

import lombok.RequiredArgsConstructor;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService implementation for Spring Security.
 * Loads user-specific data from the database for authentication.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(),
            user.isEnabled(),
            true, // accountNonExpired
            true, // credentialsNonExpired
            !user.isLocked(), // accountNonLocked
            getAuthorities(user)
        );
    }

    /**
     * Gets the authorities for a user.
     * Currently, all users have the same ROLE_USER authority.
     * This can be extended to support roles and permissions in the future.
     *
     * @param user the user
     * @return collection of granted authorities
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        // For MVP, all users have the same role
        // Future: Add roles/permissions to User entity
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
