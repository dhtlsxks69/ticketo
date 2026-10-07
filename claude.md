# Ticketo Project Configuration & Guidelines

## Technical Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Gradle (Groovy DSL)
- **Database**: MySQL 8.0 (`utf8mb4`) / Spring Data JPA (or MyBatis)
- **Utilities**: Lombok, Spring Validation

## Architecture & Package Structure
- **Pattern**: Feature-based Layered Architecture
- **Root Package**: `com.ticketo`

### Directory Layout Example
```text
com.ticketo
├── config              # Global Configs (Security, Web, etc.)
├── com                 # Common Utility/Global components
└── {feature}           # e.g., member, reservation, sys, user
    ├── controller      # {Feature}Controller.java
    ├── service         # {Feature}Service.java
    ├── mapper          # {Feature}Mapper.java (or Repository)
    └── vo              # {Feature}VO.java (Data transfer objects / Entities)