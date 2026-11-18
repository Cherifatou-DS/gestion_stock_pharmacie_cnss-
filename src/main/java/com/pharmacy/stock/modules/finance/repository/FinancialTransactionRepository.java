package com.pharmacy.stock.modules.finance.repository;

import com.pharmacy.stock.modules.finance.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    /**
     * Liste des transactions avec références et enregistreur chargés,
     * du plus récent au plus ancien. Les filtres null sont ignorés.
     */
    @Query("""
            select ft from FinancialTransaction ft
            left join fetch ft.referenceOrder o
            left join fetch ft.referenceSupplyRequest sr
            join fetch ft.recordedByUser u
            where (:type is null or ft.transactionType = :type)
            order by ft.createdAt desc, ft.id desc
            """)
    List<FinancialTransaction> search(@Param("type") String type);

    /**
     * Total des montants d'un type de transaction (engagements ou sorties valorisées).
     */
    @Query("""
            select COALESCE(sum(ft.amount), 0) from FinancialTransaction ft
            where (:type is null or ft.transactionType = :type)
            """)
    BigDecimal totalByType(@Param("type") String type);
}
