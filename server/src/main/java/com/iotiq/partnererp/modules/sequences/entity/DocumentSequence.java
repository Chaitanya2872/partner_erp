package com.iotiq.partnererp.modules.sequences.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Last issued number per prefix and financial-year tag (e.g. EST / 2627). Rows are locked when a
 * number is allocated so concurrent saves cannot issue duplicates.
 */
@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @EmbeddedId
    private Key id;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected DocumentSequence() {
    }

    public DocumentSequence(String prefix, String fyTag) {
        this.id = new Key(prefix, fyTag);
    }

    public Key getId() {
        return id;
    }

    public int getLastValue() {
        return lastValue;
    }

    public void setLastValue(int lastValue) {
        this.lastValue = lastValue;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "prefix", nullable = false, length = 10)
        private String prefix;

        @Column(name = "fy_tag", nullable = false, length = 4)
        private String fyTag;

        protected Key() {
        }

        public Key(String prefix, String fyTag) {
            this.prefix = prefix;
            this.fyTag = fyTag;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getFyTag() {
            return fyTag;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && prefix.equals(k.prefix) && fyTag.equals(k.fyTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prefix, fyTag);
        }
    }
}
