package main;

import main.processor.BlockProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Cluster {
    private static final Logger logger = LoggerFactory.getLogger(Cluster.class);

    private Cluster() {
        // Not instantiable
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> params = parseParams(args);
        int port = Integer.parseInt(params.get("port"));
        boolean isGenesis = "true".equals(params.get("isGenesis"));

        String otherNodesArg = params.get("otherNodes");
        List<String> otherNodes = Arrays.stream(otherNodesArg.split(" ")).toList();
        String currentNode = params.get("currentNode");
        BlockProcessor blockProcessor = new BlockProcessor(port, isGenesis, currentNode, otherNodes);
        blockProcessor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(blockProcessor::stop));
    }

    private static Map<String, String> parseParams(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            params.put(args[i].substring(1), args[i + 1]);
        }
        return params;
    }
}
