-- V21: torna product_variants.size nullable
-- Produtos simples sincronizados do Bling (isVariacao=false, isVariacaoPai=false)
-- terão uma única ProductVariant sem tamanho (size = null), já que o conceito
-- de "atributos" (tamanho) só existe no Bling para variações de fato.

ALTER TABLE product_variants
    ALTER COLUMN size DROP NOT NULL;