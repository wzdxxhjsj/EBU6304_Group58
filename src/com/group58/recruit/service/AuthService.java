package com.group58.recruit.service;

import java.util.List;
import java.util.Optional;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.UserRepository;

/**
 * Authentication and role-based access checks.
 */
public final class AuthService {

    private final UserRepository users = new UserRepository();
    private User currentUser;

    /**
     * Validates credentials and selected role, then stores session user.
     */
    public Optional<User> login(String qmId, String password, Role selectedRole) {
        List<User> allUsers = users.findAll();
        for (User user : allUsers) {
            if (!user.getQmId().equals(qmId)) {
                continue;
            }
            if (!user.getPassword().equals(password)) {
                return Optional.empty();
            }
            if (user.getRole() != selectedRole) {
                return Optional.empty();
            }
            currentUser = user;
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public boolean hasRole(Role role) {
        return currentUser != null && currentUser.getRole() == role;
    }

    public void logout() {
        currentUser = null;
    }
}
