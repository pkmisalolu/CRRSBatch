package com.abcbs.crrs.jobs.P09390;

import org.springframework.batch.item.ItemProcessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class P09390Processor implements ItemProcessor<P09390InputFepmanChks, P09390OutputFepVendor> {

    private final Map<String, String> vendorByNbr;
    private final Map<String, String> vendorByName;
    private final Map<String, AtomicInteger> suffixTally = new ConcurrentHashMap<>();

    private static final Map<String, String> SPECIAL_SUFFIX = Map.ofEntries(
            Map.entry("ARKANSAS BLUE CROSS BLUE SHIEL", "-BC"),
            Map.entry("AR BLUE CROSS BLUE SHIELD", "-BC"),
            Map.entry("HEALTH ADVANTAGE", "-HA"),
            Map.entry("BLUE ADVANTAGE", "-BA"),
            Map.entry("MEDIPAK ADVANTAGE", "-MA"),
            Map.entry("MAPD ADVANTAGE", "-MA"),
            Map.entry("MAPD HMO", "-MH"),
            Map.entry("MEDIPAK ADVANTAGE HMO", "-MH")
    );

    private static final String[] SEQ = new String[]{
            "-M ", "-N ", "-O ", "-P ", "-Q ", "-R ", "-S ", "-T ", "-U ", "-V ",
            "-W ", "-X ", "-Y ", "-Z ", "-MM", "-NN", "-OO", "-PP", "-QQ", "-RR",
            "-SS", "-TT", "-UU", "-VV", "-WW"
    };

    public P09390Processor(Map<String, String> vendorByNbr, Map<String, String> vendorByName) {
        this.vendorByNbr = vendorByNbr;
        this.vendorByName = vendorByName;
    }

    @Override
    public P09390OutputFepVendor process(P09390InputFepmanChks in) {

        P09390OutputFepVendor out = new P09390OutputFepVendor();

        String provider = trim(in.getProviderNbr());
        String name = utrim(in.getPayeeName());

        // ===== TABLE LOOKUPS LIKE COBOL (0600 & 0610 & 0620) =====

        String resolved = null;

        // exact number+name
        if (vendorByNbr.containsKey(provider)) {
            if (eq(vendorByNbr.get(provider), name)) resolved = provider;
        }

        // number only
        if (resolved == null && vendorByNbr.containsKey(provider)) {
            resolved = provider;
        }

        // name only
        if (resolved == null && vendorByName.containsKey(name)) {
            resolved = vendorByName.get(name);
        }

        // ===== SUFFIX LOGIC (0225 / 0230 / 0240) =====

        String vendorBase = provider.isBlank() ? "999999999" : provider;
        String suffix = null;

        if (resolved == null) {

            suffix = SPECIAL_SUFFIX.entrySet().stream()
                    .filter(e -> name.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);

            if (suffix == null) {
                AtomicInteger counter = suffixTally.computeIfAbsent(vendorBase, k -> new AtomicInteger(0));
                int n = counter.incrementAndGet();
                suffix = suffixFrom(n);
            }

            resolved = vendorBase;
        }

        String outVendorNbr = suffix == null ? resolved : (resolved + suffix).trim();
        out.setProviderNbr(fit(outVendorNbr, 9));

        // ===== MOVE FIELDS (COBOL 0250) =====

        out.setName(fit(in.getPayeeName(), 36));
        out.setAddr1(fit(in.getAddr1(), 36));
        out.setAddr2(fit(in.getAddr2(), 36));
        out.setCity(fit(in.getCity(), 24));
        out.setState(fit(in.getState(), 2));

        String zip = trim(in.getZip1()) + trim(in.getZip2());
        out.setZip(fit(zip, 5));

        out.setInvoiceDate(fit(invoiceDate(in), 10));
        out.setRefundType(fit(in.getRefundType(), 3));
        out.setCntrlDate(pickCntrlDate(in.getCntrlDate()));
        out.setCntrlNbr(fit(in.getCntrlNbr(), 4));
        out.setInvoiceAmt(formatAmt(in.getInvoiceAmt()));
        out.setDesc(fit(in.getDesc(), 40));
        out.setCrrs("CRRS");
        out.setPayeeIdType(fit(in.getIdType(), 1));
        out.setCheckType(fit(in.getCheckType(), 2));

        // fillers
        out.setFiller1(" ");
        out.setFiller2(" ");
        out.setFiller3(" ");
        out.setFiller4(" ");
        out.setFiller5(" ");
        out.setFiller6(" ");
        out.setFiller7(" ");
        out.setFiller8(" ");
        out.setFiller9(" ");
        out.setFiller10(" ");
        out.setFiller11(" ");
        out.setFiller12(" ");
        out.setFiller13(" ");
        out.setFiller14(" ");
        out.setFiller15("  ");

        return out;
    }

    private static String pickCntrlDate(String s) {
        if (s == null) return "00000000";

        s = s.trim();
        // expected: yyyy-MM-dd
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            String yyyy = s.substring(0, 4);
            String mm   = s.substring(5, 7);
            String dd   = s.substring(8,10);
            String yy   = yyyy.substring(2,4);
            return mm + "/" + dd + "/" + yy;   // return 8-byte COBOL date
        }

        return "00000000";
    }
    
    // ===== Helper Methods =====

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String utrim(String s) {
        return trim(s).toUpperCase();
    }

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static String fit(String s, int len) {
        s = s == null ? "" : s;
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }

    private static String invoiceDate(P09390InputFepmanChks in) {
        String mm = trim(in.getInvMM());
        String dd = trim(in.getInvDD());
        String cc = trim(in.getInvCC());
        String yy = trim(in.getInvYY());
        if (mm.length() == 1) mm = "0" + mm;
        if (dd.length() == 1) dd = "0" + dd;
        return mm + "/" + dd + "/" + (cc + yy);
    }

    private static String formatAmt(String amt) {
        if (amt == null || amt.isBlank()) return "00000000000";
        String n = amt.replaceAll("\\D", "");
        return leftPad(n, 11);
    }

    private static String leftPad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return "0".repeat(len - s.length()) + s;
    }

    private static String suffixFrom(int idx) {
        int i = idx - 1;
        if (i < SEQ.length) return SEQ[i];
        return "-WW";
    }
}