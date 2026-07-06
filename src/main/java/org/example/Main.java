package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.checker.BPMNChecker;
import org.example.model.*;
import org.example.parser.MermaidParser;
import org.example.reporter.JsonReporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.ECField;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;


// export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

// cd /Users/xuan/Documents/thesis/BPMN-Structural-Error-checker
//./gradlew run --args="/Users/xuan/Documents/thesis/llm-generated-mermaid-models/gpt-4o /Users/xuan/Documents/thesis/llm-generated-mermaid-models/gpt-4o-output"
public class Main {
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


//        String path = args[0];
//
//        MermaidParser parser;
//        try {
//            parser = new MermaidParser(path);
//
//        } catch (Exception e) {
//            System.err.println("Failed to parse file: " + e.getMessage());
//            System.exit(1);
//            return;
//        }



//        JsonReport report = generateReport(path, parser, errorList);

        // we need Json format
//        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
//        System.out.println(gson.toJson(report));
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
        } catch (Exception e) {
            System.out.println("Failed to deal with " + path + ".");
        }
    }

    private static void runBatch(String input, String output) {
        File inputFolder = new File(input);

        if (!inputFolder.isDirectory()) {
            System.out.println("Can't find directory:" + input);
            return;
        }

        List<File> mermaidFiles = new ArrayList<>();
        extractTxtFiles(inputFolder, mermaidFiles);

        int successNum = 0;
        int failedNum = 0;

        for (File file : mermaidFiles) {
            try {
                JsonReporter reporter = check(file.getPath());

                FileWriter writer = getFileWriter(input, output, file);
                writer.write(getJsonForm(reporter));
                writer.close();

                successNum++;

            } catch (Exception e) {
                System.out.println("Failed with: " + file.getName() + ".");
                failedNum++;
            }
        }

        System.out.println("Finished. Successful Number: " + successNum + ", Failed Number: " + failedNum + ".");
    }

    // // under same mermaid process name
    //                // input was like: llmxxxx/gpt-4o/
    //                // output was like: llmxxx/gpt-4o/output/ or somewhere else
    private static FileWriter getFileWriter(String input, String output, File file) throws IOException {
        String name = file.getPath().substring(input.length());
        String outputPath = output + name;
        outputPath = outputPath.replace(".txt", ".json");

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        return new FileWriter(outputFile);
    }

    private static void extractTxtFiles(File folder, List<File> extracted) {
        File[] files = folder.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                extractTxtFiles(file, extracted);
            } else if (file.getName().endsWith(".txt")) {
                extracted.add(file);
            }
        }
    }

    private static String getJsonForm(JsonReporter reporter) {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
        return gson.toJson(reporter);
    }












//    private static JsonReport generateReport(String path, MermaidParser parser, List<BPMNError> errorList) {
//        JsonReport jsonReport = new JsonReport();
//
//        jsonReport.meta.file = path;
//        jsonReport.meta.timestamp = ZonedDateTime.now().toString();
//        jsonReport.meta.nodeCount = parser.getNodes().size();
//        jsonReport.meta.edgeCount = parser.getEdges().size();
//
//
//        for (BPMNError error : errorList) {
//            if (error.getSeverity() == Severity.ERROR) {
//                jsonReport.meta.errorCount++;
//            } else if (error.getSeverity() == Severity.WARNING){
//                jsonReport.meta.warningCount++;
//            } else {
//                // for new LBL02, if not X/O/AND
//                // according to prompt
//                jsonReport.meta.infoCount++;
//            }
//
//            JsonIssue issue = new JsonIssue();
//            issue.errorId = error.getErrorId();
//            issue.errorName = error.getErrorName();
//            issue.category = error.getErrorCategory();
//            issue.severity = error.getSeverity().name();
//            issue.scope = convertScope(error.getScope());
//            // TODO we havent add any messages for each error
//            issue.message = error.getMessage();
//            // TODO add attribute suggestion
//            issue.suggestion = null;
//
//            for (Node node : error.getNode()) {
//                JsonNode n = new JsonNode();
//                n.key = node.getKey();
//                n.label = node.getLabel();
//                n.type = node.getType().name().toLowerCase();
//                n.location = node.getLocation();
//                for (Role role : node.getRoles()) {
//                    n.roles.add(role.name().toLowerCase());
//                }
//                issue.errorNodes.add(n);
//            }
//
//            for (Edge edge : error.getEdges()) {
//                JsonEdge e = new JsonEdge();
//                e.sourceKey = edge.getSourceKey();
//                e.targetKey = edge.getTargetKey();
//                e.condition = edge.getCondition();
//                issue.errorEdges.add(e);
//            }
//
//            jsonReport.issues.add(issue);
//        }
//
//        jsonReport.meta.totalIssues = jsonReport.issues.size();
//        return jsonReport;
//    }



//    private static class JsonReport {
//        // remove to meta info part
////        int errorCount = 0;
////        int warningCount = 0;
////        int totalIssues = 0;
//        JsonMeta meta = new JsonMeta();
//
//        List<JsonIssue> issues = new ArrayList<>();
//    }

//    private static class JsonMeta {
//        String file;
//        String timestamp;
//
//        int nodeCount;
//        int edgeCount;
//
//        int errorCount = 0;
//        int warningCount = 0;
//        int infoCount = 0;
//
//        int totalIssues = 0;
//    }

//    private static class JsonIssue {
//        String errorId;
//        String errorName;
//        String category;
//        String severity;
//        String scope;
//        String message;
//        // while repairing, extract suggestion as part of new prompt back to llm
//        String suggestion;
//        List<JsonNode> errorNodes = new ArrayList<>();
//        List<JsonEdge> errorEdges = new ArrayList<>();
//    }
//
//    private static class JsonNode {
//        String key;
//        String label;
//        String type;
//        String location;
//        List<String> roles = new ArrayList<>();
//    }
//
//    private static class JsonEdge {
//        String sourceKey;
//        String targetKey;
//        String condition;
//    }
}