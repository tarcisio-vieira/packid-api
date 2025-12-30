-- =========================================
-- SEED INICIAL (Tenant + Condomínio + Pessoa + Usuário + Unidades)
-- Postgres
-- =========================================

-- 1) UUID helper (gen_random_uuid)
-- Se der erro de permissão, você pode remover esta linha e usar uuid_generate_v4() (uuid-ossp)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_slug        text := 'tenant-teste-recanto-tropical';
  v_created_by  text := 'seed@packid';
  v_email       text := 'tarcisio.vieira.dom@google.com';

  v_tenant_id   uuid;
  v_condo_id    uuid;
  v_person_id   uuid;
  v_user_id     uuid;
BEGIN
  -- ----------------------------
  -- TENANT (reutiliza por slug)
  -- ----------------------------
  SELECT id INTO v_tenant_id
  FROM tenant
  WHERE slug = v_slug;

  IF v_tenant_id IS NULL THEN
    v_tenant_id := gen_random_uuid();

    INSERT INTO tenant (id, name, slug, active, created_by, deleted)
    VALUES (
      v_tenant_id,
      'Tenant Teste - Recanto Tropical',
      v_slug,
      true,
      v_created_by,
      false
    );
  END IF;

  -- ----------------------------
  -- CONDOMINIUM (reutiliza por tenant_id + name)
  -- ----------------------------
  SELECT id INTO v_condo_id
  FROM condominium
  WHERE tenant_id = v_tenant_id
    AND name = 'Condomínio Recanto Tropical';

  IF v_condo_id IS NULL THEN
    v_condo_id := gen_random_uuid();

    INSERT INTO condominium (
      id, tenant_id, name,
      address_line1, address_line2, city, state, zip_code,
      created_by, deleted
    ) VALUES (
      v_condo_id, v_tenant_id, 'Condomínio Recanto Tropical',
      'Rua Fagundes Varella, 245', NULL, NULL, NULL, NULL,
      v_created_by, false
    );
  END IF;

  -- ----------------------------
  -- PERSON (reutiliza por tenant_id + email)
  -- ----------------------------
  SELECT id INTO v_person_id
  FROM person
  WHERE tenant_id = v_tenant_id
    AND email = v_email;

  IF v_person_id IS NULL THEN
    v_person_id := gen_random_uuid();

    INSERT INTO person (
      id, tenant_id, full_name, document, email, phone, person_type,
      created_by, deleted
    ) VALUES (
      v_person_id, v_tenant_id, 'Tarcisio Vieira', NULL, v_email, NULL, 'RESIDENT',
      v_created_by, false
    );
  END IF;

  -- ----------------------------
  -- APP_USER (reutiliza por tenant_id + email)
  -- ----------------------------
  SELECT id INTO v_user_id
  FROM app_user
  WHERE tenant_id = v_tenant_id
    AND email = v_email;

  IF v_user_id IS NULL THEN
    v_user_id := gen_random_uuid();

    INSERT INTO app_user (
      id, tenant_id, person_id,
      email, full_name,
      provider, provider_subject,
      role, enabled, last_login_at,
      created_by, deleted
    ) VALUES (
      v_user_id, v_tenant_id, v_person_id,
      v_email, 'Tarcisio Vieira',
      'GOOGLE', v_email,
      'ADMIN', true, NULL,
      v_created_by, false
    );
  END IF;

  -- ----------------------------
  -- RESIDENTIAL UNITS (Bloco 2)
  -- 12 andares, 8 aptos por andar:
  -- 2101..2108, 2201..2208, ..., 21201..21208
  -- ----------------------------
  INSERT INTO residential_unit (
    id, tenant_id, condominium_id,
    code, name,
    active,
    created_by, deleted
  )
  SELECT
    gen_random_uuid(),
    v_tenant_id,
    v_condo_id,
    ('2' || f::text || lpad(a::text, 2, '0')) AS code,
    ('Bloco 2 - Andar ' || f::text || ' - Apto ' || lpad(a::text, 2, '0')) AS name,
    true,
    v_created_by,
    false
  FROM generate_series(1, 12) AS f
  CROSS JOIN generate_series(1, 8) AS a
  ON CONFLICT (tenant_id, condominium_id, code) DO NOTHING;

  RAISE NOTICE 'Tenant: %', v_tenant_id;
  RAISE NOTICE 'Condominium: %', v_condo_id;
  RAISE NOTICE 'Person: %', v_person_id;
  RAISE NOTICE 'AppUser: %', v_user_id;
END $$;

-- -----------------------------------------
-- Conferências rápidas
-- -----------------------------------------
SELECT id, name, slug FROM tenant WHERE slug = 'tenant-teste-recanto-tropical';

SELECT id, tenant_id, name, address_line1
FROM condominium
WHERE name = 'Condomínio Recanto Tropical';

SELECT id, tenant_id, full_name, email, person_type
FROM person
WHERE email = 'tarcisio.vieira.dom@google.com';

SELECT id, tenant_id, email, provider, provider_subject, role, enabled
FROM app_user
WHERE email = 'tarcisio.vieira.dom@google.com';

-- Deve dar 96 unidades (12 * 8)
SELECT COUNT(*) AS total_unidades_bloco2
FROM residential_unit ru
JOIN condominium c ON c.id = ru.condominium_id
WHERE ru.code LIKE '2%'
  AND c.name = 'Condomínio Recanto Tropical';

-- Exemplos: primeiro e último
SELECT code, name
FROM residential_unit
WHERE code IN ('2101','2108','21201','21208')
ORDER BY code;


-- =========================================
-- UOM (Unidades de Medida) mais usadas
-- =========================================

-- 1) Troque pelo tenant_id do seu tenant de teste
-- (Se quiser pegar pelo slug:)
-- SELECT id FROM tenant WHERE slug = 'tenant-teste-recanto-tropical';

DO $$
DECLARE
  v_tenant_id  uuid := (SELECT id FROM tenant WHERE slug = 'tenant-teste-recanto-tropical');
  v_created_by text := 'seed@packid';
BEGIN
  IF v_tenant_id IS NULL THEN
    RAISE EXCEPTION 'Tenant não encontrado (slug tenant-teste-recanto-tropical).';
  END IF;

  INSERT INTO unit_of_measure (
    id, tenant_id, code, name, description, symbol,
    created_by, deleted
  )
  VALUES
    -- Quantidade / unidade
    (gen_random_uuid(), v_tenant_id, 'UN',  'Unidade',            'Quantidade unitária',                          'un',  v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'PC',  'Peça',               'Item individual / peça',                       'pc',  v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'CX',  'Caixa',              'Embalagem tipo caixa',                         'cx',  v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'PCT', 'Pacote',             'Embalagem tipo pacote',                        'pct', v_created_by, false),

    -- Massa
    (gen_random_uuid(), v_tenant_id, 'KG',  'Quilograma',         'Massa (kg)',                                   'kg',  v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'G',   'Grama',              'Massa (g)',                                    'g',   v_created_by, false),

    -- Volume
    (gen_random_uuid(), v_tenant_id, 'L',   'Litro',              'Volume (L)',                                   'L',   v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'ML',  'Mililitro',          'Volume (mL)',                                  'mL',  v_created_by, false),

    -- Comprimento
    (gen_random_uuid(), v_tenant_id, 'M',   'Metro',              'Comprimento (m)',                              'm',   v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'CM',  'Centímetro',         'Comprimento (cm)',                             'cm',  v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'MM',  'Milímetro',          'Comprimento (mm)',                             'mm',  v_created_by, false),

    -- Área
    (gen_random_uuid(), v_tenant_id, 'M2',  'Metro quadrado',     'Área (m²)',                                     'm²',  v_created_by, false),

    -- Tempo (se você usar em serviços/aluguéis)
    (gen_random_uuid(), v_tenant_id, 'H',   'Hora',               'Tempo (hora)',                                 'h',   v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'D',   'Dia',                'Tempo (dia)',                                  'd',   v_created_by, false),
    (gen_random_uuid(), v_tenant_id, 'MES', 'Mês',                'Tempo (mês)',                                  'mês', v_created_by, false)

  ON CONFLICT (tenant_id, code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        symbol      = EXCLUDED.symbol,
        updated_at  = CURRENT_TIMESTAMP,
        updated_by  = v_created_by;

END $$;

-- Conferir
SELECT tenant_id, code, name, symbol
FROM unit_of_measure
WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'tenant-teste-recanto-tropical')
ORDER BY code;

