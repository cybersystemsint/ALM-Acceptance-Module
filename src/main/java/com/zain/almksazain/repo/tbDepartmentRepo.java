package com.zain.almksazain.repo;
import com.zain.almksazain.model.tb_department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface tbDepartmentRepo extends JpaRepository<tb_department, Integer> {
    Optional<tb_department> findByRecordNo(Integer recordNo);
    List<tb_department> findAll();
    
}