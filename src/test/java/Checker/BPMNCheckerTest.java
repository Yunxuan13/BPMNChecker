package Checker;

import org.example.checker.BPMNChecker;
import org.example.model.BPMNError;
import org.example.parser.MermaidParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BPMNCheckerTest {
    private static final String PATH = "User/xuan/Documents/thesis/categories/";

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

    // GTW 1-3

    // XOR 1-3

    // AND 1-3

    // OR 1-3

    // SUB 1-2

    // LBL 1

    // EDGE 1

    // LOOP 1-2
}
