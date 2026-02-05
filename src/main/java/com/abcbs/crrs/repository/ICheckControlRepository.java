package com.abcbs.crrs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.abcbs.crrs.entity.CheckControl;
import com.abcbs.crrs.entity.CheckControlPK;

public interface ICheckControlRepository extends JpaRepository<CheckControl, CheckControlPK> {

	@Query("""
			    SELECT c
			    FROM CheckControl c
			    WHERE c.ccId.fileIndicator = :fileIndicator
			      AND c.ccId.bankAccountNbr = :bankAccountNbr
			      AND c.ccId.openIndicator = 'O'
			""")
	Optional<CheckControl> findOpenCheckControl(String fileIndicator, String bankAccountNbr);
}