package tabby.analysis.data;

/**
 * @author wh1t3p1g
 * @since 2021/11/26
 */
public enum Domain {

    /**
     * 污染状态
     * 集合里只要有污染的，就是污染的
     */
    POLLUTED(1),
    /**
     * 非对象本身污染，而是类属性污染
     */
    PART_POLLUTED(2),
    /**
     * top
     * 不确定状态，包括有可能是污染的，也有可能是非污染的
     * 目前unknown也识别为污染状态
     */
    UNKNOWN(3),
    /**
     * 非污染状态
     */
    NOT_POLLUTED(0),
    /**
     * bottom 暂为定义
     */
    UNDEFINED(-1);

    private int index;

    Domain(int index) {
        this.index = index;
    }

    public String toString(int m) {
        if (m == 1) {
            return "POLLUTED";
        } else if (m == 0) {
            return "NOT_POLLUTED";
        } else if (m == 2) {
            return "PART_POLLUTED";
        } else if (m == 3) {
            return "UNKNOWN";
        } else {
            return "UNDEFINED";
        }
    }

    public boolean isPolluted() {
        return index > 0;
    }

    public boolean isRealPolluted() {
        return index == 1;
    }

    public boolean isPartPolluted() {
        return index == 2;
    }

    public boolean isUnknown() {
        return index == 3;
    }

    public boolean isNotPolluted() {
        return index <= 0;
    }

    public static Domain merge(Domain o1, Domain o2) {
        if (o1 == null && o2 == null) {
            return Domain.NOT_POLLUTED;
        } else if (o1 == null) {
            return o2.copy();
        } else if (o2 == null) {
            return o1.copy();
        }

        if (o1.isRealPolluted()) {
            return Domain.POLLUTED;
        }

        if (o2.isRealPolluted()) {
            return Domain.POLLUTED;
        }

        if (o1.isUnknown() || o2.isUnknown()) {
            return Domain.UNKNOWN;
        }

        if (o1.isPolluted() || o2.isPolluted()) {
            return Domain.PART_POLLUTED;
        }

        return Domain.NOT_POLLUTED;
    }

    public Domain copy() {
        if (index == 1) {
            return Domain.POLLUTED;
        } else if (index == 0) {
            return Domain.NOT_POLLUTED;
        } else if (index == 2) {
            return Domain.PART_POLLUTED;
        } else if (index == 3) {
            return Domain.UNKNOWN;
        } else {
            return Domain.UNDEFINED;
        }
    }
}
