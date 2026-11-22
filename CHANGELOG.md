# Cambios

## 0.2.0 (2026-11-22)

- Panel nuevo en React con estética de sala de control: mapa de la estación, reloj de órbita, alarma maestra, línea de tiempo con las incidencias y gráficas de energía, batería, oxígeno y comunicaciones.
- La simulación corre en el servidor (`/run` y `/pause`) y el panel se actualiza con Server-Sent Events.
- Endpoint `/state`, que devuelve todo lo que pinta el panel leído en un mismo tick.
- Las alarmas se pueden reconocer desde el registro de incidencias.
- Se puede volver a cualquier tick del historial y abrir una simulación por su URL.
- Dos escenarios nuevos: `oxygen-leak` y `solar-storm`.
- Demo pública en Render + Neon, con límites de tick, de simulaciones y de peticiones por minuto, y borrado de las simulaciones abandonadas.
- La demo abre en una misión de ejemplo de `solar-degradation` ya avanzada, de solo lectura para que todos vean la misma cascada de fallos; «Probar mi propia simulación» crea una independiente.
- Tests (Vitest) y lint (ESLint) del panel dentro del build, y pruebas de navegador (Playwright) en CI.

## 0.1.0 (2026-11-20)

- Simulador determinista con energía, oxígeno y comunicaciones, órbita con día y noche y reparto de energía por prioridad.
- Escenarios `nominal` y `solar-degradation`.
- Telemetría e incidencias en PostgreSQL, con idempotencia garantizada por la base de datos.
- Cinco reglas con histéresis y grafo de dependencias entre módulos.
- API REST, script de demo y CI en GitHub Actions.
