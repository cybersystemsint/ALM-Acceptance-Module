package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.TagNumber;

// tb_TagNumber.tagNumber has a binary collation, so every tagNumber comparison below is
// case-sensitive ('ABC' <> 'abc') without any extra handling here.
public interface TagNumberRepo extends JpaRepository<TagNumber, Integer> {

    /** Non-deleted rows for these vendors whose TAG is one of tagNumbers - used for the uniqueness check. */
    @Query("SELECT t FROM TagNumber t WHERE t.status <> 'Deleted' "
            + "AND t.vendorNumber IN :vendorNumbers AND t.tagNumber IN :tagNumbers")
    List<TagNumber> findNonDeleted(@Param("vendorNumbers") Collection<String> vendorNumbers,
            @Param("tagNumbers") Collection<String> tagNumbers);

    /** Rows usable on an acceptance request: this vendor's 'Active' TAGs among tagNumbers. */
    @Query("SELECT t FROM TagNumber t WHERE t.status = 'Active' "
            + "AND t.vendorNumber = :vendorNumber AND t.tagNumber IN :tagNumbers")
    List<TagNumber> findActiveForVendor(@Param("vendorNumber") String vendorNumber,
            @Param("tagNumbers") Collection<String> tagNumbers);
}
