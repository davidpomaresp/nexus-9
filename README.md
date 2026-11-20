# NEXUS-9

Software de supervisión para una estación espacial ficticia: un **motor de simulación determinista** genera telemetría y averías, y un **backend** interpreta los eventos, mantiene el estado de cada sistema, detecta dependencias y abre incidencias con prioridad.

No es un panel con números aleatorios: si fallan los paneles solares, baja la generación de energía, la batería se descarga, el sistema recorta cargas y eso afecta al oxígeno y a las comunicaciones.

## Estado

En desarrollo: v0.1 "Telemetry Core". Ver [especificación](docs/especificacion-v0.1.md) y [plan de tareas](docs/plan-v0.1.md).

## Problema de ingeniería central

Reproducir una cadena de averías y demostrar que, aunque se reinicie el backend o lleguen eventos duplicados, **el historial sigue siendo coherente y las incidencias no se duplican**.

## Cómo ejecutarlo

Requisitos: Java 21 y Docker.

```bash
# Tests (unitarios + integración con PostgreSQL real vía Testcontainers)
./mvnw verify

# Aplicación completa (PostgreSQL + backend) en http://localhost:8080
docker compose up --build
curl localhost:8080/actuator/health
```

Para desarrollar con el IDE: `docker compose up -d postgres` y ejecutar `NexusApplication`.

## Estructura

```
simulator/   Motor de simulación (Java puro, sin Spring)
backend/     API y lógica de negocio (Spring Boot, PostgreSQL, Flyway)
docs/        Especificación, plan y decisiones de arquitectura (ADR)
```

## Stack (v0.1)

Java 21 · Spring Boot 4 · Maven · PostgreSQL 17 · Spring JDBC · Flyway · JUnit 5 · Testcontainers · GitHub Actions · Docker Compose

## Documentación

- [Especificación v0.1](docs/especificacion-v0.1.md)
- [Plan de tareas y estimación](docs/plan-v0.1.md)
- [Flujo de trabajo](docs/flujo-de-trabajo.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)
