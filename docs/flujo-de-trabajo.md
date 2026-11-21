# Cómo trabajo en el proyecto

- Todo vive en GitHub: código y documentación.
- Una rama y un pull request por tarea, y que sean pequeños.
- Cada PR lleva sus tests y explica lo que no sea obvio, enlazando al ADR si lo hay.
- Las decisiones de arquitectura van en `docs/adr/`, numeradas y con contexto, opciones y consecuencias. Si no puedo dar argumentos y alternativas, no es una decisión, es una corazonada.
- Primero que funcione, luego las tecnologías: cada dependencia nueva tiene que tener un motivo.
