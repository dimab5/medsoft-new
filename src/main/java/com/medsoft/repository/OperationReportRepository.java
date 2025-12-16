package com.medsoft.repository;

import com.medsoft.models.OperationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationReportRepository extends JpaRepository<OperationReport, Long> {
}
