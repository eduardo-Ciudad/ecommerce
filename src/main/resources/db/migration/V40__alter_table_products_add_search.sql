ALTER TABLE products ADD COLUMN search_text VARCHAR(300);

UPDATE products p
SET search_text = lower(translate(
        p.name || ' ' || c.name,
        'áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ–—-',
        'aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC   '))
FROM categories c
WHERE c.id = p.category_id;