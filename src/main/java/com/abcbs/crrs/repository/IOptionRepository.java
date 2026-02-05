package com.abcbs.crrs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.OptionPK;
import com.abcbs.crrs.entity.P09Option;

public interface IOptionRepository extends JpaRepository<P09Option, OptionPK> {

	@Query(value = """
			SELECT
			  opt_record_type,
			  opt_seq_nbr,
			  opt_field_name,
			  opt_field_narr
			FROM P09_OPTION
			WHERE opt_record_type = :recordType
			  AND opt_field_narr LIKE :likeValue
			ORDER BY opt_seq_nbr
			""", nativeQuery = true)
	List<P09Option> findOptions(@Param("recordType") Short recordType, @Param("likeValue") String likeValue);
	
	 /**
     * COBOL equivalent of:
     * SELECT OPT_SEQ_NBR, OPT_FIELD_NAME, OPT_FIELD_NARR
     *   INTO host variables
     *   FROM V_P09_OPTION
     *  WHERE OPT_RECORD_TYPE = :W-RECORD-TYPE
     *    AND OPT_FIELD_NARR LIKE :W-NARRATIVE
     *
     * Returns the first row by OPT_SEQ_NBR (COBOL SELECT INTO behavior)
     */
    @Query("""
           SELECT o
           FROM P09Option o
           WHERE o.optId.optRecordType = :recordType
             AND o.optFieldNarr LIKE :likeValue
           ORDER BY o.optId.optSeqNbr
           """)
    List<P09Option> findP09175Option(@Param("recordType") short recordType,
                                    @Param("likeValue") String likeValue);
}