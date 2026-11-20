# ADR 0001 — Monolito modular

- Estado: Propuesto
- Fecha: 2026-11-20

## Contexto

NEXUS-9 tiene varias responsabilidades (simulación, ingesta de telemetría, reglas, estado, API) y un único desarrollador. La v0.1 debe ejecutarse en local y probarse con facilidad.

## Opciones

1. **Microservicios** desde el principio (simulador, ingesta, reglas, API por separado).
2. **Monolito sin estructura** (todo en paquetes mezclados).
3. **Monolito modular**: un proceso, pero con módulos de límites claros y dependencias en una sola dirección.

## Decisión

Opción 3. Proyecto Maven multimódulo:

- `simulator`: Java puro, sin dependencias de Spring ni de base de datos.
- `backend`: Spring Boot, con paquetes `telemetry`, `rules`, `station`, `api`. Depende de `simulator`; nunca al revés.

## Consecuencias

- (+) Un solo despliegue, transacciones locales, depuración sencilla.
- (+) El simulador se puede extraer a un proceso propio en v0.2 sin reescribirlo.
- (−) La disciplina de límites depende de nosotros; se puede reforzar más adelante con tests de arquitectura (ArchUnit) si hace falta.
- Microservicios descartados: añaden red, despliegues y consistencia distribuida sin aportar nada a los objetivos de la v0.1.
