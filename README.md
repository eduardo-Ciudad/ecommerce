# 🛍️ E-commerce API

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-blue?style=flat-square&logo=postgresql)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

API REST de e-commerce desenvolvida para uma loja de roupas infantil. O projeto cobre o fluxo completo de compra: cadastro de produtos com variações de tamanho, carrinho persistente e geração de pedidos com controle de estoque.

---

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 3.5**
- **Spring Data JPA** + **Hibernate**
- **PostgreSQL**
- **Flyway** — migrações de banco de dados
- **Lombok**
- **Bean Validation**
- **JUnit 5** + **Mockito** — testes unitários

---

## 📦 Funcionalidades

- Cadastro e consulta de usuários
- Gerenciamento de categorias e produtos
- Variações de produto por tamanho (P, M, G, GG) com preço e estoque individuais
- Carrinho persistente por usuário
- Criação de pedidos com decremento automático de estoque
- Soft delete de produtos (campo `active`)
- Tratamento global de exceções com respostas padronizadas

---

## 🗂️ Estrutura do projeto

```
src/main/java/com/eduardo/ecomerce
├── controller        # Endpoints REST
├── domain            # Entidades JPA e Repositories
│   ├── user
│   ├── category
│   ├── product
│   ├── productvariant
│   ├── cart
│   ├── cartitem
│   ├── order
│   └── orderitem
├── dto
│   ├── input         # Payloads de entrada
│   └── output        # Payloads de saída
├── infra
│   └── exception     # GlobalExceptionHandler
└── service           # Regras de negócio
```

---

## ⚙️ Como rodar localmente

### Pré-requisitos

- Java 17
- Maven
- PostgreSQL rodando localmente

### 1. Clone o repositório

```bash
git clone https://github.com/educiudad/ecommerce.git
cd ecommerce
```

### 2. Crie o banco de dados

```sql
CREATE DATABASE ecommerce_db;
```

### 3. Configure as variáveis de ambiente

Na sua IDE, configure as seguintes variáveis em **Run Configurations → Environment Variables**:

```
DB_USERNAME=postgres
DB_PASSWORD=sua_senha
```

Ou crie um arquivo `.env` na raiz (não versionado):

```
DB_USERNAME=postgres
DB_PASSWORD=sua_senha
```

### 4. Rode a aplicação

```bash
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.
As migrações Flyway são aplicadas automaticamente na inicialização.

### 5. Rode os testes

```bash
./mvnw test
```

---

## 📡 Endpoints principais

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/users` | Cadastrar usuário |
| GET | `/users` | Listar usuários |
| POST | `/categories` | Criar categoria |
| GET | `/categories` | Listar categorias |
| POST | `/products` | Criar produto |
| GET | `/products` | Listar produtos ativos |
| POST | `/products/{id}/variants` | Adicionar variação de tamanho |
| GET | `/cart/{userId}` | Consultar carrinho |
| POST | `/cart/{userId}/items` | Adicionar item ao carrinho |
| POST | `/orders/{userId}` | Criar pedido |
| GET | `/orders/{userId}` | Listar pedidos do usuário |
| PUT | `/orders/{id}/status` | Atualizar status do pedido |

---

## 🔜 Próximos passos

- [ ] Spring Security + autenticação JWT
- [ ] Docker + docker-compose
- [ ] Deploy
- [ ] Integração com pagamento

---

## 👨‍💻 Autor

Desenvolvido por [Eduardo](https://github.com/eduardo-ciudad)