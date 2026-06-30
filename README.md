# 🛍️ MiniModa — E-commerce API

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-blue?style=flat-square&logo=postgresql)
![CI](https://github.com/eduardo-Ciudad/ecommerce/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

API REST de e-commerce desenvolvida para uma loja de roupas infantil. O projeto cobre o fluxo completo de compra: cadastro de produtos com variações de tamanho, carrinho persistente, geração de pedidos com controle de estoque, autenticação JWT com RBAC e rate limiting.

---

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 3.5**
- **Spring Data JPA** + **Hibernate**
- **Spring Security** + **JWT** (JJWT 0.12.6) — autenticação stateless com refresh token
- **PostgreSQL**
- **Flyway** — migrações de banco (V1–V8)
- **Bucket4j** + **Caffeine** — rate limiting por IP
- **Springdoc OpenAPI** — documentação Swagger
- **Lombok** + **Bean Validation**
- **JUnit 5** + **Mockito** — testes unitários
- **GitHub Actions** — CI
- **Docker** — containerização multi-stage

---

## 📦 Funcionalidades

- Autenticação e registro com JWT (access token + refresh token)
- RBAC com roles ADMIN e CLIENT (`/admin/**` restrito)
- Rate limiting por IP via Bucket4j + Caffeine
- Cadastro e consulta de categorias e produtos
- Variações de produto por tamanho (P, M, G, GG) com preço e estoque individuais
- Carrinho persistente por usuário (protegido contra IDOR)
- Criação de pedidos com decremento automático de estoque
- Soft delete de produtos (campo `active`)
- Tratamento global de exceções com respostas padronizadas
- Documentação interativa via Swagger UI
- Logging estruturado com `@Slf4j` em todos os services

---

## 🗂️ Estrutura do projeto

```
src/main/java/com/eduardo/ecomerce
├── controller           # Endpoints REST
├── domain               # Entidades JPA e Repositories
│   ├── user
│   ├── category
│   ├── product
│   ├── productvariant
│   ├── cart
│   ├── cartitem
│   ├── order
│   └── orderitem
├── dto
│   ├── input            # Payloads de entrada
│   └── output           # Payloads de saída
├── infra
│   ├── exception        # GlobalExceptionHandler
│   └── security         # SecurityConfig, JwtFilter, JwtService, RateLimitFilter
└── service              # Regras de negócio
```

---

## ⚙️ Como rodar

### Com Docker (recomendado)

```bash
git clone https://github.com/eduardo-Ciudad/ecommerce.git
cd ecommerce
```

Crie um arquivo `.env` na raiz:

```
DB_PASSWORD=sua_senha
JWT_SECRET=sua-chave-secreta-com-no-minimo-256-bits
```

```bash
docker compose up --build
```

A aplicação sobe em `http://localhost:8080`. As migrações Flyway rodam automaticamente.

### Sem Docker

**Pré-requisitos:** Java 17, Maven, PostgreSQL rodando localmente.

```bash
# Crie o banco
psql -U postgres -c "CREATE DATABASE minimoda;"

# Configure as variáveis de ambiente na IDE ou exporte no terminal:
export DB_PASSWORD=sua_senha
export JWT_SECRET=sua-chave-secreta-com-no-minimo-256-bits

# Rode
./mvnw spring-boot:run
```

### Testes

```bash
./mvnw test
```

---

## 📡 Endpoints principais

### Autenticação

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/auth/register` | Registrar usuário | Público |
| POST | `/auth/login` | Login (retorna access + refresh token) | Público |
| POST | `/auth/refresh` | Renovar access token | Público |

### Categorias

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/categories` | Criar categoria | ADMIN |
| GET | `/categories` | Listar categorias | Autenticado |
| GET | `/categories/{id}` | Buscar por ID | Autenticado |
| DELETE | `/categories/{id}` | Deletar categoria | ADMIN |

### Produtos

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/products` | Criar produto | ADMIN |
| GET | `/products` | Listar produtos ativos | Autenticado |
| POST | `/products/{id}/variants` | Adicionar variação | ADMIN |

### Carrinho

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| GET | `/cart` | Consultar carrinho | Autenticado (próprio) |
| POST | `/cart/items` | Adicionar item | Autenticado (próprio) |

### Pedidos

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/orders` | Criar pedido | Autenticado (próprio) |
| GET | `/orders` | Listar pedidos | Autenticado (próprio) |
| PUT | `/orders/{id}/status` | Atualizar status | ADMIN |

> 📄 Documentação completa disponível em `/swagger-ui.html` com a aplicação rodando.

---

## 🧪 Testes

| Classe de teste | Service |
|----------------|---------|
| `AuthServiceTest` | Registro, login, refresh token |
| `CategoryServiceTest` | CRUD de categorias, validação de duplicata e produtos vinculados |

---

## 🔜 Próximos passos

- [x] Spring Security + autenticação JWT
- [x] Docker + docker-compose
- [x] CI com GitHub Actions
- [ ] Deploy (Render)
- [ ] Integração com pagamento

---

## 👨‍💻 Autor

Desenvolvido por [Eduardo](https://github.com/eduardo-Ciudad)