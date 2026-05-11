package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

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
}
