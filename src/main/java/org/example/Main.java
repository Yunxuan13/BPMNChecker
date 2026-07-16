package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.checker.BPMNChecker;
import org.example.model.*;
import org.example.parser.InputValidationException;
import org.example.parser.MermaidParser;
import org.example.reporter.JsonIssue;
import org.example.reporter.JsonNode;
import org.example.reporter.JsonReporter;

import java.io.File;
import java.io.FileWriter;
import java.util.List;


// export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

// cd /Users/xuan/Documents/thesis/BPMN-Structural-Error-checker
//./gradlew run --args="/Users/xuan/Documents/thesis/llm-generated-mermaid-models/gpt-4o /Users/xuan/Documents/thesis/llm-generated-mermaid-models/gpt-4o-output"
public class Main {

    private static final String[] DATASETS = {"domains", "mad150", "pet", "realset", "sapsam"};
    private static final int PROCESSING_FAILED = -1;
    private static final int INPUT_NOT_SUPPORTED = -2;

    public static void main(String[] args) {

        // design 2 method to use
        // 1. only give one txt file to check this process (single process)
        // 2. give a input path and output path --> run all txt files in input file
         if (args.length == 1) {
            runSingle(args[0]);
        } else if (args.length == 2) {
            runBatch(args[0], args[1]);
        } else {
            System.out.println("We need to receive a path to Mermaid-File.");
        }

    }

    private static JsonReporter check(String path) throws Exception {
        MermaidParser parser = new MermaidParser(path);
        BPMNChecker checker = new BPMNChecker(parser);
        checker.detectErrors();
        List<BPMNError> errorList = checker.getErrorList();

        return new JsonReporter(path, parser, errorList);
    }

    private static void runSingle(String path) {
        try {
            JsonReporter reporter = check(path);
            System.out.println(getJsonForm(reporter));
        } catch (InputValidationException e) {
            System.err.println("Input not supported by the structural checker for [" + e.getReason() + "]: " + e.getMessage());
            // =/= normal exception 1
            System.exit(2);
        }

        catch (Exception e) {
            System.err.println("Failed to deal with " + path + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runBatch(String input, String output) {
        File inputFolder = new File(input);
        File outputFolder = new File(output);

        if (!inputFolder.isDirectory()) {
            System.out.println("Can't find directory:" + input);
            return;
        }

        outputFolder.mkdirs();

        int totalFiles = 0;
        int unsupportedInputs = 0;
        int processingFailed = 0;
        int clean = 0;
        int issues = 0;

        try {
            File csvFile = new File(output, "result.csv");
            FileWriter csv = new FileWriter(csvFile);

            csv.write("dataset,file,errorId,severity,scope,nodes,message,human\n");

            for (String dataset : DATASETS) {
                File[] files = new File(inputFolder, dataset).listFiles();
                if (files == null) {
                    System.out.println("Dataset '" + dataset + "' not found!");
                    continue;
                }

                for (File file : files) {
                    // only for txt file (skip png files)
                    // dont parse trans_vx
                    if (!file.isFile() || !file.getName().endsWith(".txt")) {
                        continue;
                    }

                    totalFiles++;

                    int result = processFile(csv, outputFolder, dataset, file);

                    if (result == INPUT_NOT_SUPPORTED) {
                        unsupportedInputs++;
                    } else if (result == PROCESSING_FAILED) {
                        processingFailed++;
                    } else if (result == 0) {
                        clean++;
                    } else {
                        issues = issues + result;
                    }


                }
            }

            csv.close();


        } catch (Exception e) {
            System.out.println("Fail tot write output, " + e.getMessage());
            return;
        }

        System.out.println("Finished. Total Number: " + totalFiles + ", Unsupported files Number: " + unsupportedInputs + ", Processing failures: " + processingFailed + ", Clean Files: " + clean + ", Total issues: " + issues +  ".");
    }

    // number of detected issues or -1 for failed parsed
    // single file
    private static int processFile(FileWriter csv, File outputFolder, String dataset, File file) throws Exception {
        String name = file.getName();

        JsonReporter reporter;

        try {
            reporter = check(file.getPath());
        } catch (InputValidationException e) {
            csv.write(row(dataset, name, "INPUT_NOT_SUPPORTED", "", "", "", e.getReason().name(), "") + "\n");
            return INPUT_NOT_SUPPORTED;
        } catch (Exception e) {
            csv.write(row(dataset, name, "PROCESSING_FAILED", "","","",e.getMessage(),"") + "\n");
            return PROCESSING_FAILED;
        }

        File jsonFile = new File(new File(outputFolder, dataset), name.replace(".txt", ".json"));
        jsonFile.getParentFile().mkdirs();

        FileWriter writer = new FileWriter(jsonFile);
        writer.write(getJsonForm(reporter));
        writer.close();

        List<JsonIssue> issues = reporter.getIssues();
        if (issues.isEmpty()) {
            csv.write(row(dataset, name, "CLEAN", "", "", "", "", "") + "\n");
            return 0;
        }

        for (JsonIssue issue : issues) {
            csv.write(issueRow(dataset, name, issue) + "\n");
        }

        return issues.size();
    }

    // csv row for issues
    private static String issueRow(String dataset, String name, JsonIssue issue) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode node : issue.getErrorNodes()) {
            if (!builder.isEmpty()) {
                builder.append(";");
            }
            builder.append(node.getKey());
        }

        return row(dataset, name, issue.getErrorId(), issue.getSeverity(), issue.getScope(), builder.toString(), issue.getMessage(), "");
    }

    private static String row(String dataset, String file, String errorId, String severity, String scope, String nodes, String message, String human) {
        return convert(dataset) + "," + convert(file) + "," + convert(errorId) + "," + convert(severity) + "," + convert(scope) + "," + convert(nodes) + "," + convert(message) + "," + convert(human);
    }

    private static String convert(String text) {
        // add "" to each
        if (text == null) {
            text = "";
        }
        String a = "\"";
        String b = "\"\"";

        return a + text.replace(a, b) + a;
    }

    private static String getJsonForm(JsonReporter reporter) {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
        return gson.toJson(reporter);
    }
}