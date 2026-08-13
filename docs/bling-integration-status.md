# Status do Projeto GabiKids — Foco na Integração com o Bling

> Relatório gerado por leitura estática do código-fonte. Nenhum arquivo de produção foi alterado, corrigido ou refatorado como parte desta tarefa — apenas este documento foi criado.
>
> Data da análise: commit `51c76cd` ("fix: create new method in Bling Service"), branch `main`.

---

## 1. Estado geral do projeto

**Stack**: Java 17, Spring Boot 3.5.14, Spring Data JPA/Hibernate, Spring Security + JWT (JJWT 0.12.6), PostgreSQL + Flyway (20 migrações), Docker, GitHub Actions CI.

**Estrutura de pacotes** (`src/main/java/com/eduardo/ecomerce`):

```
controller/    Endpoints REST
domain/        Entidades JPA + Repositories (um subpacote por agregado)
dto/input/     Payloads de entrada (records com Bean Validation)
dto/output/    Payloads de saída (records)
email/         Envio de email transacional (verificação, senha)
infra/
  bling/       Cliente REST do Bling + exceção dedicada
  config/      Beans de configuração (Mercado Pago, OpenAPI, Storage)
  exception/   GlobalExceptionHandler + exceções de domínio
  payment/     Validação de assinatura de webhook do Mercado Pago
  security/    SecurityConfig, JwtFilter, JwtService, RateLimitFilter
  shipping/    Cliente da API dos Correios
service/       Regras de negócio
```

### Módulos/domínios implementados

| Domínio | Resumo |
|---|---|
| **Autenticação** (`AuthService`, `AuthController`, `JwtService`, `JwtFilter`) | Registro/login com JWT (access + refresh token), verificação de email obrigatória, recuperação/troca de senha via token com expiração, RBAC (`ADMIN`/`CLIENT`). |
| **Categorias e Produtos** (`CategoryService`, `ProductService`, `ProductVariantService`) | CRUD com upload de imagem para Cloudflare R2 (validação de content-type + magic bytes), soft delete de produtos, variações por tamanho com preço/estoque e locking otimista (`@Version`). |
| **Carrinho** (`CartService`) | Carrinho persistente por usuário, protegido contra IDOR (todo acesso a item valida `cart.user.id`). |
| **Endereços** (`AddressService`) | CRUD de endereços por usuário, lógica de endereço padrão único (`isDefault`). |
| **Frete** (`ShippingService`, `CorreiosClient`) | Cálculo de frete PAC/SEDEX via API dos Correios, com token cacheado e retry em 401. Dimensões e peso são fixos (não vêm do produto: 20x7x16cm, 500g). |
| **Pedidos** (`OrderService`) | Criação a partir do carrinho com decremento de estoque (lock pessimista), snapshot de endereço de entrega e frete escolhido gravados no próprio `Order`. |
| **Pagamento** (`PaymentService`, `PaymentController`, `WebhookSignatureValidator`) | Checkout Mercado Pago (Pix + cartão), webhook com validação HMAC-SHA256 (constant-time), checagem de valor pago vs. total do pedido, idempotência por `paymentId`+status, ownership check (pedido deve pertencer ao usuário autenticado). |
| **Storage** (`StorageService`) | Upload/delete de imagens no R2 (S3-compatible), validação de tipo/tamanho/magic bytes. |
| **Rate limiting** (`RateLimitFilter`) | Bucket4j + Caffeine, por IP (via `X-Forwarded-For`/`X-Real-IP`), limites diferenciados por prefixo de rota (`/auth`, `/payments`, `/orders`, `/cart`). |
| **Cleanup** (`TokenCleanupService`) | Job agendado (`@Scheduled`, 3h da manhã) que remove `password_tokens` expirados/usados. |
| **Bling** (`BlingService`, `BlingController`, `BlingClient`) | **Em andamento — ver seção 2.** OAuth 2.0 e sincronização de categorias implementados; sincronização de produtos e webhook ainda não existem. |

---

## 2. Integração com o Bling — Foco principal

### 2.1 Inventário de arquivos

| Arquivo | Camada |
|---|---|
| `domain/blingtoken/BlingToken.java` | Entidade JPA |
| `domain/blingtoken/BlingTokenRepository.java` | Repositório |
| `infra/bling/BlingClient.java` | Cliente REST (infraestrutura) |
| `infra/bling/BlingIntegrationException.java` | Exceção dedicada |
| `service/BlingService.java` | Orquestração / regras de negócio |
| `controller/BlingController.java` | Endpoints REST (`/bling/authorize`, `/bling/callback`) |
| `db/migration/V18__add_bling_integration_fields.sql` | Adiciona `bling_product_id` (products), `bling_variation_id` + `sku` (product_variants) |
| `db/migration/V19__create_table_bling_tokens.sql` | Cria tabela `bling_tokens` |
| `db/migration/V20__add_bling_category_id.sql` | Adiciona `bling_category_id` (categories) |
| `domain/product/Product.java` (campo) | `blingProductId` (Long, unique) |
| `domain/productvariant/ProductVariant.java` (campos) | `blingVariationId` (Long, unique), `sku` (String, unique) |
| `domain/category/Category.java` (campo) | `blingCategoryId` (Long, unique) |
| `domain/product/ProductRepository.java` (método) | `findByBlingProductId(Long)` |
| `domain/productvariant/ProductVariantRepository.java` (método) | `findByBlingVariationId(Long)` |
| `domain/category/CategoryRepository.java` (método) | `findByBlingCategoryId(Long)` |

Não existem arquivos `BlingServiceTest`, `BlingClientTest`, `BlingControllerTest` ou qualquer teste sob `src/test` que mencione Bling. **Não há cobertura de teste para esta integração.**

Não há pasta `infra/bling/webhook`, controller de webhook do Bling, nem tabela de eventos processados — nada disso existe ainda no código.

### 2.2 Resumo de cada classe

**`BlingToken`** (entidade) — mapeia a tabela `bling_tokens`. Campos: `id` (UUID), `accessToken` (TEXT), `refreshToken` (TEXT), `expiresAt`, `updatedAt`. Método `isExpired()` compara `expiresAt` com `LocalDateTime.now()`. `@PrePersist`/`@PreUpdate` atualizam `updatedAt` automaticamente. Não há campo indicando *quem* autorizou (é uma integração single-tenant: apenas 1 linha é mantida, a mais recente).

**`BlingTokenRepository`** — repositório Spring Data com um único método customizado: `findFirstByOrderByUpdatedAtDesc()`, usado para sempre buscar o token mais recente.

**`BlingClient`** — cliente REST (`RestClient` do Spring) para a API v3 do Bling. Métodos expostos:
- `exchangeCodeForToken(String code)` — POST `/oauth/token` com `grant_type=authorization_code`, `code`, `redirect_uri`.
- `refreshAccessToken(String refreshToken)` — POST `/oauth/token` com `grant_type=refresh_token`, `refresh_token`.
- `listProducts(String accessToken, int page)` — GET `/produtos?pagina={page}`. **Método existe mas não é consumido por nenhum service ainda.**
- `getCategories(String accessToken, int page, int limit)` — GET `/categorias/produtos?pagina={page}&limite={limit}`.

Detalhes de implementação: autenticação Basic (`client_id:client_secret` em Base64) nas chamadas de token; Bearer token nas chamadas de dados; header customizado `enable-jwt: 1` em ambos os casos. Exceções de `RestClientException` são convertidas em `BlingIntegrationException`.

**`BlingIntegrationException`** — `RuntimeException` simples com `message` + `cause`, usada uniformemente por `BlingClient` e `BlingService` para sinalizar falhas de integração.

**`BlingService`** — orquestra todo o fluxo. Métodos públicos:
- `buildAuthorizationUrl()` — gera `state` aleatório (UUID), armazena em cache Caffeine (`pendingStates`, TTL 10 min, máx. 100 entradas), monta a URL de autorização do Bling.
- `validateState(String state)` — verifica se o `state` está no cache; lança `BlingIntegrationException` se ausente/expirado; invalida (uso único) após validar.
- `handleAuthorizationCode(String code)` — troca o `code` por tokens via `BlingClient` e persiste (`@Transactional`).
- `getValidAccessToken()` — busca o token mais recente; se expirado, renova via `refresh_token` automaticamente. Método `synchronized` + `@Transactional`.
- `syncCategories()` — pagina pela API de categorias do Bling e faz upsert local (`@Transactional`).

Métodos privados: `upsertCategory(JsonNode)`, `saveToken(JsonNode)`.

**`BlingController`** — `@RestController` em `/bling`, dois endpoints:
- `GET /bling/authorize` — redireciona (302) para a URL de autorização do Bling.
- `GET /bling/callback` — recebe `code` (obrigatório) e `state` (opcional); valida `state` se presente (loga warning se ausente, mas segue em frente); chama `handleAuthorizationCode`; retorna `200 OK` com texto simples.

Não existe endpoint para disparar `syncCategories()` manualmente — o método existe no service mas não é exposto via nenhum controller nem agendado via `@Scheduled`. **Não há como acionar a sincronização de categorias fora de um teste manual/console.**

### 2.3 Fluxo OAuth 2.0 mapeado

```
1. Cliente/admin  → GET /bling/authorize
2. BlingController.authorize()
     → BlingService.buildAuthorizationUrl()
         - gera state (UUID), guarda em cache Caffeine (TTL 10min, uso único)
         - monta URL: {bling.authorize-url}?response_type=code&client_id={id}&state={state}
     → responde 302 redirecionando o navegador para o Bling
3. Usuário aprova o app na tela do Bling
4. Bling redireciona o navegador para {bling.redirect-uri}?code=...&state=...
5. GET /bling/callback?code=...&state=...
6. BlingController.callback()
     → se state != null: BlingService.validateState(state)
         - busca no cache; se ausente/expirado → BlingIntegrationException
         - remove do cache (uso único)
       se state == null: apenas loga warning e segue (não bloqueia)
     → BlingService.handleAuthorizationCode(code)
         → BlingClient.exchangeCodeForToken(code)
             - POST {api-base-url}/oauth/token
             - Basic auth (client_id:client_secret em Base64)
             - body form-urlencoded: grant_type=authorization_code, code, redirect_uri
         → saveToken(response)
             - lê response["access_token"], ["refresh_token"], ["expires_in"]
             - busca o BlingToken mais recente (ou cria novo — só existe 1 linha)
             - seta accessToken, refreshToken, expiresAt = now + expires_in segundos
             - salva (persistência via @Transactional)
7. Responde 200 "Integração com o Bling autorizada com sucesso."
```

**Renovação (fora do fluxo de callback)**: sempre que `getValidAccessToken()` é chamado (por `syncCategories()`, e futuramente por qualquer chamada autenticada à API do Bling), o método verifica `token.isExpired()`. Se expirado, chama `BlingClient.refreshAccessToken(refreshToken)` e persiste o novo token via `saveToken()`. Não há job agendado de renovação proativa — a renovação é sempre lazy, disparada pela próxima chamada que precisar de um token válido.

### 2.4 Sincronização de categorias mapeada

```
BlingService.syncCategories()  (@Transactional)
  1. accessToken = getValidAccessToken()   // renova se necessário
  2. page = 1
  3. loop (até page > 500, safety limit):
       a. response = BlingClient.getCategories(accessToken, page, limit=100)
better      b. data = response["data"]
       c. se data == null OU não é array OU está vazio → break (fim da paginação)
       d. para cada nó em data: upsertCategory(nó)
       e. page++
  4. loga total sincronizado

upsertCategory(categoryNode):
  - blingCategoryId = categoryNode["id"].asLong()
  - descricao = categoryNode["descricao"].asText()
  - busca Category local por blingCategoryId; se não achar, cria nova instância
  - seta blingCategoryId e name = descricao
  - salva
```

Não há tratamento de categorias removidas no Bling (a sincronização é só de upsert — nunca deleta/desativa categorias locais que sumiram do lado do Bling). Não há paginação baseada em cursor/token de continuação — é paginação por número de página incrementado, com um limite de segurança de 500 páginas (50.000 categorias no máximo, dado `limit=100`).

### 2.5 Comentários/suposições não confirmadas contra a documentação oficial do Bling

**Não existe nenhum comentário `TODO`, `FIXME` ou bloco de Javadoc no código do Bling** (`BlingClient`, `BlingService`, `BlingController`, `BlingToken`, `BlingIntegrationException`) marcando explicitamente suposições pendentes de confirmação. Nenhuma das classes tem Javadoc.

Isso, por si, é um ponto de atenção: as suposições sobre o formato da API existem apenas *implicitamente* no código (chamadas diretas a `JsonNode.get("campo")` sem validação de presença/nulidade, sem comentário explicando de onde veio o nome do campo). Seguem as suposições implícitas identificadas por leitura do código, que deveriam ser confirmadas contra a documentação oficial do Bling antes de ir para produção:

| Onde | Suposição implícita no código | Risco se estiver errado |
|---|---|---|
| `BlingClient.postToken` / `BlingService.saveToken` | Resposta do endpoint `/oauth/token` tem os campos `access_token`, `refresh_token`, `expires_in` (nomes exatos, `expires_in` em segundos, inteiro) | `NullPointerException` no `.get(...).asText()`/`.asInt()` se o nome ou tipo do campo divergir |
| `BlingClient.postToken` | Autenticação Basic com `client_id:client_secret`, mais o header custom `enable-jwt: 1`, é a forma correta de chamar `/oauth/token` no Bling v3 | Se o Bling exigir client credentials no corpo (`client_id`/`client_secret` como parâmetros form) em vez de header Basic, a troca de token falharia |
| `BlingService.syncCategories` | Resposta de `/categorias/produtos` vem envelopada em um campo `"data"` contendo um array | Se o Bling devolver a lista na raiz do JSON (sem wrapper) ou usar outro nome de campo, `data` será `null` e o loop encerra silenciosamente na primeira página sem sincronizar nada — sem erro visível |
| `BlingService.upsertCategory` | Cada item de categoria tem os campos `id` (numérico) e `descricao` (string) | `NullPointerException` se os nomes forem diferentes (ex.: `nome` em vez de `descricao`) |
| `BlingClient.listProducts` | Endpoint de listagem de produtos é `/produtos`, paginado via query param `pagina` | Não confirmado contra doc oficial; além disso, o método nunca é chamado em nenhum service, então o formato de resposta de produtos (envelope `data`? campos de nome/preço/estoque/variações?) **ainda não foi sequer inspecionado no código** |
| `BlingClient.getCategories` | Paginação usa `pagina` (1-indexed) e `limite` como nomes de query param | Não confirmado contra doc oficial |
| — | Formato do campo `nome` da variação de produto no padrão `"tamanho:10"` (mencionado no prompt de próximos passos) | Não há nenhum código ainda que faça esse parsing — é uma suposição de formato ainda não verificada nem implementada |

### 2.6 Variáveis de ambiente exigidas

Do `application.properties` (linhas 59-64):

```properties
bling.api-base-url=${BLING_API_BASE_URL:https://api.bling.com.br/Api/v3}
bling.client-id=${BLING_CLIENT_ID}
bling.client-secret=${BLING_CLIENT_SECRET}
bling.redirect-uri=${BLING_REDIRECT_URI}
bling.authorize-url=${BLING_AUTHORIZE_URL:https://www.bling.com.br/Api/v3/oauth/authorize}
```

| Variável | Valor padrão? | Obrigatória para o app subir |
|---|---|---|
| `BLING_API_BASE_URL` | Sim (`https://api.bling.com.br/Api/v3`) | Não |
| `BLING_CLIENT_ID` | **Não** | **Sim** — sem ela, o contexto Spring falha ao criar o bean `BlingClient`/`BlingService` (`@Value` sem default em propriedade ausente) |
| `BLING_CLIENT_SECRET` | **Não** | **Sim**, mesmo motivo |
| `BLING_REDIRECT_URI` | **Não** | **Sim**, mesmo motivo |
| `BLING_AUTHORIZE_URL` | Sim (`https://www.bling.com.br/Api/v3/oauth/authorize`) | Não |

**Achado relevante**: nem o `.env` local nem o `.env.example` do repositório contêm `BLING_CLIENT_ID`, `BLING_CLIENT_SECRET` ou `BLING_REDIRECT_URI`. O `.env.example` atual só lista `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` — está desatualizado em relação a todas as integrações mais recentes (Mercado Pago, Storage, Correios, Mail e Bling não aparecem nele). O `docker-compose.yml` também não repassa nenhuma variável `BLING_*` para o container `backend`, apesar de repassar as variáveis do Mercado Pago, Storage, Mail e DB. **Isso significa que, hoje, subir a aplicação via `docker compose up` falhará ao inicializar o bean `BlingClient`/`BlingService`**, a menos que essas três variáveis obrigatórias sejam adicionadas manualmente ao ambiente do container.

O workflow de CI (`.github/workflows/ci.yml`) também não define nenhuma variável `BLING_*` — mas isso não quebra a CI porque o teste de carregamento de contexto Spring (`EcomerceApplicationTests.contextLoads`) está anotado com `@Disabled`, então o build nunca tenta efetivamente instanciar o contexto completo da aplicação.

---

## 3. O que já foi testado vs. o que não foi

### 3.1 O que sabemos que funciona

- As migrations Flyway V1 a V20 existem em sequência contínua sem gaps de versão; a estrutura das migrations V18/V19/V20 (sintaxe SQL, `ALTER TABLE`/`CREATE TABLE`) está correta e segue o padrão das migrations anteriores do projeto.
- O código compila (evidenciado pelos commits subsequentes de "fix" e pela suíte de testes do projeto — que não inclui Bling — passando em CI, conforme badge no README).
- A suíte de testes existente (`AuthServiceTest`, `OrderServiceTest`, `PaymentServiceTest`, `ShippingServiceTest`, `AddressServiceTest`, `CartServiceTest`, `CategoryServiceTest`, `ProductServiceTest`, `ProductVariantServiceTest`, `StorageServiceTest`, `WebhookSignatureValidatorTest`, `RateLimitFilterTest`, `GlobalExceptionHandlerTest`) não toca em nenhum código Bling, então seu sucesso não diz nada sobre a integração Bling.

### 3.2 O que foi implementado mas ainda não foi testado de ponta a ponta

- **Fluxo OAuth completo** (`/bling/authorize` → aprovação no Bling → `/bling/callback` → persistência do token): não há teste unitário nem evidência de execução manual contra o ambiente real do Bling no código ou histórico de commits.
- **Renovação de token** (`getValidAccessToken()` com token expirado): lógica existe mas não há teste cobrindo o cenário de expiração/refresh.
- **`syncCategories()` contra a API real**: não há teste, nem endpoint/job que a dispare em produção. A suposição do wrapper `"data"` e dos campos `id`/`descricao` nunca foi validada automaticamente.
- **`listProducts()`**: implementado no `BlingClient`, mas não é chamado por nenhum service — nunca foi exercitado, nem manualmente nem via teste.
- Não há testes de unidade (`BlingServiceTest`) mockando `BlingClient`/`BlingTokenRepository`/`CategoryRepository` para validar as regras de negócio (expiração, upsert, paginação, safety limit de 500 páginas).
- **Confirmação de que o app sobe com as variáveis do Bling configuradas**: não verificável a partir do código — como reportado na seção 2.6, faltam as variáveis obrigatórias tanto no `.env`/`.env.example` locais quanto no `docker-compose.yml`, e o teste de contexto Spring está desabilitado. Não há evidência de que alguém tenha efetivamente subido a aplicação com o módulo Bling habilitado.

---

## 4. Próximos passos pendentes (ordem sugerida)

1. **Confirmar os formatos reais da API do Bling contra a documentação oficial** antes de escrever mais código — em especial os três pontos da tabela da seção 2.5 (campos do token, wrapper `data` de categorias, e o formato ainda não inspecionado do endpoint `/produtos`). Sem isso, qualquer código novo de sincronização de produtos herda o mesmo risco de suposição não verificada que já existe em `syncCategories`.

2. **Sincronização de produtos** (`BlingClient` já expõe `listProducts`, mas nenhum service o consome ainda):
   - Implementar um método em `BlingService` (ex. `syncProducts()`) espelhando a paginação já usada em `syncCategories()`.
   - Resolver a `Category` local via `blingCategoryId` (repositório já expõe `findByBlingCategoryId`) para vincular o produto à categoria correta — decidir o que fazer quando a categoria referenciada ainda não foi sincronizada localmente (falhar o item, pular, ou criar uma categoria "órfã"?).
   - Upsert de `Product` via `findByBlingProductId` (repositório já expõe o método).
   - Upsert de `ProductVariant` via `findByBlingVariationId` (repositório já expõe o método), incluindo o parsing do tamanho a partir do campo `nome` no formato `"tamanho:10"` — esse parsing ainda não existe em lugar nenhum do código; será necessário decidir a estratégia de split/regex e o que fazer se o formato não bater (nome sem o prefixo `"tamanho:"`, valores não numéricos, etc.).
   - Definir a fonte do `sku` (coluna já existe em `ProductVariant`, `unique`) — não há indicação no código de qual campo da resposta do Bling ele viria.

3. **Endpoint de webhook** (`POST /bling/webhook`):
   - Ainda não existe nenhum código relacionado (nem controller, nem validador de assinatura, nem parsing de payload).
   - Implementar validação da assinatura via header `X-Bling-Signature-256` — o projeto já tem um precedente de validação HMAC constant-time em `WebhookSignatureValidator` (usado pelo Mercado Pago, com `MessageDigest.isEqual`), que pode servir de referência de padrão a seguir.
   - Definir e implementar o processamento dos eventos de **Produto** e **Estoque** (o payload/nome exato dos tipos de evento ainda não foi confirmado contra a documentação do Bling, mesma ressalva da seção 2.5).
   - Registrar o endpoint em `SecurityConfig` — hoje nenhuma rota `/bling/**` está listada explicitamente nas regras de autorização (nem `/bling/authorize`, nem `/bling/callback`); ambas caem no `anyRequest().authenticated()` genérico ao final da cadeia, o que na prática exige que o chamador tenha um JWT válido de **qualquer usuário autenticado** (não necessariamente `ADMIN`) para acionar `/bling/authorize` — isso é um ponto a revisar ao desenhar o endpoint de webhook, que por natureza precisa ser público (chamado pelo Bling, não por um usuário logado) e validado apenas pela assinatura, como já acontece em `/payments/webhook`.

4. **Idempotência de eventos de webhook**:
   - Não existe hoje nenhuma tabela ou registro de eventos processados para o Bling (diferente do webhook do Mercado Pago, cuja idempotência é resolvida comparando `paymentId` + `status` já gravados no próprio `Order` — não há uma tabela de eventos dedicada nem lá).
   - Será necessário decidir a estratégia: tabela dedicada de eventos processados (ex. `bling_webhook_events` com um identificador único do evento) ou reaproveitar uma estratégia de comparação de estado como a do Mercado Pago, se o payload do Bling permitir.

---

## 5. Outros pontos observados (não solicitados explicitamente, mas relevantes)

Registrados aqui apenas como observação — nenhuma correção foi aplicada:

- **Rotas `/bling/**` sem regra explícita no `SecurityConfig`**: caem no `anyRequest().authenticated()` genérico. Isso significa que qualquer usuário autenticado (papel `CLIENT` incluso) pode hoje acionar `GET /bling/authorize` e `GET /bling/callback`, não apenas um `ADMIN`. Não há checagem de role nesses endpoints.
- **`syncCategories()` não é acionável em produção**: não há endpoint, nem `@Scheduled`, que dispare esse método fora de uma chamada direta ao service (ex. em teste). Hoje ele é código morto do ponto de vista de execução em produção.
- **Possível colisão de nome único em `upsertCategory`**: a coluna `categories.name` tem constraint `UNIQUE` (migration V2). Se já existir uma categoria local com o mesmo nome de uma categoria do Bling mas sem `bling_category_id` vinculado (ex. criada manualmente antes da integração), o `upsertCategory` tentaria criar uma nova linha com nome duplicado e falharia com violação de constraint — não há tratamento desse caso no código.
- **Cache de `state` OAuth (`pendingStates`) é em memória (Caffeine)**: não sobrevive a restart da aplicação nem é compartilhado entre múltiplas instâncias caso o app rode com mais de uma réplica. Para o estágio atual (single-instance) não é um problema, mas é uma limitação a considerar se a aplicação escalar horizontalmente.
- **`.env.example` desatualizado**: não reflete nenhuma das variáveis de ambiente adicionadas nas últimas features (Mercado Pago, Storage/R2, Correios, Mail, Bling) — só contém `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
