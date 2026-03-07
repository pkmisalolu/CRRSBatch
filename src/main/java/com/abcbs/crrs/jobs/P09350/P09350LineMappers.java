/**
* @author Chandrakanth Gangalam
* Created on Feb 10, 2026
*/

package com.abcbs.crrs.jobs.P09350;

import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;

public class P09350LineMappers {
	public static LineMapper<P09350CorpCardInput> corpCardLineMapper() {

	       DefaultLineMapper<P09350CorpCardInput> lm = new DefaultLineMapper<>();
	       FixedLengthTokenizer t = new FixedLengthTokenizer();
	       t.setStrict(false);

	       t.setNames(
	           "filler1",
	           "corpCode",
	           "filler2"
	       );

	       t.setColumns(
	           new Range(1,10),   // filler1   X(10)
	           new Range(11,12),  // corpCode  X(02)
	           new Range(13,80)   // filler2   X(68)
	       );

	       lm.setLineTokenizer(t);

	       lm.setFieldSetMapper(fs -> {
	    	   P09350CorpCardInput o = new P09350CorpCardInput();
	           o.setFiller1(fs.readString("filler1"));
	           o.setCorpCode(fs.readString("corpCode"));
	           o.setFiller2(fs.readString("filler2"));
	           return o;
	       });

	       return lm;
	   }
	
	public static LineMapper<ChkpCardRec> checkpointLineMapper() {

	       DefaultLineMapper<ChkpCardRec> lm = new DefaultLineMapper<>();
	       FixedLengthTokenizer t = new FixedLengthTokenizer();
	       t.setStrict(false);

	       t.setNames(
	           "count",
	           "filler"
	       );

	       t.setColumns(
	           new Range(1,6),    // count   9(06)
	           new Range(7,80)    // filler  X(74)
	       );

	       lm.setLineTokenizer(t);

	       lm.setFieldSetMapper(fs -> {
	    	   ChkpCardRec o = new ChkpCardRec();
	           o.setChkpCardCnt(fs.readInt("count"));
	           o.setFiller(fs.readString("filler"));
	           return o;
	       });

	       return lm;
	   }
	
	public static LineMapper<InputVoucherRec> inputVoucherLineMapper() {

        DefaultLineMapper<InputVoucherRec> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
            "inputLastVoucherNbrPrefix",
            "inputLastVoucherNbrSuffix",
            "inputLastVoucherNbrFiller"
        );

        t.setColumns(
            new Range(1,1),
            new Range(2,6),
            new Range(7,80)
        );

        lm.setLineTokenizer(t);

        lm.setFieldSetMapper(fs -> {
        	InputVoucherRec o = new InputVoucherRec();
        	o.setPrefix(fs.readString("inputLastVoucherNbrPrefix"));
        	o.setSuffix(fs.readInt("inputLastVoucherNbrSuffix"));
        	o.setFiller(fs.readString("inputLastVoucherNbrFiller"));
            return o;
        });

        return lm;
    }
}
