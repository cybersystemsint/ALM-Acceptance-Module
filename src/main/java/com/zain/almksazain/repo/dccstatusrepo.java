package com.zain.almksazain.repo;

import com.zain.almksazain.model.dccstatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface dccstatusrepo extends JpaRepository<dccstatus,Long> {
    dccstatus findByRecordNo(long recordno);
}
