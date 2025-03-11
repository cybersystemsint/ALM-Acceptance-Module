/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.almKSAZain.repo;

import com.telkom.almKSAZain.model.tbSerialNumber;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author jgithu
 */
public interface tbSerialNumberRepo extends JpaRepository<tbSerialNumber, Long> {

    List<tbSerialNumber> findBySerialNumber(String serialNumber);

    List<tbSerialNumber> findBySerialNumberAndItemCode(String serialNumber, String itemCode);

}
