-- WF-MedTech v2.0：模拟检查/检验配置表（Dify 工作流 HTTP 拉取）
-- 可重复执行（幂等）

CREATE TABLE IF NOT EXISTS simulation_config (
    id                  SERIAL PRIMARY KEY,
    config_key          VARCHAR(64)  NOT NULL UNIQUE,
    tech_code           VARCHAR(64)  DEFAULT NULL,
    check_name          VARCHAR(128) NOT NULL,
    match_keywords      TEXT         DEFAULT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    simulation_mode     VARCHAR(32)  NOT NULL DEFAULT 'lab_items',
    version             INTEGER      NOT NULL DEFAULT 1,
    prompt_sections     JSONB        NOT NULL,
    disease_mappings    JSONB        NOT NULL DEFAULT '[]',
    output_schema       JSONB        NOT NULL,
    defaults            JSONB        NOT NULL DEFAULT '{}',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_simulation_config_tech_code ON simulation_config (tech_code);
CREATE INDEX IF NOT EXISTS idx_simulation_config_check_name ON simulation_config (check_name);

-- 通用 output_schema
-- 种子数据：8 类检验（XCG/NCG/LFT/RFT/TFT/Coag/Stool/CRP）

INSERT INTO simulation_config (
    config_key, tech_code, check_name, match_keywords, enabled, simulation_mode, version,
    prompt_sections, disease_mappings, output_schema, defaults
) VALUES
(
    'XCG', 'XCG', '血常规', '血常规,CBC,全血细胞计数', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据血常规项目生成逼真的模拟数据。",
      "scope": "仅模拟血常规（CBC），禁止输出 CT、B 超、尿常规等其他检查内容。",
      "itemCatalog": "WBC 白细胞计数、RBC 红细胞计数、HGB 血红蛋白、HCT 红细胞压积、PLT 血小板计数、NEUT% 中性粒细胞百分比、LYMPH% 淋巴细胞百分比、MONO% 单核细胞百分比、EO% 嗜酸性粒细胞百分比、BASO% 嗜碱性粒细胞百分比",
      "referenceRanges": "WBC 3.5-9.5×10^9/L；RBC 男 4.3-5.8、女 3.8-5.1×10^12/L；HGB 男 130-175、女 115-150 g/L；HCT 男 40-50、女 36-44%；PLT 125-350×10^9/L；NEUT% 40-75%；LYMPH% 20-50%；MONO% 3-10%；EO% 0.4-8%；BASO% 0-1%",
      "normalRules": "所有指标 value 落在参考范围内，status 全部为 normal，meaning 描述指标在正常范围。",
      "abnormalRules": "至少 2 项、至多 5 项 status 为 high 或 low，其余为 normal。数值须与 status 一致。conclusion 写模拟倾向，不给确诊。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 每项含 itemName, itemCode, value, unit, referenceRange, status, meaning。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["细菌", "肺炎", "化脓"], "hint": "WBC↑ NEUT%↑", "priority": 1},
      {"keywords": ["病毒", "流感", "上呼吸道"], "hint": "LYMPH%↑ WBC 正常或偏低", "priority": 2},
      {"keywords": ["过敏", "寄生虫"], "hint": "EO%↑", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"血常规模拟结果未见明显异常"}'::jsonb
),
(
    'NCG', 'NCG', '尿常规', '尿常规,尿液分析', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据尿常规项目生成逼真的模拟数据。",
      "scope": "仅模拟尿常规，禁止输出其他检查内容。",
      "itemCatalog": "PRO 尿蛋白、GLU 尿糖、KET 尿酮体、BIL 尿胆红素、URO 尿胆原、BLD 尿潜血、LEU 尿白细胞、NIT 亚硝酸盐、SG 尿比重、pH 尿酸碱度",
      "referenceRanges": "PRO/GLU/KET/BIL/LEU/NIT 阴性(-)；URO 正常或弱阳性；BLD 阴性；SG 1.003-1.030；pH 4.5-8.0",
      "normalRules": "定性项为阴性或正常，status 为 normal；定量项在参考范围。",
      "abnormalRules": "至少 2 项 status 为 high、positive 或 abnormal，其余 normal。蛋白、潜血、白细胞等可阳性。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 允许 normal/high/low/positive/abnormal。"
    }'::jsonb,
    '[
      {"keywords": ["泌尿", "感染", "膀胱炎"], "hint": "LEU+ NIT+ PRO±", "priority": 1},
      {"keywords": ["糖尿病", "血糖"], "hint": "GLU+ KET±", "priority": 2},
      {"keywords": ["肝胆", "黄疸"], "hint": "BIL+ URO↑", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"尿常规模拟结果未见明显异常"}'::jsonb
),
(
    'LFT', 'LFT', '肝功能', '肝功能,肝功', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据肝功能项目生成逼真的模拟数据。",
      "scope": "仅模拟肝功能，禁止输出其他检查内容。",
      "itemCatalog": "ALT 丙氨酸氨基转移酶、AST 天门冬氨酸氨基转移酶、ALP 碱性磷酸酶、GGT γ-谷氨酰转移酶、TBIL 总胆红素、DBIL 直接胆红素、IBIL 间接胆红素、ALB 白蛋白、TP 总蛋白",
      "referenceRanges": "ALT 7-40 U/L；AST 13-35 U/L；ALP 45-125 U/L；GGT 7-45 U/L；TBIL 3.4-17.1 μmol/L；DBIL 0-6.8 μmol/L；ALB 40-55 g/L；TP 65-85 g/L",
      "normalRules": "所有酶学及胆红素指标在参考范围内，status 为 normal。",
      "abnormalRules": "至少 2 项偏高或偏低，肝细胞损伤可 ALT/AST↑，胆道梗阻可 ALP/GGT/TBIL↑，其余 normal。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["肝炎", "肝损伤"], "hint": "ALT↑ AST↑", "priority": 1},
      {"keywords": ["胆道", "梗阻", "黄疸"], "hint": "TBIL↑ DBIL↑ ALP↑ GGT↑", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"肝功模拟结果未见明显异常"}'::jsonb
),
(
    'RFT', 'RFT', '肾功能', '肾功能,肾功', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据肾功能项目生成逼真的模拟数据。",
      "scope": "仅模拟肾功能，禁止输出其他检查内容。",
      "itemCatalog": "BUN 尿素氮、CREA 肌酐、UA 尿酸、eGFR 估算肾小球滤过率",
      "referenceRanges": "BUN 2.9-8.2 mmol/L；CREA 男 57-97、女 41-81 μmol/L；UA 男 208-428、女 155-357 μmol/L；eGFR ≥90 mL/min/1.73m²",
      "normalRules": "所有指标在参考范围内，status 为 normal。",
      "abnormalRules": "至少 2 项异常，肾功能减退可 BUN↑ CREA↑ eGFR↓，高尿酸可 UA↑，其余 normal。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["肾衰", "肾功能不全"], "hint": "BUN↑ CREA↑ eGFR↓", "priority": 1},
      {"keywords": ["痛风", "高尿酸"], "hint": "UA↑", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"肾功模拟结果未见明显异常"}'::jsonb
),
(
    'TFT', 'TFT', '甲状腺功能', '甲状腺,甲功,TSH,FT3,FT4', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据甲状腺功能项目生成逼真的模拟数据。",
      "scope": "仅模拟甲状腺功能，禁止输出其他检查内容。",
      "itemCatalog": "TSH 促甲状腺激素、FT3 游离三碘甲状腺原氨酸、FT4 游离甲状腺素、T3 三碘甲状腺原氨酸、T4 甲状腺素",
      "referenceRanges": "TSH 0.27-4.2 mIU/L；FT3 3.1-6.8 pmol/L；FT4 12-22 pmol/L；T3 1.3-3.1 nmol/L；T4 66-181 nmol/L",
      "normalRules": "所有激素指标在参考范围内，status 为 normal。",
      "abnormalRules": "至少 2 项异常，甲亢可 TSH↓ FT3/FT4↑，甲减可 TSH↑ FT3/FT4↓，其余 normal。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["甲亢", "Graves"], "hint": "TSH↓ FT3↑ FT4↑", "priority": 1},
      {"keywords": ["甲减", "桥本"], "hint": "TSH↑ FT3↓ FT4↓", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"甲状腺功能模拟结果未见明显异常"}'::jsonb
),
(
    'Coag', 'Coag', '凝血功能', '凝血,凝血功能,凝血四项', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据凝血功能项目生成逼真的模拟数据。",
      "scope": "仅模拟凝血功能，禁止输出其他检查内容。",
      "itemCatalog": "PT 凝血酶原时间、INR 国际标准化比值、APTT 活化部分凝血活酶时间、TT 凝血酶时间、FIB 纤维蛋白原、D-Dimer D-二聚体",
      "referenceRanges": "PT 11-14.5 s；INR 0.8-1.2；APTT 25-35 s；TT 14-21 s；FIB 2-4 g/L；D-Dimer <0.5 mg/L",
      "normalRules": "所有凝血指标在参考范围内，status 为 normal。",
      "abnormalRules": "至少 2 项异常，高凝可 D-Dimer↑，出血倾向可 PT/APTT 延长 FIB↓，其余 normal。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["血栓", "肺栓塞", "DVT"], "hint": "D-Dimer↑", "priority": 1},
      {"keywords": ["出血", "凝血障碍"], "hint": "PT↑ APTT↑ FIB↓", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"凝血功能模拟结果未见明显异常"}'::jsonb
),
(
    'Stool', 'Stool', '粪便常规', '粪便常规,大便常规', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据粪便常规项目生成逼真的模拟数据。",
      "scope": "仅模拟粪便常规，禁止输出其他检查内容。",
      "itemCatalog": "性状、颜色、OB 隐血、WBC 白细胞、RBC 红细胞、寄生虫卵、脂肪滴",
      "referenceRanges": "性状成形软便；颜色黄褐色；OB 阴性；WBC/RBC 未见；寄生虫卵未见",
      "normalRules": "所有项目正常，定性项 status 为 normal。",
      "abnormalRules": "至少 2 项 status 为 high、positive 或 abnormal，如隐血阳性、白细胞增多、稀便等，其余 normal。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 允许 normal/high/low/positive/abnormal。"
    }'::jsonb,
    '[
      {"keywords": ["消化道出血", "溃疡"], "hint": "OB阳性", "priority": 1},
      {"keywords": ["肠炎", "感染"], "hint": "WBC↑ 稀便", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"粪便常规模拟结果未见明显异常"}'::jsonb
),
(
    'CRP', 'CRP', 'C反应蛋白', 'C反应蛋白,CRP,超敏CRP', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据 C 反应蛋白项目生成逼真的模拟数据。",
      "scope": "仅模拟 C 反应蛋白，禁止输出其他检查内容。",
      "itemCatalog": "CRP C反应蛋白",
      "referenceRanges": "CRP <10 mg/L（一般参考）；hs-CRP <3 mg/L",
      "normalRules": "CRP 在参考范围内，status 为 normal，可不输出 inflammationLevel 或设为 normal。",
      "abnormalRules": "CRP status 为 high，根据数值设置 inflammationLevel：10-40 mild，40-100 moderate，100-200 marked，>200 severe。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice, inflammationLevel（可选）。不要输出 isNormal。status 仅允许 normal/high。"
    }'::jsonb,
    '[
      {"keywords": ["细菌", "感染", "肺炎"], "hint": "CRP 明显升高 moderate-marked", "priority": 1},
      {"keywords": ["病毒"], "hint": "CRP 轻度升高 mild", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"C反应蛋白模拟结果未见明显异常"}'::jsonb
)
ON CONFLICT (config_key) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('simulation_config', 'id'),
    COALESCE((SELECT MAX(id) FROM simulation_config), 1),
    TRUE
);
