package com.zain.almksazain.repo;

import com.zain.almksazain.model.poview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface poviewrepo extends JpaRepository<poview,Long> {
    List<poview> findBySupplierId(String supplierId);
}
