-- migrate_030: 检查申请与 CT 影像 volume 绑定
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_volume_id VARCHAR(64);
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_uploaded_at TIMESTAMP;
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_source_name VARCHAR(255);

COMMENT ON COLUMN check_request.imaging_volume_id IS 'ct-viewer-service 返回的 volumeId';
COMMENT ON COLUMN check_request.imaging_uploaded_at IS '影像绑定时间';
COMMENT ON COLUMN check_request.imaging_source_name IS '上传来源文件名或 DICOM 序列描述';
