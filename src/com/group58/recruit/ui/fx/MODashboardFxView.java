package com.group58.recruit.ui.fx;

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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
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
    private final Label sidebarUserName = new Label();
    private final Label sidebarUserRole = new Label("Module Owner");
    private final GridPane moduleGrid = new GridPane();
    private ScrollPane moduleScroll;
    private final Label totalModulesValue = new Label("0");
    private final Label openModulesValue = new Label("0");
    private final Label finishedModulesValue = new Label("0");
    private final Label pendingApplicationsValue = new Label("0");

    // Sidebar navigation buttons
    private final Button dashboardBtn = new Button("Dashboard");
    private final Button modulesBtn = new Button("Modules");
    private final Button applicationsBtn = new Button("Applications");
    private final Button logoutBtn = new Button("Logout");

    public MODashboardFxView(Runnable logoutAction) {
        // Force English locale for all built-in dialogs (e.g., Alert buttons)
        Locale.setDefault(Locale.ENGLISH);

        this.logoutAction = logoutAction == null ? () -> {} : logoutAction;
        setStyle("-fx-background-color: linear-gradient(to bottom, #f5efff, #f4f7fb);");
        buildSidebar();
        setTop(buildTopBar());
        setCenter(buildContent());
    }

    // ======================= SIDEBAR =======================

    private void buildSidebar() {
        VBox sidebarContent = new VBox(16);
        sidebarContent.setPadding(new Insets(28, 16, 24, 16));
        sidebarContent.setStyle("-fx-background-color: white; -fx-border-color: #e7edf4; -fx-border-width: 0 1 0 0;");
        sidebarContent.setPrefWidth(260);
        sidebarContent.setMinWidth(260);
        sidebarContent.setMaxWidth(260);

        // User info section
        StackPane avatar = new StackPane(icon(FontAwesomeSolid.USER_CIRCLE, 32, "#6d28d9"));
        avatar.setPrefSize(64, 64);
        avatar.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 32;");

        sidebarUserName.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 16px; -fx-font-weight: 800;");
        sidebarUserName.setText("MO");
        sidebarUserRole.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        VBox userInfo = new VBox(4, sidebarUserName, sidebarUserRole);
        userInfo.setAlignment(Pos.CENTER);

        VBox userSection = new VBox(12, avatar, userInfo);
        userSection.setAlignment(Pos.CENTER);

        // Navigation menu
        VBox navMenu = new VBox(6);
        navMenu.setPadding(new Insets(16, 0, 0, 0));
        styleNavButton(dashboardBtn, FontAwesomeSolid.TACHOMETER_ALT, true);
        styleNavButton(modulesBtn, FontAwesomeSolid.CUBES, false);
        styleNavButton(applicationsBtn, FontAwesomeSolid.HISTORY, false);
        styleNavButton(logoutBtn, FontAwesomeSolid.SIGN_OUT_ALT, false);
        logoutBtn.setStyle(logoutBtn.getStyle() + " -fx-text-fill: #dc2626; -fx-font-weight: 800;");

        dashboardBtn.setOnAction(e -> {
            setActiveNav(dashboardBtn);
            refreshModules();
        });
        modulesBtn.setOnAction(e -> {
            setActiveNav(modulesBtn);
            refreshModules();
        });
        applicationsBtn.setOnAction(e -> showApplicationHistory());
        logoutBtn.setOnAction(e -> logoutAction.run());

        navMenu.getChildren().addAll(dashboardBtn, modulesBtn, applicationsBtn);

        // Spacer to push logout to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebarContent.getChildren().addAll(userSection, navMenu, spacer, logoutBtn);

        // Wrap sidebar in ScrollPane to enable scrolling when window is short
        ScrollPane sideScroll = new ScrollPane(sidebarContent);
        sideScroll.setFitToWidth(true);
        sideScroll.setFitToHeight(true);   // Allow content to fill height
        sideScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        sideScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        sideScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        setLeft(sideScroll);
    }

    private void styleNavButton(Button btn, FontAwesomeSolid icon, boolean active) {
        FontIcon graphic = icon(icon, 18, active ? "#6d28d9" : "#6b7280");
        btn.setGraphic(graphic);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(12);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(44);
        btn.setPadding(new Insets(0, 16, 0, 16));
        if (active) {
            btn.setStyle("-fx-background-color: #f3ebff; -fx-text-fill: #6d28d9; -fx-font-weight: 700; -fx-background-radius: 10; -fx-border-radius: 10;");
        } else {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4b5563; -fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
        btn.setOnMouseEntered(e -> {
            if (!btn.getStyle().contains("#f3ebff")) {
                btn.setStyle("-fx-background-color: #f9f5ff; -fx-text-fill: #6d28d9; -fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
            }
        });
        btn.setOnMouseExited(e -> {
            if (!btn.getStyle().contains("#f3ebff")) {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4b5563; -fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
            }
        });
    }

    private void setActiveNav(Button activeBtn) {
        for (Button btn : new Button[]{dashboardBtn, modulesBtn, applicationsBtn}) {
            boolean isActive = btn == activeBtn;
            FontIcon graphic = (FontIcon) btn.getGraphic();
            if (graphic != null) {
                graphic.setIconColor(Color.web(isActive ? "#6d28d9" : "#6b7280"));
            }
            if (isActive) {
                btn.setStyle("-fx-background-color: #f3ebff; -fx-text-fill: #6d28d9; -fx-font-weight: 700; -fx-background-radius: 10; -fx-border-radius: 10;");
            } else {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4b5563; -fx-font-weight: 600; -fx-background-radius: 10; -fx-border-radius: 10;");
            }
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

        Button newModuleBtn = new Button("+ New Module");
        stylePrimaryButton(newModuleBtn);
        newModuleBtn.setOnAction(e -> showModuleDialog(null));
        bar.getChildren().addAll(avatar, userLabel, spacer, newModuleBtn);
        return bar;
    }

    // ======================= MAIN CONTENT =======================

    private Node buildContent() {
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

        moduleGrid.setHgap(18);
        moduleGrid.setVgap(18);
        moduleGrid.setPadding(new Insets(2));
        moduleGrid.widthProperty().addListener((obs, oldV, newV) -> refreshModules());
        moduleGrid.setMaxWidth(Double.MAX_VALUE);
        moduleGrid.setMinWidth(0);
        moduleGrid.setAlignment(Pos.TOP_CENTER);

        // 关键：ScrollPane 必须能自由扩张
        moduleScroll = new ScrollPane(moduleGrid);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        moduleScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        moduleScroll.setPannable(true);
        moduleScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        // 移除 minHeight 限制，允许 ScrollPane 自适应填满剩余空间
        moduleScroll.setMinHeight(0);
        VBox.setVgrow(moduleScroll, Priority.ALWAYS);

        modulesPanel.getChildren().addAll(title, moduleScroll);
        root.getChildren().addAll(statsRow, modulesPanel);
        VBox.setVgrow(root, Priority.ALWAYS);
        return root;
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
            if (actionResult.isSuccess()) refreshModules();
        });
    }

    private void closeModule(ModulePosting module) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Close");
        confirm.setHeaderText("Close module: " + module.getModuleCode());
        confirm.setContentText("Are you sure you want to close this module? TA applications will no longer be accepted.");
        Optional<ButtonType> response = confirm.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            MOService.MOActionResult result = moService.closeModule(module.getModuleId(), currentUser.getQmId());
            showAlert(result.isSuccess() ? "Success" : "Failed", result.getMessage());
            if (result.isSuccess()) refreshModules();
        }
    }

    // ======================= MODULE LIST & STATS =======================

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.MO) {
            currentUser = null;
            userLabel.setText("MO: -");
            sidebarUserName.setText("Not logged in");
            moduleGrid.getChildren().setAll(emptyState("Please login as MO first."));
            return;
        }
        currentUser = user;
        String displayName = user.getName() == null || user.getName().isBlank() ? user.getQmId() : user.getName();
        userLabel.setText("MO: " + displayName + " (" + user.getQmId() + ")");
        sidebarUserName.setText(displayName);
        refreshModules();
    }

    private void refreshModules() {
        if (currentUser == null) {
            moduleGrid.getChildren().setAll(emptyState("Please login as MO first."));
            return;
        }

        List<ModulePosting> modules = moService.getMyModules(currentUser.getQmId());
        refreshStats(modules);
        moduleGrid.getChildren().clear();

        if (modules.isEmpty()) {
            moduleGrid.add(emptyState("No module postings found."), 0, 0);
            return;
        }

        double viewportWidth = moduleScroll == null ? 1200 : moduleScroll.getViewportBounds().getWidth();
        int cols = viewportWidth < 1120 ? 1 : 2;
        moduleGrid.setPrefWidth(cols == 1 ? 1080 : 1520);

        for (int i = 0; i < modules.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            Node card = buildModuleCard(modules.get(i));
            moduleGrid.add(card, col, row);
        }
    }

    private Node buildModuleCard(ModulePosting module) {
        int total = Math.max(1, module.getVacanciesTotal());
        int filled = Math.min(module.getVacanciesFilled(), total);
        boolean finished = filled >= total || module.getStatus() == ModuleStatus.FINISHED || module.getStatus() == ModuleStatus.CLOSED;
        double progress = finished ? 1.0 : (double) filled / total;
        String statusText = moduleDisplayStatus(module);
        String statusColor = statusColor(statusText);

        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setMinHeight(340);
        card.setPrefHeight(340);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.15, 0, 3);");

        HBox top = new HBox(12);
        top.setAlignment(Pos.TOP_LEFT);
        StackPane iconBg = new StackPane(icon(FontAwesomeSolid.MICROCHIP, 18, "#6d28d9"));
        iconBg.setPrefSize(52, 52);
        iconBg.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 29;");

        VBox titleBox = new VBox(6);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        Label code = new Label(module.getModuleCode() + " - " + module.getModuleName());
        code.setStyle("-fx-text-fill: #111827; -fx-font-size: 22px; -fx-font-weight: 800;");
        code.setWrapText(true);
        HBox.setHgrow(code, Priority.ALWAYS);
        Label badge = new Label(statusText);
        badge.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-size: 11px; -fx-font-weight: 800; -fx-background-color: " + tint(statusColor) + "; -fx-background-radius: 999; -fx-padding: 4 10 4 10;");
        titleRow.getChildren().addAll(code, badge);
        Label recruited = new Label(filled + " / " + total + " recruited");
        recruited.setStyle("-fx-text-fill: #111827; -fx-font-size: 14px; -fx-font-weight: 700;");
        titleBox.getChildren().addAll(titleRow, recruited);
        top.getChildren().addAll(iconBg, titleBox);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Progress bar
        HBox progressTrack = new HBox();
        progressTrack.setPrefHeight(10);
        progressTrack.setStyle("-fx-background-color: #e8edf5; -fx-background-radius: 999;");
        Region fill = new Region();
        fill.setPrefHeight(10);
        fill.setStyle("-fx-background-color: " + (finished ? "#2563eb" : "#16a34a") + "; -fx-background-radius: 999;");
        fill.prefWidthProperty().bind(progressTrack.widthProperty().multiply(progress));
        progressTrack.getChildren().add(fill);
        HBox.setHgrow(progressTrack, Priority.ALWAYS);
        Label percent = new Label((int) Math.round(progress * 100) + "%");
        percent.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-font-weight: 700;");
        HBox progressLine = new HBox(8, progressTrack, percent);
        progressLine.setAlignment(Pos.CENTER_LEFT);

        // Metrics
        HBox metrics = new HBox(10,
                metricCell("Workload", safe(module.getWorkload())),
                metricCell("Unprocessed", String.valueOf(moService.countPendingForModule(module.getModuleId()))));
        for (Node n : metrics.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        // Actions
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.BOTTOM_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editBtn = new Button("Edit");
        stylePurpleButton(editBtn);
        editBtn.setOnAction(e -> showModuleDialog(module));

        Button closeBtn = new Button("Close");
        styleGhostButton(closeBtn);
        closeBtn.setVisible(module.getStatus() == ModuleStatus.OPEN);
        closeBtn.setOnAction(e -> closeModule(module));

        Button appBtn = new Button("Applications");
        stylePrimaryButton(appBtn);
        appBtn.setOnAction(e -> showApplicantsDialog(module));

        actions.getChildren().addAll(spacer, editBtn, closeBtn, appBtn);

        VBox body = new VBox(10, top, progressLine, metrics);
        VBox.setVgrow(body, Priority.ALWAYS);
        card.getChildren().addAll(body, actions);
        return card;
    }

    private Node metricCell(String label, String value) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #fafbff; -fx-background-radius: 10; -fx-border-color: #edf2f7; -fx-border-radius: 10;");
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px; -fx-font-weight: 700;");
        Label v = new Label(value == null || value.isBlank() ? "-" : value);
        v.setStyle("-fx-text-fill: #111827; -fx-font-size: 13px; -fx-font-weight: 800;");
        box.getChildren().addAll(l, v);
        return box;
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
            refreshModules();
            showApplicantsDialog(module);
        });
        Button rejectBtn = new Button("Reject");
        styleGhostButton(rejectBtn);
        rejectBtn.setDisable(!canDecide);
        rejectBtn.setOnAction(e -> {
            MOService.MOActionResult res = moService.rejectApplication(row.getApplicationId(), currentUser.getQmId());
            showAlert(res.isSuccess() ? "Success" : "Failed", res.getMessage());
            refreshModules();
            showApplicantsDialog(module);
        });
        actions.getChildren().addAll(spacer, openCv, rejectBtn, acceptBtn);
        card.getChildren().addAll(header, details, actions);
        return card;
    }

    // ======================= APPLICATION HISTORY =======================

    private void showApplicationHistory() {
        if (currentUser == null) {
            showAlert("Error", "Please login as MO first.");
            return;
        }
        List<ModulePosting> modules = moService.getMyModules(currentUser.getQmId());
        List<HistoryRecord> history = new ArrayList<>();
        for (ModulePosting m : modules) {
            List<ApplicantRow> rows = moService.getApplicantsForModule(m.getModuleId());
            for (ApplicantRow row : rows) {
                if (row.getStatus() != ApplicationStatus.SUBMITTED) {
                    history.add(new HistoryRecord(row, m.getModuleCode(), m.getModuleName()));
                }
            }
        }

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #f4f7fb;");

        Label title = new Label("Application History");
        title.setStyle("-fx-text-fill: #111827; -fx-font-size: 20px; -fx-font-weight: 800;");
        if (history.isEmpty()) {
            content.getChildren().addAll(title, emptyState("No processed applications yet."));
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            VBox list = new VBox(8);
            for (HistoryRecord rec : history) {
                list.getChildren().add(buildHistoryCard(rec));
            }
            scroll.setContent(list);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            content.getChildren().addAll(title, scroll);
        }
        showDialog("History", content, 800, 600);
    }

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