-- Resultado esperado depois da correção:
-- total_visiveis = 336, validas = 336, invalidas = 0, faltantes = 0.
WITH target_condo AS (
    SELECT c.id AS condominium_id, c.tenant_id
      FROM public.condominium c
     WHERE c.deleted = false
       AND (
            regexp_replace(COALESCE(c.document_number, ''), '[^0-9]', '', 'g') = '26296992000130'
            OR LOWER(TRIM(c.name)) LIKE '%recanto tropical%'
       )
),
expected_codes AS (
    SELECT
        tc.tenant_id,
        tc.condominium_id,
        b::text || f::text || LPAD(a::text, 2, '0') AS code
      FROM target_condo tc
      CROSS JOIN generate_series(1, 4) AS b
      CROSS JOIN generate_series(1, 12) AS f
      CROSS JOIN LATERAL generate_series(1, CASE WHEN b = 1 THEN 4 ELSE 8 END) AS a
),
visible_units AS (
    SELECT ru.*
      FROM public.residential_unit ru
      JOIN target_condo tc
        ON tc.tenant_id = ru.tenant_id
       AND tc.condominium_id = ru.condominium_id
     WHERE ru.deleted = false
)
SELECT
    (SELECT COUNT(*) FROM visible_units) AS total_visiveis,
    (SELECT COUNT(*)
       FROM visible_units vu
      WHERE EXISTS (
          SELECT 1 FROM expected_codes ec
           WHERE ec.tenant_id = vu.tenant_id
             AND ec.condominium_id = vu.condominium_id
             AND ec.code = vu.code
      )) AS validas,
    (SELECT COUNT(*)
       FROM visible_units vu
      WHERE NOT EXISTS (
          SELECT 1 FROM expected_codes ec
           WHERE ec.tenant_id = vu.tenant_id
             AND ec.condominium_id = vu.condominium_id
             AND ec.code = vu.code
      )) AS invalidas,
    (SELECT COUNT(*)
       FROM expected_codes ec
      WHERE NOT EXISTS (
          SELECT 1 FROM visible_units vu
           WHERE vu.tenant_id = ec.tenant_id
             AND vu.condominium_id = ec.condominium_id
             AND vu.code = ec.code
      )) AS faltantes;

-- Lista qualquer unidade inválida que ainda esteja visível.
WITH target_condo AS (
    SELECT c.id AS condominium_id, c.tenant_id
      FROM public.condominium c
     WHERE c.deleted = false
       AND (
            regexp_replace(COALESCE(c.document_number, ''), '[^0-9]', '', 'g') = '26296992000130'
            OR LOWER(TRIM(c.name)) LIKE '%recanto tropical%'
       )
),
expected_codes AS (
    SELECT
        tc.tenant_id,
        tc.condominium_id,
        b::text || f::text || LPAD(a::text, 2, '0') AS code
      FROM target_condo tc
      CROSS JOIN generate_series(1, 4) AS b
      CROSS JOIN generate_series(1, 12) AS f
      CROSS JOIN LATERAL generate_series(1, CASE WHEN b = 1 THEN 4 ELSE 8 END) AS a
)
SELECT ru.id, ru.code, ru.name
  FROM public.residential_unit ru
  JOIN target_condo tc
    ON tc.tenant_id = ru.tenant_id
   AND tc.condominium_id = ru.condominium_id
 WHERE ru.deleted = false
   AND NOT EXISTS (
       SELECT 1 FROM expected_codes ec
        WHERE ec.tenant_id = ru.tenant_id
          AND ec.condominium_id = ru.condominium_id
          AND ec.code = ru.code
   )
 ORDER BY ru.code;
