package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Model.UserPrincipal;
import com.example.AI_InterviewSystem.Model.Users;
import com.example.AI_InterviewSystem.Repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepo repo;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepo repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = repo.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        return new UserPrincipal(user);
    }

    public Users addadmin(Users user) {
        user.setRole("ADMIN");
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repo.save(user);
    }

    public Users addUser(Users user) {
        user.setRole("USER");
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repo.save(user);
    }

    public Users findByUsername(String username) {
        return repo.findByUsername(username);
    }
    public Users addAdmin(Users user) {

        if (repo.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }

        user.setRole("ADMIN"); // 🔥 force admin role
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return repo.save(user);
    }
}