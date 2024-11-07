package com.jpage4500.devicemanager.data;

public enum LogFilterExpression {
    EQUALS("is"),
    CONTAINS("contains"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with"),
    NOT_EQUALS("is NOT"),
    NOT_CONTAINS("does NOT contain"),
    NOT_STARTS_WITH("does NOT start with"),
    NOT_ENDS_WITH("does NOT end with"),
    ;
    String desc;

    LogFilterExpression(String desc) {
        this.desc = desc;
    }

    public boolean isNotExpression() {
        switch (this) {
            case NOT_EQUALS:
            case NOT_CONTAINS:
            case NOT_STARTS_WITH:
            case NOT_ENDS_WITH:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return desc;
    }
}
