package com.xikang.registration.service;

import com.xikang.registration.entity.RegistLevel;
import com.xikang.registration.mapper.RegistLevelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RegistLevel Service - 挂号级别服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistLevelService {

    private final RegistLevelMapper registLevelMapper;

    /**
     * 获取所有挂号级别
     */
    public List<RegistLevel> getAllLevels() {
        return registLevelMapper.selectAll();
    }

    /**
     * 根据ID获取挂号级别
     */
    public RegistLevel getLevel(Long id) {
        return registLevelMapper.selectById(id);
    }

    /**
     * 创建挂号级别
     */
    public RegistLevel createLevel(RegistLevel level) {
        level.setStatus(1);
        registLevelMapper.insert(level);
        log.info("创建挂号级别: id={}, name={}", level.getId(), level.getName());
        return level;
    }

    /**
     * 更新挂号级别
     */
    public void updateLevel(Long id, RegistLevel level) {
        level.setId(id);
        registLevelMapper.update(level);
        log.info("更新挂号级别: id={}", id);
    }

    /**
     * 删除挂号级别
     */
    public void deleteLevel(Long id) {
        registLevelMapper.deleteById(id);
        log.info("删除挂号级别: id={}", id);
    }
}
