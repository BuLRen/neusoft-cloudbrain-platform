-- 扩展模拟检查/检验配置：常见项目（不含既有 8 类检验种子）
-- 可重复执行（幂等）：ON CONFLICT (config_key) DO NOTHING

INSERT INTO simulation_config (
    config_key, tech_code, check_name, match_keywords, enabled, simulation_mode, version,
    prompt_sections, disease_mappings, output_schema, defaults
) VALUES
(
    'FLU_AG', 'FLU_AG', '流感病毒抗原检测', '流感,流感病毒,抗原检测,Flu A,Flu B,甲乙流', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据呼吸道病毒抗原快速检测结果生成逼真的模拟数据。",
      "scope": "仅模拟流感病毒抗原检测（甲乙型流感抗原），禁止输出血常规、CT、培养鉴定等其他检查内容。",
      "itemCatalog": "FluA 甲型流感病毒抗原、FluB 乙型流感病毒抗原、检测方法（胶体金/免疫层析）、标本类型（鼻咽拭子/咽拭子）",
      "referenceRanges": "FluA/FluB 抗原：阴性(-) 为正常；阳性(+) 为检出",
      "normalRules": "FluA、FluB 均为阴性，status 为 normal，meaning 描述未检出流感抗原。",
      "abnormalRules": "至少 1 项抗原阳性（status 为 positive），另一项可阴性；若可能疾病提示流感，优先 FluA 或 FluB 阳性，与季节/流行病学相符；不得两项均阴性却写流感阳性结论。conclusion 写「抗原检测模拟倾向」，不给确诊。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 每项含 itemName, itemCode, value, unit, referenceRange, status, meaning。定性项 status 允许 normal/positive/negative。"
    }'::jsonb,
    '[
      {"keywords": ["流感", "甲流", "H1N1", "H3N2"], "hint": "FluA 阳性 FluB 阴性", "priority": 1},
      {"keywords": ["乙流", "流感"], "hint": "FluB 阳性 FluA 阴性", "priority": 2},
      {"keywords": ["上呼吸道", "发热", "咳嗽"], "hint": "至少一项抗原阳性", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"流感病毒抗原检测模拟结果阴性，未检出甲乙型流感抗原"}'::jsonb
),
(
    'THROAT_EXAM', 'THROAT_EXAM', '咽部检查', '咽部检查,咽喉检查,口咽检查,扁桃体检查', TRUE, 'general_check', 1,
    '{
      "role": "你是门诊体格检查模拟助手，根据咽部视诊结果生成结构化的模拟检查记录。",
      "scope": "仅模拟咽部/口咽视诊与相关体征，禁止输出实验室检验、影像报告或下咽部内镜详细描述。",
      "itemCatalog": "咽部黏膜色泽、咽后壁淋巴滤泡、扁桃体大小与充血、扁桃体分泌物/脓点、悬雍垂、会厌可见部分、颈部淋巴结（自述/间接）、吞咽不适关联体征",
      "referenceRanges": "黏膜淡红湿润；扁桃体 I 度以内；无充血肿胀；无脓性分泌物；淋巴滤泡不增生；未见溃疡及假膜",
      "normalRules": "所有体征项 status 为 normal，value 描述未见明显异常，meaning 说明符合正常口咽表现。",
      "abnormalRules": "至少 2 项、至多 5 项 status 为 abnormal 或 positive，如充血、肿大、脓苔、滤泡增生等，与其余 normal 项并存；结论写「查体模拟倾向」，不给确诊。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 每项含 itemName, itemCode, value, referenceRange, status, meaning。status 允许 normal/abnormal/positive。"
    }'::jsonb,
    '[
      {"keywords": ["咽炎", "扁桃体", "化脓", "链球菌"], "hint": "扁桃体充血肿大 II度 脓点+", "priority": 1},
      {"keywords": ["病毒", "流感", "上呼吸道"], "hint": "黏膜充血 咽后壁滤泡增生 扁桃体轻度肿大", "priority": 2},
      {"keywords": ["过敏", "鼻后滴漏"], "hint": "咽后壁黏液附着 黏膜轻度充血", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"咽部检查模拟结果未见明显异常"}'::jsonb
),
(
    'RAPID_STREP', 'RAPID_STREP', '快速链球菌检测', '快速链球菌,链球菌抗原,A组链球菌,链球菌快速检测,strep', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据 A 组链球菌快速抗原检测结果生成逼真的模拟数据。",
      "scope": "仅模拟咽拭子 A 组β溶血性链球菌快速抗原检测，禁止输出培养药敏、血常规等其他内容。",
      "itemCatalog": "GAS_Ag A组链球菌抗原、检测方法（免疫层析）、标本类型（咽拭子）",
      "referenceRanges": "GAS_Ag：阴性(-) 为正常；阳性(+) 为检出",
      "normalRules": "GAS_Ag 阴性，status 为 normal，meaning 描述未检出 A 组链球菌抗原。",
      "abnormalRules": "GAS_Ag 阳性，status 为 positive；若可能疾病含化脓性扁桃体炎/链球菌感染，应与之相符；不得阴性结论却提示链球菌感染。conclusion 写模拟倾向，不给确诊。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 允许 normal/positive/negative。"
    }'::jsonb,
    '[
      {"keywords": ["链球菌", "化脓性扁桃体", "咽痛", "发热"], "hint": "GAS_Ag 阳性", "priority": 1},
      {"keywords": ["病毒", "流感"], "hint": "GAS_Ag 阴性", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"快速链球菌检测模拟结果阴性"}'::jsonb
),
(
    'SPUTUM_CULT', 'SPUTUM_CULT', '痰培养', '痰培养,呼吸道培养,细菌培养,痰标本培养', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学微生物检验模拟助手，根据痰标本培养结果生成逼真的模拟报告。",
      "scope": "仅模拟痰培养及常见致病菌鉴定，禁止输出药敏全部明细以外的无关检验。",
      "itemCatalog": "培养结果（细菌生长情况）、优势菌/致病菌、菌量（少量/中等/大量）、革兰染色（若报告）、正常菌群干扰说明",
      "referenceRanges": "合格痰标本：正常菌群生长或无菌生长为常见正常/阴性；致病菌中-大量生长为异常",
      "normalRules": "培养未见致病菌或仅正常菌群少量生长，status 为 normal，meaning 描述未分离到明确致病菌。",
      "abnormalRules": "至少报告 1 种致病菌中-大量生长（status 为 positive 或 abnormal），可与可能疾病一致（肺炎链球菌、流感嗜血杆菌、卡他莫拉菌、金黄色葡萄球菌、肺炎克雷伯菌等）；可注明「建议药敏」但不展开完整药敏表。conclusion 写培养模拟倾向。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 含 itemName, itemCode, value, referenceRange, status, meaning。status 允许 normal/positive/abnormal。"
    }'::jsonb,
    '[
      {"keywords": ["肺炎", "细菌", "咳脓痰"], "hint": "肺炎链球菌或流感嗜血杆菌 中量生长", "priority": 1},
      {"keywords": ["支气管扩张", "慢性感染"], "hint": "铜绿假单胞菌 大量生长", "priority": 2},
      {"keywords": ["病毒", "支原体"], "hint": "正常菌群 未分离致病菌", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"痰培养模拟结果未分离到明确致病菌"}'::jsonb
),
(
    'MP_AB', 'MP_AB', '肺炎支原体抗体', '肺炎支原体,支原体抗体,MP-IgM,MP-IgG', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据肺炎支原体血清抗体检测结果生成逼真的模拟数据。",
      "scope": "仅模拟肺炎支原体 IgM/IgG 抗体，禁止输出其他病原体抗体或影像报告。",
      "itemCatalog": "MP-IgM 肺炎支原体 IgM 抗体、MP-IgG 肺炎支原体 IgG 抗体、检测方法（胶体金/ELISA）",
      "referenceRanges": "IgM/IgG：<1:80 或 阴性(-) 为参考（按报告单位）；阳性(+) 或滴度升高为异常",
      "normalRules": "IgM、IgG 均在阴性或低滴度范围，status 为 normal。",
      "abnormalRules": "IgM 阳性或滴度升高提示近期感染；IgG 单独升高可提示既往感染；至少 1 项 abnormal/positive，与急性支原体肺炎倾向一致时 IgM 应阳性。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 允许 normal/positive/high。"
    }'::jsonb,
    '[
      {"keywords": ["支原体", "肺炎", "久咳"], "hint": "MP-IgM 阳性 IgG 可阳性", "priority": 1},
      {"keywords": ["儿童", "社区获得性肺炎"], "hint": "IgM 阳性", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"肺炎支原体抗体模拟结果未见急性感染征象"}'::jsonb
),
(
    'HBA1C', 'HBA1C', '糖化血红蛋白', '糖化血红蛋白,HbA1c,HbA1C,血红蛋白A1c', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据糖化血红蛋白（HbA1c）检测结果生成逼真的模拟数据。",
      "scope": "仅模拟 HbA1c，禁止输出空腹血糖、OGTT 等其他血糖相关项目。",
      "itemCatalog": "HbA1c 糖化血红蛋白",
      "referenceRanges": "HbA1c 4.0-6.0%（非糖尿病参考）；6.0-6.4% 糖尿病前期；≥6.5% 提示糖尿病控制或诊断需结合临床",
      "normalRules": "HbA1c 在 4.0-6.0%，status 为 normal。",
      "abnormalRules": "HbA1c status 为 high，6.1-7.0% 轻度升高，7.0-8.5% 中度，>8.5% 明显升高；与糖尿病/血糖控制不佳疾病倾向一致。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 允许 normal/high。"
    }'::jsonb,
    '[
      {"keywords": ["糖尿病", "血糖高", "2型糖尿病"], "hint": "HbA1c 7-9%", "priority": 1},
      {"keywords": ["前期", "空腹血糖受损"], "hint": "HbA1c 6.0-6.4%", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"糖化血红蛋白模拟结果在参考范围内"}'::jsonb
),
(
    'ELYT', 'ELYT', '电解质', '电解质,钾钠氯,血电解质,K Na Cl', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据血清电解质结果生成逼真的模拟数据。",
      "scope": "仅模拟电解质（K、Na、Cl、Ca、Mg 等），禁止输出肾功能、血气等其他项目。",
      "itemCatalog": "K 钾、Na 钠、Cl 氯、Ca 钙、Mg 镁、CO2 二氧化碳结合力（或 HCO3）",
      "referenceRanges": "K 3.5-5.5 mmol/L；Na 135-145 mmol/L；Cl 96-106 mmol/L；Ca 2.10-2.60 mmol/L；Mg 0.75-1.02 mmol/L",
      "normalRules": "所有电解质在参考范围内，status 为 normal。",
      "abnormalRules": "至少 2 项 high 或 low，如低钾、低钠、高钾等与脱水、肾衰、利尿剂、呕吐等倾向相符；数值与 status 一致。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["呕吐", "腹泻", "脱水"], "hint": "Na↓ K↓ 或正常", "priority": 1},
      {"keywords": ["肾衰", "肾功能不全"], "hint": "K↑", "priority": 2},
      {"keywords": ["利尿剂", "低钾"], "hint": "K↓", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"电解质模拟结果未见明显异常"}'::jsonb
),
(
    'LIPID', 'LIPID', '血脂四项', '血脂,血脂四项,胆固醇,甘油三酯,LDL,HDL', TRUE, 'lab_items', 1,
    '{
      "role": "你是医学检验结果模拟助手，根据血脂四项结果生成逼真的模拟数据。",
      "scope": "仅模拟血脂（TC、TG、LDL-C、HDL-C），禁止输出肝功能、血糖等其他项目。",
      "itemCatalog": "TC 总胆固醇、TG 甘油三酯、LDL-C 低密度脂蛋白胆固醇、HDL-C 高密度脂蛋白胆固醇",
      "referenceRanges": "TC <5.2 mmol/L；TG <1.7 mmol/L；LDL-C <3.4 mmol/L；HDL-C 男 >1.0、女 >1.3 mmol/L",
      "normalRules": "四项均在参考范围，status 为 normal。",
      "abnormalRules": "至少 2 项 high 或 low（HDL 偏低算 low），高脂血症倾向 TC↑ LDL↑ TG↑，HDL 可偏低；数值与 status 一致。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。status 仅允许 normal/high/low。"
    }'::jsonb,
    '[
      {"keywords": ["高脂血症", "动脉硬化", "冠心病"], "hint": "TC↑ LDL↑ TG↑ HDL↓", "priority": 1},
      {"keywords": ["肥胖", "代谢综合征"], "hint": "TG↑ LDL↑", "priority": 2}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"血脂四项模拟结果未见明显异常"}'::jsonb
),
(
    'CXR', 'CXR', '胸部X线', '胸部X线,胸片,胸部摄片,DR胸片', TRUE, 'general_check', 1,
    '{
      "role": "你是医学影像报告模拟助手，根据胸部 X 线（胸片）生成结构化的模拟影像所见。",
      "scope": "仅模拟胸部 X 线平片文字报告，禁止输出 CT、MRI 或实验室检验。",
      "itemCatalog": "胸廓对称性、气管居中、肺野透亮度、肺纹理、肺门、心影大小形态、主动脉、膈面、肋膈角、软组织及骨骼所见",
      "referenceRanges": "肺野清晰，心影不大，膈面光滑，肋膈角锐利，未见明显实质性病变及胸腔积液",
      "normalRules": "各所见项 status 为 normal，描述未见明显异常。",
      "abnormalRules": "至少 2 项 abnormal，如斑片影、纹理增粗、心影增大、肋膈角钝等，与肺炎、支气管炎、心衰倾向等一致；结论写影像模拟倾向，不给确诊。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 含 itemName, itemCode, value, status, meaning。status 允许 normal/abnormal。"
    }'::jsonb,
    '[
      {"keywords": ["肺炎", "感染", "咳嗽"], "hint": "肺野斑片影 纹理增粗", "priority": 1},
      {"keywords": ["支气管炎", "COPD"], "hint": "肺纹理增粗 肺气肿征象", "priority": 2},
      {"keywords": ["心衰", "积液"], "hint": "心影增大 肋膈角钝", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"胸部X线模拟结果未见明显异常"}'::jsonb
),
(
    'ECG', 'ECG', '心电图', '心电图,ECG,EKG,十二导联心电图', TRUE, 'general_check', 1,
    '{
      "role": "你是心电图报告模拟助手，根据常规 12 导联心电图生成结构化的模拟分析结果。",
      "scope": "仅模拟心电图测量与描述，禁止输出超声、实验室检验或 CT。",
      "itemCatalog": "心率、节律、P 波、PR 间期、QRS 时限、QT/QTc、电轴、ST 段、T 波、U 波、各导联描述",
      "referenceRanges": "窦性心律 60-100 次/分；PR 120-200 ms；QRS <120 ms；QTc 男<450 ms、女<470 ms；ST 段无抬高压低；T 波与主波方向一致",
      "normalRules": "各项测量在参考范围，节律为窦性，status 主要为 normal。",
      "abnormalRules": "至少 2 项 abnormal 或 high/low，如窦速、窦缓、ST-T 改变、左室高电压、房颤等之一与可能疾病倾向一致；不得全部 normal 却描述明显心律失常。",
      "outputFormat": "输出 JSON：checkName, simulatedForDiseases, resultItems, conclusion, notice。不要输出 isNormal。resultItems 含 itemName, itemCode, value, unit, referenceRange, status, meaning。"
    }'::jsonb,
    '[
      {"keywords": ["冠心病", "心肌缺血", "胸痛"], "hint": "ST 段压低 T 波低平", "priority": 1},
      {"keywords": ["房颤", "心悸"], "hint": "心房颤动 绝对不齐", "priority": 2},
      {"keywords": ["电解质紊乱", "高钾"], "hint": "T 波高尖 QRS 增宽", "priority": 3}
    ]'::jsonb,
    '{"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}'::jsonb,
    '{"notice":"本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。","normalConclusion":"心电图模拟结果大致正常"}'::jsonb
)
ON CONFLICT (config_key) DO NOTHING;
