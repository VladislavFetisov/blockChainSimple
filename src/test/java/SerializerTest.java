import main.model.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializerTest {

    @Test
    public void serializerTest() {
        Block block = new Block(1, "1", 1);
        Block block1 = new Block(2, "2", 2);
        Block block2 = new Block(3, "3", 3);
        List<Block> blocks = List.of(block, block1, block2);
        byte[] serialize = Block.serializeList(blocks);
        List<Block> res = Block.deserializeList(serialize);
        equalsIterable(blocks, res);
    }

    @Test
    public void serializerSingleTest() {
        Block block = new Block(1, "1", 1);
        byte[] serialize = block.serialize();
        Block res = Block.fromBytes(serialize);
        assertEquals(block, res);
    }
    private static void equalsIterable(List<Block> it1, List<Block> it2) {
        assertEquals(it1.size(), it2.size());
        for (int i = 0; i < it1.size(); i++) {
            assertEquals(it1.get(i), it2.get(i));
        }
    }
}
