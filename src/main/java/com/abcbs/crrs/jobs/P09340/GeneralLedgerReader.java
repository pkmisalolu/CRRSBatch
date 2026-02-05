package com.abcbs.crrs.jobs.P09340;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.utilities.NoTrimLineTokenizer;

@Component
@StepScope
public class GeneralLedgerReader extends FlatFileItemReader<P09340GlRecord> {
	
	private static final Logger logger = LogManager.getLogger(GeneralLedgerReader.class);


	public GeneralLedgerReader(@Value("#{jobParameters['glFile']}") String glFilePath,
            ResourceLoader resourceLoader) {

		logger.info("Initializing GeneralLedgerReader for file: {}", glFilePath);
		
        NoTrimLineTokenizer tokenizer = new NoTrimLineTokenizer();
        tokenizer.setColumns(
                new Range(1, 5),     
                new Range(6, 12),    
                new Range(13,17),
                new Range(18, 19),   
                new Range(20, 22),   
                new Range(23, 32),   
                new Range(33, 36),   
                new Range(37, 38),   
                new Range(39, 73),   
                new Range(74, 85),   
                new Range(86, 88),   
                new Range(89, 100),  
                new Range(101, 110),  
                new Range(111, 112),  
                new Range(113, 114),  
                new Range(115, 129),  
                new Range(130, 140),  
                new Range(141, 152),  
                new Range(153, 154),  
                new Range(155, 174),  
                new Range(175, 184),  
                new Range(185, 196),  
                new Range(197, 198)   
        		);

        tokenizer.setNames(
            "p09dedsJulian",
            "p09dedsHhmmss",
            "p09deds29",
            "glP09dedsId",
            "glRefundType",
            "glControlDate",
            "glControlNbr",
            "glReceiptType",
            "glBankAcctNbr",
            "glAcctNbr",
            "glActCode",
            "glActAmt",
            "glActDate",
            "glReportMo",
            "glReportYr",
            "glPatientLn",
            "glPatientFn",
            "glMbrIdNbr",
            "glXrefType",
            "glXrefClaimNbr",
            "glXrefDate",     
            "glCashRecBal",
            "glCorp"
        );
        
        // Configure mapper
        BeanWrapperFieldSetMapper<P09340GlRecord> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(P09340GlRecord.class);
        fieldSetMapper.setDistanceLimit(0);
        fieldSetMapper.setStrict(false);

        DefaultLineMapper<P09340GlRecord> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        //Resource resource = resourceLoader.getResource(new FileSystemResource(glFilePath));
        //Configure reader
        this.setResource(new FileSystemResource(glFilePath));
        this.setLineMapper(lineMapper);
        this.setStrict(true);
        this.setLinesToSkip(0);
        this.setName("generalLedgerReader");
        
        logger.info("GeneralLedgerReader initialized successfully.");
    }
}
