-- =============================================================================
-- 补全 drug_info 表中 id=1/2/3 三条老药品的说明书字段
--
-- 背景：init.sql 中最早插入的 3 条药品（阿司匹林、布洛芬、阿莫西林）
-- 只写了基础字段，instructions / contraindications / adverse_reactions /
-- storage_conditions 全为 NULL，导致前端"查看用药说明"按钮点击后无内容。
--
-- 本脚本只 UPDATE 这 3 条，不影响其它药品（SEED010+ 的种子数据已完整）。
-- 可重复执行（幂等）：再跑一次也是同样的值。
-- =============================================================================

UPDATE drug_info SET
    storage_conditions = '密封，阴凉干燥处保存',
    instructions = '口服，每日一次，每次 100mg（1 片）。肠溶片不可掰开或咀嚼，建议饭后服用以减少胃肠道刺激。',
    contraindications = '活动性消化道溃疡禁用；血友病或出血倾向禁用；妊娠期最后三个月禁用；对水杨酸类过敏禁用。',
    adverse_reactions = '长期大剂量可见胃肠道出血、溃疡；少见皮疹、哮喘发作；罕见肝肾损害。'
WHERE id = 1;

UPDATE drug_info SET
    storage_conditions = '密封，不超过 25℃ 保存',
    instructions = '口服，整粒吞服，每日 2 次，每次 1 粒（0.3g）。缓释胶囊不可掰开或咀嚼。',
    contraindications = '活动性消化道溃疡/出血禁用；严重心功能不全禁用；妊娠期晚期禁用；对布洛芬过敏禁用；正在使用其他 NSAID 者禁用。',
    adverse_reactions = '常见恶心、胃部不适、头晕；少见皮疹、水肿；长期使用可能增加心血管事件风险。'
WHERE id = 2;

UPDATE drug_info SET
    storage_conditions = '密封，室温保存',
    instructions = '口服，成人每日 3~4 次，每次 0.5g（2 粒）。肾功能不全者需调整剂量。用药期间及停药后 3 天内禁止饮酒（双硫仑样反应）。',
    contraindications = '青霉素过敏者禁用；传染性单核细胞增多症患者禁用（易发皮疹）；严重肾功能不全慎用。',
    adverse_reactions = '常见皮疹、瘙痒、腹泻；少见恶心、呕吐；罕见过敏性休克（用药前需皮试）。'
WHERE id = 3;

-- 校验：确认 3 条药品的说明书字段已写入
SELECT id, drug_name,
       CASE WHEN instructions IS NULL OR instructions = '' THEN '缺' ELSE '有' END AS 用法,
       CASE WHEN contraindications IS NULL OR contraindications = '' THEN '缺' ELSE '有' END AS 禁忌,
       CASE WHEN adverse_reactions IS NULL OR adverse_reactions = '' THEN '缺' ELSE '有' END AS 不良反应
FROM drug_info
WHERE id IN (1, 2, 3)
ORDER BY id;
