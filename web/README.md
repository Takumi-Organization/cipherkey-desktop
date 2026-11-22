# Web de presentación de CipherKey

Página estática (HTML + CSS + un JS corto) con la identidad Takumi: papel negro, monocromo, esquina superior derecha cortada,
Outfit 200 y JetBrains Mono. No necesita compilación: se abre `index.html` o se sirve la carpeta (`python3 -m http.server -d web`).

- `index.html`, `style.css`, `site.js`: la página; el generador de contraseñas usa `crypto.getRandomValues` y nada sale del navegador.
- `fonts/`: Outfit y JetBrains Mono (SIL OFL 1.1, con sus licencias).
- `img/`: marca Takumi y capturas reales de la aplicación (bóveda de demostración con datos inventados), en WebP.

No está desplegada en ningún sitio. Las capturas se regeneran con `tools/Capturas.java` (ver el README principal) y se convierten a WebP.
