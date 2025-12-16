package com.medsoft.services;

import com.medsoft.models.RecognitionResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class VoskRecognitionServiceImpl implements VoiceRecognitionService {

    @Value("${vosk.model.path}")
    private String modelPath;

    private org.vosk.Recognizer recognizer;
    private org.vosk.Model model;
    private TargetDataLine microphone;
    private ExecutorService recognitionExecutor;
    private volatile boolean isListening = false;

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
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String resultJson = recognizer.getResult();
                        String text = extractTextFromJson(resultJson);

                        if (!text.isEmpty()) {
                            log.info("Распознано: {}", text);

                            if (isCommand(text)) {
                                String commandType = identifyCommandType(text);
                                log.info("Выполняется команда: {}", commandType);
                                // TODO: Вызвать обработчик команды
                            } else {
                                log.info("Данные для поля: {}", text);
                                // TODO: Записать в активное поле формы
                            }
                        }
                    }
                }

                Thread.sleep(50);

            } catch (Exception e) {
                log.error("Ошибка в цикле распознавания: {}", e.getMessage());
            }
        }
    }

    private String extractTextFromJson(String json) {
        try {
            if (json.contains("\"text\" : \"")) {
                int start = json.indexOf("\"text\" : \"") + 10;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
            return "";
        } catch (Exception e) {
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