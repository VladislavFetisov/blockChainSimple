package main;

import java.util.Random;

public class StringUtils {
    private static final String digits = "0123456789";

    private StringUtils() {

    }

    public static String generateRandomString(int length) {
        char[] res = new char[length];
        Random random = new Random();
        for (int i = 0; i < res.length; i++) {
            res[i] = digits.charAt(random.nextInt(digits.length()));
        }
        return String.valueOf(res);
    }
}
