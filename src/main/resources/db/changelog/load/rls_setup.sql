-- Rode: sudo -iu postgres psql -d packid -f rls_setup.sql

-- role da aplicação (opcional mas recomendado)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'packid_app') THEN
    CREATE ROLE packid_app NOINHERIT;
  END IF;
END $$;

-- Ajuste o usuário real da app (packid_user)
GRANT packid_app TO packid_user;

GRANT USAGE ON SCHEMA public TO packid_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO packid_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO packid_app;

-- Tabela tenant (não tem tenant_id)
ALTER TABLE public.tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenant FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname='public' AND tablename='tenant' AND policyname='tenant_isolation'
  ) THEN
    EXECUTE $p$
      CREATE POLICY tenant_isolation ON public.tenant
      USING (id = current_setting('app.tenant_id', true)::uuid)
      WITH CHECK (id = current_setting('app.tenant_id', true)::uuid)
    $p$;
  END IF;
END $$;

-- Todas as tabelas que tiverem tenant_id: policy padrão
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT table_schema, table_name
    FROM information_schema.columns
    WHERE column_name = 'tenant_id'
      AND table_schema = 'public'
    GROUP BY table_schema, table_name
  LOOP
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', r.table_schema, r.table_name);
    EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY',  r.table_schema, r.table_name);

    IF NOT EXISTS (
      SELECT 1 FROM pg_policies
      WHERE schemaname = r.table_schema
        AND tablename  = r.table_name
        AND policyname = 'tenant_isolation'
    ) THEN
      EXECUTE format(
        'CREATE POLICY tenant_isolation ON %I.%I
         USING (tenant_id = current_setting(''app.tenant_id'', true)::uuid)
         WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true)::uuid)',
        r.table_schema, r.table_name
      );
    END IF;
  END LOOP;
END $$;
