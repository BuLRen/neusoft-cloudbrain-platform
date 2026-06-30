package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.AdminDrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/drugs")
@RequiredArgsConstructor
public class AdminDrugController {

    private final AdminDrugService adminDrugService;

    @GetMapping
    public Result<Map<String, Object>> listDrugs(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dosageForm,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminDrugService.listCatalog(keyword, dosageForm, category, page, size));
    }

    @GetMapping("/categories")
    public Result<List<String>> listCategories() {
        return Result.success(adminDrugService.listCategories());
    }
}
