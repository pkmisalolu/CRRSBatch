package com.abcbs.crrs.jobs.P09305;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CorpNameLoader {
 private final Map<String,String> byNo = new HashMap<>();

 public CorpNameLoader(String corpFile) {
     try (var in = new FileSystemResource(corpFile).getInputStream();
          var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
         String line;
         while ((line = r.readLine()) != null) {
             line = line.trim();
             if (line.isEmpty() || line.startsWith("#")) continue;
             // format: 01,ARKANSAS BLUE CROSS AND BLUE SHIELD
             String[] parts = line.split(",", 2);
             if (parts.length == 2) byNo.put(parts[0].trim(), parts[1].trim());
         }
     } catch (Exception e) {
         throw new IllegalStateException("Failed to load CORP-FILE", e);
     }
 }

 public String corpName(String corpNo) {
     return byNo.getOrDefault(corpNo, "ARKANSAS BLUE CROSS AND BLUE SHIELD");
 }
}

