package com.medsoft.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String patientFullName;

	@Column(nullable = false)
	private String doctorFullName;

	@Column(nullable = false)
	private String diagnosis;

	@Column(columnDefinition = "TEXT")
	private String operationDescription;

	@Column(nullable = false)
	private String fillerFullName;

	@Column(nullable = false)
	private String personalNumber;

	@Column(nullable = false)
	private LocalDateTime createdAt;
}

