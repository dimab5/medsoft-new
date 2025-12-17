package com.medsoft.services;

import com.medsoft.models.dto.OperationReportDto;
import com.medsoft.models.OperationReport;

public interface OperationReportService {

	OperationReport createReport(OperationReportDto dto);
}
