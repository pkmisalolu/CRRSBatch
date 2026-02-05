package com.abcbs.crrs.utilities;

import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.item.file.transform.LineTokenizer;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.Range;

import java.util.ArrayList;
import java.util.List;

public class NoTrimLineTokenizer implements LineTokenizer {

    private Range[] ranges;
    private String[] names;

    public void setColumns(Range... ranges) {
        this.ranges = ranges;
    }

    public void setNames(String... names) {
        this.names = names;
    }

    @Override
    public FieldSet tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        for (Range range : ranges) {
            int start = range.getMin() - 1;               // 1-based to 0-based
            int end = Math.min(line.length(), range.getMax());
            String raw = line.substring(start, end);      // <-- no trim
            tokens.add(raw);
        }
        return new DefaultFieldSet(tokens.toArray(new String[0]), names);
    }
}

