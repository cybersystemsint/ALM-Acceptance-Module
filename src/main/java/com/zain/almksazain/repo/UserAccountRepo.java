package com.zain.almksazain.repo;
import com.zain.almksazain.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserAccountRepo extends JpaRepository<UserAccount, Integer> {
    Optional<UserAccount> findBySupplierId(String supplierId);
    // List<UserAccount> findAll();
    Optional<UserAccount> findByFullName(String fullName);
    Optional<UserAccount> findByUsername(String username);
    // Optional<UserAccount> findById(Integer userId);
    List<UserAccount> findByUsernameIn(Collection<String> usernames);
}