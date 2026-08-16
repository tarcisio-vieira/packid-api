# VSGI Condomínio — Prestadores de serviço e otimização de imagens

## Novas áreas de gestão

A Gestão do condomínio ganhou duas abas:

- **Prestadores de serviço**: cadastro do profissional, vínculo com empresa prestadora, foto do prestador, foto do CPF, foto do RG, telefone, e-mail, CPF/documento, RG, observações e status ativo/inativo.
- **Empresas prestadoras**: cadastro separado das empresas, com nome, nome fantasia, CNPJ/documento, telefone, e-mail, contato, endereço, cidade, estado, CEP, observações e status ativo/inativo.

No cadastro do prestador, o campo **Empresa prestadora** utiliza pesquisa/autocomplete. A empresa deve ser cadastrada antes e estar ativa para ser selecionada.

## Registro diário do serviço

Cada prestador possui uma ação verde **Registrar serviço realizado**. Cada execução cria um registro histórico independente contendo:

- prestador;
- empresa vinculada ao prestador;
- data e hora;
- descrição do serviço;
- observação;
- destino do serviço.

O destino pode ser:

1. **Unidade** — exige bloco + apartamento. O registro aparece na visão consolidada daquela unidade e respeita o período da ocupação selecionada.
2. **Condomínio como um todo** — não exige bloco/apartamento e aparece no histórico geral **Serviços do condomínio** na aba de prestadores.

Também é possível cadastrar um prestador novo e, no mesmo fluxo, marcar **Cadastrar e registrar o serviço de hoje agora**.

## Documentos e fotos no Google Drive

Nenhuma imagem é gravada como binário no PostgreSQL. O banco mantém somente IDs/metadados do Google Drive.

Para prestadores, as imagens são organizadas na conta Google oficial em:

`VSGI-Condominium / Service Providers / Provider {UUID}`

Nessa pasta ficam:

- foto do prestador;
- foto do CPF;
- foto do RG.

As três imagens podem ser escolhidas de arquivo ou capturadas diretamente pela câmera do equipamento. Para CPF/RG, a interface tenta priorizar a câmera traseira/ambiente quando disponível.

## Redimensionamento e compactação

Todas as novas imagens enviadas pelos cadastros passam pelo backend antes do upload:

- entrada: JPG ou PNG;
- limite de entrada: 12 MB;
- maior dimensão: **1280 px**;
- proporção preservada;
- saída: **JPEG**;
- qualidade JPEG: **82%**;
- transparência PNG recebe fundo branco.

Isso reduz o consumo de espaço no Google Drive e torna a abertura dos grids mais rápida.

## Banco de dados

Nova migration Liquibase:

`db.changelog-0019-service-providers.yaml`

Ela cria:

- `service_company`;
- `service_record`;
- vínculo `registry_entry.service_company_id`;
- metadados das fotos de CPF e RG em `registry_entry`;
- índices para empresa, prestador e histórico por unidade/data.

Com o Liquibase habilitado, a migration é aplicada automaticamente na inicialização da API.
