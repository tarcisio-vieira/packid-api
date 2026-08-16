# VSGI Condomínio — mensagens e paginação (15/08/2026)

## Mensagens para o usuário

- Erros da API agora retornam mensagem JSON amigável para validação, conflito, registro não encontrado, dados inválidos e erro inesperado.
- O frontend traduz falhas de HTTP e falhas de conexão para mensagens em português.
- Erros aparecem também em alerta flutuante no topo, inclusive quando há um modal aberto.
- Operações concluídas exibem confirmação de sucesso para cadastro, foto, visita, entrega e ocupação.
- Botões de ação mostram estados como `Salvando...`, `Registrando...` e `Processando...` enquanto aguardam resposta.
- Erro de câmera/scanner informa que o usuário pode verificar a permissão ou digitar o código manualmente.

## Paginação

Todos os grids de dados passam a iniciar com **10 linhas por página** e permitem selecionar:

- 5 linhas
- 10 linhas
- 50 linhas

Aplicado em:

- Gestão do condomínio: Condôminos, Entregadores, Visitantes, Bicicletas, Pets e Veículos.
- Histórico de encomendas da tela PackID.
- Encomendas na visão do apartamento.
- Visitas na visão do apartamento e histórico do visitante.
- Entregas na visão do apartamento e histórico do entregador.

A lista compacta `Últimas encomendas` da Home continua compacta, pois não é um grid e foi criada como resumo rápido no painel lateral.

## Banco de dados

Nenhuma migration nova é necessária para estas alterações.
