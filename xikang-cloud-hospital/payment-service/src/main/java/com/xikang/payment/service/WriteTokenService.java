package com.xikang.payment.service;

import com.xikang.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 写令牌服务（v3.2 §4.2）：基于 Redis SET NX EX 30s 的跨服务写者互斥。
 *
 * 使用场景：registration-service 的 ChargeService.charge（收费员集中收费）在写 expense_record 前
 * 必须先申请令牌；若已被 payment-service 持有（说明患者正在自助支付）则返回 409。
 *
 * 注意：payment-service 内部的 payItem 不走令牌（行级锁 FOR UPDATE + 二次校验已足够防患者侧并发）。
 * 令牌仅用于跨服务协调。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteTokenService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "payment:write-token:";

    /**
     * 申请写令牌。
     *
     * @return true 拿到令牌；false 表示已被其他持有者占用。
     */
    public boolean tryAcquire(Long registerId, String holder, int ttlSeconds) {
        String key = KEY_PREFIX + registerId;
        String value = holder + "|" + System.currentTimeMillis();
        Boolean ok = redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        log.debug("write-token tryAcquire | registerId={}, holder={}, ok={}", registerId, holder, ok);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 查询当前持有者（用于 409 响应里告诉客户端"谁在用"）。
     */
    public String currentHolder(Long registerId) {
        String value = redis.opsForValue().get(KEY_PREFIX + registerId);
        if (value == null) return null;
        int idx = value.indexOf('|');
        return idx > 0 ? value.substring(0, idx) : value;
    }

    /**
     * 主动释放令牌。
     * 安全检查：仅当 value 仍属于本 holder 时才删（避免误删过期后他人持有的令牌）。
     */
    public void release(Long registerId, String holder) {
        String key = KEY_PREFIX + registerId;
        String value = redis.opsForValue().get(key);
        if (value != null && value.startsWith(holder + "|")) {
            redis.delete(key);
            log.debug("write-token released | registerId={}, holder={}", registerId, holder);
        }
    }

    /**
     * 申请令牌，失败则抛 409。
     */
    public void acquireOrThrow(Long registerId, String holder, int ttlSeconds) {
        if (!tryAcquire(registerId, holder, ttlSeconds)) {
            String current = currentHolder(registerId);
            throw new BusinessException(409,
                    "该挂号正在被 " + (current != null ? current : "其他流程") + " 处理，请稍后再试");
        }
    }
}
