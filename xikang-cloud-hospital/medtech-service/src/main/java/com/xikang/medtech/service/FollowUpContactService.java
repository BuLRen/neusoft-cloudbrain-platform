package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpContactMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpContactService {

    private final FollowUpContactMapper contactMapper;
    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpHistoryService historyService;

    public List<Map<String, Object>> listRecords(Long registerId, Integer limit) {
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        return contactMapper.selectContactRecords(registerId, limit != null ? limit : 50);
    }

    @Transactional
    public Map<String, Object> createRecord(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        String summary = request.get("summary") != null ? String.valueOf(request.get("summary")).trim() : "";
        if (summary.isEmpty()) {
            throw new BusinessException("联系摘要不能为空");
        }

        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        LocalDate contactDate = parseDate(request.get("contactDate"));
        if (contactDate == null) {
            contactDate = LocalDate.now();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("employeeId", employeeId);
        payload.put("contactDate", contactDate);
        payload.put("channel", request.get("channel") != null ? String.valueOf(request.get("channel")) : "phone");
        payload.put("durationMinutes", toInt(request.get("durationMinutes")));
        payload.put("summary", summary);
        payload.put("nextAction", request.get("nextAction"));

        contactMapper.insertContactRecord(payload);
        Long recordId = toLong(payload.get("id"));

        historyService.recordContactCompleted(
            registerId,
            employeeId,
            String.valueOf(payload.get("channel")),
            summary,
            recordId
        );

        Map<String, Object> shift = shiftMapper.selectShiftByEmployeeAndDate(employeeId, contactDate);
        if (shift != null) {
            contactMapper.completeShiftContactTask(toLong(shift.get("id")), registerId);
        }

        Map<String, Object> result = new LinkedHashMap<>(contactMapper.selectContactRecordById(recordId));
        return result;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value).trim());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
