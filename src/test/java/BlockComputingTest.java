import main.model.Block;
import main.processor.BlockComputingTask;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockComputingTest {
    @Test
    public void computeBlock() throws ExecutionException, InterruptedException {
        Block startBlock = Block.START_BLOCK;
        Block block = CompletableFuture.supplyAsync(new BlockComputingTask(startBlock)).get();
        assertEquals(startBlock.hash(), block.prevHash());
        assertTrue(block.hash().endsWith("0000"));
        assertEquals(startBlock.index() + 1, block.index());
    }
}
