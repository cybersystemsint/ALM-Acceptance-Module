package com.zain.almksazain.repo;

import com.zain.almksazain.model.departmentsdata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface deptsrepo extends JpaRepository<departmentsdata,Long> {
    departmentsdata findByRecordNo(long recordno);
    List<departmentsdata> findBySysStatus(boolean active);
}
