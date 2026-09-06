package com.misl.leavetracker.entity;

/**
 * The kind of leave being requested.
 *
 * Note these are labels, not separate balances: the annual entitlement in
 * LeaveService is a single pool shared by all three types, not 27 days of each.
 * Per-type allowances would be the natural next step, and would mean storing the
 * allowance per type rather than as one application-wide value.
 */
public enum LeaveType {
    CASUAL,
    SICK,
    ANNUAL
}
