package com.wms.repository;

import com.wms.dto.InventoryResponse;
import com.wms.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    long countByProductId(Long productId);

    @Modifying
    @Query(value = """
        INSERT INTO inventory (product_id, location_code, quantity)
        VALUES (:productId, :locationCode, :quantity)
        ON CONFLICT (product_id, location_code)
        DO UPDATE SET quantity = inventory.quantity + :quantity
        """, nativeQuery = true)
    void upsertQuantity(@Param("productId") Long productId,
                        @Param("locationCode") String locationCode,
                        @Param("quantity") int quantity);

    @Modifying
    @Query(value = """
        UPDATE inventory
        SET quantity = quantity - :quantity, updated_at = NOW()
        WHERE product_id = :productId
          AND location_code = :locationCode
          AND quantity >= :quantity
        """, nativeQuery = true)
    int deductQuantity(@Param("productId") Long productId,
                       @Param("locationCode") String locationCode,
                       @Param("quantity") int quantity);

    @Query("""
        SELECT new com.wms.dto.InventoryResponse(
            i.productId, p.name, p.sku, i.locationCode, w.name, i.quantity, i.updatedAt
        )
        FROM Inventory i
        JOIN Product p ON i.productId = p.id
        JOIN Location l ON i.locationCode = l.code
        JOIN Warehouse w ON l.warehouseId = w.id
        WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%)
          AND (:warehouseId IS NULL OR w.id = :warehouseId)
        ORDER BY i.updatedAt DESC
        """)
    Page<InventoryResponse> search(@Param("keyword") String keyword,
                                    @Param("warehouseId") Long warehouseId,
                                    Pageable pageable);
}
