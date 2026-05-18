package game.gui.controller;

import game.engine.*;
import game.engine.cards.Card;
import game.engine.cells.*;
import game.engine.exceptions.*;
import game.engine.monsters.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameController {

    // ── FXML fields ───────────────────────────────────────────────
    @FXML private VBox      playerPanel;
    @FXML private VBox      opponentPanel;
    @FXML private StackPane boardContainer;
    @FXML private Label     turnIndicator;
    @FXML private Label     statusLabel;
    @FXML private Label     eventLabel;
    @FXML private Button    powerupButton;
    @FXML private Button    menuButton;         // bottom-right menu toggle

    // ── Programmatic refs ─────────────────────────────────────────
    private ImageView diceImageView;
    private ImageView cardBackInPanel;
    private int       lastDiceFace = 1;

    // ── State ─────────────────────────────────────────────────────
    private Game      game;
    private String    playerName = "Player";
    private boolean   gameOver   = false;
    private GridPane  boardGrid;
    private Stage     gameStage;
    /** Maps each monster-cell linear index → the image path assigned to it (computed once per game). */
    private final Map<Integer, String> monsterCellImages = new HashMap<>();

    // ── Board geometry ────────────────────────────────────────────
    private static final double CELL_SIZE = 65;
    private static final double CELL_GAP  = 4;

    // ── Palette ───────────────────────────────────────────────────
    private static final String PLAYER_COLOR   = "#2E4A7A";
    private static final String OPPONENT_COLOR = "#7A4A1E";
    private static final String GOLD           = "#FFD700";
    private static final String BG             = "#1E3248";
    private static final String DARK_BG        = "#142236";
    private static final String TEXT_LIGHT     = "#CCCCEE";

    // ═══════════════════════════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════════════════════════

    public void initGame(Game game, String playerName) {
        this.game       = game;
        this.playerName = (playerName == null || playerName.trim().isEmpty()) ? "Player" : playerName.trim();
        this.gameOver   = false;

        buildBoard();
        refreshPanels();
        wireButtons();
        updateTurnIndicator();

        javafx.application.Platform.runLater(() -> {
            if (boardContainer.getScene() != null) {
                gameStage = (Stage) boardContainer.getScene().getWindow();
                // Req 1: open board in fullscreen
                gameStage.setFullScreen(true);
                gameStage.setFullScreenExitHint("");
                setupCheatKeys();
            }
        });

        updateEvent("Game on! " + this.playerName + " vs OPPONENT — click the dice to roll!");
    }

    // ── Button wiring ─────────────────────────────────────────────

    private void wireButtons() {
        if (powerupButton != null) {
            powerupButton.setOnAction(e -> handlePowerup());
            powerupButton.setOnMouseEntered(e -> {
                if (!powerupButton.isDisabled()) powerupButton.setStyle(btnSmStyle("#9A6A2E"));
            });
            powerupButton.setOnMouseExited(e -> updatePowerupButton());
        }
        if (menuButton != null) {
            menuButton.setOnAction(e -> showMenuPopup());
            menuButton.setOnMouseEntered(e -> menuButton.setStyle(
                    "-fx-background-color:#2E4A7A;-fx-text-fill:"+GOLD+
                            ";-fx-font-size:12px;-fx-font-weight:bold;-fx-background-radius:8;" +
                            "-fx-border-color:#2E4A7A;-fx-border-width:1;-fx-border-radius:8;-fx-cursor:hand;"));
            menuButton.setOnMouseExited(e -> menuButton.setStyle(
                    "-fx-background-color:#1A2E4A;-fx-text-fill:"+GOLD+
                            ";-fx-font-size:12px;-fx-font-weight:bold;-fx-background-radius:8;" +
                            "-fx-border-color:#2E4A7A;-fx-border-width:1;-fx-border-radius:8;-fx-cursor:hand;"));
        }
    }

    // ── Cheat keys ────────────────────────────────────────────────

    private void setupCheatKeys() {
        Scene sc = boardContainer.getScene();
        if (sc == null) return;
        // Consume SPACE at the filter level so no button ever fires on SPACE
        sc.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) e.consume();
        });
        sc.setOnKeyPressed(e -> {
            if (gameOver) return;
            // W → player wins (position + energy)
            if (e.getCode() == KeyCode.W) {
                game.getPlayer().setPosition(99);
                game.getPlayer().setEnergy(1500);
                refreshBoard(); refreshPanels();
                updateEvent("[CHEAT W] Player at cell 100 with 1500 energy!");
                Monster w = getUIWinner(); if (w != null) showEndScreen(w);
            }
            // E → boost player energy
            else if (e.getCode() == KeyCode.E) {
                game.getPlayer().setEnergy(game.getPlayer().getEnergy() + 300);
                refreshPanels(); updatePowerupButton();
                updateEvent("[CHEAT E] +300 energy (now " + game.getPlayer().getEnergy() + ")");
            }
            // L → opponent wins, triggers LOSING screen
            else if (e.getCode() == KeyCode.L) {
                game.getOpponent().setPosition(99);
                game.getOpponent().setEnergy(1500);
                refreshBoard(); refreshPanels();
                updateEvent("[CHEAT L] Opponent at cell 100 with 1500 energy!");
                Monster w = getUIWinner(); if (w != null) showEndScreen(w);
            }
        });
    }

    // ── Win detection ─────────────────────────────────────────────

    /**
     * Reqs 4 & 5: only position 99 + energy >= 1000 wins.
     * Explicit check so behaviour is clear regardless of engine implementation.
     */
    private Monster getUIWinner() {
        if (game.getPlayer().getPosition()   == 99 && game.getPlayer().getEnergy()   >= 1000) return game.getPlayer();
        if (game.getOpponent().getPosition() == 99 && game.getOpponent().getEnergy() >= 1000) return game.getOpponent();
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME ACTIONS
    // ═══════════════════════════════════════════════════════════════

    private void handleRollDice() {
        if (gameOver) return;
        if (game.getCurrent() != game.getPlayer()) { updateStatus("Not your turn!"); return; }

        setDiceEnabled(false); setButtonsEnabled(false);

        boolean playerFrozen = game.getPlayer().isFrozen();
        int     oldPos       = game.getPlayer().getPosition();
        int     enBefore     = game.getPlayer().getEnergy();
        Card    cardBefore   = Board.getLastDrawnCard();

        try {
            game.playTurn();

            if (playerFrozen) {
                updateStatus("You are FROZEN! Turn skipped."); updateEvent(playerName + " frozen — skipped.");
                refreshBoard(); refreshPanels(); checkWinnerThenAI(); return;
            }

            int  roll      = game.getLastRoll();
            int  newPos    = game.getPlayer().getPosition();
            int  enAfter   = game.getPlayer().getEnergy();
            Card cardAfter = Board.getLastDrawnCard();
            boolean drewCard = (cardAfter != cardBefore);

            String logMsg = playerName + " rolled " + roll + " | cell " + (oldPos+1) + " → " + (newPos+1);
            if (enAfter != enBefore) logMsg += " | energy " + (enAfter-enBefore > 0 ? "+" : "") + (enAfter-enBefore);
            final String log = logMsg;

            animateDice(roll, () -> {
                updateEvent(log);
                // Req 6: no token movement animation — direct board refresh
                refreshBoard();
                if (drewCard && cardAfter != null) {
                    updateStatus("Drew: " + cardAfter.getName() + "!");
                    playCardAnimation(cardAfter, () -> { refreshPanels(); checkWinnerThenAI(); });
                } else {
                    addCellMessage(newPos, enBefore, enAfter);
                    refreshPanels(); checkWinnerThenAI();
                }
            });

        } catch (InvalidMoveException ex) {
            int roll = game.getLastRoll();
            updateStatus("BLOCKED! Cannot land on opponent. Roll again.");
            updateEvent(playerName + " rolled " + roll + " — blocked!");
            animateDice(roll, () -> { refreshBoard(); refreshPanels(); setDiceEnabled(true); setButtonsEnabled(true); });
        }
    }

    private void checkWinnerThenAI() {
        Monster winner = getUIWinner();
        if (winner != null) { showEndScreen(winner); return; }
        updateTurnIndicator();
        PauseTransition delay = new PauseTransition(Duration.millis(1200));
        delay.setOnFinished(e -> aiTurn());
        delay.play();
    }

    private void aiTurn() {
        if (gameOver) return;

        boolean aiFrozen   = game.getOpponent().isFrozen();
        int     oldPos     = game.getOpponent().getPosition();
        int     enBefore   = game.getOpponent().getEnergy();
        Card    cardBefore = Board.getLastDrawnCard();

        try {
            game.playTurn();

            if (aiFrozen) {
                updateEvent("OPPONENT frozen — skipped."); refreshBoard(); refreshPanels(); afterAI(); return;
            }

            int  roll      = game.getLastRoll();
            int  newPos    = game.getOpponent().getPosition();
            int  enAfter   = game.getOpponent().getEnergy();
            Card cardAfter = Board.getLastDrawnCard();
            boolean drewCard = (cardAfter != cardBefore);

            Image aiDice = loadImage("/images/dice " + roll + ".png");
            if (aiDice != null && diceImageView != null) diceImageView.setImage(aiDice);
            lastDiceFace = roll;

            String logMsg = "OPPONENT rolled " + roll + " | cell " + (oldPos+1) + " → " + (newPos+1);
            if (enAfter != enBefore) logMsg += " | energy " + (enAfter-enBefore > 0 ? "+" : "") + (enAfter-enBefore);
            updateEvent(logMsg);

            // Req 6: no token animation — direct board refresh
            refreshBoard();
            if (drewCard && cardAfter != null) {
                updateStatus("OPPONENT drew: " + cardAfter.getName() + "!");
                playCardAnimation(cardAfter, () -> { refreshPanels(); afterAI(); });
            } else { refreshPanels(); afterAI(); }

        } catch (InvalidMoveException ex) {
            updateEvent("OPPONENT blocked — retrying...");
            refreshBoard(); refreshPanels();
            PauseTransition retry = new PauseTransition(Duration.millis(800));
            retry.setOnFinished(e -> aiTurn());
            retry.play();
        }
    }

    private void afterAI() {
        Monster winner = getUIWinner();
        if (winner != null) { showEndScreen(winner); return; }
        updateTurnIndicator();
        updateStatus("Click the dice to roll!");
        setDiceEnabled(true); setButtonsEnabled(true); updatePowerupButton();
    }

    private void handlePowerup() {
        if (gameOver) return;
        if (game.getCurrent() != game.getPlayer()) {
            updateStatus("Not your turn!"); updateEvent("Cannot use powerup — not your turn."); return;
        }
        int enBefore = game.getPlayer().getEnergy();
        try {
            game.usePowerup();
            int cost = enBefore - game.getPlayer().getEnergy();
            updateStatus("POWERUP activated! -" + cost + " energy.");
            updateEvent(playerName + " used POWERUP: " + getPowerupDesc(game.getPlayer()));
            animatePowerup(); refreshPanels(); updatePowerupButton();
        } catch (OutOfEnergyException e) {
            updateStatus("Not enough energy! Need 500, have " + game.getPlayer().getEnergy() + ".");
            updateEvent("Powerup failed — need 500 (have " + game.getPlayer().getEnergy() + ").");
        }
    }

    private String getPowerupDesc(Monster m) {
        if (m instanceof Dasher)      return "Momentum Rush! 3x speed for 3 turns.";
        if (m instanceof Dynamo)      return "Ice Blast! Opponent frozen for 1 turn.";
        if (m instanceof MultiTasker) return "Focus Mode! Normal speed for 2 turns.";
        if (m instanceof Schemer)     return "Chain Attack! Stole energy from all monsters.";
        return "Powerup activated!";
    }

    // ═══════════════════════════════════════════════════════════════
    //  MENU POPUP  (req 8)
    // ═══════════════════════════════════════════════════════════════

    private void showMenuPopup() {
        Stage menuStage = new Stage();
        menuStage.setTitle("Menu");
        menuStage.initModality(Modality.APPLICATION_MODAL);
        if (gameStage != null) menuStage.initOwner(gameStage);
        menuStage.setResizable(false);

        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30, 35, 30, 35));
        root.setStyle("-fx-background-color: #1E3248; -fx-border-color: #2E4A7A; -fx-border-width: 2;");

        Label title = new Label("GAME MENU");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #FFD700;");

        Separator sep = new Separator(); sep.setStyle("-fx-background-color: #2E4A7A;"); sep.setMaxWidth(220);

        // Req 8.1: Restart — same player name + role, no start screen
        Button restartBtn = new Button("↺   RESTART GAME");
        styleMenuPopupBtn(restartBtn, "#1A5C3A");
        restartBtn.setOnAction(e -> { menuStage.close(); handleRestart(); });

        // Req 8.2: Help — same instructions as start screen
        Button helpBtn = new Button("?   HOW TO PLAY");
        styleMenuPopupBtn(helpBtn, PLAYER_COLOR);
        helpBtn.setOnAction(e -> showHelp(menuStage));

        // Req 8.3: Back to game — just close this popup
        Button backBtn = new Button("◀   BACK TO GAME");
        styleMenuPopupBtn(backBtn, "#3A3A3A");
        backBtn.setOnAction(e -> menuStage.close());

        // Quit — close everything
        Button quitBtn = new Button("✕   QUIT GAME");
        styleMenuPopupBtn(quitBtn, "#4A1A1A");
        quitBtn.setStyle(quitBtn.getStyle().replace(GOLD, "#FF6B6B")); // red text for quit
        quitBtn.setOnAction(e -> System.exit(0));

        root.getChildren().addAll(title, sep, restartBtn, helpBtn, backBtn, quitBtn);

        menuStage.setScene(new Scene(root, 290, 350));
        menuStage.show();
    }

    private void styleMenuPopupBtn(Button btn, String color) {
        String base = "-fx-background-color:" + color + ";-fx-text-fill:" + GOLD +
                ";-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:9 0;" +
                "-fx-background-radius:8;-fx-cursor:hand;";
        btn.setPrefWidth(220); btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-opacity:0.82;"));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
    }

    /** Req 8.1: restart same player + role without going back to start screen */
    private void handleRestart() {
        try {
            Game newGame = new Game(game.getPlayer().getRole());
            initGame(newGame, playerName);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Req 8.2: instructions — same content as start screen */
    private void showHelp(Stage owner) {
        Stage helpStage = new Stage();
        helpStage.setTitle("How to Play — DoorDash");
        helpStage.initModality(Modality.APPLICATION_MODAL);
        helpStage.initOwner(owner != null ? owner : gameStage);

        BorderPane mainPane = new BorderPane();
        mainPane.setStyle("-fx-background-color: #1E3248;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER); titleBox.setPadding(new Insets(18, 20, 12, 20));
        titleBox.setStyle("-fx-background-color: #0D1B2A;");
        Label titleLbl = new Label("DOORDASH — GAME GUIDE");
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        titleLbl.setStyle("-fx-text-fill: #FFD700;");
        Separator ts = new Separator(); ts.setStyle("-fx-background-color: #FFD700;");
        titleBox.getChildren().addAll(titleLbl, ts);

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle("-fx-background: #1E3248; -fx-background-color: #1E3248;"); scroll.setFitToWidth(true);

        VBox body = new VBox(12); body.setPadding(new Insets(18)); body.setStyle("-fx-background-color: #1E3248;");

        body.getChildren().addAll(
                helpSection("WINNING",      "Land EXACTLY on cell 100 (Boo's Door) AND have 1000+ energy to win!"),
                helpSection("TURN ORDER",   "1) Powerup optional (costs 500 energy)\n2) Click the dice to roll\n3) Move your monster\n4) Cell effect activates"),
                helpSection("DOOR CELLS",   "Same role = gain energy. Opposite = lose energy. One-time use."),
                helpSection("CARD CELLS",   "Draw a random card — effect applies immediately."),
                helpSection("CONVEYORS",    "Move you FORWARD automatically."),
                helpSection("CONTAM. SOCK", "Move BACKWARD and lose 100 energy!"),
                helpSection("MONSTERS",     "DASHER: 2x speed\nDYNAMO: doubles all energy changes\nMULTITASKER: slower but +energy\nSCHEMER: +10 bonus to all changes"),
                helpSection("CHEATS",       "W = player jumps to cell 100 (+ 1500 energy)\nE = +300 energy\nL = opponent jumps to cell 100\nSPACE = roll the dice")
        );

        scroll.setContent(body);

        Button closeBtn = new Button("GOT IT!");
        closeBtn.setStyle("-fx-background-color:#FFD700;-fx-text-fill:#1E3248;-fx-font-size:14px;" +
                "-fx-font-weight:bold;-fx-padding:10 30;-fx-background-radius:20;-fx-cursor:hand;");
        closeBtn.setOnAction(e -> helpStage.close());
        HBox footer = new HBox(closeBtn); footer.setAlignment(Pos.CENTER); footer.setPadding(new Insets(12));
        footer.setStyle("-fx-background-color: #0D1B2A;");

        mainPane.setTop(titleBox); mainPane.setCenter(scroll); mainPane.setBottom(footer);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        helpStage.setScene(new Scene(mainPane, 600, 540));
        helpStage.show();
    }

    private VBox helpSection(String title, String body) {
        Label t = new Label(title); t.setFont(Font.font("Arial", FontWeight.BOLD, 13)); t.setStyle("-fx-text-fill:" + GOLD + ";");
        Label b = new Label(body); b.setWrapText(true); b.setStyle("-fx-text-fill:" + TEXT_LIGHT + ";-fx-font-size:12px;");
        VBox box = new VBox(5, t, b); box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle("-fx-background-color:rgba(46,74,122,0.2);-fx-background-radius:8;" +
                "-fx-border-color:#2E4A7A;-fx-border-radius:8;-fx-border-width:1;");
        return box;
    }

    // ═══════════════════════════════════════════════════════════════
    //  BOARD
    // ═══════════════════════════════════════════════════════════════

    private void buildBoard() {
        computeMonsterCellImages();   // assign unique monsters to cells before rendering
        boardContainer.getChildren().clear();
        boardGrid = new GridPane();
        boardGrid.setHgap(CELL_GAP); boardGrid.setVgap(CELL_GAP); boardGrid.setAlignment(Pos.CENTER);
        renderCells();
        boardContainer.getChildren().add(boardGrid);
    }

    private void refreshBoard() {
        if (boardGrid == null) { buildBoard(); return; }
        boardGrid.getChildren().clear();
        renderCells();
    }

    private void renderCells() {
        Cell[][] cells = game.getBoard().getBoardCells();
        for (int row = 0; row < 10; row++)
            for (int col = 0; col < 10; col++)
                boardGrid.add(createCellPane(cells[row][col], linearIndex(row, col)), col, 9 - row);
    }

    private int linearIndex(int row, int col) {
        return row * 10 + ((row % 2 == 0) ? col : (9 - col));
    }

    private StackPane createCellPane(Cell cell, int index) {
        StackPane pane = new StackPane();
        pane.setPrefSize(CELL_SIZE, CELL_SIZE); pane.setMaxSize(CELL_SIZE, CELL_SIZE);

        // Req 2: getImagePath returns null for excluded monster types → use fallback colours
        String imgPath = getImagePath(cell, index);
        boolean loaded = false;
        if (imgPath != null) {
            try {
                InputStream s = getClass().getResourceAsStream(imgPath);
                if (s != null) {
                    Image img = new Image(s);
                    if (!img.isError()) {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(CELL_SIZE); iv.setFitHeight(CELL_SIZE); iv.setPreserveRatio(false);
                        Rectangle clip = new Rectangle(CELL_SIZE, CELL_SIZE);
                        clip.setArcWidth(9); clip.setArcHeight(9); iv.setClip(clip);
                        pane.getChildren().add(iv); loaded = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!loaded) {
            Rectangle bg  = new Rectangle(CELL_SIZE, CELL_SIZE); bg.setArcWidth(9);  bg.setArcHeight(9);  bg.setFill(getFallbackColor(cell));
            Rectangle brd = new Rectangle(CELL_SIZE, CELL_SIZE); brd.setArcWidth(9); brd.setArcHeight(9);
            brd.setFill(Color.TRANSPARENT); brd.setStroke(getBorderColor(cell)); brd.setStrokeWidth(1.5);
            pane.getChildren().addAll(bg, brd);
        }

        Label idx = new Label(String.valueOf(index + 1));
        idx.setStyle("-fx-text-fill:white;-fx-font-size:8px;-fx-background-color:rgba(0,0,0,0.55);-fx-padding:1 3;");
        StackPane.setAlignment(idx, Pos.TOP_LEFT); pane.getChildren().add(idx);

        if (cell instanceof DoorCell) {
            DoorCell door = (DoorCell) cell;
            Label el = door.isActivated() ? miniLabel("USED","#FF4444")
                    : miniLabel((door.getEnergy()>0?"+":"")+door.getEnergy(), GOLD);
            StackPane.setAlignment(el, Pos.BOTTOM_RIGHT); pane.getChildren().add(el);
        }
        if (cell instanceof MonsterCell) {
            MonsterCell mc = (MonsterCell) cell;
            String n = mc.getCellMonster().getName(); if (n.length()>5) n=n.substring(0,4)+".";
            Label ml = miniLabel(n,"#00FF99"); StackPane.setAlignment(ml,Pos.BOTTOM_LEFT); pane.getChildren().add(ml);
        }

        int pp=game.getPlayer().getPosition(), op=game.getOpponent().getPosition();
        if (index==pp) { StackPane t=makeToken("P",PLAYER_COLOR);   if(index==op) t.setTranslateX(-9); pane.getChildren().add(t); }
        if (index==op) { StackPane t=makeToken("O",OPPONENT_COLOR); if(index==pp) t.setTranslateX(9);  pane.getChildren().add(t); }
        if (index==99) pane.setStyle("-fx-effect:dropshadow(gaussian,#FFD700,12,0.7,0,0);");
        return pane;
    }

    private Label miniLabel(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:"+color+";-fx-font-size:7px;-fx-background-color:rgba(0,0,0,0.6);-fx-padding:1 2;");
        return l;
    }

    private StackPane makeToken(String letter, String color) {
        Circle c=new Circle(12); c.setFill(Color.web(color)); c.setStroke(Color.web(GOLD)); c.setStrokeWidth(2);
        Label l=new Label(letter); l.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:9px;-fx-font-weight:bold;");
        StackPane sp=new StackPane(c,l); sp.setAlignment(Pos.CENTER); return sp;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANELS
    // ═══════════════════════════════════════════════════════════════

    private void refreshPanels() {
        buildPanel(playerPanel,   game.getPlayer(),   playerName,  PLAYER_COLOR,   true);
        buildPanel(opponentPanel, game.getOpponent(), "OPPONENT",  OPPONENT_COLOR, false);
        updatePowerupButton();
    }

    private void buildPanel(VBox panel, Monster monster, String title, String color, boolean isPlayer) {
        panel.getChildren().clear();
        panel.setAlignment(Pos.TOP_CENTER); panel.setSpacing(5);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:15px;-fx-font-weight:bold;" +
                "-fx-background-color:"+color+";-fx-padding:5 14;-fx-background-radius:6;");

        ImageView avatar=new ImageView(); avatar.setFitWidth(145); avatar.setFitHeight(145); avatar.setPreserveRatio(true); avatar.setSmooth(true);
        try { InputStream s=getClass().getResourceAsStream(getMonsterNobgPath(monster)); if(s!=null){Image img=new Image(s); if(!img.isError()) avatar.setImage(img);} } catch(Exception ignored){}

        StackPane avatarStack=new StackPane(avatar);
        String statusPath=getStatusIconPath(monster);
        if(statusPath!=null){ Image si=loadImage(statusPath); if(si!=null){ ImageView sv=new ImageView(si); sv.setFitWidth(48); sv.setFitHeight(48); sv.setPreserveRatio(true); StackPane.setAlignment(sv,Pos.BOTTOM_RIGHT); avatarStack.getChildren().add(sv); } }

        Label nameLbl=new Label(monster.getName());   nameLbl.setStyle("-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:bold;"); nameLbl.setWrapText(true);
        Label typeLbl=new Label("Type: "+monster.getClass().getSimpleName()); typeLbl.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:14px;");
        Label roleLbl=new Label("Role: "+monster.getRole());                  roleLbl.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:14px;");

        int energy=monster.getEnergy();
        Label energyLbl=new Label("Energy: "+energy+" / 1000"); energyLbl.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:15px;-fx-font-weight:bold;");

        double pct=Math.min(1.0,(double)energy/1000);
        HBox barBg=new HBox(); barBg.setPrefWidth(192); barBg.setPrefHeight(12); barBg.setStyle("-fx-background-color:#0D1B2A;-fx-background-radius:6;");
        HBox barFill=new HBox(); barFill.setPrefWidth(192*pct); barFill.setPrefHeight(12);
        String bc=pct>0.6?"#00CC66":pct>0.3?GOLD:"#FF4444";
        barFill.setStyle("-fx-background-color:"+bc+";-fx-background-radius:6;");
        StackPane bar=new StackPane(); bar.setAlignment(Pos.CENTER_LEFT); bar.getChildren().addAll(barBg,barFill);

        Label posLbl=new Label("Position: "+(monster.getPosition()+1)+" / 100"); posLbl.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:14px;");

        panel.getChildren().addAll(titleLbl,avatarStack,nameLbl,typeLbl,roleLbl,energyLbl,bar,posLbl);

        Separator sep=new Separator(); sep.setStyle("-fx-background-color:"+color+";"); sep.setMaxWidth(192);
        panel.getChildren().add(sep);

        if(isPlayer) addDiceToPanel(panel); else addCardBackToPanel(panel);
    }

    private String getStatusIconPath(Monster m){
        if(m.isFrozen())   return "/images/icon frozen.png";
        if(m.isShielded()) return "/images/icon sheild.png";
        if(m.isConfused()) return "/images/icon confused.png";
        return null;
    }

    private void addDiceToPanel(VBox panel) {
        diceImageView=new ImageView(); diceImageView.setFitWidth(92); diceImageView.setFitHeight(92); diceImageView.setPreserveRatio(true); diceImageView.setSmooth(true);
        Image img=loadImage("/images/dice "+lastDiceFace+".png"); if(img!=null) diceImageView.setImage(img);
        Rectangle clip=new Rectangle(92,92); clip.setArcWidth(18); clip.setArcHeight(18); diceImageView.setClip(clip);
        diceImageView.setStyle("-fx-cursor:hand;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),10,0.4,0,2);");
        diceImageView.setOnMouseClicked(e->handleRollDice());
        diceImageView.setOnMouseEntered(e->{ ScaleTransition st=new ScaleTransition(Duration.millis(150),diceImageView); st.setToX(1.1); st.setToY(1.1); st.play(); });
        diceImageView.setOnMouseExited(e ->{ ScaleTransition st=new ScaleTransition(Duration.millis(150),diceImageView); st.setToX(1.0); st.setToY(1.0); st.play(); });
        VBox.setMargin(diceImageView,new Insets(12,0,0,0));
        Label hint=new Label("CLICK DICE TO ROLL"); hint.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:9px;");
        panel.getChildren().addAll(diceImageView,hint);
    }

    private void addCardBackToPanel(VBox panel) {
        cardBackInPanel=new ImageView(); cardBackInPanel.setFitWidth(115); cardBackInPanel.setFitHeight(162); cardBackInPanel.setPreserveRatio(false); cardBackInPanel.setSmooth(true);
        Image img=loadImage("/images/backcard.png"); if(img!=null) cardBackInPanel.setImage(img);
        VBox.setMargin(cardBackInPanel,new Insets(14,0,0,0));
        Label hint=new Label("CARD DECK"); hint.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:9px;");
        panel.getChildren().addAll(cardBackInPanel,hint);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    private void animateDice(int finalValue, Runnable onFinished) {
        if(diceImageView==null){ if(onFinished!=null) onFinished.run(); return; }
        Random rand=new Random();
        ScaleTransition up=new ScaleTransition(Duration.millis(180),diceImageView); up.setToX(1.35); up.setToY(1.35);
        up.setOnFinished(e->{
            SequentialTransition cycle=new SequentialTransition();
            for(int i=0;i<10;i++){ final int r=rand.nextInt(6)+1; PauseTransition pt=new PauseTransition(Duration.millis(40+i*12)); pt.setOnFinished(ev->{ Image di=loadImage("/images/dice "+r+".png"); if(di!=null) diceImageView.setImage(di); }); cycle.getChildren().add(pt); }
            PauseTransition finish=new PauseTransition(Duration.millis(120));
            finish.setOnFinished(ev->{ Image fi=loadImage("/images/dice "+finalValue+".png"); if(fi!=null) diceImageView.setImage(fi); lastDiceFace=finalValue;
                ScaleTransition down=new ScaleTransition(Duration.millis(180),diceImageView); down.setToX(1.0); down.setToY(1.0);
                down.setOnFinished(ev2->{ if(onFinished!=null) onFinished.run(); }); down.play(); });
            cycle.getChildren().add(finish); cycle.play();
        }); up.play();
    }

    /** Req 7: card travels from opponent panel → centre, flips, holds 2.5 s, returns */
    private void playCardAnimation(Card card, Runnable onFinished) {
        double cardW=175, cardH=245, centerX=boardContainer.getWidth()/2, centerY=boardContainer.getHeight()/2;
        double startTX, startTY, startSX=0.65, startSY=0.65;
        if(cardBackInPanel!=null&&boardContainer.getWidth()>0){
            try{ Bounds b=cardBackInPanel.localToScene(cardBackInPanel.getBoundsInLocal()); Point2D p=boardContainer.sceneToLocal(b.getCenterX(),b.getCenterY()); startTX=p.getX()-cardW/2; startTY=p.getY()-cardH/2; }
            catch(Exception ex){ startTX=boardContainer.getWidth()-cardW-15; startTY=centerY-cardH/2; }
        } else{ startTX=boardContainer.getWidth()-cardW-15; startTY=centerY-cardH/2; }
        double targetTX=centerX-cardW/2, targetTY=centerY-cardH/2, dx=targetTX-startTX, dy=targetTY-startTY;

        StackPane overlay=new StackPane(); overlay.setStyle("-fx-background-color:rgba(0,0,0,0.55);"); boardContainer.getChildren().add(overlay);
        Image backImg=loadImage("/images/backcard.png"), frontImg=loadImage(getCardImagePath(card));
        ImageView fc=new ImageView(); fc.setFitWidth(cardW); fc.setFitHeight(cardH); fc.setPreserveRatio(false); fc.setSmooth(true);
        if(backImg!=null) fc.setImage(backImg); fc.setScaleX(startSX); fc.setScaleY(startSY);
        StackPane.setAlignment(fc,Pos.TOP_LEFT); fc.setTranslateX(startTX); fc.setTranslateY(startTY);
        boardContainer.getChildren().add(fc);

        Label cardLbl=new Label(card!=null?card.getName():""); cardLbl.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-background-color:rgba(0,0,0,0.75);-fx-padding:5 12;-fx-background-radius:6;"); cardLbl.setOpacity(0); cardLbl.setTranslateY(130); boardContainer.getChildren().add(cardLbl);
        if(cardBackInPanel!=null) cardBackInPanel.setOpacity(0);

        TranslateTransition moveIn=new TranslateTransition(Duration.millis(420),fc); moveIn.setByX(dx); moveIn.setByY(dy);
        ScaleTransition scaleUp=new ScaleTransition(Duration.millis(420),fc); scaleUp.setToX(1.0); scaleUp.setToY(1.0);
        ParallelTransition arrive=new ParallelTransition(moveIn,scaleUp);
        ScaleTransition h1=new ScaleTransition(Duration.millis(180),fc); h1.setToX(0);
        ScaleTransition h2=new ScaleTransition(Duration.millis(180),fc); h2.setFromX(0); h2.setToX(1);
        PauseTransition hold=new PauseTransition(Duration.millis(2500)); // req 7: 2.5 seconds
        ScaleTransition b1=new ScaleTransition(Duration.millis(180),fc); b1.setToX(0);
        ScaleTransition b2=new ScaleTransition(Duration.millis(180),fc); b2.setFromX(0); b2.setToX(1);
        TranslateTransition moveOut=new TranslateTransition(Duration.millis(420),fc); moveOut.setByX(-dx); moveOut.setByY(-dy);
        ScaleTransition scaleDown=new ScaleTransition(Duration.millis(420),fc); scaleDown.setToX(startSX); scaleDown.setToY(startSY);
        ParallelTransition depart=new ParallelTransition(moveOut,scaleDown);

        arrive.setOnFinished(e->h1.play());
        h1.setOnFinished(e->{ if(frontImg!=null) fc.setImage(frontImg); FadeTransition fn=new FadeTransition(Duration.millis(150),cardLbl); fn.setToValue(1); fn.play(); h2.play(); });
        h2.setOnFinished(e->hold.play());
        hold.setOnFinished(e->b1.play());
        b1.setOnFinished(e->{ if(backImg!=null) fc.setImage(backImg); FadeTransition fn=new FadeTransition(Duration.millis(150),cardLbl); fn.setToValue(0); fn.play(); b2.play(); });
        b2.setOnFinished(e->depart.play());
        depart.setOnFinished(e->{ boardContainer.getChildren().removeAll(fc,overlay,cardLbl); if(cardBackInPanel!=null) cardBackInPanel.setOpacity(1); if(onFinished!=null) onFinished.run(); });
        arrive.play();
    }

    private void animatePowerup() {
        if(powerupButton!=null){
            ScaleTransition pop=new ScaleTransition(Duration.millis(130),powerupButton); pop.setToX(1.25); pop.setToY(1.25); pop.setAutoReverse(true); pop.setCycleCount(2);
            pop.setOnFinished(e->{ powerupButton.setScaleX(1); powerupButton.setScaleY(1); }); pop.play();
            powerupButton.setStyle(btnSmStyle(OPPONENT_COLOR)+"-fx-effect:dropshadow(gaussian,"+GOLD+",18,0.8,0,0);");
            PauseTransition sp=new PauseTransition(Duration.millis(600)); sp.setOnFinished(e->updatePowerupButton()); sp.play();
        }
        Image icon=loadImage("/images/icon powerup.png");
        if(icon!=null){
            ImageView iv=new ImageView(icon); iv.setFitWidth(90); iv.setFitHeight(90); iv.setOpacity(0); iv.setScaleX(0.3); iv.setScaleY(0.3);
            boardContainer.getChildren().add(iv);
            ScaleTransition grow=new ScaleTransition(Duration.millis(300),iv); grow.setToX(1.4); grow.setToY(1.4);
            FadeTransition fi=new FadeTransition(Duration.millis(250),iv); fi.setToValue(1);
            ParallelTransition appear=new ParallelTransition(grow,fi);
            appear.setOnFinished(e->{ PauseTransition h=new PauseTransition(Duration.millis(300)); h.setOnFinished(ev->{ FadeTransition fo=new FadeTransition(Duration.millis(400),iv); fo.setToValue(0); fo.setOnFinished(ev2->boardContainer.getChildren().remove(iv)); fo.play(); }); h.play(); }); appear.play();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  END SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void showEndScreen(Monster winner) {
        if(gameOver) return;
        gameOver=true; setDiceEnabled(false); setButtonsEnabled(false);

        boolean playerWon=(winner==game.getPlayer());
        Stage ws=new Stage(); ws.setTitle(playerWon?"You Win!":"Game Over");
        ws.initModality(Modality.APPLICATION_MODAL);
        if(gameStage!=null) ws.initOwner(gameStage);

        StackPane root=new StackPane();
        Image bgImg=loadImage(playerWon?"/images/winning screen.png":"/images/losing screen.png");
        if(bgImg!=null){ ImageView bg=new ImageView(bgImg); bg.setFitWidth(640); bg.setFitHeight(480); bg.setPreserveRatio(false); root.getChildren().add(bg); }
        else { root.setStyle("-fx-background-color:"+BG+";"); }

        VBox content=new VBox(12); content.setAlignment(Pos.BOTTOM_CENTER); content.setPadding(new Insets(0,30,35,30));

        ImageView monImg=new ImageView(); monImg.setFitWidth(130); monImg.setFitHeight(130); monImg.setPreserveRatio(true); monImg.setSmooth(true);
        Image mi=loadImage(getMonsterNobgPath(game.getPlayer())); if(mi!=null) monImg.setImage(mi);

        VBox stats=new VBox(6); stats.setAlignment(Pos.CENTER); stats.setPadding(new Insets(12,20,12,20));
        stats.setStyle("-fx-background-color:rgba(13,27,42,0.88);-fx-background-radius:10;");
        Label st=new Label("FINAL STATS"); st.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:13px;-fx-font-weight:bold;");
        Label ps=new Label(playerName+"  |  Cell: "+(game.getPlayer().getPosition()+1)+"  |  Energy: "+game.getPlayer().getEnergy()); ps.setStyle("-fx-text-fill:#6A9AD4;-fx-font-size:13px;");
        Label os=new Label("OPPONENT ("+game.getOpponent().getName()+")  |  Cell: "+(game.getOpponent().getPosition()+1)+"  |  Energy: "+game.getOpponent().getEnergy()); os.setStyle("-fx-text-fill:#C47A3E;-fx-font-size:13px;");
        stats.getChildren().addAll(st,ps,os);

        HBox btnRow=new HBox(15); btnRow.setAlignment(Pos.CENTER);
        Button backBtn=new Button("BACK TO MENU");
        backBtn.setStyle("-fx-background-color:"+PLAYER_COLOR+";-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;");
        backBtn.setOnMouseEntered(e->backBtn.setStyle("-fx-background-color:#3A6A9A;-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;"));
        backBtn.setOnMouseExited(e ->backBtn.setStyle("-fx-background-color:"+PLAYER_COLOR+";-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;"));
        backBtn.setOnAction(e->{ ws.close(); navigateToStart(); });
        Button quitBtn=new Button("QUIT"); quitBtn.setStyle("-fx-background-color:#4A1A1A;-fx-text-fill:#FF6B6B;-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;");
        quitBtn.setOnAction(e->System.exit(0));
        btnRow.getChildren().addAll(backBtn,quitBtn);

        content.getChildren().addAll(monImg,stats,btnRow);
        root.getChildren().add(content);

        Scene scene=new Scene(root,640,480); ws.setScene(scene); ws.show(); ws.requestFocus(); ws.toFront();
    }

    private void navigateToStart() {
        try {
            // Req 1: exit fullscreen before returning to start screen
            if(gameStage!=null) gameStage.setFullScreen(false);
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/StartView.fxml"));
            Parent startRoot=loader.load();
            Scene scene=new Scene(startRoot,1280,720);
            Scale scale=new Scale();
            scale.xProperty().bind(scene.widthProperty().divide(1280));
            scale.yProperty().bind(scene.heightProperty().divide(720));
            startRoot.getTransforms().add(scale);
            if(gameStage!=null) gameStage.setScene(scene);
        } catch(Exception ex){ ex.printStackTrace(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void updateTurnIndicator() {
        boolean isPlayer=(game.getCurrent()==game.getPlayer());
        if(turnIndicator!=null){ turnIndicator.setText(isPlayer?playerName.toUpperCase()+"'S TURN":"OPPONENT'S TURN"); turnIndicator.setStyle("-fx-text-fill:"+(isPlayer?GOLD:"#FF6B35")+";-fx-font-size:18px;-fx-font-weight:bold;"); }
        updateStatus(isPlayer?"Click the dice to roll!":"Opponent is moving...");
    }

    private void updateStatus(String msg){ if(statusLabel!=null) statusLabel.setText(msg); }
    private void updateEvent(String msg) { if(eventLabel!=null)  eventLabel.setText(msg); }

    private void setDiceEnabled(boolean on){ if(diceImageView!=null){ diceImageView.setMouseTransparent(!on); diceImageView.setOpacity(on?1.0:0.45); } }
    private void setButtonsEnabled(boolean on){ if(powerupButton!=null) powerupButton.setDisable(!on); }

    private void updatePowerupButton() {
        if(powerupButton==null) return;
        boolean can=!gameOver&&(game.getCurrent()==game.getPlayer())&&(game.getPlayer().getEnergy()>=500);
        powerupButton.setStyle(can?btnSmStyle(OPPONENT_COLOR):btnSmStyle("#3A2A1A")); powerupButton.setOpacity(can?1.0:0.55); powerupButton.setDisable(false);
    }

    private void addCellMessage(int pos, int enBefore, int enAfter) {
        Cell c=getCellAt(pos);
        if(c instanceof ContaminationSock) updateEvent("Contamination Sock! Moved back, -100 energy.");
        else if(c instanceof ConveyorBelt) updateEvent("Conveyor Belt! Moved forward.");
        else if(c instanceof MonsterCell)  updateEvent("Monster Cell! Effect triggered.");
        else if(c instanceof DoorCell){ DoorCell d=(DoorCell)c; updateEvent("Door Cell ("+d.getRole()+") — "+(d.getRole()==game.getPlayer().getRole()?"energy gained!":"energy lost!")); }
    }

    private Cell getCellAt(int p){ int r=p/10,c=p%10; if(r%2==1)c=9-c; try{return game.getBoard().getBoardCells()[r][c];}catch(Exception e){return null;} }

    private String btnSmStyle(String color){ return "-fx-background-color:"+color+";-fx-text-fill:"+GOLD+";-fx-font-size:12px;-fx-font-weight:bold;-fx-background-radius:8;-fx-cursor:hand;"; }

    // ═══════════════════════════════════════════════════════════════
    //  ASSET HELPERS
    // ═══════════════════════════════════════════════════════════════

    private Image loadImage(String path){ try{ InputStream s=getClass().getResourceAsStream(path); if(s!=null) return new Image(s); }catch(Exception ignored){} return null; }

    private String getCardImagePath(Card card){
        if(card==null) return "/images/backcard.png";
        switch(card.getName()){
            case "Position Swap":      return "/images/swapper.png";
            case "Super Shield":       return "/images/super sheild.png";
            case "Small Snatcher":     return "/images/small snatcher.png";
            case "Sneaky Thief":       return "/images/sneaky thief.png";
            case "Contamination Code": return "/images/contamination code.png";
            case "2319 Alert":         return "/images/2319 alert.png";
            case "Mind Scramble":      return "/images/mind scramble.png";
            case "Total Confusion":    return "/images/total confusion.png";
            default:                   return "/images/backcard.png";
        }
    }

    private String getImagePath(Cell cell, int index) {
        if(index==99) return "/images/boo's door.png";
        if(cell instanceof MonsterCell){
            // Always use the pre-assigned unique monster image for this cell
            String assigned = monsterCellImages.get(index);
            if (assigned != null) return assigned;
            return getMonsterImagePath(((MonsterCell) cell).getCellMonster());
        }
        if(cell instanceof CardCell)          return "/images/backcard.png";
        if(cell instanceof ContaminationSock) return "/images/contamination sock.png";
        if(cell instanceof ConveyorBelt)      return "/images/gopass.png";
        if(cell instanceof DoorCell){ DoorCell d=(DoorCell)cell; return d.getRole()==Role.SCARER?"/images/scarer cell.png":"/images/laugher cell.png"; }
        return "/images/normal cell.png";
    }

    /**
     * Builds the pool of 6 available monsters and assigns each uniquely to one of the
     * 6 monster cells on the board. Called once when the board is constructed.
     */
    private void computeMonsterCellImages() {
        monsterCellImages.clear();

        // 1. Build ordered pool of the 6 remaining monsters
        List<String> pool = buildAvailableMonsterPool();

        // 2. Collect all monster-cell linear indices in board traversal order
        Cell[][] cells = game.getBoard().getBoardCells();
        List<Integer> mcIndices = new ArrayList<>();
        for (int row = 0; row < 10; row++)
            for (int col = 0; col < 10; col++)
                if (cells[row][col] instanceof MonsterCell)
                    mcIndices.add(linearIndex(row, col));

        // 3. Assign one unique monster image to each cell (zip the two lists)
        for (int i = 0; i < mcIndices.size() && i < pool.size(); i++)
            monsterCellImages.put(mcIndices.get(i), pool.get(i));
    }

    /** Returns the 6 monster image paths that are NOT used by the player or opponent. */
    private List<String> buildAvailableMonsterPool() {
        Class<?> pc  = game.getPlayer().getClass();
        Role     pr  = game.getPlayer().getRole();
        Class<?> oc  = game.getOpponent().getClass();
        Role     or2 = game.getOpponent().getRole();

        List<String> pool = new ArrayList<>();

        if (!(Dasher.class.equals(pc)      && pr  == Role.SCARER)  && !(Dasher.class.equals(oc)      && or2 == Role.SCARER))
            pool.add("/images/dasher scarer.png");
        if (!(Dasher.class.equals(pc)      && pr  == Role.LAUGHER) && !(Dasher.class.equals(oc)      && or2 == Role.LAUGHER))
            pool.add("/images/dahser laugher.png");
        if (!(Dynamo.class.equals(pc)      && pr  == Role.SCARER)  && !(Dynamo.class.equals(oc)      && or2 == Role.SCARER))
            pool.add("/images/dynamo scarer.png");
        if (!(Dynamo.class.equals(pc)      && pr  == Role.LAUGHER) && !(Dynamo.class.equals(oc)      && or2 == Role.LAUGHER))
            pool.add("/images/dynamo laugher.png");
        if (!(MultiTasker.class.equals(pc) && pr  == Role.SCARER)  && !(MultiTasker.class.equals(oc) && or2 == Role.SCARER))
            pool.add("/images/multitasker scarer.png");
        if (!(MultiTasker.class.equals(pc) && pr  == Role.LAUGHER) && !(MultiTasker.class.equals(oc) && or2 == Role.LAUGHER))
            pool.add("/images/multitastker laugher.png");
        if (!(Schemer.class.equals(pc)     && pr  == Role.SCARER)  && !(Schemer.class.equals(oc)     && or2 == Role.SCARER))
            pool.add("/images/schemer1.png");
        if (!(Schemer.class.equals(pc)     && pr  == Role.LAUGHER) && !(Schemer.class.equals(oc)     && or2 == Role.LAUGHER))
            pool.add("/images/schemer2.png");

        return pool;   // will always contain exactly 6 entries
    }

    private String getMonsterImagePath(Monster m){
        boolean sc=m.getRole()==Role.SCARER;
        switch(m.getClass().getSimpleName().toLowerCase()){
            case "dasher":      return sc?"/images/dasher scarer.png"     :"/images/dahser laugher.png";
            case "dynamo":      return sc?"/images/dynamo scarer.png"     :"/images/dynamo laugher.png";
            case "multitasker": return sc?"/images/multitasker scarer.png":"/images/multitastker laugher.png";
            case "schemer":     return sc?"/images/schemer1.png"          :"/images/schemer2.png";
            default:            return "/images/normal cell.png";
        }
    }

    private String getMonsterNobgPath(Monster m){
        boolean sc=m.getRole()==Role.SCARER;
        switch(m.getClass().getSimpleName().toLowerCase()){
            case "dasher":      return sc?"/images/dasher scarer_nobg.png"     :"/images/dasher laugher_nobg.png";
            case "dynamo":      return sc?"/images/dynamo scarer_nobg.png"     :"/images/dynamo laugher_nobg.png";
            case "multitasker": return sc?"/images/multitasker scarer_nobg.png":"/images/multitasker laugher_nobg.png";
            case "schemer":     return sc?"/images/schemer1_nobg.png"          :"/images/schemer2_nobg.png";
            default:            return "/images/normal cell.png";
        }
    }

    private Color getFallbackColor(Cell cell){
        if(cell instanceof DoorCell) return((DoorCell)cell).getRole()==Role.SCARER?Color.web("#2E4A7A"):Color.web("#7A4A1E");
        if(cell instanceof CardCell)          return Color.web("#4A2E7A");
        if(cell instanceof MonsterCell)       return Color.web("#1A5C3A");
        if(cell instanceof ConveyorBelt)      return Color.web("#5C4A1A");
        if(cell instanceof ContaminationSock) return Color.web("#5C1A1A");
        return Color.web("#1E3A5C");
    }

    private Color getBorderColor(Cell cell){
        if(cell instanceof DoorCell) return((DoorCell)cell).getRole()==Role.SCARER?Color.web("#4A7AB5"):Color.web("#B57A3E");
        if(cell instanceof CardCell)          return Color.web("#7A4EB5");
        if(cell instanceof MonsterCell)       return Color.web("#2A8C5A");
        if(cell instanceof ConveyorBelt)      return Color.web("#B59A3E");
        if(cell instanceof ContaminationSock) return Color.web("#B53A3A");
        return Color.web("#2E4A6A");
    }

}
