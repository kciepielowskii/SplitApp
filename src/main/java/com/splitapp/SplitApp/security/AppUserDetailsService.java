package com.splitapp.SplitApp.security;

import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findLoginByEmail(User.normalizeEmail(username))
                .map(login -> new AppUserDetails(login.getId(), login.getEmail(), login.getPassword()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
