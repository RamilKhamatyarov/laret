---
title: Crear un archivo
summary: Crea un archivo nuevo, opcionalmente sobrescribiendo uno existente.
synopsis: laret file create <path> [--content <texto>] [--force]
examples:
  - laret file create notas.txt --content "hola"
  - laret file create notas.txt --force
see_also:
  - laret-file-delete
  - laret-file-read
---
Crea un archivo en la ruta `<path>` indicada. El contenido proviene de
`--content`, o de la entrada estándar cuando no se proporciona el indicador.

Si el archivo ya existe, el comando falla salvo que se use `--force`. La
operación se puede deshacer con `laret undo`.
