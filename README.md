# ShopLoc - Store Service
Merchant management microservice for the ShopLoc project (MIAGE Collectiv'IT).

## Table of contents
- [0. Prerequisites](#0-prerequisites)
- [1. Tech Stack & Dependencies](#1-tech-stack--dependencies)
- [2. Installation & Setup](#2-installation--setup)
  - [2.1. Husky](#21-husky)
- [3. Launch & Test](#3-launch--test)
- [4. API Documentation & Swagger UI](#4-api-documentation--swagger-ui)
  - [4.1 Interactive Documentation](#41-interactive-documentation)
  - [4.2 Authentication in application.yaml](#42-authentication-in-applicationyaml)
  - [4.3 OpenAPI Vendor Extensions](#43-openapi-vendor-extensions)
- [5. Project structure](#5-project-structure)
- [6. Workflow](#6-workflow)
  - [6.1 Semantic versioning](#61-semantic-versioning)
  - [6.2 Quality pipeline](#62-quality-pipeline)
    - [6.2.1 (Husky) Pre-commit](#621-husky-pre-commit)
    - [6.2.2 GitHub Actions](#622-github-actions)

## 0. Prerequisites
- **Java 21** (JDK 21 LTS minimum, enforced by `maven-enforcer-plugin`)
- **Maven** (>= 3.8.0, included via the Maven Wrapper `./mvnw` / `mvnw.cmd` and enforced by `maven-enforcer-plugin`)
- **Node.js** (>= 18) & **npm** (for Husky git hooks)

## 1. Tech Stack & Dependencies
- **Spring Boot**: Foundation framework for creating standalone REST microservices.
- **Spring Boot Starter WebMVC**: RESTful API development with embedded Apache Tomcat.
- **Spring Boot Starter Validation**: Bean Validation with Hibernate Validator.
- **Springdoc OpenAPI (Swagger UI)**: OpenAPI 3 interactive documentation (`/swagger-ui.html`) with JWT authentication support.
- **Project Lombok**: Reduces boilerplate code (getters, setters, builders, constructors) via annotation processing.
- **Spring Boot Starter Test & Testing Suite**:
  - JUnit Jupiter (JUnit 5)
  - Mockito & Mockito JUnit Jupiter
  - AssertJ, Hamcrest, JSONassert
  - Spring Test Context Framework & MockMvc
  - Spring Boot Starter Validation Test & WebMVC Test
- **Maven Enforcer Plugin**: Enforces minimum versions for both Java (>= 21) and Maven (>= 3.8.0) during build.
- **Spotless Maven Plugin**: Automated code formatting using Google Java Format.
- **PMD Maven Plugin**: Static code analysis detecting code smells, anti-patterns, and error-prone constructs.

## 2. Installation & Setup

## 2.1. Husky 
> Do not bypass the root initialization. Husky pre-commit hooks are mandatory. Your code will be rejected automatically if quality standards are not met.
```bash
# At the root of the project (Git hooks for linters and tests)
npm install
```

## 3. Launch & Test

```bash
# Run the application locally
./mvnw spring-boot:run

# Run the unit and integration test suite
./mvnw test

# Validate build, enforce version rules, and run all tests & verifications
./mvnw clean verify

# Format code with Spotless (Google Java Format)
./mvnw spotless:apply

# Check code formatting without modifying files
./mvnw spotless:check

# Run PMD static code analysis
./mvnw pmd:check

# Validate environment constraints specifically with Maven Enforcer
./mvnw enforcer:enforce
```

*On Windows (PowerShell / Command Prompt), use `.\mvnw.cmd` instead of `./mvnw`.*

## 4. API Documentation & Swagger UI

### 4.1 Interactive Documentation
Once the service is started (`./mvnw spring-boot:run`), access the interactive Swagger UI and OpenAPI documentation at:
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) (or `http://localhost:8080/swagger-ui.html`)
- **OpenAPI JSON specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 4.2 Authentication in application.yaml
Authentication is configured centrally in `application.yaml` under the `application.security` section:
```yaml
application:
  security:
    auth-type: "Bearer JWT"
    jwt:
      header-name: "Authorization"
      token-prefix: "Bearer "
      secret-key: "${JWT_SECRET:dGhpc2lzYXZlcnlzZWNyZXRqd3RzdG9yZXNlcnZpY2VrZXkxMjM0NTY=}"
      issuer: "shoploc-auth-service"
      expiration-ms: 86400000
    description: "Authenticate using a JWT Bearer token."
```
In Swagger UI, click the **Authorize** button (padlock icon) to provide your JWT Bearer token. All authorized requests will automatically include the `Authorization: Bearer <token>` header.

### 4.3 OpenAPI Vendor Extensions
Custom vendor extensions are declared under `application.openapi.extensions` in `application.yaml` and injected into the OpenAPI definition:
```yaml
application:
  openapi:
    extensions:
      x-api-audience: "ShopLoc Internal Microservices"
      x-service-environment: "${ENVIRONMENT:development}"
      x-service-name: "store-service"
```

## 5. Project structure
```
├── .github/                     # GitHub Actions workflows (CI & CD)
│   └── workflows/
│       ├── CI.yml               # Spotless lint + PMD analysis + Build & Tests
│       └── CD.yml               # Semantic Release & automated versioning
├── .husky/                      # Git hooks (pre-commit)
├── .mvn/                        # Maven Wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/                # Java Spring Boot source code
│   │   │   └── fr/miage/collectivit/storeservice/
│   │   │       ├── config/
│   │   │       │   └── OpenApiConfig.java          # Swagger & OpenAPI authentication setup
│   │   │       ├── controller/
│   │   │       │   └── StoreStatusController.java  # Sample endpoint with OpenAPI annotations
│   │   │       └── StoreServiceApplication.java    # Application entrypoint
│   │   └── resources/
│   │       └── application.yaml                    # Application, OpenAPI & security configuration
│   └── test/
│       └── java/                # Unit & integration tests
│           └── fr/miage/collectivit/storeservice/
│               ├── controller/
│               │   └── StoreStatusControllerTest.java
│               └── StoreServiceApplicationTests.java
├── mvnw                         # Maven Wrapper script (Linux / macOS)
├── mvnw.cmd                     # Maven Wrapper batch script (Windows)
├── pmd-ruleset.xml              # PMD customized ruleset configuration
├── pom.xml                      # Maven project configuration, dependencies & plugins
└── README.md
```

## 6. Workflow
### 6.1 Semantic versioning
Automated versioning runs on every deployment and bumps the project version according to Conventional Commits:
- `fix:` → Patch Increment (e.g. 1.0.1)
- `feat:` → Minor Increment (e.g. 1.1.0)
- `BREAKING CHANGE:` (or `feat!:`) → Major Increment (e.g. 2.0.0)

### 6.2 Quality pipeline 
#### 6.2.1 (Husky) Pre-commit
A pre-commit hook runs automatically before every commit to format code with Spotless, run PMD static analysis, and ensure tests compile:
```bash
./mvnw spotless:apply
./mvnw pmd:check
./mvnw clean test-compile
```

#### 6.2.2 GitHub Actions 
- **CI**: Runs on every Pull Request targeting `main` and `dev`. Executes the complete verification suite (`./mvnw -B -ntp -T 1C verify`) covering Maven Enforcer rules, Spotless formatting, PMD static analysis, and all unit/integration tests in a single optimized pass.
- **CD**: Runs on push to `main` to generate an automated semantic release using `cycjimmy/semantic-release-action`.
