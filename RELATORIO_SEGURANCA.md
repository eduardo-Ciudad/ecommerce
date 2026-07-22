# Relatório de Auditoria de Segurança — MiniModa Backend

**Escopo:** código-fonte do backend Spring Boot (`src/main/java`, `src/main/resources`, `pom.xml`).
**Metodologia:** revisão manual de código (SAST manual) cobrindo autenticação/JWT, autorização (IDOR/RBAC), integração com Mercado Pago, validação de input, configuração (CORS, rate limit, properties) e dependências. Não foi executado nenhum teste dinâmico (DAST) nem scan automatizado de dependências (ambiente sem acesso ao repositório Maven Central para baixar o plugin `dependency-check`).

> ⚠️ Nenhuma correção foi aplicada. Este documento é somente diagnóstico.

---

## Resumo Executivo

| Severidade | Quantidade |
|---|---|
| 🔴 CRÍTICA | 1 |
| 🟠 ALTA | 3 |
| 🟡 MÉDIA | 5 |
| ⚪ BAIXA | 5 |
| **Total** | **14** |

**Ação mais urgente:** o endpoint `POST /payments/webhook` não valida a assinatura do Mercado Pago nem confere se o pagamento informado pertence de fato ao pedido (`orderId`) recebido na query string. Isso permite que qualquer pessoa marque **qualquer pedido como pago sem pagar**, reaproveitando o ID de um pagamento aprovado próprio. Corrigir antes de qualquer outra coisa.

Outros dois pontos de alta prioridade: uma rota de gerenciamento de variantes de produto (preço/estoque) ficou fora da regra `hasRole("ADMIN")` no `SecurityConfig` e pode ser chamada por qualquer usuário autenticado; e o `JwtFilter` não trata exceções de token inválido, derrubando com erro 500 **qualquer** requisição (inclusive rotas públicas) que chegue com um header `Authorization` malformado.

### Pontos positivos observados
- Segredos (`JWT_SECRET`, `DB_PASSWORD`, `MP_ACCESS_TOKEN`, credenciais do storage) são todos injetados via variáveis de ambiente (`${...}`), sem hardcode no `application.properties` nem no histórico do Git.
- Senha é sempre persistida com `BCryptPasswordEncoder`; nenhum DTO de saída expõe o hash da senha ou o objeto `User` diretamente.
- IDs são UUID (não sequenciais/previsíveis).
- Todas as consultas são via Spring Data JPA (métodos derivados / JPQL implícito) — não há `nativeQuery` nem concatenação de SQL, então o risco de SQL Injection é baixo.
- O valor cobrado no pagamento (`transactionAmount`) é lido do `Order.getTotal()` salvo no banco, não do que o cliente manda no `PaymentInput` — boa prática de não confiar no valor vindo do frontend.
- Endpoints de escrita usam `@Valid` com Bean Validation na maioria dos DTOs de entrada.

---

## 1. Vazamento de Dados Sensíveis

### 1.1 [BAIXA] Ausência de `@ExceptionHandler` genérico (catch-all)
**Arquivo:** `src/main/java/com/eduardo/ecomerce/infra/exception/GlobalExceptionHandler.java`

O handler trata apenas `ResourceNotFoundException`, `BusinessException`, `MethodArgumentNotValidException`, `DataIntegrityViolationException` e `HttpMessageNotReadableException`. Qualquer outra exceção (ex.: `RuntimeException` lançada em `PaymentService`, `NullPointerException`, `EntityNotFoundException` do `jakarta.persistence` usada em `ProductService`/`PaymentService`) cai no tratamento padrão do Spring Boot.

**Risco real:** hoje isso não vaza stacktrace porque os defaults do Spring Boot 3.x (`server.error.include-stacktrace=never`, `include-message=never`) são seguros e não há configuração explícita alterando isso — mas também não há controle/documentação explícita disso no projeto, e o cliente recebe uma resposta genérica/inconsistente (`Whitelabel`/JSON padrão) em vez de um erro padronizado da API. Uma futura mudança de configuração (ex.: alguém setando `include-stacktrace=on-param` para debug e esquecendo de reverter) passaria a vazar detalhes internos sem que exista uma rede de segurança no código.

**Recomendação:** adicionar um `@ExceptionHandler(Exception.class)` de fallback que logue o stacktrace no servidor e devolva uma mensagem genérica ao cliente; fixar explicitamente `server.error.include-stacktrace=never` e `server.error.include-message=never` no `application.properties` de produção.

### 1.2 [BAIXA] Log do payload bruto do webhook do Mercado Pago
**Arquivo:** `src/main/java/com/eduardo/ecomerce/controller/PaymentController.java:54`

```java
log.info("Webhook recebido: {}", payload);
```

O endpoint é público (`permitAll`) e loga integralmente o corpo recebido, sem sanitização.

**Risco real:** hoje o payload da MP não contém dados de cartão, então o vazamento direto de segredo é baixo. Mas como o endpoint é público e o payload é logado sem validação/whitelist de campos, qualquer um pode injetar dados arbitrários (grandes, malformados ou enganosos) no log — poluição de log e potencial vetor de log injection/log forging se os logs forem consumidos por outra ferramenta sem escaping.

**Recomendação:** logar apenas os campos relevantes (`type`, `data.id`), não o `Map<String, Object>` inteiro; nunca logar corpo bruto de requisição vindo de rota pública.

---

## 2. Autenticação e Autorização

### 2.1 [ALTA] Broken Access Control em `PUT/DELETE /variants/{id}`
**Arquivos:** `infra/security/SecurityConfig.java:56-61`, `controller/ProductVariantController.java:38,51`

O `SecurityConfig` restringe a `ROLE_ADMIN` apenas os padrões abaixo:

```java
.requestMatchers(HttpMethod.POST, "/products/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")
```

Só o `POST /products/{productId}/variants` (criação) cai dentro de `/products/**`. As rotas de **atualização e remoção de variante** são mapeadas em `/variants/{id}`:

```java
@PutMapping("/variants/{id}")     // ProductVariantController
@DeleteMapping("/variants/{id}")  // ProductVariantController
```

Como esse padrão não aparece em nenhuma regra específica, cai em `.anyRequest().authenticated()` — ou seja, **qualquer usuário autenticado com `ROLE_CLIENT` consegue alterar preço/estoque de uma variante de produto, ou apagá-la**, sem ser ADMIN.

**Cenário de exploração:** um cliente comum se cadastra normalmente (`POST /auth/register`), pega o access token e chama `PUT /variants/{id}` alterando `price` para `0.01` ou zerando/inflando `stock`, ou `DELETE /variants/{id}` removendo o estoque de um concorrente.

**Recomendação:** adicionar regras explícitas no `SecurityConfig` para `PUT`/`DELETE` em `/variants/**` exigindo `hasRole("ADMIN")` (o mesmo vale para o `POST /products/{productId}/variants`, que hoje só funciona por coincidir com `/products/**`). Recomenda-se também considerar adicionar `@PreAuthorize("hasRole('ADMIN')")` diretamente nos métodos administrativos como defesa em profundidade, já que hoje toda a autorização depende exclusivamente das regras do `SecurityConfig` (nenhum `@PreAuthorize` é usado no projeto).

### 2.2 [ALTA] IDOR em `POST /payments/process`
**Arquivos:** `controller/PaymentController.java:36-45`, `service/PaymentService.java:34-101`

```java
Order order = orderRepository.findById(input.orderId())
        .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
```

O pedido é buscado apenas por `orderId`, vindo do corpo da requisição, **sem checar se `order.getUser()` corresponde ao usuário autenticado** (`payerEmail`/`auth.getName()` é usado só para preencher o campo `payer.email` do pagamento, não para autorizar o acesso ao pedido).

**Risco real:** qualquer usuário autenticado pode iniciar um pagamento apontando para o `orderId` de outro usuário (os IDs de pedido aparecem, por exemplo, na resposta de `POST /orders` do próprio dono, mas nada impede tentativas/enumeração, engenharia social ou vazamento do ID por outro canal). Isso permite alterar o `paymentId`/`paymentStatus`/`status` de um pedido alheio, criando pagamentos duplicados associados ao pedido de terceiros ou interferindo no fluxo de compra de outro usuário.

**Recomendação:** validar `order.getUser().getId().equals(SecurityUtils.getAuthenticatedUserId())` (mesmo padrão já usado em `OrderService.findByUserIdAndOrderId` e `CartItemRepository.findByIdAndCartUserId`) antes de processar o pagamento, lançando 404/403 caso o pedido não pertença ao usuário.

### 2.3 [ALTA] `JwtFilter` sem tratamento de exceção para token inválido
**Arquivo:** `infra/security/JwtFilter.java:38-39`

```java
String token = authHeader.substring(7);
String email = jwtService.extractUsername(token);
```

`extractUsername` → `extractClaim` chama `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`, que lança `ExpiredJwtException`, `MalformedJwtException`, `SignatureException`, etc. quando o token está expirado, corrompido ou tem assinatura inválida. Nenhum `try/catch` envolve essa chamada dentro do filtro.

**Risco real:** como o `JwtFilter` roda em **toda** requisição (inclusive rotas públicas como `GET /products`), basta enviar um header `Authorization: Bearer <qualquer-coisa-invalida>` para qualquer endpoint (público ou privado) para derrubar a requisição com erro 500, em vez de simplesmente ser tratada como não-autenticada. Isso é ao mesmo tempo:
- **Disponibilidade:** vetor barato de negação de serviço parcial (nenhuma autenticação necessária, e o `RateLimitFilter` só limita `/auth/**`, então essas chamadas não são rate-limited).
- **Consistência de segurança:** o comportamento esperado de "token inválido = tratar como anônimo/401" não é respeitado.

**Recomendação:** envolver a validação do token em `try/catch` (capturando `JwtException`/`io.jsonwebtoken.JwtException`), tratando qualquer falha de parsing como "token inválido" e apenas seguindo o filtro sem autenticar (equivalente ao comportamento de token ausente).

---

## 3. Integração com Mercado Pago

### 3.1 [CRÍTICA] Webhook sem validação de origem e sem checagem de `external_reference`
**Arquivos:** `controller/PaymentController.java:49-65`, `service/PaymentService.java:103-129`

```java
@PostMapping("/webhook")
public ResponseEntity<Void> webhook(
        @RequestParam(required = false) String orderId,
        @RequestBody Map<String, Object> payload) {
    ...
    paymentService.processWebhook(paymentId, UUID.fromString(orderId));
}
```

```java
public void processWebhook(String paymentId, UUID orderId) {
    Payment payment = paymentClient.get(Long.parseLong(paymentId)); // busca real na API do MP — ok
    String status = payment.getStatus();
    Order order = orderRepository.findById(orderId)...
    order.setPaymentId(paymentId);
    order.setPaymentStatus(status);
    switch (status) {
        case "approved" -> order.setStatus(OrderStatus.PAID);
        ...
    }
    orderRepository.save(order);
}
```

Dois problemas se combinam:

1. **Sem validação de assinatura:** o endpoint é `permitAll` (necessário, pois o MP chama de fora) mas não valida o header `x-signature`/`x-request-id` que o Mercado Pago envia para provar que a notificação realmente veio da MP (ver [documentação de webhooks do MP](https://www.mercadopago.com.br) sobre validação de assinatura). Qualquer pessoa na internet pode chamar esse endpoint diretamente.
2. **Sem checagem de vínculo pagamento↔pedido:** o código busca o pagamento real na API da MP (bom, isso evita confiar no `status` vindo do corpo do webhook), **mas nunca compara `payment.getExternalReference()` com o `orderId` recebido por query string**. O `orderId` é só um parâmetro que o chamador do endpoint controla livremente.

**Cenário de exploração:**
1. Um atacante compra normalmente um item de baixo valor na loja e completa o pagamento (pagamento realmente aprovado, `paymentId = X`, vinculado ao próprio pedido barato).
2. O atacante (ou qualquer pessoa) chama `POST /payments/webhook?orderId=<ID_DE_QUALQUER_OUTRO_PEDIDO>` com `{"type":"payment","data":{"id":"X"}}`.
3. O backend busca o pagamento `X` na API da MP (que retorna "approved", pois é um pagamento real do atacante), e marca o pedido de **outra pessoa** (ou um pedido de alto valor do próprio atacante) como `PAID`, sem que aquele pedido tenha sido efetivamente pago.

Isso é um bypass completo de cobrança: mercadoria/pedido liberado sem pagamento correspondente.

**Recomendação (2 camadas):**
- Validar a assinatura do webhook conforme a documentação oficial do Mercado Pago (header `x-signature` + `x-request-id`, usando o *webhook secret* configurado no painel da aplicação MP) antes de processar qualquer notificação.
- Não confiar no `orderId` vindo da query string do chamador: usar exclusivamente `payment.getExternalReference()` retornado pela API do MP para identificar o pedido (esse campo já é setado corretamente em `processPayment` com `order.getId().toString()`), e validar que o valor pago (`payment.getTransactionAmount()`) bate com `order.getTotal()` antes de marcar como `PAID`.

### 3.2 [MÉDIA] Sem idempotência no processamento de webhook
**Arquivo:** `service/PaymentService.java:103-129`

Não há verificação de que a notificação já foi processada antes (ex.: pedido já está `PAID`, ou já existe um registro do `paymentId` processado). Cada chamada simplesmente sobrescreve `paymentId`/`paymentStatus`/`status`.

**Risco real:** o Mercado Pago pode reenviar a mesma notificação várias vezes (comportamento documentado/esperado). Hoje o efeito é apenas idempotente por acaso (sobrescrever com o mesmo valor), mas qualquer evolução futura do fluxo (disparo de e-mail, baixa de estoque adicional, webhook de terceiros, métricas) sobre esse método vai duplicar efeitos colaterais. Combinado com o item 3.1, também facilita reprocessamento malicioso repetido.

**Recomendação:** checar se `order.getPaymentId()` já é igual ao `paymentId` recebido e o status já reflete o mesmo estado antes de aplicar side-effects; considerar registrar os `paymentId` já processados (tabela de eventos de pagamento) para auditoria e idempotência explícita.

---

## 4. Validação de Input

### 4.1 [MÉDIA] Validação de upload de imagem confia no `Content-Type` enviado pelo cliente
**Arquivo:** `service/StorageService.java:49,61-75`

```java
private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
...
if (!ALLOWED_TYPES.contains(file.getContentType())) { ... }
...
PutObjectRequest.builder()....contentType(file.getContentType())...
```

`MultipartFile.getContentType()` reflete o header `Content-Type` enviado pelo cliente no multipart, que é **totalmente controlável pelo atacante** — não há inspeção dos "magic bytes" (assinatura real do arquivo) nem uso de uma biblioteca de detecção de tipo (ex.: Apache Tika).

**Risco real:** um usuário ADMIN mal-intencionado (ou uma sessão de ADMIN comprometida) pode enviar um arquivo com extensão/conteúdo arbitrário (ex.: HTML/SVG com script embutido) apenas setando `Content-Type: image/png`, e esse conteúdo é salvo no bucket e servido publicamente via `storage.public-url`. Se o bucket/CDN não força `Content-Disposition: attachment` nem sanitiza o `Content-Type` ao servir, isso pode resultar em XSS armazenado servido a partir do domínio de storage, ou permitir que o arquivo seja interpretado de forma inesperada pelo navegador do cliente final.

**Recomendação:** validar o tipo real do arquivo pelos primeiros bytes (magic number) em vez de confiar no header; considerar reprocessar a imagem (ex.: recodificar via biblioteca de imagem) antes de subir, o que também remove metadados/payloads maliciosos.

### 4.2 Observação positiva
Os endpoints de escrita usam `@Valid` com anotações Bean Validation (`@NotBlank`, `@NotNull`, `@Min`, `@DecimalMin`, `@Size`, `@Email`) de forma consistente nos DTOs de entrada (`RegisterInput`, `LoginInput`, `ProductVariantInput`, `CartItemInput`, `PaymentInput`, etc.), e o `GlobalExceptionHandler` trata `MethodArgumentNotValidException` retornando 400 com os erros de campo. Não foram encontradas queries nativas (`@Query(nativeQuery = true)`) nem concatenação manual de SQL — risco de SQL Injection é baixo.

Uma exceção pontual: `OrderController.updateStatus` recebe `@RequestBody OrderStatus status` sem `@Valid`, mas por ser um enum a validação é implícita (Jackson rejeita valores fora do enum com `HttpMessageNotReadableException`, já tratado). Não é uma vulnerabilidade, apenas uma inconsistência de padrão frente aos demais endpoints.

---

## 5. Configuração e Infraestrutura

### 5.1 [MÉDIA] Rate limiting não cobre endpoints sensíveis além de `/auth`
**Arquivo:** `infra/security/RateLimitFilter.java:37`

```java
if (!path.startsWith("/auth")) {
    filterChain.doFilter(request, response);
    return;
}
```

Somente rotas sob `/auth` (login/register/refresh) são limitadas a 10 requisições/minuto por IP. `POST /payments/process` e `POST /payments/webhook` — os dois endpoints mais sensíveis do sistema em termos de custo (chamadas à API do Mercado Pago) e de abuso (tentativas de fraude no item 3.1) — não têm nenhum limite de taxa.

**Risco real:** permite abuso/força-bruta contra o fluxo de pagamento (ex.: repetição em massa do endpoint de webhook para explorar o item 3.1, ou flood de tentativas de pagamento gerando custo/carga na integração com a MP).

**Recomendação:** aplicar rate limiting também em `/payments/**` (limites diferentes para `process` vs `webhook`, já que o webhook precisa aceitar picos legítimos da própria MP).

### 5.2 [MÉDIA] `RateLimitFilter` usa `getRemoteAddr()` como chave
**Arquivo:** `infra/security/RateLimitFilter.java:42`

```java
String clientIp = request.getRemoteAddr();
```

Em produção, atrás de um proxy reverso, load balancer ou plataforma PaaS (Render, Railway, Nginx, etc. — comum para apps Spring Boot), `getRemoteAddr()` normalmente retorna o IP interno do proxy, não o IP real do cliente.

**Risco real:** todos os usuários passam a compartilhar o mesmo "bucket" de rate limit (um único IP aparente), o que tanto **anula a proteção** contra um atacante que abusa do endpoint por trás do mesmo proxy quanto pode **bloquear usuários legítimos** uns pelos outros (qualquer pico de tráfego de qualquer usuário consome o limite compartilhado de todos).

**Recomendação:** configurar o servidor para confiar no cabeçalho `X-Forwarded-For`/`Forwarded` apenas quando vindo do proxy confiável conhecido (`server.forward-headers-strategy=native` no Spring Boot, ou uso de `ForwardedHeaderFilter`), e derivar o IP do cliente a partir daí — nunca confiar cegamente em `X-Forwarded-For` se o app for exposto diretamente à internet sem proxy.

### 5.3 [MÉDIA] Swagger UI / OpenAPI docs públicos sem restrição de ambiente
**Arquivo:** `infra/security/SecurityConfig.java:50-51`

```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/v3/api-docs").permitAll()
```

(nota: a regra está duplicada nas duas linhas — não é um bug funcional, apenas redundância). A documentação interativa da API fica acessível publicamente em qualquer ambiente, incluindo produção, sem toggle por profile.

**Risco real:** não é uma vulnerabilidade direta, mas expõe todo o mapa de rotas, DTOs, parâmetros esperados e regras de negócio documentadas (ex.: que `/payments/webhook` existe e seu formato de payload), facilitando reconhecimento por um atacante — inclusive descoberta mais rápida do item 3.1.

**Recomendação:** desabilitar `springdoc.swagger-ui.enabled` / `springdoc.api-docs.enabled` no profile de produção, ou proteger o acesso à documentação (ex.: exigir autenticação ADMIN, ou restringir por IP/VPN).

### 5.4 [BAIXA] CORS configurado em duplicidade, com origem de desenvolvimento fixa
**Arquivos:** `infra/security/SecurityConfig.java:33-42`, `infra/security/WebConfig.java:10-19`

O CORS é configurado duas vezes de forma independente (uma vez dentro do `SecurityFilterChain`, outra via `WebMvcConfigurer`), ambas fixando a mesma origem `http://127.0.0.1:5500` (porta típica do "Live Server" do VS Code) com `allowCredentials(true)`.

**Risco real:** não há o padrão clássico perigoso de `origin: "*"` combinado com `allowCredentials(true)` (isso, inclusive, é rejeitado pelos navegadores) — então não há vulnerabilidade de CORS aberto aqui. O problema é operacional/de manutenção: (a) duas configurações independentes tendem a divergir com o tempo (uma pessoa atualiza uma e esquece a outra); (b) a origem está hardcoded para um ambiente de desenvolvimento local, não parametrizada por variável de ambiente — em produção, isso provavelmente bloqueia o próprio frontend legítimo (ou alguém vai simplesmente adicionar a origem de produção "para funcionar", potencialmente afrouxando demais sem revisão).

**Recomendação:** manter apenas uma fonte de verdade para CORS (o bean dentro do `SecurityFilterChain` é suficiente, remover o `WebConfig`), e mover a lista de origens permitidas para uma property configurável por ambiente (`app.cors.allowed-origins=${CORS_ORIGINS}`).

### 5.5 [BAIXA] `aplication-dev.properties` com nome incorreto (arquivo morto)
**Arquivo:** `src/main/resources/aplication-dev.properties`

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

O Spring Boot só reconhece arquivos de profile no padrão `application-{profile}.properties`. Este arquivo está grafado `aplication-dev.properties` (faltando o "p"), então **nunca é carregado**, independentemente do profile ativo.

**Risco real:** nenhum diretamente (o `show-sql=true` nunca entra em vigor, então não há risco de vazamento de SQL/dados em log por causa deste arquivo). É um item de higiene: indica que o profile "dev" pretendido não está funcionando como o time provavelmente imagina, e pode causar confusão futura (alguém "ativa dev" esperando esse comportamento e não acontece, ou pior, alguém corrige o nome do arquivo sem perceber que isso liga `show-sql` — que não deve ir para produção).

**Recomendação:** renomear para `application-dev.properties` e garantir que o profile `dev` nunca seja o ativo em produção (`SPRING_PROFILES_ACTIVE` controlado explicitamente no deploy), já que `show-sql`/`format_sql` não devem rodar em produção (custo de performance e verbosidade de log).

---

## 6. Dependências (`pom.xml`)

Não foi possível executar uma checagem automatizada de CVEs (`mvn org.owasp:dependency-check-maven:check`) neste ambiente, pois o plugin não está disponível no repositório Maven local e não há acesso à internet para baixá-lo. Revisão manual das dependências com versão fixada:

| Dependência | Versão no `pom.xml` |
|---|---|
| `org.springframework.boot` (parent) | 3.5.14 |
| `io.jsonwebtoken:jjwt-*` | 0.12.6 |
| `com.bucket4j:bucket4j-core` | 8.10.1 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.8 |
| `software.amazon.awssdk:s3` | 2.25.60 |
| `com.mercadopago:sdk-java` | 2.1.29 |

### 6.1 [BAIXA] Recomenda-se checagem automatizada de CVEs no CI
**Arquivo:** `pom.xml`

Nenhuma versão chamou atenção como manifestamente desatualizada a ponto de indicar uma CVE crítica conhecida de cabeça, mas isso **não substitui** uma varredura real, já que novas CVEs são publicadas continuamente.

**Recomendação:** adicionar ao pipeline de CI uma das opções:
- `mvn org.owasp:dependency-check-maven:check` (OWASP Dependency-Check), ou
- GitHub Dependabot (alerts + PRs automáticos de atualização) — mais simples de habilitar em repositórios no GitHub, ou
- Snyk (`snyk test` / integração via GitHub Action).

Rodar isso localmente com acesso à internet também é recomendado antes do próximo deploy, dado que a checagem não pôde ser feita nesta auditoria.

---

## Priorização sugerida de correção

1. **3.1** — Webhook do Mercado Pago sem validação de assinatura/`external_reference` (CRÍTICA).
2. **2.1** — Controle de acesso quebrado em `/variants/{id}` (ALTA).
3. **2.2** — IDOR em `/payments/process` (ALTA).
4. **2.3** — `JwtFilter` sem tratamento de exceção (ALTA).
5. Itens MÉDIA (3.2, 4.1, 5.1, 5.2, 5.3) — priorizar 5.1/3.2 por estarem diretamente ligados ao fluxo de pagamento.
6. Itens BAIXA — ajustes de higiene/configuração, podem entrar no próximo ciclo de manutenção.