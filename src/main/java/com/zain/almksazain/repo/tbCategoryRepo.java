package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface tbCategoryRepo extends JpaRepository<tbCategory, Integer> {

    List<tbCategory> findByItemCategoryCodeIn(Collection<String> itemCategoryCodes);
}
