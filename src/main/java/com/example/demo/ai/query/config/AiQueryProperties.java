package com.example.demo.ai.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable limits enforced by the generic AI query system, bound from {@code ai.query.*}.
 * These are hard server-side ceilings the model cannot override or negotiate around.
 */
@Component
@ConfigurationProperties(prefix = "ai.query")
public class AiQueryProperties {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private int maxFilters = 10;
    private int maxSelectedFields = 25;
    private int maxSortFields = 5;
    /** Reserved for future relationship-traversal support; joins are not implemented yet. */
    private int maxJoins = 0;
    /** Reserved for future relationship-traversal support; joins are not implemented yet. */
    private int maxJoinDepth = 0;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxFilters() {
        return maxFilters;
    }

    public void setMaxFilters(int maxFilters) {
        this.maxFilters = maxFilters;
    }

    public int getMaxSelectedFields() {
        return maxSelectedFields;
    }

    public void setMaxSelectedFields(int maxSelectedFields) {
        this.maxSelectedFields = maxSelectedFields;
    }

    public int getMaxSortFields() {
        return maxSortFields;
    }

    public void setMaxSortFields(int maxSortFields) {
        this.maxSortFields = maxSortFields;
    }

    public int getMaxJoins() {
        return maxJoins;
    }

    public void setMaxJoins(int maxJoins) {
        this.maxJoins = maxJoins;
    }

    public int getMaxJoinDepth() {
        return maxJoinDepth;
    }

    public void setMaxJoinDepth(int maxJoinDepth) {
        this.maxJoinDepth = maxJoinDepth;
    }
}
