package com.fhir.admin.controller;

import com.fhir.auth.dto.RegisterRequest;
import com.fhir.auth.model.AppUser;
import com.fhir.auth.repository.AuthUserRepository;
import com.fhir.auth.service.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AuthUserRepository authUserRepository;
    private final AuthService authService;

    public AdminUserController(AuthUserRepository authUserRepository, AuthService authService) {
        this.authUserRepository = authUserRepository;
        this.authService = authService;
    }

    @GetMapping
    public List<AppUser> getAllUsers() {
        return authUserRepository.findAll();
    }

    @PostMapping
    public AppUser createUser(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        authUserRepository.deleteById(id);
    }
}
