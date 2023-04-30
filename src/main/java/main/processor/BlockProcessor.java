package main.processor;

import main.model.Block;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class BlockProcessor extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(BlockProcessor.class);
    private static final Duration TIMEOUT = Duration.ofMillis(2000);
    private static final String HTTP = "http://";
    private static final String BLOCK = "/block";
    private static final String BLOCKCHAIN = "/blockChain";

    private static final ExecutorService blockComputingExecutor
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "BlockComputingExecutor"));
    private final AtomicReference<CompletableFuture<Block>> currentBlockFuture = new AtomicReference<>();

    private volatile List<Block> blockChain;
    private final Semaphore blockReceivingSemaphore = new Semaphore(1);
    private final boolean isGenesis;
    private final String currentNode;
    private final List<String> otherNodes;
    private final java.net.http.HttpClient client;

    private final Object mutex = new Object();

    public BlockProcessor(int port,
                          boolean isGenesis,
                          String currentNode,
                          List<String> endpoints) throws IOException {
        super(from(port));
        this.isGenesis = isGenesis;
        this.currentNode = currentNode;
        this.otherNodes = endpoints;

        client = java.net.http.HttpClient
                .newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .executor(new ForkJoinPool())
                .build();
        ArrayList<Block> blocks = new ArrayList<>();
        blocks.add(Block.START_BLOCK);
        blockChain = blocks;
    }

    @Override
    public synchronized void start() {
        logger.info("Block processor start on port [{}]", port);
        super.start();
        if (!isGenesis) {
            return;
        }
        logger.info("Start generate genesis");
        stateIsComputingNextBlock(Block.START_BLOCK);
        logger.info("Genesis is generated");
    }

    @Override
    public synchronized void stop() {
        super.stop();
        blockComputingExecutor.shutdown();
    }

    @Path(BLOCKCHAIN)
    @RequestMethod(Request.METHOD_GET)
    public Response requestChain(Request request,
                                 HttpSession session,
                                 @Param(value = "idx", required = true) int index) {
        List<Block> requiredBlocks;
        synchronized (mutex) {
            requiredBlocks = blockChain.subList(index, blockChain.size());
        }
        byte[] serializedBlocks = Block.serializeList(requiredBlocks);
        return Response.ok(serializedBlocks);
    }

    @Path(BLOCK)
    @RequestMethod(Request.METHOD_POST)
    public void receiveBlock(Request request,
                             HttpSession session,
                             @Param(value = "node", required = true) String sender) {
        Block block = Block.fromBytes(request.getBody());
        logger.info("Receive block=[{}]", block);
        boolean receive = blockReceivingSemaphore.tryAcquire();
        if (!receive) {
            logger.info("Block is rejected");
            return;
        }
        try {
            receiveBlock(block, sender);
        } finally {
            blockReceivingSemaphore.release(1);
        }
    }

    private void receiveBlock(Block block, String remoteHost) {
        Block currentBlock;
        synchronized (mutex) {
            currentBlock = blockChain.get(blockChain.size() - 1);
            Objects.requireNonNull(currentBlock, "Current block is null");
            if (block.index() <= currentBlock.index()) {
                logger.info("Received block is old");
                return;
            }
            stopCountingBlock();
            if (block.index() == currentBlock.index() + 1 &&
                    block.prevHash().equals(currentBlock.hash())) { // чтобы не запрашивать цепь
                blockChain.add(block);
                stateIsComputingNextBlock(block);
                return;
            }
            try {
                logger.info("Request blocks from [{}]", remoteHost);
                List<Block> listOfBLocks = requestChain(remoteHost, 0);
                Block receivedGenesis = listOfBLocks.get(1);
                Block ourGenesis = blockChain.get(1);
                if (!receivedGenesis.hash().equals(ourGenesis.hash())) {
                    logger.warn("Received chain genesis=[{}] is not equals with our genesis=[{}]", receivedGenesis, ourGenesis);
                    return;
                }
                blockChain = listOfBLocks;
                logger.info("New chain from=[{}] is applied", remoteHost);
                stateIsComputingNextBlock(listOfBLocks.get(listOfBLocks.size() - 1));
            } catch (IOException | InterruptedException | ClassNotFoundException e) {
                logger.error("Cant request chain from node=[{}]", remoteHost, e);
            }
        }
    }

    private List<Block> requestChain(String remoteHost, int idx)
            throws IOException, InterruptedException, ClassNotFoundException {
        HttpRequest request = createRequestChain(remoteHost, idx);
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return Block.deserializeList(response.body());
    }

    private static HttpRequest createRequestChain(String remoteHost, int idx) {
        String uri = HTTP + remoteHost + BLOCKCHAIN + "?idx=" + idx;
        return HttpRequest
                .newBuilder()
                .uri(URI.create(uri))
                .timeout(TIMEOUT)
                .GET()
                .build();
    }

    private void sendBlockToOtherNodes(Block block) {
        for (String otherNode : otherNodes) {
            HttpRequest request;
            try {
                request = createSendBlockRequest(block, HTTP + otherNode + BLOCK + "?node=" + currentNode);
            } catch (IOException e) {
                logger.error("Failed to create request", e);
                return;
            }
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        }

    }

    private static HttpRequest createSendBlockRequest(Block block, String uri) throws IOException {
        byte[] serializedBlock = block.serialize();
        return HttpRequest
                .newBuilder()
                .uri(URI.create(uri))
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(serializedBlock))
                .build();
    }

    private CompletableFuture<Block> countNextBlock(Block block) {
        CompletableFuture<Block> future =
                CompletableFuture.supplyAsync(new BlockComputingTask(block), blockComputingExecutor);
        currentBlockFuture.set(future);
        return future;
    }

    private void stopCountingBlock() {
        CompletableFuture<Block> blockCompletableFuture = currentBlockFuture.get();
        if (blockCompletableFuture == null) {
            return;
        }
        blockCompletableFuture.cancel(true);
    }

    private void stateIsComputingNextBlock(Block block) {
        CompletableFuture<Block> nextBlockFuture = countNextBlock(block);
        nextBlockFuture.thenAccept(nextBlock -> {
            logger.info("Generate new block [{}], send it to other nodes", nextBlock);
            synchronized (mutex) {
                blockChain.add(nextBlock);
            }
            sendBlockToOtherNodes(nextBlock);
            stateIsComputingNextBlock(nextBlock);
        });
        currentBlockFuture.set(nextBlockFuture);
    }

    private static HttpServerConfig from(int port) {
        HttpServerConfig config = new HttpServerConfig();
        AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;
        ac.reusePort = true;
        config.acceptors = new AcceptorConfig[]{ac};
        config.selectors = 4;
        return config;
    }

}
