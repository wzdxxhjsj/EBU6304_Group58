package com.group58.recruit.ui.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.MOService.ApplicantRow;
import com.group58.recruit.util.DataFileOpen;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * MO Dashboard with modern sidebar design.
 * Features: left navigation, statistics cards, module management,
 * applicant review with accept/reject, CV preview, history log.
 */
public final class MODashboardFxView extends BorderPane {
    private final MOService moService = new MOService();
    private final Runnable logoutAction;

    private User currentUser;
    private final Label userLabel = new Label("MO: -");
    private static final double MODULE_MIN_CARD_WIDTH = 520;
    private static final double MODULE_GRID_GAP = 20;
    private static final int MODULE_MAX_COLUMNS = 3;

    private final GridPane moduleGrid = new GridPane();
    private ScrollPane moduleScroll;
    private int moduleGridColumns = 2;
    private List<ModulePosting> displayedModules = List.of();
    private final Label totalModulesValue = new Label("0");
    private final Label openModulesValue = new Label("0");
    private final Label finishedModulesValue = new Label("0");
    private final Label pendingApplicationsValue = new Label("0");

    // Sidebar navigation buttons
    private final Button dashboardBtn = new Button("Dashboard");
    private final Button applicationsBtn = new Button("Applications");
    private final Button logoutBtn = new Button("Logout");
    private Button activeNavBtn;
    private Button newModuleBtn;

    private final StackPane centerPages = new StackPane();
    private Node dashboardPage;
    private Node applicationsPage;
    private final VBox applicationsListHost = new VBox(10);
    private String activePage = "dashboard";

    public MODashboardFxView(Runnable logoutAction) {
        // Force English locale for all built-in dialogs (e.g., Alert buttons)
        Locale.setDefault(Locale.ENGLISH);

        this.logoutAction = logoutAction == null ? () -> {} : logoutAction;
        setStyle("-fx-background-color: linear-gradient(to bottom, #f5efff, #f4f7fb);");
        buildSidebar();
        setTop(buildTopBar());
        dashboardPage = buildDashboardPage();
        applicationsPage = buildApplicationsPage();
        centerPages.getChildren().addAll(dashboardPage, applicationsPage);
        setCenter(centerPages);
        showDashboardPage();
    }

    // ======================= SIDEBAR =======================

    private void buildSidebar() {
        VBox sidebarContent = new VBox(12);
        sidebarContent.setPadding(new Insets(18, 14, 14, 14));
        sidebarContent.setPrefWidth(220);
        sidebarContent.setMinWidth(220);
        sidebarContent.setMaxWidth(220);
        sidebarContent.setStyle("-fx-background-color: linear-gradient(to bottom, #5b21b6, #3b0764);");

        VBox navMenu = new VBox(8);
        navMenu.setPadding(new Insets(8, 0, 0, 0));
        styleNavButton(dashboardBtn, FontAwesomeSolid.TACHOMETER_ALT, true);
        styleNavButton(applicationsBtn, FontAwesomeSolid.HISTORY, false);

        dashboardBtn.setOnAction(e -> showDashboardPage());
        applicationsBtn.setOnAction(e -> showApplicationsPage());

        navMenu.getChildren().addAll(dashboardBtn, applicationsBtn);
        activeNavBtn = dashboardBtn;

        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setGraphic(icon(FontAwesomeSolid.SIGN_OUT_ALT, 14, "#ffffff"));
        logoutBtn.setContentDisplay(ContentDisplay.LEFT);
        logoutBtn.setAlignment(Pos.CENTER_LEFT);
        logoutBtn.setStyle(
                "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.25); "
                + "-fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: white; "
                + "-fx-font-size: 13; -fx-font-weight: 700; -fx-padding: 10 14 10 14;");
        logoutBtn.setOnAction(e -> logoutAction.run());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebarContent.getChildren().addAll(createSidebarLogo(), navMenu, spacer, logoutBtn);

        // Wrap sidebar in ScrollPane to enable scrolling when window is short
        ScrollPane sideScroll = new ScrollPane(sidebarContent);
        sideScroll.setFitToWidth(true);
        sideScroll.setFitToHeight(true);   // Allow content to fill height
        sideScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        sideScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        sideScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        setLeft(sideScroll);
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
        Label fallback = new Label("\uD83C\uDF93");
        fallback.setStyle("-fx-font-size: 28; -fx-text-fill: white;");
        frame.getChildren().add(fallback);
        return frame;
    }

    private void styleNavButton(Button btn, FontAwesomeSolid iconGlyph, boolean active) {
        FontIcon graphic = icon(iconGlyph, 18, active ? "#ffffff" : "rgba(255,255,255,0.85)");
        btn.setGraphic(graphic);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(12);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(44);
        btn.setPadding(new Insets(0, 14, 0, 14));
        applyNavButtonStyle(btn, active);
        btn.setOnMouseEntered(e -> {
            if (btn != activeNavBtn) {
                btn.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; "
                        + "-fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
            }
        });
        btn.setOnMouseExited(e -> applyNavButtonStyle(btn, btn == activeNavBtn));
    }

    private static void applyNavButtonStyle(Button btn, boolean active) {
        if (active) {
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.22); -fx-text-fill: white; "
                    + "-fx-font-weight: 700; -fx-background-radius: 10; -fx-border-radius: 10;");
        } else {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.92); "
                    + "-fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
    }

    private void setActiveNav(Button activeBtn) {
        activeNavBtn = activeBtn;
        for (Button btn : new Button[]{dashboardBtn, applicationsBtn}) {
            boolean isActive = btn == activeBtn;
            FontIcon graphic = (FontIcon) btn.getGraphic();
            if (graphic != null) {
                graphic.setIconColor(Color.web(isActive ? "#ffffff" : "rgba(255,255,255,0.85)"));
            }
            applyNavButtonStyle(btn, isActive);
        }
    }

    // ======================= TOP BAR =======================

    private Node buildTopBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e7edf4; -fx-border-width: 0 0 1 0;");

        StackPane avatar = new StackPane(icon(FontAwesomeSolid.CHALKBOARD_TEACHER, 18, "#6d28d9"));
        avatar.setPrefSize(42, 42);
        avatar.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 21;");

        userLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 20px; -fx-font-weight: 800;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        newModuleBtn = new Button("+ New Module");
        stylePrimaryButton(newModuleBtn);
        newModuleBtn.setOnAction(e -> showModuleDialog(null));
        bar.getChildren().addAll(avatar, userLabel, spacer, newModuleBtn);
        return bar;
    }

    private void showDashboardPage() {
        activePage = "dashboard";
        setActiveNav(dashboardBtn);
        dashboardPage.setVisible(true);
        dashboardPage.setManaged(true);
        applicationsPage.setVisible(false);
        applicationsPage.setManaged(false);
        if (newModuleBtn != null) {
            newModuleBtn.setVisible(true);
            newModuleBtn.setManaged(true);
        }
        refreshModules();
    }

    private void showApplicationsPage() {
        if (currentUser == null) {
            showAlert("Error", "Please login as MO first.");
            setActiveNav(dashboardBtn);
            return;
        }
        activePage = "applications";
        setActiveNav(applicationsBtn);
        dashboardPage.setVisible(false);
        dashboardPage.setManaged(false);
        applicationsPage.setVisible(true);
        applicationsPage.setManaged(true);
        if (newModuleBtn != null) {
            newModuleBtn.setVisible(false);
            newModuleBtn.setManaged(false);
        }
        refreshApplicationsPage();
    }

    // ======================= MAIN CONTENT =======================

    private Node buildDashboardPage() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(16, 24, 24, 24));
        root.setAlignment(Pos.TOP_LEFT);

        // 统计卡片行
        HBox statsRow = new HBox(16,
                buildStatCard("Total Modules", totalModulesValue, "#6d28d9", FontAwesomeSolid.BOOK),
                buildStatCard("Open", openModulesValue, "#16a34a", FontAwesomeSolid.DOT_CIRCLE),
                buildStatCard("Finished", finishedModulesValue, "#2563eb", FontAwesomeSolid.CHECK_CIRCLE),
                buildStatCard("Unprocessed", pendingApplicationsValue, "#dc2626", FontAwesomeSolid.FILE_ALT));
        for (Node n : statsRow.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        // 模块列表面板
        VBox modulesPanel = new VBox(14);
        modulesPanel.setPadding(new Insets(16));
        modulesPanel.setStyle(panelStyle());
        // 移除固定高度限制，让内容自动撑开
        modulesPanel.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(modulesPanel, Priority.ALWAYS);

        Label title = new Label("My Modules");
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 18px; -fx-font-weight: 800;");

        moduleGrid.setHgap(MODULE_GRID_GAP);
        moduleGrid.setVgap(MODULE_GRID_GAP);
        moduleGrid.setPadding(new Insets(2));
        moduleGrid.setMaxWidth(Double.MAX_VALUE);
        moduleGrid.setMinWidth(0);
        moduleGrid.setAlignment(Pos.TOP_LEFT);

        VBox modulesWrapper = new VBox(moduleGrid);
        modulesWrapper.setFillWidth(true);
        modulesWrapper.setMinWidth(0);
        modulesWrapper.setMaxWidth(Double.MAX_VALUE);

        moduleScroll = new ScrollPane(modulesWrapper);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        moduleScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        moduleScroll.setPannable(true);
        moduleScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        moduleScroll.setMinHeight(0);
        moduleGrid.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, moduleScroll.getViewportBounds().getWidth()),
                moduleScroll.viewportBoundsProperty()));
        moduleScroll.viewportBoundsProperty().addListener((obs, oldB, newB) ->
                onModuleViewportResized(newB.getWidth()));
        configureModuleGridColumns(computeModuleColumns(moduleScroll.getViewportBounds().getWidth()));
        VBox.setVgrow(moduleScroll, Priority.ALWAYS);

        modulesPanel.getChildren().addAll(title, moduleScroll);
        root.getChildren().addAll(statsRow, modulesPanel);
        VBox.setVgrow(root, Priority.ALWAYS);
        return root;
    }

    private Node buildApplicationsPage() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(16, 24, 24, 24));
        root.setAlignment(Pos.TOP_LEFT);

        VBox panel = new VBox(14);
        panel.setPadding(new Insets(16));
        panel.setStyle(panelStyle());
        panel.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label title = new Label("Application History");
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 18px; -fx-font-weight: 800;");

        applicationsListHost.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(applicationsListHost);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(title, scroll);
        root.getChildren().add(panel);
        VBox.setVgrow(root, Priority.ALWAYS);
        return root;
    }

    private void refreshApplicationsPage() {
        applicationsListHost.getChildren().clear();
        if (currentUser == null) {
            applicationsListHost.getChildren().add(emptyState("Please login as MO first."));
            return;
        }
        List<HistoryRecord> history = loadApplicationHistory();
        if (history.isEmpty()) {
            applicationsListHost.getChildren().add(emptyState("No processed applications yet."));
            return;
        }
        for (HistoryRecord rec : history) {
            applicationsListHost.getChildren().add(buildHistoryCard(rec));
        }
    }

    private List<HistoryRecord> loadApplicationHistory() {
        List<HistoryRecord> history = new ArrayList<>();
        for (ModulePosting m : moService.getMyModules(currentUser.getQmId())) {
            for (ApplicantRow row : moService.getApplicantsForModule(m.getModuleId())) {
                if (row.getStatus() != ApplicationStatus.SUBMITTED) {
                    history.add(new HistoryRecord(row, m.getModuleCode(), m.getModuleName()));
                }
            }
        }
        return history;
    }

    private Node buildStatCard(String title, Label valueLabel, String accentColor, FontAwesomeSolid glyph) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setPrefHeight(108);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);");

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane iconBg = new StackPane(icon(glyph, 16, accentColor));
        iconBg.setPrefSize(44, 44);
        iconBg.setStyle("-fx-background-color: " + tint(accentColor) + "; -fx-background-radius: 22;");

        VBox textWrap = new VBox(2);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-font-weight: 700;");
        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 28px; -fx-font-weight: 800;");
        textWrap.getChildren().addAll(t, valueLabel);

        row.getChildren().addAll(iconBg, textWrap);
        card.getChildren().add(row);
        return card;
    }

    // ======================= MODULE CRUD =======================

    private void showModuleDialog(ModulePosting existing) {
        if (currentUser == null) {
            showAlert("Error", "Please login as MO first.");
            return;
        }

        boolean isNew = (existing == null);
        Dialog<ModulePosting> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isNew ? "Create New Module" : "Edit Module");
        dialog.getDialogPane().setMinHeight(400);   // 保证按钮不被完全遮挡
        dialog.setResizable(true);                  // 允许用户手动调整大小
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #f5efff; -fx-background-radius: 16;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16;");

        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = icon(isNew ? FontAwesomeSolid.PLUS_CIRCLE : FontAwesomeSolid.EDIT, 24, "#6d28d9");
        Label titleLabel = new Label(isNew ? "New Module Posting" : "Edit Module");
        titleLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 20px; -fx-font-weight: 800;");
        titleBox.getChildren().addAll(icon, titleLabel);

        TextField codeField = createStyledTextField("e.g., EBU6304");
        if (!isNew) codeField.setText(existing.getModuleCode());
        codeField.setEditable(isNew);

        TextField nameField = createStyledTextField("Module Name");
        if (!isNew) nameField.setText(existing.getModuleName());

        TextArea descArea = createStyledTextArea("Description of duties", 3);
        if (!isNew) descArea.setText(existing.getDescription());

        TextField workloadField = createStyledTextField("e.g., 8 hours/week");
        if (!isNew) workloadField.setText(existing.getWorkload());

        TextArea reqArea = createStyledTextArea("Requirements (skills, experience)", 3);
        if (!isNew) reqArea.setText(existing.getRequirements());

        ComboBox<Integer> vacanciesCombo = new ComboBox<>();
        vacanciesCombo.getItems().addAll(1, 2, 3);
        vacanciesCombo.setValue(isNew ? 1 : existing.getVacanciesTotal());
        vacanciesCombo.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-padding: 6;");

        ComboBox<ModuleStatus> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(ModuleStatus.values());
        if (isNew) {
            statusCombo.setValue(ModuleStatus.OPEN);
            statusCombo.setDisable(true);
        } else {
            statusCombo.setValue(existing.getStatus());
        }
        statusCombo.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-padding: 6;");

        VBox form = new VBox(8);
        form.getChildren().addAll(
                formField("Module Code *", codeField),
                formField("Module Name *", nameField),
                formField("Description", descArea),
                formField("Workload", workloadField),
                formField("Requirements", reqArea),
                formField("Total Vacancies", vacanciesCombo)
        );
        if (!isNew) {
            form.getChildren().add(formField("Status", statusCombo));
        }

        content.getChildren().addAll(titleBox, form);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                if (codeField.getText().trim().isEmpty() || nameField.getText().trim().isEmpty()) {
                    showAlert("Validation Error", "Module Code and Name are required.");
                    return null;
                }
                try {
                    ModulePosting module = isNew ? new ModulePosting() : existing;
                    if (isNew) module.setModuleCode(codeField.getText().trim());
                    module.setModuleName(nameField.getText().trim());
                    module.setDescription(descArea.getText().trim());
                    module.setWorkload(workloadField.getText().trim());
                    module.setRequirements(reqArea.getText().trim());
                    module.setVacanciesTotal(vacanciesCombo.getValue());
                    if (!isNew) module.setStatus(statusCombo.getValue());
                    else module.setStatus(ModuleStatus.OPEN);
                    return module;
                } catch (Exception e) {
                    showAlert("Error", "Invalid input: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<ModulePosting> result = dialog.showAndWait();
        result.ifPresent(module -> {
            MOService.MOActionResult actionResult;
            if (isNew) {
                actionResult = moService.createModule(module, currentUser.getQmId());
            } else {
                actionResult = moService.updateModule(module, currentUser.getQmId());
            }
            showAlert(actionResult.isSuccess() ? "Success" : "Failed", actionResult.getMessage());
            if (actionResult.isSuccess()) refreshMoViews();
        });
    }

    // ======================= MODULE LIST & STATS =======================

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.MO) {
            currentUser = null;
            userLabel.setText("MO: -");
            moduleGrid.getChildren().clear();
            Node empty = emptyState("Please login as MO first.");
            moduleGrid.add(empty, 0, 0);
            GridPane.setColumnSpan(empty, Math.max(1, moduleGridColumns));
            return;
        }
        currentUser = user;
        String displayName = user.getName() == null || user.getName().isBlank() ? user.getQmId() : user.getName();
        userLabel.setText("MO: " + displayName + " (" + user.getQmId() + ")");
        showDashboardPage();
    }

    private int computeModuleColumns(double availableWidth) {
        if (availableWidth <= 0) {
            return 2;
        }
        int columns = (int) Math.floor((availableWidth + MODULE_GRID_GAP)
                / (MODULE_MIN_CARD_WIDTH + MODULE_GRID_GAP));
        return Math.max(1, Math.min(MODULE_MAX_COLUMNS, columns));
    }

    private void configureModuleGridColumns(int columns) {
        moduleGridColumns = columns;
        moduleGrid.getColumnConstraints().clear();
        double percentEach = 100.0 / columns;
        for (int i = 0; i < columns; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(percentEach);
            col.setHgrow(Priority.ALWAYS);
            col.setFillWidth(true);
            moduleGrid.getColumnConstraints().add(col);
        }
    }

    private void onModuleViewportResized(double availableWidth) {
        if (availableWidth <= 0) {
            return;
        }
        int columns = computeModuleColumns(availableWidth);
        if (columns == moduleGridColumns) {
            return;
        }
        configureModuleGridColumns(columns);
        relayoutModuleGrid();
    }

    private void relayoutModuleGrid() {
        if (displayedModules.isEmpty()) {
            return;
        }
        List<Node> cards = new ArrayList<>(moduleGrid.getChildren());
        if (cards.isEmpty()) {
            return;
        }
        moduleGrid.getChildren().clear();
        for (int i = 0; i < cards.size(); i++) {
            Node card = cards.get(i);
            moduleGrid.add(card, i % moduleGridColumns, i / moduleGridColumns);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
        }
    }

    private void refreshModules() {
        if (currentUser == null) {
            moduleGrid.getChildren().clear();
            Node empty = emptyState("Please login as MO first.");
            moduleGrid.add(empty, 0, 0);
            GridPane.setColumnSpan(empty, Math.max(1, moduleGridColumns));
            return;
        }

        List<ModulePosting> modules = moService.getMyModules(currentUser.getQmId());
        displayedModules = List.copyOf(modules);
        refreshStats(modules);
        moduleGrid.getChildren().clear();

        double viewportWidth = moduleScroll == null ? 0 : moduleScroll.getViewportBounds().getWidth();
        configureModuleGridColumns(computeModuleColumns(viewportWidth));

        if (modules.isEmpty()) {
            Node empty = emptyState("No module postings found.");
            moduleGrid.add(empty, 0, 0);
            GridPane.setColumnSpan(empty, moduleGridColumns);
            return;
        }

        for (int i = 0; i < modules.size(); i++) {
            Node card = buildModuleCard(modules.get(i));
            int col = i % moduleGridColumns;
            int row = i / moduleGridColumns;
            moduleGrid.add(card, col, row);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
        }
    }

    private Node buildModuleCard(ModulePosting module) {
        int total = Math.max(1, module.getVacanciesTotal());
        int filled = Math.min(module.getVacanciesFilled(), total);
        int unprocessed = moService.countPendingForModule(module.getModuleId());
        boolean finished = filled >= total || module.getStatus() == ModuleStatus.FINISHED || module.getStatus() == ModuleStatus.CLOSED;
        double progress = finished ? 1.0 : (double) filled / total;
        String statusText = moduleDisplayStatus(module);
        String statusColor = statusColor(statusText);
        String progressColor = finished ? "#2563eb" : "#16a34a";
        String[] moduleColors = moduleIconColors(module);

        VBox card = new VBox(14);
        card.setPadding(new Insets(18));
        card.setMinHeight(360);
        card.setPrefHeight(360);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.15, 0, 3);");

        StackPane iconBg = new StackPane(icon(moduleGlyphFor(module), 20, moduleColors[0]));
        iconBg.setPrefSize(52, 52);
        iconBg.setMinSize(52, 52);
        iconBg.setMaxSize(52, 52);
        iconBg.setStyle("-fx-background-color: " + moduleColors[1] + "; -fx-background-radius: 26;");

        Label title = new Label(module.getModuleCode() + " - " + module.getModuleName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: #111827; -fx-font-size: 20px; -fx-font-weight: 800;");
        Label badge = new Label(statusText);
        badge.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-size: 11px; -fx-font-weight: 800; "
                + "-fx-background-color: " + tint(statusColor) + "; -fx-background-radius: 999; -fx-padding: 4 10 4 10;");

        VBox titleCol = new VBox(8);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        titleCol.setMinWidth(0);
        titleCol.setPadding(new Insets(0, 72, 0, 0));

        HBox titleIconRow = new HBox(12, iconBg, title);
        titleIconRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);

        HBox recruitedRow = new HBox(6);
        recruitedRow.setAlignment(Pos.BASELINE_LEFT);
        Label recruitedCount = new Label(filled + " / " + total);
        recruitedCount.setStyle("-fx-text-fill: " + progressColor + "; -fx-font-size: 22px; -fx-font-weight: 800;");
        Label recruitedWord = new Label("recruited");
        recruitedWord.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 600;");
        recruitedRow.getChildren().addAll(recruitedCount, recruitedWord);

        titleCol.getChildren().addAll(titleIconRow, recruitedRow);

        StackPane header = new StackPane(titleCol, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);

        HBox progressTrack = new HBox();
        progressTrack.setPrefHeight(10);
        progressTrack.setMinHeight(10);
        progressTrack.setStyle("-fx-background-color: #e8edf5; -fx-background-radius: 999;");
        Region fill = new Region();
        fill.setMinHeight(10);
        fill.setPrefHeight(10);
        fill.setStyle("-fx-background-color: " + progressColor + "; -fx-background-radius: 999;");
        fill.prefWidthProperty().bind(progressTrack.widthProperty().multiply(progress));
        progressTrack.getChildren().add(fill);
        HBox.setHgrow(progressTrack, Priority.ALWAYS);
        Label percent = new Label((int) Math.round(progress * 100) + "%");
        percent.setMinWidth(Region.USE_PREF_SIZE);
        percent.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-font-weight: 700;");
        HBox progressLine = new HBox(10, progressTrack, percent);
        progressLine.setAlignment(Pos.CENTER_LEFT);

        HBox metrics = new HBox(0);
        metrics.setAlignment(Pos.CENTER);
        metrics.setStyle("-fx-background-color: #fafbff; -fx-background-radius: 12; -fx-border-color: #edf2f7; -fx-border-radius: 12;");
        metrics.setPadding(new Insets(12, 8, 12, 8));
        Node recruitedMetric = iconMetricCell(FontAwesomeSolid.USERS, "Recruited", filled + " / " + total, "#111827");
        Node workloadMetric = iconMetricCell(FontAwesomeSolid.CLOCK, "Workload", safe(module.getWorkload()), "#111827");
        String unprocessedColor = unprocessed > 0 ? "#dc2626" : "#111827";
        Node unprocessedMetric = iconMetricCell(FontAwesomeSolid.FILE_ALT, "Unprocessed applications",
                String.valueOf(unprocessed), unprocessedColor);
        metrics.getChildren().addAll(recruitedMetric, metricDivider(), workloadMetric, metricDivider(), unprocessedMetric);
        for (Node n : metrics.getChildren()) {
            if (n instanceof Region) {
                continue;
            }
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editBtn = outlineActionButton(FontAwesomeSolid.EDIT, "Edit");
        editBtn.setOnAction(e -> showModuleDialog(module));

        Button appBtn = outlineActionButton(FontAwesomeSolid.CHART_BAR, "Application Status");
        appBtn.setOnAction(e -> showApplicantsDialog(module));

        actions.getChildren().addAll(spacer, editBtn, appBtn);

        card.getChildren().addAll(header, progressLine, metrics, actions);
        return card;
    }

    private static Region metricDivider() {
        Region divider = new Region();
        divider.setPrefWidth(1);
        divider.setMinWidth(1);
        divider.setMaxWidth(1);
        divider.setPrefHeight(44);
        divider.setStyle("-fx-background-color: #e2e8f0;");
        return divider;
    }

    private Node iconMetricCell(FontAwesomeSolid glyph, String label, String value, String valueColor) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(0, 10, 0, 10));
        HBox.setHgrow(box, Priority.ALWAYS);

        HBox labelRow = new HBox(6);
        labelRow.setAlignment(Pos.CENTER);
        Label iconLabel = new Label();
        iconLabel.setGraphic(icon(glyph, 13, "#94a3b8"));
        Label l = new Label(label);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px; -fx-font-weight: 700;");
        labelRow.getChildren().addAll(iconLabel, l);

        Label v = new Label(value == null || value.isBlank() ? "-" : value);
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-size: 14px; -fx-font-weight: 800;");
        box.getChildren().addAll(labelRow, v);
        return box;
    }

    private Button outlineActionButton(FontAwesomeSolid glyph, String text) {
        Button button = new Button(text);
        button.setGraphic(icon(glyph, 14, "#7c3aed"));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
        stylePurpleButton(button);
        return button;
    }

    private FontAwesomeSolid moduleGlyphFor(ModulePosting posting) {
        String key = ((posting.getModuleCode() == null ? "" : posting.getModuleCode()) + " "
                + (posting.getModuleName() == null ? "" : posting.getModuleName())).toLowerCase(Locale.ROOT);
        if (key.contains("embedded")) return FontAwesomeSolid.MICROCHIP;
        if (key.contains("network")) return FontAwesomeSolid.NETWORK_WIRED;
        if (key.contains("web")) return FontAwesomeSolid.GLOBE;
        if (key.contains("software") || key.contains("program") || key.contains("engineering")) return FontAwesomeSolid.CODE;
        if (key.contains("operating") || key.contains("system")) return FontAwesomeSolid.DESKTOP;
        if (key.contains("middleware")) return FontAwesomeSolid.LAYER_GROUP;
        if (key.contains("database")) return FontAwesomeSolid.DATABASE;
        if (key.contains("math") || key.contains("algorithm") || key.contains("structure")) return FontAwesomeSolid.CALCULATOR;
        return FontAwesomeSolid.BOOK_OPEN;
    }

    private String[] moduleIconColors(ModulePosting posting) {
        int bucket = Math.abs((posting.getModuleCode() == null ? "" : posting.getModuleCode().toUpperCase(Locale.ROOT)).hashCode()) % 6;
        return switch (bucket) {
            case 0 -> new String[] {"#4f46e5", "#eef2ff"};
            case 1 -> new String[] {"#059669", "#eafaf2"};
            case 2 -> new String[] {"#0284c7", "#eaf6fe"};
            case 3 -> new String[] {"#d97706", "#fff8eb"};
            case 4 -> new String[] {"#7c3aed", "#f5f0ff"};
            default -> new String[] {"#0f766e", "#edfdf9"};
        };
    }

    private void refreshMoViews() {
        refreshModules();
        if ("applications".equals(activePage)) {
            refreshApplicationsPage();
        }
    }

    private void refreshStats(List<ModulePosting> modules) {
        int total = modules.size();
        int open = 0;
        int finished = 0;
        int pending = 0;
        for (ModulePosting m : modules) {
            String status = moduleDisplayStatus(m);
            if ("OPEN".equals(status)) open++;
            if ("FINISHED".equals(status)) finished++;
            pending += moService.countPendingForModule(m.getModuleId());
        }
        totalModulesValue.setText(String.valueOf(total));
        openModulesValue.setText(String.valueOf(open));
        finishedModulesValue.setText(String.valueOf(finished));
        pendingApplicationsValue.setText(String.valueOf(pending));
    }

    // ======================= APPLICANT REVIEW =======================

    private void showApplicantsDialog(ModulePosting module) {
        List<ApplicantRow> rows = moService.getApplicantsForModule(module.getModuleId());
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Applications - " + module.getModuleCode());
        dialog.setResizable(true);
        dialog.setWidth(980);
        dialog.setHeight(700);

        VBox dialogContent = new VBox(14);
        dialogContent.setPadding(new Insets(16));
        dialogContent.setStyle("-fx-background-color: #f4f7fb;");

        Label title = new Label("Applicants - " + module.getModuleCode());
        title.setStyle("-fx-text-fill: #111827; -fx-font-size: 20px; -fx-font-weight: 800;");
        long submitted = rows.stream().filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED).count();
        Label summary = new Label(rows.size() + " total, " + submitted + " pending");
        summary.setStyle("-fx-text-fill: #7c3aed; -fx-font-size: 13px;");

        VBox listContainer = new VBox(10);
        if (rows.isEmpty()) {
            listContainer.getChildren().add(emptyState("No applications yet."));
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            scroll.setStyle("-fx-background-color: transparent;");
            VBox list = new VBox(10);
            for (ApplicantRow row : rows) {
                list.getChildren().add(buildApplicantCard(module, row));
            }
            scroll.setContent(list);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            listContainer.getChildren().add(scroll);
        }

        HBox buttonBar = new HBox(8);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));
        Button closeBtn = new Button("Close");
        styleGhostButton(closeBtn);
        closeBtn.setStyle(closeBtn.getStyle() + " -fx-background-color: #e2e8f0; -fx-text-fill: #334155;");
        closeBtn.setOnAction(e -> dialog.close());
        buttonBar.getChildren().add(closeBtn);

        dialogContent.getChildren().addAll(title, summary, listContainer, buttonBar);
        Scene scene = new Scene(dialogContent);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private Node buildApplicantCard(ModulePosting module, ApplicantRow row) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.TOP_LEFT);
        StackPane avatar = new StackPane(icon(FontAwesomeSolid.USER, 14, "#6d28d9"));
        avatar.setPrefSize(36, 36);
        avatar.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 18;");

        VBox info = new VBox(4);
        Label name = new Label(row.getTaName());
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 16px; -fx-font-weight: 800;");
        Label meta = new Label(row.getTaUserId());
        meta.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        info.getChildren().addAll(name, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox rightBox = new VBox(6);
        rightBox.setAlignment(Pos.TOP_RIGHT);
        Label statusBadge = new Label(row.getStatus().name());
        String statusColor = applicationStatusColor(row.getStatus().name());
        statusBadge.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-size: 11px; -fx-font-weight: 800; -fx-background-color: " + tint(statusColor) + "; -fx-background-radius: 999; -fx-padding: 4 10 4 10;");
        Label cvLabel = new Label(cvName(row.getCvFilePath()));
        cvLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: 700;");
        rightBox.getChildren().addAll(statusBadge, cvLabel);
        header.getChildren().addAll(avatar, info, rightBox);

        HBox details = new HBox(12,
                chip("Email", safe(row.getTaEmail())),
                chip("Phone", safe(row.getTaPhone())),
                chip("Skills", row.getSkills() == null || row.getSkills().isEmpty() ? "-" : String.join(", ", row.getSkills())));
        for (Node n : details.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button openCv = new Button("Open CV");
        styleGhostButton(openCv);
        openCv.setDisable(row.getCvFilePath() == null || row.getCvFilePath().isBlank());
        openCv.setOnAction(e -> openCv(row.getCvFilePath()));

        boolean canDecide = row.getStatus() == ApplicationStatus.SUBMITTED;
        Button acceptBtn = new Button("Accept");
        stylePrimaryButton(acceptBtn);
        acceptBtn.setDisable(!canDecide);
        acceptBtn.setOnAction(e -> {
            MOService.MOActionResult res = moService.acceptApplication(row.getApplicationId(), currentUser.getQmId());
            showAlert(res.isSuccess() ? "Success" : "Failed", res.getMessage());
            refreshMoViews();
            showApplicantsDialog(module);
        });
        Button rejectBtn = new Button("Reject");
        styleGhostButton(rejectBtn);
        rejectBtn.setDisable(!canDecide);
        rejectBtn.setOnAction(e -> {
            MOService.MOActionResult res = moService.rejectApplication(row.getApplicationId(), currentUser.getQmId());
            showAlert(res.isSuccess() ? "Success" : "Failed", res.getMessage());
            refreshMoViews();
            showApplicantsDialog(module);
        });
        actions.getChildren().addAll(spacer, openCv, rejectBtn, acceptBtn);
        card.getChildren().addAll(header, details, actions);
        return card;
    }

    // ======================= APPLICATION HISTORY =======================

    private Node buildHistoryCard(HistoryRecord rec) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e7edf4; -fx-border-radius: 12;");
        card.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(icon(FontAwesomeSolid.CHECK_CIRCLE, 16,
                rec.status == ApplicationStatus.ACCEPTED ? "#2563eb" : "#dc2626"));
        icon.setPrefSize(40, 40);
        icon.setStyle("-fx-background-color: " + tint(rec.status == ApplicationStatus.ACCEPTED ? "#2563eb" : "#dc2626") + "; -fx-background-radius: 20;");

        VBox info = new VBox(4);
        Label name = new Label(rec.taName + " (" + rec.taId + ")");
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 14px; -fx-font-weight: 800;");
        Label moduleLabel = new Label(rec.moduleCode + " - " + rec.moduleName);
        moduleLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Label time = new Label("Decided: " + (rec.decisionTime != null ? rec.decisionTime : "N/A"));
        time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        info.getChildren().addAll(name, moduleLabel, time);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label statusLabel = new Label(rec.status.name());
        statusLabel.setStyle("-fx-text-fill: " + (rec.status == ApplicationStatus.ACCEPTED ? "#2563eb" : "#dc2626") + "; -fx-font-size: 12px; -fx-font-weight: 800;");
        card.getChildren().addAll(icon, info, statusLabel);
        return card;
    }

    private static class HistoryRecord {
        final String taId;
        final String taName;
        final String moduleCode;
        final String moduleName;
        final ApplicationStatus status;
        final String decisionTime;

        HistoryRecord(ApplicantRow row, String moduleCode, String moduleName) {
            this.taId = row.getTaUserId();
            this.taName = row.getTaName();
            this.moduleCode = moduleCode;
            this.moduleName = moduleName;
            this.status = row.getStatus();
            this.decisionTime = row.getCreatedAt(); // placeholder, can be improved
        }
    }

    // ======================= UTILITIES =======================

    private void openCv(String cvPath) {
        if (cvPath == null || cvPath.isBlank()) {
            showAlert("CV", "No CV file available.");
            return;
        }
        Path resolved = DataFileOpen.resolveUnderData(cvPath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            showAlert("CV", "CV file not found.");
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(resolved.toFile());
        } catch (IOException ex) {
            showAlert("CV", "Could not open CV: " + ex.getMessage());
        }
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private String cvName(String path) {
        if (path == null || path.isBlank()) return "-";
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private Node emptyState(String text) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        box.getChildren().add(label);
        return box;
    }

    private String moduleDisplayStatus(ModulePosting m) {
        if (m == null) return "UNKNOWN";
        int total = Math.max(1, m.getVacanciesTotal());
        if (m.getStatus() == ModuleStatus.CLOSED) return "CLOSED";
        if (m.getVacanciesFilled() >= total) return "FINISHED";
        return "OPEN";
    }

    private String statusColor(String status) {
        if ("FINISHED".equals(status)) return "#2563eb";
        if ("CLOSED".equals(status)) return "#6d28d9";
        if ("OPEN".equals(status)) return "#16a34a";
        return "#64748b";
    }

    private String tint(String color) {
        if ("#6d28d9".equals(color)) return "#f3ebff";
        if ("#16a34a".equals(color)) return "#ecfdf3";
        if ("#2563eb".equals(color)) return "#eff6ff";
        if ("#dc2626".equals(color)) return "#fef2f2";
        return "#f3f4f6";
    }

    private String applicationStatusColor(String status) {
        if ("SUBMITTED".equals(status)) return "#7c3aed";
        if ("ACCEPTED".equals(status)) return "#2563eb";
        if ("REJECTED".equals(status)) return "#dc2626";
        return "#64748b";
    }

    private static FontIcon icon(FontAwesomeSolid glyph, int size, String color) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(size);
        icon.setIconColor(Color.web(color));
        return icon;
    }

    private void stylePrimaryButton(Button button) {
        button.setStyle("-fx-background-color: #6d28d9; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 8; -fx-padding: 8 14 8 14;");
    }

    private void stylePurpleButton(Button button) {
        button.setStyle("-fx-background-color: white; -fx-border-color: #7c3aed; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #7c3aed; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
    }

    private void styleGhostButton(Button button) {
        button.setStyle("-fx-background-color: white; -fx-border-color: #d7dfea; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #334155; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
    }

    private String panelStyle() {
        return "-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);";
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showDialog(String title, VBox content, double width, double height) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.setWidth(width);
        stage.setHeight(height);
        Scene scene = new Scene(content);
        stage.setScene(scene);
        stage.showAndWait();
    }

    // Form field helpers
    private VBox formField(String label, Node field) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(0, 0, 8, 0));
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 13px; -fx-font-weight: 700;");
        box.getChildren().addAll(lbl, field);
        return box;
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8;");
        return tf;
    }

    private TextArea createStyledTextArea(String prompt, int rows) {
        TextArea ta = new TextArea();
        ta.setPromptText(prompt);
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        ta.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8;");
        return ta;
    }

    private VBox chip(String label, String value) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: 700;");
        Label v = new Label(value == null || value.isBlank() ? "-" : value);
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: 700;");
        box.getChildren().addAll(l, v);
        return box;
    }
}