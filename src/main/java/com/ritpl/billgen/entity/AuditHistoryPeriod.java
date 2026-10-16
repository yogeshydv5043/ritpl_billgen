package com.ritpl.billgen.entity;

/**
 * How far back a customer account may browse and export its own audit trail.
 *
 * <p>This is a <em>viewing</em> window chosen by the account. It is not the retention rule: records
 * are only ever removed by the retention job, which is driven by each audit type's own retention
 * days. An account that shortens this period hides history from itself without deleting anything.</p>
 */
public enum AuditHistoryPeriod {
    DAYS_15("Last 15 days", 15),
    ONE_MONTH("Last 1 month", 31),
    THREE_MONTHS("Last 3 months", 92),
    SIX_MONTHS("Last 6 months", 183),
    ONE_YEAR("Last 1 year", 366);

    private final String displayName;
    private final int days;

    AuditHistoryPeriod(String displayName, int days) {
        this.displayName = displayName;
        this.days = days;
    }

    public String getCode() { return name(); }
    public String getDisplayName() { return displayName; }
    public int getDays() { return days; }

    /** A row saved before this column existed, or written by hand, still gets a usable window. */
    public static AuditHistoryPeriod orDefault(AuditHistoryPeriod value) {
        return value == null ? ONE_YEAR : value;
    }
}
