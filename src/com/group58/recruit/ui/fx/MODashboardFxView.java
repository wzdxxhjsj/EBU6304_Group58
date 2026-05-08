package com.group58.recruit.ui.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class MODashboardFxView extends BorderPane {
    private final MOService moService = new MOService();
    private final Runnable logoutAction;

    private User currentUser;
    private final Label userLabel = new Label("MO: -");
    private final GridPane moduleGrid = new GridPane();
    private ScrollPane moduleScroll;
    private final TextField searchField = new TextField();
    private final ChoiceBox<String> statusFilter = new ChoiceBox<>();
    private final Label totalModulesValue = new Label("0");
    private final Label openModulesValue = new Label("0");
    private final Label finishedModulesValue = new Label("0");
    private final Label pendingApplicationsValue = new Label("0");

    public MODashboardFxView(Runnable logoutAction) {
        this.logoutAction = logoutAction == null ? () -> {
        } : logoutAction;
        setStyle("-fx-background-color: linear-gradient(to bottom, #f5efff, #f4f7fb);");
        setTop(buildTopBar());
        setCenter(buildContent());
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.MO) {
            currentUser = null;
            userLabel.setText("MO: -");
            moduleGrid.getChildren().setAll(emptyState("Please login as MO first."));
            return;
        }
        currentUser = user;
        String displayName = user.getName() == null || user.getName().isBlank() ? user.getQmId() : user.getName();
        userLabel.setText("MO: " + displayName + " (" + user.getQmId() + ")");
        refreshModules();
    }

    private Node buildTopBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(16));
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
        newModuleBtn.setOnAction(e -> showInfo("New Module", "Module creation is available in the Swing dashboard."));

        Button logoutBtn = new Button("Logout");
        styleGhostButton(logoutBtn);
        logoutBtn.setOnAction(e -> logoutAction.run());

        bar.getChildren().addAll(avatar, userLabel, spacer, newModuleBtn, logoutBtn);
        return bar;
    }

    private Node buildContent() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.TOP_LEFT);

        HBox statsRow = new HBox(12,
                buildStatCard("Total Modules", totalModulesValue, "#6d28d9", FontAwesomeSolid.BOOK),
                buildStatCard("Open", openModulesValue, "#16a34a", FontAwesomeSolid.DOT_CIRCLE),
                buildStatCard("Finished", finishedModulesValue, "#2563eb", FontAwesomeSolid.CHECK_CIRCLE),
                buildStatCard("Unprocessed Applications", pendingApplicationsValue, "#dc2626",
                        FontAwesomeSolid.FILE_ALT));
        for (Node n : statsRow.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        VBox modulesPanel = new VBox(14);
        modulesPanel.setPadding(new Insets(16));
        modulesPanel.setStyle(panelStyle());
        modulesPanel.setMinHeight(820);
        VBox.setVgrow(modulesPanel, Priority.ALWAYS);

        Label title = new Label("My Modules");
        title.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 18px; -fx-font-weight: 800;");

        HBox filters = new HBox(8);

        moduleGrid.setHgap(18);
        moduleGrid.setVgap(18);
        moduleGrid.setPadding(new Insets(2));
        moduleGrid.widthProperty().addListener((obs, oldV, newV) -> refreshModules());
        moduleGrid.setMaxWidth(Double.MAX_VALUE);
        moduleGrid.setMinWidth(0);
        moduleGrid.setAlignment(Pos.TOP_CENTER);

        moduleScroll = new ScrollPane(moduleGrid);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        moduleScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        moduleScroll.setPannable(true);
        moduleScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        moduleScroll.setMinHeight(600);
        VBox.setVgrow(moduleScroll, Priority.ALWAYS);

        modulesPanel.getChildren().addAll(title, filters, moduleScroll);
        root.getChildren().addAll(statsRow, modulesPanel);
        VBox.setVgrow(root, Priority.ALWAYS);
        return root;
    }

    private Node buildStatCard(String title, Label valueLabel, String accentColor, FontAwesomeSolid glyph) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setPrefHeight(108);
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);");

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

    private void refreshModules() {
        if (currentUser == null) {
            moduleGrid.getChildren().setAll(emptyState("Please login as MO first."));
            return;
        }

        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = statusFilter.getValue();
        List<ModulePosting> modules = new ArrayList<>();
        for (ModulePosting module : moService.getMyModules(currentUser.getQmId())) {
            String combined = ((module.getModuleCode() == null ? "" : module.getModuleCode()) + " "
                    + (module.getModuleName() == null ? "" : module.getModuleName())).toLowerCase(Locale.ROOT);
            boolean matchesQuery = query.isBlank() || combined.contains(query);
            String displayStatus = moduleDisplayStatus(module);
            boolean matchesFilter = filter == null || "All".equals(filter)
                    || filter.equalsIgnoreCase(displayStatus);
            if (matchesQuery && matchesFilter) {
                modules.add(module);
            }
        }

        refreshStats(modules);
        moduleGrid.getChildren().clear();

        if (modules.isEmpty()) {
            moduleGrid.add(emptyState("No module postings found."), 0, 0);
            return;
        }

        double viewportWidth = moduleScroll == null ? 1200 : moduleScroll.getViewportBounds().getWidth();
        if (viewportWidth <= 0 && moduleScroll != null) {
            viewportWidth = moduleScroll.getWidth();
        }
        int cols = viewportWidth < 1120 ? 1 : 2;
        moduleGrid.setPrefWidth(cols == 1 ? 1080 : 1520);

        for (int i = 0; i < modules.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            Node card = buildModuleCard(modules.get(i), cols);
            moduleGrid.add(card, col, row);
        }
    }

    private Node buildModuleCard(ModulePosting module, int cols) {
        int total = Math.max(1, module.getVacanciesTotal());
        int filled = Math.min(module.getVacanciesFilled(), total);
        boolean finished = filled >= total || ModuleStatus.CLOSED.equals(module.getStatus())
                || ModuleStatus.FINISHED.equals(module.getStatus());
        double progress = finished ? 1.0 : Math.max(0.0, Math.min(1.0, filled / (double) total));
        String status = moduleDisplayStatus(module);

        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setMinHeight(320);
        card.setPrefHeight(320);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(cols == 1 ? 860 : 620);
        card.setPrefWidth(cols == 1 ? 980 : 740);
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.15, 0, 3);");

        HBox top = new HBox(12);
        top.setAlignment(Pos.TOP_LEFT);
        StackPane iconBg = new StackPane(icon(FontAwesomeSolid.MICROCHIP, 18, "#6d28d9"));
        iconBg.setPrefSize(52, 52);
        iconBg.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 29;");

        VBox titleBox = new VBox(8);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        Label code = new Label(safe(module.getModuleCode()) + " - " + safe(module.getModuleName()));
        code.setStyle("-fx-text-fill: #111827; -fx-font-size: 24px; -fx-font-weight: 800;");
        code.setWrapText(true);
        code.setMinWidth(0);
        code.setMaxWidth(cols == 1 ? 620 : 360);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String statusColor = statusColor(status);
        Label badge = new Label(status);
        badge.setMinWidth(68);
        badge.setPrefHeight(22);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-text-fill: " + statusColor
                + "; -fx-font-size: 11px; -fx-font-weight: 800; -fx-background-color: " + tint(statusColor)
                + "; -fx-background-radius: 999; -fx-padding: 4 10 4 10;");

        titleRow.getChildren().addAll(code, spacer, badge);
        HBox.setHgrow(code, Priority.ALWAYS);
        Region titleGap = new Region();
        titleGap.setPrefHeight(8);
        Label recruited = new Label(filled + " / " + total + " recruited");
        recruited.setStyle("-fx-text-fill: #111827; -fx-font-size: 16px; -fx-font-weight: 800;");
        titleBox.getChildren().addAll(titleRow, titleGap, recruited);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        top.getChildren().addAll(iconBg, titleBox);

        VBox progressWrap = new VBox(6);
        progressWrap.setMaxWidth(Double.MAX_VALUE);
        HBox progressLine = new HBox(8);
        progressLine.setAlignment(Pos.CENTER_LEFT);
        progressLine.setMaxWidth(Double.MAX_VALUE);
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);
        divider.setStyle("-fx-background-color: #e5e7eb;");
        HBox progressTrack = new HBox();
        progressTrack.setPrefHeight(10);
        progressTrack.setMinHeight(10);
        progressTrack.setStyle("-fx-background-color: #e8edf5; -fx-background-radius: 999;");
        Region fill = new Region();
        fill.setPrefHeight(10);
        fill.setMinHeight(10);
        fill.setStyle("-fx-background-color: " + (isModuleFinished(module) ? "#2563eb" : "#16a34a")
                + "; -fx-background-radius: 999;");
        double percent = Math.max(0.0, Math.min(1.0, progress));
        fill.prefWidthProperty().bind(progressTrack.widthProperty().multiply(percent));
        progressTrack.getChildren().add(fill);
        HBox.setHgrow(progressTrack, Priority.ALWAYS);
        Label pct = new Label((int) Math.round(progress * 100) + "%");
        pct.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-font-weight: 700;");
        progressLine.getChildren().addAll(progressTrack, pct);
        progressWrap.getChildren().addAll(progressLine, divider);

        HBox metrics = new HBox(10,
                metricCell("Recruited", filled + " / " + total),
                metricCell("Workload", safe(module.getWorkload())),
                metricCell("Unprocessed applications",
                        String.valueOf(moService.countPendingForModule(module.getModuleId()))));
        metrics.setMinHeight(54);
        metrics.setFillHeight(true);
        for (Node n : metrics.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.BOTTOM_RIGHT);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        Button edit = new Button("Edit");
        stylePurpleButton(edit);
        edit.setGraphic(icon(FontAwesomeSolid.PENCIL_ALT, 12, "#7c3aed"));
        edit.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        edit.setGraphicTextGap(8);
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        edit.setOnAction(e -> showInfo("Edit", "Module editing is available in the Swing dashboard."));

        Button app = new Button("Application Status");
        stylePurpleButton(app);
        app.setGraphic(icon(FontAwesomeSolid.FOLDER_OPEN, 12, "#7c3aed"));
        app.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        app.setGraphicTextGap(8);
        app.setOnAction(e -> showApplicantsDialog(module));
        actions.getChildren().addAll(actionSpacer, edit, app);

        VBox body = new VBox(10, top, progressWrap, metrics);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox bottom = new VBox(12, metrics, actions);
        bottom.setFillWidth(true);
        card.getChildren().addAll(body, bottom);
        card.setFillWidth(true);
        return card;
    }

    private Node metricCell(String label, String value) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10));
        box.setStyle(
                "-fx-background-color: #fafbff; -fx-background-radius: 10; -fx-border-color: #edf2f7; -fx-border-radius: 10;");
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px; -fx-font-weight: 700;");
        Label v = new Label(value == null || value.isBlank() ? "-" : value);
        v.setStyle("-fx-text-fill: #111827; -fx-font-size: 13px; -fx-font-weight: 800;");
        box.getChildren().addAll(l, v);
        return box;
    }

    private void refreshStats(List<ModulePosting> modules) {
        int total = modules == null ? 0 : modules.size();
        int open = 0;
        int finished = 0;
        int pending = 0;
        if (modules != null) {
            for (ModulePosting module : modules) {
                String displayStatus = moduleDisplayStatus(module);
                if ("OPEN".equalsIgnoreCase(displayStatus)) {
                    open++;
                }
                if ("FINISHED".equalsIgnoreCase(displayStatus)) {
                    finished++;
                }
                pending += moService.countPendingForModule(module.getModuleId());
            }
        }
        totalModulesValue.setText(String.valueOf(total));
        openModulesValue.setText(String.valueOf(open));
        finishedModulesValue.setText(String.valueOf(finished));
        pendingApplicationsValue.setText(String.valueOf(pending));
    }

    private void showApplicantsDialog(ModulePosting module) {
        List<ApplicantRow> rows = moService.getApplicantsForModule(module.getModuleId());

        VBox dialog = new VBox(14);
        dialog.setPadding(new Insets(16));
        dialog.setStyle("-fx-background-color: #f4f7fb;");

        VBox header = new VBox(4);
        Label title = new Label("Application Status");
        title.setStyle("-fx-text-fill: #111827; -fx-font-size: 20px; -fx-font-weight: 800;");
        Label subtitle = new Label(safe(module.getModuleCode()) + " - " + safe(module.getModuleName()));
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        long submittedCount = rows.stream().filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED).count();
        Label summary = new Label(rows.size() + " application(s) • " + submittedCount + " submitted");
        summary.setStyle("-fx-text-fill: #7c3aed; -fx-font-size: 12px; -fx-font-weight: 700;");
        header.getChildren().addAll(title, subtitle, summary);

        if (rows.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setPadding(new Insets(18));
            empty.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
            Label emptyTitle = new Label("No application records yet.");
            emptyTitle.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px; -fx-font-weight: 700;");
            Label emptyHint = new Label("Once students apply, they will appear here with their CV and decision actions.");
            emptyHint.setWrapText(true);
            emptyHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
            empty.getChildren().addAll(emptyTitle, emptyHint);
            dialog.getChildren().addAll(header, empty);
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            VBox list = new VBox(10);
            list.setPadding(new Insets(2));
            for (ApplicantRow row : rows) {
                list.getChildren().add(buildApplicantCard(module, row));
            }
            scroll.setContent(list);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            dialog.getChildren().addAll(header, scroll);
        }

        showDialog("Applications", dialog, 980, 700);
    }

    private Node buildApplicantCard(ModulePosting module, ApplicantRow row) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.04), 10, 0.15, 0, 2);");

        HBox header = new HBox(12);
        header.setAlignment(Pos.TOP_LEFT);
        StackPane avatar = new StackPane(icon(FontAwesomeSolid.USER, 14, "#6d28d9"));
        avatar.setPrefSize(36, 36);
        avatar.setStyle("-fx-background-color: #f3ebff; -fx-background-radius: 18;");

        VBox info = new VBox(4);
        Label name = new Label(row.getTaName());
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 16px; -fx-font-weight: 800;");
        Label meta = new Label((row.getTaUserId() == null ? "" : row.getTaUserId()));
        meta.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        info.getChildren().addAll(name, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox rightBox = new VBox(6);
        rightBox.setAlignment(Pos.TOP_RIGHT);

        String appStatus = row.getStatus() == null ? "UNKNOWN" : row.getStatus().name();
        Label statusBadge = new Label(appStatus);
        statusBadge.setMinWidth(120);
        statusBadge.setAlignment(Pos.CENTER);
        statusBadge.setStyle("-fx-text-fill: " + applicationStatusColor(appStatus)
                + "; -fx-font-size: 11px; -fx-font-weight: 800; -fx-background-color: " + tint(applicationStatusColor(appStatus))
                + "; -fx-background-radius: 999; -fx-padding: 4 10 4 10;");

        Label cvLabel = new Label(row.getCvFilePath() == null || row.getCvFilePath().isBlank() ? "No CV" : cvName(row.getCvFilePath()));
        cvLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: 700;");

        rightBox.getChildren().addAll(statusBadge, cvLabel);

        header.getChildren().addAll(avatar, info, rightBox);

        HBox details = new HBox(12,
                chip("Email", safe(row.getTaEmail())),
                chip("Phone", safe(row.getTaPhone())),
                chip("Skills", row.getSkills() == null || row.getSkills().isEmpty() ? "-" : String.join(", ", row.getSkills())));
        details.setFillHeight(true);
        for (Node n : details.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        HBox actions = new HBox(8);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openCv = new Button("Open CV");
        styleGhostButton(openCv);
        openCv.setDisable(row.getCvFilePath() == null || row.getCvFilePath().isBlank());
        openCv.setOnAction(e -> openCv(row.getCvFilePath()));

        boolean decidable = row.getStatus() == ApplicationStatus.SUBMITTED;

        Button acceptBtn = new Button("Accept");
        stylePrimaryButton(acceptBtn);
        acceptBtn.setDisable(!decidable);
        acceptBtn.setOnAction(e -> {
            MOService.MOActionResult result = moService.acceptApplication(row.getApplicationId(), currentUser.getQmId());
            showInfo(result.isSuccess() ? "Success" : "Failed", result.getMessage());
            refreshModules();
            showApplicantsDialog(module);
        });

        Button rejectBtn = new Button("Reject");
        styleGhostButton(rejectBtn);
        rejectBtn.setDisable(!decidable);
        rejectBtn.setOnAction(e -> {
            MOService.MOActionResult result = moService.rejectApplication(row.getApplicationId(), currentUser.getQmId());
            showInfo(result.isSuccess() ? "Success" : "Failed", result.getMessage());
            refreshModules();
            showApplicantsDialog(module);
        });

        actions.getChildren().addAll(spacer, openCv, rejectBtn, acceptBtn);
        card.getChildren().addAll(header, details, actions);
        return card;
    }

    private VBox chip(String label, String value) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: 700;");
        Label v = new Label(value == null || value.isBlank() ? "-" : value);
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: 700;");
        box.getChildren().addAll(l, v);
        return box;
    }

    private void showDialog(String title, VBox content, double width, double height) {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(width, height);
        dialog.showAndWait();
    }

    private void decision(ModulePosting module, ApplicantRow row, boolean accept) {
        MOService.MOActionResult result = accept
                ? moService.acceptApplication(row.getApplicationId(), currentUser.getQmId())
                : moService.rejectApplication(row.getApplicationId(), currentUser.getQmId());
        showInfo(result.isSuccess() ? "Success" : "Failed", result.getMessage());
        refreshModules();
    }

    private void openCv(String cvPath) {
        if (cvPath == null || cvPath.isBlank()) {
            showInfo("CV", "No CV file available.");
            return;
        }
        Path resolved = DataFileOpen.resolveUnderData(cvPath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            showInfo("CV", "CV file not found.");
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(resolved.toFile());
        } catch (IOException ex) {
            showInfo("CV", "Could not open CV: " + ex.getMessage());
        }
    }

    private String cvName(String path) {
        if (path == null || path.isBlank()) {
            return "-";
        }
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.startsWith("UP_") && name.length() > 3 ? name.substring(3) : name;
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String moduleDisplayStatus(ModulePosting posting) {
        if (posting == null) {
            return "UNKNOWN";
        }
        int total = Math.max(1, posting.getVacanciesTotal());
        int filled = Math.max(0, posting.getVacanciesFilled());
        if (posting.getStatus() == ModuleStatus.CLOSED) {
            return "CLOSED";
        }
        if (filled >= total) {
            return "FINISHED";
        }
        return "OPEN";
    }

    private String statusColor(String status) {
        if ("FINISHED".equalsIgnoreCase(status))
            return "#2563eb";
        if ("CLOSED".equalsIgnoreCase(status))
            return "#6d28d9";
        if ("OPEN".equalsIgnoreCase(status))
            return "#16a34a";
        return "#64748b";
    }

    private boolean isModuleFinished(ModulePosting posting) {
        if (posting == null) {
            return false;
        }
        int total = Math.max(1, posting.getVacanciesTotal());
        return posting.getStatus() == ModuleStatus.FINISHED
                || (posting.getStatus() != ModuleStatus.CLOSED && posting.getVacanciesFilled() >= total);
    }

    private boolean isModuleClosed(ModulePosting posting) {
        return posting != null && posting.getStatus() == ModuleStatus.CLOSED;
    }

    private String tint(String color) {
        if ("#6d28d9".equalsIgnoreCase(color))
            return "#f3ebff";
        if ("#16a34a".equalsIgnoreCase(color))
            return "#ecfdf3";
        if ("#2563eb".equalsIgnoreCase(color))
            return "#eff6ff";
        if ("#dc2626".equalsIgnoreCase(color))
            return "#fef2f2";
        if ("#7c3aed".equalsIgnoreCase(color))
            return "#f3e8ff";
        return "#f3f4f6";
    }

    private String applicationStatusColor(String status) {
        if ("SUBMITTED".equalsIgnoreCase(status)) return "#7c3aed";
        if ("WAITING_FOR_ASSIGNMENT".equalsIgnoreCase(status)) return "#f59e0b";
        if ("ACCEPTED".equalsIgnoreCase(status)) return "#2563eb";
        if ("REJECTED".equalsIgnoreCase(status)) return "#dc2626";
        return "#64748b";
    }

    private static FontIcon icon(FontAwesomeSolid glyph, int size, String color) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(size);
        icon.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        return icon;
    }

    private void stylePrimaryButton(Button button) {
        button.setStyle(
                "-fx-background-color: #6d28d9; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 8; -fx-padding: 8 14 8 14;");
    }

    private void stylePurpleButton(Button button) {
        button.setStyle(
                "-fx-background-color: white; -fx-border-color: #7c3aed; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #7c3aed; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
    }

    private void styleGhostButton(Button button) {
        button.setStyle(
                "-fx-background-color: white; -fx-border-color: #d7dfea; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #334155; -fx-font-weight: 700; -fx-padding: 8 14 8 14;");
    }

    private String panelStyle() {
        return "-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.15, 0, 3);";
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
