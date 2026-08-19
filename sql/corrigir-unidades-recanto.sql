BEGIN;

-- 1) Cria/reativa e padroniza exatamente as 336 unidades válidas.
WITH target_condo AS (
    SELECT c.id AS condominium_id, c.tenant_id
      FROM public.condominium c
     WHERE c.deleted = false
       AND (
            regexp_replace(COALESCE(c.document_number, ''), '[^0-9]', '', 'g') = '26296992000130'
            OR LOWER(TRIM(c.name)) LIKE '%recanto tropical%'
       )
),
expected_units AS (
    SELECT
        tc.tenant_id,
        tc.condominium_id,
        b::text || f::text || LPAD(a::text, 2, '0') AS code,
        'Bloco ' || b::text || ' - Andar ' || f::text || ' - Apto ' || LPAD(a::text, 2, '0') AS name
      FROM target_condo tc
      CROSS JOIN generate_series(1, 4) AS b
      CROSS JOIN generate_series(1, 12) AS f
      CROSS JOIN LATERAL generate_series(1, CASE WHEN b = 1 THEN 4 ELSE 8 END) AS a
)
INSERT INTO public.residential_unit (
    id, tenant_id, condominium_id, code, name, active,
    created_at, created_by, deleted
)
SELECT
    gen_random_uuid(), eu.tenant_id, eu.condominium_id, eu.code, eu.name, true,
    CURRENT_TIMESTAMP, 'correcao-unidades-recanto', false
  FROM expected_units eu
ON CONFLICT (tenant_id, condominium_id, code)
DO UPDATE SET
    name = EXCLUDED.name,
    active = true,
    deleted = false,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'correcao-unidades-recanto';

-- 2) Reaponta PackIDs com bloco/apartamento válidos para a unidade canônica.
WITH target_condo AS (
    SELECT c.id AS condominium_id, c.tenant_id
      FROM public.condominium c
     WHERE c.deleted = false
       AND (
            regexp_replace(COALESCE(c.document_number, ''), '[^0-9]', '', 'g') = '26296992000130'
            OR LOWER(TRIM(c.name)) LIKE '%recanto tropical%'
       )
)
UPDATE public.pack_id p
   SET residential_unit_id = ru.id,
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'correcao-unidades-recanto'
  FROM target_condo tc
  JOIN public.residential_unit ru
    ON ru.tenant_id = tc.tenant_id
   AND ru.condominium_id = tc.condominium_id
   AND ru.deleted = false
 WHERE p.tenant_id = tc.tenant_id
   AND ru.code = TRIM(COALESCE(p.building_block, '')) || TRIM(COALESCE(p.apartment, ''))
   AND p.residential_unit_id IS DISTINCT FROM ru.id;

-- 3) Soft delete nas unidades inválidas; não apaga fisicamente por causa do histórico.
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
UPDATE public.residential_unit ru
   SET active = false,
       deleted = true,
       deleted_at = COALESCE(ru.deleted_at, CURRENT_TIMESTAMP),
       deleted_by = COALESCE(ru.deleted_by, 'correcao-unidades-recanto'),
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'correcao-unidades-recanto'
  FROM target_condo tc
 WHERE ru.tenant_id = tc.tenant_id
   AND ru.condominium_id = tc.condominium_id
   AND ru.deleted = false
   AND NOT EXISTS (
       SELECT 1
         FROM expected_codes ec
        WHERE ec.tenant_id = ru.tenant_id
          AND ec.condominium_id = ru.condominium_id
          AND ec.code = ru.code
   );

COMMIT;
