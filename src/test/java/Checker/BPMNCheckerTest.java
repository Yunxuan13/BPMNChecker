package Checker;

import org.example.checker.BPMNChecker;
import org.example.model.BPMNError;
import org.example.parser.MermaidParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BPMNCheckerTest {
    private static final String PATH = "/Users/xuan/Documents/thesis/categories/";

    private BPMNChecker check(String folder, String file) throws Exception {
        MermaidParser parser = new MermaidParser(PATH + folder + "/" + file);
        BPMNChecker checker = new BPMNChecker(parser);

        checker.detectErrors();
        return checker;
    }

    private boolean hasError(BPMNChecker checker, String errorId) {
        for (BPMNError error : checker.getErrorList()) {
            if (error.getErrorId().equals(errorId)) {
                return true;
            }
        }
        return false;
    }

    // CON 1-5
    @Test
    void testCON01() throws Exception {
        BPMNChecker checker = check("1. Connectivity & Reachability [CON]", "CON-01: Isolated Node");
        assertTrue(hasError(checker, "CON-01"));
    }

    @Test
    void testCON02() throws Exception {
        BPMNChecker checker = check("1. Connectivity & Reachability [CON]", "CON-02: Missing Incoming Sequence Flow");
        assertTrue(hasError(checker, "CON-02"));
    }

    @Test
    void testCON03() throws Exception {
        BPMNChecker checker = check("1. Connectivity & Reachability [CON]", "CON-03: Missing Outgoing Sequence Flow");
        assertTrue(hasError(checker, "CON-03"));
    }

    @Test
    void testCON04() throws Exception {
        BPMNChecker checker = check("1. Connectivity & Reachability [CON]", "CON-04: Unreachable Activity");
        assertTrue(hasError(checker, "CON-04"));
    }

    @Test
    void testCON05() throws Exception {
        BPMNChecker checker = check("1. Connectivity & Reachability [CON]", "CON-05: End Event Unreachable from Start");
        assertTrue(hasError(checker, "CON-05"));
    }

    // SE 1-6
    @Test
    void testSE01() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-01: Missing Start Event");
        assertTrue(hasError(checker, "SE-01"));
    }

    @Test
    void testSE02() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-02: Missing End Event");
        assertTrue(hasError(checker, "SE-02"));
    }

    @Test
    void testSE03() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-03: Multiple Start Events");
        assertTrue(hasError(checker, "SE-03"));
    }

    @Test
    void testSE04() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-04: Multiple End Events");
        assertTrue(hasError(checker, "SE-04"));
    }

    @Test
    void testSE05() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-05: Start Event with Incoming Sequence Flow");
        assertTrue(hasError(checker, "SE-05"));
    }

    @Test
    void testSE06() throws Exception {
        BPMNChecker checker = check("2. Start & End Event Errors [SE]", "SE-06: End Event with Outgoing Sequence Flow");
        assertTrue(hasError(checker, "SE-06"));
    }


    // GTW 1-3
    @Test
    void testGTW01() throws Exception {
        BPMNChecker checker = check("3. General Gateway Errors [GTW]", "GTW-01: Implicit Split");
        assertTrue(hasError(checker, "GTW-01"));
    }

    @Test
    void testGTW02() throws Exception {
        BPMNChecker checker = check("3. General Gateway Errors [GTW]", "GTW-02: Implicit Join");
        assertTrue(hasError(checker, "GTW-02"));
    }

    @Test
    void testGTW03() throws Exception {
        BPMNChecker checker = check("3. General Gateway Errors [GTW]", "GTW-03: Mismatched Gateway Types");
        assertTrue(hasError(checker, "GTW-03"));
    }

    @Test
    void testGTW04() throws Exception {
        BPMNChecker checker = check("3. General Gateway Errors [GTW]", "GTW-04: Gateway Nesting Violation");
        assertTrue(hasError(checker, "GTW-04"));
    }

    // XOR 1-3
    @Test
    void testXOR01() throws Exception {
        BPMNChecker checker = check("4. XOR Gateway Errors [XOR]", "XOR-01: XOR Gateway Used as Both Split and Join");
        assertTrue(hasError(checker, "XOR-01"));
    }

    @Test
    void testXOR02() throws Exception {
        BPMNChecker checker = check("4. XOR Gateway Errors [XOR]", "XOR-02: Missing Condition on XOR Outgoing Flow");
        assertTrue(hasError(checker, "XOR-02"));
    }

    @Test
    void testXOR03() throws Exception {
        BPMNChecker checker = check("4. XOR Gateway Errors [XOR]", "XOR-03: Redundant XOR Gateway");
        assertTrue(hasError(checker, "XOR-03"));
    }

    // AND 1-3
    @Test
    void testAND01() throws Exception {
        BPMNChecker checker = check("5. AND Gateway Errors [AND]", "AND-01: AND Gateway Used as Both Split and Join");
        assertTrue(hasError(checker, "AND-01"));
    }

    @Test
    void testAND02() throws Exception {
        BPMNChecker checker = check("5. AND Gateway Errors [AND]", "AND-02: Redundant AND Gateway");
        assertTrue(hasError(checker, "AND-02"));
    }

    @Test
    void testAND03() throws Exception {
        BPMNChecker checker = check("5. AND Gateway Errors [AND]", "AND-03: AND Split and Join Branch Count Mismatch");
        assertTrue(hasError(checker, "AND-03"));
    }

    // OR 1-3
    @Test
    void testOR01() throws Exception {
        BPMNChecker checker = check("6. OR Gateway Errors [OR]", "OR-01: OR Gateway Used as Both Split and Join");
        assertTrue(hasError(checker, "OR-01"));
    }

    @Test
    void testOR02() throws Exception {
        BPMNChecker checker = check("6. OR Gateway Errors [OR]", "OR-02: Missing Condition on OR Outgoing Flow");
        assertTrue(hasError(checker, "OR-02"));
    }

    @Test
    void testOR03() throws Exception {
        BPMNChecker checker = check("6. OR Gateway Errors [OR]", "OR-03: Redundant OR Gateway");
        assertTrue(hasError(checker, "OR-03"));
    }


    // SUB 1-2
    @Test
    void testSUB01() throws Exception {
        BPMNChecker checker = check("7. Subprocess Structural Errors [SUB]", "SUB-01: Empty Subprocess");
        assertTrue(hasError(checker, "SUB-01"));
    }

    @Test
    void testSUB02() throws Exception {
        BPMNChecker checker = check("7. Subprocess Structural Errors [SUB]", "SUB-02: Subprocess Boundary Violation");
        assertTrue(hasError(checker, "SUB-02"));
    }

    // LBL 1
    @Test
    void testLBL01() throws Exception {
        BPMNChecker checker = check("8. Label & Naming Errors [LBL]", "LBL-01: Duplicate Activity Name");
        assertTrue(hasError(checker, "LBL-01"));
    }

    // EDGE 1
    @Test
    void testEDGE01() throws Exception {
        BPMNChecker checker = check("9. Edge Structural Errors [EDGE]", "EDGE-01: Duplicate Sequence Flow");
        assertTrue(hasError(checker, "EDGE-01"));
    }

    // LOOP 1-2
    // TODO
}
