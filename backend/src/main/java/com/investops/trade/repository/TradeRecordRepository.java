package com.investops.trade.repository;

import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, UUID>, JpaSpecificationExecutor<TradeRecord> {

    boolean existsBySourceSystemAndExternalTradeId(SourceSystem sourceSystem, String externalTradeId);

    Optional<TradeRecord> findBySourceSystemAndExternalTradeId(SourceSystem sourceSystem, String externalTradeId);

    List<TradeRecord> findByImportBatchId(UUID importBatchId);

    List<TradeRecord> findBySourceSystemAndTradeDateBetween(
            SourceSystem sourceSystem, java.time.LocalDate tradeDateFrom, java.time.LocalDate tradeDateTo);
}
