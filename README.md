# ⚔ Battle Balls

**Batalha caótica de bolas com habilidades especiais**  
Simulação 2D de combate automático entre times de bolas com diferentes classes, mecânicas únicas e física divertida.

```
Versão atual: 1.3
Tecnologia: Java + JavaFX
https://github.com/vixus-dev/BatalhaDeBolas 
```

---

## 🎮 Como Jogar
```
1. Clique em "PREPARAR BATALHA"
2. Na janela de configuração:
   - Escolha o tipo de bola (clique nos cards)
   - Defina o HP inicial
   - Escolha o modo (Normal / Super 5× / Hiper 10×)
   - Adicione bolas nos times azul e vermelho
3. Clique em "INICIAR BATALHA"
4. Assista a batalha! 🏟️💥
```
O time que eliminar todos os adversários vence.

---

## ⚔ Classes de Bolas (v1.3)
```
| Ícone | Nome              | Cor principal     | Estilo de jogo principal                          | Habilidade Especial (resumo)                              |
|:-----:|:------------------|:------------------|---------------------------------------------------|-------------------------------------------------------------|
| 👊    | Desarmado         | Cinza             | Evolui com colisões                               | +dano e +velocidade permanente por colisão                 |
| 🗡     | Espada            | Laranja           | Ataque orbital                                    | Espada causa dano extra sem contato físico (+dano por hit) |
| 🌱    | Crescedor         | Verde claro       | Tanque que cresce                                 | +raio e +massa por colisão / parede                        |
| ⚡    | Acelerado         | Vermelho forte    | Velocidade insana                                 | +velocidade por colisão / dano extra proporcional à vel     |
| ⚡    | Mercúrio          | Azul celeste      | Velocidade extrema                                | +5 de velocidade por colisão / dano extra muito alto        |
| 🔪    | Adaga             | Dourado           | Giro frenético                                    | Rotação cada vez mais rápida (+spin por acerto)             |
| ☠     | Centy             | Branco acinzentado| Dano percentual                                   | Causa % do HP **máximo** do alvo (+% por acerto)            |
| 🛡     | Tanque            | Azul aço          | Tanque pesado                                     | 35% redução de dano + massa 3×                             |
| 🦇    | Vampiro           | Roxo médio        | Sustain + scaling                                 | Rouba 50% do dano causado como vida (+dano por cura)       |
| 💣    | Bomba             | Laranja escuro    | Explosões periódicas                              | Detona a cada ~3s (dano e raio crescem)                    |
| 🌵    | Espinho           | Verde escuro      | Contra-ataque passivo                             | Reflete parte do dano recebido (+% por hit sofrido)         |
```

---
## ✨ Principais Recursos (v1.3)
```
- Sistema de física com sub-steps
- 11 classes de bolas com mecânicas únicas
- Modos de potência: Normal, Super (5× HP), Hiper (10× HP)
- Interface de configuração com preview animado + gráfico radar
- Efeitos visuais: explosões, textos flutuantes de dano, trails, raios, brilhos
- Barra de vida dos times + overlay com status detalhado
- Contador de FPS no canto
- Sistema de vitória / empate / cancelamento
```

---
### Pré-requisitos

- **Java 17+** (recomendado Java 21)
- JavaFX SDK configurado no projeto
