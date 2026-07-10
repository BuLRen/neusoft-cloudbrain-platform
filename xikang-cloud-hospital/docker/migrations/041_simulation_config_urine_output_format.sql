-- 强化检验类 simulation_config 的 outputFormat：
-- 要求 LLM 使用 referenceRange / meaning 标准字段名（兼容尿常规、粪便常规等定性项目）
-- 可重复执行（幂等）

UPDATE simulation_config
SET
    prompt_sections = jsonb_set(
        prompt_sections,
        '{outputFormat}',
        '"输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 每项必须含 itemName, value, unit, referenceRange, status, meaning。referenceRange 填参考范围（禁止使用 reference 字段名）；meaning 填该指标的临床含义简述。status 允许 normal/high/low/positive/abnormal。"'::jsonb
    ),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE simulation_mode = 'lab_items'
  AND COALESCE(prompt_sections ->> 'outputFormat', '') NOT LIKE '%referenceRange%';
