package tabby.util;

/**
 * @author wh1t3P1g
 * @since 2020/12/30
 */
public enum Status {
    NOT_POLLUTED(-2),THIS(-1),PARAM(0);

    int index;

    Status(int index) {
        this.index = index;
    }
}
