-- V3: Wallet deposit/withdrawal enhancements
-- V3：钱包充值/提现功能增强
-- 内容：① 充值地址池表 ② 全局资产发行量表 ③ 充值日志幂等键改造
-- 关联实现：DepositAddressServiceImpl（地址分配）、DepositServiceImpl（回调入账）

-- ============================================================
-- ① 充值地址池（Address Pool）
-- ============================================================

-- 充值地址池表：预先按 BIP44 离线派生的一批链上地址，供充值地址分配接口消费
-- 分配规则：同用户 + 同链 + 同资产固定不变；地址一经分配（used）永不复用
-- 运维要求：池中 available 地址即将耗尽时，需离线继续派生并批量插入补充
CREATE TABLE address_pool (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    chain VARCHAR(50) NOT NULL,                                 -- 所属链的规范名：须使用 currencies.chain 的取值（Ethereum/TRON 等）
    derivation_path VARCHAR(100),                               -- BIP44 派生路径（如 m/44'/60'/0'/0/1），分配后随地址写入 deposit_addresses
    address VARCHAR(100) NOT NULL,                              -- 链上地址本体
    memo VARCHAR(100) DEFAULT '',                               -- 地址备注：部分链（EOS/XRP 等）转账需 memo 定位到用户，其余为空字符串
    status VARCHAR(20) NOT NULL DEFAULT 'available',            -- 状态：available（未分配，可分配）/ used（已分配，永不复用）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间（派生入库时间）
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间（状态变更时间）
);
-- 同一链上地址唯一：防止同一地址被重复导入地址池（派生索引重复属于运维事故，须拦截）
CREATE UNIQUE INDEX uk_address_pool_chain_address ON address_pool(chain, address);
-- 组合索引：加速分配时的取址查询（WHERE chain = ? AND status = 'available' ORDER BY id LIMIT 1）
CREATE INDEX idx_address_pool_chain_status ON address_pool(chain, status);

-- ============================================================
-- ② 全局资产发行量（Asset Total Supply）
-- ============================================================

-- 全局资产发行量表：每种资产一行，充值入账时累加 total_supply，作为账本层的全局资产统计
-- 写入方式：通过 upsert（ON CONFLICT）原子累加，见 AssetTotalSupplyMapper.xml 的 addTotalSupply
CREATE TABLE asset_total_supply (
    id BIGSERIAL PRIMARY KEY,                                   -- 主键，自增
    currency_id BIGINT NOT NULL UNIQUE REFERENCES currencies(id), -- 资产 ID，唯一约束保证每种资产仅一行（upsert 冲突依据）
    total_supply DECIMAL(32,18) NOT NULL DEFAULT 0,             -- 累计发行量（人可读单位，非最小单位）
    created_at TIMESTAMPTZ DEFAULT NOW(),                       -- 创建时间（首笔入账时间）
    updated_at TIMESTAMPTZ DEFAULT NOW()                        -- 最后更新时间（最近一次入账时间）
);

-- ============================================================
-- ③ 充值日志幂等键改造（Deposits）
-- ============================================================

-- 新增 log_index 列：交易在区块内的日志序号
-- 背景：一笔链上交易可能包含多笔转账事件（如 ERC20 合约的多次 Transfer log），
--       仅靠 tx_hash 无法区分同一交易内的多笔充值，必须以 log_index 区分
ALTER TABLE deposits ADD COLUMN log_index BIGINT NOT NULL DEFAULT 0;
-- 移除原有 tx_hash 全局唯一约束（约束名 deposits_tx_hash_key 为 PostgreSQL 对 UNIQUE 列的默认命名）
-- 否则同一 tx_hash 的第二笔充值事件将无法入库
ALTER TABLE deposits DROP CONSTRAINT deposits_tx_hash_key;
-- 重建幂等唯一索引：currency_id + tx_hash + log_index
-- 对应充值回调的幂等键 chain + txHash + logIndex（chain+asset 先解析为 currency_id）：
-- 重复回调将违反唯一约束/先查后插判断，保证同一充值事件只入账一次
CREATE UNIQUE INDEX uk_deposits_tx ON deposits(currency_id, tx_hash, log_index);
