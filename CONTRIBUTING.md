# 🤝 Guía de Contribución a Boumusic Desktop

¡Bienvenido al proyecto Boumusic Desktop! Apreciamos cualquier contribución, desde reportes de errores y traducción hasta nuevas funcionalidades y optimizaciones.

---

## 🛠️ Entorno de Desarrollo

- **Lenguaje:** Kotlin 2.0 (JVM)
- **Framework UI:** Jetpack Compose Multiplatform (Compose Desktop 1.6.11)
- **Compilador / Build:** Gradle 8.7 con JDK 17 o JDK 21
- **Arquitectura:** MVI / MVVM reactivo con StateFlow y Coroutines

### Pasos iniciales

```bash
# 1. Clonar el repositorio
git clone https://github.com/bouet21/Boumusic-Desktop.git
cd Boumusic-Desktop

# 2. Ejecutar la app en modo desarrollo
./gradlew run

# 3. Ejecutar pruebas unitarias
./gradlew test
```

---

## 🧪 Normas de Código y Commits

1. **Pruebas unitarias:** Antes de enviar un Pull Request, asegúrate de que todos los tests pasen:
   ```bash
   ./gradlew test
   ```
2. **Formato de Commits:** Seguimos la convención [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat(...)`: Nuevas funcionalidades
   - `fix(...)`: Corrección de errores
   - `docs(...)`: Cambios en documentación o README
   - `refactor(...)`: Reestructuración sin cambios de comportamiento
   - `perf(...)`: Optimizaciones de rendimiento

---

## 🔒 Reporte de Seguridad

Si descubres una vulnerabilidad de seguridad relacionada con credenciales o protocolos de red, por favor repórtala de forma responsable a través de los canales privados del repositorio.
