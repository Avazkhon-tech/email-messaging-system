package com.emailsystem.auth;

import com.emailsystem.auth.dto.AuthResponse;
import com.emailsystem.auth.dto.LoginRequest;
import com.emailsystem.auth.dto.RegisterRequest;
import com.emailsystem.common.exception.ConflictException;
import com.emailsystem.common.exception.UnauthorizedException;
import com.emailsystem.security.JwtService;
import com.emailsystem.user.User;
import com.emailsystem.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMapper mapper;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = User.builder()
                .fullname(request.fullname().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .build();
        user = userRepository.save(user);
        return mapper.toResponse(user, jwtService.generateToken(user.getId(), user.getEmail()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return mapper.toResponse(user, jwtService.generateToken(user.getId(), user.getEmail()));
    }
}
