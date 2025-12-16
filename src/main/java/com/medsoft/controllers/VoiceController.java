package com.medsoft.controllers;

import com.medsoft.models.OperationReport;
import com.medsoft.models.RecognitionResult;
import com.medsoft.models.dto.OperationReportDto;
import com.medsoft.services.OperationReportService;
import com.medsoft.services.VoiceRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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


    /**
     * Тестирование с записью аудио через микрофон
     */
    @PostMapping("/record-and-recognize")
    public ResponseEntity<Map<String, Object>> recordAndRecognize(
            @RequestParam(defaultValue = "3000") int durationMs) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Начинаем запись аудио на {} мс", durationMs);

            byte[] audioData = recordAudio(durationMs);

            if (audioData.length == 0) {
                result.put("error", "Не удалось записать аудио");
                return ResponseEntity.badRequest().body(result);
            }

            String filename = saveAudioForDebug(audioData);

            RecognitionResult recognitionResult = voiceRecognitionService.recognizeFromBytes(audioData);

            result.put("filename", filename);
            result.put("recognizedText", recognitionResult.getText());
            result.put("isCommand", recognitionResult.isCommand());
            result.put("commandType", recognitionResult.getRecognizedCommand());
            result.put("confidence", recognitionResult.getConfidence());
            result.put("processingTimeMs", recognitionResult.getProcessingTimeMs());
            result.put("audioSizeBytes", audioData.length);
            result.put("timestamp", LocalDateTime.now().format(formatter));

            log.info("Результат распознавания: {}", result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Ошибка при записи/распознавании: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Тестирование с загруженным аудиофайлом
     */
    @PostMapping("/upload-and-recognize")
    public ResponseEntity<Map<String, Object>> uploadAndRecognize(
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("error", "Файл пуст");
                return ResponseEntity.badRequest().body(result);
            }

            Path tempFile = Files.createTempFile("audio_test_", ".wav");
            file.transferTo(tempFile);

            log.info("Загружен файл: {} ({} байт)",
                    file.getOriginalFilename(), file.getSize());

            RecognitionResult recognitionResult = voiceRecognitionService.recognizeFromFile(tempFile.toString());

            Files.deleteIfExists(tempFile);

            result.put("originalFilename", file.getOriginalFilename());
            result.put("recognizedText", recognitionResult.getText());
            result.put("isCommand", recognitionResult.isCommand());
            result.put("commandType", recognitionResult.getRecognizedCommand());
            result.put("confidence", recognitionResult.getConfidence());
            result.put("processingTimeMs", recognitionResult.getProcessingTimeMs());
            result.put("timestamp", LocalDateTime.now().format(formatter));

            log.info("Результат распознавания файла: {}", result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Тестирование непрерывного распознавания
     */
    @PostMapping("/start-continuous")
    public ResponseEntity<Map<String, Object>> startContinuousTest(
            @RequestParam(defaultValue = "10000") int durationMs) {

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
			@RequestBody OperationReportDto reportDto) {

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

	/**
     * Тестовый набор команд и фраз
     */
    @GetMapping("/test-cases")
    public ResponseEntity<Map<String, Object>> getTestCases() {
        Map<String, Object> testCases = new HashMap<>();

        testCases.put("commands", Arrays.asList(
                "пациент",
                "врач",
                "диагноз",
                "операция",
                "следующее поле",
                "предыдущее поле",
                "готово",
                "завершить",
                "создать pdf",
                "отправить отчет",
                "поле диагноз",
                "поле пациент"
        ));

        testCases.put("fieldData", Arrays.asList(
                "Иванов Иван Иванович",
                "Петров Петр Петрович",
                "Острый аппендицит",
                "Лапароскопическая аппендэктомия",
                "Сидоров Алексей Владимирович",
                "табельный номер один два три четыре пять"
        ));

        testCases.put("mixedPhrases", Arrays.asList(
                "пациент иванов иван иванович",
                "диагноз острый аппендицит следующее поле",
                "заполняющий сидоров алексей владимирович готово"
        ));

        return ResponseEntity.ok(testCases);
    }

    /**
     * Проверка доступности микрофона
     */
    @GetMapping("/check-microphone")
    public ResponseEntity<Map<String, Object>> checkMicrophone() {
        Map<String, Object> result = new HashMap<>();

        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            boolean isSupported = AudioSystem.isLineSupported(info);
            result.put("microphoneSupported", isSupported);

            if (isSupported) {
                result.put("message", "Микрофон поддерживается");

                try {
                    TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                    line.open(format);
                    line.close();
                    result.put("accessGranted", true);
                    result.put("message", "Микрофон доступен");
                } catch (LineUnavailableException e) {
                    result.put("accessGranted", false);
                    result.put("error", "Микрофон занят или недоступен: " + e.getMessage());
                }
            } else {
                result.put("message", "Микрофон не поддерживается на данной системе");
            }

            result.put("audioFormat", format.toString());

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    private byte[] recordAudio(int durationMs) throws LineUnavailableException, IOException {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            log.warn("Микрофон не поддерживается");
            return new byte[0];
        }

        try (TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[4096];

            long startTime = System.currentTimeMillis();
            long endTime = startTime + durationMs;

            log.info("Запись аудио...");

            while (System.currentTimeMillis() < endTime) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            microphone.stop();
            microphone.close();

            log.info("Запись завершена, записано {} байт", out.size());

            return createWavFile(out.toByteArray(), format);

        } catch (Exception e) {
            log.error("Ошибка записи аудио: {}", e.getMessage());
            throw e;
        }
    }

    private byte[] createWavFile(byte[] pcmData, AudioFormat format) throws IOException {
        try (ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {
            writeWavHeader(wavOut, format, pcmData.length);

            wavOut.write(pcmData);

            updateWavHeader(wavOut);

            return wavOut.toByteArray();
        }
    }

    private void writeWavHeader(OutputStream out, AudioFormat format, int dataLength) throws IOException {
        int fileSize = 36 + dataLength;

        out.write("RIFF".getBytes());
        writeInt(out, fileSize);
        out.write("WAVE".getBytes());

        out.write("fmt ".getBytes());
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, format.getChannels());
        writeInt(out, (int) format.getSampleRate());
        writeInt(out, (int) (format.getSampleRate() * format.getFrameSize()));
        writeShort(out, format.getFrameSize());
        writeShort(out, format.getSampleSizeInBits());

        out.write("data".getBytes());
        writeInt(out, dataLength);
    }

    private void updateWavHeader(ByteArrayOutputStream wavOut) throws IOException {
        byte[] wavData = wavOut.toByteArray();

        int fileSize = wavData.length - 8;
        wavData[4] = (byte) (fileSize & 0xFF);
        wavData[5] = (byte) ((fileSize >> 8) & 0xFF);
        wavData[6] = (byte) ((fileSize >> 16) & 0xFF);
        wavData[7] = (byte) ((fileSize >> 24) & 0xFF);

        int dataSize = fileSize - 36;
        wavData[40] = (byte) (dataSize & 0xFF);
        wavData[41] = (byte) ((dataSize >> 8) & 0xFF);
        wavData[42] = (byte) ((dataSize >> 16) & 0xFF);
        wavData[43] = (byte) ((dataSize >> 24) & 0xFF);

        wavOut.reset();
        wavOut.write(wavData);
    }

    private void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private String saveAudioForDebug(byte[] audioData) throws IOException {
        String filename = "debug_audio_" + LocalDateTime.now().format(formatter) + ".wav";
        Path filePath = Paths.get("debug_audio", filename);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, audioData);

        log.info("Аудио сохранено для отладки: {}", filePath.toAbsolutePath());
        return filename;
    }

    private String identifyCommandType(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("пациент")) return "FIELD_PATIENT";
        if (lowerText.contains("врач")) return "FIELD_DOCTOR";
        if (lowerText.contains("диагноз")) return "FIELD_DIAGNOSIS";
        if (lowerText.contains("операция")) return "FIELD_OPERATION";
        if (lowerText.contains("заполняющий")) return "FIELD_FILLER";
        if (lowerText.contains("табельный")) return "FIELD_PERSONAL_NUMBER";
        if (lowerText.contains("следующее поле")) return "NEXT_FIELD";
        if (lowerText.contains("предыдущее поле")) return "PREVIOUS_FIELD";
        if (lowerText.contains("готово") || lowerText.contains("заверши")) return "COMPLETE";
        if (lowerText.contains("создай pdf") || lowerText.contains("создать pdf")) return "GENERATE_PDF";
        if (lowerText.contains("отправь") || lowerText.contains("отправить")) return "SEND_EMAIL";

        return "UNKNOWN";
    }
}