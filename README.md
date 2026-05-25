# DooR DasH: Scare vs. Laugh Touchdown

A complete turn-based board game engine built in Java for CSEN 401 (Computer Programming Lab) at the German University in Cairo — Spring 2026.

---

## Overview

Two players control monster characters moving across a 10×10 board, collecting cards and using powerups to outmaneuver each other. The first to reach the end zone wins — unless they run out of energy first.

The project focuses on **software architecture**: every game mechanic is expressed through a clean class hierarchy, custom interfaces, and typed exceptions rather than conditional logic.

---

## Architecture

### Board
- 10×10 grid with **zigzag index mapping** — row direction alternates, so the path snakes across the board
- 5 distinct **Cell types**, each with unique effects on a monster that lands on them
- Board synchronises state after position swaps to prevent inconsistency

### Monsters
4 playable subclasses, each with **distinct passive traits and active powerup effects**:

| Monster | Trait |
|---|---|
| **Dynamo** | Energy-focused passive |
| **Dasher** | Movement-focused passive |
| **MultiTasker** | Multi-effect passive |
| **Schemer** | Strategy / board-manipulation passive |

All extend a shared abstract `Monster` class with overridden behaviour methods.

### Cards
- **5 Card types** with rarity-based expansion — rarer cards enter the deck as the game progresses
- Deck is shuffled at game start; drawn cards trigger immediate or deferred effects
- Cards interact with the `CanisterModifier` interface for composable modifier logic

### Turn Logic
Each turn runs through multiple phases:
1. Check frozen state (skip if frozen)
2. Move monster along board path
3. Resolve cell effect
4. Draw and apply card
5. Evaluate win condition

### Exception Handling
| Exception | When thrown |
|---|---|
| `InvalidMoveException` | Move targets a cell the monster cannot legally reach |
| `OutOfEnergyException` | Monster attempts an action with insufficient energy |

Custom exceptions carry context (attempted value, current state) to aid debugging and UI messaging.

---

## Class Hierarchy

```
Monster (abstract)
├── Dynamo
├── Dasher
├── MultiTasker
└── Schemer

Card (abstract)
├── [5 concrete card types]

Cell (abstract)
├── [5 concrete cell types]

Interfaces
└── CanisterModifier
```

---

## Running the Project

**Requirements:** Java 11+, IntelliJ IDEA (recommended)

```bash
git clone https://github.com/leylaelfadali-eng/door-dash-game.git
cd door-dash-game
```

Open in IntelliJ → right-click `Main.java` → Run.

Or from the terminal:
```bash
javac -d out src/**/*.java
java -cp out Main
```

---

## Built With

- **Java** — core language
- **OOP principles** — abstract classes, method overriding, interfaces
- **Custom exception design** — typed, contextual exceptions throughout
- **IntelliJ IDEA** · **Git**

---

## Course Context

**CSEN 401** — Computer Programming Lab · Spring 2026  
German University in Cairo (GUC)

---

*Layla Elfadali · [fadali.layla@gmail.com](mailto:fadali.layla@gmail.com)*
