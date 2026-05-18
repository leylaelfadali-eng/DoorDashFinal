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

    @FXML private Button scarerButton;
    @FXML private Button laugherButton;

    // ── BUTTON HANDLERS ──────────────────────────────────────────

    @FXML
    private void handleScarer(ActionEvent event) {
        showNameInput(Role.SCARER);
    }

    @FXML
    private void handleLaugher(ActionEvent event) {
        showNameInput(Role.LAUGHER);
    }

    @FXML
    private void handleInstructions(ActionEvent event) {
        showFancyInstructions();
    }

    // ── NAME INPUT POPUP ─────────────────────────────────────────

    private void showNameInput(Role role) {
        Stage nameStage = new Stage();
        nameStage.initModality(Modality.APPLICATION_MODAL);
        nameStage.initOwner(scarerButton.getScene().getWindow());
        nameStage.setTitle("Enter Your Name");
        nameStage.setResizable(false);

        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(35, 40, 35, 40));
        root.setStyle("-fx-background-color: #1E3248;");

        Label titleLbl = new Label("ENTER YOUR NAME");
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLbl.setStyle("-fx-text-fill: #FFD700;");

        String roleColor = (role == Role.SCARER) ? "#2E4A7A" : "#7A4A1E";
        Label roleLbl = new Label("You chose: " + role.name());
        roleLbl.setStyle(
                "-fx-text-fill: " + roleColor + ";" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-color: rgba(255,215,0,0.08);" +
                        "-fx-padding: 4 12;" +
                        "-fx-background-radius: 6;"
        );

        TextField nameField = new TextField();
        nameField.setPromptText("Your name...");
        nameField.setMaxWidth(240);
        nameField.setStyle(
                "-fx-background-color: #142236;" +
                        "-fx-text-fill: #FFD700;" +
                        "-fx-font-size: 14px;" +
                        "-fx-prompt-text-fill: #5A6A7A;" +
                        "-fx-border-color: #FFD700;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 8 12;"
        );

        Button startBtn = new Button("START GAME");
        startBtn.setPrefWidth(200);
        startBtn.setPrefHeight(40);
        startBtn.setStyle(
                "-fx-background-color: #2E4A7A;" +
                        "-fx-text-fill: #FFD700;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        startBtn.setOnMouseEntered(e -> startBtn.setStyle(
                "-fx-background-color: #3A6A9A;" +
                        "-fx-text-fill: #FFD700;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        ));
        startBtn.setOnMouseExited(e -> startBtn.setStyle(
                "-fx-background-color: #2E4A7A;" +
                        "-fx-text-fill: #FFD700;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        ));

        startBtn.setOnAction(e -> doStartGame(role, nameField, nameStage));
        nameField.setOnAction(e -> doStartGame(role, nameField, nameStage));

        root.getChildren().addAll(titleLbl, roleLbl, nameField, startBtn);

        root.setOpacity(0);
        Scene scene = new Scene(root, 340, 240);
        nameStage.setScene(scene);
        nameStage.show();
        nameField.requestFocus();

        FadeTransition ft = new FadeTransition(Duration.millis(300), root);
        ft.setToValue(1); ft.play();
    }

    private void doStartGame(Role role, TextField nameField, Stage nameStage) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = (role == Role.SCARER) ? "Scarer" : "Laugher";
        nameStage.close();
        try {
            loadGame(role, name);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ── GAME LOADING ─────────────────────────────────────────────

    private void loadGame(Role role, String playerName) throws Exception {
        Game game = new Game(role);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
        Parent root = loader.load();
        GameController controller = loader.getController();
        controller.initGame(game, playerName);
        Stage stage = (Stage) scarerButton.getScene().getWindow();
        stage.setScene(new Scene(root, 1280, 720));
        stage.show();
    }

    // ── INSTRUCTIONS ─────────────────────────────────────────────

    private void showFancyInstructions() {
        Stage instrStage = new Stage();
        instrStage.setTitle("Game Instructions - DoorDash");

        BorderPane mainPane = new BorderPane();
        mainPane.setStyle("-fx-background-color: #1E3248;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(20, 20, 15, 20));
        titleBox.setStyle("-fx-background-color: #1E3248;");

        Label titleLabel = new Label("DOORDASH - GAME GUIDE");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 30));
        titleLabel.setStyle("-fx-text-fill: #FFD700;");

        Label subtitleLabel = new Label("Scare vs Laugh Touchdown - The Floor Awaits!");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setStyle("-fx-text-fill: #CCCCEE;");

        Separator titleSep = new Separator();
        titleSep.setStyle("-fx-background-color: #FFD700;");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel, titleSep);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: #1E3248; -fx-background-color: #1E3248;");
        scrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: #1E3248;");

        contentBox.getChildren().add(makeSection("WINNING CONDITIONS",
                "BOTH conditions must be met to win:\n" +
                        "  1) Land EXACTLY on cell 100 (Boo's Door)\n" +
                        "  2) Have at least 1000 energy in your canister\n\n" +
                        "If you pass cell 100 without landing exactly, you wrap around!",
                "rgba(255,215,0,0.08)", "#FFD700", "#FFD700"));

        contentBox.getChildren().add(makeDivider());

        contentBox.getChildren().add(makeSection("TURN SEQUENCE",
                "1) POWER-UP (Optional): Pay 500 energy for your monster's special ability.\n" +
                        "   FREE if you landed on a Monster Cell of the same role!\n\n" +
                        "2) ROLL DICE: Click the dice image (1-6).\n\n" +
                        "3) MOVE: Advance by dice roll. Cannot land on opponent's cell!\n\n" +
                        "4) CELL EFFECT: Activate the landed cell's effect.\n\n" +
                        "5) CHECK WIN CONDITION. If not met, switch turns.",
                "rgba(46,74,122,0.3)", "#2E4A7A", "#FFD700"));

        contentBox.getChildren().add(makeDivider());

        contentBox.getChildren().add(makeSection("DOOR MECHANICS",
                "- 50 doors alternate between SCARER and LAUGHER.\n" +
                        "- Landing affects YOUR ENTIRE TEAM energy.\n" +
                        "- ONE-TIME USE: door exhausted after first landing.\n" +
                        "- Role MATCH: gain energy. Role MISMATCH: lose energy.\n" +
                        "- SHIELD blocks energy loss (door stays active!).",
                "rgba(122,74,30,0.3)", "#7A4A1E", "#FFD700"));

        contentBox.getChildren().add(makeDivider());

        contentBox.getChildren().add(makeSection("SPECIAL CELLS",
                "DOORS:          Team-wide energy gain or loss.\n" +
                        "MONSTER CELL:   Same role = FREE power-up. Opposite = SWAP energy.\n" +
                        "CARD CELL:      Draw a random card. Watch the flip animation!\n" +
                        "CONVEYOR BELT:  Move FORWARD automatically.\n" +
                        "CONTAM. SOCK:   Move BACKWARD and lose 100 energy!\n" +
                        "NORMAL:         Safe passage, no effect.",
                "rgba(46,74,122,0.3)", "#2E4A7A", "#FFD700"));

        contentBox.getChildren().add(makeDivider());

        contentBox.getChildren().add(makeSection("MONSTER TYPES AND POWER-UPS",
                "DASHER:      2x speed. Powerup: 3x speed for 3 turns.\n" +
                        "DYNAMO:      ALL gains/losses DOUBLED. Powerup: Freeze opponent 1 turn.\n" +
                        "MULTITASKER: 1/2 speed but +200 energy bonus. Powerup: Normal speed 2 turns.\n" +
                        "SCHEMER:     +10 bonus to all changes. Powerup: Steal from ALL monsters.",
                "rgba(122,74,30,0.3)", "#7A4A1E", "#FFD700"));

        contentBox.getChildren().add(makeDivider());

        contentBox.getChildren().add(makeSection("STRATEGY TIPS",
                "- Save your power-up for critical moments!\n" +
                        "- Land on Monster Cells of your role for FREE power-ups.\n" +
                        "- Doors affect your ENTIRE TEAM. Coordinate!\n" +
                        "- Contamination Socks hurt badly. Avoid if possible.\n" +
                        "- Dynamo doubles EVERYTHING: gains AND losses!\n" +
                        "- Schemer steals from ALL monsters including teammates!",
                "rgba(46,74,122,0.3)", "#2E4A7A", "#FFD700"));

        scrollPane.setContent(contentBox);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(15));
        buttonBox.setStyle("-fx-background-color: #1E3248;");

        Button closeBtn = new Button("GOT IT! CHOOSE YOUR SIDE");
        closeBtn.setStyle(
                "-fx-background-color: #FFD700;" +
                        "-fx-text-fill: #1E3248;" +
                        "-fx-font-size: 16px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 12 35;" +
                        "-fx-background-radius: 30;" +
                        "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                "-fx-background-color: #2E4A7A;-fx-text-fill: #FFD700;-fx-font-size: 16px;" +
                        "-fx-font-weight: bold;-fx-padding: 12 35;-fx-background-radius: 30;-fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                "-fx-background-color: #FFD700;-fx-text-fill: #1E3248;-fx-font-size: 16px;" +
                        "-fx-font-weight: bold;-fx-padding: 12 35;-fx-background-radius: 30;-fx-cursor: hand;"));
        closeBtn.setOnAction(e -> instrStage.close());
        buttonBox.getChildren().add(closeBtn);

        mainPane.setTop(titleBox);
        mainPane.setCenter(scrollPane);
        mainPane.setBottom(buttonBox);

        mainPane.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(400), mainPane);
        ft.setToValue(1); ft.play();

        instrStage.setScene(new Scene(mainPane, 750, 750));
        instrStage.show();
    }

    // ── HELPERS ──────────────────────────────────────────────────

    private VBox makeSection(String title, String content,
                             String bgColor, String borderColor, String titleColor) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1.5;"
        );
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        titleLbl.setStyle("-fx-text-fill: " + titleColor + ";");
        Label contentLbl = new Label(content);
        contentLbl.setWrapText(true);
        contentLbl.setStyle("-fx-text-fill: #CCCCEE; -fx-font-size: 13px;");
        box.getChildren().addAll(titleLbl, contentLbl);
        return box;
    }

    private Separator makeDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #FFD700;");
        sep.setMaxWidth(500);
        return sep;
    }
}