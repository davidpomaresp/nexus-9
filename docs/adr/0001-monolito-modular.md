# ADR 0001 — Monolito modular

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

NEXUS-9 hace varias cosas (simular, guardar telemetría, evaluar reglas, mantener el estado, exponer una API) y lo desarrollo yo solo. Para la v0.1 quería algo que arrancara en local con un comando y que fuera fácil de probar.

## Opciones

1. **Microservicios** desde el principio (simulador, ingesta, reglas, API por separado).
2. **Monolito sin estructura** (todo en paquetes mezclados).
3. **Monolito modular**: un proceso, pero con módulos de límites claros y dependencias en una sola dirección.

## Decisión

Opción 3. Proyecto Maven multimódulo:

- `simulator`: Java puro, sin dependencias de Spring ni de base de datos.
- `backend`: Spring Boot, con los paquetes `simulation`, `telemetry`, `incident`, `api` y `domain`. Depende de `simulator`, nunca al revés.

## Consecuencias

- (+) Un solo despliegue, transacciones locales, depuración sencilla.
- (+) El simulador se puede extraer a un proceso propio en v0.2 sin reescribirlo.
- (−) Que los límites se respeten depende de mí. Si algún día empiezan a mezclarse, se pueden vigilar con tests de arquitectura (ArchUnit).
- Descarté los microservicios porque traen red, varios despliegues y consistencia distribuida, y nada de eso me ayudaba con lo que quería demostrar en la v0.1.
