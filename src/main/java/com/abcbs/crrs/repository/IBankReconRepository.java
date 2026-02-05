package com.abcbs.crrs.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.BankRecon;
import com.abcbs.crrs.entity.BankReconPK;

public interface IBankReconRepository extends JpaRepository<BankRecon, BankReconPK> {

	// *****************************P07505****************************************************
	@Query("""
			SELECT r.brId.checkNbr FROM BankRecon r WHERE r.brId.checkNbr = :checkNbr AND r.brId.bankAccountNbr = :bankAccountNbr
			AND r.brId.checkDate = :checkDate
			""")
	public String findCheckNbr(@Param("checkNbr") String checkNbr, @Param("bankAccountNbr") String bankAccountNbr,
			@Param("checkDate") LocalDate checkDate);

	// SELECT (COBOL)
	@Query("""
			    SELECT b
			    FROM BankRecon b
			    WHERE b.brId.bankAccountNbr = :bankAcctNbr
			      AND b.brId.checkNbr = :checkNbr
			      AND b.brId.checkDate = :checkDate
			""")
	Optional<BankRecon> findBankReconRecord(String bankAcctNbr, String checkNbr, LocalDate checkDate);

	// UPDATE (COBOL)
	@Modifying
	@Query("""
			    UPDATE BankRecon b
			    SET b.checkStatusCode  = :checkStatusCode,
			        b.checkStatusDate  = :checkStatusDate,
			        b.statusSourceCode = :statusSourceCode,
			        b.stDaily          = :stDaily,
			        b.reportDate       = :reportDate
			    WHERE b.fileIndicator = :fileIndicator
			      AND b.brId.bankAccountNbr = :bankAccountNbr
			      AND b.brId.checkNbr = :checkNbr
			      AND b.brId.checkDate = :checkDate
			""")
	int updateBankReconRecord(String checkStatusCode, LocalDate checkStatusDate, String statusSourceCode,
			String stDaily, LocalDate reportDate, String fileIndicator, String bankAccountNbr, String checkNbr,
			LocalDate checkDate);

}
