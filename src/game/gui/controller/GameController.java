package game.gui.controller;

import game.engine.*;
import game.engine.cells.*;
import game.engine.exceptions.*;
import game.engine.monsters.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Random;

public class GameController {

    // ── FXML fields ──────────────────────────────────────────────
    @FXML private VBox playerPanel;
    @FXML private VBox opponentPanel;
    @FXML private StackPane boardContainer;
    @FXML private Label statusLabel;
    @FXML private Label turnIndicator;
    @FXML private Button rollButton;
    @FXML private Button powerupButton;
    @FXML private Label diceLabel;
    @FXML private VBox eventLog;
    @FXML private ScrollPane eventLogScroll;

    // ── State ─────────────────────────────────────────────────────
    private Game game;
    private boolean gameOver = false;
    private GridPane boardGrid;

    // ── Colors (match start screen palette) ───────────────────────
    private static final String PLAYER_COLOR   = "#2E4A7A";
    private static final String OPPONENT_COLOR = "#7A4A1E";
    private static final String GOLD           = "#FFD700";
    private static final String BG             = "#1E3248";
    private static final String DARK_BG        = "#142236";
    private static final String DARKER_BG      = "#0D1B2A";
    private static final String TEXT_LIGHT     = "#CCCCEE";

    // ═══════════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public void initGame(Game game) {
        this.game = game;
        gameOver = false;

        buildBoard();
        refreshPanels();
        setupButtons();
        updateTurnIndicator();

        addLog("Game started!");
        addLog("You: " + game.getPlayer().getName()
                + " (" + game.getPlayer().getClass().getSimpleName() + ", " + game.getPlayer().getRole() + ")");
        addLog("AI: " + game.getOpponent().getName()
                + " (" + game.getOpponent().getClass().getSimpleName() + ", " + game.getOpponent().getRole() + ")");
        addLog("First to reach cell 100 with 1000+ energy wins!");
    }

    private void setupButtons() {
        if (rollButton != null) {
            rollButton.setOnAction(e -> handleRollDice());
            rollButton.setOnMouseEntered(e -> rollButton.setStyle(btnStyle("#3A6A9A")));
            rollButton.setOnMouseExited(e -> rollButton.setStyle(btnStyle(PLAYER_COLOR)));
        }
        if (powerupButton != null) {
            powerupButton.setOnAction(e -> handlePowerup());
            powerupButton.setOnMouseEntered(e -> powerupButton.setStyle(btnStyle("#9A6A2E")));
            powerupButton.setOnMouseExited(e -> powerupButton.setStyle(btnSmallStyle(OPPONENT_COLOR)));
        }
    }

    private String btnStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: " + GOLD + "; " +
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    private String btnSmallStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: " + GOLD + "; " +
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME ACTIONS
    // ═══════════════════════════════════════════════════════════════

    private void handleRollDice() {
        if (gameOver) return;
        if (game.getCurrent() != game.getPlayer()) {
            updateStatus("Not your turn!");
            return;
        }

        setButtonsEnabled(false);

        boolean playerFrozen = game.getPlayer().isFrozen();
        int positionBefore  = game.getPlayer().getPosition();
        int energyBefore    = game.getPlayer().getEnergy();

        try {
            game.playTurn();

            if (playerFrozen) {
                // Turn was auto-skipped by engine
                addLog(game.getPlayer().getName() + " is FROZEN! Turn skipped.");
                updateStatus("You are frozen! Turn skipped.");
                refreshBoard();
                refreshPanels();
                checkWinnerThenAI();
            } else {
                int roll          = game.getLastRoll();
                int positionAfter = game.getPlayer().getPosition();
                int energyAfter   = game.getPlayer().getEnergy();

                String moveMsg = "You rolled " + roll
                        + " | " + positionBefore + " -> " + positionAfter;
                if (energyAfter != energyBefore) {
                    int diff = energyAfter - energyBefore;
                    moveMsg += " | Energy " + (diff > 0 ? "+" : "") + diff;
                }

                final String logEntry = moveMsg;
                animateDice(roll, () -> {
                    addLog(logEntry);
                    addCellLog(positionAfter);
                    refreshBoard();
                    refreshPanels();
                    checkWinnerThenAI();
                });
            }

        } catch (InvalidMoveException e) {
            int roll = game.getLastRoll();
            addLog("Rolled " + roll + " - BLOCKED! Cannot land on opponent.");
            updateStatus("Blocked! Cannot land on opponent. Roll again.");
            // Turn was NOT switched - player rolls again
            animateDice(roll, () -> {
                refreshBoard();
                refreshPanels();
                setButtonsEnabled(true);
            });
        }
    }

    private void checkWinnerThenAI() {
        Monster winner = game.getWinner();
        if (winner != null) {
            showWinScreen(winner);
            return;
        }
        updateTurnIndicator();
        addLog("AI is thinking...");

        PauseTransition delay = new PauseTransition(Duration.millis(1200));
        delay.setOnFinished(e -> aiTurn());
        delay.play();
    }

    private void aiTurn() {
        if (gameOver) return;

        boolean aiFrozen     = game.getOpponent().isFrozen();
        int positionBefore   = game.getOpponent().getPosition();
        int energyBefore     = game.getOpponent().getEnergy();

        try {
            game.playTurn();

            if (aiFrozen) {
                addLog(game.getOpponent().getName() + " is FROZEN! AI turn skipped.");
            } else {
                int roll          = game.getLastRoll();
                int positionAfter = game.getOpponent().getPosition();
                int energyAfter   = game.getOpponent().getEnergy();

                String moveMsg = "AI rolled " + roll
                        + " | " + positionBefore + " -> " + positionAfter;
                if (energyAfter != energyBefore) {
                    int diff = energyAfter - energyBefore;
                    moveMsg += " | Energy " + (diff > 0 ? "+" : "") + diff;
                }
                addLog(moveMsg);
                addCellLog(positionAfter);

                // Update dice display for AI roll
                if (diceLabel != null) diceLabel.setText("[ " + roll + " ]");
            }

            refreshBoard();
            refreshPanels();

            Monster winner = game.getWinner();
            if (winner != null) {
                showWinScreen(winner);
                return;
            }

            updateTurnIndicator();
            updateStatus("Your turn! Roll the dice.");
            setButtonsEnabled(true);

        } catch (InvalidMoveException e) {
            addLog("AI blocked! " + e.getMessage() + " Retrying...");
            refreshBoard();
            refreshPanels();
            // AI retries after short delay
            PauseTransition retry = new PauseTransition(Duration.millis(800));
            retry.setOnFinished(ev -> aiTurn());
            retry.play();
        }
    }

    private void handlePowerup() {
        if (gameOver) return;
        if (game.getCurrent() != game.getPlayer()) {
            updateStatus("Not your turn!");
            return;
        }

        int energyBefore = game.getPlayer().getEnergy();
        try {
            game.usePowerup();
            int energyAfter = game.getPlayer().getEnergy();
            int cost        = energyBefore - energyAfter;
            addLog(game.getPlayer().getName() + " used POWERUP! (-" + cost + " energy)");
            addLog("Effect: " + getPowerupDescription(game.getPlayer()));
            updateStatus("Powerup activated!");
            refreshPanels();
        } catch (OutOfEnergyException e) {
            updateStatus("Not enough energy! Need 500.");
            addLog("Powerup failed: need 500 energy, have " + game.getPlayer().getEnergy());
        }
    }

    private String getPowerupDescription(Monster monster) {
        if (monster instanceof Dasher)      return "Momentum Rush! 3x speed for 3 turns.";
        if (monster instanceof Dynamo)      return "Froze opponent for 1 turn!";
        if (monster instanceof MultiTasker) return "Focus Mode! Normal speed for 2 turns.";
        if (monster instanceof Schemer)     return "Chain Attack! Stole energy from all monsters.";
        return "Powerup activated!";
    }

    // ═══════════════════════════════════════════════════════════════
    //  BOARD
    // ═══════════════════════════════════════════════════════════════

    private void buildBoard() {
        boardContainer.getChildren().clear();
        boardGrid = new GridPane();
        boardGrid.setHgap(2);
        boardGrid.setVgap(2);
        boardGrid.setAlignment(Pos.CENTER);
        renderAllCells();
        boardContainer.getChildren().add(boardGrid);
    }

    private void refreshBoard() {
        if (boardGrid == null) { buildBoard(); return; }
        boardGrid.getChildren().clear();
        renderAllCells();
    }

    private void renderAllCells() {
        Cell[][] cells = game.getBoard().getBoardCells();
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                Cell cell  = cells[row][col];
                int  index = linearIndex(row, col);
                StackPane cellPane = createCellPane(cell, index);
                // row 0 = bottom of board visually → display inverted
                boardGrid.add(cellPane, col, 9 - row);
            }
        }
    }

    private int linearIndex(int row, int col) {
        int actualCol = (row % 2 == 0) ? col : (9 - col);
        return row * 10 + actualCol;
    }

    private StackPane createCellPane(Cell cell, int index) {
        StackPane pane = new StackPane();
        pane.setPrefSize(62, 62);
        pane.setMaxSize(62, 62);

        // Background image or fallback color
        String imagePath  = getImagePath(cell, index);
        boolean imgLoaded = false;

        try {
            java.io.InputStream stream = getClass().getResourceAsStream(imagePath);
            if (stream != null) {
                Image img = new Image(stream);
                if (!img.isError()) {
                    ImageView imgView = new ImageView(img);
                    imgView.setFitWidth(62);
                    imgView.setFitHeight(62);
                    imgView.setPreserveRatio(false);
                    Rectangle clip = new Rectangle(62, 62);
                    clip.setArcWidth(8);
                    clip.setArcHeight(8);
                    imgView.setClip(clip);
                    pane.getChildren().add(imgView);
                    imgLoaded = true;
                }
            }
        } catch (Exception ignored) {}

        if (!imgLoaded) {
            Rectangle fallback = new Rectangle(62, 62);
            fallback.setArcWidth(8);
            fallback.setArcHeight(8);
            fallback.setFill(getFallbackColor(cell));
            // Border
            Rectangle border = new Rectangle(62, 62);
            border.setArcWidth(8);
            border.setArcHeight(8);
            border.setFill(Color.TRANSPARENT);
            border.setStroke(getBorderColor(cell));
            border.setStrokeWidth(1.5);
            pane.getChildren().addAll(fallback, border);
        }

        // Cell number (top-left)
        Label idxLabel = new Label(String.valueOf(index + 1));
        idxLabel.setStyle("-fx-text-fill: white; -fx-font-size: 7px;" +
                "-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 1 2;");
        StackPane.setAlignment(idxLabel, Pos.TOP_LEFT);
        pane.getChildren().add(idxLabel);

        // Door energy (bottom-right)
        if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            if (!door.isActivated()) {
                Label eLabel = new Label((door.getEnergy() > 0 ? "+" : "") + door.getEnergy());
                eLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 7px;" +
                        "-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 1 2;");
                StackPane.setAlignment(eLabel, Pos.BOTTOM_RIGHT);
                pane.getChildren().add(eLabel);
            } else {
                Label usedLabel = new Label("USED");
                usedLabel.setStyle("-fx-text-fill: #FF4444; -fx-font-size: 6px;" +
                        "-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 1 2;");
                StackPane.setAlignment(usedLabel, Pos.BOTTOM_RIGHT);
                pane.getChildren().add(usedLabel);
            }
        }

        // Stationed monster name (bottom-left)
        if (cell instanceof MonsterCell) {
            MonsterCell mc = (MonsterCell) cell;
            String shortName = mc.getCellMonster().getName();
            if (shortName.length() > 5) shortName = shortName.substring(0, 4) + ".";
            Label mLabel = new Label(shortName);
            mLabel.setStyle("-fx-text-fill: #00FF99; -fx-font-size: 6px;" +
                    "-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 1 2;");
            StackPane.setAlignment(mLabel, Pos.BOTTOM_LEFT);
            pane.getChildren().add(mLabel);
        }

        // Player token (blue circle with P)
        int playerPos   = game.getPlayer().getPosition();
        int opponentPos = game.getOpponent().getPosition();

        if (index == playerPos) {
            StackPane token = makeToken("P", PLAYER_COLOR);
            if (index == opponentPos) token.setTranslateX(-8);
            pane.getChildren().add(token);
        }

        // Opponent token (brown circle with O)
        if (index == opponentPos) {
            StackPane token = makeToken("O", OPPONENT_COLOR);
            if (index == playerPos) token.setTranslateX(8);
            pane.getChildren().add(token);
        }

        // Winning cell glow
        if (index == 99) {
            pane.setStyle("-fx-effect: dropshadow(gaussian, #FFD700, 8, 0.6, 0, 0);");
        }

        return pane;
    }

    private StackPane makeToken(String letter, String color) {
        Circle circle = new Circle(11);
        circle.setFill(Color.web(color));
        circle.setStroke(Color.web(GOLD));
        circle.setStrokeWidth(2);

        Label label = new Label(letter);
        label.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 9px; -fx-font-weight: bold;");

        StackPane token = new StackPane(circle, label);
        token.setAlignment(Pos.CENTER);
        return token;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PLAYER PANELS
    // ═══════════════════════════════════════════════════════════════

    private void refreshPanels() {
        buildPanel(playerPanel,   game.getPlayer(),   "YOU",       PLAYER_COLOR);
        buildPanel(opponentPanel, game.getOpponent(), "OPPONENT",  OPPONENT_COLOR);
    }

    private void buildPanel(VBox panel, Monster monster, String title, String color) {
        panel.getChildren().clear();
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setSpacing(6);

        // Title
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 13px; -fx-font-weight: bold;" +
                "-fx-background-color: " + color + "; -fx-padding: 4 12; -fx-background-radius: 6;");

        // Avatar
        ImageView avatar = new ImageView();
        avatar.setFitWidth(140);
        avatar.setFitHeight(140);
        avatar.setPreserveRatio(true);
        avatar.setSmooth(true);
        try {
            java.io.InputStream stream = getClass().getResourceAsStream(getMonsterNobgPath(monster));
            if (stream != null) {
                Image img = new Image(stream);
                if (!img.isError()) avatar.setImage(img);
            }
        } catch (Exception ignored) {}

        // Info labels
        Label nameLabel = new Label(monster.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        nameLabel.setWrapText(true);

        Label typeLabel = new Label("Type: " + monster.getClass().getSimpleName());
        typeLabel.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 10px;");

        Label roleLabel = new Label("Role: " + monster.getRole());
        roleLabel.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 10px;");

        // Energy bar
        int energy    = monster.getEnergy();
        int maxEnergy = 1000;
        double pct    = Math.min(1.0, (double) energy / maxEnergy);

        Label energyLabel = new Label("Energy: " + energy + " / " + maxEnergy);
        energyLabel.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        HBox energyBarBg = new HBox();
        energyBarBg.setPrefWidth(180);
        energyBarBg.setPrefHeight(8);
        energyBarBg.setStyle("-fx-background-color: #0D1B2A; -fx-background-radius: 4;");

        HBox energyBarFill = new HBox();
        energyBarFill.setPrefWidth(180 * pct);
        energyBarFill.setPrefHeight(8);
        String barColor = pct > 0.6 ? "#00CC66" : pct > 0.3 ? GOLD : "#FF4444";
        energyBarFill.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 4;");

        StackPane energyBar = new StackPane();
        energyBar.setAlignment(Pos.CENTER_LEFT);
        energyBar.getChildren().addAll(energyBarBg, energyBarFill);

        Label posLabel = new Label("Position: " + (monster.getPosition() + 1) + " / 100");
        posLabel.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 10px;");

        // Status effects
        String statusText = "";
        if (monster.isFrozen())    statusText += "FROZEN  ";
        if (monster.isShielded())  statusText += "SHIELDED  ";
        if (monster.isConfused())  statusText += "CONFUSED(" + monster.getConfusionTurns() + ")";

        if (!statusText.isEmpty()) {
            Label statusEff = new Label(statusText.trim());
            statusEff.setStyle("-fx-text-fill: #FF6B35; -fx-font-size: 9px; -fx-font-weight: bold;" +
                    "-fx-background-color: rgba(255,107,53,0.15); -fx-padding: 2 6; -fx-background-radius: 4;");
            panel.getChildren().addAll(titleLabel, avatar, nameLabel, typeLabel, roleLabel,
                    energyLabel, energyBar, posLabel, statusEff);
        } else {
            panel.getChildren().addAll(titleLabel, avatar, nameLabel, typeLabel, roleLabel,
                    energyLabel, energyBar, posLabel);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void updateTurnIndicator() {
        boolean isPlayerTurn = (game.getCurrent() == game.getPlayer());
        if (turnIndicator != null) {
            turnIndicator.setText(isPlayerTurn ? "YOUR TURN" : "OPPONENT'S TURN");
            turnIndicator.setStyle("-fx-text-fill: " + (isPlayerTurn ? GOLD : "#FF6B35") +
                    "; -fx-font-size: 14px; -fx-font-weight: bold;");
        }
        updateStatus(isPlayerTurn ? "Roll the dice!" : "Opponent is moving...");
    }

    private void updateStatus(String message) {
        if (statusLabel != null) statusLabel.setText(message);
    }

    private void addLog(String message) {
        if (eventLog == null) return;
        Label entry = new Label("• " + message);
        entry.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 10px;");
        entry.setWrapText(true);
        entry.setMaxWidth(Double.MAX_VALUE);
        eventLog.getChildren().add(0, entry); // newest at top
        // Cap at 25 entries
        while (eventLog.getChildren().size() > 25)
            eventLog.getChildren().remove(eventLog.getChildren().size() - 1);
    }

    private void addCellLog(int position) {
        Cell cell = getCellAt(position);
        if (cell instanceof CardCell)          addLog("Drew a card! Effects applied.");
        else if (cell instanceof ContaminationSock) addLog("Contamination Sock! -100 energy, moved back.");
        else if (cell instanceof ConveyorBelt)      addLog("Conveyor Belt! Moved forward.");
        else if (cell instanceof MonsterCell)        addLog("Monster Cell! Special effect triggered.");
        else if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            addLog("Door Cell (" + door.getRole() + ")! Team energy effects applied.");
        }
    }

    private Cell getCellAt(int position) {
        int row = position / 10;
        int col = position % 10;
        if (row % 2 == 1) col = 9 - col;
        try { return game.getBoard().getBoardCells()[row][col]; }
        catch (Exception e) { return null; }
    }

    private void animateDice(int finalValue, Runnable onFinished) {
        if (diceLabel == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        Random rand = new Random();
        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i < 12; i++) {
            final int r = rand.nextInt(6) + 1;
            PauseTransition pt = new PauseTransition(Duration.millis(40 + i * 15));
            pt.setOnFinished(e -> diceLabel.setText("[ " + r + " ]"));
            seq.getChildren().add(pt);
        }

        PauseTransition settle = new PauseTransition(Duration.millis(200));
        settle.setOnFinished(e -> {
            diceLabel.setText("[ " + finalValue + " ]");
            diceLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 26px; -fx-font-weight: bold;");
            // Bounce animation on the dice label
            ScaleTransition bounce = new ScaleTransition(Duration.millis(150), diceLabel);
            bounce.setToX(1.3);
            bounce.setToY(1.3);
            bounce.setAutoReverse(true);
            bounce.setCycleCount(2);
            bounce.setOnFinished(ev -> {
                diceLabel.setScaleX(1);
                diceLabel.setScaleY(1);
                if (onFinished != null) onFinished.run();
            });
            bounce.play();
        });
        seq.getChildren().add(settle);
        seq.play();
    }

    private void setButtonsEnabled(boolean enabled) {
        if (rollButton    != null) rollButton.setDisable(!enabled);
        if (powerupButton != null) powerupButton.setDisable(!enabled);
    }

    // ═══════════════════════════════════════════════════════════════
    //  WIN SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void showWinScreen(Monster winner) {
        gameOver = true;
        setButtonsEnabled(false);

        boolean playerWon = (winner == game.getPlayer());

        Stage winStage = new Stage();
        winStage.setTitle(playerWon ? "You Win!" : "Game Over");

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: " + BG + ";");

        // Big result label
        Label resultLabel = new Label(playerWon ? "YOU WIN!" : "YOU LOSE!");
        resultLabel.setFont(Font.font("Arial", FontWeight.BOLD, 52));
        resultLabel.setStyle("-fx-text-fill: " + GOLD + ";" +
                "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.5), 15, 0.5, 0, 0);");

        Label winnerLine = new Label(winner.getName() + " reached Boo's Door with "
                + winner.getEnergy() + " energy!");
        winnerLine.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 14px;");
        winnerLine.setWrapText(true);
        winnerLine.setMaxWidth(420);
        winnerLine.setTextAlignment(TextAlignment.CENTER);

        // Stats box
        VBox stats = new VBox(8);
        stats.setAlignment(Pos.CENTER);
        stats.setPadding(new Insets(15, 25, 15, 25));
        stats.setStyle("-fx-background-color: " + DARK_BG + "; -fx-background-radius: 10;");

        Label statsTitle = new Label("FINAL STATS");
        statsTitle.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label pStats = new Label(game.getPlayer().getName()
                + " (YOU)  Position: " + (game.getPlayer().getPosition() + 1)
                + "  Energy: " + game.getPlayer().getEnergy());
        pStats.setStyle("-fx-text-fill: #6A9AD4; -fx-font-size: 12px;");

        Label oStats = new Label(game.getOpponent().getName()
                + " (AI)  Position: " + (game.getOpponent().getPosition() + 1)
                + "  Energy: " + game.getOpponent().getEnergy());
        oStats.setStyle("-fx-text-fill: #C47A3E; -fx-font-size: 12px;");

        stats.getChildren().addAll(statsTitle, pStats, oStats);

        // Buttons
        HBox btnRow = new HBox(15);
        btnRow.setAlignment(Pos.CENTER);

        Button backBtn = new Button("BACK TO MENU");
        backBtn.setStyle("-fx-background-color: " + PLAYER_COLOR + "; -fx-text-fill: " + GOLD + ";" +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 28;" +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        backBtn.setOnMouseEntered(e -> backBtn.setStyle("-fx-background-color: #3A6A9A; -fx-text-fill: " + GOLD + ";" +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 28;" +
                "-fx-background-radius: 8; -fx-cursor: hand;"));
        backBtn.setOnMouseExited(e -> backBtn.setStyle("-fx-background-color: " + PLAYER_COLOR + "; -fx-text-fill: " + GOLD + ";" +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 28;" +
                "-fx-background-radius: 8; -fx-cursor: hand;"));
        backBtn.setOnAction(e -> {
            winStage.close();
            navigateToStart();
        });

        Button quitBtn = new Button("QUIT");
        quitBtn.setStyle("-fx-background-color: #4A1A1A; -fx-text-fill: #FF6B6B;" +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 28;" +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        quitBtn.setOnAction(e -> System.exit(0));

        btnRow.getChildren().addAll(backBtn, quitBtn);
        root.getChildren().addAll(resultLabel, winnerLine, stats, btnRow);

        // Fade in
        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), root);
        ft.setToValue(1);
        ft.play();

        Scene scene = new Scene(root, 520, 380);
        winStage.setScene(scene);
        winStage.show();
    }

    private void navigateToStart() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/StartView.fxml"));
            Parent startRoot  = loader.load();
            Scene  scene      = new Scene(startRoot, 1280, 720);
            Scale  scale      = new Scale();
            scale.xProperty().bind(scene.widthProperty().divide(1280));
            scale.yProperty().bind(scene.heightProperty().divide(720));
            startRoot.getTransforms().add(scale);
            Stage stage = (Stage) boardContainer.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ASSET HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String getImagePath(Cell cell, int index) {
        if (index == 99) return "/images/boo's door.png";
        if (cell instanceof MonsterCell) return getMonsterImagePath(((MonsterCell) cell).getCellMonster());
        if (cell instanceof CardCell)          return "/images/cards.png";
        if (cell instanceof ContaminationSock) return "/images/contamination sock.png";
        if (cell instanceof ConveyorBelt)      return "/images/gopass.png";
        if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            return door.getRole() == Role.SCARER ? "/images/scarer cell.png" : "/images/laugher cell.png";
        }
        return "/images/normal cell.png";
    }

    private String getMonsterImagePath(Monster monster) {
        String type = monster.getClass().getSimpleName().toLowerCase();
        Role   role = monster.getRole();
        switch (type) {
            case "dasher":      return role == Role.SCARER ? "/images/dasher scarer.png"      : "/images/dahser laugher.png";
            case "dynamo":      return role == Role.SCARER ? "/images/dynamo scarer.png"      : "/images/dynamo laugher.png";
            case "multitasker": return role == Role.SCARER ? "/images/multitasker scarer.png" : "/images/multitastker laugher.png";
            case "schemer":     return role == Role.SCARER ? "/images/schemer1.png"           : "/images/schemer2.png";
            default:            return "/images/normal cell.png";
        }
    }

    private String getMonsterNobgPath(Monster monster) {
        String type = monster.getClass().getSimpleName().toLowerCase();
        Role   role = monster.getRole();
        switch (type) {
            case "dasher":      return role == Role.SCARER ? "/images/dasher scarer_nobg.png"      : "/images/dasher laugher_nobg.png";
            case "dynamo":      return role == Role.SCARER ? "/images/dynamo scarer_nobg.png"      : "/images/dynamo laugher_nobg.png";
            case "multitasker": return role == Role.SCARER ? "/images/multitasker scarer_nobg.png" : "/images/multitasker laugher_nobg.png";
            case "schemer":     return role == Role.SCARER ? "/images/schemer1_nobg.png"           : "/images/schemer2_nobg.png";
            default:            return "/images/normal cell.png";
        }
    }

    private Color getFallbackColor(Cell cell) {
        if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            return door.getRole() == Role.SCARER ? Color.web("#2E4A7A") : Color.web("#7A4A1E");
        }
        if (cell instanceof CardCell)          return Color.web("#4A2E7A");
        if (cell instanceof MonsterCell)       return Color.web("#1A5C3A");
        if (cell instanceof ConveyorBelt)      return Color.web("#5C4A1A");
        if (cell instanceof ContaminationSock) return Color.web("#5C1A1A");
        return Color.web("#1E3A5C");
    }

    private Color getBorderColor(Cell cell) {
        if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            return door.getRole() == Role.SCARER ? Color.web("#4A7AB5") : Color.web("#B57A3E");
        }
        if (cell instanceof CardCell)          return Color.web("#7A4EB5");
        if (cell instanceof MonsterCell)       return Color.web("#2A8C5A");
        if (cell instanceof ConveyorBelt)      return Color.web("#B59A3E");
        if (cell instanceof ContaminationSock) return Color.web("#B53A3A");
        return Color.web("#2E4A6A");
    }
}
