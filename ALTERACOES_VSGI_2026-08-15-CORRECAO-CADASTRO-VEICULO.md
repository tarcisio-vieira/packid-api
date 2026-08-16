# Correção — cadastro de veículo / RegistryEntry

## Erro observado

Ao salvar um novo veículo, o backend respondia HTTP 500 com Hibernate:

`Identifier of an instance of 'RegistryEntry' was altered ...`

O registro chegava a ser inserido/flushado, mas consultas adicionais executadas ainda dentro da mesma transação de criação carregavam outras entidades `RegistryEntry` e consultavam a conta Google oficial durante a montagem da resposta. Esse fluxo deixava o contexto de persistência do Hibernate em estado inconsistente.

## Correções aplicadas

1. A busca de destinatários de notificação da unidade não carrega mais entidades `RegistryEntry` completas. Agora o repositório retorna somente os e-mails dos condôminos ativos da unidade.
2. A conta Google oficial é consultada uma única vez antes da alteração/persistência nos fluxos de criação e atualização.
3. O mapeamento `RegistryEntry -> RegistryEntryResponse` passou a receber o e-mail oficial já resolvido, evitando consulta ao banco durante a montagem da resposta após o `save`.
4. `getAll` e `unit-summary` também reutilizam a conta oficial em vez de executar uma consulta por linha, eliminando N+1 desnecessário.

## Banco

Não há nova migration e nenhuma alteração de estrutura no banco.

## Teste recomendado

1. Reiniciar a API.
2. Cadastrar um veículo novo em uma unidade com ocupação ativa.
3. Confirmar retorno HTTP 201 de `POST /api/registry`.
4. Se houver foto selecionada, confirmar em seguida o `PUT /api/registry/{id}/photo`.
5. Confirmar que o veículo aparece no grid e na view da unidade.
