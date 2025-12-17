package com.medsoft.services;

public interface VoiceRecognitionService {
    void startContinuousRecognition();
    void stopContinuousRecognition();
    boolean isCommand(String text);
}
