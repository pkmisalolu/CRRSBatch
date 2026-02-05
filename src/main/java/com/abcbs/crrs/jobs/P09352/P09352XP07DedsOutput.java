package com.abcbs.crrs.jobs.P09352;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Full COBOL-faithful mapping of I-P07DEDS-RECORD (P07 - OCCS DEDS) — Option A (full fidelity).
 * All COBOL fields included in same order, including NF COMP-3 flags and filler fields.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352XP07DedsOutput implements Serializable {
    private static final long serialVersionUID = 1L;

    // I-P07DEDS-DATE-TIME.
    private BigDecimal p07JulianDate;                 // PIC S9(05) COMP-3.
    private BigDecimal p07TimeHhmmsss;                // PIC S9(07) COMP-3.

    // FILLER PIC X(05)
    private String p07Filler1;                        // PIC X(05).

    // Core record identifiers
    private String p07ActionId;                       // PIC X(02).  ('01' insert, '02' update)
    private String p07BatchNbr;                       // PIC X(06).
    private String p07FileCode;                       // PIC X(01).

    // Account
    private String p07AccountNbr;                     // PIC X(35).

    // Check number group
    private String p07ChkFiller;                      // PIC X(02).
    private String p07ChkNbr;                         // PIC X(06).

    // Check date and amount
    private String p07ChkDate;                        // PIC X(10).
    private BigDecimal p07ChkAmt;                     // PIC S9(09)V99 COMP-3.

    // Payee / identification
    private String p07PayeeId;                        // PIC X(09).
    private String p07Npi;                            // PIC X(10).
    private String p07PayeeName;                      // PIC X(36).
    private String p07Title;                          // PIC X(03).
    private String p07Addr1;                          // PIC X(36).
    private String p07Addr2;                          // PIC X(36).
    private String p07City;                           // PIC X(24).
    private String p07State;                          // PIC X(02).

    // ZIP fields (split)
    private String p07Zip5;                           // PIC X(05).
    private String p07Zip4;                           // PIC X(04).

    // Check metadata
    private String p07ChkOrigin;                      // PIC X(01).
    private String p07ChkType;                        // PIC X(02).
    private String p07ChkStatus;                      // PIC X(02).
    private String p07ChkStatusDate;                  // PIC X(10).
    private String p07StatusSource;                   // PIC X(02).

    // Daily indicators / counts
    private String p07OsDaily;                        // PIC X(01).
    private String p07StDaily;                        // PIC X(01).
    private String p07SdDaily;                        // PIC X(01).
    private Integer p07SdDailyNf;                     // PIC S9(01) COMP-3.
    private String p07TsDaily;                        // PIC X(01).
    private Integer p07TsDailyNf;                     // PIC S9(01) COMP-3.

    // Stale check fields
    private String p07StaleDate;                      // PIC X(10).
    private Integer p07StaleDateNf;                   // PIC S9(01) COMP-3.
    private String p07StaleOrigin;                    // PIC X(01).
    private Integer p07StaleOriginNf;                 // PIC S9(01) COMP-3.

    // Transfer fields
    private String p07TransferDate;                   // PIC X(10).
    private Integer p07TransferDateNf;                // PIC S9(01) COMP-3.
    private String p07TransferOrigin;                 // PIC X(01).
    private Integer p07TransferOriginNf;              // PIC S9(01) COMP-3.

    // Reissue fields
    private String p07ReissueAcctNbr;                 // PIC X(35).
    private Integer p07ReissueAcctNbrNf;              // PIC S9(01) COMP-3.
    private String p07ReissueChkNbr;                  // PIC X(08).
    private Integer p07ReissueChkNbrNf;               // PIC S9(01) COMP-3.
    private String p07ReissueChkDate;                 // PIC X(10).
    private Integer p07ReissueChkDateNf;              // PIC S9(01) COMP-3.
    private String p07ReissueChkType;                 // PIC X(02).
    private Integer p07ReissueChkTypeNf;              // PIC S9(01) COMP-3.

    // Report date
    private String p07ReportDate;                     // PIC X(10).

    // Initial check fields
    private String p07InitialAcctNbr;                 // PIC X(35).
    private Integer p07InitialAcctNbrNf;              // PIC S9(01) COMP-3.
    private String p07InitialChkNbr;                  // PIC X(08).
    private Integer p07InitialChkNbrNf;               // PIC S9(01) COMP-3.
    private String p07InitialChkDate;                 // PIC X(10).
    private Integer p07InitialChkDateNf;              // PIC S9(01) COMP-3.
    private String p07InitialChkType;                 // PIC X(02).
    private Integer p07InitialChkTypeNf;              // PIC S9(01) COMP-3.

    // PPA date
    private String p07PpaDate;                        // PIC X(10).
    private Integer p07PpaDateNf;                     // PIC S9(01) COMP-3.

    // Payee id type and NF
    private String p07PayeeIdType;                    // PIC X(01).
    private Integer p07PayeeIdTypeNf;                 // PIC S9(01) COMP-3.

    // Tax ID and NF
    private String p07TaxIdNbr;                       // PIC X(09).
    private Integer p07TaxIdNbrNf;                    // PIC S9(01) COMP-3.

    // Final filler
    private String p07FillerFinal;                    // PIC X(55).
}