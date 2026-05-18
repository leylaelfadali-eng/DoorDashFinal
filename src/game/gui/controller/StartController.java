package game.gui.controller;

import game.engine.Game;
import game.engine.Role;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class StartController {

    // ── Mode-selection panel ──────────────────────────────────────
    @FXML private Label  chooseModeLabel;
    @FXML private Button spButton;
    @FXML private Button mpButton;

    // ── SP role-selection panel (hidden initially) ────────────────
    @FXML private Label  chooseRoleLabel;
    @FXML private Button scarerButton;
    @FXML private Button laugherButton;
    @FXML private Button backToModeBtn;

    // ── Multiplayer temp storage ──────────────────────────────────
    private String mp_p1Name;
    private Role   mp_p1Role;

    // ═══════════════════════════════════════════════════════════════
    //  MODE SELECTION
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void handleSinglePlayer(ActionEvent event) {
        // Hide mode panel, reveal SP role panel
        setVisible(chooseModeLabel, false);
        setVisible(spButton, false);
        setVisible(mpButton, false);

        setVisible(chooseRoleLabel, true);
        setVisible(scarerButton, true);
        setVisible(laugherButton, true);
        setVisible(backToModeBtn, true);
    }

    @FXML
    private void handleBackToMode(ActionEvent event) {
        // Reverse: show mode panel, hide SP role panel
        setVisible(chooseModeLabel, true);
        setVisible(spButton, true);
        setVisible(mpButton, true);

        setVisible(chooseRoleLabel, false);
        setVisible(scarerButton, false);
        setVisible(laugherButton, false);
        setVisible(backToModeBtn, false);
    }

    @FXML
    private void handleMultiplayer(ActionEvent event) {
        showP1Setup();
    }

    // ═══════════════════════════════════════════════════════════════
    //  SINGLE-PLAYER ROLE SELECTION
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void handleScarer(ActionEvent event) { showNameInput(Role.SCARER); }

    @FXML
    private void handleLaugher(ActionEvent event) { showNameInput(Role.LAUGHER); }

    private void showNameInput(Role role) {
        Stage nameStage = buildNamePopup("Enter Your Name",
                "You chose: " + role.name(), role == Role.SCARER ? "#2E4A7A" : "#7A4A1E");

        VBox root     = (VBox) nameStage.getScene().getRoot();
        TextField tf  = (TextField) root.lookup("#nameField");
        Button    btn = (Button)    root.lookup("#startBtn");

        btn.setOnAction(e -> doStartSP(role, tf, nameStage));
        tf.setOnAction(e  -> doStartSP(role, tf, nameStage));

        nameStage.show();
        tf.requestFocus();
        fadeIn(root);
    }

    private void doStartSP(Role role, TextField tf, Stage nameStage) {
        String name = tf.getText().trim();
        if (name.isEmpty()) name = (role == Role.SCARER) ? "Scarer" : "Laugher";
        nameStage.close();
        try { loadGameSP(role, name); } catch (Exception ex) { ex.printStackTrace(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MULTIPLAYER SETUP  (P1 → P2 → load game)
    // ═══════════════════════════════════════════════════════════════

    private void showP1Setup() {
        Stage s = buildRolePickPopup("Player 1 — Name & Role", "PLAYER 1", "#2E4A7A");
        VBox  root = (VBox) s.getScene().getRoot();
        TextField tf = (TextField) root.lookup("#nameField");
        Button scarer  = (Button) root.lookup("#scarerBtn");
        Button laugher = (Button) root.lookup("#laugherBtn");

        scarer.setOnAction(e  -> confirmMPPlayer(s, tf, Role.SCARER,  1));
        laugher.setOnAction(e -> confirmMPPlayer(s, tf, Role.LAUGHER, 1));

        s.show(); tf.requestFocus(); fadeIn(root);
    }

    /** P2 name-only popup. Role is automatically the opposite of P1's choice. */
    private void showP2Setup() {
        Role p2Role = (mp_p1Role == Role.SCARER) ? Role.LAUGHER : Role.SCARER;
        String roleColor = (p2Role == Role.SCARER) ? "#2E4A7A" : "#7A4A1E";

        Stage s = buildNamePopup("Player 2 — Enter Name",
                "Role: " + p2Role.name() + "  (automatically assigned)", roleColor);

        VBox  root = (VBox)  s.getScene().getRoot();
        TextField tf  = (TextField) root.lookup("#nameField");
        Button    btn = (Button)    root.lookup("#startBtn");

        btn.setOnAction(e -> {
            String name = tf.getText().trim();
            if (name.isEmpty()) name = "Player 2";
            s.close();
            try { loadGameMP(mp_p1Name, mp_p1Role, name, p2Role); }
            catch (Exception ex) { ex.printStackTrace(); }
        });
        tf.setOnAction(e -> btn.fire());

        s.show(); tf.requestFocus(); fadeIn(root);
    }

    private void confirmMPPlayer(Stage stage, TextField tf, Role role, int playerNum) {
        String name = tf.getText().trim();
        if (name.isEmpty()) name = (playerNum == 1) ? "Player 1" : "Player 2";
        stage.close();
        if (playerNum == 1) {
            mp_p1Name = name; mp_p1Role = role;
            showP2Setup();
        } else {
            try { loadGameMP(mp_p1Name, mp_p1Role, name, role); }
            catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME LOADING
    // ═══════════════════════════════════════════════════════════════

    private void loadGameSP(Role role, String playerName) throws Exception {
        Game game = new Game(role);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
        Parent root = loader.load();
        GameController ctrl = loader.getController();
        ctrl.initGame(game, playerName);
        switchScene(root);
    }

    private void loadGameMP(String p1Name, Role p1Role, String p2Name, Role p2Role) throws Exception {
        Game game = new Game(p1Role);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
        Parent root = loader.load();
        GameController ctrl = loader.getController();
        ctrl.initGameMultiplayer(game, p1Name, p2Name);
        switchScene(root);
    }

    private void switchScene(Parent root) {
        Stage stage = (Stage) spButton.getScene().getWindow();
        stage.setScene(new Scene(root, 1280, 720));
        stage.setFullScreenExitHint("");
        stage.setFullScreen(true);      // set BEFORE show() so there is no windowed flash
        stage.show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  POPUP BUILDERS
    // ═══════════════════════════════════════════════════════════════

    /** Simple name-only popup for single-player (existing behaviour). */
    private Stage buildNamePopup(String title, String subtitle, String roleColor) {
        Stage s = new Stage();
        s.setTitle(title);
        s.initModality(Modality.APPLICATION_MODAL);
        s.initOwner(spButton.getScene().getWindow());
        s.setResizable(false);

        Label titleLbl = new Label("ENTER YOUR NAME");
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLbl.setStyle("-fx-text-fill: #FFD700;");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-text-fill: " + roleColor + "; -fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-color: rgba(255,215,0,0.08); -fx-padding: 4 12; -fx-background-radius: 6;");

        TextField tf = buildTextField();
        tf.setId("nameField");

        Button btn = buildActionBtn("START GAME", "#2E4A7A");
        btn.setId("startBtn");

        VBox root = buildPopupRoot();
        root.getChildren().addAll(titleLbl, subLbl, tf, btn);
        s.setScene(new Scene(root, 340, 250));
        return s;
    }

    /** Name + role-pick popup for multiplayer setup. */
    private Stage buildRolePickPopup(String title, String playerLabel, String accentColor) {
        Stage s = new Stage();
        s.setTitle(title);
        s.initModality(Modality.APPLICATION_MODAL);
        s.initOwner(spButton.getScene().getWindow());
        s.setResizable(false);

        Label titleLbl = new Label(playerLabel + " SETUP");
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLbl.setStyle("-fx-text-fill: #FFD700;");

        Label nameLbl = new Label("Enter your name:");
        nameLbl.setStyle("-fx-text-fill: #CCCCEE; -fx-font-size: 13px;");

        TextField tf = buildTextField();
        tf.setId("nameField");

        Label roleLbl = new Label("Choose your role:");
        roleLbl.setStyle("-fx-text-fill: #CCCCEE; -fx-font-size: 13px;");

        Button scarerBtn = buildActionBtn("⚡  SCARER", "#2E4A7A");
        scarerBtn.setId("scarerBtn");
        scarerBtn.setPrefWidth(145);

        Button laugherBtn = buildActionBtn("😄  LAUGHER", "#7A4A1E");
        laugherBtn.setId("laugherBtn");
        laugherBtn.setPrefWidth(145);

        HBox roleRow = new HBox(12, scarerBtn, laugherBtn);
        roleRow.setAlignment(Pos.CENTER);

        VBox root = buildPopupRoot();
        root.getChildren().addAll(titleLbl, nameLbl, tf, roleLbl, roleRow);
        s.setScene(new Scene(root, 360, 300));
        return s;
    }

    private VBox buildPopupRoot() {
        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30, 35, 30, 35));
        root.setStyle("-fx-background-color: #1E3248;");
        root.setOpacity(0);
        return root;
    }

    private TextField buildTextField() {
        TextField tf = new TextField();
        tf.setPromptText("Your name...");
        tf.setMaxWidth(240);
        tf.setStyle("-fx-background-color: #142236; -fx-text-fill: #FFD700;" +
                "-fx-font-size: 14px; -fx-prompt-text-fill: #5A6A7A;" +
                "-fx-border-color: #FFD700; -fx-border-width: 1;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12;");
        return tf;
    }

    private Button buildActionBtn(String text, String color) {
        Button btn = new Button(text);
        btn.setPrefWidth(200); btn.setPrefHeight(40);
        String base = "-fx-background-color: " + color + "; -fx-text-fill: #FFD700;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 8; -fx-cursor: hand;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-opacity: 0.85;"));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    private void fadeIn(VBox root) {
        FadeTransition ft = new FadeTransition(Duration.millis(280), root);
        ft.setToValue(1); ft.play();
    }

    private void setVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible); node.setManaged(visible);
    }

    // ═══════════════════════════════════════════════════════════════
    //  INSTRUCTIONS
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void handleInstructions(ActionEvent event) { showFancyInstructions(); }

    private void showFancyInstructions() {
        Stage instrStage = new Stage();
        instrStage.setTitle("Game Instructions - DoorDash");

        BorderPane mainPane = new BorderPane();
        mainPane.setStyle("-fx-background-color: #1E3248;");

        VBox titleBox = new VBox(8); titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(18,20,12,20)); titleBox.setStyle("-fx-background-color:#0D1B2A;");
        Label titleLbl = new Label("DOORDASH — GAME GUIDE");
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLbl.setStyle("-fx-text-fill: #FFD700;");
        Separator ts = new Separator(); ts.setStyle("-fx-background-color:#FFD700;");
        titleBox.getChildren().addAll(titleLbl, ts);

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle("-fx-background:#1E3248;-fx-background-color:#1E3248;"); scroll.setFitToWidth(true);

        VBox body = new VBox(12); body.setPadding(new Insets(18)); body.setStyle("-fx-background-color:#1E3248;");
        body.getChildren().addAll(
                makeSection("WINNING",       "Land EXACTLY on cell 100 (Boo's Door) AND have 1000+ energy to win!"),
                makeDivider(),
                makeSection("TURN ORDER",    "1) Powerup optional (costs 500 energy)\n2) Click the dice to roll\n3) Move monster\n4) Cell effect activates"),
                makeDivider(),
                makeSection("MULTIPLAYER",   "Each player has their own dice and powerup button.\nOnly the active player's controls are clickable.\nW / E / L cheats apply to whoever's turn it is."),
                makeDivider(),
                makeSection("DOOR CELLS",    "Same role = gain energy. Opposite = lose energy. One-time use."),
                makeDivider(),
                makeSection("CARD CELLS",    "Draw a random card. Watch the flip animation!"),
                makeDivider(),
                makeSection("CONVEYORS",     "Move you FORWARD automatically."),
                makeDivider(),
                makeSection("CONTAM. SOCK",  "Move BACKWARD and lose 100 energy!"),
                makeDivider(),
                makeSection("MONSTERS",      "DASHER: 2x speed\nDYNAMO: doubles all energy changes\nMULTITASKER: slower but +energy\nSCHEMER: +10 bonus to all changes"),
                makeDivider(),
                makeSection("CHEATS",        "W = current player wins (jumps to cell 100)\nE = current player +300 energy\nL = current player's opponent wins")
        );
        scroll.setContent(body);

        Button closeBtn = new Button("GOT IT!");
        closeBtn.setStyle("-fx-background-color:#FFD700;-fx-text-fill:#1E3248;-fx-font-size:14px;" +
                "-fx-font-weight:bold;-fx-padding:10 30;-fx-background-radius:20;-fx-cursor:hand;");
        closeBtn.setOnAction(e -> instrStage.close());
        HBox footer = new HBox(closeBtn); footer.setAlignment(Pos.CENTER); footer.setPadding(new Insets(12));
        footer.setStyle("-fx-background-color:#0D1B2A;");

        mainPane.setTop(titleBox); mainPane.setCenter(scroll); mainPane.setBottom(footer);

        mainPane.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), mainPane); ft.setToValue(1); ft.play();

        instrStage.setScene(new Scene(mainPane, 680, 600)); instrStage.show();
    }

    private VBox makeSection(String title, String content) {
        Label t = new Label(title); t.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        t.setStyle("-fx-text-fill:#FFD700;");
        Label b = new Label(content); b.setWrapText(true); b.setStyle("-fx-text-fill:#CCCCEE;-fx-font-size:13px;");
        VBox box = new VBox(5, t, b); box.setPadding(new Insets(10,14,10,14));
        box.setStyle("-fx-background-color:rgba(46,74,122,0.2);-fx-background-radius:8;" +
                "-fx-border-color:#2E4A7A;-fx-border-radius:8;-fx-border-width:1;");
        return box;
    }

    private Separator makeDivider() {
        Separator s = new Separator(); s.setStyle("-fx-background-color:#FFD700;"); s.setMaxWidth(500);
        return s;
    }
}
