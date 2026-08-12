-- V19: armazenamento dos tokens OAuth 2.0 do Bling
-- Integração única da loja com o Bling (não é por usuário do e-commerce).

CREATE TABLE bling_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);