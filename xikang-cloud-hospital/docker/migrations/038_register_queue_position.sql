-- 医生候诊队列可调序：queue_position 与 check_in_time 解耦
ALTER TABLE register ADD COLUMN IF NOT EXISTS queue_position INTEGER NULL;
COMMENT ON COLUMN register.queue_position IS '医生候诊队列序号（同医生同天）；报到时默认赋位，医生可调整';

-- 历史数据回填：按报到时间顺序赋 1..n
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY employee_id, DATE(visit_date)
               ORDER BY check_in_time ASC NULLS LAST, id ASC
           ) AS pos
    FROM register
    WHERE check_in_time IS NOT NULL
)
UPDATE register r
SET queue_position = ranked.pos
FROM ranked
WHERE r.id = ranked.id
  AND r.queue_position IS NULL;
