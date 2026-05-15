INSERT INTO `t_user` (`id`, `username`, `password`, `role`, `avatar`, `create_time`, `update_time`, `deleted`)
VALUES (2001523723396308993, 'admin', 'admin', 'admin',
        'https://static.deepseek.com/user-avatar/G_6cuD8GbD53VwGRwisvCsZ6', '2025-12-21 15:55:44',
        '2025-12-21 15:55:44', 0);

-- 默认流水线（用于小说续写、文章仿写等场景）
INSERT INTO `t_ingestion_pipeline` (`id`, `name`, `description`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`)
VALUES (1001, '默认文档处理流水线', '适用于小说续写、文章仿写等场景的默认流水线：解析 -> 分块 -> 向量化', 'system', 'system', '2025-12-21 15:55:44', '2025-12-21 15:55:44', 0);

INSERT INTO `t_ingestion_pipeline_node` (`id`, `pipeline_id`, `node_id`, `node_type`, `next_node_id`, `settings_json`, `condition_json`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`)
VALUES
(100101, 1001, 'parser', 'parser', 'chunker', NULL, NULL, 'system', 'system', '2025-12-21 15:55:44', '2025-12-21 15:55:44', 0),
(100102, 1001, 'chunker', 'chunker', 'indexer', '{"strategy":"sentence","chunkSize":500,"overlap":50}', NULL, 'system', 'system', '2025-12-21 15:55:44', '2025-12-21 15:55:44', 0),
(100103, 1001, 'indexer', 'indexer', NULL, NULL, NULL, 'system', 'system', '2025-12-21 15:55:44', '2025-12-21 15:55:44', 0);