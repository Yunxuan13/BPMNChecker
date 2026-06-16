package Checker;

import org.example.checker.BPMNChecker;
import org.example.model.BPMNError;
import org.example.parser.MermaidParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiErrorTest {
    private static final String Path = "/Users/xuan/documents/thesis/llm-generated-mermaid-models/gpt-4o/domains/";

    private BPMNChecker check(String path) throws Exception {
        MermaidParser parser = new MermaidParser(path);
        BPMNChecker bpmnChecker = new BPMNChecker(parser);
        bpmnChecker.detectErrors();
        return bpmnChecker;
    }

    private boolean hasError(BPMNChecker checker, String errorId) {
        for (BPMNError error : checker.getErrorList()) {
            if (error.getErrorId().equals(errorId)) {
                return true;
            }
        }
        return false;
    }

    // test correct bpmn wont be reported
    @Test
    void testManufacturingBpmai2NoError() throws Exception {
        BPMNChecker checker = check(Path + "manufacturing_bpmai_2.txt");
        List<BPMNError> errors = checker.getErrorList();
        assertTrue(errors.isEmpty());
    }

    // TODO
}
