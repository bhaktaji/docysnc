package com.docsync.organization.entity;

/**
 * Organization roles ordered from least to most privileged.
 */
public enum MemberRole {
    VIEWER(0),
    MEMBER(1),
    ADMIN(2),
    OWNER(3);

    private final int level;

    MemberRole(int level) {
        this.level = level;
    }

    public boolean atLeast(MemberRole minimum) {
        return this.level >= minimum.level;
    }

    public boolean isHigherThan(MemberRole other) {
        return this.level > other.level;
    }
}
