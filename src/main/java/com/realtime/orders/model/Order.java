package com.realtime.orders.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId()                   { return id; }
    public void setId(Long id)            { this.id = id; }

    public String getCustomerName()                    { return customerName; }
    public void   setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductName()                   { return productName; }
    public void   setProductName(String productName) { this.productName = productName; }

    public String getStatus()            { return status; }
    public void   setStatus(String status) { this.status = status; }

    public LocalDateTime getUpdatedAt()                      { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
