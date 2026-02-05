package com.abcbs.crrs.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Embeddable
@NoArgsConstructor
public class OptionPK implements Serializable{
	
	//OPT_RECORD_TYPE                SMALLINT NOT NULL
    @Column(name = "OPT_RECORD_TYPE", nullable = false)
    private short optRecordType;
    
	//OPT_SEQ_NBR                    SMALLINT NOT NULL
    @Column(name = "OPT_SEQ_NBR", nullable = false)
    private short optSeqNbr;
	
}
