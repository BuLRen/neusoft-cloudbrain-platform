package com.xikang.registration.service;

import com.xikang.registration.feign.PaymentFeignClient;
import com.xikang.registration.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stats Service - 管理员统计聚合服务
 *
 * v3.2：dailyTrend 的 charges 列改由 payment-service.dailyCharges 合并，
 *      SQL 不再读 expense_record 表（payment-service 已接管该表的读写主职责）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;
    private final PaymentFeignClient paymentFeignClient;

    /**
     * 科室工作量
     * @param startDate 可选，包含
     * @param endDate   可选，排除（即 [startDate, endDate)）
     */
    public List<Map<String, Object>> departmentWorkload(LocalDate startDate, LocalDate endDate) {
        return statsMapper.departmentWorkload(startDate, endDate);
    }

    /**
     * 每日趋势：默认近 7 天
     * v3.2：SQL 只算挂号量，charges 列从 paymentFeignClient.dailyCharges 合并。
     */
    public List<Map<String, Object>> dailyTrend(Integer days) {
        int n = (days == null || days <= 0 || days > 90) ? 7 : days;
        LocalDate end = LocalDate.now().plusDays(1);   // 排除边界：到明天 0 点
        LocalDate start = end.minusDays(n);

        List<Map<String, Object>> rows = statsMapper.dailyTrend(start, end);
        Map<LocalDate, BigDecimal> chargeMap = fetchDailyCharges(start, end);

        for (Map<String, Object> row : rows) {
            Object d = row.get("date");
            if (d instanceof java.sql.Date sqlDate) {
                BigDecimal amt = chargeMap.getOrDefault(sqlDate.toLocalDate(), BigDecimal.ZERO);
                row.put("charges", amt);
            } else if (d instanceof LocalDate ld) {
                BigDecimal amt = chargeMap.getOrDefault(ld, BigDecimal.ZERO);
                row.put("charges", amt);
            }
        }
        return rows;
    }

    /**
     * KPI 汇总（今日）
     */
    public Map<String, Object> kpiSummary() {
        return statsMapper.kpiSummary(LocalDate.now());
    }

    /**
     * 通过 Feign 拉 payment-service 的每日收费额，按日期索引。
     * 失败时返回空 Map（charges 列退化为 0），不阻塞统计接口。
     */
    @SuppressWarnings("unchecked")
    private Map<LocalDate, BigDecimal> fetchDailyCharges(LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        try {
            Map<String, Object> resp = paymentFeignClient.dailyCharges(start, end);
            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Object d = m.get("date");
                    Object amt = m.get("amount");
                    LocalDate key = toLocalDate(d);
                    if (key != null && amt != null) {
                        result.put(key, new BigDecimal(amt.toString()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Feign 调 payment.dailyCharges 失败，charges 退化为 0 | err={}", e.getMessage());
        }
        return result;
    }

    private LocalDate toLocalDate(Object d) {
        if (d instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (d instanceof LocalDate ld) return ld;
        if (d instanceof java.util.Date udt) {
            return new java.sql.Timestamp(udt.getTime()).toLocalDateTime().toLocalDate();
        }
        if (d instanceof String s) return LocalDate.parse(s);
        return null;
    }
}

