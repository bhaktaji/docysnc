package com.docsync.organization.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A tenant boundary. All repository and documentation data belongs to an organization.
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private OrganizationPlan plan = OrganizationPlan.FREE;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    protected Organization() {
    }

    public Organization(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.plan = OrganizationPlan.FREE;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OrganizationPlan getPlan() {
        return plan;
    }

    public void setPlan(OrganizationPlan plan) {
        this.plan = plan;
    }

    public String getBillingEmail() {
        return billingEmail;
    }

    public void setBillingEmail(String billingEmail) {
        this.billingEmail = billingEmail;
    }
}
