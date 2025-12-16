package com.medsoft.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionResult {
    private String text;
    private boolean isCommand;
    private String recognizedCommand;
    private double confidence;
    private long processingTimeMs;
}
