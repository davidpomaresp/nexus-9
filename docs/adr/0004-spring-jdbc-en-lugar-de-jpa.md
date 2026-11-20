# ADR 0004 — Spring JDBC (JdbcClient) en lugar de JPA/Hibernate

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

La persistencia de NEXUS-9 se basa en garantías concretas de PostgreSQL (ADR 0002): `INSERT ... ON CONFLICT DO NOTHING`, índices únicos parciales y una tabla de eventos append-only. Hay que elegir cómo habla el backend con la base de datos.

## Opciones

1. **JPA/Hibernate (Spring Data JPA)**: mapeo objeto-relacional, repositorios generados, caché de primer nivel.
2. **Spring JDBC con `JdbcClient`**: SQL escrito a mano, mapeo explícito a records.
3. **jOOQ**: SQL tipado generado desde el esquema.

## Decisión

Opción 2. El esquema lo controla Flyway y las consultas se escriben en SQL.

## Consecuencias

- (+) El SQL que garantiza la idempotencia está a la vista y se puede explicar línea a línea; con JPA, `ON CONFLICT` e índices parciales obligan a consultas nativas de todas formas.
- (+) Sin "magia" de Hibernate (flush automático, carga perezosa, dirty checking) que complique razonar sobre transacciones.
- (−) Más código de mapeo manual. Aceptable: el modelo es pequeño y se mapea a records.
- jOOQ descartado por ahora: aporta tipado, pero añade generación de código al build sin necesidad en la v0.1.
