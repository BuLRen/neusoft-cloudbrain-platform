-- migrate_038: 检查单 CT 病灶分割结果持久化
-- 建议整文件一次性执行；若在 DBeaver/DataGrip 等 GUI 中执行，请运行本文件全部内容（勿只选中 COMMENT 行）。
-- 若 ALTER TABLE 长时间无响应，请先停止 medtech-service 等占用 check_request 的服务后再执行。

ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_segmentation_result TEXT;
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_segmented_at TIMESTAMP;
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_segmentation_mask_volume_id VARCHAR(64);

COMMENT ON COLUMN check_request.imaging_segmentation_result IS 'CT 病灶分割结果 JSON（掩码 volumeId + lesions + summary）';
COMMENT ON COLUMN check_request.imaging_segmented_at IS 'CT 病灶分割完成时间';
COMMENT ON COLUMN check_request.imaging_segmentation_mask_volume_id IS 'CT 病灶分割掩码 volume UUID';
