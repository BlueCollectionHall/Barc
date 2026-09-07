-- 背景图场景标签（支持按「时间 + 节日」切换）
-- time_period：白天(day) / 傍晚(eventing) / 夜晚(night)，NULL 表示任意时段
-- festival：新年(newyear)，NULL 表示非节日通用图
ALTER TABLE background_image
    ADD COLUMN time_period VARCHAR(16) NULL,
    ADD COLUMN festival VARCHAR(32) NULL;
