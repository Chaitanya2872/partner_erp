package com.iotiq.partnererp.modules.auth.entity;

/**
 * Which records of a module a user may see (stored per user and module).
 */
public enum DataScope {

    /** Records owned by or assigned to the user. */
    SELF,
    /** Records belonging to the user's team. */
    TEAM,
    /** All records of the module. */
    ALL
}
