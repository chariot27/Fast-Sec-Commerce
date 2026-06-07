-- Ativar extensão para UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Não há extensão nativa para uuidv7 no PG16 ainda, mas podemos preparar a estrutura.
-- Usaremos uuid para as chaves primárias.

-- Tabela de transações com particionamento por data (RANGE)
CREATE TABLE IF NOT EXISTS transactions (
    id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Criar partições mensais como exemplo
CREATE TABLE transactions_2026_06 PARTITION OF transactions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE transactions_2026_07 PARTITION OF transactions
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- Index no customer_id
CREATE INDEX idx_transactions_customer ON transactions (customer_id);
