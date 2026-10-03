# Por qué los textos del Termux Agent no se traducen

Los ficheros del agente (`AiTermuxModels.kt`, `AiTermuxEngine.kt`,
`AiTermuxActivity.kt`, `AiLocalTrainer.kt`, `AiLocalModel.kt`,
`AgentScriptJudge.kt`) tienen unas 1800 cadenas en chino simplificado.
`check_literals.py --gate` las reporta. **No es un olvido: es deliberado.**

Este documento explica por qué, para que nadie las "arregle" después.

## Qué hay dentro y por qué no se toca

### 1. Los System Prompts (~118 líneas)

El más grande es `DEFAULT_SYSTEM_PROMPT` en `AiTermuxModels.kt`: 160 líneas
que definen cómo se comporta el agente. No es un texto de interfaz — es la
instrucción que se envía al modelo.

Contiene las categorías de skills (A: requiere pulsación del usuario; B/C:
ejecución inmediata), el formato XML de las tarjetas, la marca `[END_TURN]`,
las reglas de gestión de tareas y ocho prohibiciones explícitas (no inventar
resultados, no simular llamadas a herramientas, no repetir, no rellenar
contenido truncado, no saltarse herramientas, no omitir avisos de peligro).

Traducirlos cambiaría el comportamiento del modelo, no el idioma. El riesgo
concreto: perder un `skillType`, un `[技能结果]` o un `[END_TURN]` al
reescribir. Eso no da error de compilación — el agente simplemente deja de
funcionar de forma distinta, sin que nada lo señale.

**Contadores de referencia** (verificados el 2026-10-02, antes de cualquier
cambio): 152 skillTypes, 6 apariciones de `[技能结果]`, 4 de `[END_TURN]`,
6 de `tool_call`. Si alguna vez se tocan, estas cifras deben conservarse.

### 2. Los patrones de detección de alucinaciones (149 líneas)

En `AiTermuxEngine.kt`, alrededor de la línea 300, hay patrones que filtran
resultados inventados por el modelo comparando contra texto chino
hardcodeado:

```kotlin
Regex("""[\r\n]*[*_#\s]*技能执行结果[*_#\s]*[\r\n]+[\s\S]*?(?=[\r\n]{2,}|$)""")
Regex("""^[*_#\s]*(技能名称|操作(?:说明)?|状态(?:说明)?|详细信息)[:：].*$""")
```

Estas cadenas no sontraducibles: son el patrón contra el que se comprueba lo
que el modelo responde. Si el modelo contesta en español, este filtro deja de
detectar resultados fabricados — y esa detección es la barrera que impide que
el agente invente que una orden se ejecutó.

Es el punto más frágil de todo el fichero, y el menos visible.

## Qué sí se traduce en esos ficheros

Los textos que el usuario lee de verdad: títulos de diálogo, botones,
mensajes de error y —en particular— los **avisos de confirmación de
seguridad** de `validateSkillParams`, que aparecen antes de ejecutar algo
peligroso:

```kotlin
SkillType.FILE_DELETE ->
    "禁止删除 Termux 根目录，这会导致整个应用数据丢失"
SkillType.EXIT_TERMUX ->
    "将退出 Termux 应用，所有运行中的进程会终止"
```

Esos van a `strings.xml` en los tres idiomas.

## Cómo está separado en el detector

`tools/i18n/check_literals.py` clasifica los hallazgos en tres categorías:

- `texto CJK` — interfaz real, pendiente de traducir
- `SystemPrompt` — instrucción del modelo, fuera de alcance a propósito
- keywords de búsqueda (`check_keywords()`) — términos del buscador de
  Ajustes, que no son UI visible pero un usuario que busca en su idioma
  necesita

El gate sigue dando rojo mientras queden textos de interfaz sin traducir. Que
diga rojo no significa "todo está mal": significa "queda trabajo de interfaz".