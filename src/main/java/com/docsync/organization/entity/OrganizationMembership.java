package com.docsync.organization.entity;

import com.docsync.common.entity.BaseEntity;
import com.docsync.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Links a user to an organization with a role. The pair (user, organization)
 * is unique and forms the tenant-isolation boundary.
 */
@Entity
@Table(name = "organization_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_user_org",
                columnNames = {"user_id", "organization_id"}))
public class OrganizationMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected OrganizationMembership() {
    }

    public OrganizationMembership(User user, Organization organization, MemberRole role) {
        this.user = user;
        this.organization = organization;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }
}
