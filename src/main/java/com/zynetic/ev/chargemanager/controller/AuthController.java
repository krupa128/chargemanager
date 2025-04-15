package com.zynetic.ev.chargemanager.controller;

import com.zynetic.ev.chargemanager.dto.AuthResponse;
import com.zynetic.ev.chargemanager.dto.LoginRequest;
import com.zynetic.ev.chargemanager.entity.User;
import com.zynetic.ev.chargemanager.repository.UserRepository;
import com.zynetic.ev.chargemanager.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
       Optional<User> user = userRepository.findByUsername(request.getUsername());

        if (user.isEmpty() || !passwordEncoder.matches(request.getPassword(), user.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }

        // 🔹 Generate token using username (Not entire User object)
        String token = jwtService.generateToken(user.get().getUsername());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}
//INSERT INTO users (id, username, password) VALUES (1, 'chandu', '$2a$10$S723x4jEXUejeFEBN/IJu.Gb7eCY09gTILJnS556g0AMonNvWZc6i');

//select * from users
