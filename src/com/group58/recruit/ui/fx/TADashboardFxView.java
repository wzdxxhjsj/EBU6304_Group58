package com.group58.recruit.ui.fx;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
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

public final class TADashboardFxView extends BorderPane {
    private final TAService taService = new TAService();
    private final Runnable logoutAction;
    private User currentTaUser;
    private boolean profileEditMode = false;
    private String activePage = "home";

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
    private final VBox pageContent = new VBox(14);
    private final VBox noticeLabel = new VBox();
    private VBox filterPanel;

    public TADashboardFxView() { this(() -> {}); }

    public TADashboardFxView(Runnable logoutAction) {
        this.logoutAction = logoutAction == null ? () -> {} : logoutAction;
        setStyle("-fx-background-color: #f4f7fb;");
        setLeft(buildSidebar());
        setCenter(buildContent());
        reloadWorkloadOptions();
        showOverviewPage();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.TA) {
            currentTaUser = null;
            nameLabel.setText("No TA user");
            avatarLabel.setText("TA");
            cvLabel.setText("CV: not uploaded");
            openCvButton.setDisable(true);
            renderModules(List.of());
            showOverviewPage();
            return;
        }
        currentTaUser = user;
        refreshHeader();
        refreshCards();
        showOverviewPage();
    }

    private Node buildSidebar() {
        VBox bar = new VBox(12);
        bar.setPadding(new Insets(18, 14, 14, 14));
        bar.setPrefWidth(220);
        bar.setStyle("-fx-background-color: linear-gradient(to bottom, #0d2f67, #051f49);");

        VBox menu = new VBox(10);
        menu.setPadding(new Insets(8, 0, 0, 0));
        menu.getChildren().addAll(
                navItem("Home", FontAwesomeSolid.HOME, "home".equals(activePage), this::showOverviewPage),
                navItem("Applications", FontAwesomeSolid.BRIEFCASE, "applications".equals(activePage), this::showApplicationsPage),
                navItem("Profile", FontAwesomeSolid.USER, "profile".equals(activePage), this::showProfilePage));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setGraphic(icon(FontAwesomeSolid.SIGN_OUT_ALT, 14, "#ffffff"));
        logout.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        logout.setAlignment(Pos.CENTER_LEFT);
        logout.setStyle("-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: 700; -fx-padding: 10 14 10 14;");
        logout.setOnAction(e -> { setCurrentUser(null); logoutAction.run(); });

        bar.getChildren().addAll(createSidebarLogo(), menu, spacer, logout);
        return bar;
    }

    private Node createSidebarLogo() {
        StackPane frame = new StackPane();
        frame.setPrefSize(68, 68);
        File logoFile = new File("assets/icons/qmul-logo.png");
        if (logoFile.isFile()) {
            ImageView imageView = new ImageView(new Image(logoFile.toURI().toString(), true));
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setFitWidth(64);
            imageView.setFitHeight(64);
            frame.getChildren().add(imageView);
            return frame;
        }
        Label fallback = new Label("🎓");
        fallback.setStyle("-fx-font-size: 16;");
        frame.getChildren().add(fallback);
        return frame;
    }

    private Node buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18, 18, 16, 18));
        noticeLabel.setManaged(false);
        noticeLabel.setVisible(false);
        noticeLabel.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 12; -fx-border-color: #d7e5ff; -fx-border-radius: 12; -fx-text-fill: #245ca6; -fx-padding: 10 14 10 14; -fx-font-size: 13; -fx-font-weight: 700;");
        filterPanel = buildFilterRow();
        root.getChildren().addAll(buildAppTitleBar(), noticeLabel, buildTopHeader(), buildStatsRow(), filterPanel, pageContent);
        return root;
    }

    private Node buildAppTitleBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("BUPT International School — TA Recruitment");
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 24; -fx-font-weight: 800;");
        bar.getChildren().add(title);
        return bar;
    }

    private Node buildTopHeader() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.15, 0, 4);");
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane avatar = new StackPane(avatarLabel);
        avatar.setPrefSize(62, 62);
        avatar.setStyle("-fx-background-color: linear-gradient(to bottom right, #3979ff, #2f6ced); -fx-background-radius: 31;");
        avatarLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22; -fx-font-weight: bold;");
        VBox info = new VBox(4);
        nameLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 28; -fx-font-weight: 700;");
        cvLabel.setStyle("-fx-text-fill: #68778a; -fx-font-size: 13;");
        HBox cvRow = new HBox(8, cvLabel, openCvButton);
        cvRow.setAlignment(Pos.CENTER_LEFT);
        openCvButton.setStyle("-fx-background-color: #f0f6ff; -fx-border-color: #c6dafd; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #225da8; -fx-font-weight: 600;");
        openCvButton.setDisable(false);
        info.getChildren().addAll(nameLabel, cvRow);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox quick = new HBox(8,
                ghostButton("Upload", FontAwesomeSolid.UPLOAD, () -> uploadCv(getScene() == null ? null : getScene().getWindow())),
                ghostButton("History", FontAwesomeSolid.HISTORY, this::showApplicationsPage));
        openCvButton.setOnAction(e -> openCurrentCv());
        row.getChildren().addAll(avatar, info, spacer, quick);
        card.getChildren().add(row);
        return card;
    }

    private Node buildStatsRow() {
        HBox row = new HBox(12);
        Node left = statCard("Applications", appliedCountLabel, appliedHintLabel, appliedProgress, "#2c6bfd", FontAwesomeSolid.CLIPBOARD_LIST);
        Node right = statCard("Accepted", acceptedCountLabel, acceptedHintLabel, acceptedProgress, "#22b458", FontAwesomeSolid.CHECK_CIRCLE);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        row.getChildren().addAll(left, right);
        return row;
    }

    private VBox buildFilterRow() {
        VBox wrap = new VBox(10);
        filterPanel = wrap;
        wrap.setPadding(new Insets(14));
        wrap.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.07), 16, 0.15, 0, 3);");
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
        search.setStyle("-fx-background-color: #2167f7; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: 600; -fx-padding: 9 22 9 22;");
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
        pane.setHbarPolicy(ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        pane.setPadding(new Insets(2));
        pane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private void showOverviewPage() {
        activePage = "home";
        refreshCards();
        setLeft(buildSidebar());
        pageContent.getChildren().setAll(buildModuleArea());
        if (filterPanel != null) { filterPanel.setVisible(true); filterPanel.setManaged(true); }
        showNotice("");
    }

    private void showProfilePage() {
        activePage = "profile";
        setLeft(buildSidebar());
        pageContent.getChildren().setAll(buildProfilePage());
        if (filterPanel != null) { filterPanel.setVisible(false); filterPanel.setManaged(false); }
        showNotice("");
    }

    private void showApplicationsPage() {
        activePage = "applications";
        setLeft(buildSidebar());
        pageContent.getChildren().setAll(buildApplicationsPage());
        if (filterPanel != null) { filterPanel.setVisible(false); filterPanel.setManaged(false); }
        showNotice("");
    }

    private Button navItem(String text, FontAwesomeSolid glyph, boolean active, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setGraphic(icon(glyph, 18, active ? "#ffffff" : "rgba(255,255,255,0.8)"));
        b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(navItemStyle(active));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button ghostButton(String text, FontAwesomeSolid iconGlyph, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(this.icon(iconGlyph, 12, "#334155"));
        b.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e3e8ef; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #334155; -fx-font-weight: 600;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node statCard(String title, Label valueLabel, Label hintLabel, ProgressBar progress, String progressColor, FontAwesomeSolid glyph) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(16));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.07), 16, 0.15, 0, 3);");
        StackPane iconBg = new StackPane(icon(glyph, 18, progressColor));
        iconBg.setPrefSize(42, 42);
        iconBg.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 21;");
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13; -fx-font-weight: 600;");
        valueLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 34; -fx-font-weight: 800;");
        progress.setMinHeight(20);
        progress.setPrefHeight(20);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setStyle("-fx-accent: " + progressColor + "; -fx-control-inner-background: #eef2f7; -fx-background-radius: 10; -fx-border-color: #c9d8ee; -fx-border-radius: 10; -fx-border-width: 1;");
        hintLabel.setStyle("-fx-text-fill: #7b8794; -fx-font-size: 12;");
        hintLabel.setWrapText(true);
        info.getChildren().addAll(t, valueLabel, progress, hintLabel);
        card.getChildren().addAll(iconBg, info);
        return card;
    }

    private void refreshHeader() {
        if (currentTaUser == null) return;
        TAProfile profile = taService.loadOrCreateProfile(currentTaUser);
        String display = profile != null && profile.getName() != null && !profile.getName().isBlank() ? profile.getName() : currentTaUser.getName();
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
        if (currentTaUser == null) { renderModules(List.of()); return; }
        DashboardData data = taService.getDashboardData(currentTaUser.getQmId(), searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT), workloadFilter.getValue());
        int applied = Math.max(0, Math.min(4, data.getAppliedCount()));
        int accepted = Math.max(0, Math.min(3, data.getAcceptedCount()));
        appliedCountLabel.setText(applied + " / 4");
        acceptedCountLabel.setText(accepted + " / 3");
        appliedHintLabel.setText("Maximum 4 applications allowed. You applied: " + applied + "/4");
        acceptedHintLabel.setText("Maximum 3 applications will be accepted. Accepted: " + accepted + "/3");
        appliedProgress.setProgress(applied / 4.0);
        acceptedProgress.setProgress(accepted / 3.0);
        refreshHeader();
        List<ModulePosting> all = data.getPostings();
        String status = statusFilter.getValue();
        if (status == null || "All status".equals(status)) renderModules(all);
        else renderModules(all.stream().filter(p -> p.getStatus() != null && status.equalsIgnoreCase(p.getStatus().name())).collect(Collectors.toList()));
    }

    private void renderModules(List<ModulePosting> modules) {
        moduleGrid.getChildren().clear();
        if (modules.isEmpty()) {
            Label empty = new Label("No module postings match your filter.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14;");
            moduleGrid.getChildren().add(empty);
            return;
        }
        for (ModulePosting posting : modules) moduleGrid.getChildren().add(buildModuleCard(posting));
    }

    private Node buildModuleCard(ModulePosting posting) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 2);");
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        String[] colors = moduleIconColors(posting);
        StackPane iconBg = new StackPane(icon(moduleGlyphFor(posting), 20, colors[0]));
        iconBg.setPrefSize(46, 46);
        iconBg.setStyle("-fx-background-color: " + colors[1] + "; -fx-background-radius: 23;");
        VBox titleWrap = new VBox(3);
        Label title = new Label(posting.getModuleCode() + " - " + posting.getModuleName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 18; -fx-font-weight: 700;");
        Label meta = new Label("Workload: " + posting.getWorkload() + "   ·   Vacancies: " + posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal());
        meta.setStyle("-fx-text-fill: #667085; -fx-font-size: 12.5;");
        titleWrap.getChildren().addAll(title, meta);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(posting.getStatus() == null ? "UNKNOWN" : posting.getStatus().name());
        status.setStyle(statusChipStyle(posting.getStatus()));
        top.getChildren().addAll(iconBg, titleWrap, spacer, status);
        Button view = new Button("View & Apply");
        view.setStyle("-fx-background-color: #f0f6ff; -fx-border-color: #c6dafd; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #245ca6; -fx-font-weight: 700;");
        view.setOnAction(e -> showModuleDetail(posting));
        card.getChildren().addAll(top, view);
        return card;
    }

    private FontAwesomeSolid moduleGlyphFor(ModulePosting posting) {
        String key = ((posting.getModuleCode() == null ? "" : posting.getModuleCode()) + " " + (posting.getModuleName() == null ? "" : posting.getModuleName())).toLowerCase(Locale.ROOT);
        if (key.contains("network")) return FontAwesomeSolid.NETWORK_WIRED;
        if (key.contains("web")) return FontAwesomeSolid.GLOBE;
        if (key.contains("software") || key.contains("program")) return FontAwesomeSolid.CODE;
        if (key.contains("operating") || key.contains("system")) return FontAwesomeSolid.DESKTOP;
        if (key.contains("middleware")) return FontAwesomeSolid.LAYER_GROUP;
        if (key.contains("database")) return FontAwesomeSolid.DATABASE;
        if (key.contains("math") || key.contains("algorithm")) return FontAwesomeSolid.CALCULATOR;
        return FontAwesomeSolid.BOOK_OPEN;
    }

    private String[] moduleIconColors(ModulePosting posting) {
        int bucket = Math.abs((posting.getModuleCode() == null ? "" : posting.getModuleCode().toUpperCase(Locale.ROOT)).hashCode()) % 6;
        switch (bucket) {
            case 0: return new String[] {"#4f46e5", "#eef2ff"};
            case 1: return new String[] {"#059669", "#eafaf2"};
            case 2: return new String[] {"#0284c7", "#eaf6fe"};
            case 3: return new String[] {"#d97706", "#fff8eb"};
            case 4: return new String[] {"#7c3aed", "#f5f0ff"};
            default: return new String[] {"#0f766e", "#edfdf9"};
        }
    }

    private void showModuleDetail(ModulePosting posting) {
        VBox detail = new VBox(12);
        detail.setPadding(new Insets(16));
        detail.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
        detail.getChildren().addAll(
                detailRow("Workload", posting.getWorkload()),
                detailRow("Status", posting.getStatus() == null ? "UNKNOWN" : posting.getStatus().name()),
                detailRow("Vacancies", posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal()),
                detailRow("Description", posting.getDescription()),
                detailRow("Requirements", posting.getRequirements()));
        Button apply = new Button("Apply");
        apply.setStyle("-fx-background-color: #2167f7; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
        apply.setOnAction(e -> {
            if (currentTaUser == null) { showNotice("No TA user selected."); return; }
            if (!canApply(posting)) { showNotice("This position is no longer open for application."); return; }
            ApplyResult result = taService.submitApplication(currentTaUser.getQmId(), posting.getModuleId());
            if (result.isSuccess()) { refreshCards(); showApplicationsPage(); showNotice(result.getMessage()); }
            else showNotice(result.getMessage());
        });
        detail.getChildren().add(apply);
        pageContent.getChildren().setAll(buildModuleArea(), detail);
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
        if (workloadFilter.getItems().isEmpty()) workloadFilter.getItems().add("All workload");
        workloadFilter.getSelectionModel().select(0);
    }

    private void openCurrentCv() {
        if (currentTaUser == null) { showNotice("No TA user selected."); return; }
        String rel = taService.getCvFilePath(currentTaUser.getQmId());
        if (rel == null || rel.isBlank()) { showNotice("CV has not been uploaded yet."); return; }
        Path path = DataFileOpen.resolveUnderData(rel);
        if (path == null || !Files.isRegularFile(path)) { showNotice("CV file not found."); return; }
        if (!Desktop.isDesktopSupported()) { showNotice("Desktop open is not supported on this machine."); return; }
        try { Desktop.getDesktop().open(path.toFile()); } catch (IOException ex) { showNotice("Could not open file: " + ex.getMessage()); }
    }

    public void uploadCv(Window ownerWindow) {
        if (currentTaUser == null) { showNotice("No TA user selected."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select CV (PDF)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
        File selected = chooser.showOpenDialog(ownerWindow);
        if (selected == null) return;
        ApplyResult result = taService.updateCvFilePath(currentTaUser, selected.toPath());
        if (result.isSuccess()) { refreshHeader(); showNotice("CV uploaded: " + selected.getName()); }
        else showNotice(result.getMessage());
    }

    private Node buildProfilePage() {
        VBox page = new VBox(18);
        TAProfile profile = currentTaUser == null ? null : taService.loadOrCreateProfile(currentTaUser);
        String displayName = currentTaUser == null ? "No TA user" : profile != null && profile.getName() != null && !profile.getName().isBlank() ? profile.getName() : currentTaUser.getName();
        String email = currentTaUser == null ? "" : profile != null && profile.getEmail() != null && !profile.getEmail().isBlank() ? profile.getEmail() : currentTaUser.getEmail();
        String phone = profile != null && profile.getPhone() != null ? profile.getPhone() : "";
        String skills = profile == null || profile.getSkills() == null || profile.getSkills().isEmpty() ? "" : String.join(", ", profile.getSkills());
        String cvPath = profile != null && profile.getCvFilePath() != null && !profile.getCvFilePath().isBlank() ? profile.getCvFilePath() : "-";
        if (profileEditMode) page.getChildren().addAll(buildProfileFormCard(profile, displayName, email, phone, skills), buildCvCard(cvPath));
        else page.getChildren().addAll(buildProfileSummaryCard(profile, displayName, email, phone, skills), buildCvCard(cvPath));
        return wrapPage(page);
    }

    private Node buildApplicationsPage() {
        VBox page = new VBox(18);
        List<TAService.ApplicationHistoryRow> rows = currentTaUser == null ? List.of() : taService.listMyApplications(currentTaUser.getQmId());
        page.getChildren().addAll(buildHistoryHeader(rows), buildHistoryTable(rows), buildHistoryNote());
        return wrapPage(page);
    }

    private Node wrapPage(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        pane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrapper = new VBox(pane);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return wrapper;
    }

    private Node statMiniCard(String title, String value, String color, double progress) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12; -fx-border-color: #e7edf4; -fx-border-radius: 12;");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11; -fx-font-weight: 700;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 18; -fx-font-weight: 800;");
        ProgressBar p = new ProgressBar(progress);
        p.setPrefWidth(140);
        p.setPrefHeight(10);
        p.setStyle("-fx-accent: " + color + "; -fx-background-insets: 0; -fx-padding: 0; -fx-background-radius: 8;");
        box.getChildren().addAll(t, v, p);
        return box;
    }

    private Node buildProfileSummaryCard(TAProfile profile, String displayName, String email, String phone, String skills) {
        VBox card = new VBox(14);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);");
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(icon(FontAwesomeSolid.USER, 14, "#2c6bfd"));
        badge.setPrefSize(34, 34);
        badge.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 17;");
        Label t = new Label("Personal Information");
        t.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 20; -fx-font-weight: 800;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button edit = new Button("Edit");
        edit.setGraphic(icon(FontAwesomeSolid.PENCIL_ALT, 12, "#245ca6"));
        edit.setStyle("-fx-background-color: white; -fx-border-color: #c6dafd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #245ca6; -fx-font-weight: 700; -fx-padding: 7 16 7 16;");
        edit.setOnAction(e -> { profileEditMode = true; showProfilePage(); });
        Button save = new Button("Save");
        save.setGraphic(icon(FontAwesomeSolid.SAVE, 12, "#245ca6"));
        save.setStyle("-fx-background-color: white; -fx-border-color: #c6dafd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #245ca6; -fx-font-weight: 700; -fx-padding: 7 16 7 16;");
        save.setOnAction(e -> { profileEditMode = false; showProfilePage(); });
        header.getChildren().addAll(badge, t, spacer, edit, save);
        card.getChildren().addAll(header,
                profileRow("Name", displayName, FontAwesomeSolid.USER), dividerLine(),
                profileRow("Phone", phone, FontAwesomeSolid.PHONE), dividerLine(),
                profileRow("Email", email, FontAwesomeSolid.ENVELOPE), dividerLine(),
                profileRow("Skills", skills, FontAwesomeSolid.CODE), dividerLine(),
                settingChipRow(profile == null || profile.isAllowAdjustment() ? "Willing to accept reassignment" : "Not willing to accept reassignment", "This setting applies to all your applications.", profile == null || profile.isAllowAdjustment() ? "Enabled" : "Disabled"));
        return card;
    }

    private Node profileRow(String label, String value, FontAwesomeSolid glyph) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 10, 0));
        StackPane iconBg = new StackPane(icon(glyph, 14, "#6b7280"));
        iconBg.setPrefSize(28, 28);
        iconBg.setStyle("-fx-background-color: #f3f6fb; -fx-background-radius: 14;");
        Label key = new Label(label);
        key.setPrefWidth(90);
        key.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13; -fx-font-weight: 700;");
        Label val = new Label(value == null ? "" : value);
        val.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 13;");
        row.getChildren().addAll(iconBg, key, val);
        return row;
    }

    private Node dividerLine() {
        Region line = new Region();
        line.setPrefHeight(1);
        line.setStyle("-fx-background-color: #edf2f7;");
        return line;
    }

    private Node settingChipRow(String title, String subtitle, String state) {
        HBox row = new HBox(12);
        row.setPadding(new Insets(14));
        row.setAlignment(Pos.CENTER_LEFT);
        boolean disabled = state != null && state.equalsIgnoreCase("Disabled");
        row.setStyle(disabled ? "-fx-background-color: #fff5f5; -fx-background-radius: 14; -fx-border-color: #f5c2c7; -fx-border-radius: 14;" : "-fx-background-color: #f8fcf7; -fx-background-radius: 14; -fx-border-color: #e3f1e6; -fx-border-radius: 14;");
        StackPane iconBg = new StackPane(icon(disabled ? FontAwesomeSolid.TIMES_CIRCLE : FontAwesomeSolid.CHECK_CIRCLE, 16, disabled ? "#dc2626" : "#3a9461"));
        iconBg.setPrefSize(30, 30);
        iconBg.setStyle(disabled ? "-fx-background-color: #fdecec; -fx-background-radius: 15;" : "-fx-background-color: #e8f8ef; -fx-background-radius: 15;");
        VBox texts = new VBox(2);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 13; -fx-font-weight: 800;");
        Label s = new Label(subtitle);
        s.setStyle("-fx-text-fill: #7b8794; -fx-font-size: 12;");
        texts.getChildren().addAll(t, s);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label chip = new Label(state == null ? "" : state);
        chip.setStyle(profileChipStyle(state));
        row.getChildren().addAll(iconBg, texts, spacer, chip);
        return row;
    }


    private Node buildProfileFormCard(TAProfile profile, String displayName, String email, String phone, String skills) {
        VBox card = new VBox(14);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);");
        HBox title = new HBox(10);
        title.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(icon(FontAwesomeSolid.USER, 14, "#2c6bfd"));
        badge.setPrefSize(34, 34);
        badge.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 17;");
        Label t = new Label("Personal Information");
        t.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 20; -fx-font-weight: 800;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button save = new Button("Save");
        save.setGraphic(icon(FontAwesomeSolid.SAVE, 12, "#245ca6"));
        save.setStyle("-fx-background-color: white; -fx-border-color: #c6dafd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #245ca6; -fx-font-weight: 700; -fx-padding: 7 16 7 16;");
        title.getChildren().addAll(badge, t, spacer, save);
        TextField nameField = new TextField(displayName);
        TextField phoneField = new TextField(phone);
        TextField emailField = new TextField(email);
        TextField skillsField = new TextField(skills);
        ComboBox<String> adjustmentBox = new ComboBox<>();
        adjustmentBox.getItems().addAll("Enabled", "Disabled");
        adjustmentBox.getSelectionModel().select(profile != null && !profile.isAllowAdjustment() ? "Disabled" : "Enabled");
        VBox form = new VBox(12,
                labeledField("Name", nameField),
                labeledField("Phone", phoneField),
                labeledField("Email", emailField),
                labeledField("Skills", skillsField),
                labeledChoice("Not willing to accept reassignment", adjustmentBox));
        save.setOnAction(e -> saveProfile(profile, nameField, phoneField, emailField, skillsField, adjustmentBox));
        card.getChildren().addAll(title, form);
        return card;
    }

    private Node buildCvCard(String cvPath) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);");
        HBox title = new HBox(10, iconLabel(FontAwesomeSolid.FILE_PDF, "#2c6bfd", "#eef4ff", "CV File"));
        title.setAlignment(Pos.CENTER_LEFT);
        Label path = new Label(cvPath == null ? "-" : cvPath);
        path.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13;");
        HBox actions = new HBox(10,
                cvActionButton("Open CV", FontAwesomeSolid.EYE, "#245ca6", this::openCurrentCv),
                cvActionButton("Upload / Replace", FontAwesomeSolid.UPLOAD, "#245ca6", () -> uploadCv(getScene() == null ? null : getScene().getWindow())),
                cvActionButton("Open Location", FontAwesomeSolid.FOLDER_OPEN, "#245ca6", () -> openCvFolder()));
        card.getChildren().addAll(title, path, actions);
        return card;
    }

    private Node buildHistoryHeader(List<TAService.ApplicationHistoryRow> rows) {
        int total = rows == null ? 0 : rows.size();
        long accepted = rows == null ? 0 : rows.stream().filter(r -> "Accepted".equalsIgnoreCase(r.getStatusDisplayLabel())).count();
        long submitted = rows == null ? 0 : rows.stream().filter(r -> "Submitted".equalsIgnoreCase(r.getStatusDisplayLabel())).count();
        long rejected = rows == null ? 0 : rows.stream().filter(r -> "Rejected".equalsIgnoreCase(r.getStatusDisplayLabel())).count();
        HBox card = new HBox(14);
        card.setPadding(new Insets(18));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.15, 0, 4);");
        VBox titleWrap = new VBox(4, styledTitle("Application History"), styledSubTitle("My applications"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox stats = new HBox(12,
                historyStatCard("All", String.valueOf(total), "#2c6bfd"),
                historyStatCard("Accepted", String.valueOf(accepted), "#22b458"),
                historyStatCard("Submitted", String.valueOf(submitted), "#f59e0b"),
                historyStatCard("Rejected", String.valueOf(rejected), "#ef4444"));
        card.getChildren().addAll(titleWrap, spacer, stats);
        return card;
    }

    private Node buildHistoryTable(List<TAService.ApplicationHistoryRow> rows) {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 12, 0.12, 0, 2);");
        HBox header = new HBox();
        header.setPadding(new Insets(14, 18, 14, 18));
        header.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 16 16 0 0;");
        header.getChildren().addAll(tableHeadCell("Module", 360), tableHeadCell("Workload", 140), tableHeadCell("Applied On", 180), tableHeadCell("Status", 140));
        card.getChildren().add(header);
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label("No application history.");
            empty.setPadding(new Insets(18));
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14;");
            card.getChildren().add(empty);
            return card;
        }
        VBox body = new VBox();
        for (TAService.ApplicationHistoryRow row : rows) body.getChildren().add(historyRow(row));
        card.getChildren().add(body);
        return card;
    }

    private Node buildHistoryNote() {
        HBox note = new HBox(10);
        note.setPadding(new Insets(14, 16, 14, 16));
        note.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 14; -fx-border-color: #d7e5ff; -fx-border-radius: 14;");
        Label info = new Label("Application status will be updated when module owners review your application.");
        info.setStyle("-fx-text-fill: #245ca6; -fx-font-size: 13;");
        note.getChildren().addAll(icon(FontAwesomeSolid.INFO_CIRCLE, 16, "#245ca6"), info);
        return note;
    }

    private Node historyRow(TAService.ApplicationHistoryRow row) {
        HBox line = new HBox();
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(14, 18, 14, 18));
        line.setStyle("-fx-border-color: transparent transparent #edf2f7 transparent;");
        VBox module = new VBox(2);
        module.getChildren().add(styledCellTitle((row.getModuleCode() == null ? "" : row.getModuleCode()) + " - " + (row.getModuleName() == null ? "" : row.getModuleName())));
        Label workload = styledCell(row.getWorkload() == null ? "-" : row.getWorkload());
        Label appliedOn = styledCell(row.getAppliedOn() == null ? "-" : row.getAppliedOn().replace('T', ' '));
        Label status = new Label(row.getStatusDisplayLabel() == null ? "-" : row.getStatusDisplayLabel());
        status.setStyle(historyStatusStyle(row.getStatusDisplayLabel()));
        line.getChildren().addAll(fixedCell(module, 360), fixedCell(workload, 140), fixedCell(appliedOn, 180), fixedCell(status, 140));
        return line;
    }

    private Node fixedCell(Node node, double width) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(width);
        box.setMinWidth(width);
        box.setMaxWidth(width);
        return box;
    }

    private Label styledTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 24; -fx-font-weight: 800;");
        return label;
    }

    private Label styledSubTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13;");
        return label;
    }

    private Label styledCellTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 13; -fx-font-weight: 700;");
        label.setWrapText(true);
        return label;
    }

    private Label styledCell(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 13;");
        return label;
    }

    private Node tableHeadCell(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12; -fx-font-weight: 700;");
        return label;
    }

    private Node historyStatCard(String title, String value, String color) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12; -fx-border-color: #e7edf4; -fx-border-radius: 12;");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11; -fx-font-weight: 700;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 18; -fx-font-weight: 800;");
        card.getChildren().addAll(t, v);
        return card;
    }

    private String historyStatusStyle(String status) {
        if (status == null) return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 4 10 4 10; -fx-font-size: 12; -fx-font-weight: 700;";
        String normalized = status.toLowerCase(Locale.ROOT);
        if (normalized.contains("accept")) return "-fx-background-color: #e8f8ef; -fx-text-fill: #149f59; -fx-background-radius: 12; -fx-padding: 4 10 4 10; -fx-font-size: 12; -fx-font-weight: 700;";
        if (normalized.contains("reject")) return "-fx-background-color: #fdecec; -fx-text-fill: #dc2626; -fx-background-radius: 12; -fx-padding: 4 10 4 10; -fx-font-size: 12; -fx-font-weight: 700;";
        return "-fx-background-color: #fff7e6; -fx-text-fill: #d97706; -fx-background-radius: 12; -fx-padding: 4 10 4 10; -fx-font-size: 12; -fx-font-weight: 700;";
    }

    private String profileChipStyle(String state) {
        if (state == null) return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 5 12 5 12; -fx-font-size: 12; -fx-font-weight: 700;";
        String normalized = state.toLowerCase(Locale.ROOT);
        if (normalized.contains("enable")) return "-fx-background-color: #e8f8ef; -fx-text-fill: #2e8b57; -fx-background-radius: 12; -fx-padding: 5 12 5 12; -fx-font-size: 12; -fx-font-weight: 700;";
        if (normalized.contains("disable")) return "-fx-background-color: #fdecec; -fx-text-fill: #dc2626; -fx-background-radius: 12; -fx-padding: 5 12 5 12; -fx-font-size: 12; -fx-font-weight: 700;";
        return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 5 12 5 12; -fx-font-size: 12; -fx-font-weight: 700;";
    }

    private String navItemStyle(boolean active) {
        return active
                ? "-fx-background-color: linear-gradient(to right, #2f7bff, #215fff); -fx-background-radius: 10; -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: 700; -fx-padding: 12 14 12 14;"
                : "-fx-background-color: transparent; -fx-background-radius: 10; -fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 14; -fx-font-weight: 600; -fx-padding: 12 14 12 14;";
    }

    private Node labeledField(String label, TextField field) {
        VBox box = new VBox(5);
        Label t = new Label(label);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12; -fx-font-weight: 700;");
        field.setStyle(inputStyle());
        box.getChildren().addAll(t, field);
        return box;
    }

    private Node labeledChoice(String label, ComboBox<String> choice) {
        VBox box = new VBox(5);
        Label t = new Label(label);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12; -fx-font-weight: 700;");
        choice.setStyle(inputStyle());
        box.getChildren().addAll(t, choice);
        return box;
    }

    private void saveProfile(TAProfile existing, TextField nameField, TextField phoneField, TextField emailField, TextField skillsField, ComboBox<String> adjustmentBox) {
        if (currentTaUser == null) { showProfileError("No TA session."); return; }
        TAProfile profile = existing == null ? new TAProfile() : existing;
        profile.setQmId(currentTaUser.getQmId());
        profile.setName(nameField.getText() == null ? "" : nameField.getText().trim());
        profile.setPhone(phoneField.getText() == null ? "" : phoneField.getText().trim());
        profile.setEmail(emailField.getText() == null ? "" : emailField.getText().trim());
        profile.setSkills(parseSkills(skillsField.getText()));
        profile.setAllowAdjustment(!"Disabled".equalsIgnoreCase(adjustmentBox.getValue()));
        if (profile.getProfileId() == null || profile.getProfileId().isBlank()) profile.setProfileId("prof-" + currentTaUser.getQmId());
        String validationError = validateProfileInput(profile.getPhone(), profile.getEmail());
        if (validationError != null) { showProfileError(validationError); return; }
        ApplyResult result = taService.saveProfile(profile);
        if (result.isSuccess()) { refreshHeader(); profileEditMode = false; showProfilePage(); showNotice(result.getMessage()); }
        else showProfileError(result.getMessage());
    }

    private List<String> parseSkills(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private Node iconLabel(FontAwesomeSolid glyph, String color, String background, String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane bg = new StackPane(icon(glyph, 14, color));
        bg.setPrefSize(34, 34);
        bg.setStyle("-fx-background-color: " + background + "; -fx-background-radius: 17;");
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 14; -fx-font-weight: 800;");
        row.getChildren().addAll(bg, label);
        return row;
    }

    private Button cvActionButton(String text, FontAwesomeSolid glyph, String color, Runnable action) {
        Button button = new Button(text);
        button.setGraphic(icon(glyph, 12, color));
        button.setStyle("-fx-background-color: white; -fx-border-color: #c6dafd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: " + color + "; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
        button.setOnAction(e -> action.run());
        return button;
    }

    private void openCvFolder() {
        if (currentTaUser == null) { showNotice("No TA user selected."); return; }
        String rel = taService.getCvFilePath(currentTaUser.getQmId());
        if (rel == null || rel.isBlank()) { showNotice("CV has not been uploaded yet."); return; }
        Path path = DataFileOpen.resolveUnderData(rel);
        if (path == null) { showNotice("CV file not found."); return; }
        try { Desktop.getDesktop().open(path.getParent().toFile()); } catch (IOException ex) { showNotice("Could not open folder: " + ex.getMessage()); }
    }

    private FontIcon icon(FontAwesomeSolid glyph, int size, String color) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(size);
        icon.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        return icon;
    }

    private static String inputStyle() { return "-fx-background-color: white; -fx-border-color: #dbe4ee; -fx-border-radius: 8; -fx-background-radius: 8;"; }
    private static String statusChipStyle(ModuleStatus status) {
        if (status == ModuleStatus.OPEN) return "-fx-background-color: #e8f8ef; -fx-text-fill: #149f59; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
        if (status == ModuleStatus.FINISHED) return "-fx-background-color: #eef2ff; -fx-text-fill: #4f46e5; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
        return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 4 9 4 9; -fx-font-size: 11; -fx-font-weight: 700;";
    }
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "TA";
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length == 1) return tokens[0].substring(0, Math.min(2, tokens[0].length())).toUpperCase(Locale.ROOT);
        return (tokens[0].substring(0, 1) + tokens[tokens.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private void showNotice(String message) {
        noticeLabel.getChildren().clear();
        if (message == null || message.isBlank()) {
            noticeLabel.setManaged(false);
            noticeLabel.setVisible(false);
            return;
        }
        Label l = new Label(message);
        l.setStyle("-fx-text-fill: #245ca6; -fx-font-size: 13; -fx-font-weight: 700;");
        noticeLabel.getChildren().add(l);
        noticeLabel.setManaged(true);
        noticeLabel.setVisible(true);
    }

    private String validateProfileInput(String phone, String email) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() != 11) return "Phone number must be exactly 11 digits.";
        if (email == null || email.isBlank()) return "Email cannot be empty.";
        if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$")) return "Email format is invalid.";
        return null;
    }

    private void showProfileError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Profile validation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
