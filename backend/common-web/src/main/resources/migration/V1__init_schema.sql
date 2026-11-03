-- V1: Initial schema for crypto exchange
-- Core tables for Phase 1 (Spot Trading + Wallet)

-- Users & Authentication
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uid VARCHAR(32) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    google_auth_secret VARCHAR(32),
    google_auth_enabled BOOLEAN DEFAULT FALSE,
    kyc_level VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ref_code VARCHAR(16) UNIQUE,
    referred_by BIGINT REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE user_kyc_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    doc_type VARCHAR(20) NOT NULL,
    doc_front_url VARCHAR(500),
    doc_back_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT REFERENCES users(id),
    reject_reason VARCHAR(500),
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    refresh_token_hash VARCHAR(64),
    ip_address VARCHAR(45),
    device_info VARCHAR(500),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Currencies
CREATE TABLE currencies (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    chain VARCHAR(50),
    contract_addr VARCHAR(42),
    decimals INT DEFAULT 18,
    min_withdraw DECIMAL(32,18),
    withdraw_fee DECIMAL(32,18),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deposit_confirm INT DEFAULT 2,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Wallets
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currencies(id),
    wallet_type VARCHAR(20) NOT NULL DEFAULT 'SPOT',
    balance DECIMAL(32,18) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(32,18) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, currency_id, wallet_type)
);

CREATE TABLE deposit_addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currencies(id),
    address VARCHAR(100),
    derivation_path VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, currency_id)
);

CREATE TABLE deposits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currencies(id),
    tx_hash VARCHAR(200) UNIQUE,
    from_address VARCHAR(100),
    to_address VARCHAR(100),
    amount DECIMAL(32,18),
    fee DECIMAL(32,18),
    confirmations INT DEFAULT 0,
    required_confirm INT DEFAULT 2,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    block_height BIGINT,
    block_hash VARCHAR(200),
    credited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE withdrawals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currencies(id),
    request_id VARCHAR(64) UNIQUE,
    to_address VARCHAR(100),
    amount DECIMAL(32,18),
    fee DECIMAL(32,18),
    actual_amount DECIMAL(32,18),
    tx_hash VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_score INT DEFAULT 0,
    approver_id BIGINT,
    memo VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Trading Pairs
CREATE TABLE trading_pairs (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) UNIQUE NOT NULL,
    base_currency_id BIGINT NOT NULL REFERENCES currencies(id),
    quote_currency_id BIGINT NOT NULL REFERENCES currencies(id),
    price_precision INT DEFAULT 2,
    qty_precision INT DEFAULT 5,
    min_order_qty DECIMAL(32,18),
    min_order_notional DECIMAL(32,18),
    maker_fee_rate DECIMAL(8,6) DEFAULT 0.001,
    taker_fee_rate DECIMAL(8,6) DEFAULT 0.001,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Orders
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    pair_id BIGINT NOT NULL REFERENCES trading_pairs(id),
    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    price DECIMAL(32,18),
    stop_price DECIMAL(32,18),
    original_qty DECIMAL(32,18),
    executed_qty DECIMAL(32,18) NOT NULL DEFAULT 0,
    executed_quote DECIMAL(32,18) NOT NULL DEFAULT 0,
    avg_fill_price DECIMAL(32,18),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    time_in_force VARCHAR(10) DEFAULT 'GTC',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_orders_user_pair_status ON orders(user_id, pair_id, status);
CREATE INDEX idx_orders_order_id ON orders(order_id);

-- Trades
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) UNIQUE NOT NULL,
    pair_id BIGINT NOT NULL REFERENCES trading_pairs(id),
    maker_order_id BIGINT NOT NULL REFERENCES orders(id),
    taker_order_id BIGINT NOT NULL REFERENCES orders(id),
    maker_user_id BIGINT NOT NULL,
    taker_user_id BIGINT NOT NULL,
    price DECIMAL(32,18),
    qty DECIMAL(32,18),
    quote_amount DECIMAL(32,18),
    maker_fee DECIMAL(32,18),
    taker_fee DECIMAL(32,18),
    is_buyer_maker BOOLEAN,
    executed_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_trades_pair_time ON trades(pair_id, executed_at DESC);
CREATE INDEX idx_trades_user_time ON trades(maker_user_id, executed_at DESC);

-- Klines for TimescaleDB (regular table for standard PostgreSQL)
CREATE TABLE klines (
    pair_id BIGINT NOT NULL,
    interval VARCHAR(5) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open DECIMAL(32,18),
    high DECIMAL(32,18),
    low DECIMAL(32,18),
    close DECIMAL(32,18),
    volume DECIMAL(32,18),
    quote_volume DECIMAL(32,18),
    trades_count INT DEFAULT 0,
    PRIMARY KEY (pair_id, interval, open_time)
);

-- Risk Events
CREATE TABLE risk_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(30),
    severity VARCHAR(10),
    details JSONB,
    action_taken VARCHAR(30),
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insert default currencies
INSERT INTO currencies (symbol, full_name, chain, decimals, min_withdraw, withdraw_fee, deposit_confirm) VALUES
('BTC', 'Bitcoin', 'Bitcoin', 8, 0.001, 0.0005, 2),
('ETH', 'Ethereum', 'Ethereum', 18, 0.01, 0.005, 12),
('USDT', 'Tether USD', 'Ethereum', 6, 10, 1, 12),
('USDT_TRC20', 'Tether USD', 'TRON', 6, 10, 1, 20),
('BNB', 'BNB', 'BSC', 18, 0.01, 0.0005, 10);

-- Insert default trading pairs
INSERT INTO trading_pairs (symbol, base_currency_id, quote_currency_id, price_precision, qty_precision, min_order_qty, min_order_notional) VALUES
('BTCUSDT', 1, 3, 2, 5, 0.00001, 10),
('ETHUSDT', 2, 3, 2, 4, 0.0001, 10),
('BNBUSDT', 5, 3, 2, 3, 0.001, 10);
