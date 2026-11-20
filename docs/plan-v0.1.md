# Plan de tareas — v0.1 "Telemetry Core"

Cada tarea es una rama y un pull request pequeño, con tests y una sección "Cómo funciona" en la descripción.

| # | Tarea | Contenido | Horas estimadas |
|---|---|---|---|
| 0 | Especificación y ADRs | Alcance, modelo, reglas, API y decisiones de arquitectura | 2–3 |
| 1 | Esqueleto del proyecto | Maven multimódulo, Spring Boot, Docker Compose con PostgreSQL, CI en GitHub Actions | 2–3 |
| 2 | Simulador: reloj, semilla, POWER | Órbita, paneles, batería, test de reproducibilidad | 3–4 |
| 3 | Simulador: OXYGEN, COMMS, cargas y escenarios | Load shedding, averías programadas, escenario `solar-degradation` | 3–4 |
| 4 | Persistencia de telemetría | Flyway, `telemetry_event`, idempotencia, Testcontainers | 3–4 |
| 5 | Reglas e incidencias | Reglas con histéresis, índice único parcial, grafo de dependencias | 4–5 |
| 6 | API REST | Endpoints, errores `problem+json`, tests de integración | 2–3 |
| 7 | Cierre de la v0.1 | Test de punta a punta del escenario, README, demo | 2–3 |

## Estimación

- **Total v0.1: 21–29 horas.**
- Con 8–10 horas a la semana: unas 3 semanas. Con 15 horas: unas 2.
