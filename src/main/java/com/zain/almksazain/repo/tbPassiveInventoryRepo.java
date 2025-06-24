/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbPassiveInventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author jgithu
 */

public interface tbPassiveInventoryRepo extends JpaRepository<tbPassiveInventory, Long> {

    List<tbPassiveInventory> findByItemCodeAndSerialNumber(String itemCode, String serialNumber);
}
