// controller/HealthController.java
package com.example.stepsonic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}


// controller/AnalyzeController.java
package com.example.stepsonic.controller;

import com.example.stepsonic.model.AnalyzeRequest;
import com.example.stepsonic.model.StepFrequency;
import com.example.stepsonic.service.StepAnalysisService;
import com.github.javaparser.ParseProblemException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final StepAnalysisService stepAnalysisService;

    public AnalyzeController(StepAnalysisService stepAnalysisService) {
        this.stepAnalysisService = stepAnalysisService;
    }

    @PostMapping("/analyze")
    public List<StepFrequency> analyze(@Valid @RequestBody AnalyzeRequest request) {
        return stepAnalysisService.analyze(request.code(), request.algorithm());
    }

    @ExceptionHandler(ParseProblemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleParseError(ParseProblemException ex) {
        return Map.of("error", "Could not parse code: " + firstLine(ex.getMessage()));
    }

    private String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl == -1 ? text : text.substring(0, nl);
    }
}