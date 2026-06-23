package com.xikang.registration.service;

import com.xikang.registration.entity.SettleCategory;
import com.xikang.registration.mapper.SettleCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SettleCategory Service - 结算类别服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettleCategoryService {

    private final SettleCategoryMapper settleCategoryMapper;

    /**
     * 获取所有结算类别
     */
    public List<SettleCategory> getAllCategories() {
        return settleCategoryMapper.selectAll();
    }

    /**
     * 根据ID获取结算类别
     */
    public SettleCategory getCategory(Long id) {
        return settleCategoryMapper.selectById(id);
    }

    /**
     * 根据代码获取结算类别
     */
    public SettleCategory getCategoryByCode(String code) {
        return settleCategoryMapper.selectByCode(code);
    }

    /**
     * 创建结算类别
     */
    public SettleCategory createCategory(SettleCategory category) {
        // delmark 在 Mapper INSERT 中自动设为 0
        settleCategoryMapper.insert(category);
        log.info("创建结算类别: id={}, name={}", category.getId(), category.getName());
        return category;
    }

    /**
     * 更新结算类别
     */
    public void updateCategory(Long id, SettleCategory category) {
        category.setId(id);
        settleCategoryMapper.update(category);
        log.info("更新结算类别: id={}", id);
    }
}
