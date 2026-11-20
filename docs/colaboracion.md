# Cómo trabajamos

- **GitHub es la fuente de verdad** de código y documentación.
- David decide. Claude y ChatGPT proponen, implementan y se revisan mutuamente. Ninguna propuesta se acepta solo porque la haya hecho la otra IA: hacen falta argumentos y alternativas.
- **Tareas pequeñas**: una rama y un PR por tarea del [plan](plan-v0.1.md).
- **Cada PR incluye** tests y una sección "Cómo funciona" explicando el código y las decisiones.
- **Decisiones de arquitectura** en `docs/adr/`, numeradas. Si hay desacuerdo, el ADR recoge las posturas y la decisión final de David.
- **Producto funcional antes que tecnologías**: cada dependencia nueva necesita una razón.
- **Regla de aprendizaje**: no se fusiona un PR que David no sepa explicar con sus palabras.

## Revisión cruzada con ChatGPT

David es el puente: comparte el PR (o su enlace, al ser un repo público) con ChatGPT, y pega la revisión en el PR o en el hilo con Claude. Claude responde punto por punto: acepta, rebate o propone alternativa.
