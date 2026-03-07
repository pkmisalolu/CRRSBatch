/**
* @author Chandrakanth Gangalam
* Created on Feb 10, 2026
*/

package com.abcbs.crrs.jobs.P09350;

import java.io.Serializable;
import java.util.List;

import com.abcbs.crrs.jobs.P09352.P09352ApInterfaceOutput;

public class P09350OutputWrapper implements Serializable{
	private static final long serialVersionUID = 1L;
	
	private List<LastVoucherNbrRec> LastVoucherRecords;
	private List<P09350XP09DedsOutput> glRecords;
	private List<ApIntrfaceOutRec> apIntrfaceRecords;
	private List<P09350XP07DedsOutput> occsRecords;
	private List<P09350ApInterfaceOutput> apRecords;
	
	public List<LastVoucherNbrRec> getLastVoucherRecords() {
		return LastVoucherRecords;
	}
	public void setLastVoucherRecords(List<LastVoucherNbrRec> lastVoucherRecords) {
		LastVoucherRecords = lastVoucherRecords;
	}
	public void setGlRecords(List<P09350XP09DedsOutput> glRecords) {
		this.glRecords = glRecords;
	}
	public List<P09350XP07DedsOutput> getOccsRecords() {
		return occsRecords;
	}
	public void setOccsRecords(List<P09350XP07DedsOutput> occsRecords) {
		this.occsRecords = occsRecords;
	}
	public List<ApIntrfaceOutRec> getApIntrfaceRecords() {
		return apIntrfaceRecords;
	}
	public void setApIntrfaceRecords(List<ApIntrfaceOutRec> apIntrfaceRecords) {
		this.apIntrfaceRecords = apIntrfaceRecords;
	}
	
	public List<P09350ApInterfaceOutput> getApRecords() {
        return apRecords;
    }
	
	public List<P09350XP09DedsOutput> getGlRecords() {
        return glRecords;
    }
	
	public void setApRecords(List<P09350ApInterfaceOutput> apRecords) {
        this.apRecords = apRecords;
    }
}
