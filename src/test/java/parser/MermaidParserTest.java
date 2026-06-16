package parser;

import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.parser.MermaidParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MermaidParserTest {

    // TODO vorsicht! under this path, it is NOT full mermaid code for all situation
    private static final String PATH = "/Users/xuan/documents/thesis/Script_drafts/";


    @Test
    void testParseMinimalAllElements() throws Exception {
        MermaidParser parser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        LinkedHashMap<String, Node> nodes = parser.getNodes();
        List<Edge> edgeList = parser.getEdges();

        // temporarily as simple version
        assertEquals(18, nodes.size());
        assertFalse(edgeList.isEmpty());

        // TODO implement real test case
    }

    @Test
    void testNodeTypes() throws Exception {
        MermaidParser mermaidParser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        LinkedHashMap<String, Node> nodes = mermaidParser.getNodes();

        assertEquals(NodeType.STARTEVENT, nodes.get("0:startevent").getType());
        assertEquals(NodeType.ENDEVENT, nodes.get("14:endevent").getType());
        assertEquals(NodeType.TASK, nodes.get("1:task").getType());
        assertEquals(NodeType.PARALLELGATEWAY, nodes.get("2:parallelgateway").getType());
        assertEquals(NodeType.EXCLUSIVEGATEWAY, nodes.get("10:exclusivegateway").getType());
        assertEquals(NodeType.INCLUSIVEGATEWAY, nodes.get("6:inclusivegateway").getType());
        assertEquals(NodeType.SUBPROCESS, nodes.get("SP:subprocess").getType());
    }

    @Test
    void testNodeKeys() throws Exception {
        MermaidParser mermaidParser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        LinkedHashMap<String, Node> nodes = mermaidParser.getNodes();

        assertTrue(nodes.containsKey("0:startevent"));
        assertTrue(nodes.containsKey("1:task"));
        assertTrue(nodes.containsKey("2:parallelgateway"));
        assertTrue(nodes.containsKey("14:endevent"));
        assertTrue(nodes.containsKey("SP:subprocess"));
    }

    @Test
    void testEdgeWithConditionSingleCase() throws Exception {
        MermaidParser mermaidParser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        List<Edge> edges = mermaidParser.getEdges();

        boolean found = false;

        for (Edge edge : edges) {
            if (edge.getSourceKey().equals("6:inclusivegateway") && edge.getTargetKey().equals("7:task")
                    && "o1".equals(edge.getCondition()))  {
                found  = true;
                break;
            }
        }

        assertTrue(found);
    }

    @Test
    void testEdgeWithoutCondition() throws Exception {
        MermaidParser parser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        List<Edge> edges = parser.getEdges();

        boolean found = false;
        for (Edge edge : edges) {
            if (edge.getSourceKey().equals("0:startevent") && edge.getTargetKey().equals("1:task")) {
                assertNull(edge.getCondition());
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSubprocessLocation() throws Exception {
        MermaidParser mermaidParser = new MermaidParser(PATH + "minimal_bpmn_all_elements.txt");
        LinkedHashMap<String, Node> nodes = mermaidParser.getNodes();

        assertEquals("SP", nodes.get("15:startevent").getLocation());
        assertEquals("SP", nodes.get("16:task").getLocation());
        assertEquals("SP", nodes.get("17:endevent").getLocation());
        assertNull(nodes.get("0:startevent").getLocation());
        assertNull(nodes.get("1:task").getLocation());
    }
}
