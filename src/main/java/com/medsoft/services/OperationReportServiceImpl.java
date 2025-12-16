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

	@Override
	public OperationReport updateField(Long reportId, String fieldName, String value) {
		OperationReport report = reportRepository.findById(reportId)
				.orElseThrow(() -> new RuntimeException("Отчет не найден"));

		switch (fieldName) {
			case "patientField" -> report.setPatientFullName(value);
			case "doctorField" -> report.setDoctorFullName(value);
			case "diagnosisField" -> report.setDiagnosis(value);
			case "operationField" -> report.setOperationDescription(value);
			case "fillerField" -> report.setFillerFullName(value);
			case "personalNumberField" -> report.setPersonalNumber(value);
			default -> throw new IllegalArgumentException("Неизвестное поле: " + fieldName);
		}

		return reportRepository.save(report);
	}

	@Override
	public OperationReport getReport(Long reportId) {
		return reportRepository.findById(reportId)
				.orElseThrow(() -> new RuntimeException("Отчет не найден"));
	}
}

