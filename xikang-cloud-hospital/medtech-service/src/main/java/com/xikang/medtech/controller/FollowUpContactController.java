package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/contact")
@RequiredArgsConstructor
public class FollowUpContactController {

    private final FollowUpContactService contactService;

    @GetMapping("/records/{registerId}")
    public Result<List<Map<String, Object>>> listRecords(
        @PathVariable Long registerId,
        @RequestParam(required = false) Integer limit
    ) {
        return Result.success(contactService.listRecords(registerId, limit));
    }

    @PostMapping("/records")
    public Result<Map<String, Object>> createRecord(@RequestBody Map<String, Object> request) {
        return Result.success("联系记录已保存", contactService.createRecord(request));
    }
}
