package com.group58.recruit.model;

import java.util.Objects;

/**
 * Login account for TA / MO / ADMIN. See {@link TAProfile} for TA-only extended fields.
 */
public final class User {

    private String qmId;
    private String password;
    private Role role;
    private String name;
    private String email;

    public User() {
    }

    public User(String qmId, String password, Role role, String name, String email) {
        this.qmId = qmId;
        this.password = password;
        this.role = role;
        this.name = name;
        this.email = email;
    }

    public String getQmId() {
        return qmId;
    }

    public void setQmId(String qmId) {
        this.qmId = qmId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(qmId, user.qmId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(qmId);
    }
}
