package com.xikang.pharmacy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.xikang.pharmacy.entity.MedicationGuide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

/**
 * 用药指导单 PDF 渲染器。
 *
 * <p>设计：把 medication_guide.guide_content (JSON) 套进 HTML 模板字符串，
 * 用 OpenHTMLtoPDF 转 PDF 字节流。<b>PDF 永不落盘</b>，调用方拿到 byte[] 直接写 response。</p>
 *
 * <p>中文字体：OpenHTMLtoPDF 默认只带 Latin 字体，遇到中文会乱码。
 * 本类从 classpath:/fonts/simhei.ttf 加载黑体并通过 useFont() 注册为 "CJK" 字体，
 * CSS 中所有 body / h1 / table 等都显式引用该字体，确保中文正确嵌入 PDF。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PharmacyPdfRenderer {

    private final ObjectMapper objectMapper;

    /** 注册到 OpenHTMLtoPDF 的中文字体名（CSS font-family 引用此名）。 */
    private static final String CJK_FONT_NAME = "CJK";
    /** classpath 下的字体资源路径。 */
    private static final String CJK_FONT_RESOURCE = "/fonts/simhei.ttf";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 渲染 PDF。失败抛 RuntimeException，调用方返回 500/404。
     */
    public byte[] render(MedicationGuide guide) {
        JsonNode root = parseGuide(guide);
        String html = buildHtml(guide, root);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerCjkFont(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("渲染 PDF 失败 | registerId={}", guide.getRegisterId(), e);
            throw new RuntimeException("渲染 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册中文字体。字体从 classpath 加载，跨平台兼容。
     * 若字体文件缺失，记录警告但继续渲染（中文会乱码，但流程不中断）。
     */
    private void registerCjkFont(PdfRendererBuilder builder) {
        try (InputStream fontStream = getClass().getResourceAsStream(CJK_FONT_RESOURCE)) {
            if (fontStream == null) {
                log.warn("中文字体未找到：{}，PDF 中文将显示为乱码。请把 TTF 放到 resources/fonts/ 下。", CJK_FONT_RESOURCE);
                return;
            }
            // useFont 接收 Supplier<InputStream>，每次调用都会重新打开流
            builder.useFont(() -> getClass().getResourceAsStream(CJK_FONT_RESOURCE), CJK_FONT_NAME);
            log.debug("中文字体已注册：{}", CJK_FONT_NAME);
        } catch (Exception e) {
            log.warn("注册中文字体失败：{}", e.getMessage());
        }
    }

    private JsonNode parseGuide(MedicationGuide guide) {
        try {
            return objectMapper.readTree(guide.getGuideContent());
        } catch (Exception e) {
            throw new RuntimeException("guide_content JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private String buildHtml(MedicationGuide guide, JsonNode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"/><style>");
        sb.append(CSS);
        sb.append("</style></head><body>");

        // 医院标题
        sb.append("<div class=\"hospital-header\">");
        sb.append("<h1>熙康云医院</h1>");
        sb.append("<h2>用药指导单</h2>");
        sb.append("</div>");

        // 患者信息
        sb.append("<table class=\"info-table\">");
        sb.append("<tr><td class=\"label\">患者</td><td>")
          .append(nullSafe(guide.getPatientName())).append("</td>");
        sb.append("<td class=\"label\">挂号号</td><td>")
          .append(nullSafe(guide.getRegisterId())).append("</td></tr>");
        sb.append("<tr><td class=\"label\">生成时间</td><td>")
          .append(formatTime(guide.getCreateTime())).append("</td>");
        sb.append("<td class=\"label\">来源</td><td>")
          .append(sourceLabel(guide.getSource())).append("</td></tr>");
        sb.append("</table>");

        // 整体建议
        String generalAdvice = text(root, "generalAdvice");
        if (generalAdvice != null) {
            sb.append("<div class=\"section advice\"><div class=\"section-title\">用药总提示</div>");
            sb.append("<p>").append(escape(generalAdvice)).append("</p></div>");
        }

        // 每种药一节
        JsonNode items = root.get("items");
        if (items != null && items.isArray()) {
            int idx = 1;
            for (Iterator<JsonNode> it = items.elements(); it.hasNext(); idx++) {
                sb.append(renderItemSection(it.next(), idx));
            }
        }

        // 多药提示
        String interactionsNote = text(root, "interactionsNote");
        if (interactionsNote != null) {
            sb.append("<div class=\"section interactions\"><div class=\"section-title\">联合用药提示</div>");
            sb.append("<p>").append(escape(interactionsNote)).append("</p></div>");
        }

        // 页脚
        sb.append("<div class=\"footer\">");
        sb.append("<p>本指导单由 AI 辅助生成，仅供患者参考；具体用药请以医嘱为准。</p>");
        sb.append("<p>如有不适，请及时联系医生或药师。</p>");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private String renderItemSection(JsonNode item, int idx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section drug\">");
        sb.append("<div class=\"drug-header\">");
        sb.append("<span class=\"drug-idx\">").append(idx).append("</span>");
        sb.append("<span class=\"drug-name\">").append(escape(text(item, "drugName"))).append("</span>");
        if (text(item, "specification") != null) {
            sb.append("<span class=\"drug-spec\">").append(escape(text(item, "specification"))).append("</span>");
        }
        sb.append("</div>");

        sb.append("<table class=\"drug-table\">");

        row(sb, "数量", item.has("quantity") ? item.get("quantity").asText() : null);
        row(sb, "医嘱用法（医生原话）", text(item, "usageText"));
        row(sb, "服药建议", text(item, "howToTake"));
        row(sb, "服药时机", text(item, "takeWithFood"));
        row(sb, "注意事项", text(item, "precautions"));
        row(sb, "可能的不良反应", text(item, "sideEffects"));
        row(sb, "储存条件", text(item, "storage"));

        sb.append("</table>");
        sb.append("</div>");
        return sb.toString();
    }

    private void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td class=\"label\" style=\"width:30%\">").append(label).append("</td>");
        sb.append("<td>").append(value == null || value.isBlank() ? "详见药品说明书或咨询药师" : escape(value))
          .append("</td></tr>");
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return "null".equals(s) ? null : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String nullSafe(Object o) {
        return o == null ? "-" : o.toString();
    }

    private String formatTime(LocalDateTime t) {
        return t == null ? "-" : t.format(FMT);
    }

    private String sourceLabel(String source) {
        if (source == null) return "-";
        return switch (source) {
            case "ai" -> "AI 生成";
            case "fallback" -> "AI 生成（降级）";
            case "manual" -> "人工";
            default -> source;
        };
    }

    private static final String CSS = """
        @page { size: A4; margin: 18mm 16mm; }
        body { font-family: "CJK", sans-serif;
               font-size: 11pt; color: #222; line-height: 1.55; }
        .hospital-header { text-align: center; margin-bottom: 14pt; border-bottom: 2pt solid #1a73e8; padding-bottom: 8pt; }
        .hospital-header h1 { font-size: 18pt; color: #1a73e8; margin: 0; }
        .hospital-header h2 { font-size: 14pt; color: #444; margin: 4pt 0 0; font-weight: normal; }
        .info-table { width: 100%; border-collapse: collapse; margin-bottom: 12pt; font-size: 10pt; }
        .info-table td { border: 1pt solid #ddd; padding: 5pt 8pt; }
        .info-table td.label { background: #f5f7fa; width: 18%; font-weight: bold; color: #555; }
        .section { margin-bottom: 12pt; page-break-inside: avoid; }
        .section-title { font-size: 12pt; font-weight: bold; color: #1a73e8;
                         border-left: 3pt solid #1a73e8; padding-left: 6pt; margin-bottom: 6pt; }
        .advice { background: #f0f7ff; padding: 8pt 10pt; border-radius: 4pt; }
        .advice p { margin: 0; }
        .interactions { background: #fff8e6; padding: 8pt 10pt; border-radius: 4pt; }
        .interactions p { margin: 0; }
        .drug { border: 1pt solid #e0e0e0; border-radius: 4pt; padding: 0; }
        .drug-header { background: #f5f7fa; padding: 6pt 10pt; border-bottom: 1pt solid #e0e0e0;
                       display: flex; align-items: baseline; gap: 8pt; }
        .drug-idx { display: inline-block; width: 18pt; height: 18pt; line-height: 18pt;
                    text-align: center; background: #1a73e8; color: #fff; border-radius: 50%; font-size: 10pt; }
        .drug-name { font-size: 12pt; font-weight: bold; color: #222; }
        .drug-spec { font-size: 10pt; color: #666; margin-left: 6pt; }
        .drug-table { width: 100%; border-collapse: collapse; font-size: 10pt; }
        .drug-table td { border: none; border-bottom: 1pt solid #f0f0f0; padding: 5pt 10pt; vertical-align: top; }
        .drug-table td.label { color: #666; background: transparent; font-weight: normal; width: 30%; }
        .footer { margin-top: 18pt; padding-top: 8pt; border-top: 1pt dashed #ccc;
                  font-size: 9pt; color: #888; text-align: center; }
        .footer p { margin: 2pt 0; }
        """;
}
