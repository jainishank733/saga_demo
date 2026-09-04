package com.example.inventoryservice.dto;

public class InventoryReserveResponse {

    private String productCode;
    private Integer changedQuantity;
    private Integer remainingStock;

    public InventoryReserveResponse() {
    }

    public InventoryReserveResponse(String productCode, Integer changedQuantity, Integer remainingStock) {
        this.productCode = productCode;
        this.changedQuantity = changedQuantity;
        this.remainingStock = remainingStock;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Integer getChangedQuantity() {
        return changedQuantity;
    }

    public void setChangedQuantity(Integer changedQuantity) {
        this.changedQuantity = changedQuantity;
    }

    public Integer getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Integer remainingStock) {
        this.remainingStock = remainingStock;
    }
}
