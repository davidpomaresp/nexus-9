# ADR 0003 — Simulador determinista con reloj simulado

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

Quiero poder repetir una cadena de averías exactamente igual y tener tests que no fallen una vez de cada diez. Con la hora real y `Math.random()` eso es imposible.

## Opciones

1. Simulación en tiempo real con temporizador y aleatoriedad libre.
2. **Simulación por ticks con reloj simulado y semilla explícita**, avanzada bajo demanda.

## Decisión

Opción 2:

- 1 tick = 60 s de tiempo de estación. La hora real no entra en el simulador.
- Todo el ruido sale de un generador con semilla explícita.
- La API avanza la simulación con `POST /advance?ticks=N`. El modo en tiempo real que llegó en la v0.2 ([ADR 0006](0006-reloj-en-servidor-y-sse.md)) no cambia esto: solo llama a ese mismo método cada 250 ms.

## Consecuencias

- (+) Misma semilla + mismo escenario ⇒ mismas lecturas. Tests deterministas y demos repetibles.
- (+) Se pueden simular horas de estación en milisegundos.
- (−) Hay que vigilar las fuentes de no determinismo que no se ven a simple vista, como el orden de un `HashMap` o la hora del sistema. Por eso uso siempre colecciones con orden definido.
