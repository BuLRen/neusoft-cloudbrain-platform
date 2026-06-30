package com.xikang.registration.service;

import com.xikang.registration.mapper.AdminDrugMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDrugService {

    private final AdminDrugMapper adminDrugMapper;

    public Map<String, Object> listCatalog(
        String keyword,
        String dosageForm,
        String category,
        Integer page,
        Integer size
    ) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        int offset = (currentPage - 1) * pageSize;
        long total = adminDrugMapper.countCatalog(keyword, dosageForm, category);
        var records = adminDrugMapper.selectCatalog(keyword, dosageForm, category, offset, pageSize);
        var result = new LinkedHashMap<String, Object>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public List<String> listCategories() {
        return adminDrugMapper.selectCategories();
    }
}
