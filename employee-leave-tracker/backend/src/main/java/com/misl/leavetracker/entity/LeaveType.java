package com.misl.leavetracker.entity;

/**
 * The kind of leave being requested.
 * Kept to three values on purpose - the assessment does not ask for a
 * leave-balance/quota system, so these are labels, not accounting categories.
 */
public enum LeaveType {
    CASUAL,
    SICK,
    ANNUAL
}
