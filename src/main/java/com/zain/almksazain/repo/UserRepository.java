package com.zain.almksazain.repo;
import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    List<User> findByUsernameIn(List<String> usernames);
    List<User> findByUsernameIn(Collection<String> usernames);
    Optional<User> findFirstByFullName(String fullName);
    Optional<User> findFirstByEmailAddress(String emailAddress);
    Optional<User> findByUserId(Integer userId);
}
