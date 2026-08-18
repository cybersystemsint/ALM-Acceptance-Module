package com.zain.almksazain.repo;



import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    List<Role> findByRoleNameContainingIgnoreCase(String roleNamePart);
}