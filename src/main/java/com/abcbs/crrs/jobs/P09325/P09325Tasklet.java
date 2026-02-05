package com.abcbs.crrs.jobs.P09325;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.Resource;

import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09SummaryRepository;

public class P09325Tasklet implements Tasklet {

	private final IP09CashReceiptRepository repo;
	private final IP09SummaryRepository summaryRepo;
	private final RoutingReportWriter writerA;
	private final SummaryReportWriter writerB;
	private final String outA, outB;
	private String lastKey;
	
	private static final Logger LOG = LogManager.getLogger(P09325Tasklet.class);

	public P09325Tasklet(IP09CashReceiptRepository repo,IP09SummaryRepository summaryRepo, RoutingReportWriter writerA, SummaryReportWriter writerB,
			Resource chkpFile, String outA, String outB, String checkpointKey) {
		
		this.summaryRepo = summaryRepo;
		this.repo = repo;
		this.writerA = writerA;
		this.writerB = writerB;
		this.outA = outA;
		this.outB = outB;
		this.lastKey = checkpointKey == null ? "" : checkpointKey;
	}

	@Override
	public RepeatStatus execute(StepContribution c, ChunkContext ctx) throws Exception {
	    final int freq = 2;                              
	    final LocalDateTime now = LocalDateTime.now();
	    writerA.open(outA, now);                         
	    writerB.open(outB, now);
	    List<P09325RoutingView> fetchRoutingPage = repo.fetchRoutingPage(lastKey);
	    LOG.info("Records fetched: " + fetchRoutingPage.size());

	    if (!fetchRoutingPage.isEmpty()) {
	        String curArea = null;
	        String curClerk = null;
	        long sinceCkpt = 0L;
	        int written = 0;

	        for (P09325RoutingView r : fetchRoutingPage) {                  
	            // section boundary?
	            if (curArea == null || !curArea.equals(r.getArea()) || !curClerk.equals(r.getClerk())) {
	                // open new section
	                Optional<P09325kHeaderView> hdr = summaryRepo.fetchHeader(r.getArea(), r.getClerk());
	                writerA.startSection(r.getArea(), r.getClerk(), hdr.orElse(null));
	                curArea = r.getArea();
	                curClerk = r.getClerk();
	            }

	            writerA.writeDetail(r);                  
	            written++;
	            sinceCkpt++;
//	            // advance lastKey every row
	            lastKey = IP09SummaryRepository.makeKey(r);

	            // optional checkpoint by frequency
	            if (sinceCkpt >= freq) {
	                writerA.noteCheckpoint(lastKey, sinceCkpt);
	                sinceCkpt = 0L;
	            }
	        }

	        // close last open section
	        writerA.finishSectionTotals();

	
	        // SUMMARY Execution Started
	
			var list = summaryRepo.fetchControllerSummary();
			
			writerB.writeControllerSummary(list);
			
			writerB.noteUpdate(summaryRepo.rollSummary());
			writerB.closeB();
	        }    
	    
	    writerA.closeA();                                
	    return RepeatStatus.FINISHED;
	}

}
