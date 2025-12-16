package com.medsoft.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medsoft.models.RecognitionResult;
import com.medsoft.websocket.VoiceWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class VoskRecognitionServiceImpl implements VoiceRecognitionService {

    @Value("${vosk.model.path}")
    private String modelPath;

    private org.vosk.Recognizer recognizer;
    private org.vosk.Model model;
    private TargetDataLine microphone;
    private ExecutorService recognitionExecutor;
    private volatile boolean isListening = false;
	private final VoiceWebSocketHandler voiceWebSocketHandler;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> COMMANDS = new HashSet<>(Arrays.asList(
            "пациент", "врач", "диагноз", "операция", "заполняющий", "табельный",
            "следующее поле", "предыдущее поле", "готово", "завершить",
            "очистить", "отмена", "создать pdf", "отправить отчет",
            "поле диагноз", "поле операция", "поле пациент", "поле врач"
    ));

    private static final Map<String, String> FIELD_COMMANDS = Map.of(
            "пациент", "patientField",
            "врач", "doctorField",
            "диагноз", "diagnosisField",
            "операция", "operationField",
            "заполняющий", "fillerField",
            "табельный", "personalNumberField"
    );

    @PostConstruct
    public void init() {
        try {
            log.info("Инициализация модели Vosk из: {}", modelPath);
            model = new org.vosk.Model(modelPath);
            recognizer = new org.vosk.Recognizer(model, 16000.0f);

            recognizer.setWords(true);
            recognizer.setPartialWords(true);

            recognitionExecutor = Executors.newSingleThreadExecutor();
            log.info("Сервис распознавания речи инициализирован");

        } catch (Exception e) {
            log.error("Ошибка инициализации Vosk: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать Vosk", e);
        }
    }

    @Override
    public RecognitionResult recognizeFromFile(String audioFilePath) {
        long startTime = System.currentTimeMillis();

        try (InputStream ais = new FileInputStream(audioFilePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            StringBuilder resultBuilder = new StringBuilder();

            while ((bytesRead = ais.read(buffer)) >= 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String resultJson = recognizer.getResult();
                    String text = extractTextFromJson(resultJson);
                    if (!text.isEmpty()) {
                        resultBuilder.append(text).append(" ");
                    }
                }
            }

            String finalResult = recognizer.getFinalResult();
            String finalText = extractTextFromJson(finalResult);
            if (!finalText.isEmpty()) {
                resultBuilder.append(finalText);
            }

            String recognizedText = resultBuilder.toString().trim();
            long processingTime = System.currentTimeMillis() - startTime;

            return createRecognitionResult(recognizedText, processingTime);

        } catch (IOException e) {
            log.error("Ошибка чтения аудиофайла: {}", e.getMessage());
            return new RecognitionResult("", false, "", 0.0, 0);
        }
    }

    @Override
    public RecognitionResult recognizeFromBytes(byte[] audioData) {
        long startTime = System.currentTimeMillis();

        try {
            byte[] pcmData = convertAudioToPCM(audioData);

            if (recognizer.acceptWaveForm(pcmData, pcmData.length)) {
                String resultJson = recognizer.getResult();
                String recognizedText = extractTextFromJson(resultJson);
                long processingTime = System.currentTimeMillis() - startTime;

                return createRecognitionResult(recognizedText, processingTime);
            }

            return new RecognitionResult("", false, "", 0.0,
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Ошибка распознавания: {}", e.getMessage());
            return new RecognitionResult("", false, "", 0.0, 0);
        }
    }

    @Override
    public void startContinuousRecognition() {
        if (isListening) {
            log.warn("Распознавание уже запущено");
            return;
        }

        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                log.error("Микрофон не поддерживается");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            isListening = true;
            recognitionExecutor.submit(this::continuousRecognitionLoop);

            log.info("Непрерывное распознавание запущено");

        } catch (LineUnavailableException e) {
            log.error("Ошибка доступа к микрофону: {}", e.getMessage());
        }
    }

    @Override
    public void stopContinuousRecognition() {
        isListening = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        log.info("Непрерывное распознавание остановлено");
    }

    @Override
    public boolean isCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase();

        for (String command : COMMANDS) {
            if (lowerText.contains(command)) {
                return true;
            }
        }

        return lowerText.matches(".*(поле|заполни|введи|очисти|удали|готово|заверши|отправь|создай).*");
    }

	private void continuousRecognitionLoop() {
		byte[] buffer = new byte[4096];

		while (isListening) {
			try {
				long startTime = System.currentTimeMillis();

				int bytesRead = microphone.read(buffer, 0, buffer.length);

				if (bytesRead > 0) {
					if (recognizer.acceptWaveForm(buffer, bytesRead)) {
						String resultJson = recognizer.getResult();
						String text = extractTextFromJson(resultJson);

						if (!text.isEmpty()) {
							long processingTime =
									System.currentTimeMillis() - startTime;

							log.info("Распознано: {}", text);

							RecognitionResult result =
									createRecognitionResult(text, processingTime);

							if (result.isCommand()) {
								log.info(
										"Выполняется команда: {}",
										result.getRecognizedCommand()
								);
							} else {
								log.info("Данные для поля: {}", text);
							}

							voiceWebSocketHandler.broadcast(result);
						}
					}
				}

				Thread.sleep(50);

			} catch (Exception e) {
				log.error("Ошибка в цикле распознавания: {}", e.getMessage(), e);
			}
		}
	}


	private String fixEncoding(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Pattern utf8Pattern = Pattern.compile("[РВСТУФХЦЧШЩЪЫЬЭЮЯ]+");
        Matcher matcher = utf8Pattern.matcher(text);

        if (matcher.find() && text.length() > matcher.group().length() * 2) {
            try {
                byte[] bytes = text.getBytes("Windows-1251");
                String decoded = new String(bytes, "UTF-8");

                if (decoded.matches(".*[А-Яа-яЁё].*") && !decoded.contains("Р")) {
                    return decoded;
                }
            } catch (UnsupportedEncodingException e) {
                log.warn("Ошибка перекодировки: {}", e.getMessage());
            }
        }

        return text;
    }

    private String extractTextFromJson(String json) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            String text = jsonNode.get("text").asText();

            if (text == null || text.trim().isEmpty()) {
                return "";
            }

            return fixEncoding(text);

        } catch (Exception e) {
            log.error("Ошибка извлечения текста из JSON: {} | JSON: {}", e.getMessage(), json);
            return "";
        }
    }

    private RecognitionResult createRecognitionResult(String text, long processingTime) {
        if (text == null || text.trim().isEmpty()) {
            return new RecognitionResult("", false, "", 0.0, processingTime);
        }

        boolean isCmd = isCommand(text);
        String commandType = isCmd ? identifyCommandType(text) : "";
        double confidence = calculateConfidence(text);

        return new RecognitionResult(text, isCmd, commandType, confidence, processingTime);
    }

    private String identifyCommandType(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("поле")) {
            return "FIELD_SWITCH";
        } else if (lowerText.contains("следующее")) {
            return "NEXT_FIELD";
        } else if (lowerText.contains("предыдущее")) {
            return "PREVIOUS_FIELD";
        } else if (lowerText.contains("готово") || lowerText.contains("заверши")) {
            return "COMPLETE";
        } else if (lowerText.contains("создай pdf") || lowerText.contains("создать pdf")) {
            return "GENERATE_PDF";
        } else if (lowerText.contains("отправь") || lowerText.contains("отправить")) {
            return "SEND_EMAIL";
        } else if (lowerText.contains("очисти") || lowerText.contains("очистить")) {
            return "CLEAR";
        }

        for (Map.Entry<String, String> entry : FIELD_COMMANDS.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                return "FIELD_" + entry.getValue().toUpperCase();
            }
        }

        return "UNKNOWN";
    }

    private double calculateConfidence(String text) {
        if (text.length() < 2) return 0.1;
        if (text.split(" ").length > 5) return 0.9;
        return 0.5 + (text.length() * 0.05);
    }

    private byte[] convertAudioToPCM(byte[] audioData) {
        return audioData;
    }

    @PreDestroy
    public void cleanup() {
        stopContinuousRecognition();

        if (recognitionExecutor != null) {
            recognitionExecutor.shutdown();
            try {
                if (!recognitionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    recognitionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                recognitionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (recognizer != null) {
            recognizer.close();
        }

        if (model != null) {
            model.close();
        }

        log.info("Сервис распознавания речи остановлен");
    }
}