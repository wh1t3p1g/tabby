package tabby.common.utils;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * @author wh1t3p1g
 * @project tabby
 * @since 2024/1/10
 */
public class HashingUtils {

    public static String hashString(String name){
        return Hashing.md5()
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
    }
}
