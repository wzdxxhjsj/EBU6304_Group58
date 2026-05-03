package com.group58.recruit.ui.fx;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import com.group58.recruit.service.TAService.DashboardData;
import com.group58.recruit.util.DataFileOpen;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * JavaFX TA dashboard view. Business logic remains in TAService.
 */
public final class TADashboardFxView extends BorderPane {
    private static final int MAX_APPLICATIONS = 4;
    private static final int MAX_ACCEPTED = 3;
    private static final String SIDEBAR_LOGO_PATH = "assets/icons/qmul-logo.png";

    private final TAService taService = new TAService();
    private final Runnable logoutAction;

    private User currentTaUser;

    private final Label nameLabel = new Label("TA User");
    private final Label avatarLabel = new Label("TA");
    private final Label cvLabel = new Label("CV: not uploaded");
    private final Button openCvButton = new Button("Open CV");
    private final Label appliedCountLabel = new Label("0 / 4");
    private final Label acceptedCountLabel = new Label("0 / 3");
    private final Label appliedHintLabel = new Label("Maximum 4 applications allowed. You applied: 0/4");
    private final Label acceptedHintLabel = new Label("Maximum 3 applications will be accepted. Accepted: 0/3");
    private final ProgressBar appliedProgress = new ProgressBar(0.0);
    private final ProgressBar acceptedProgress = new ProgressBar(0.0);
    private final TextField searchField = new TextField();
    private final ComboBox<String> workloadFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TilePane moduleGrid = new TilePane();

    public TADashboardFxView() {
        this(() -> {
        });
    }

    public TADashboardFxView(Runnable logoutAction) {
        this.logoutAction = logoutAction == null ? () -> {
        } : logoutAction;
        setStyle("-fx-background-color: #f4f7fb;");
        setLeft(buildSidebar());
        setCenter(buildContent());
        bindActions();
        reloadWorkloadOptions();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.TA) {
            currentTaUser = null;
            nameLabel.setText("No TA user");
            avatarLabel.setText("TA");
            cvLabel.setText("CV: not uploaded");
            openCvButton.setDisable(true);
            renderModules(List.of());
            return;
        }
        currentTaUser = user;
        refreshHeader();
        refreshCards();
    }

    private Node buildSidebar() {
        VBox bar = new VBox(18);
        bar.setPadding(new Insets(16, 10, 12, 10));
        bar.setPrefWidth(96);
        bar.setAlignment(Pos.TOP_CENTER);
        bar.setStyle("-fx-background-color: linear-gradient(to bottom, #0d63f3, #003c95);");

        Node logo = createSidebarLogo();

        VBox nav = new VBox(10);
        nav.setAlignment(Pos.CENTER);
        nav.getChildren().addAll(
                navButton(FontAwesomeSolid.HOME, true, this::refreshCards),
                navButton(FontAwesomeSolid.CLIPBOARD_LIST, false, this::openHistoryDialog),
                navButton(FontAwesomeSolid.USER_FRIENDS, false, this::openProfileDialog));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.setPrefWidth(74);
        logout.setMinWidth(74);
        logout.setPrefHeight(34);
        logout.setGraphic(icon(FontAwesomeSolid.SIGN_OUT_ALT, 12, "#ffffff"));
        logout.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        logout.setStyle(
                "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.6); -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: white; -fx-font-size: 11;");
        logout.setOnAction(e -> {
            setCurrentUser(null);
            logoutAction.run();
        });

        bar.getChildren().addAll(logo, nav, spacer, logout);
        return bar;
    }

    private Node createSidebarLogo() {
        StackPane frame = new StackPane();
        frame.setPrefSize(68, 68);
        frame.setMinSize(68, 68);
        frame.setMaxSize(68, 68);
        frame.setStyle("-fx-background-color: transparent;");
        File logoFile = new File(SIDEBAR_LOGO_PATH);
        if (logoFile.isFile()) {
            ImageView imageView = new ImageView(new Image(logoFile.toURI().toString(), true));
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setFitWidth(64);
            imageView.setFitHeight(64);
            frame.getChildren().add(imageView);
            return frame;
        }
        Label fallback = new Label("\uD83C\uDF93");
        fallback.setStyle("-fx-font-size: 16;");
        frame.getChildren().add(fallback);
        return frame;
    }

    private Node buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18, 18, 16, 18));
        root.getChildren().addAll(buildAppTitleBar(), buildTopHeader(), buildStatsRow(), buildFilterRow(), buildModuleArea());
        return root;
    }

    private Node buildAppTitleBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 2, 2, 2));
        Label title = new Label("BUPT International School \u2014 TA Recruitment");
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 24; -fx-font-weight: 800;");
        bar.getChildren().add(title);
        return bar;
    }

    private Node buildTopHeader() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.15, 0, 4);");

        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane(avatarLabel);
        avatar.setPrefSize(62, 62);
        avatar.setStyle("-fx-background-color: linear-gradient(to bottom right, #3979ff, #2f6ced); -fx-background-radius: 31;");
        avatarLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22; -fx-font-weight: bold;");

        VBox info = new VBox(4);
        nameLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 36; -fx-font-weight: 700;");
        cvLabel.setStyle("-fx-text-fill: #68778a; -fx-font-size: 13;");
        HBox cvRow = new HBox(8, cvLabel, openCvButton);
        cvRow.setAlignment(Pos.CENTER_LEFT);

        openCvButton.setStyle(
                "-fx-background-color: #f0f6ff; -fx-border-color: #c6dafd; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #225da8; -fx-font-weight: 600;");
        openCvButton.setDisable(true);

        info.getChildren().addAll(nameLabel, cvRow);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button upload = ghostButton("Upload", FontAwesomeSolid.UPLOAD,
                () -> uploadCv(getScene() == null ? null : getScene().getWindow()));
        HBox quick = new HBox(8, upload,
                ghostButton("Profile", FontAwesomeSolid.USER, this::openProfileDialog),
                ghostButton("History", FontAwesomeSolid.HISTORY, this::openHistoryDialog));

        topRow.getChildren().addAll(avatar, info, spacer, quick);
        card.getChildren().add(topRow);
        return card;
    }

    private Node buildStatsRow() {
        HBox row = new HBox(12);
        Node left = statCard("Applications", appliedCountLabel, appliedHintLabel, appliedProgress, "#2c6bfd",
                FontAwesomeSolid.CLIPBOARD_LIST);
        Node right = statCard("Accepted", acceptedCountLabel, acceptedHintLabel, acceptedProgress, "#22b458",
                FontAwesomeSolid.CHECK_CIRCLE);
        if (left instanceof Region) {
            ((Region) left).setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(left, Priority.ALWAYS);
        }
        if (right instanceof Region) {
            ((Region) right).setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(right, Priority.ALWAYS);
        }
        row.getChildren().addAll(left, right);
        return row;
    }

    private Node buildFilterRow() {
        VBox wrap = new VBox(10);
        wrap.setPadding(new Insets(14));
        wrap.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.07), 16, 0.15, 0, 3);");

        Label title = new Label("Module");
        title.setStyle("-fx-font-size: 13; -fx-text-fill: #4b5563; -fx-font-weight: 600;");

        HBox row = new HBox(10);
        searchField.setPromptText("Search modules...");
        searchField.setPrefWidth(300);
        searchField.setStyle(inputStyle());

        workloadFilter.setPrefWidth(200);
        workloadFilter.setStyle(inputStyle());

        statusFilter.getItems().setAll("All status", "OPEN", "CLOSED", "FINISHED");
        statusFilter.getSelectionModel().select(0);
        statusFilter.setPrefWidth(180);
        statusFilter.setStyle(inputStyle());

        Button search = new Button("Search");
        search.setGraphic(icon(FontAwesomeSolid.SEARCH, 12, "white"));
        search.setStyle(
                "-fx-background-color: #2167f7; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: 600; -fx-padding: 9 22 9 22;");
        search.setOnAction(e -> refreshCards());

        row.getChildren().addAll(searchField, workloadFilter, statusFilter, search);
        wrap.getChildren().addAll(title, row);
        return wrap;
    }

    private Node buildModuleArea() {
        moduleGrid.setHgap(12);
        moduleGrid.setVgap(12);
        moduleGrid.setPrefColumns(2);
        moduleGrid.setPrefTileWidth(460);
        moduleGrid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane pane = new ScrollPane(moduleGrid);
        pane.setFitToWidth(true);
        pane.setPadding(new Insets(2));
        pane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(pane, Priority.ALWAYS);

        moduleGrid.prefColumnsProperty().bind(Bindings.createIntegerBinding(() -> {
            double w = pane.getViewportBounds().getWidth();
            if (w >= 1450) {
                return 3;
            }
            if (w >= 920) {
                return 2;
            }
            return 1;
        }, pane.viewportBoundsProperty()));
        moduleGrid.prefTileWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            double w = pane.getViewportBounds().getWidth();
            int cols = moduleGrid.getPrefColumns();
            double gaps = Math.max(0, cols - 1) * moduleGrid.getHgap();
            double usable = w - gaps - 2;
            if (cols <= 0) {
                return 420d;
            }
            return Math.max(330, usable / cols);
        }, pane.viewportBoundsProperty(), moduleGrid.prefColumnsProperty()));
        return pane;
    }

    private void bindActions() {
        openCvButton.setOnAction(e -> openCurrentCv());
    }

    private Button navButton(FontAwesomeSolid glyph, boolean active, Runnable action) {
        Button b = new Button();
        b.setGraphic(icon(glyph, 16, active ? "#2167f7" : "#ffffff"));
        b.setPrefSize(42, 42);
        b.setStyle(active
                ? "-fx-background-color: white; -fx-background-radius: 10;"
                : "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.5); -fx-border-radius: 10;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button ghostButton(String text, FontAwesomeSolid icon, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(this.icon(icon, 12, "#334155"));
        b.setStyle(
                "-fx-background-color: #f8fafc; -fx-border-color: #e3e8ef; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #334155; -fx-font-weight: 600;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node statCard(String title, Label valueLabel, Label hintLabel, ProgressBar progress, String progressColor,
            FontAwesomeSolid glyph) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(16));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.07), 16, 0.15, 0, 3);");

        StackPane iconBg = new StackPane(icon(glyph, 18, progressColor));
        iconBg.setPrefSize(42, 42);
        iconBg.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 21;");

        VBox info = new VBox(5);
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13; -fx-font-weight: 600;");
        valueLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 34; -fx-font-weight: 800;");
        progress.setMinHeight(20);
        progress.setPrefHeight(20);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefWidth(320);
        progress.setStyle(
                "-fx-accent: " + progressColor + ";"
                        + "-fx-control-inner-background: #eef2f7;"
                        + "-fx-background-radius: 10;"
                        + "-fx-border-color: #c9d8ee;"
                        + "-fx-border-radius: 10;"
                        + "-fx-border-width: 1;");
        hintLabel.setStyle("-fx-text-fill: #7b8794; -fx-font-size: 12;");
        hintLabel.setWrapText(true);
        info.getChildren().addAll(t, valueLabel, progress, hintLabel);

        card.getChildren().addAll(iconBg, info);
        return card;
    }

    private void refreshHeader() {
        if (currentTaUser == null) {
            return;
        }
        TAProfile profile = taService.loadOrCreateProfile(currentTaUser);
        String display = profile != null && profile.getName() != null && !profile.getName().isBlank()
                ? profile.getName()
                : currentTaUser.getName();
        nameLabel.setText(display);
        avatarLabel.setText(initials(display));

        String cvPath = taService.getCvFilePath(currentTaUser.getQmId());
        if (cvPath == null || cvPath.isBlank()) {
            cvLabel.setText("CV: not uploaded");
            openCvButton.setDisable(true);
            return;
        }

        String compact = cvPath.length() > 60 ? "..." + cvPath.substring(cvPath.length() - 57) : cvPath;
        cvLabel.setText("CV: " + compact);
        Path abs = DataFileOpen.resolveUnderData(cvPath);
        openCvButton.setDisable(abs == null || !Files.isRegularFile(abs));
    }

    private void refreshCards() {
        if (currentTaUser == null) {
            renderModules(List.of());
            return;
        }
        String workload = workloadFilter.getValue();
        DashboardData data = taService.getDashboardData(currentTaUser.getQmId(),
                searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT),
                workload);

        int applied = Math.max(0, Math.min(MAX_APPLICATIONS, data.getAppliedCount()));
        int accepted = Math.max(0, Math.min(MAX_ACCEPTED, data.getAcceptedCount()));
        appliedCountLabel.setText(applied + " / " + MAX_APPLICATIONS);
        acceptedCountLabel.setText(accepted + " / " + MAX_ACCEPTED);
        appliedHintLabel.setText("Maximum 4 applications allowed. You applied: " + applied + "/" + MAX_APPLICATIONS);
        acceptedHintLabel.setText("Maximum 3 applications will be accepted. Accepted: " + accepted + "/" + MAX_ACCEPTED);
        appliedProgress.setProgress(applied / (double) MAX_APPLICATIONS);
        acceptedProgress.setProgress(accepted / (double) MAX_ACCEPTED);

        String status = statusFilter.getValue();
        List<ModulePosting> all = data.getPostings();
        if (status == null || "All status".equals(status)) {
            renderModules(all);
            return;
        }
        renderModules(all.stream()
                .filter(p -> p.getStatus() != null && status.equalsIgnoreCase(p.getStatus().name()))
                .collect(Collectors.toList()));
    }

    private void renderModules(List<ModulePosting> modules) {
        moduleGrid.getChildren().clear();
        if (modules.isEmpty()) {
            Label empty = new Label("No module postings match your filter.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14;");
            moduleGrid.getChildren().add(empty);
            return;
        }
        for (ModulePosting posting : modules) {
            moduleGrid.getChildren().add(buildModuleCard(posting));
        }
    }

    private Node buildModuleCard(ModulePosting posting) {
        BorderPane card = new BorderPane();
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 2);");

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        FontAwesomeSolid moduleGlyph = moduleGlyphFor(posting);
        String[] colors = moduleIconColors(posting);
        StackPane iconBg = new StackPane(icon(moduleGlyph, 20, colors[0]));
        iconBg.setPrefSize(46, 46);
        iconBg.setStyle("-fx-background-color: " + colors[1] + "; -fx-background-radius: 23;");

        VBox titleWrap = new VBox(3);
        Label title = new Label(posting.getModuleCode() + " - " + posting.getModuleName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 18; -fx-font-weight: 700;");
        Label meta = new Label("Workload: " + posting.getWorkload() + "   ·   Vacancies: "
                + posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal());
        meta.setStyle("-fx-text-fill: #667085; -fx-font-size: 12.5;");
        titleWrap.getChildren().addAll(title, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label status = new Label(posting.getStatus() == null ? "UNKNOWN" : posting.getStatus().name());
        status.setStyle(statusChipStyle(posting.getStatus()));

        top.getChildren().addAll(iconBg, titleWrap, spacer, status);
        card.setCenter(top);

        Button view = new Button("View");
        view.setStyle(
                "-fx-background-color: #f0f6ff; -fx-border-color: #c6dafd; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #245ca6; -fx-font-weight: 700;");
        view.setOnAction(e -> showModuleDialog(posting));
        BorderPane.setAlignment(view, Pos.CENTER_RIGHT);
        card.setBottom(view);
        BorderPane.setMargin(view, new Insets(12, 0, 0, 0));

        return card;
    }

    private FontAwesomeSolid moduleGlyphFor(ModulePosting posting) {
        String key = ((posting.getModuleCode() == null ? "" : posting.getModuleCode()) + " "
                + (posting.getModuleName() == null ? "" : posting.getModuleName())).toLowerCase(Locale.ROOT);
        if (key.contains("network")) {
            return FontAwesomeSolid.NETWORK_WIRED;
        }
        if (key.contains("web")) {
            return FontAwesomeSolid.GLOBE;
        }
        if (key.contains("software") || key.contains("program")) {
            return FontAwesomeSolid.CODE;
        }
        if (key.contains("operating") || key.contains("system")) {
            return FontAwesomeSolid.DESKTOP;
        }
        if (key.contains("middleware")) {
            return FontAwesomeSolid.LAYER_GROUP;
        }
        if (key.contains("database")) {
            return FontAwesomeSolid.DATABASE;
        }
        if (key.contains("math") || key.contains("algorithm")) {
            return FontAwesomeSolid.CALCULATOR;
        }
        return FontAwesomeSolid.BOOK_OPEN;
    }

    private String[] moduleIconColors(ModulePosting posting) {
        String code = posting.getModuleCode() == null ? "" : posting.getModuleCode().toUpperCase(Locale.ROOT);
        int bucket = Math.abs(code.hashCode()) % 6;
        switch (bucket) {
            case 0:
                return new String[] { "#4f46e5", "#eef2ff" };
            case 1:
                return new String[] { "#059669", "#eafaf2" };
            case 2:
                return new String[] { "#0284c7", "#eaf6fe" };
            case 3:
                return new String[] { "#d97706", "#fff8eb" };
            case 4:
                return new String[] { "#7c3aed", "#f5f0ff" };
            default:
                return new String[] { "#0f766e", "#edfdf9" };
        }
    }

    private void showModuleDialog(ModulePosting posting) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Module Detail");
        alert.setHeaderText(posting.getModuleCode() + " - " + posting.getModuleName());

        VBox body = new VBox(10);
        body.setPadding(new Insets(6, 2, 6, 2));
        body.getChildren().addAll(
                detailRow("Workload", posting.getWorkload()),
                detailRow("Status", posting.getStatus() == null ? "UNKNOWN" : posting.getStatus().name()),
                detailRow("Vacancies", posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal()),
                detailRow("Description", posting.getDescription()),
                detailRow("Requirements", posting.getRequirements()));
        alert.getDialogPane().setContent(body);

        ButtonType applyBtn = new ButtonType("Apply");
        ButtonType closeBtn = new ButtonType("Close");
        alert.getDialogPane().getButtonTypes().setAll(applyBtn, closeBtn);
        alert.showAndWait().ifPresent(type -> {
            if (type != applyBtn) {
                return;
            }
            if (!taHasCvFileReady()) {
                Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
                warn.setTitle("No CV on file");
                warn.setHeaderText("You have not uploaded a CV, or file cannot be found.");
                warn.setContentText("Apply anyway?");
                warn.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                if (warn.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    return;
                }
            }
            if (!canApply(posting)) {
                showWarn("Apply failed", "This position is no longer open for application.");
                return;
            }
            if (currentTaUser == null) {
                showWarn("No TA user", "No TA user found to submit the application.");
                return;
            }
            ApplyResult result = taService.submitApplication(currentTaUser.getQmId(), posting.getModuleId());
            if (result.isSuccess()) {
                showInfo("Apply", result.getMessage());
                refreshCards();
            } else {
                showWarn("Apply failed", result.getMessage());
            }
        });
    }

    private boolean taHasCvFileReady() {
        if (currentTaUser == null) {
            return false;
        }
        String rel = taService.getCvFilePath(currentTaUser.getQmId());
        if (rel == null || rel.isBlank()) {
            return false;
        }
        Path abs = DataFileOpen.resolveUnderData(rel);
        return abs != null && Files.isRegularFile(abs);
    }

    private boolean canApply(ModulePosting posting) {
        return posting.getStatus() == ModuleStatus.OPEN && posting.getVacanciesFilled() < posting.getVacanciesTotal();
    }

    private Node detailRow(String label, String value) {
        VBox box = new VBox(4);
        Label key = new Label(label);
        key.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12; -fx-font-weight: 700;");
        Label val = new Label(value == null ? "-" : value);
        val.setWrapText(true);
        val.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 13;");
        box.getChildren().addAll(key, val);
        return box;
    }

    private void reloadWorkloadOptions() {
        workloadFilter.getItems().setAll(taService.getWorkloadOptions());
        if (workloadFilter.getItems().isEmpty()) {
            workloadFilter.getItems().add("All workload");
        }
        workloadFilter.getSelectionModel().select(0);
    }

    private void openCurrentCv() {
        if (currentTaUser == null) {
            showWarn("No TA session", "No TA user selected.");
            return;
        }
        String rel = taService.getCvFilePath(currentTaUser.getQmId());
        if (rel == null || rel.isBlank()) {
            showWarn("Open CV", "CV has not been uploaded yet.");
            return;
        }
        Path path = DataFileOpen.resolveUnderData(rel);
        if (path == null || !Files.isRegularFile(path)) {
            showWarn("Open CV", "CV file not found.");
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showWarn("Open CV", "Desktop open is not supported on this machine.");
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            showWarn("Open CV", "Could not open file: " + ex.getMessage());
        }
    }

    public void uploadCv(Window ownerWindow) {
        if (currentTaUser == null) {
            showWarn("No TA session", "No TA user selected.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select CV (PDF)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
        File selected = chooser.showOpenDialog(ownerWindow);
        if (selected == null) {
            return;
        }
        ApplyResult result = taService.updateCvFilePath(currentTaUser, selected.toPath());
        if (result.isSuccess()) {
            refreshHeader();
            showInfo("CV upload", "CV uploaded: " + selected.getName());
        } else {
            showWarn("Upload failed", result.getMessage());
        }
    }

    private void openProfileDialog() {
        if (currentTaUser == null) {
            showWarn("No TA session", "Please login as TA first.");
            return;
        }
        TAProfile profile = taService.loadOrCreateProfile(currentTaUser);
        String skills = (profile == null || profile.getSkills() == null || profile.getSkills().isEmpty())
                ? "-"
                : String.join(", ", profile.getSkills());
        String text = "QMID: " + currentTaUser.getQmId() + "\n"
                + "Name: " + (profile != null && profile.getName() != null ? profile.getName() : currentTaUser.getName()) + "\n"
                + "Email: " + (profile != null && profile.getEmail() != null ? profile.getEmail() : currentTaUser.getEmail()) + "\n"
                + "Phone: " + (profile != null && profile.getPhone() != null ? profile.getPhone() : "-") + "\n"
                + "Skills: " + skills + "\n"
                + "Allow Adjustment: " + (profile == null || profile.isAllowAdjustment() ? "Yes" : "No") + "\n"
                + "CV Path: " + (profile != null && profile.getCvFilePath() != null ? profile.getCvFilePath() : "-");
        showInfo("Profile", text);
    }

    private void openHistoryDialog() {
        if (currentTaUser == null) {
            showWarn("No TA session", "Please login as TA first.");
            return;
        }
        List<TAService.ApplicationHistoryRow> rows = taService.listMyApplications(currentTaUser.getQmId());
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefWidth(620);
        area.setPrefHeight(420);
        if (rows.isEmpty()) {
            area.setText("No application history.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (TAService.ApplicationHistoryRow row : rows) {
                sb.append(row.getModuleCode()).append(" - ").append(row.getModuleName())
                        .append(" | ").append(row.getStatusDisplayLabel())
                        .append(" | appId=").append(row.getApplicationId())
                        .append("\n");
            }
            area.setText(sb.toString());
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Application History");
        alert.setHeaderText("My applications");
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private FontIcon icon(FontAwesomeSolid glyph, int size, String color) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(size);
        icon.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        return icon;
    }

    private static String inputStyle() {
        return "-fx-background-color: white; -fx-border-color: #dbe4ee; -fx-border-radius: 8; -fx-background-radius: 8;";
    }

    private static String statusChipStyle(ModuleStatus status) {
        if (status == ModuleStatus.OPEN) {
            return "-fx-background-color: #e8f8ef; -fx-text-fill: #149f59; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
        }
        if (status == ModuleStatus.FINISHED) {
            return "-fx-background-color: #eef2ff; -fx-text-fill: #4f46e5; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
        }
        return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "TA";
        }
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length == 1) {
            return tokens[0].substring(0, Math.min(2, tokens[0].length())).toUpperCase(Locale.ROOT);
        }
        return (tokens[0].substring(0, 1) + tokens[tokens.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void showWarn(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
