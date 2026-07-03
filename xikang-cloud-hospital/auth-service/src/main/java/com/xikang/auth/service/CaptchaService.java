package com.xikang.auth.service;

import com.wf.captcha.SpecCaptcha;
import com.xikang.auth.dto.CaptchaResponse;
import com.xikang.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";

    private final StringRedisTemplate redis;

    @Value("${captcha.ttl-seconds:180}")
    private long ttlSeconds;

    @Value("${captcha.length:4}")
    private int length;

    public CaptchaResponse generate() {
        SpecCaptcha captcha = new SpecCaptcha(112, 40, length);
        String code = captcha.text();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String imageBase64 = captcha.toBase64();

        redis.opsForValue().set(
                KEY_PREFIX + captchaId,
                normalize(code),
                Duration.ofSeconds(ttlSeconds)
        );

        return new CaptchaResponse(captchaId, imageBase64);
    }

    public void validateAndConsume(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }

        String key = KEY_PREFIX + captchaId.trim();
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BusinessException(400, "验证码已过期，请刷新");
        }

        redis.delete(key);

        if (!stored.equals(normalize(captchaCode))) {
            throw new BusinessException(400, "验证码错误");
        }
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toUpperCase();
    }
}
