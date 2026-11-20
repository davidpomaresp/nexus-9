# NEXUS-9

Software de supervisión para una estación espacial ficticia: un **motor de simulación determinista** genera telemetría y averías, y un **backend** interpreta los eventos, mantiene el estado de cada sistema, detecta dependencias y abre incidencias con prioridad.

No es un panel con números aleatorios: si fallan los paneles solares, baja la generación de energía, la batería se descarga, el sistema recorta cargas y eso afecta al oxígeno y a las comunicaciones.

## Estado

🚧 **v0.1 "Telemetry Core" en diseño.** Ver [especificación](docs/especificacion-v0.1.md) y [plan de tareas](docs/plan-v0.1.md).

## Problema de ingeniería central

Reproducir una cadena de averías y demostrar que, aunque se reinicie el backend o lleguen eventos duplicados, **el historial sigue siendo coherente y las incidencias no se duplican**.

## Stack (v0.1)

Java 21 · Spring Boot · Maven · PostgreSQL · Flyway · JUnit 5 · Testcontainers · GitHub Actions · Docker Compose

## Documentación

- [Especificación v0.1](docs/especificacion-v0.1.md)
- [Plan de tareas y estimación](docs/plan-v0.1.md)
- [Cómo trabajamos](docs/colaboracion.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)
- [Análisis inicial de Claude](docs/analisis-claude-v0.1.md)
