# PackID por bloco/apartamento na visão do condômino

A visão consolidada de Bloco + Apartamento agora também retorna as encomendas PackID da unidade.

- Endpoint existente: `GET /api/registry/unit-summary?block={bloco}&apartment={apartamento}`
- Novo campo da resposta: `packIds`
- Busca isolada por tenant + bloco + apartamento.
- Ordenação: encomendas mais recentes primeiro.
- Limite atual: 200 encomendas por consulta.
- Registros antigos sem `building_block` também podem ser encontrados quando o PackID estiver ligado a uma pessoa que possua cadastro de condômino no mesmo bloco/apartamento.
- Não há nova migration de banco para esta alteração.
