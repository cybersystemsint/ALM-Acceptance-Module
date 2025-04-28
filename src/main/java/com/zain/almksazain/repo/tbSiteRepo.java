/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_Site;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author jgithu
 */
public interface tbSiteRepo extends JpaRepository<tb_Site, Long> {

    tb_Site findByrecordNo(long recordNo);

 //   tb_Site findBySiteId(String siteId);
    
     tb_Site findFirstBySiteId(String siteId);

}
