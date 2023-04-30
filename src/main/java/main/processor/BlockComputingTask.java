package main.processor;

import com.google.common.hash.Hashing;
import main.model.Block;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class BlockComputingTask implements Supplier<Block> {

    private final Block block;

    public BlockComputingTask(Block prevBlock) {
        this.block = new Block(prevBlock.index() + 1, prevBlock.hash(), 0);

    }

    @Override
    public Block get() {
        int nonce = block.nonce();
        StringBuilder input = new StringBuilder()
                .append(block.index())
                .append(block.prevHash())
                .append(block.data());
        while (true) {
            String stringNonce = String.valueOf(nonce);
            input.append(stringNonce);
            String hash = Hashing.sha256()
                    .hashString(input.toString(), StandardCharsets.UTF_8)
                    .toString();
            if (hash.endsWith("0000")) {
                block.setHash(hash);
                block.setNonce(nonce);
                return block;
            }
            input.delete(input.length() - stringNonce.length(), input.length());
            nonce++;
        }
    }
}
