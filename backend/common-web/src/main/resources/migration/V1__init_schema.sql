-- V1: Initial schema for crypto exchange
-- V1：加密货币交易所初始表结构
-- Phase 1（一期）核心表：用户体系 + 现货交易 + 钱包（充值/提现）

-- ============================================================
-- 用户与认证（Users & Authentication）
-- ============================================================

-- 用户主表：存储账号基础信息、KYC 等级、邀请关系与账号状态
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增长整型（BIGSERIAL = BIGINT + 序列自增）
    uid VARCHAR(32) UNIQUE NOT NULL,                            -- 对外展示的用户唯一标识（脱敏 ID，不暴露自增主键）
    email VARCHAR(255) UNIQUE,                                  -- 邮箱，登录凭证之一，全表唯一
    phone VARCHAR(20),                                          -- 手机号（可选，用于短信验证/找回）
    password_hash VARCHAR(255),                                 -- 密码哈希（BCrypt 等单向哈希，严禁明文存储）
    google_auth_secret VARCHAR(32),                             -- 谷歌验证器（2FA）密钥种子，用于 TOTP 动态口令
    google_auth_enabled BOOLEAN DEFAULT FALSE,                  -- 是否已开启谷歌二步验证，默认关闭
    kyc_level VARCHAR(20) NOT NULL DEFAULT 'NONE',              -- KYC 等级：NONE（未认证）→ 提现/交易额度随等级提升
    ref_code VARCHAR(16) UNIQUE,                                -- 本用户的邀请码，用于注册邀请返佣
    referred_by BIGINT REFERENCES users(id),                    -- 邀请人用户 ID，自引用外键，形成邀请链
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',               -- 账号状态：ACTIVE（正常）/ FROZEN（冻结）/ BANNED（封禁）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间（带时区的时间戳）
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- KYC 证件资料表：每个用户可提交多份证件（正面/背面照片），走人工或自动审核
CREATE TABLE user_kyc_documents (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL REFERENCES users(id),               -- 所属用户 ID，外键关联 users 表
    doc_type VARCHAR(20) NOT NULL,                              -- 证件类型：PASSPORT（护照）/ ID_CARD（身份证）/ DRIVER_LICENSE（驾照）
    doc_front_url VARCHAR(500),                                 -- 证件正面照的对象存储 URL
    doc_back_url VARCHAR(500),                                  -- 证件反面照的对象存储 URL
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',              -- 审核状态：PENDING（待审）/ APPROVED（通过）/ REJECTED（驳回）
    reviewer_id BIGINT REFERENCES users(id),                    -- 审核人（运营人员）的用户 ID
    reject_reason VARCHAR(500),                                 -- 驳回原因，审核不通过时告知用户
    submitted_at TIMESTAMPTZ,                                   -- 资料提交时间
    reviewed_at TIMESTAMPTZ,                                    -- 审核完成时间
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- 用户会话表：管理登录态，支持多端登录与令牌吊销（登出）
CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL REFERENCES users(id),               -- 所属用户 ID，外键关联 users 表
    token_hash VARCHAR(64) UNIQUE NOT NULL,                     -- 访问令牌的 SHA-256 哈希（不存明文 token，防库泄露被盗用）
    refresh_token_hash VARCHAR(64),                             -- 刷新令牌哈希，用于无感续期
    ip_address VARCHAR(45),                                     -- 登录 IP（45 字节兼容 IPv6）
    device_info VARCHAR(500),                                   -- 设备/UA 信息，用于安全审计与多端管理
    expires_at TIMESTAMPTZ NOT NULL,                            -- 会话过期时间，过期后需重新登录
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 币种（Currencies）
-- ============================================================

-- 币种字典表：平台支持的所有链上资产定义（同符号不同链为多行，如 USDT / USDT_TRC20）
CREATE TABLE currencies (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增（钱包/充值/提现表均通过 currency_id 引用）
    symbol VARCHAR(20) UNIQUE NOT NULL,                         -- 资产符号，全局唯一：BTC / ETH / USDT / USDT_TRC20
    full_name VARCHAR(100),                                     -- 资产全称，如 Tether USD，用于前端展示
    chain VARCHAR(50),                                          -- 所属链的规范名：Bitcoin / Ethereum / TRON / BSC
    contract_addr VARCHAR(42),                                  -- 代币合约地址（42 = 0x + 40 位十六进制；原生币为空）
    decimals INT DEFAULT 18,                                    -- 精度：最小单位换算基数（USDT=6，ETH=18；前端展示需除以 10^decimals）
    min_withdraw DECIMAL(32,18),                                -- 单次最小提现数量（低于该值拒绝提现）
    withdraw_fee DECIMAL(32,18),                                -- 提现手续费（固定值，按币种计）
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',               -- 币种状态：ACTIVE（启用，可充提）/ SUSPENDED（暂停）
    deposit_confirm INT DEFAULT 2,                              -- 充值到账所需区块确认数（确认数达到后才入账，防双花/链回滚）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 钱包（Wallets）
-- ============================================================

-- 用户钱包表（余额账本）：账本唯一权威数据源，余额查询不做链上查询
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL,                                    -- 用户 ID（用户量大，不设外键以降低写放大）
    currency_id BIGINT NOT NULL REFERENCES currencies(id),      -- 资产 ID，外键关联 currencies 表
    wallet_type VARCHAR(20) NOT NULL DEFAULT 'SPOT',            -- 钱包类型：SPOT（现货），预留 FUNDING（资金）/ MARGIN（杠杆）
    balance DECIMAL(32,18) NOT NULL DEFAULT 0,                  -- 可用余额（人可读单位；32,18 精度覆盖所有链的最小单位）
    frozen_balance DECIMAL(32,18) NOT NULL DEFAULT 0,           -- 冻结余额（提现申请冻结、挂单占用等，不可使用）
    version BIGINT NOT NULL DEFAULT 0,                          -- 乐观锁版本号，并发更新时防止丢失更新（MyBatis-Plus @Version）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW(),                       -- 最后更新时间
    UNIQUE(user_id, currency_id, wallet_type)                   -- 唯一约束：一用户一资产一类型仅一个钱包（兼兜底并发创建）
);

-- ============================================================
-- 充值（Deposits & Deposit Addresses）
-- ============================================================

-- 用户充值地址分配表：同用户 + 同币种地址固定不变（唯一约束保证）
CREATE TABLE deposit_addresses (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL,                                    -- 用户 ID
    currency_id BIGINT NOT NULL REFERENCES currencies(id),      -- 资产 ID，外键关联 currencies 表
    address VARCHAR(100),                                       -- 分配给用户的链上充值地址（从地址池中占用）
    derivation_path VARCHAR(50),                                -- BIP44 派生路径（如 m/44'/60'/0'/0/1），离线签名时定位密钥
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW(),                       -- 最后更新时间
    UNIQUE(user_id, currency_id)                                -- 唯一约束：同用户同资产只有一条分配记录（地址固定不变的保证）
);

-- 充值流水表：链上监听服务发现充值交易后写入，作为入账依据
CREATE TABLE deposits (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL,                                    -- 充值用户 ID
    currency_id BIGINT NOT NULL REFERENCES currencies(id),      -- 资产 ID，外键关联 currencies 表
    tx_hash VARCHAR(200) UNIQUE,                                -- 链上交易哈希（注意：V3 已改为与 log_index 组合唯一）
    from_address VARCHAR(100),                                  -- 付款方链上地址
    to_address VARCHAR(100),                                    -- 收款方链上地址（应为该用户的充值地址）
    amount DECIMAL(32,18),                                      -- 充值金额（人可读单位）
    fee DECIMAL(32,18),                                         -- 链上手续费（仅记录，不参与入账计算）
    confirmations INT DEFAULT 0,                                -- 当前区块确认数，随链增长
    required_confirm INT DEFAULT 2,                             -- 到账所需确认数（取自 currencies.deposit_confirm）
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',              -- 状态：PENDING（待确认）→ CREDITED（已入账）/ FAILED
    block_height BIGINT,                                        -- 交易所在区块高度
    block_hash VARCHAR(200),                                    -- 交易所在区块哈希
    credited_at TIMESTAMPTZ,                                    -- 入账时间（确认数达标、余额加到账后回填）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间（监听服务首次发现该交易的时间）
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 提现（Withdrawals）
-- ============================================================

-- 提现流水表：用户提现申请单，全生命周期状态记录
CREATE TABLE withdrawals (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增（对外单号 wd_yyyyMMdd_序号 由该 id 生成）
    user_id BIGINT NOT NULL,                                    -- 提现用户 ID
    currency_id BIGINT NOT NULL REFERENCES currencies(id),      -- 资产 ID，外键关联 currencies 表
    request_id VARCHAR(64) UNIQUE,                              -- 客户端幂等键（clientId）：唯一索引保证重复提交去重
    to_address VARCHAR(100),                                    -- 提现目标外部地址
    amount DECIMAL(32,18),                                      -- 申请提现数量
    fee DECIMAL(32,18),                                         -- 提现手续费（取自 currencies.withdraw_fee）
    actual_amount DECIMAL(32,18),                               -- 实际到账数量 = amount - fee
    tx_hash VARCHAR(200),                                       -- 广播后的链上交易哈希（广播成功后回填）
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',              -- 状态：SUBMITTED → RISK_CHECK → SIGNING → BROADCASTING → CONFIRMED / FAILED
    risk_score INT DEFAULT 0,                                   -- 风控评分，超过阈值进入人工审核
    approver_id BIGINT,                                         -- 人工审核人 ID（大额提现需审批）
    memo VARCHAR(200),                                          -- 备注（部分链如 EOS/XRP 提现需要 memo）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间（申请时间）
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- ============================================================
-- 现货交易（Trading Pairs / Orders / Trades / Klines）
-- ============================================================

-- 交易对字典表：定义可交易的币对及其精度、费率、限额
CREATE TABLE trading_pairs (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    symbol VARCHAR(20) UNIQUE NOT NULL,                         -- 交易对符号，全局唯一：BTCUSDT / ETHUSDT / BNBUSDT
    base_currency_id BIGINT NOT NULL REFERENCES currencies(id), -- 计价基础币 ID（买入方得到的币，如 BTCUSDT 中的 BTC）
    quote_currency_id BIGINT NOT NULL REFERENCES currencies(id),-- 报价币 ID（下单使用的币，如 BTCUSDT 中的 USDT）
    price_precision INT DEFAULT 2,                              -- 价格精度（小数位）
    qty_precision INT DEFAULT 5,                                -- 数量精度（小数位）
    min_order_qty DECIMAL(32,18),                               -- 最小下单数量（低于拒绝）
    min_order_notional DECIMAL(32,18),                          -- 最小下单金额 = 价格 × 数量（低于拒绝）
    maker_fee_rate DECIMAL(8,6) DEFAULT 0.001,                  -- 挂单（Maker）费率，如 0.001 = 0.1%
    taker_fee_rate DECIMAL(8,6) DEFAULT 0.001,                  -- 吃单（Taker）费率
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',               -- 状态：ACTIVE（可交易）/ SUSPENDED（停牌）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);

-- 订单表：现货买卖委托单全生命周期记录
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,                                   -- 内部主键，自增
    order_id VARCHAR(64) UNIQUE NOT NULL,                       -- 对外订单号（全局唯一，暴露给客户端）
    user_id BIGINT NOT NULL,                                    -- 下单用户 ID
    pair_id BIGINT NOT NULL REFERENCES trading_pairs(id),       -- 交易对 ID，外键关联 trading_pairs 表
    side VARCHAR(10) NOT NULL,                                  -- 方向：BUY（买）/ SELL（卖）
    order_type VARCHAR(20) NOT NULL,                            -- 订单类型：LIMIT（限价）/ MARKET（市价）
    price DECIMAL(32,18),                                       -- 委托价格（限价单必填，市价单为空）
    stop_price DECIMAL(32,18),                                  -- 触发价（预留：止盈止损单触发条件）
    original_qty DECIMAL(32,18),                                -- 委托原始数量
    executed_qty DECIMAL(32,18) NOT NULL DEFAULT 0,             -- 已成交数量（累计撮合量）
    executed_quote DECIMAL(32,18) NOT NULL DEFAULT 0,           -- 已成交金额（计价币累计）
    avg_fill_price DECIMAL(32,18),                              -- 平均成交价 = executed_quote / executed_qty
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',                  -- 状态：NEW → PARTIALLY_FILLED → FILLED / CANCELED / REJECTED
    time_in_force VARCHAR(10) DEFAULT 'GTC',                    -- 有效期：GTC（直到成交/撤销）/ IOC（立即成交剩余撤销）/ FOK（全部成交或撤销）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间
);
CREATE INDEX idx_orders_user_pair_status ON orders(user_id, pair_id, status);   -- 组合索引：加速「用户某交易对的在途订单」查询
CREATE INDEX idx_orders_order_id ON orders(order_id);                            -- 索引：加速对外订单号查询

-- 成交记录表：撮合引擎产出的逐笔成交明细
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    trade_id VARCHAR(64) UNIQUE NOT NULL,                       -- 对外成交号（全局唯一）
    pair_id BIGINT NOT NULL REFERENCES trading_pairs(id),       -- 交易对 ID，外键关联 trading_pairs 表
    maker_order_id BIGINT NOT NULL REFERENCES orders(id),       -- 挂单方（Maker）订单 ID
    taker_order_id BIGINT NOT NULL REFERENCES orders(id),       -- 吃单方（Taker）订单 ID
    maker_user_id BIGINT NOT NULL,                              -- 挂单方用户 ID（冗余存储，避免回查订单表）
    taker_user_id BIGINT NOT NULL,                              -- 吃单方用户 ID（冗余存储）
    price DECIMAL(32,18),                                       -- 成交价格
    qty DECIMAL(32,18),                                         -- 成交数量
    quote_amount DECIMAL(32,18),                                -- 成交金额 = price × qty
    maker_fee DECIMAL(32,18),                                   -- 挂单方手续费
    taker_fee DECIMAL(32,18),                                   -- 吃单方手续费
    is_buyer_maker BOOLEAN,                                     -- 买方是否为挂单方（K 线红绿/主动买卖方向判定依据）
    executed_at TIMESTAMPTZ DEFAULT NOW()                       -- 成交时间
);
CREATE INDEX idx_trades_pair_time ON trades(pair_id, executed_at DESC);   -- 组合索引：加速交易对最近成交列表/成交明细页
CREATE INDEX idx_trades_user_time ON trades(maker_user_id, executed_at DESC);  -- 组合索引：加速用户成交历史查询

-- K 线表：行情聚合数据，预留给 TimescaleDB（普通 PostgreSQL 下为标准表）
CREATE TABLE klines (
    pair_id BIGINT NOT NULL,                                    -- 交易对 ID（联合主键之一）
    interval VARCHAR(5) NOT NULL,                               -- K 线周期：1m / 5m / 15m / 1h / 4h / 1d 等（联合主键之一）
    open_time TIMESTAMPTZ NOT NULL,                             -- 周期开始时间（联合主键之一）
    close_time TIMESTAMPTZ NOT NULL,                            -- 周期结束时间
    open DECIMAL(32,18),                                        -- 开盘价（周期内第一笔成交价）
    high DECIMAL(32,18),                                        -- 最高价
    low DECIMAL(32,18),                                         -- 最低价
    close DECIMAL(32,18),                                       -- 收盘价（周期内最后一笔成交价）
    volume DECIMAL(32,18),                                      -- 成交量（基础币）
    quote_volume DECIMAL(32,18),                                -- 成交额（计价币）
    trades_count INT DEFAULT 0,                                 -- 周期内成交笔数
    PRIMARY KEY (pair_id, interval, open_time)                  -- 联合主键：一交易对一周期一时间唯一，天然去重与快速查询
);

-- ============================================================
-- 风控（Risk）
-- ============================================================

-- 风控事件表：记录命中风控规则的事件（异常登录、大额提现、刷单嫌疑等）
CREATE TABLE risk_events (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    user_id BIGINT NOT NULL,                                    -- 关联用户 ID
    event_type VARCHAR(30),                                     -- 事件类型：LARGE_WITHDRAWAL / SUSPICIOUS_LOGIN / WASH_TRADE 等
    severity VARCHAR(10),                                       -- 严重级别：LOW / MEDIUM / HIGH
    details JSONB,                                              -- 事件详情（JSONB 半结构化，便于不同事件类型灵活存储上下文）
    action_taken VARCHAR(30),                                   -- 处置动作：REVIEW（人工审核）/ BLOCK（拦截）/ NONE
    resolved BOOLEAN DEFAULT FALSE,                             -- 是否已处理完结
    created_at TIMESTAMPTZ DEFAULT NOW()                        -- 事件发生时间
);

-- ============================================================
-- 初始化数据（种子数据）
-- ============================================================

-- 预置 5 个基础币种：注意 USDT 在 Ethereum 与 TRON 是两行（不同链独立记账）
INSERT INTO currencies (symbol, full_name, chain, decimals, min_withdraw, withdraw_fee, deposit_confirm) VALUES
('BTC', 'Bitcoin', 'Bitcoin', 8, 0.001, 0.0005, 2),       -- 比特币：8 位精度，确认数 2
('ETH', 'Ethereum', 'Ethereum', 18, 0.01, 0.005, 12),     -- 以太坊：18 位精度，确认数 12
('USDT', 'Tether USD', 'Ethereum', 6, 10, 1, 12),         -- USDT-ERC20：6 位精度
('USDT_TRC20', 'Tether USD', 'TRON', 6, 10, 1, 20),       -- USDT-TRC20：6 位精度，TRON 确认数 20
('BNB', 'BNB', 'BSC', 18, 0.01, 0.0005, 10);              -- 币安币：BSC 链

-- 预置 3 个交易对：BTCUSDT / ETHUSDT / BNBUSDT，均以 USDT（currency_id=3）计价
INSERT INTO trading_pairs (symbol, base_currency_id, quote_currency_id, price_precision, qty_precision, min_order_qty, min_order_notional) VALUES
('BTCUSDT', 1, 3, 2, 5, 0.00001, 10),   -- BTC/USDT：价格 2 位、数量 5 位精度，最小下单 0.00001 BTC 或 10 USDT
('ETHUSDT', 2, 3, 2, 4, 0.0001, 10),    -- ETH/USDT：价格 2 位、数量 4 位精度
('BNBUSDT', 5, 3, 2, 3, 0.001, 10);     -- BNB/USDT：价格 2 位、数量 3 位精度
