/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Option entity with composite primary key
 */
package com.abcbs.crrs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="P09_OPTION")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Option {
	
	@EmbeddedId
	private OptionPK optId;
	
    @Column(name = "OPT_FIELD_NAME", nullable = false, length = 20)
    private String optFieldName;
	
    @Column(name = "OPT_FIELD_NARR", nullable = false, length = 79)
    private String optFieldNarr;

}
