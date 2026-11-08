package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.SerialNumber;

// tb_SerialNumber.serialNumber / itemCode use the schema's case-insensitive collation, so every
// comparison below matches 'ABC' to 'abc' without any extra handling here. Both queries use the
// (serialNumber, itemCode) index.
public interface SerialNumberRepo extends JpaRepository<SerialNumber, Integer> {

    /**
     * Non-deleted rows of these serial numbers, locked until the transaction ends - the
     * uniqueness and Validation Rule checks of a save. Locking (including the index gap of a
     * serial number with no rows yet) stops two saves of the same serial number interleaving.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerialNumber s WHERE s.status <> 'Deleted' AND s.serialNumber IN :serialNumbers")
    List<SerialNumber> lockNonDeleted(@Param("serialNumbers") Collection<String> serialNumbers);

    /** Active rows of one serial number - the acceptance request check (APIController#postdcc). */
    @Query("SELECT s FROM SerialNumber s WHERE s.status = 'Active' AND s.serialNumber = :serialNumber")
    List<SerialNumber> findActive(@Param("serialNumber") String serialNumber);
}
