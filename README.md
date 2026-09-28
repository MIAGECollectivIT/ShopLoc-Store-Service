# ShopLoc - Store Service
Merchant management microservice for the ShopLoc project (MIAGE Collectiv'IT).

## Table of contents
- [0. Prerequisites](#0-prerequisites)
- [1. Installation & Setup](#1-installation--setup)
  - [1.1. Husky](#11-husky)
- [2. Launch & Test](#2-launch--test)
- [3. Project structure](#3-project-structure)
- [4. Workflow](#4-workflow)
  - [4.1 Semantic versioning](#41-semantic-versioning)
  - [4.2 Quality pipeline](#42-quality-pipeline)
    - [4.2.1 (Husky) Pre-commit](#421-husky-pre-commit)
    - [4.2.2 GitHub Actions](#422-github-actions)

## 0. Prerequisites
- **Java 21** (JDK 21 LTS)
- **Node.js** (>= 18) & **npm** (for Husky git hooks)
- **Maven** (included via the Maven Wrapper `./mvnw` / `mvnw.cmd`)

## 1. Installation & Setup
```bash
# Clone the repository
git clone git@github.com:MIAGECollectivIT/ShopLoc-Store-Service.git
cd ShopLoc-Store-Service

# Initialize Husky hooks
npm install
```

## 1.1. Husky 
> Do not bypass the root initialization. Husky pre-commit hooks are mandatory. Your code will be rejected automatically if quality standards are not met.
```bash
# At the root of the project (Git hooks for linters and tests)
npm install
```

## 2. Launch & Test

```bash
# Run the application locally
./mvnw spring-boot:run

# Run the unit test suite
./mvnw test

# Format code with Spotless (Google Java Format)
./mvnw spotless:apply

# Check code formatting without modifying files
./mvnw spotless:check
```

*On Windows (PowerShell / Command Prompt), use `.\mvnw.cmd` instead of `./mvnw`.*

## 3. Project structure
```
├── .github/                     # GitHub Actions workflows (CI & CD)
│   └── workflows/
│       ├── CI.yml               # Spotless lint check + Build & Tests
│       └── CD.yml               # Semantic Release & automated versioning
├── .husky/                      # Git hooks (pre-commit)
├── .mvn/                        # Maven Wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/                # Java Spring Boot source code
│   │   │   └── fr/miage/collectivit/storeservice/
│   │   │       └── StoreServiceApplication.java
│   │   └── resources/           # Configuration files (application.properties)
│   └── test/
│       └── java/                # Unit & integration tests
├── mvnw                         # Maven Wrapper script (Linux / macOS)
├── mvnw.cmd                     # Maven Wrapper batch script (Windows)
├── pom.xml                      # Maven project configuration & Spring Boot dependencies
└── README.md
```

## 4. Workflow
### 4.1 Semantic versioning
Automated versioning runs on every deployment and bumps the project version according to Conventional Commits:
- `fix:` → Patch Increment (e.g. 1.0.1)
- `feat:` → Minor Increment (e.g. 1.1.0)
- `BREAKING CHANGE:` (or `feat!:`) → Major Increment (e.g. 2.0.0)

### 4.2 Quality pipeline 
#### 4.2.1 (Husky) Pre-commit
A pre-commit hook runs automatically before every commit to format code with Spotless and ensure tests compile:
```bash
./mvnw spotless:apply
./mvnw clean test-compile
```

#### 4.2.2 GitHub Actions 
- **CI**: Runs on every Pull Request targeting `main` and `dev`. Validates Spotless formatting (`./mvnw spotless:check`) and runs the test suite (`./mvnw clean verify`).
- **CD**: Runs on push to `main` to generate an automated semantic release using `cycjimmy/semantic-release-action`.
