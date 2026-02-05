/**
 * Developer: Dharani Parimella
 * Description: This class handles BankDescription entity with composite primary key
 */
package com.abcbs.crrs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="BANK_DESCRIPTION")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDescription {
	
	@Id
    @Column(name = "BANK_ACCOUNT_NBR", length = 35, nullable = false)
    private String bankAccountNbr;

    @Column(name = "BANK_NAME", length = 30, nullable = false)
    private String bankName;

    @Column(name = "BANK_TYPE", length = 2, nullable = false)
    private String bankType;

    @Column(name = "BANK_TYPE_DESC", length = 20, nullable = false)
    private String bankTypeDesc;
    

}
