-- V2: Add contract trading tables
-- V2：新增合约交易相关表（USDT 本位永续/交割合约）

-- ============================================================
-- 合约产品（Contracts）
-- ============================================================

-- 合约产品表：定义可交易的合约品种及其杠杆、精度、费率、资金费率参数
CREATE TABLE contracts (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增（持仓/合约订单表通过 contract_id 引用）
    symbol VARCHAR(30) UNIQUE NOT NULL,                         -- 合约符号，全局唯一：BTCUSDT_PERP（永续）/ ETHUSDT_PERP
    underlying VARCHAR(20),                                     -- 标的资产，如 BTC（合约价格锚定的现货币种）
    quote_asset VARCHAR(20),                                    -- 计价资产，如 USDT（本位币种，保证金与盈亏均以其结算）
    contract_type VARCHAR(20) DEFAULT 'PERPETUAL',              -- 合约类型：PERPETUAL（永续，无到期日）/ DELIVERY（交割）
    max_leverage INT DEFAULT 125,                               -- 最大可用杠杆倍数（BTC 永续默认 125x）
    min_notional DECIMAL(32,18) DEFAULT 5,                      -- 最小下单金额（名义价值，低于拒绝下单）
    tick_size DECIMAL(32,18) DEFAULT 0.01,                      -- 价格最小变动单位（tick）
    lot_size DECIMAL(32,18) DEFAULT 0.001,                      -- 数量最小变动单位（lot）
    funding_rate DECIMAL(12,8) DEFAULT 0,                       -- 当前资金费率：永续合约锚定现货价格的定期结算费
    funding_interval INT DEFAULT 8,                             -- 资金费率结算间隔（小时），默认每 8 小时结算一次
    maintenance_margin_ratio DECIMAL(6,4) DEFAULT 0.005,        -- 维持保证金率：低于该比例触发强平，默认 0.5%
    maker_fee_rate DECIMAL(8,6) DEFAULT 0.0002,                 -- 挂单（Maker）费率，默认 0.02%（低于现货，鼓励提供流动性）
    taker_fee_rate DECIMAL(8,6) DEFAULT 0.0005,                 -- 吃单（Taker）费率，默认 0.05%
    status VARCHAR(20) DEFAULT 'ACTIVE',                        -- 状态：ACTIVE（可交易）/ SUSPENDED（暂停交易）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 合约持仓（Contract Positions）
-- ============================================================

-- 合约持仓表：用户在每个合约上的多空仓位、保证金与未实现盈亏
CREATE TABLE contract_positions (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL,                                    -- 持仓用户 ID
    contract_id BIGINT NOT NULL REFERENCES contracts(id),       -- 合约产品 ID，外键关联 contracts 表
    side VARCHAR(10) NOT NULL,                                  -- 持仓方向：LONG（做多）/ SHORT（做空）
    leverage INT DEFAULT 1,                                     -- 本仓位使用的杠杆倍数
    entry_price DECIMAL(32,18),                                 -- 开仓均价（加权平均）
    mark_price DECIMAL(32,18),                                  -- 标记价格（用于盈亏计算与强平判定，取多源指数价，防插针）
    qty DECIMAL(32,18),                                         -- 持仓数量（标的资产数量）
    margin DECIMAL(32,18),                                      -- 仓位保证金 = 持仓名义价值 / 杠杆
    margin_type VARCHAR(10) DEFAULT 'ISOLATED',                 -- 保证金模式：ISOLATED（逐仓，爆仓仅损本仓）/ CROSS（全仓）
    unrealized_pnl DECIMAL(32,18) DEFAULT 0,                    -- 未实现盈亏（随标记价格浮动）
    realized_pnl DECIMAL(32,18) DEFAULT 0,                      -- 已实现盈亏（部分平仓/减仓时落袋）
    liquidation_price DECIMAL(32,18),                           -- 预估强平价格：标记价格触及即触发强平流程
    take_profit_price DECIMAL(32,18),                           -- 止盈触发价（预留条件单）
    stop_loss_price DECIMAL(32,18),                             -- 止损触发价（预留条件单）
    status VARCHAR(20) DEFAULT 'OPEN',                          -- 持仓状态：OPEN（持仓中）/ CLOSED（已平仓）/ LIQUIDATED（已强平）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 开仓时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间（加仓/减仓/结算时刷新）
);

-- ============================================================
-- 合约订单（Contract Orders）
-- ============================================================

-- 合约订单表：合约开平仓委托单全生命周期记录（结构对齐现货 orders 表）
CREATE TABLE contract_orders (
    id BIGSERIAL PRIMARY KEY,                                   -- 内部主键，自增
    order_id VARCHAR(64) UNIQUE NOT NULL,                       -- 对外订单号（全局唯一，暴露给客户端）
    user_id BIGINT NOT NULL,                                    -- 下单用户 ID
    contract_id BIGINT NOT NULL REFERENCES contracts(id),       -- 合约产品 ID，外键关联 contracts 表
    side VARCHAR(10) NOT NULL,                                  -- 方向：BUY / SELL
    position_effect VARCHAR(10) DEFAULT 'OPEN',                 -- 开平标志：OPEN（开仓）/ CLOSE（平仓）
    order_type VARCHAR(20) NOT NULL,                            -- 订单类型：LIMIT（限价）/ MARKET（市价）
    price DECIMAL(32,18),                                       -- 委托价格（限价单必填）
    qty DECIMAL(32,18),                                         -- 委托数量（标的资产数量）
    executed_qty DECIMAL(32,18) DEFAULT 0,                      -- 已成交数量
    avg_fill_price DECIMAL(32,18),                              -- 平均成交价
    leverage INT DEFAULT 1,                                     -- 本单使用的杠杆倍数
    reduce_only BOOLEAN DEFAULT FALSE,                          -- 只减仓标记：TRUE 时只能平仓不能加仓（对冲模式防误开仓）
    status VARCHAR(20) DEFAULT 'NEW',                           -- 状态：NEW → PARTIALLY_FILLED → FILLED / CANCELED / REJECTED
    time_in_force VARCHAR(10) DEFAULT 'GTC',                    -- 有效期：GTC / IOC / FOK（含义同现货）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 初始化数据（种子数据）
-- ============================================================

-- 预置 2 个 USDT 本位永续合约品种
INSERT INTO contracts (symbol, underlying, quote_asset, max_leverage, tick_size, lot_size) VALUES
('BTCUSDT_PERP', 'BTC', 'USDT', 125, 0.01, 0.001),   -- BTC 永续：最高 125x 杠杆，价格步进 0.01，数量步进 0.001 BTC
('ETHUSDT_PERP', 'ETH', 'USDT', 100, 0.01, 0.01);    -- ETH 永续：最高 100x 杠杆，价格步进 0.01，数量步进 0.01 ETH
