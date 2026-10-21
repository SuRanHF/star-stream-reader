package com.huazhenghai.readergame.service;

/** Actual inventory effect of a grant attempt, including the catalog name at grant time. */
public record ItemGrantResult(int quantityGranted, int totalQuantity, String itemName) {

    /** Keeps existing callers source-compatible while they do not need the catalog name. */
    public ItemGrantResult(int quantityGranted, int totalQuantity) {
        this(quantityGranted, totalQuantity, null);
    }
}
