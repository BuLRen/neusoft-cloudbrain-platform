package com.xikang.ctviewer.dto;

import lombok.Data;

/**
 * AI 肺结节分割请求体。
 * <p>
 * modelId 可选，指定使用的 AI 分割模型（monai / segnet / nnunet），
 * 为空时使用 lung-nodule-seg-service 的默认模型。
 */
@Data
public class AiSegmentRequestDto {
    private String modelId;
}
