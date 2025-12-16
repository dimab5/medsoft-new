package com.medsoft.services;

import com.medsoft.models.RecognitionResult;

public interface VoiceRecognitionService {
    RecognitionResult recognizeFromFile(String audioFilePath);
    RecognitionResult recognizeFromBytes(byte[] audioData);
    void startContinuousRecognition();
    void stopContinuousRecognition();
    boolean isCommand(String text);
}
