package main.model;

import main.StringUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Block implements Serializable {
    public static final Block START_BLOCK = new Block(0, "", 0);

    static {
        START_BLOCK.setHash("");
    }
    private final int index;
    private final String prevHash;
    private final String data;
    private String hash = "";
    private int nonce;

    public Block(int index, String prevHash, int nonce) {
        this.index = index;
        this.prevHash = prevHash;
        this.nonce = nonce;
        this.data = StringUtils.generateRandomString(256);
    }

    private Block(int index, String prevHash, String data, String hash, int nonce) {
        this.index = index;
        this.prevHash = prevHash;
        this.data = data;
        this.hash = hash;
        this.nonce = nonce;
    }

    public int index() {
        return index;
    }

    public String prevHash() {
        return prevHash;
    }

    public String data() {
        return data;
    }

    public int nonce() {
        return nonce;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String hash() {
        return hash;
    }
    public void setNonce(int nonce) {
        this.nonce = nonce;
    }


    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", prevHash='" + prevHash + '\'' +
                ", hash='" + hash + '\'' +
                ", nonce=" + nonce +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return index == block.index &&
                nonce == block.nonce &&
                Objects.equals(prevHash, block.prevHash) &&
                Objects.equals(data, block.data) &&
                Objects.equals(hash, block.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, prevHash, data, hash, nonce);
    }

    public byte[] serialize() {
        String res = toSerializedString();
        return res.getBytes(StandardCharsets.UTF_8);
    }

    private String toSerializedString() {
        return "%d %s %s %s %d".formatted(index, prevHash, data, hash, nonce);
    }

    public static Block fromBytes(byte[] input) {
        String[] fields = new String(input, StandardCharsets.UTF_8).split(" ");
        return fromStringArray(fields);
    }

    private static Block fromStringArray(String[] fields) {
        int index = Integer.parseInt(fields[0]);
        int nonce = Integer.parseInt(fields[4]);
        return new Block(index, fields[1], fields[2], fields[3], nonce);
    }

    public static byte[] serializeList(List<Block> input) {
        return input
                .stream()
                .map(Block::toSerializedString)
                .collect(Collectors.joining(";"))
                .getBytes(StandardCharsets.UTF_8);
    }

    public static List<Block> deserializeList(byte[] input) {
        List<Block> res = new ArrayList<>();
        String[] stringBlocks = new String(input, StandardCharsets.UTF_8).split(";");
        for (String stringBlock : stringBlocks) {
            String[] fieldsArray = stringBlock.split(" ");
            res.add(fromStringArray(fieldsArray));
        }
        return res;
    }

}