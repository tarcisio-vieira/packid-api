# VSGI Condomínio — controle de ocupações

Implementação do ciclo de troca de moradores por bloco/apartamento.

## Regras

- Condôminos, veículos, pets e bicicletas pertencem a uma ocupação do apartamento.
- Uma unidade pode ter várias ocupações históricas, mas somente uma ocupação ativa.
- Ao encerrar a ocupação, os cadastros vinculados ficam inativos; nada é excluído.
- Encomendas, visitas e entregas permanecem como histórico operacional.
- Na visão do apartamento, o histórico operacional é filtrado pelo período da ocupação selecionada.
- É possível navegar entre ocupação atual e ocupações anteriores.
- Ao cadastrar um condômino/veículo/pet/bicicleta ativo em uma unidade sem ocupação ativa, o backend cria uma ocupação automaticamente com a data atual.
- Para controlar uma data de entrada específica, use o botão **Nova ocupação** antes de cadastrar os novos registros.

## Banco

Migration nova: `db.changelog-0016-apartment-occupancy.yaml`.

Cria `apartment_occupancy` e adiciona `registry_entry.occupancy_id`.
A migration cria uma ocupação ativa para unidades que já tenham cadastros ativos e vincula os registros atuais.

## Endpoints

- `POST /api/occupancies/start`
- `POST /api/occupancies/end`
- `GET /api/occupancies?block=...&apartment=...`
- `GET /api/registry/unit-summary?...&occupancyId=...`

## Fluxo recomendado para troca de inquilino

1. Abrir a visão do apartamento.
2. Clicar em **Encerrar ocupação** e informar a data de saída.
3. O sistema inativa condôminos, veículos, pets e bicicletas daquela ocupação.
4. Encomendas, visitas e entregas continuam preservadas.
5. Clicar em **Nova ocupação** e informar a data de entrada do novo morador.
6. Cadastrar os novos condôminos, veículos, pets e bicicletas normalmente.
