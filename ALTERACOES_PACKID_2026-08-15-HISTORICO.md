# PackID — visão por apartamento, visitantes e entregas

Implementação adicionada sobre a versão com fotos no Google Drive.

## Banco / API

- Novo tipo de cadastro `VISITOR` em `registry_entry`.
- Nova tabela `visitor_visit` para registrar cada visita sem duplicar o visitante.
- Nova tabela `delivery_record` para registrar cada entrega sem duplicar o entregador.
- Os históricos são vinculados por `tenant_id + bloco + apartamento`.
- Cada entrega registra se o entregador estava autorizado a entrar.
- Quando um visitante/entregador é enviado novamente com o mesmo documento e tipo, o cadastro existente é reutilizado; o novo evento fica somente no histórico.
- Novo endpoint `GET /api/registry/unit-summary?block=...&apartment=...` reúne:
  - condôminos;
  - pets;
  - veículos;
  - bicicletas;
  - visitas;
  - entregas.
- Novos endpoints:
  - `POST /api/visits`
  - `GET /api/visits?visitorId=...`
  - `POST /api/deliveries`
  - `GET /api/deliveries?deliveryPersonId=...`
- Liquibase: `db.changelog-0014-access-history.yaml`.

## Regra de negócio

O cadastro-base de visitante/entregador guarda os dados da pessoa. Cada nova passagem pelo condomínio gera somente um registro de visita/entrega, com data/hora e destino. Isso preserva todo o histórico sem criar várias pessoas iguais.
