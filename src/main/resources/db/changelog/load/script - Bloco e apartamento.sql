-- Rode como postgres/superuser (recomendado), por exemplo:
  -- sudo -iu postgres psql -d packid -f seed_blocks_apartments.sql

  -- Se você não tiver pgcrypto habilitado, habilite (para gerar UUID no script).
  -- Se não quiser habilitar extensão, me avise que eu te mando uma versão sem UUID gerado no banco.
  CREATE EXTENSION IF NOT EXISTS pgcrypto;

  DO $$
  DECLARE
  v_condominium_id uuid;
  v_block_id uuid;
  b int;
  f int;
  u int;
  BEGIN
  -- Para cada condomínio existente (não deletado)
  FOR v_condominium_id IN
  SELECT id
  FROM condominium
  WHERE deleted = false
  LOOP

-- Cria 4 blocos: 1..4
  FOR b IN 1..4 LOOP

  INSERT INTO block (
  id, condominium_id, code, name, floors_count, units_per_floor,
  created_by, deleted
  )
  VALUES (
  gen_random_uuid(),
  v_condominium_id,
  b::text,
  'Block ' || b::text,
  12,
  8,
  'seed',
  false
  )
  ON CONFLICT (condominium_id, code) DO NOTHING;

  -- Pega o id do bloco (existente ou recém-criado)
  SELECT id
  INTO v_block_id
  FROM block
  WHERE condominium_id = v_condominium_id
  AND code = b::text
  AND deleted = false
  LIMIT 1;

-- Cria apartamentos: andares 1..12 e unidades 1..8
  FOR f IN 1..12 LOOP
  FOR u IN 1..8 LOOP

  INSERT INTO apartment (
  id, block_id, floor, apartment_number,
  created_by, deleted
  )
  VALUES (
  gen_random_uuid(),
  v_block_id,
  f,
  u,
  'seed',
  false
  )
  ON CONFLICT (block_id, floor, apartment_number) DO NOTHING;

  END LOOP;
  END LOOP;

  END LOOP;

  END LOOP;
  END $$;

-- Exemplo: como montar o "código do apartamento" no formato que você quer:
  -- bloco + andar (sem zero à esquerda) + unidade (2 dígitos com zero à esquerda)
  -- 2 + 6 + 08 = 2608
  -- 1 + 11 + 02 = 11102
  -- 4 + 12 + 01 = 41201

-- Query exemplo para listar com o código:
  -- SELECT
  --   b.code AS bloco,
  --   a.floor AS andar,
  --   a.apartment_number AS unidade,
  --   (b.code || a.floor::text || lpad(a.apartment_number::text, 2, '0')) AS apt_code
  -- FROM apartment a
  -- JOIN block b ON b.id = a.block_id
  -- ORDER BY b.code::int, a.floor, a.apartment_number;
