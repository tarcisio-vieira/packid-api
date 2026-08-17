# VSGI Condomínio — perfis, portal do morador e controle de espaços

## Perfis de acesso

- **ADMIN**: administração técnica completa.
- **SECRETARY**: administra configurações do condomínio, usuários e os cadastros protegidos (condôminos, bicicletas, pets e veículos), além das rotinas operacionais.
- **PORTER**: executa as rotinas operacionais e consulta condôminos, bicicletas, pets e veículos, mas não pode criar, editar ou excluir esses quatro tipos de cadastro. Também não acessa Configurações.

A proteção foi aplicada no front-end e, principalmente, no back-end, para impedir que um usuário da portaria contorne a interface chamando a API diretamente.

## Conta Google oficial e login da equipe

O login Google da equipe continua separado da conta Google oficial do condomínio.

Configuração prevista para o Recanto Tropical:

- `condominiorecantotropical@gmail.com`: cadastrar como **SECRETARY** e conectar em **Configurações > Integração Google oficial**. O Drive desta conta será usado para as novas imagens e o Gmail desta conta para as notificações.
- `recantoportaria245@gmail.com`: cadastrar como **PORTER**. No primeiro login OAuth2, o `providerSubject` do Google é associado automaticamente ao usuário pré-cadastrado.

A conta oficial não precisa permanecer logada na máquina da portaria. As fotos da conta oficial são lidas pelo back-end com o token institucional salvo para o tenant.

## Acesso do morador

No cadastro de **Condômino** foi incluída a opção **Liberar acesso de visualização ao morador**, com:

- usuário do morador;
- senha do morador (BCrypt no banco);
- senha em branco durante edição mantém a senha atual.

O login do morador usa:

- identificador/slug do tenant;
- bloco;
- apartamento;
- usuário;
- senha.

O morador acessa apenas os dados vinculados à própria ocupação/unidade: condôminos, veículos, pets, bicicletas, encomendas e histórico de solicitações de espaços. Ele não recebe acesso OAuth2 nem permissões administrativas.

## Espaços e chaves

Foram adicionados três espaços:

- Brinquedoteca (`PLAYROOM`);
- Sala de Jogos (`GAMES_ROOM`);
- Academia (`GYM`).

Fluxo:

1. Morador toca em **Solicitar chave**.
2. A portaria recebe uma notificação/card no canto superior direito e a solicitação aparece na tela **Espaços e chaves**.
3. Porteiro clica em **Liberar chave**; o registro passa para **Em uso** e grava data/hora e usuário da portaria.
4. No celular, o botão do morador muda para **Solicitar devolução da chave**.
5. Ao tocar, a portaria recebe a solicitação de devolução.
6. Porteiro clica em **Receber chave**, encerrando o fluxo e registrando data/hora e usuário responsável.

A tela de espaços permite filtro por espaço e período e impressão do relatório filtrado. O histórico também aparece na visão geral da unidade.

## Banco de dados

O Liquibase recebeu:

- `0025-access-control-resident`: libera `provider_subject` para pré-cadastro OAuth2 e adiciona credenciais do morador ao `registry_entry`.
- `0026-space-access`: cria `space_access_request` e índices/FKs do histórico de espaços.

As senhas dos moradores nunca são retornadas pela API; apenas o hash BCrypt fica persistido.

## Validação realizada neste ambiente

- Todos os arquivos TS/TSX do `src` foram processados pelo compilador TypeScript em modo de transpilação: **0 erros de sintaxe**.
- Os YAMLs do Liquibase e `application.yaml` foram validados por parser YAML.
- O build completo do front não pôde ser concluído neste sandbox porque o `node_modules` ficou incompleto durante a instalação (faltaram definições `vite/client` e `node`) e o ambiente não conseguiu baixar os pacotes faltantes.
- O projeto de API enviado não contém a pasta `.mvn/wrapper`, e não há Maven instalado neste ambiente; por isso o WAR não foi compilado aqui.

Antes do deploy em produção, execute os builds normais em uma máquina com as dependências disponíveis.
