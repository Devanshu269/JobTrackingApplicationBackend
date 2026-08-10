package com.jobtracker.enums;

/**
 * Names match the frontend's ACTIVITY_ACTIONS map in src/lib/activity.js — that module was
 * written against this contract so switching from the derived feed to the real endpoint is a
 * one-line change rather than a rewrite of the widget.
 */
public enum ActivityAction {
    JOB_CREATED,
    JOB_UPDATED,
    STATUS_CHANGED,
    OFFER_RECEIVED,
    REJECTED,
    ROUND_SCHEDULED,
    JOB_DELETED
}
