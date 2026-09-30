# Plan de implementación — T-73 · Ensayo del coloquio

Transversal, depende de T-71 y T-72 (ambas hechas).

## 1. Qué

Un guion escrito de la demo (orden, quién muestra qué, tiempos) que cubra cada requisito obligatorio del enunciado con un momento concreto donde se ve funcionando, más un ensayo real del grupo sobre un stack levantado de cero.

## 2. Qué puedo hacer yo y qué no

Puedo escribir el guion completo y validar técnicamente cada paso (tiempos, comandos, qué debería verse) corriéndolo yo mismo de punta a punta. **No puedo hacer el ensayo real**: "el grupo corre la demo entera sin improvisar" requiere a las tres personas practicando juntas, decidiendo quién dice qué en voz alta y cronometrándose — eso queda para ustedes tres. Dejo el requisito correspondiente sin marcar por esa razón, no porque falte algo de mi lado.

## 3. Componentes creados

- `docs/guion_coloquio.md`: guion completo, con los 8 requisitos obligatorios + Resilience4J mapeados 1 a 1 a un momento de la demo, repartidos en los mismos tres carriles de `docs/planificacion_global.md` sección 5 (ajustable: son ustedes los que se conocen, no yo). Incluye comandos exactos y qué se espera ver en cada paso, para no tener que improvisar delante del profesor.

## 4. Plan de verificación

Corrí yo mismo, en orden, todos los pasos técnicos del guion contra un stack recién levantado, cronometrando cada bloque — el detalle está en la sección final de `guion_coloquio.md`. Lo que falta para cerrar T-73 de verdad: que ustedes tres se junten, se repartan las partes (la propuesta de reparto es solo un punto de partida) y lo corran en voz alta al menos una vez, con el tiempo real que les lleva a ustedes explicándolo.
