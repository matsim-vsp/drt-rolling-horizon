package org.matsim.project.drtOperationStudy.run.caseStudy;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CaseStudy {
    static String prepare(Path configPath, String output) throws IOException {
        // Create a temporary config file in the same folder, so that multiple runs can be run in the cluster at the same time
        int taskId = (int) (System.currentTimeMillis() / 1000);
        File originalConfig = new File(configPath.toString());
        String temporaryConfig = configPath.getParent().toString() + "/temporary_" + taskId + ".config.xml";
        File copy = new File(temporaryConfig);
        try {
            FileUtils.copyFile(originalConfig, copy);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!Files.exists(Path.of(output))) {
            Files.createDirectory(Path.of(output));
        }

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(output + "/result-summary.tsv", true), CSVFormat.TDF);
        tsvWriter.printRecord("Fleet_size", "Rejections", "Total_driving_time");
        tsvWriter.close();
        return temporaryConfig;
    }
}
