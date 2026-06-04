package com.realtime.orders.dto;

import jakarta.validation.constraints.NotBlank;

public class OrderRequest {

    @NotBlank(message = "customerName is required")
    private String customerName;

    @NotBlank(message = "productName is required")
    private String productName;

    // Optional on create (defaults to 'pending'); optional on update (only status changes allowed)
    private String status;

    public String getCustomerName()                    { return customerName; }
    public void   setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductName()                   { return productName; }
    public void   setProductName(String productName) { this.productName = productName; }

    public String getStatus()              { return status; }
    public void   setStatus(String status) { this.status = status; }
}
