# Alterações PackID - 15/08/2026

## Funcionalidades implementadas

1. **Cadastros do condomínio**
   - Novo menu **Cadastros**.
   - Abas para **Condôminos**, **Entregadores**, **Bicicletas**, **Pets** e **Veículos**.
   - Inclusão, edição, exclusão lógica, pesquisa e status ativo/inativo.
   - Condôminos possuem bloco/página e apartamento e são sincronizados com a tabela `person`, permitindo que um pacote novo seja associado ao morador quando existir exatamente um condômino ativo para a unidade.

2. **Pesquisa no grid de encomendas**
   - Campo de pesquisa por **código da encomenda**, **bloco/página**, **apartamento** ou **nome do condômino**.
   - O bloco/página passa a ser persistido em `pack_id.building_block` para novos registros.
   - Registros antigos continuam pesquisáveis pelo código/apartamento, mas podem exibir `-` no bloco/página porque esse valor não era persistido anteriormente.

3. **Sessão de login**
   - Timeout do servidor configurado para **24 horas**.
   - Cookie da sessão configurado com validade de **24 horas**.
   - Em produção o cookie é `HttpOnly`, `Secure` e `SameSite=Lax`.
   - `/api/**` agora exige autenticação de forma efetiva e devolve HTTP 401 para chamadas AJAX sem sessão.

## Banco de dados / Liquibase

Foram adicionados os changelogs:

- `db.changelog-0011-registry-entry.yaml`: cria `registry_entry`.
- `db.changelog-0012-packid-building-block.yaml`: adiciona `building_block` em `pack_id`.

O Liquibase aplica as alterações automaticamente na inicialização, conforme a configuração atual do projeto.

## Build

### Front-end

```bash
npm ci
npm run build:prod
```

### Back-end

Windows:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Linux/macOS:

```bash
./mvnw clean package -DskipTests
```

Foi incluído o arquivo `.mvn/wrapper/maven-wrapper.properties`, ausente no ZIP original.

## Integração front + WAR

Se a produção continuar servindo o React pelo próprio Spring Boot, após o build do front copie o conteúdo de `packid-web/dist/` para `packid-api/src/main/resources/static/` e depois gere o WAR do back-end.

## Fotos dos cadastros no Google Drive

- Cadastros de **Condôminos, Entregadores, Bicicletas, Pets e Veículos** agora aceitam foto.
- A imagem **não é gravada no PostgreSQL**. O arquivo é criado no Google Drive da conta Google que está autenticada no PackID.
- O Drive cria/usa a pasta **`PackID - Fotos`**.
- No banco ficam somente metadados de referência: ID do arquivo no Drive, MIME type, nome original e e-mail da conta que fez o upload.
- A foto é privada: o sistema não cria link público. A visualização/troca/exclusão exige a mesma conta Google que realizou o upload.
- Formatos aceitos: JPG e PNG, até 5 MB.
- Novo changelog Liquibase: `db.changelog-0013-registry-photo-drive.yaml`.
- O OAuth Google passou a solicitar o escopo mínimo `https://www.googleapis.com/auth/drive.file` e `access_type=offline`, permitindo renovação do token durante a sessão.

### Configuração necessária no Google Cloud

1. Ativar a **Google Drive API** no mesmo projeto do `GOOGLE_CLIENT_ID` utilizado pelo PackID.
2. Garantir que o OAuth Consent Screen permita o escopo `.../auth/drive.file`.
3. Após publicar a versão, sair do PackID e entrar novamente com Google para conceder a nova permissão.
