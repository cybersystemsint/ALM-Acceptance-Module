package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.TagNumber;

// tb_TagNumber.tagNumber uses the schema's case-insensitive collation, so every tagNumber
// comparison below matches 'ABC' to 'abc' without any extra handling here.
public interface TagNumberRepo extends JpaRepository<TagNumber, Integer> {

    /** Non-deleted rows whose TAG is one of tagNumbers - uniqueness and acceptance request checks. */
    @Query("SELECT t FROM TagNumber t WHERE t.status <> 'Deleted' AND t.tagNumber IN :tagNumbers")
    List<TagNumber> findNonDeleted(@Param("tagNumbers") Collection<String> tagNumbers);
}
