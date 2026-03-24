package com.group58.recruit.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.UserRepository;

/**
 * User-related use cases (replace with real registration / auth later).
 */
public final class UserService {

    private final UserRepository users = new UserRepository();

    public List<User> listUsers() {
        return users.findAll();
    }

    /** Demo: append a user and persist (for verifying JSON read/write). */
    public void addDemoUser(String usernamePrefix, Role role) throws IOException {
        List<User> all = users.findAll();
        String qmId = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        all.add(new User(qmId, "changeme", role, usernamePrefix, usernamePrefix + "@example.edu"));
        users.saveAll(all);
    }
}
