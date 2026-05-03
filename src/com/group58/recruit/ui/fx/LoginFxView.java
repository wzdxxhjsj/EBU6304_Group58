package com.group58.recruit.ui.fx;

import java.io.File;
import java.util.function.Consumer;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AuthService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Full-screen login (glass card over {@code assets/background/login-bg.png}). Uses {@link AuthService} only.
 */
public final class LoginFxView extends StackPane {

    private static final String BG_PATH = "assets/background/login-bg.png";

    private final AuthService authService;
    private final Consumer<User> onSuccess;
    private final TextField idField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField passwordPlain = new TextField();
    private final ToggleGroup roleGroup = new ToggleGroup();
    private final RadioButton roleTa = new RadioButton("TA");
    private final RadioButton roleMo = new RadioButton("MO");
    private final RadioButton roleAdmin = new RadioButton("Admin");

    public LoginFxView(AuthService authService, Consumer<User> onSuccess) {
        this.authService = authService;
        this.onSuccess = onSuccess;
        build();
    }

    private void build() {
        getChildren().clear();

        File bgFile = new File(BG_PATH);
        if (bgFile.isFile()) {
            ImageView bg = new ImageView(new Image(bgFile.toURI().toString(), false));
            bg.setPreserveRatio(false);
            bg.fitWidthProperty().bind(widthProperty());
            bg.fitHeightProperty().bind(heightProperty());
            getChildren().add(bg);
        } else {
            Region fallback = new Region();
            fallback.setStyle("-fx-background-color: linear-gradient(to bottom right, #3b82f6, #1d4ed8);");
            fallback.prefWidthProperty().bind(widthProperty());
            fallback.prefHeightProperty().bind(heightProperty());
            getChildren().add(fallback);
        }

        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(255,255,255,0.05);");
        dim.prefWidthProperty().bind(widthProperty());
        dim.prefHeightProperty().bind(heightProperty());
        getChildren().add(dim);

        VBox card = new VBox(18);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(440);
        // StackPane 默认会把子节点在垂直方向拉满；限制高度随内容，避免出现大块空白。
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setFillWidth(true);
        card.setPadding(new Insets(36, 40, 40, 40));
        card.setStyle(
                "-fx-background-color: rgba(255,255,255,0.88);"
                        + "-fx-background-radius: 28;"
                        + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.18), 28, 0.12, 0, 10);");

        StackPane topIcon = new StackPane(icon(FontAwesomeSolid.BOOK_OPEN, 22, "#1e4a8c"));
        topIcon.setMinSize(52, 52);
        topIcon.setMaxSize(52, 52);
        topIcon.setStyle("-fx-background-color: rgba(30,74,140,0.08); -fx-background-radius: 26;");

        Label welcome = new Label("WELCOME!");
        welcome.setStyle("-fx-font-family: Georgia, 'Times New Roman', serif; -fx-font-size: 42px; -fx-font-weight: bold;"
                + "-fx-text-fill: #153a75;");

        Label system = new Label("TA Recruitment System");
        system.setStyle("-fx-font-size: 22px; -fx-font-weight: 600; -fx-text-fill: #1e4a8c;");

        Label hint = new Label("Please choose your role and log in");
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");

        roleTa.setToggleGroup(roleGroup);
        roleMo.setToggleGroup(roleGroup);
        roleAdmin.setToggleGroup(roleGroup);
        roleTa.setSelected(true);
        for (RadioButton rb : new RadioButton[] { roleTa, roleMo, roleAdmin }) {
            rb.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 14px; -fx-font-weight: 600;");
        }

        HBox roleRow = new HBox(16, roleTa, roleMo, roleAdmin);
        roleRow.setAlignment(Pos.CENTER);
        roleRow.setPadding(new Insets(8, 14, 8, 14));
        roleRow.setStyle(
                "-fx-background-color: rgba(241,245,249,0.95); -fx-background-radius: 12;"
                        + "-fx-border-color: #cbd5e1; -fx-border-radius: 12; -fx-border-width: 1;");

        idField.setPromptText("ID");
        HBox idRow = textFieldRow(icon(FontAwesomeSolid.USER, 14, "#64748b"), idField, null);

        passwordField.setPromptText("Password");
        passwordPlain.setPromptText("Password");
        passwordPlain.setVisible(false);
        passwordPlain.setManaged(false);
        ToggleButton eye = new ToggleButton();
        eye.setGraphic(icon(FontAwesomeSolid.EYE, 14, "#64748b"));
        eye.setFocusTraversable(false);
        eye.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 10 4 8;");
        HBox pwRow = passwordRow(eye);

        Button loginBtn = new Button("Login");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(48);
        loginBtn.setDefaultButton(true);
        loginBtn.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #2b7ce8, #1e5fbf); -fx-text-fill: white;"
                        + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 12;"
                        + "-fx-effect: dropshadow(gaussian, rgba(30,95,191,0.35), 8, 0.2, 0, 2);");
        loginBtn.setOnAction(e -> attemptLogin());

        card.getChildren().addAll(
                topIcon,
                welcome,
                system,
                hint,
                roleRow,
                idRow,
                pwRow,
                loginBtn);
        StackPane.setAlignment(card, Pos.CENTER);
        getChildren().add(card);
    }

    private HBox passwordRow(ToggleButton eye) {
        StackPane fieldStack = new StackPane(passwordField, passwordPlain);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordPlain.setMaxWidth(Double.MAX_VALUE);
        passwordField.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        passwordPlain.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        passwordField.setPadding(new Insets(12, 8, 12, 4));
        passwordPlain.setPadding(new Insets(12, 8, 12, 4));

        eye.selectedProperty().addListener((obs, was, now) -> {
            if (Boolean.TRUE.equals(now)) {
                passwordPlain.setText(passwordField.getText());
                passwordPlain.setVisible(true);
                passwordPlain.setManaged(true);
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                eye.setGraphic(icon(FontAwesomeSolid.EYE_SLASH, 14, "#64748b"));
            } else {
                passwordField.setText(passwordPlain.getText());
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                passwordPlain.setVisible(false);
                passwordPlain.setManaged(false);
                eye.setGraphic(icon(FontAwesomeSolid.EYE, 14, "#64748b"));
            }
        });

        HBox left = new HBox(new StackPane(icon(FontAwesomeSolid.LOCK, 14, "#64748b")));
        left.setMinWidth(44);
        left.setMaxWidth(44);
        left.setAlignment(Pos.CENTER);

        HBox row = new HBox(0, left, fieldStack, eye);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 2, 2, 2));
        row.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #cbd5e1;"
                        + "-fx-border-radius: 12; -fx-border-width: 1;");
        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        return row;
    }

    private static HBox textFieldRow(FontIcon leadingIcon, TextField field, ToggleButton trailing) {
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setPadding(new Insets(12, 12, 12, 8));
        field.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        HBox left = new HBox(leadingIcon);
        left.setMinWidth(44);
        left.setMaxWidth(44);
        left.setAlignment(Pos.CENTER);

        HBox row = new HBox(0, left, field);
        if (trailing != null) {
            row.getChildren().add(trailing);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 2, 2, 2));
        row.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #cbd5e1;"
                        + "-fx-border-radius: 12; -fx-border-width: 1;");
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private static FontIcon icon(FontAwesomeSolid g, int size, String color) {
        FontIcon fi = new FontIcon(g);
        fi.setIconSize(size);
        fi.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        return fi;
    }

    private void attemptLogin() {
        Role role;
        if (roleMo.isSelected()) {
            role = Role.MO;
        } else if (roleAdmin.isSelected()) {
            role = Role.ADMIN;
        } else {
            role = Role.TA;
        }
        String qmid = idField.getText() == null ? "" : idField.getText().trim();
        String password = passwordPlain.isVisible() ? passwordPlain.getText() : passwordField.getText();
        if (password == null) {
            password = "";
        }

        if (qmid.isEmpty() || password.isEmpty()) {
            showError("Missing input", "Please fill role, ID and password.");
            return;
        }

        User user = authService.login(qmid, password, role).orElse(null);
        if (user == null) {
            showError("Login failed", "Invalid ID/password or role mismatch.");
            return;
        }
        onSuccess.accept(user);
    }

    private static void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}
