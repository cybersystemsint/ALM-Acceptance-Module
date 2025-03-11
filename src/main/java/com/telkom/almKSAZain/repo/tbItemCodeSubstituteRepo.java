/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.almKSAZain.repo;

import com.telkom.almKSAZain.model.tbItemCodeSubstitute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author jgithu
 */
public interface tbItemCodeSubstituteRepo extends JpaRepository<tbItemCodeSubstitute, Long> {

    tbItemCodeSubstitute findByRecordNo(long recordNo);

    List<tbItemCodeSubstitute> findByItemCodeAndRelatedItemCode(String itemCode, String relatedItemCode);

    List<tbItemCodeSubstitute> findByRelatedItemCode(String relatedItemCode);

}
