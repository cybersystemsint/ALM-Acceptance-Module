package com.zain.almksazain.repo;

import com.zain.almksazain.model.dccpoview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface dccpoviewrepo extends JpaRepository<dccpoview,Long> {

    List<dccpoview> findBySupplierId(String supplierId);
    List<dccpoview> findAll();
}
