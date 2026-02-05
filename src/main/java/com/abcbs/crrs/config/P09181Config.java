package com.abcbs.crrs.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class P09181Config {
	
	@Autowired
    private com.abcbs.crrs.listener.JobLoggingListener jobLoggingListener;
	
	// 1. Reader: reads 2300-char records from CCM-XML-FILE
    @Bean(name = "p09181Reader")
    @StepScope
    public FlatFileItemReader<String> p09181Reader(
        @Value("#{jobParameters['inputFile']}") String inputFile) {

        FlatFileItemReader<String> reader = new FlatFileItemReader<>();
        reader.setResource(new ClassPathResource(inputFile)); // look inside resources
        reader.setLineMapper((line, lineNumber) -> line);
        return reader;
    }




	
	// 2. Processor: replicates 200000-PROCESS-RECORDS + 300000-ADD-SEMI
    @Bean(name = "p09181Processor")
    public ItemProcessor<String, List<String>> p09181Processor() {
        return record -> {
            // Step 1: sanitize apostrophes
            String sanitized = record.replace("'", " ");

            // Step 2: trim trailing spaces
            int actualLength = sanitized.stripTrailing().length();
            String trimmed = sanitized.substring(0, actualLength);

            // Step 3: add delimiters (simulate 300000-ADD-SEMI)
            String delimited = addDelimiters(trimmed);

            // Step 4: split into fields
            String[] fields = delimited.split("\\|", -1);

            // Step 5: build XML lines
            List<String> xmlLines = new ArrayList<>();
            xmlLines.add("<RECORD_INFO>");
            xmlLines.add("<CCM-INFO>" + stripTags(fields[0]) + "</CCM-INFO>");
            xmlLines.add("<MEMBER-ID>" + stripTags(fields[1]) + "</MEMBER-ID>");
            xmlLines.add("<CCM-TYPE>" + stripTags(fields[2]) + "</CCM-TYPE>");
            xmlLines.add("<LOB>" + stripTags(fields[3]) + "</LOB>");
            xmlLines.add("<BUS-LOCATION>" + stripTags(fields[4]) + "</BUS-LOCATION>");
            xmlLines.add("<LOCATION-NBR>" + stripTags(fields[5]) + "</LOCATION-NBR>");
            xmlLines.add("<OTIS-NBR>" + stripTags(fields[6]) + "</OTIS-NBR>");
            xmlLines.add("<SECTION-CODE>" + stripTags(fields[7]) + "</SECTION-CODE>");
            xmlLines.add("<REFUND-TYPE>" + stripTags(fields[8]) + "</REFUND-TYPE>");
            xmlLines.add("<CONTROL-DATE>" + stripTags(fields[9]) + "</CONTROL-DATE>");
            xmlLines.add("<CONTROL-NBR>" + stripTags(fields[10]) + "</CONTROL-NBR>");
            xmlLines.add("<STATUS-CODE>" + stripTags(fields[11]) + "</STATUS-CODE>");
            xmlLines.add("<STATUS-DATE>" + stripTags(fields[12]) + "</STATUS-DATE>");
            xmlLines.add("<EOB-IND>" + stripTags(fields[13]) + "</EOB-IND>");
            xmlLines.add("<RECEIPT-TYPE>" + stripTags(fields[14]) + "</RECEIPT-TYPE>");
            xmlLines.add("<REMITTOR-NAME>" + stripTags(fields[15]) + "</REMITTOR-NAME>");
            xmlLines.add("<REMITTOR-TITLE>" + stripTags(fields[16]) + "</REMITTOR-TITLE>");
            xmlLines.add("<REMITTOR-TYPE>" + stripTags(fields[17]) + "</REMITTOR-TYPE>");
            xmlLines.add("<CLAIM-TYPE>" + stripTags(fields[18]) + "</CLAIM-TYPE>");
            xmlLines.add("<OPL-IND>" + stripTags(fields[19]) + "</OPL-IND>");
            xmlLines.add("<LETTER-DATE>" + stripTags(fields[20]) + "</LETTER-DATE>");
            xmlLines.add("<REASON-CODE>" + stripTags(fields[21]) + "</REASON-CODE>");
            xmlLines.add("<OTHER-CORR>" + stripTags(fields[22]) + "</OTHER-CORR>");
            xmlLines.add("<COMMENTS>" + stripTags(fields[23]) + "</COMMENTS>");
            xmlLines.add("<PATIENT-FNAME>" + stripTags(fields[24]) + "</PATIENT-FNAME>");
            xmlLines.add("<PATIENT-LNAME>" + stripTags(fields[25]) + "</PATIENT-LNAME>");
            xmlLines.add("<ADDR1>" + stripTags(fields[26]) + "</ADDR1>");
            xmlLines.add("<ADDR2>" + stripTags(fields[27]) + "</ADDR2>");
            xmlLines.add("<CITY>" + stripTags(fields[28]) + "</CITY>");
            xmlLines.add("<STATE>" + stripTags(fields[29]) + "</STATE>");
            xmlLines.add("<ZIP>" + stripTags(fields[30]) + "</ZIP>");
            xmlLines.add("<CHECK-DATE>" + stripTags(fields[31]) + "</CHECK-DATE>");
            xmlLines.add("<CHECK-NBR>" + stripTags(fields[32]) + "</CHECK-NBR>");
            xmlLines.add("<CHECK-AMOUNT>" + stripTags(fields[33]) + "</CHECK-AMOUNT>");
            xmlLines.add("<CONTROLLED-AMOUNT>" + stripTags(fields[34]) + "</CONTROLLED-AMOUNT>");
            xmlLines.add("<LOCATION-CODE>" + stripTags(fields[35]) + "</LOCATION-CODE>");
            xmlLines.add("<FORM-COUNT>" + stripTags(fields[36]) + "</FORM-COUNT>");
            xmlLines.add("<TOTAL-CONTROLLED-AMOUNT>" + stripTags(fields[37]) + "</TOTAL-CONTROLLED-AMOUNT>");
            xmlLines.add("</RECORD_INFO>");
            return xmlLines;
        };
    }
	
	// Helper method for 300000-ADD-SEMI
    private String addDelimiters(String input) {
        StringBuilder sb = new StringBuilder();
        boolean slashFound = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '/') {
                slashFound = true;
                sb.append(c);
            } else if (c == '>' && slashFound) {
                sb.append(c);
                sb.append('|');   // insert delimiter
                slashFound = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
	
	// 3. Writer: writes XML lines to CCM-XML-EDITED
    @Bean(name = "p09181Writer")
    @StepScope
    public FlatFileItemWriter<List<String>> p09181Writer(
        @Value("#{jobParameters['outputFile']}") String outputFile) {

        FlatFileItemWriter<List<String>> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource(outputFile));
        writer.setAppendAllowed(false);

        writer.setLineAggregator(lines -> String.join("\n", lines));
        writer.setHeaderCallback(fileWriter -> fileWriter.write("<ROW>"));
        writer.setFooterCallback(fileWriter -> fileWriter.write("</ROW>"));

        return writer;
    }


	
	// 4. Step definition
    @Bean(name = "p09181Step")
    public Step p09181Step(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           @Value("#{p09181Reader}") ItemReader<String> reader,
                           @Value("#{p09181Processor}") ItemProcessor<String, List<String>> processor,
                           @Value("#{p09181Writer}") ItemWriter<List<String>> writer) {
        return new StepBuilder("p09181Step", jobRepository)
            .<String, List<String>>chunk(2000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
	
	// 5. Job definition
    @Bean(name = "p09181Job")
    public Job p09181Job(JobRepository jobRepository, @Value("#{p09181Step}") Step step) {
        return new JobBuilder("p09181Job", jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(jobLoggingListener)
            .start(step)
            .build();
    }
    
    private String stripTags(String value) {
        return value.replaceAll("<[^>]+>", ""); // remove anything between <...>
    }

}
