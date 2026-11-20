# ADR 0003 — Simulador determinista con reloj simulado

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

Queremos reproducir cadenas de averías y escribir tests que no fallen al azar. Un simulador que use la hora real y `Math.random()` no es reproducible.

## Opciones

1. Simulación en tiempo real con temporizador y aleatoriedad libre.
2. **Simulación por ticks con reloj simulado y semilla explícita**, avanzada bajo demanda.

## Decisión

Opción 2:

- 1 tick = 60 s de tiempo de estación. La hora real no entra en el simulador.
- Todo el ruido sale de un generador con semilla explícita.
- La API avanza la simulación con `POST /advance?ticks=N`. Un modo "tiempo real" se podrá añadir encima más adelante, llamando a ese mismo método periódicamente.

## Consecuencias

- (+) Misma semilla + mismo escenario ⇒ mismas lecturas. Tests deterministas y demos repetibles.
- (+) Se pueden simular horas de estación en milisegundos.
- (−) Hay que tener cuidado con fuentes ocultas de no determinismo (orden de iteración de `HashMap`, hora del sistema). Se usarán colecciones con orden definido.
