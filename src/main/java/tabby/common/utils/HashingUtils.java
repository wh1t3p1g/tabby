package tabby.common.utils;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

public class HashingUtils {

    public static String hashString(String name) {
        return Hashing.md5()
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
    }
}
