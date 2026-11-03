-- V2: Add contract trading tables

CREATE TABLE contracts (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(30) UNIQUE NOT NULL,
    underlying VARCHAR(20),
    quote_asset VARCHAR(20),
    contract_type VARCHAR(20) DEFAULT 'PERPETUAL',
    max_leverage INT DEFAULT 125,
    min_notional DECIMAL(32,18) DEFAULT 5,
    tick_size DECIMAL(32,18) DEFAULT 0.01,
    lot_size DECIMAL(32,18) DEFAULT 0.001,
    funding_rate DECIMAL(12,8) DEFAULT 0,
    funding_interval INT DEFAULT 8,
    maintenance_margin_ratio DECIMAL(6,4) DEFAULT 0.005,
    maker_fee_rate DECIMAL(8,6) DEFAULT 0.0002,
    taker_fee_rate DECIMAL(8,6) DEFAULT 0.0005,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE contract_positions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL REFERENCES contracts(id),
    side VARCHAR(10) NOT NULL,
    leverage INT DEFAULT 1,
    entry_price DECIMAL(32,18),
    mark_price DECIMAL(32,18),
    qty DECIMAL(32,18),
    margin DECIMAL(32,18),
    margin_type VARCHAR(10) DEFAULT 'ISOLATED',
    unrealized_pnl DECIMAL(32,18) DEFAULT 0,
    realized_pnl DECIMAL(32,18) DEFAULT 0,
    liquidation_price DECIMAL(32,18),
    take_profit_price DECIMAL(32,18),
    stop_loss_price DECIMAL(32,18),
    status VARCHAR(20) DEFAULT 'OPEN',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE contract_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL REFERENCES contracts(id),
    side VARCHAR(10) NOT NULL,
    position_effect VARCHAR(10) DEFAULT 'OPEN',
    order_type VARCHAR(20) NOT NULL,
    price DECIMAL(32,18),
    qty DECIMAL(32,18),
    executed_qty DECIMAL(32,18) DEFAULT 0,
    avg_fill_price DECIMAL(32,18),
    leverage INT DEFAULT 1,
    reduce_only BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'NEW',
    time_in_force VARCHAR(10) DEFAULT 'GTC',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insert default contracts
INSERT INTO contracts (symbol, underlying, quote_asset, max_leverage, tick_size, lot_size) VALUES
('BTCUSDT_PERP', 'BTC', 'USDT', 125, 0.01, 0.001),
('ETHUSDT_PERP', 'ETH', 'USDT', 100, 0.01, 0.01);
