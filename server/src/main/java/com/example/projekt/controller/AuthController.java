package com.example.projekt.controller;



import com.example.projekt.model.User;
import com.example.projekt.repository.UserRepository;
import com.example.projekt.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user login and registration")
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

        @PostMapping("/login")
        @Operation(summary = "Login", description = "Authenticate user and return a JWT token")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWT token returned",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = java.util.Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
        })
        public ResponseEntity<?> login(@RequestBody User loginRequest) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
        String jwt = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(Map.of("token", jwt));
    }

        @PostMapping("/register")
        @Operation(summary = "Register", description = "Create a new user account")
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "User already exists")
        })
        public ResponseEntity<?> register(@RequestBody User newUser) {
        String username = newUser.getUsername() == null ? "" : newUser.getUsername().trim();
        String password = newUser.getPassword() == null ? "" : newUser.getPassword();

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body("Username and password are required");
        }

        
        if (!username.matches("^[A-Za-z0-9_]{3,20}$")) {
            return ResponseEntity.badRequest().body("Username must be 3-20 characters and contain only letters, digits, and underscores");
        }

        
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            return ResponseEntity.badRequest().body("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            return ResponseEntity.badRequest().body("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            return ResponseEntity.badRequest().body("Password must contain at least one digit");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User already exists");
        }

        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setWins(0);
        newUser.setLoses(0);
        newUser.setDraws(0);

        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body("Registration successful");
    }
}