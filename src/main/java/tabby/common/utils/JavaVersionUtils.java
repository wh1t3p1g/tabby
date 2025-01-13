package tabby.common.utils;

/**
 * @author mbechler
 */
public class JavaVersionUtils {

    public int major;
    public int minor;
    public int update;

    public static JavaVersionUtils getLocalVersion() {
        String property = System.getProperties().getProperty("java.version");
        if (property == null) {
            return null;
        }
        JavaVersionUtils v = new JavaVersionUtils();
        String parts[] = property.split("\\.|_|-");
        int start = "1".equals(parts[0]) ? 1 : 0; // skip "1." prefix
        v.major = Integer.parseInt(parts[start + 0]);
        v.minor = Integer.parseInt(parts[start + 1]);
        v.update = Integer.parseInt(parts[start + 2]);
        return v;
    }

    public static boolean isAnnInvHUniversalMethodImpl() {
        JavaVersionUtils v = JavaVersionUtils.getLocalVersion();
        return v != null && (v.major < 8 || (v.major == 8 && v.update <= 71));
    }

    public static boolean isBadAttrValExcReadObj() {
        JavaVersionUtils v = JavaVersionUtils.getLocalVersion();
        return v != null && (v.major > 8 && v.update >= 76);
    }

    public static boolean isAtLeast(int major) {
        JavaVersionUtils v = JavaVersionUtils.getLocalVersion();
        return v != null && v.major >= major;
    }

    public static boolean isJDK8() {
        JavaVersionUtils v = JavaVersionUtils.getLocalVersion();
        return v != null && (v.major == 8);
    }

    public static boolean isWin() {
        String os = System.getProperty("os.name");
        return os.toLowerCase().startsWith("win");
    }

}
