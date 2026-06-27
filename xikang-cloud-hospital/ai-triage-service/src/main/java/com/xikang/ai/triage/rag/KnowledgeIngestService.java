package com.xikang.ai.triage.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库入库服务 —— 从医院业务表抽取知识，向量化后存入 PgVector。
 *
 * <p>仅在 {@code spring.ai.rag.enabled=true} 时生效。
 *
 * <p>知识来源（4 类）：
 * <ul>
 *   <li>科室知识 ← {@code department} 表</li>
 *   <li>疾病字典 ← {@code disease} 表</li>
 *   <li>医生专长 ← {@code employee} 表</li>
 * </ul>
 *
 * <p>调用 {@link #reload()} 后：
 * <ol>
 *   <li>按 source 标签批量删除旧向量</li>
 *   <li>从 DB 重新抽取知识文档</li>
 *   <li>调 {@link VectorStore#add(List)} 自动 embedding + 入库</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
public class KnowledgeIngestService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 全量重建知识库（先删后插）。
     *
     * @return 入库文档数
     */
    public int reload() {
        log.info("[知识库重建] 开始");
        long t0 = System.currentTimeMillis();

        // 1. 清空旧数据（直接 SQL 删，绕过 Spring AI 的表达式解析 —— 它把字符串当 SpEL 解析，单引号会报错）
        List<String> sources = List.of("department", "disease", "doctor");
        for (String src : sources) {
            try {
                // metadata 是 jsonb，->>'source' 取出 source 字段的字符串值
                int deleted = jdbcTemplate.update(
                        "DELETE FROM ai_triage_knowledge WHERE metadata->>'source' = ?",
                        src);
                log.info("[知识库重建] 删除 source={} 旧向量 {} 条", src, deleted);
            } catch (Exception e) {
                log.warn("[知识库重建] 删除 source={} 失败（可能首次入库，忽略）: {}", src, e.getMessage());
            }
        }

        // 2. 抽取并入库
        List<Document> docs = new ArrayList<>();
        docs.addAll(loadDepartments());
        docs.addAll(loadDiseases());
        docs.addAll(loadDoctors());

        if (docs.isEmpty()) {
            log.warn("[知识库重建] 抽取到 0 条文档，检查业务表是否有数据");
            return 0;
        }

        vectorStore.add(docs);
        log.info("[知识库重建] 完成，入库 {} 条文档，耗时 {} ms",
                docs.size(), System.currentTimeMillis() - t0);
        return docs.size();
    }

    /**
     * 抽取科室知识：每个科室一段文档。
     */
    private List<Document> loadDepartments() {
        String sql = "SELECT id, dept_code, dept_name, dept_type, dept_description "
                + "FROM department WHERE delmark = 0 ORDER BY id";

        return jdbcTemplate.query(sql, (rs, i) -> {
            StringBuilder content = new StringBuilder();
            content.append("科室：").append(rs.getString("dept_name")).append("\n");
            content.append("科室ID：").append(rs.getInt("id")).append("\n");
            content.append("科室编码：").append(rs.getString("dept_code")).append("\n");
            String type = rs.getString("dept_type");
            if (type != null && !type.isBlank()) {
                content.append("科室类型：").append(type).append("\n");
            }
            String desc = rs.getString("dept_description");
            if (desc != null && !desc.isBlank()) {
                content.append("科室简介：").append(desc);
            }

            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "department");
            meta.put("deptId", rs.getInt("id"));
            meta.put("deptName", rs.getString("dept_name"));
            return new Document(content.toString(), meta);
        });
    }

    /**
     * 抽取疾病知识：按科室精选典型疾病。
     *
     * <p><b>设计理由</b>：disease 字典表有 3 万+ 条 ICD 编码，全量入库会
     * 耗时数小时且 API 调用量爆炸。导诊场景真正需要的是<b>症状→科室</b>的关联，
     * 而非完整 ICD 字典。
     *
     * <p>策略：每个科室配 3-5 个最常见、最典型的疾病，作为知识库的"科室→疾病"映射。
     * 这些疾病覆盖了患者导诊时最常描述的症状，足够支撑 RAG 检索。
     *
     * <p>不依赖 disease 表的实际数据（避免 3 万条字典污染）。
     */
    private List<Document> loadDiseases() {
        // 科室ID → 该科室典型疾病列表（覆盖导诊常见症状场景）
        Map<Integer, List<String>> deptDiseases = Map.ofEntries(
                Map.entry(1,  List.of("感冒", "发热", "乏力", "慢性病复诊")),
                Map.entry(2,  List.of("咳嗽", "支气管炎", "肺炎", "哮喘", "慢阻肺")),
                Map.entry(3,  List.of("高血压", "冠心病", "心律失常", "心绞痛", "心悸")),
                Map.entry(4,  List.of("胃炎", "胃溃疡", "反流性食管炎", "腹泻", "便秘")),
                Map.entry(5,  List.of("头痛", "偏头痛", "头晕", "失眠", "脑梗死")),
                Map.entry(6,  List.of("肾炎", "尿路感染", "水肿", "肾功能异常")),
                Map.entry(7,  List.of("糖尿病", "甲状腺功能异常", "痛风", "肥胖")),
                Map.entry(8,  List.of("阑尾炎", "疝气", "体表包块", "外伤")),
                Map.entry(9,  List.of("骨折", "颈椎病", "腰椎间盘突出", "关节炎", "扭伤")),
                Map.entry(10, List.of("月经不调", "妇科炎症", "痛经", "孕期检查")),
                Map.entry(11, List.of("儿童发热", "小儿腹泻", "手足口病", "湿疹", "咳嗽")),
                Map.entry(12, List.of("新生儿黄疸", "早产儿随访", "新生儿喂养问题")),
                Map.entry(13, List.of("近视", "结膜炎", "白内障", "干眼症", "青光眼")),
                Map.entry(14, List.of("鼻炎", "咽炎", "中耳炎", "扁桃体炎", "耳鸣")),
                Map.entry(15, List.of("龋齿", "牙周炎", "牙痛", "口腔溃疡")),
                Map.entry(16, List.of("湿疹", "痤疮", "皮炎", "荨麻疹", "真菌感染")),
                Map.entry(17, List.of("中医调理", "体质调养", "慢性病中医治疗", "针灸推拿")),
                Map.entry(18, List.of("肿瘤筛查", "化疗随访", "肿瘤复查", "肿瘤症状管理")),
                Map.entry(19, List.of("急性外伤", "急性胸痛", "急性腹痛", "高热急症", "中毒")),
                Map.entry(20, List.of("术后康复", "卒中康复", "骨伤康复", "运动功能恢复"))
        );

        // 反查科室名（从 department 表，避免硬编码科室名）
        Map<Integer, String> deptNames = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, dept_name FROM department WHERE delmark = 0",
                rs -> {
                    deptNames.put(rs.getInt("id"), rs.getString("dept_name"));
                });

        List<Document> docs = new ArrayList<>();
        deptDiseases.forEach((deptId, diseases) -> {
            String deptName = deptNames.getOrDefault(deptId, "未知科室");
            for (String disease : diseases) {
                StringBuilder content = new StringBuilder();
                content.append("疾病：").append(disease).append("\n");
                content.append("所属科室：").append(deptName).append("\n");
                content.append("科室ID：").append(deptId).append("\n");
                content.append("说明：该疾病应就诊于 ").append(deptName)
                        .append("，患者描述相关症状时可推荐该科室。");

                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "disease");
                meta.put("diseaseName", disease);
                meta.put("deptId", deptId);
                meta.put("deptName", deptName);
                docs.add(new Document(content.toString(), meta));
            }
        });
        log.info("[知识库重建] 疾病知识 {} 条（按科室精选）", docs.size());
        return docs;
    }

    /**
     * 抽取医生专长：每个医生一段文档。
     */
    private List<Document> loadDoctors() {
        // employee 表关联 department 取科室名，关联 regist_level 取职称
        String sql = "SELECT e.id, e.realname, e.deptment_id, d.dept_name, "
                + "e.regist_level_id, rl.regist_name "
                + "FROM employee e "
                + "LEFT JOIN department d ON e.deptment_id = d.id "
                + "LEFT JOIN regist_level rl ON e.regist_level_id = rl.id "
                + "WHERE e.delmark = 0 ORDER BY e.id";

        return jdbcTemplate.query(sql, (rs, i) -> {
            StringBuilder content = new StringBuilder();
            content.append("医生：").append(rs.getString("realname")).append("\n");
            String dept = rs.getString("dept_name");
            if (dept != null && !dept.isBlank()) {
                content.append("所属科室：").append(dept).append("\n");
            }
            String level = rs.getString("regist_name");
            if (level != null && !level.isBlank()) {
                content.append("职称：").append(level);
            }

            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "doctor");
            meta.put("doctorName", rs.getString("realname"));
            meta.put("deptId", rs.getInt("deptment_id"));
            return new Document(content.toString(), meta);
        });
    }
}
