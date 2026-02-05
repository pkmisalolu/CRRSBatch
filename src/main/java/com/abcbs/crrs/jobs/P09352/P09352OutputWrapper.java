package com.abcbs.crrs.jobs.P09352;

import java.util.List;
import java.io.Serializable;

public class P09352OutputWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<P09352ApInterfaceOutput> apRecords;
    private List<P09352XP09DedsOutput> glRecords;
    private List<P09352XP07DedsOutput> occsRecords;

    public List<P09352ApInterfaceOutput> getApRecords() {
        return apRecords;
    }

    public void setApRecords(List<P09352ApInterfaceOutput> apRecords) {
        this.apRecords = apRecords;
    }

    public List<P09352XP09DedsOutput> getGlRecords() {
        return glRecords;
    }

    public void setGlRecords(List<P09352XP09DedsOutput> glRecords) {
        this.glRecords = glRecords;
    }

    public List<P09352XP07DedsOutput> getOccsRecords() {
        return occsRecords;
    }

    public void setOccsRecords(List<P09352XP07DedsOutput> occsRecords) {
        this.occsRecords = occsRecords;
    }
}