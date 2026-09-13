# R8 corre en modo completo (android.enableR8.fullMode=true en gradle.properties).
#
# Casi no hacen falta reglas manuales:
#   - Hilt, Room y Compose traen las suyas en sus artefactos.
#   - kotlinx.serialization no usa reflexión, solo el plugin del compilador.
#
# Las clases que se serializan al backup llevan @Serializable, así que sus
# nombres de campo los fija el plugin y no dependen de que R8 los preserve.
# Si aparece algo, se añade aquí con un comentario que diga POR QUÉ.

# Mantiene los nombres de archivo y línea en los stack traces de crash.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
