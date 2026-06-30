package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.GlucoseForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/outcome")
@RequiredArgsConstructor
public class GlucoseForecastController {

    private final GlucoseForecastService glucoseForecastService;

    @GetMapping("/forecast/{registerId}")
    public Result<Map<String, Object>> getForecast(
        @PathVariable Long registerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return Result.success(glucoseForecastService.getForecast(registerId, from, to));
    }

    @PostMapping("/forecast/{registerId}/refresh")
    public Result<Map<String, Object>> refreshForecast(@PathVariable Long registerId) {
        return Result.success("预测已更新", glucoseForecastService.refreshForecast(registerId));
    }

    @GetMapping("/glucose-cohort/{registerId}")
    public Result<Map<String, Object>> isGlucoseCohort(@PathVariable Long registerId) {
        return Result.success(Map.of(
            "registerId", registerId,
            "glucoseCohort", glucoseForecastService.isGlucosePatient(registerId)
        ));
    }
}
