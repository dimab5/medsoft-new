package com.medsoft.models.dto;

import lombok.Data;

@Data
public class OperationReportDto {

	private String patientFullName;
	private String doctorFullName;
	private String diagnosis;
	private String operationDescription;
	private String fillerFullName;
	private String personalNumber;
}
