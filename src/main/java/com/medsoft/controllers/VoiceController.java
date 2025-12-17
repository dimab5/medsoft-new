package com.medsoft.controllers;

import com.medsoft.models.OperationReport;
import com.medsoft.models.dto.OperationReportDto;
import com.medsoft.services.OperationReportService;
import com.medsoft.services.VoiceRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/test/voice")
@RequiredArgsConstructor
@Slf4j
public class VoiceController {

    private final VoiceRecognitionService voiceRecognitionService;
	private final OperationReportService operationReportService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @PostMapping("/start-continuous")
    public ResponseEntity<Map<String, Object>> startContinuousTest(
            @RequestParam(defaultValue = "100000000") int durationMs
	) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Запуск непрерывного распознавания на {} мс", durationMs);

            voiceRecognitionService.startContinuousRecognition();

            new Thread(() -> {
                try {
                    Thread.sleep(durationMs);
                    voiceRecognitionService.stopContinuousRecognition();
                    log.info("Непрерывное распознавание автоматически остановлено");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            result.put("status", "continuous_recognition_started");
            result.put("durationMs", durationMs);
            result.put("message", "Распознавание запущено. Лог будет в консоли.");
            result.put("timestamp", LocalDateTime.now().format(formatter));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

	@PostMapping("/stop-continuous")
	public ResponseEntity<Map<String, Object>> stopContinuousRecognition() {
		Map<String, Object> result = new HashMap<>();

		try {
			voiceRecognitionService.stopContinuousRecognition();

			result.put("status", "continuous_recognition_stopped");
			result.put("timestamp", LocalDateTime.now().format(formatter));

			log.info("Непрерывное распознавание остановлено вручную");

			return ResponseEntity.ok(result);

		} catch (Exception e) {
			log.error("Ошибка остановки распознавания", e);
			result.put("error", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
		}
	}

	@PostMapping("/save-report")
	public ResponseEntity<?> saveOperationReport(
			@RequestBody OperationReportDto reportDto
	) {
		try {
			OperationReport savedReport =
					operationReportService.createReport(reportDto);

			log.info("Отчет сохранен с id={}", savedReport.getId());

			return ResponseEntity.ok(Map.of(
					"status", "saved",
					"reportId", savedReport.getId(),
					"createdAt", savedReport.getCreatedAt()
			));

		} catch (Exception e) {
			log.error("Ошибка сохранения отчета", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", e.getMessage()));
		}
	}
}