package com.medsoft.services;

import com.medsoft.models.dto.OperationReportDto;
import com.medsoft.models.OperationReport;
import com.medsoft.repository.OperationReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationReportServiceImpl implements OperationReportService {

	private final OperationReportRepository reportRepository;

	@Override
	public OperationReport createReport(OperationReportDto dto) {
		OperationReport report = OperationReport.builder()
				.patientFullName(dto.getPatientFullName())
				.doctorFullName(dto.getDoctorFullName())
				.diagnosis(dto.getDiagnosis())
				.operationDescription(dto.getOperationDescription())
				.fillerFullName(dto.getFillerFullName())
				.personalNumber(dto.getPersonalNumber())
				.createdAt(LocalDateTime.now())
				.build();

		return reportRepository.save(report);
	}
}

