-- V4: 现货交易下单/撤单/成交/推送增强
-- V4：现货交易（spot-trade）功能增强
-- 内容：① orders 幂等键与市价买单/手续费列 ② trades 审计列与用户游标分页索引 ③ 对外单号日序列表

-- ============================================================
-- ① orders：客户端幂等键 + 市价买单报价数量 + 成交手续费
-- ============================================================

-- 客户端幂等键（clientOrderId）：同一用户重复提交必须返回同一订单，唯一索引兜底并发重复下单
ALTER TABLE orders ADD COLUMN client_order_id VARCHAR(64);
-- 市价买单的报价数量（花多少计价币买，如 USDT）；限价单与市价卖单为 NULL
ALTER TABLE orders ADD COLUMN quote_qty DECIMAL(32,18);
-- 该订单累计手续费（人可读单位）与手续费币种（成交资产：买单收基础币、卖单收计价币）
ALTER TABLE orders ADD COLUMN fee DECIMAL(32,18) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN fee_asset VARCHAR(20);
-- 下单时冻结的资产：币种 + 数量。成交/撤单时据此精确解冻剩余部分
-- （市价买单按数量下单会按「参考价 × (1+最大偏离)」上浮预估冻结，实际花费以成交金额为准）
ALTER TABLE orders ADD COLUMN frozen_currency_id BIGINT;
ALTER TABLE orders ADD COLUMN frozen_amount DECIMAL(32,18);
-- 幂等唯一索引：(user_id, client_order_id) 保证一用户一 clientOrderId 仅一单
CREATE UNIQUE INDEX uk_orders_user_client_order_id ON orders(user_id, client_order_id);
-- 组合索引：加速单日限额统计（按用户 + 创建时间倒序）
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- ============================================================
-- ② trades：审计时间列 + 用户维度游标分页索引
-- ============================================================

-- 成交表补充审计列，与其余业务表结构对齐（INSERT/UPDATE 由 AuditMetaObjectHandler 填充）
ALTER TABLE trades ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE trades ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
-- 「我的成交」按 (maker_user_id OR taker_user_id) + 游标（id < cursor）倒序查，
-- 两个用户维度各自建索引，避免 OR 条件导致全表扫描
CREATE INDEX idx_trades_maker_user_id ON trades(maker_user_id, id DESC);
CREATE INDEX idx_trades_taker_user_id ON trades(taker_user_id, id DESC);

-- ============================================================
-- ③ 对外单号日序列表（spot_serial）
-- ============================================================

-- 对外单号按日递增：订单号 o_yyyyMMdd_序号、成交号 t_yyyyMMdd_序号
-- 通过 upsert（ON CONFLICT DO UPDATE）原子取号，避免依赖自增主键（订单落库前即需单号）
CREATE TABLE spot_serial (
    biz_date DATE NOT NULL,                                    -- 业务日期（UTC）
    biz_type VARCHAR(20) NOT NULL,                             -- 业务类型：ORDER / TRADE
    last_seq BIGINT NOT NULL DEFAULT 0,                        -- 当日已发放的最大序号
    PRIMARY KEY (biz_date, biz_type)                           -- 联合主键：一日一类型一行，upsert 冲突依据
);
