# VSGI Condomínio — Entregadores com documentos e empresas

Data: 16/08/2026

## Objetivo

A área de Entregadores passa a utilizar os mesmos recursos de identificação e vínculo empresarial já existentes para Prestadores de serviço.

## Frontend

- Entregador possui foto principal.
- Entregador pode armazenar foto do CPF.
- Entregador pode armazenar foto da identidade/RG.
- Fotos podem ser escolhidas do dispositivo ou capturadas pela câmera.
- As imagens continuam passando pelo redimensionamento/compactação já implementado antes do envio ao Google Drive.
- Campo de RG/Identidade adicionado ao cadastro do entregador.
- Campo de e-mail adicionado ao cadastro do entregador.
- O campo texto livre Empresa/Transportadora foi substituído pelo mesmo Autocomplete pesquisável utilizado pelos prestadores.
- O Autocomplete permite digitar para pesquisar empresas por nome/nome fantasia.
- A aba visual `Empresas prestadoras` foi renomeada para `Empresas`, pois o mesmo cadastro passa a atender prestadores e entregadores/transportadoras.
- O cadastro de empresas continua único, evitando duplicidade de transportadoras e empresas prestadoras.
- Na grade e na visualização selecionada, Entregadores passam a exibir a empresa cadastrada no mesmo padrão dos prestadores.
- Correção do import `Paper` não utilizado em `ServiceCompanyPanel.tsx`, que impedia o build TypeScript com `noUnusedLocals`.

## Backend

- `RegistryEntryService.syncServiceCompany` agora aceita `SERVICE_PROVIDER` e `DELIVERY_PERSON`.
- Para novos/alterados entregadores, a empresa/transportadora é selecionada pelo `service_company_id` e validada no tenant atual.
- `RegistryDocumentPhotoService` agora permite fotos de CPF e RG/Identidade tanto para prestadores quanto para entregadores.
- Não foi necessária nova migration: as colunas `service_company_id`, `cpf_photo_*` e `rg_photo_*` já existem desde a migration 0019.
- O histórico de entregas continua usando o nome da empresa gravado no cadastro do entregador.

## Google Drive

Novos uploads de entregadores passam a ser organizados em pasta própria:

```text
VSGI-Condominium/
  Delivery People/
    Delivery Person <UUID>/
      foto principal
      cpf-...
      rg-...
```

Fotos antigas não são movidas automaticamente.

## Correção Hibernate incluída

Esta entrega também inclui a correção já identificada no cadastro de condômino:

- `RegistryEntry.occupancy` deixou de mapear a associação com `tenant_id + occupancy_id` e passou a usar apenas `occupancy_id -> apartment_occupancy.id`.
- A mudança evita o erro `Identifier of an instance of ApartmentOccupancy was altered...` observado no commit da transação.
- Não há mudança no banco para essa correção; as constraints existentes continuam protegendo a integridade dos dados.

## Validação realizada neste ambiente

- `RegistryScreen.tsx`: parsing/transpilação TypeScript/TSX sem erro de sintaxe.
- `ServiceCompanyPanel.tsx`: parsing/transpilação TypeScript/TSX sem erro de sintaxe.
- Arquivos Java alterados: verificação de sintaxe sem erros estruturais; a compilação completa não foi executada porque as dependências Maven não estão disponíveis localmente neste ambiente.
- `npm install --offline` não pôde concluir porque o pacote `@yudiel/react-qr-scanner` não estava no cache. No ambiente do desenvolvedor, executar o build normal.

## Build antes do próximo deploy EC2

Frontend:

```bash
cd /c/Projetos/vsgi/packid/packid-web
npm run build:prod
grep -n "packid" dist/index.html
```

Esperado: caminhos `/packid/...`, nunca `/packid-dev/...`.

Depois copiar o build para a API:

```bash
cd /c/Projetos/vsgi/packid/packid-api
rm -rf src/main/resources/static/*
cp -R ../packid-web/dist/. src/main/resources/static/
./mvnw clean package -DskipTests
```

Somente após `BUILD SUCCESS` enviar o WAR novo para a EC2.
