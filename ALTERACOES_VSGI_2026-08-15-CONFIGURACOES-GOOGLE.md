# VSGI Condomínio - Configurações e conta Google oficial

## Nova área Configurações

Foi adicionada uma área **Configurações** no menu, visível para usuários com perfil `ADMIN`.

### Dados do condomínio

É possível consultar e alterar:

- Nome do condomínio
- CNPJ / documento
- Síndico / responsável
- E-mail do condomínio
- Telefone
- WhatsApp
- Endereço
- Complemento
- Cidade
- Estado
- CEP
- Observações
- Slug do tenant (somente leitura)

Ao alterar o nome, o sistema mantém sincronizados o nome do `tenant` e o nome do `condominium`.

## Conta Google oficial do condomínio

Na mesma tela existe a seção **Integração Google oficial**.

O administrador pode clicar em **Conectar conta Google** e escolher a conta institucional do condomínio, por exemplo `condominiorecanto@gmail.com`.

A conta oficial pode ser diferente da conta do administrador que está usando o VSGI. O fluxo guarda temporariamente a sessão do administrador, abre o Google para selecionar a conta institucional, salva a autorização da conta oficial e devolve o usuário à sessão administrativa original.

A conta oficial passa a ser usada para:

- Google Drive: novas fotos dos cadastros
- Gmail API: notificações automáticas de alterações das unidades

A tela mostra:

- E-mail da conta conectada
- Google Drive ativo/inativo
- Gmail ativo/inativo
- Data da conexão
- Última renovação do acesso
- Último erro de integração
- Botão de reconexão
- Botão de desconexão

## Gmail sem senha de app na EC2

O envio de e-mails não depende mais de SMTP nem de senha de app do Gmail.

Foram removidas do fluxo de e-mail as configurações:

- `VSGI_MAIL_ENABLED`
- `VSGI_MAIL_USERNAME`
- `VSGI_MAIL_FROM`
- `VSGI_MAIL_FROM_NAME`
- `VSGI_MAIL_APP_PASSWORD`

O VSGI usa a Gmail API com OAuth da conta oficial. Na conexão/reconexão, o Google solicita a permissão necessária para enviar mensagens e o acesso offline.

O `refresh_token` é persistido na tabela `tenant_google_account` de forma criptografada. A chave usada deriva do `GOOGLE_CLIENT_SECRET`, que já é necessário para o OAuth do sistema.

> Se o `GOOGLE_CLIENT_SECRET` for trocado, reconecte a conta Google oficial para salvar novamente a autorização.

## Google Drive

Depois que a conta oficial estiver conectada, novos uploads de fotos preferem o Drive dessa conta e mantêm a estrutura:

`VSGI-Condominium / Block X / Apartment Y`

Fotos antigas continuam referenciadas pelo ID que já estava salvo. Quando possível, o sistema continua acessando-as pela conta proprietária anterior; novos uploads passam para a conta oficial.

## Banco de dados

Nova migration:

`db.changelog-0018-condominium-settings-google.yaml`

Ela:

1. adiciona campos administrativos à tabela `condominium`;
2. cria `tenant_google_account` para a autorização Google oficial de cada tenant.

O token de atualização não é salvo em texto puro.

## Configuração que continua necessária no servidor

Continuam sendo usados os dados OAuth que o VSGI já precisa para autenticação Google:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

Não há configuração adicional de senha do Gmail na EC2.

## Preparação no Google Cloud

No mesmo projeto Google usado pelo OAuth do VSGI:

1. manter a Google Drive API habilitada;
2. habilitar também a Gmail API;
3. permitir o escopo de envio do Gmail no consentimento OAuth quando aplicável;
4. manter os redirect URIs do ambiente local e de produção já utilizados pelo VSGI.

## Primeiro uso no Recanto Tropical

1. Fazer login no VSGI com um usuário `ADMIN`.
2. Abrir **Menu > Configurações**.
3. Preencher os dados do condomínio. No Recanto Tropical, o e-mail geral pode ser `condominiorecanto@gmail.com`.
4. Salvar os dados.
5. Clicar em **Conectar conta Google**.
6. Na escolha de contas do Google, selecionar `condominiorecanto@gmail.com`.
7. Autorizar Google Drive e Gmail.
8. O VSGI salva a autorização da conta oficial e retorna para Configurações mantendo o administrador original logado.

Depois disso, fotos e notificações utilizam a conta oficial sem senha de Gmail configurada no servidor.
