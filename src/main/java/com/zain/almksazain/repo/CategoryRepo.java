package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

import com.zain.almksazain.model.Category;

@Repository
public interface CategoryRepo extends JpaRepository<Category, Integer> {
    // Batch fetch by itemCategoryCode
    List<Category> findByItemCategoryCodeIn(Set<String> itemCategoryCodes);
}
