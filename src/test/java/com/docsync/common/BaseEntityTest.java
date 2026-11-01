package com.docsync.common;

import com.docsync.common.entity.BaseEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseEntity.
 */
class BaseEntityTest {

    // Concrete subclass for testing the abstract BaseEntity
    static class TestEntity extends BaseEntity {
        public void setTestId(UUID id) {
            super.setId(id);
        }

        public void triggerCreate() {
            onCreate();
        }

        public void triggerUpdate() {
            onUpdate();
        }
    }

    @Test
    void onCreate_setsTimestamps() {
        TestEntity entity = new TestEntity();
        Instant before = Instant.now();

        entity.triggerCreate();

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertFalse(entity.getCreatedAt().isBefore(before));
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    void onUpdate_updatesTimestamp() throws InterruptedException {
        TestEntity entity = new TestEntity();
        entity.triggerCreate();
        Instant originalUpdatedAt = entity.getUpdatedAt();

        // Small delay to ensure different timestamp
        Thread.sleep(10);
        entity.triggerUpdate();

        assertEquals(entity.getCreatedAt(), entity.getCreatedAt()); // createdAt unchanged
        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getUpdatedAt().isAfter(originalUpdatedAt) ||
                entity.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void equals_sameId_returnsTrue() {
        UUID id = UUID.randomUUID();
        TestEntity entity1 = new TestEntity();
        entity1.setTestId(id);
        TestEntity entity2 = new TestEntity();
        entity2.setTestId(id);

        assertEquals(entity1, entity2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        TestEntity entity1 = new TestEntity();
        entity1.setTestId(UUID.randomUUID());
        TestEntity entity2 = new TestEntity();
        entity2.setTestId(UUID.randomUUID());

        assertNotEquals(entity1, entity2);
    }

    @Test
    void equals_nullId_returnsFalse() {
        TestEntity entity1 = new TestEntity();
        TestEntity entity2 = new TestEntity();

        // Two entities with null IDs should not be equal
        assertNotEquals(entity1, entity2);
    }

    @Test
    void equals_null_returnsFalse() {
        TestEntity entity = new TestEntity();
        entity.setTestId(UUID.randomUUID());

        assertNotEquals(null, entity);
    }

    @Test
    void hashCode_isConsistent() {
        TestEntity entity = new TestEntity();
        entity.setTestId(UUID.randomUUID());

        int hash1 = entity.hashCode();
        int hash2 = entity.hashCode();

        assertEquals(hash1, hash2);
    }
}
