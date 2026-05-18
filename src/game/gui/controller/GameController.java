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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameController {

    // ── FXML ─────────────────────────────────────────────────────
    @FXML private VBox      playerPanel;
    @FXML private VBox      opponentPanel;
    @FXML private StackPane boardContainer;
    @FXML private Label     turnIndicator;
    @FXML private Label     statusLabel;
    @FXML private Label     eventLabel;
    @FXML private Button    powerupButton;   // bottom bar — SP only
    @FXML private Button    menuButton;
    @FXML private HBox      bottomBar;

    // ── Programmatic refs built inside panels ─────────────────────
    // SP:  diceImageView = left(P1)    cardBackInPanel = right(AI)
    // MP:  diceImageView = left(P1)    diceImageView2  = right(P2)
    //      cardBackInPanel = left(P1)  cardBackInPanel2 = right(P2)
    //      panelPowerup1  = left       panelPowerup2   = right
    private ImageView diceImageView;
    private ImageView diceImageView2;
    private ImageView cardBackInPanel;
    private ImageView cardBackInPanel2;
    private Button    panelPowerup1;
    private Button    panelPowerup2;
    private int       lastDiceFace  = 1;
    private int       lastDiceFace2 = 1;

    // ── State ─────────────────────────────────────────────────────
    private Game    game;
    private String  playerName  = "Player";
    private String  player2Name = "Player 2";
    private boolean isMultiplayer = false;
    private boolean gameOver      = false;
    /** True while an animation is running — blocks all dice clicks. */
    private boolean animating     = false;
    private GridPane boardGrid;
    private Stage    gameStage;
    private final Map<Integer, String> monsterCellImages = new HashMap<>();

    // ── Board geometry ────────────────────────────────────────────
    private static final double CELL_SIZE = 65;
    private static final double CELL_GAP  = 4;

    // ── Palette ───────────────────────────────────────────────────
    private static final String PLAYER_COLOR   = "#2E4A7A";
    private static final String OPPONENT_COLOR = "#7A4A1E";
    private static final String GOLD           = "#FFD700";
    private static final String BG             = "#1E3248";
    private static final String TEXT_LIGHT     = "#CCCCEE";

    // ═══════════════════════════════════════════════════════════════
    //  INIT — single player
    // ═══════════════════════════════════════════════════════════════

    public void initGame(Game game, String playerName) {
        this.game         = game;
        this.playerName   = sanitize(playerName, "Player");
        this.isMultiplayer = false;
        this.gameOver     = false;
        this.animating    = false;
        commonInit();
    }

    // ═══════════════════════════════════════════════════════════════
    //  INIT — multiplayer
    // ═══════════════════════════════════════════════════════════════

    public void initGameMultiplayer(Game game, String p1Name, String p2Name) {
        this.game         = game;
        this.playerName   = sanitize(p1Name, "Player 1");
        this.player2Name  = sanitize(p2Name, "Player 2");
        this.isMultiplayer = true;
        this.gameOver     = false;
        this.animating    = false;
        commonInit();
    }

    private void commonInit() {
        buildBoard();
        refreshPanels();
        wireButtons();
        updateTurnIndicator();

        javafx.application.Platform.runLater(() -> {
            if (boardContainer.getScene() != null) {
                gameStage = (Stage) boardContainer.getScene().getWindow();
                // Fullscreen is already set by StartController.switchScene on first launch.
                // On restart (handleRestart), ensure fullscreen is maintained.
                if (!gameStage.isFullScreen()) {
                    gameStage.setFullScreenExitHint("");
                    gameStage.setFullScreen(true);
                }
                setupCheatKeys();
            }
        });
        updateEvent("Game on! " + playerName + (isMultiplayer ? " vs " + player2Name : " vs OPPONENT")
                + " — click the dice to roll!");
    }

    private String sanitize(String name, String fallback) {
        return (name == null || name.trim().isEmpty()) ? fallback : name.trim();
    }

    // ── Button wiring ─────────────────────────────────────────────

    private void wireButtons() {
        // SP bottom-bar powerup
        if (powerupButton != null) {
            if (isMultiplayer) {
                powerupButton.setVisible(false);
                powerupButton.setManaged(false);
            } else {
                powerupButton.setOnAction(e -> handlePowerup());
                powerupButton.setOnMouseEntered(e -> { if (!powerupButton.isDisabled()) powerupButton.setStyle(btnSmStyle("#9A6A2E")); });
                powerupButton.setOnMouseExited(e  -> updateBottomPowerup());
            }
        }
        // Menu
        if (menuButton != null) {
            menuButton.setOnAction(e -> showMenuPopup());
            menuButton.setOnMouseEntered(e -> menuButton.setStyle(
                    "-fx-background-color:#2E4A7A;-fx-text-fill:"+GOLD+";-fx-font-size:12px;" +
                            "-fx-font-weight:bold;-fx-background-radius:8;" +
                            "-fx-border-color:#2E4A7A;-fx-border-width:1;-fx-border-radius:8;-fx-cursor:hand;"));
            menuButton.setOnMouseExited(e -> menuButton.setStyle(
                    "-fx-background-color:#1A2E4A;-fx-text-fill:"+GOLD+";-fx-font-size:12px;" +
                            "-fx-font-weight:bold;-fx-background-radius:8;" +
                            "-fx-border-color:#2E4A7A;-fx-border-width:1;-fx-border-radius:8;-fx-cursor:hand;"));
        }
        // MP: reshape bottom bar to just the centered menu button
        if (isMultiplayer && bottomBar != null) {
            bottomBar.getChildren().clear();
            bottomBar.setAlignment(Pos.CENTER);
            if (menuButton != null) bottomBar.getChildren().add(menuButton);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHEAT KEYS — apply to current player in BOTH modes
    // ═══════════════════════════════════════════════════════════════

    private void setupCheatKeys() {
        Scene sc = boardContainer.getScene();
        if (sc == null) return;
        sc.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) e.consume();
        });
        sc.setOnKeyPressed(e -> {
            if (gameOver) return;
            Monster cur = game.getCurrent();
            Monster opp = (cur == game.getPlayer()) ? game.getOpponent() : game.getPlayer();
            if (e.getCode() == KeyCode.W) {
                cur.setPosition(99); cur.setEnergy(1500);
                refreshBoard(); refreshPanels();
                updateEvent("[CHEAT W] " + currentName() + " at cell 100!");
                Monster w = getUIWinner(); if (w != null) showEndScreen(w);
            } else if (e.getCode() == KeyCode.E) {
                cur.setEnergy(cur.getEnergy() + 300);
                refreshPanels(); updateAllPowerupButtons();
                updateEvent("[CHEAT E] " + currentName() + " +300 energy");
            } else if (e.getCode() == KeyCode.L) {
                opp.setPosition(99); opp.setEnergy(1500);
                refreshBoard(); refreshPanels();
                updateEvent("[CHEAT L] " + opponentName() + " at cell 100!");
                Monster w = getUIWinner(); if (w != null) showEndScreen(w);
            }
        });
    }

    private String currentName() {
        return (game.getCurrent() == game.getPlayer()) ? playerName : player2Name;
    }
    private String opponentName() {
        return (game.getCurrent() == game.getPlayer()) ? player2Name : playerName;
    }

    // ═══════════════════════════════════════════════════════════════
    //  WIN DETECTION
    // ═══════════════════════════════════════════════════════════════

    private Monster getUIWinner() {
        if (game.getPlayer().getPosition()   == 99 && game.getPlayer().getEnergy()   >= 1000) return game.getPlayer();
        if (game.getOpponent().getPosition() == 99 && game.getOpponent().getEnergy() >= 1000) return game.getOpponent();
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME ACTIONS
    // ═══════════════════════════════════════════════════════════════

    /** Unified roll handler — works for both P1 and P2 dice in SP and MP. */
    private void handleRollDice() {
        if (gameOver || animating) return;
        setAnimating(true);

        boolean currentFrozen = game.getCurrent().isFrozen();
        int     oldPos        = game.getCurrent().getPosition();
        int     enBefore      = game.getCurrent().getEnergy();
        Card    cardBefore    = Board.getLastDrawnCard();

        try {
            game.playTurn();

            if (currentFrozen) {
                updateStatus(currentName() + " is FROZEN! Turn skipped.");
                updateEvent(currentName() + " frozen — skipped.");
                refreshBoard(); refreshPanels();
                setAnimating(false);
                afterTurn(); return;
            }

            int  roll      = game.getLastRoll();
            int  newPos    = game.getCurrent() == game.getPlayer()
                    ? game.getOpponent().getPosition()   // just switched — read correctly
                    : game.getPlayer().getPosition();
            // Actually getCurrent() already switched after playTurn, so read previous player's new pos
            // We track via oldPos + stored positions after playTurn
            int prevPlayerPos = (game.getCurrent() == game.getOpponent())
                    ? game.getPlayer().getPosition()
                    : game.getOpponent().getPosition();

            Card cardAfter  = Board.getLastDrawnCard();
            boolean drewCard = (cardAfter != cardBefore);

            // Build log with the player who JUST moved (was current before playTurn switched)
            String moverName = (game.getCurrent() == game.getOpponent()) ? playerName : player2Name;
            String logMsg = moverName + " rolled " + roll + " | cell " + (oldPos+1) + " → " + (prevPlayerPos+1);
            if (game.getCurrent() == game.getOpponent()) {
                int diff = game.getPlayer().getEnergy() - enBefore;
                if (diff != 0) logMsg += " | energy " + (diff > 0 ? "+" : "") + diff;
            } else {
                int diff = game.getOpponent().getEnergy() - enBefore;
                if (diff != 0) logMsg += " | energy " + (diff > 0 ? "+" : "") + diff;
            }
            final String log = logMsg;

            // Animate dice for the player who just rolled
            boolean movedP1 = (game.getCurrent() == game.getOpponent());
            animateDiceFor(roll, movedP1, () -> {
                updateEvent(log);
                refreshBoard();
                if (drewCard && cardAfter != null) {
                    updateStatus(moverName + " drew: " + cardAfter.getName() + "!");
                    playCardAnimation(cardAfter, movedP1, () -> {
                        refreshPanels();
                        setAnimating(false);
                        afterTurn();
                    });
                } else {
                    refreshPanels();
                    setAnimating(false);
                    afterTurn();
                }
            });

        } catch (InvalidMoveException ex) {
            int roll = game.getLastRoll();
            updateStatus("BLOCKED! Cannot land on opponent. Roll again.");
            updateEvent(currentName() + " rolled " + roll + " — blocked!");
            boolean movedP1 = (game.getCurrent() == game.getPlayer()); // turn didn't switch on block
            animateDiceFor(roll, movedP1, () -> {
                refreshBoard(); refreshPanels();
                setAnimating(false);
                refreshDiceState();
            });
        }
    }

    /** After any turn (SP or MP). */
    private void afterTurn() {
        Monster winner = getUIWinner();
        if (winner != null) { showEndScreen(winner); return; }
        updateTurnIndicator();
        updateStatus("Click the dice to roll!");
        if (isMultiplayer) {
            refreshDiceState();
            updateAllPowerupButtons();
        } else {
            // SP: trigger AI
            PauseTransition delay = new PauseTransition(Duration.millis(1200));
            delay.setOnFinished(e -> aiTurn());
            delay.play();
        }
    }

    // ── Single-player AI ──────────────────────────────────────────

    private void aiTurn() {
        if (gameOver) return;
        boolean aiFrozen   = game.getOpponent().isFrozen();
        int     oldPos     = game.getOpponent().getPosition();
        int     enBefore   = game.getOpponent().getEnergy();
        Card    cardBefore = Board.getLastDrawnCard();

        try {
            game.playTurn();

            if (aiFrozen) {
                updateEvent("OPPONENT frozen — skipped.");
                refreshBoard(); refreshPanels();
                afterAI(); return;
            }

            int  roll      = game.getLastRoll();
            int  newPos    = game.getOpponent().getPosition();
            Card cardAfter = Board.getLastDrawnCard();
            boolean drewCard = (cardAfter != cardBefore);

            // Show roll on P1's dice (SP only, no P2 dice)
            Image aiDice = loadImage("/images/dice " + roll + ".png");
            if (aiDice != null && diceImageView != null) diceImageView.setImage(aiDice);
            lastDiceFace = roll;

            String logMsg = "OPPONENT rolled " + roll + " | cell " + (oldPos+1) + " → " + (newPos+1);
            int diff = game.getOpponent().getEnergy() - enBefore;
            if (diff != 0) logMsg += " | energy " + (diff > 0 ? "+" : "") + diff;
            updateEvent(logMsg);
            refreshBoard();

            if (drewCard && cardAfter != null) {
                updateStatus("OPPONENT drew: " + cardAfter.getName() + "!");
                playCardAnimation(cardAfter, false, () -> { refreshPanels(); afterAI(); });
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
        refreshDiceState();
        updateBottomPowerup();
    }

    // ── Powerup ───────────────────────────────────────────────────

    private void handlePowerup() {
        if (gameOver || animating) return;
        if (game.getCurrent() != game.getPlayer() && !isMultiplayer) {
            updateStatus("Not your turn!"); return;
        }
        int enBefore = game.getCurrent().getEnergy();
        try {
            game.usePowerup();
            int cost = enBefore - game.getCurrent().getEnergy();
            updateStatus("POWERUP activated! -" + cost + " energy.");
            updateEvent(currentName() + " used POWERUP: " + getPowerupDesc(game.getCurrent()));
            animatePowerup(); refreshPanels(); updateAllPowerupButtons();
        } catch (OutOfEnergyException e) {
            updateStatus("Not enough energy! Need 500, have " + game.getCurrent().getEnergy() + ".");
            updateEvent("Powerup failed — need 500 energy.");
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
    //  BOARD
    // ═══════════════════════════════════════════════════════════════

    private void buildBoard() {
        computeMonsterCellImages();
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

    private int linearIndex(int r, int c) { return r * 10 + ((r % 2 == 0) ? c : (9 - c)); }

    private StackPane createCellPane(Cell cell, int index) {
        StackPane pane = new StackPane();
        pane.setPrefSize(CELL_SIZE, CELL_SIZE); pane.setMaxSize(CELL_SIZE, CELL_SIZE);

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
            DoorCell d = (DoorCell) cell;
            Label el = d.isActivated() ? miniLabel("USED","#FF4444")
                    : miniLabel((d.getEnergy()>0?"+":"")+d.getEnergy(), GOLD);
            StackPane.setAlignment(el, Pos.BOTTOM_RIGHT); pane.getChildren().add(el);
        }
        if (cell instanceof MonsterCell) {
            MonsterCell mc = (MonsterCell) cell;
            String n = mc.getCellMonster().getName(); if (n.length()>5) n=n.substring(0,4)+".";
            Label ml = miniLabel(n,"#00FF99"); StackPane.setAlignment(ml,Pos.BOTTOM_LEFT); pane.getChildren().add(ml);
        }

        int pp=game.getPlayer().getPosition(), op=game.getOpponent().getPosition();
        if (index==pp) { StackPane t=makeToken("P",PLAYER_COLOR);   if(index==op) t.setTranslateX(-9); pane.getChildren().add(t); }
        if (index==op) {
            String opLabel = isMultiplayer ? "P2" : "O";
            StackPane t=makeToken(opLabel,OPPONENT_COLOR); if(index==pp) t.setTranslateX(9); pane.getChildren().add(t);
        }
        if (index==99) pane.setStyle("-fx-effect:dropshadow(gaussian,#FFD700,12,0.7,0,0);");
        return pane;
    }

    private Label miniLabel(String t, String c) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill:"+c+";-fx-font-size:7px;-fx-background-color:rgba(0,0,0,0.6);-fx-padding:1 2;");
        return l;
    }
    private StackPane makeToken(String letter, String color) {
        Circle c=new Circle(12); c.setFill(Color.web(color)); c.setStroke(Color.web(GOLD)); c.setStrokeWidth(2);
        Label l=new Label(letter); l.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:9px;-fx-font-weight:bold;");
        StackPane sp=new StackPane(c,l); sp.setAlignment(Pos.CENTER); return sp;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANELS — SP vs MP layouts
    // ═══════════════════════════════════════════════════════════════

    private void refreshPanels() {
        buildPanel(playerPanel,   game.getPlayer(),   playerName,  PLAYER_COLOR,   true);
        buildPanel(opponentPanel, game.getOpponent(), isMultiplayer ? player2Name : "OPPONENT",
                OPPONENT_COLOR, false);
        updateAllPowerupButtons();
        refreshDiceState();
    }

    private void buildPanel(VBox panel, Monster monster, String title, String color, boolean isP1) {
        panel.getChildren().clear();
        panel.setAlignment(Pos.TOP_CENTER); panel.setSpacing(5);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:15px;-fx-font-weight:bold;" +
                "-fx-background-color:"+color+";-fx-padding:5 14;-fx-background-radius:6;");

        ImageView avatar=new ImageView(); avatar.setFitWidth(140); avatar.setFitHeight(140); avatar.setPreserveRatio(true); avatar.setSmooth(true);
        try { InputStream s=getClass().getResourceAsStream(getMonsterNobgPath(monster)); if(s!=null){Image img=new Image(s); if(!img.isError()) avatar.setImage(img);}} catch(Exception ignored){}

        StackPane avatarStack=new StackPane(avatar);
        String sp=getStatusIconPath(monster);
        if(sp!=null){ Image si=loadImage(sp); if(si!=null){ ImageView sv=new ImageView(si); sv.setFitWidth(48); sv.setFitHeight(48); sv.setPreserveRatio(true); StackPane.setAlignment(sv,Pos.BOTTOM_RIGHT); avatarStack.getChildren().add(sv);}}

        Label nameLbl=new Label(monster.getName());   nameLbl.setStyle("-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:bold;"); nameLbl.setWrapText(true);
        Label typeLbl=new Label("Type: "+monster.getClass().getSimpleName()); typeLbl.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:14px;");
        Label roleLbl=new Label("Role: "+monster.getRole()); roleLbl.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:14px;");

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

        if (isMultiplayer) {
            // Both panels: dice → powerup → card back
            addDiceToPanel(panel, isP1);
            addPanelPowerupButton(panel, isP1);
            addCardBackToPanel(panel, isP1);
        } else {
            // SP: P1 gets dice, opponent gets card back
            if (isP1) addDiceToPanel(panel, true);
            else addCardBackToPanel(panel, false);
        }
    }

    /** Add clickable dice to a panel. isP1=true → stores diceImageView, false → diceImageView2. */
    private void addDiceToPanel(VBox panel, boolean isP1) {
        ImageView iv=new ImageView();
        iv.setFitWidth(88); iv.setFitHeight(88); iv.setPreserveRatio(true); iv.setSmooth(true);
        int face = isP1 ? lastDiceFace : lastDiceFace2;
        Image img=loadImage("/images/dice "+face+".png"); if(img!=null) iv.setImage(img);
        Rectangle clip=new Rectangle(88,88); clip.setArcWidth(18); clip.setArcHeight(18); iv.setClip(clip);
        iv.setStyle("-fx-cursor:hand;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),10,0.4,0,2);");
        iv.setOnMouseClicked(e -> handleRollDice());
        iv.setOnMouseEntered(e -> { ScaleTransition st=new ScaleTransition(Duration.millis(150),iv); st.setToX(1.1); st.setToY(1.1); st.play(); });
        iv.setOnMouseExited(e  -> { ScaleTransition st=new ScaleTransition(Duration.millis(150),iv); st.setToX(1.0); st.setToY(1.0); st.play(); });
        VBox.setMargin(iv, new Insets(10, 0, 0, 0));

        if (isP1) diceImageView  = iv;
        else      diceImageView2 = iv;

        Label hint=new Label("CLICK DICE TO ROLL"); hint.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:9px;");
        panel.getChildren().addAll(iv, hint);
    }

    /** Add powerup button inside a panel (MP mode). */
    private void addPanelPowerupButton(VBox panel, boolean isP1) {
        Button btn = new Button("USE POWERUP (500)");
        btn.setPrefWidth(190); btn.setPrefHeight(34);
        String base = btnSmStyle(OPPONENT_COLOR);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> { if(!btn.isDisabled()) btn.setStyle(btnSmStyle("#9A6A2E")); });
        btn.setOnMouseExited(e  -> btn.setStyle(isPowerupApplicable(isP1) ? base : btnSmStyle("#3A2A1A")));
        btn.setOnAction(e -> handlePowerup());
        VBox.setMargin(btn, new Insets(4, 0, 0, 0));
        if (isP1) panelPowerup1 = btn;
        else      panelPowerup2 = btn;
        panel.getChildren().add(btn);
    }

    /** Add card-back image to a panel. */
    private void addCardBackToPanel(VBox panel, boolean isP1) {
        ImageView iv=new ImageView();
        iv.setFitWidth(isMultiplayer ? 90 : 115);
        iv.setFitHeight(isMultiplayer ? 128 : 162);
        iv.setPreserveRatio(false); iv.setSmooth(true);
        Image img=loadImage("/images/backcard.png"); if(img!=null) iv.setImage(img);
        VBox.setMargin(iv, new Insets(isMultiplayer ? 6 : 14, 0, 0, 0));

        // In MP: P1→cardBackInPanel (left), P2→cardBackInPanel2 (right)
        // In SP: AI panel → cardBackInPanel (right)
        if (isMultiplayer) {
            if (isP1) cardBackInPanel  = iv;
            else      cardBackInPanel2 = iv;
        } else {
            cardBackInPanel = iv;
        }

        Label hint=new Label("CARD DECK"); hint.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:9px;");
        panel.getChildren().addAll(iv, hint);
    }

    // ═══════════════════════════════════════════════════════════════
    //  DICE / POWERUP STATE CONTROL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Refresh which dice is enabled.
     * SP:  P1 dice enabled when it's P1's turn and not animating.
     * MP:  P1 dice enabled during P1's turn, P2 during P2's turn, none during animation.
     */
    private void refreshDiceState() {
        boolean p1Turn = (game.getCurrent() == game.getPlayer());
        setDiceState(diceImageView,  !animating && p1Turn);
        setDiceState(diceImageView2, !animating && isMultiplayer && !p1Turn);
    }

    private void setDiceState(ImageView iv, boolean enabled) {
        if (iv == null) return;
        iv.setMouseTransparent(!enabled);
        iv.setOpacity(enabled ? 1.0 : 0.45);
    }

    private void setAnimating(boolean v) {
        animating = v;
        refreshDiceState();
    }

    private void updateBottomPowerup() {
        if (powerupButton == null || isMultiplayer) return;
        boolean can = !gameOver && !animating && (game.getCurrent()==game.getPlayer()) && (game.getPlayer().getEnergy()>=500);
        powerupButton.setStyle(can ? btnSmStyle(OPPONENT_COLOR) : btnSmStyle("#3A2A1A"));
        powerupButton.setOpacity(can ? 1.0 : 0.55);
        powerupButton.setDisable(false);
    }

    private void updateAllPowerupButtons() {
        updateBottomPowerup();
        updatePanelPowerupStyle(panelPowerup1, isPowerupApplicable(true));
        updatePanelPowerupStyle(panelPowerup2, isPowerupApplicable(false));
    }

    private boolean isPowerupApplicable(boolean forP1) {
        if (gameOver || animating) return false;
        Monster m = forP1 ? game.getPlayer() : game.getOpponent();
        Monster cur = game.getCurrent();
        return (cur == m) && (m.getEnergy() >= 500);
    }

    private void updatePanelPowerupStyle(Button btn, boolean applicable) {
        if (btn == null) return;
        btn.setStyle(applicable ? btnSmStyle(OPPONENT_COLOR) : btnSmStyle("#3A2A1A"));
        btn.setOpacity(applicable ? 1.0 : 0.55);
        btn.setDisable(false);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    /** Animate the correct player's dice (P1 or P2). */
    private void animateDiceFor(int finalValue, boolean p1Rolled, Runnable onFinished) {
        ImageView iv = p1Rolled ? diceImageView : diceImageView2;
        if (iv == null) { if (onFinished != null) onFinished.run(); return; }
        Random rand = new Random();
        ScaleTransition up=new ScaleTransition(Duration.millis(180),iv); up.setToX(1.35); up.setToY(1.35);
        up.setOnFinished(e -> {
            SequentialTransition cycle=new SequentialTransition();
            for(int i=0;i<10;i++){ final int r=rand.nextInt(6)+1; PauseTransition pt=new PauseTransition(Duration.millis(40+i*12)); pt.setOnFinished(ev->{ Image di=loadImage("/images/dice "+r+".png"); if(di!=null) iv.setImage(di); }); cycle.getChildren().add(pt); }
            PauseTransition finish=new PauseTransition(Duration.millis(120));
            finish.setOnFinished(ev->{ Image fi=loadImage("/images/dice "+finalValue+".png"); if(fi!=null) iv.setImage(fi);
                if (p1Rolled) lastDiceFace = finalValue; else lastDiceFace2 = finalValue;
                ScaleTransition down=new ScaleTransition(Duration.millis(180),iv); down.setToX(1.0); down.setToY(1.0);
                down.setOnFinished(ev2->{ if(onFinished!=null) onFinished.run(); }); down.play();
            });
            cycle.getChildren().add(finish); cycle.play();
        }); up.play();
    }

    /**
     * Card flip animation.
     * p1CardSource=true  → card travels from P1's card back (left panel).
     * p1CardSource=false → card travels from P2/AI card back (right panel or SP right).
     */
    private void playCardAnimation(Card card, boolean p1CardSource, Runnable onFinished) {
        ImageView sourceBack = p1CardSource ? cardBackInPanel : cardBackInPanel2;
        // In SP mode cardBackInPanel is always the right panel (AI side)
        if (!isMultiplayer) sourceBack = cardBackInPanel;

        double cardW=175, cardH=245, centerX=boardContainer.getWidth()/2, centerY=boardContainer.getHeight()/2;
        double startTX, startTY, startSX=0.65, startSY=0.65;
        final ImageView finalSourceBack = sourceBack;

        if(finalSourceBack!=null && boardContainer.getWidth()>0){
            try{ Bounds b=finalSourceBack.localToScene(finalSourceBack.getBoundsInLocal()); Point2D p=boardContainer.sceneToLocal(b.getCenterX(),b.getCenterY()); startTX=p.getX()-cardW/2; startTY=p.getY()-cardH/2; }
            catch(Exception ex){ startTX=(p1CardSource?10:boardContainer.getWidth()-cardW-10); startTY=centerY-cardH/2; }
        } else { startTX=(p1CardSource?10:boardContainer.getWidth()-cardW-10); startTY=centerY-cardH/2; }

        double targetTX=centerX-cardW/2, targetTY=centerY-cardH/2, dx=targetTX-startTX, dy=targetTY-startTY;

        StackPane overlay=new StackPane(); overlay.setStyle("-fx-background-color:rgba(0,0,0,0.55);"); boardContainer.getChildren().add(overlay);
        Image backImg=loadImage("/images/backcard.png"), frontImg=loadImage(getCardImagePath(card));
        ImageView fc=new ImageView(); fc.setFitWidth(cardW); fc.setFitHeight(cardH); fc.setPreserveRatio(false); fc.setSmooth(true);
        if(backImg!=null) fc.setImage(backImg); fc.setScaleX(startSX); fc.setScaleY(startSY);
        StackPane.setAlignment(fc,Pos.TOP_LEFT); fc.setTranslateX(startTX); fc.setTranslateY(startTY);
        boardContainer.getChildren().add(fc);

        Label cardLbl=new Label(card!=null?card.getName():""); cardLbl.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-background-color:rgba(0,0,0,0.75);-fx-padding:5 12;-fx-background-radius:6;"); cardLbl.setOpacity(0); cardLbl.setTranslateY(130); boardContainer.getChildren().add(cardLbl);
        if(finalSourceBack!=null) finalSourceBack.setOpacity(0);

        TranslateTransition mi=new TranslateTransition(Duration.millis(420),fc); mi.setByX(dx); mi.setByY(dy);
        ScaleTransition su=new ScaleTransition(Duration.millis(420),fc); su.setToX(1.0); su.setToY(1.0);
        ParallelTransition arrive=new ParallelTransition(mi,su);
        ScaleTransition h1=new ScaleTransition(Duration.millis(180),fc); h1.setToX(0);
        ScaleTransition h2=new ScaleTransition(Duration.millis(180),fc); h2.setFromX(0); h2.setToX(1);
        PauseTransition hold=new PauseTransition(Duration.millis(2500));
        ScaleTransition b1=new ScaleTransition(Duration.millis(180),fc); b1.setToX(0);
        ScaleTransition b2=new ScaleTransition(Duration.millis(180),fc); b2.setFromX(0); b2.setToX(1);
        TranslateTransition mo=new TranslateTransition(Duration.millis(420),fc); mo.setByX(-dx); mo.setByY(-dy);
        ScaleTransition sd=new ScaleTransition(Duration.millis(420),fc); sd.setToX(startSX); sd.setToY(startSY);
        ParallelTransition depart=new ParallelTransition(mo,sd);

        arrive.setOnFinished(e->h1.play());
        h1.setOnFinished(e->{ if(frontImg!=null) fc.setImage(frontImg); FadeTransition fn=new FadeTransition(Duration.millis(150),cardLbl); fn.setToValue(1); fn.play(); h2.play(); });
        h2.setOnFinished(e->hold.play());
        hold.setOnFinished(e->b1.play());
        b1.setOnFinished(e->{ if(backImg!=null) fc.setImage(backImg); FadeTransition fn=new FadeTransition(Duration.millis(150),cardLbl); fn.setToValue(0); fn.play(); b2.play(); });
        b2.setOnFinished(e->depart.play());
        depart.setOnFinished(e->{ boardContainer.getChildren().removeAll(fc,overlay,cardLbl); if(finalSourceBack!=null) finalSourceBack.setOpacity(1); if(onFinished!=null) onFinished.run(); });
        arrive.play();
    }

    private void animatePowerup() {
        Button pBtn = isMultiplayer ? ((game.getCurrent()==game.getPlayer()) ? panelPowerup1 : panelPowerup2)
                : powerupButton;
        if(pBtn!=null){
            ScaleTransition pop=new ScaleTransition(Duration.millis(130),pBtn); pop.setToX(1.25); pop.setToY(1.25); pop.setAutoReverse(true); pop.setCycleCount(2);
            pop.setOnFinished(e->{ pBtn.setScaleX(1); pBtn.setScaleY(1); }); pop.play();
            pBtn.setStyle(btnSmStyle(OPPONENT_COLOR)+"-fx-effect:dropshadow(gaussian,"+GOLD+",18,0.8,0,0);");
            PauseTransition sp2=new PauseTransition(Duration.millis(600)); sp2.setOnFinished(e->updateAllPowerupButtons()); sp2.play();
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
        gameOver=true; setAnimating(true);

        boolean p1Won = (winner == game.getPlayer());
        String winnerName = p1Won ? playerName : player2Name;
        String loserName  = p1Won ? player2Name : playerName;

        Stage ws=new Stage(); ws.setTitle(p1Won ? winnerName+" Wins!" : loserName+" Loses!");
        ws.initModality(Modality.APPLICATION_MODAL);
        if(gameStage!=null) ws.initOwner(gameStage);

        StackPane root=new StackPane();
        // In SP: winning bg for P1 win, losing bg for P1 loss.
        // In MP: always winning bg — from the winner's perspective, it's always a win.
        String bgPath = (!isMultiplayer && !p1Won)
                ? "/images/losing screen.png"
                : "/images/winning screen.png";
        Image bgImg = loadImage(bgPath);
        if(bgImg!=null){ ImageView bg=new ImageView(bgImg); bg.setFitWidth(640); bg.setFitHeight(480); bg.setPreserveRatio(false); root.getChildren().add(bg); }
        else { root.setStyle("-fx-background-color:"+BG+";"); }

        VBox content=new VBox(12); content.setAlignment(Pos.BOTTOM_CENTER); content.setPadding(new Insets(0,30,35,30));

        // Show winner's monster
        ImageView monsterImg=new ImageView(); monsterImg.setFitWidth(130); monsterImg.setFitHeight(130); monsterImg.setPreserveRatio(true); monsterImg.setSmooth(true);
        Image mi=loadImage(getMonsterNobgPath(winner)); if(mi!=null) monsterImg.setImage(mi);

        VBox stats=new VBox(6); stats.setAlignment(Pos.CENTER); stats.setPadding(new Insets(12,20,12,20));
        stats.setStyle("-fx-background-color:rgba(13,27,42,0.88);-fx-background-radius:10;");

        Label resultLbl = new Label(isMultiplayer ? winnerName+" WINS! 🏆" : (p1Won ? "YOU WIN! 🏆" : "YOU LOSE! 💀"));
        resultLbl.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        resultLbl.setStyle("-fx-text-fill:"+GOLD+";");

        Label stitle=new Label("FINAL STATS"); stitle.setStyle("-fx-text-fill:"+GOLD+";-fx-font-size:13px;-fx-font-weight:bold;");
        Label ps=new Label(playerName+"  |  Cell: "+(game.getPlayer().getPosition()+1)+"  |  Energy: "+game.getPlayer().getEnergy()); ps.setStyle("-fx-text-fill:#6A9AD4;-fx-font-size:13px;");
        String oppLabel = isMultiplayer ? player2Name : "OPPONENT ("+game.getOpponent().getName()+")";
        Label os=new Label(oppLabel+"  |  Cell: "+(game.getOpponent().getPosition()+1)+"  |  Energy: "+game.getOpponent().getEnergy()); os.setStyle("-fx-text-fill:#C47A3E;-fx-font-size:13px;");
        stats.getChildren().addAll(resultLbl, stitle,ps,os);

        HBox btnRow=new HBox(15); btnRow.setAlignment(Pos.CENTER);
        Button backBtn=new Button("BACK TO MENU");
        backBtn.setStyle("-fx-background-color:"+PLAYER_COLOR+";-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;");
        backBtn.setOnMouseEntered(e->backBtn.setStyle("-fx-background-color:#3A6A9A;-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;"));
        backBtn.setOnMouseExited(e ->backBtn.setStyle("-fx-background-color:"+PLAYER_COLOR+";-fx-text-fill:"+GOLD+";-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;"));
        backBtn.setOnAction(e->{ ws.close(); navigateToStart(); });
        Button quitBtn=new Button("QUIT"); quitBtn.setStyle("-fx-background-color:#4A1A1A;-fx-text-fill:#FF6B6B;-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 28;-fx-background-radius:8;-fx-cursor:hand;");
        quitBtn.setOnAction(e->System.exit(0));
        btnRow.getChildren().addAll(backBtn,quitBtn);
        content.getChildren().addAll(monsterImg,stats,btnRow);
        root.getChildren().add(content);

        ws.setScene(new Scene(root,640,480)); ws.show(); ws.requestFocus(); ws.toFront();
    }

    // ═══════════════════════════════════════════════════════════════
    //  MENU POPUP
    // ═══════════════════════════════════════════════════════════════

    private void showMenuPopup() {
        Stage menuStage=new Stage(); menuStage.setTitle("Menu");
        menuStage.initModality(Modality.APPLICATION_MODAL);
        if(gameStage!=null) menuStage.initOwner(gameStage);
        menuStage.setResizable(false);

        VBox root=new VBox(14); root.setAlignment(Pos.CENTER); root.setPadding(new Insets(28,35,28,35));
        root.setStyle("-fx-background-color:#1E3248;-fx-border-color:#2E4A7A;-fx-border-width:2;");

        Label title=new Label("GAME MENU"); title.setFont(Font.font("Arial",FontWeight.BOLD,22)); title.setStyle("-fx-text-fill:"+GOLD+";");
        Separator sep=new Separator(); sep.setStyle("-fx-background-color:#2E4A7A;"); sep.setMaxWidth(220);

        Button restartBtn=menuBtn("↺  RESTART","#1A5C3A");
        restartBtn.setOnAction(e->{ menuStage.close(); handleRestart(); });

        Button helpBtn=menuBtn("?  HOW TO PLAY",PLAYER_COLOR);
        helpBtn.setOnAction(e->showHelp(menuStage));

        Button backBtn=menuBtn("◀  BACK TO GAME","#3A3A3A");
        backBtn.setOnAction(e->menuStage.close());

        Button quitBtn=menuBtn("✕  QUIT GAME","#4A1A1A");
        quitBtn.setStyle(quitBtn.getStyle().replace(GOLD,"#FF6B6B"));
        quitBtn.setOnAction(e->System.exit(0));

        root.getChildren().addAll(title,sep,restartBtn,helpBtn,backBtn,quitBtn);
        menuStage.setScene(new Scene(root,290,340)); menuStage.show();
    }

    private Button menuBtn(String text, String color) {
        Button btn=new Button(text); btn.setPrefWidth(220); btn.setPrefHeight(36);
        String base="-fx-background-color:"+color+";-fx-text-fill:"+GOLD+";-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:8 0;-fx-background-radius:8;-fx-cursor:hand;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e->btn.setStyle(base+"-fx-opacity:0.82;"));
        btn.setOnMouseExited(e ->btn.setStyle(base));
        return btn;
    }

    private void handleRestart() {
        try {
            Game newGame = new Game(game.getPlayer().getRole());
            if (isMultiplayer) initGameMultiplayer(newGame, playerName, player2Name);
            else               initGame(newGame, playerName);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showHelp(Stage owner) {
        Stage hs=new Stage(); hs.setTitle("How to Play");
        hs.initModality(Modality.APPLICATION_MODAL);
        hs.initOwner(owner!=null?owner:gameStage);

        BorderPane mp=new BorderPane(); mp.setStyle("-fx-background-color:#1E3248;");
        VBox th=new VBox(6); th.setAlignment(Pos.CENTER); th.setPadding(new Insets(16,20,10,20)); th.setStyle("-fx-background-color:#0D1B2A;");
        Label tl=new Label("HOW TO PLAY"); tl.setFont(Font.font("Arial",FontWeight.BOLD,24)); tl.setStyle("-fx-text-fill:"+GOLD+";");
        Separator ts=new Separator(); ts.setStyle("-fx-background-color:"+GOLD+";");
        th.getChildren().addAll(tl,ts);

        ScrollPane sc=new ScrollPane(); sc.setStyle("-fx-background:#1E3248;-fx-background-color:#1E3248;"); sc.setFitToWidth(true);
        VBox body=new VBox(10); body.setPadding(new Insets(16)); body.setStyle("-fx-background-color:#1E3248;");

        String[][] sections = {
                {"WINNING",      "Land on cell 100 (Boo's Door) AND have 1000+ energy."},
                {"TURNS",        "1) Powerup optional (500 energy)\n2) Click your dice to roll\n3) Cell effect activates"},
                {"MULTIPLAYER",  "Each player has their own dice & powerup.\nOnly the active player's controls respond.\nW/E/L cheats apply to whoever's turn it is."},
                {"DOOR CELLS",   "Same role = gain energy. Opposite = lose energy. One-time use."},
                {"CARD CELLS",   "Draw a random card — effect applies immediately."},
                {"CONVEYORS",    "Move FORWARD automatically."},
                {"CONTAM. SOCK", "Move BACKWARD, lose 100 energy!"},
                {"MONSTERS",     "DASHER: 2x speed\nDYNAMO: doubles all energy changes\nMULTITASKER: slower but +energy\nSCHEMER: +10 bonus to all changes"},
                {"CHEATS",       "W = current player wins\nE = current player +300 energy\nL = opponent wins"}
        };
        for (String[] s : sections) body.getChildren().add(helpSection(s[0], s[1]));
        sc.setContent(body);

        Button close=new Button("GOT IT!"); close.setStyle("-fx-background-color:"+GOLD+";-fx-text-fill:#1E3248;-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:10 30;-fx-background-radius:20;-fx-cursor:hand;"); close.setOnAction(e->hs.close());
        HBox ft=new HBox(close); ft.setAlignment(Pos.CENTER); ft.setPadding(new Insets(12)); ft.setStyle("-fx-background-color:#0D1B2A;");
        mp.setTop(th); mp.setCenter(sc); mp.setBottom(ft);
        hs.setScene(new Scene(mp,600,520)); hs.show();
    }

    private VBox helpSection(String title, String body) {
        Label t=new Label(title); t.setFont(Font.font("Arial",FontWeight.BOLD,13)); t.setStyle("-fx-text-fill:"+GOLD+";");
        Label b=new Label(body); b.setWrapText(true); b.setStyle("-fx-text-fill:"+TEXT_LIGHT+";-fx-font-size:12px;");
        VBox box=new VBox(5,t,b); box.setPadding(new Insets(10,14,10,14));
        box.setStyle("-fx-background-color:rgba(46,74,122,0.2);-fx-background-radius:8;-fx-border-color:#2E4A7A;-fx-border-radius:8;-fx-border-width:1;");
        return box;
    }

    private void navigateToStart() {
        try {
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
        boolean p1Turn = (game.getCurrent() == game.getPlayer());
        String name = p1Turn ? playerName : (isMultiplayer ? player2Name : "OPPONENT");
        String color = p1Turn ? GOLD : "#FF6B35";
        if(turnIndicator!=null){ turnIndicator.setText(name.toUpperCase()+"'S TURN"); turnIndicator.setStyle("-fx-text-fill:"+color+";-fx-font-size:18px;-fx-font-weight:bold;"); }
        updateStatus("Click the dice to roll!");
    }

    private void updateStatus(String msg){ if(statusLabel!=null) statusLabel.setText(msg); }
    private void updateEvent(String msg) { if(eventLabel!=null)  eventLabel.setText(msg); }
    private String btnSmStyle(String c){ return "-fx-background-color:"+c+";-fx-text-fill:"+GOLD+";-fx-font-size:12px;-fx-font-weight:bold;-fx-background-radius:8;-fx-cursor:hand;"; }

    // ═══════════════════════════════════════════════════════════════
    //  MONSTER CELL IMAGE ASSIGNMENT
    // ═══════════════════════════════════════════════════════════════

    private void computeMonsterCellImages() {
        monsterCellImages.clear();
        List<String> pool = buildAvailableMonsterPool();
        Cell[][] cells = game.getBoard().getBoardCells();
        List<Integer> mcIndices = new ArrayList<>();
        for(int row=0;row<10;row++) for(int col=0;col<10;col++) if(cells[row][col] instanceof MonsterCell) mcIndices.add(linearIndex(row,col));
        for(int i=0;i<mcIndices.size()&&i<pool.size();i++) monsterCellImages.put(mcIndices.get(i),pool.get(i));
    }

    private List<String> buildAvailableMonsterPool() {
        Class<?> pc=game.getPlayer().getClass(); Role pr=game.getPlayer().getRole();
        Class<?> oc=game.getOpponent().getClass(); Role or2=game.getOpponent().getRole();
        List<String> pool=new ArrayList<>();
        if(!(Dasher.class.equals(pc)&&pr==Role.SCARER)      &&!(Dasher.class.equals(oc)&&or2==Role.SCARER))      pool.add("/images/dasher scarer.png");
        if(!(Dasher.class.equals(pc)&&pr==Role.LAUGHER)     &&!(Dasher.class.equals(oc)&&or2==Role.LAUGHER))     pool.add("/images/dahser laugher.png");
        if(!(Dynamo.class.equals(pc)&&pr==Role.SCARER)      &&!(Dynamo.class.equals(oc)&&or2==Role.SCARER))      pool.add("/images/dynamo scarer.png");
        if(!(Dynamo.class.equals(pc)&&pr==Role.LAUGHER)     &&!(Dynamo.class.equals(oc)&&or2==Role.LAUGHER))     pool.add("/images/dynamo laugher.png");
        if(!(MultiTasker.class.equals(pc)&&pr==Role.SCARER) &&!(MultiTasker.class.equals(oc)&&or2==Role.SCARER)) pool.add("/images/multitasker scarer.png");
        if(!(MultiTasker.class.equals(pc)&&pr==Role.LAUGHER)&&!(MultiTasker.class.equals(oc)&&or2==Role.LAUGHER))pool.add("/images/multitastker laugher.png");
        if(!(Schemer.class.equals(pc)&&pr==Role.SCARER)     &&!(Schemer.class.equals(oc)&&or2==Role.SCARER))     pool.add("/images/schemer1.png");
        if(!(Schemer.class.equals(pc)&&pr==Role.LAUGHER)    &&!(Schemer.class.equals(oc)&&or2==Role.LAUGHER))    pool.add("/images/schemer2.png");
        return pool;
    }

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

    private String getImagePath(Cell cell, int index){
        if(index==99) return "/images/boo's door.png";
        if(cell instanceof MonsterCell){ String a=monsterCellImages.get(index); return a!=null?a:getMonsterImagePath(((MonsterCell)cell).getCellMonster()); }
        if(cell instanceof CardCell)          return "/images/backcard.png";
        if(cell instanceof ContaminationSock) return "/images/contamination sock.png";
        if(cell instanceof ConveyorBelt)      return "/images/gopass.png";
        if(cell instanceof DoorCell){ DoorCell d=(DoorCell)cell; return d.getRole()==Role.SCARER?"/images/scarer cell.png":"/images/laugher cell.png"; }
        return "/images/normal cell.png";
    }

    private String getMonsterImagePath(Monster m){ boolean sc=m.getRole()==Role.SCARER; switch(m.getClass().getSimpleName().toLowerCase()){ case "dasher": return sc?"/images/dasher scarer.png":"/images/dahser laugher.png"; case "dynamo": return sc?"/images/dynamo scarer.png":"/images/dynamo laugher.png"; case "multitasker": return sc?"/images/multitasker scarer.png":"/images/multitastker laugher.png"; case "schemer": return sc?"/images/schemer1.png":"/images/schemer2.png"; default: return "/images/normal cell.png"; } }
    private String getMonsterNobgPath(Monster m){ boolean sc=m.getRole()==Role.SCARER; switch(m.getClass().getSimpleName().toLowerCase()){ case "dasher": return sc?"/images/dasher scarer_nobg.png":"/images/dasher laugher_nobg.png"; case "dynamo": return sc?"/images/dynamo scarer_nobg.png":"/images/dynamo laugher_nobg.png"; case "multitasker": return sc?"/images/multitasker scarer_nobg.png":"/images/multitasker laugher_nobg.png"; case "schemer": return sc?"/images/schemer1_nobg.png":"/images/schemer2_nobg.png"; default: return "/images/normal cell.png"; } }
    private String getStatusIconPath(Monster m){ if(m.isFrozen()) return "/images/icon frozen.png"; if(m.isShielded()) return "/images/icon sheild.png"; if(m.isConfused()) return "/images/icon confused.png"; return null; }

    private Color getFallbackColor(Cell cell){ if(cell instanceof DoorCell) return((DoorCell)cell).getRole()==Role.SCARER?Color.web("#2E4A7A"):Color.web("#7A4A1E"); if(cell instanceof CardCell) return Color.web("#4A2E7A"); if(cell instanceof MonsterCell) return Color.web("#1A5C3A"); if(cell instanceof ConveyorBelt) return Color.web("#5C4A1A"); if(cell instanceof ContaminationSock) return Color.web("#5C1A1A"); return Color.web("#1E3A5C"); }
    private Color getBorderColor(Cell cell){ if(cell instanceof DoorCell) return((DoorCell)cell).getRole()==Role.SCARER?Color.web("#4A7AB5"):Color.web("#B57A3E"); if(cell instanceof CardCell) return Color.web("#7A4EB5"); if(cell instanceof MonsterCell) return Color.web("#2A8C5A"); if(cell instanceof ConveyorBelt) return Color.web("#B59A3E"); if(cell instanceof ContaminationSock) return Color.web("#B53A3A"); return Color.web("#2E4A6A"); }
}
