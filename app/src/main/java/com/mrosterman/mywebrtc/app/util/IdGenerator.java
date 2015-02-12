package com.mrosterman.mywebrtc.app.util;
import java.util.Random;

/**
 * Created by costerman on 1/30/15.
 */
public class IdGenerator
{
    public static String generateId() {
        StringBuilder result = new StringBuilder(id(9));

        format(result);

        return result.toString();
    }

    private static synchronized String id(int len) {
        int range = Integer.valueOf("1"+repeat("0", len));

        String id = String.valueOf(new Random(System.currentTimeMillis()).nextInt(range));

        int offset = len - id.length();

        if (offset != 0) id = String.format("%s%s", repeat("0", offset), id);

        return id;
    }

    private static StringBuilder format(StringBuilder sb) {
        sb.insert(6, "-");
        sb.insert(3, "-");
        return sb;
    }

    private static String repeat(String string, int repeat) {
        return new String(new char[repeat]).replace("\0", string);
    }
}
