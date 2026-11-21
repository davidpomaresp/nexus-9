# ADR 0004 — Spring JDBC (JdbcClient) en lugar de JPA/Hibernate

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

La persistencia de NEXUS-9 se basa en garantías concretas de PostgreSQL (ADR 0002): `INSERT ... ON CONFLICT DO NOTHING`, índices únicos parciales y una tabla de eventos append-only. Tenía que decidir cómo habla el backend con la base de datos.

## Opciones

1. **JPA/Hibernate (Spring Data JPA)**: mapeo objeto-relacional, repositorios generados, caché de primer nivel.
2. **Spring JDBC con `JdbcClient`**: SQL escrito a mano, mapeo explícito a records.
3. **jOOQ**: SQL tipado generado desde el esquema.

## Decisión

Opción 2. El esquema lo controla Flyway y las consultas se escriben en SQL.

## Consecuencias

- (+) El SQL que garantiza la idempotencia está a la vista y lo puedo explicar línea a línea. Con JPA, `ON CONFLICT` y los índices parciales me habrían obligado a escribir consultas nativas igualmente.
- (+) Sin "magia" de Hibernate (flush automático, carga perezosa, dirty checking) que complique razonar sobre transacciones.
- (−) Hay más código de mapeo a mano. Con un modelo tan pequeño y con records, compensa.
- jOOQ me gusta por el tipado, pero mete generación de código en el build y para la v0.1 no lo necesitaba.
