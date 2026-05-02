package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Model.Users;
import com.example.AI_InterviewSystem.Service.JwtService;
import com.example.AI_InterviewSystem.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @PostMapping("/public/register")
    public ResponseEntity<?> register(@RequestBody Users user) {
        Users savedUser = userService.addUser(user);
        savedUser.setPassword(null);
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/admin/adduser")
    public ResponseEntity<?> addAdmin(@RequestBody Users user) {
        Users savedUser = userService.addAdmin(user);
        savedUser.setPassword(null);
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/public/login")
    public ResponseEntity<?> login(@RequestBody Users user) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getUsername(),
                            user.getPassword()
                    )
            );

            if (auth.isAuthenticated()) {
                String token = jwtService.generateToken(auth.getName());

                Users dbUser = userService.findByUsername(auth.getName());

                return ResponseEntity.ok(Map.of(
                        "token", token,
                        "username", dbUser.getUsername(),
                        "role", dbUser.getRole()
                ));
            }

            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            Users user = userService.findByUsername(userDetails.getUsername());
            user.setPassword(null);
            return ResponseEntity.ok(user);
        }

        return ResponseEntity.status(401).body("Unauthorized");
    }

    @GetMapping("/admin/test")
    public String adminTest() {
        return "Admin access working";
    }

    @PostMapping("/admin/register")
    public ResponseEntity<?> registerAdmin(@RequestBody Users user) {
        try {
            Users savedUser = userService.addadmin(user);
            savedUser.setPassword(null); // hide password
            return ResponseEntity.ok(savedUser);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user/test")
    public String userTest() {
        return "User access working";
    }
}