package com.group58.recruit.ui.fx;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AdminService;
import com.group58.recruit.service.AdminService.ActionResult;
import com.group58.recruit.service.ai.RecruitmentInsightResult;
import com.group58.recruit.service.ai.RecruitmentInsightService;
import com.group58.recruit.service.AdminService.ApplicantFilter;
import com.group58.recruit.service.AdminService.ApplicationCardRow;
import com.group58.recruit.service.AdminService.CourseCardRow;
import com.group58.recruit.service.AdminService.CourseFilter;
import com.group58.recruit.service.AdminService.AdjustmentFlowEdge;
import com.group58.recruit.util.DataFileOpen;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
/**
 * Admin dashboard (JavaFX), aligned with TA {@link TADashboardFxView} styling and the admin mockup layout.
 */
public final class AdminDashboardFxView extends BorderPane {

    private static final String SIDEBAR_LOGO_PATH = "assets/icons/qmul-logo.png";
    private static final String ICON_DIR = "assets/icons";

    private final AdminService adminService = new AdminService();
    private final RecruitmentInsightService insightService = new RecruitmentInsightService();
    private final Runnable logoutAction;

    private User currentAdmin;

    private final Label titleBarLabel = new Label("BUPT International School \u2014 TA Recruitment");
    private final Label userChipLabel = new Label("Admin");
    private final Label adminIdentityLabel = new Label("Admin: -");

    private final Label statModules = new Label("0");
    private final Label statOpen = new Label("0");
    private final Label statApps = new Label("0");
    private final Label statPendingAdj = new Label("0");

    private final VBox courseCardBox = new VBox(12);
    /** Separate boxes: one ScrollPane cannot share the same VBox node with another parent. */
    private final VBox applicantCardBoxOverview = new VBox(12);
    private final VBox applicantCardBoxReassign = new VBox(12);
    private final VBox moPendingBannerOverview = new VBox(8);
    private final VBox moPendingBannerReassign = new VBox(8);
    private final FlowPane adjustmentFlowPane = new FlowPane(12, 10);
    private final TableView<AttentionRow> attentionTable = new TableView<>();

    private CourseFilter courseFilter = CourseFilter.ALL;
    private ApplicantFilter applicantFilter = ApplicantFilter.ALL;

    private final ToggleGroup courseTabGroup = new ToggleGroup();
    private final ToggleGroup applicantTabGroup = new ToggleGroup();

    private final VBox overviewPage = new VBox(14);
    private final VBox analysePage = new VBox(16);
    private final VBox reassignmentPage = new VBox(14);
    private final VBox aiPage = new VBox(16);
    private final StackPane mainStack = new StackPane();

    private ComboBox<ModulePosting> aiModuleCombo;
    private ComboBox<TAProfile> aiProfileCombo;
    private Button aiRunButton;
    private Label aiInsightMetaChip;
    private BorderPane aiVerdictCard;
    private FontIcon aiVerdictIcon;
    private Label aiVerdictTitle;
    private Label aiVerdictSubline;
    private Label aiScoreValue;
    private ProgressBar aiMatchProgress;
    private StackPane aiMatchedStack;
    private FlowPane aiMatchedFlow;
    private VBox aiMatchedEmptyBox;
    private FlowPane aiMissingFlow;
    private VBox aiWorkloadVBox;
    private Label aiWorkloadRiskBadge;
    private TextArea aiRationaleArea;
    private VBox aiSuggestionBulletBox;
    private FlowPane aiSuitableTagsFlow;
    private Label aiFooterDisclaimer;

    public AdminDashboardFxView(Runnable logoutAction) {
        this.logoutAction = logoutAction == null ? () -> {
        } : logoutAction;
        setStyle("-fx-background-color: #f4f7fb;");
        setLeft(buildSidebar());
        BorderPane centerWrap = new BorderPane();
        centerWrap.setCenter(mainStack);
        setCenter(centerWrap);
        buildPages();
        showPage(Page.OVERVIEW);
        bindAttentionTable();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            currentAdmin = null;
            adminIdentityLabel.setText("Admin: -");
            userChipLabel.setText("Admin");
            refreshAll();
            return;
        }
        currentAdmin = user;
        String display = (user.getName() == null || user.getName().isBlank()) ? user.getQmId() : user.getName();
        adminIdentityLabel.setText("Admin: " + display + " (" + user.getQmId() + ")");
        userChipLabel.setText(display);
        refreshAll();
    }

    private void buildPages() {
        overviewPage.setPadding(new Insets(0, 18, 18, 18));
        VBox.setVgrow(overviewPage, Priority.ALWAYS);
        ScrollPane overviewScroll = new ScrollPane(buildOverviewBody());
        overviewScroll.setFitToWidth(true);
        overviewScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(overviewScroll, Priority.ALWAYS);
        overviewPage.getChildren().add(overviewScroll);

        analysePage.setPadding(new Insets(48));
        analysePage.setAlignment(Pos.TOP_LEFT);
        analysePage.getChildren().add(wrapCard(buildPlaceholder(
                "Analyse",
                "Aggregate charts and export will appear here. Use Overview for live recruitment metrics.")));

        reassignmentPage.setPadding(new Insets(0, 18, 18, 18));
        ScrollPane rs = new ScrollPane(buildReassignmentBody());
        rs.setFitToWidth(true);
        rs.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(rs, Priority.ALWAYS);
        reassignmentPage.getChildren().add(rs);

        aiPage.setPadding(new Insets(0, 12, 12, 14));
        aiPage.setAlignment(Pos.TOP_LEFT);
        ScrollPane aiScroll = new ScrollPane(buildAiInsightBody());
        aiScroll.setFitToWidth(true);
        aiScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(aiScroll, Priority.ALWAYS);
        aiPage.getChildren().add(aiScroll);

        mainStack.getChildren().addAll(overviewPage, analysePage, reassignmentPage, aiPage);
        for (Node n : mainStack.getChildren()) {
            n.setVisible(false);
            n.managedProperty().bind(n.visibleProperty());
        }
    }

    private Node buildOverviewBody() {
        VBox root = new VBox(14);
        root.getChildren().addAll(
                buildAppTitleBar(),
                buildAdminHeaderCard(),
                buildStatsRow(),
                buildAdjustmentSection(),
                buildMainSplit(),
                buildAttentionSection());
        return root;
    }

    private Node buildReassignmentBody() {
        VBox root = new VBox(14);
        Label head = new Label("Reassignment");
        head.setStyle("-fx-font-size: 18; -fx-font-weight: 800; -fx-text-fill: #1f2937;");
        root.getChildren().addAll(
                head,
                buildAdjustmentSection(),
                buildApplicantPanelShell(true));
        return root;
    }

    private Node wrapCard(Node inner) {
        VBox card = new VBox(inner);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.15, 0, 4);");
        return card;
    }

    /** Tighter card for AI insight page to reduce vertical scroll. */
    private Node wrapAiCard(Node inner) {
        VBox card = new VBox(inner);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.12, 0, 3);");
        return card;
    }

    private static Node scrollCapped(Node content, double viewportHeight) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(viewportHeight);
        sp.setMinViewportHeight(Math.min(56, viewportHeight));
        sp.setMaxHeight(viewportHeight + 10);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }

    private Node buildPlaceholder(String title, String body) {
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 22; -fx-font-weight: 800; -fx-text-fill: #1f2937;");
        Label b = new Label(body);
        b.setWrapText(true);
        b.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14;");
        return new VBox(12, t, b);
    }

    private enum Page {
        OVERVIEW, ANALYSE, REASSIGNMENT, AI
    }

    private void showPage(Page page) {
        overviewPage.setVisible(page == Page.OVERVIEW);
        analysePage.setVisible(page == Page.ANALYSE);
        reassignmentPage.setVisible(page == Page.REASSIGNMENT);
        aiPage.setVisible(page == Page.AI);
        if (page == Page.OVERVIEW || page == Page.REASSIGNMENT) {
            refreshAll();
        }
        if (page == Page.AI) {
            refreshAiSelectors();
        }
    }

    private Node buildSidebar() {
        VBox bar = new VBox(14);
        bar.setPadding(new Insets(16, 12, 12, 12));
        bar.setPrefWidth(216);
        bar.setMinWidth(216);
        bar.setStyle("-fx-background-color: linear-gradient(to bottom, #0d63f3, #003c95);");

        Node logo = createSidebarLogo();

        Button overview = navRow(FontAwesomeSolid.HOME, "Overview", () -> showPage(Page.OVERVIEW));
        Button analyse = navRow(FontAwesomeSolid.CHART_BAR, "Analyse", () -> showPage(Page.ANALYSE));
        Button reassign = navRow(FontAwesomeSolid.EXCHANGE_ALT, "Reassignment", () -> showPage(Page.REASSIGNMENT));
        Button ai = navRow(FontAwesomeSolid.ROBOT, "AI", () -> showPage(Page.AI));

        VBox nav = new VBox(8, overview, analyse, reassign, ai);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setGraphic(icon(FontAwesomeSolid.SIGN_OUT_ALT, 12, "#ffffff"));
        logout.setStyle(
                "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.6); -fx-border-radius: 8;"
                        + "-fx-background-radius: 8; -fx-text-fill: white; -fx-font-size: 12; -fx-padding: 10 12 10 12;");
        logout.setOnAction(e -> {
            setCurrentUser(null);
            logoutAction.run();
        });

        bar.getChildren().addAll(logo, nav, spacer, logout);
        return bar;
    }

    private Button navRow(FontAwesomeSolid glyph, String text, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(icon(glyph, 14, "#ffffff"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: 600; -fx-font-size: 13;"
                        + "-fx-padding: 10 12 10 12; -fx-background-radius: 10;");
        b.setOnMouseEntered(e -> b.setStyle(
                "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; -fx-font-weight: 600;"
                        + "-fx-font-size: 13; -fx-padding: 10 12 10 12; -fx-background-radius: 10;"));
        b.setOnMouseExited(e -> b.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: 600; -fx-font-size: 13;"
                        + "-fx-padding: 10 12 10 12; -fx-background-radius: 10;"));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node createSidebarLogo() {
        StackPane frame = new StackPane();
        frame.setPrefSize(68, 68);
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
        fallback.setStyle("-fx-font-size: 28; -fx-text-fill: white;");
        frame.getChildren().add(fallback);
        return frame;
    }

    private Node buildAppTitleBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 2, 4, 2));
        titleBarLabel.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 22; -fx-font-weight: 800;");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Button help = iconButton(FontAwesomeSolid.QUESTION_CIRCLE, "Help");
        help.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Use the course list to inspect postings.\nClick a TA row to reassign when status is \"Waiting for adjustment\" "
                        + "and all MOs have finished reviewing CVs.")
                .showAndWait());
        StackPane avatar = new StackPane();
        avatar.setPrefSize(36, 36);
        avatar.setStyle("-fx-background-color: rgba(33,103,247,0.15); -fx-background-radius: 18;");
        Label av = new Label("A");
        av.setStyle("-fx-text-fill: #1e4a8c; -fx-font-weight: 800;");
        avatar.getChildren().add(av);
        userChipLabel.setStyle("-fx-text-fill: #334155; -fx-font-weight: 700;");
        bar.getChildren().addAll(titleBarLabel, grow, help, avatar, userChipLabel);
        return bar;
    }

    private Node buildAdminHeaderCard() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");
        adminIdentityLabel.setStyle("-fx-font-size: 20; -fx-font-weight: 800; -fx-text-fill: #1c3558;");
        Label sub = new Label("Monitor recruitment health, TA pipelines, and reassignment readiness.");
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13;");
        sub.setWrapText(true);
        card.getChildren().addAll(adminIdentityLabel, sub);
        return card;
    }

    private Node buildStatsRow() {
        HBox row = new HBox(12);
        row.getChildren().addAll(
                miniStatCard("Total modules", statModules, "All modules published", FontAwesomeSolid.BOOK),
                miniStatCard("Open recruitment", statOpen, "Modules with vacancies", FontAwesomeSolid.DOOR_OPEN),
                miniStatCard("Total applications", statApps, "All student applications", FontAwesomeSolid.USERS),
                miniStatCard("Pending adjustments", statPendingAdj, "Need admin attention", FontAwesomeSolid.HOURGLASS_HALF));
        for (Node n : row.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }
        return row;
    }

    private Node miniStatCard(String title, Label value, String hint, FontAwesomeSolid glyph) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(14));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.12, 0, 3);");
        StackPane ic = new StackPane(icon(glyph, 16, "#2167f7"));
        ic.setPrefSize(40, 40);
        ic.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 20;");
        VBox txt = new VBox(4);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12; -fx-font-weight: 600;");
        value.setStyle("-fx-text-fill: #111827; -fx-font-size: 28; -fx-font-weight: 800;");
        Label h = new Label(hint);
        h.setWrapText(true);
        h.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");
        txt.getChildren().addAll(t, value, h);
        VBox.setVgrow(txt, Priority.ALWAYS);
        card.getChildren().addAll(ic, txt);
        return card;
    }

    private Node buildAdjustmentSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
        Label title = new Label("Adjustment flow (reassignments)");
        title.setStyle("-fx-font-weight: 800; -fx-text-fill: #2e7ac4; -fx-font-size: 14;");
        adjustmentFlowPane.setPrefWrapLength(800);
        box.getChildren().addAll(title, adjustmentFlowPane);
        return box;
    }

    private Node buildMainSplit() {
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.5);

        VBox left = new VBox(10);
        left.setPadding(new Insets(12));
        left.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
        Label lc = new Label("Course recruitment");
        lc.setStyle("-fx-font-weight: 800; -fx-text-fill: #64748b; -fx-font-size: 13;");
        left.getChildren().addAll(lc, buildCourseProgressOverview(), buildCourseTabs(), buildCourseScroll());

        VBox right = new VBox(10);
        right.setPadding(new Insets(12));
        right.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
        Label rc = new Label("TA applications");
        rc.setStyle("-fx-font-weight: 800; -fx-text-fill: #64748b; -fx-font-size: 13;");
        right.getChildren().addAll(rc, buildApplicantTabs(), moPendingBannerOverview, buildApplicantScroll(applicantCardBoxOverview));

        split.getItems().addAll(left, right);
        return split;
    }

    private Node buildCourseProgressOverview() {
        VBox box = new VBox(8);
        List<CourseCardRow> all = adminService.listCourseRecruitment(CourseFilter.ALL);
        long finished = all.stream().filter(r -> adminServiceIsFinished(r)).count();
        int total = all.size();
        double ratio = total <= 0 ? 0 : (double) finished / total;
        Label line = new Label();
        line.setStyle("-fx-font-weight: 700; -fx-text-fill: #1f2937;");
        long openCount = all.stream().filter(r -> {
            ModulePosting m = r.getModule();
            return m != null && m.getStatus() == ModuleStatus.OPEN && r.getRemaining() > 0;
        }).count();
        line.setText(total == 0 ? "No modules loaded." : finished + " / " + total + " modules completed  ·  " + openCount + " modules open");
        ProgressBar bar = new ProgressBar(ratio);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: #2e7ac4; -fx-control-inner-background: #e8eef8;");
        HBox links = new HBox(16);
        Hyperlink allM = new Hyperlink("View all modules");
        allM.setOnAction(e -> showModulesSummaryAlert(all));
        links.getChildren().add(allM);
        box.getChildren().addAll(line, bar, links);
        return box;
    }

    /** Heuristic for "module recruitment finished" without exposing repository details. */
    private boolean adminServiceIsFinished(CourseCardRow row) {
        ModulePosting m = row.getModule();
        if (m == null) {
            return true;
        }
        if (m.getStatus() == ModuleStatus.FINISHED) {
            return true;
        }
        int total = Math.max(0, m.getVacanciesTotal());
        int filled = Math.max(0, m.getVacanciesFilled());
        return total > 0 && filled >= total;
    }

    private void showModulesSummaryAlert(List<CourseCardRow> rows) {
        String body = rows.stream()
                .map(r -> {
                    ModulePosting m = r.getModule();
                    String code = m != null && m.getModuleCode() != null ? m.getModuleCode() : "";
                    return code + " — " + (m != null && m.getModuleName() != null ? m.getModuleName() : "");
                })
                .collect(Collectors.joining("\n"));
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("All modules");
        a.setContentText(body.isBlank() ? "No modules." : body);
        a.showAndWait();
    }

    private Node buildCourseTabs() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: #e8eef8; -fx-background-radius: 8;");
        ToggleButton all = tabBtn("All", courseTabGroup, true);
        ToggleButton fin = tabBtn("Finished", courseTabGroup, false);
        ToggleButton unf = tabBtn("Unfinished", courseTabGroup, false);
        all.setOnAction(e -> {
            courseFilter = CourseFilter.ALL;
            refreshCoursesOnly();
        });
        fin.setOnAction(e -> {
            courseFilter = CourseFilter.FINISHED;
            refreshCoursesOnly();
        });
        unf.setOnAction(e -> {
            courseFilter = CourseFilter.UNFINISHED;
            refreshCoursesOnly();
        });
        row.getChildren().addAll(all, fin, unf);
        return row;
    }

    private Node buildApplicantTabs() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: #e8eef8; -fx-background-radius: 8;");
        ToggleButton all = tabBtn("All", applicantTabGroup, true);
        ToggleButton wait = tabBtn("Waiting for adjustment", applicantTabGroup, false);
        all.setOnAction(e -> {
            applicantFilter = ApplicantFilter.ALL;
            refreshApplicantsOnly();
        });
        wait.setOnAction(e -> {
            applicantFilter = ApplicantFilter.WAITING_FOR_ADJUSTMENT;
            refreshApplicantsOnly();
        });
        row.getChildren().addAll(all, wait);
        Hyperlink link = new Hyperlink("View all applications");
        link.setOnAction(e -> {
            List<ApplicationCardRow> list = adminService.listApplicantDashboard(ApplicantFilter.ALL);
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Applications");
            a.setContentText(list.size() + " application(s) in total.");
            a.showAndWait();
        });
        HBox wrap = new HBox(12, row, link);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private ToggleButton tabBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.setStyle("-fx-font-weight: 700; -fx-font-size: 12; -fx-padding: 6 12 6 12;");
        return b;
    }

    private ScrollPane buildCourseScroll() {
        courseCardBox.setPadding(new Insets(4));
        courseCardBox.setFillWidth(true);
        ScrollPane sp = new ScrollPane(courseCardBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(320);
        sp.setMinHeight(220);
        sp.setStyle("-fx-background-color: #f8fafc;");
        courseCardBox.setStyle("-fx-background-color: #f8fafc;");
        return sp;
    }

    private ScrollPane buildApplicantScroll(VBox cardBox) {
        cardBox.setPadding(new Insets(4));
        cardBox.setFillWidth(true);
        ScrollPane sp = new ScrollPane(cardBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(320);
        sp.setMinHeight(220);
        sp.setStyle("-fx-background-color: #f8fafc;");
        cardBox.setStyle("-fx-background-color: #f8fafc;");
        return sp;
    }

    /** Applicant list + banner used in Overview split and Reassignment page. */
    private Node buildApplicantPanelShell(boolean tall) {
        VBox v = new VBox(10);
        v.getChildren().addAll(buildApplicantTabs(), moPendingBannerReassign, buildApplicantScroll(applicantCardBoxReassign));
        if (tall) {
            VBox.setVgrow(v.getChildren().get(2), Priority.ALWAYS);
        }
        return v;
    }

    private Node buildAttentionSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;");
        HBox head = new HBox();
        head.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label("Attention needed");
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #1f2937; -fx-font-size: 15;");
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        Hyperlink all = new Hyperlink("View all issues");
        all.setOnAction(e -> attentionTable.refresh());
        head.getChildren().addAll(t, g, all);
        attentionTable.setPrefHeight(220);
        attentionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        box.getChildren().addAll(head, attentionTable);
        return box;
    }

    private void bindAttentionTable() {
        TableColumn<AttentionRow, String> colModule = new TableColumn<>("Module");
        colModule.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().module));
        TableColumn<AttentionRow, String> colMo = new TableColumn<>("MO");
        colMo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().mo));
        TableColumn<AttentionRow, String> colVac = new TableColumn<>("Vacancies");
        colVac.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().vacancies));
        TableColumn<AttentionRow, String> colWl = new TableColumn<>("Waiting list");
        colWl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().waitlist));
        TableColumn<AttentionRow, String> colIssue = new TableColumn<>("Issue");
        colIssue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().issue));
        TableColumn<AttentionRow, AttentionRow> colAct = new TableColumn<>("Action");
        colAct.setCellValueFactory(cdf -> new SimpleObjectProperty<>(cdf.getValue()));
        colAct.setCellFactory(col -> new TableCell<AttentionRow, AttentionRow>() {
            @Override
            protected void updateItem(AttentionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Button review = new Button("Review");
                review.setStyle(
                        "-fx-background-color: #eef4ff; -fx-text-fill: #1e4a8c; -fx-font-weight: 700; -fx-background-radius: 8;");
                review.setOnAction(e -> openAttentionReview(item));
                setGraphic(review);
            }
        });
        attentionTable.getColumns().setAll(List.of(colModule, colMo, colVac, colWl, colIssue, colAct));
        attentionTable.setRowFactory(tv -> new TableRow<AttentionRow>() {
            @Override
            protected void updateItem(AttentionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                if ("high".equals(item.severity)) {
                    setStyle("-fx-background-color: #fff1f1;");
                } else if ("medium".equals(item.severity)) {
                    setStyle("-fx-background-color: #fff8eb;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void openAttentionReview(AttentionRow row) {
        if (row == null) {
            return;
        }
        if (row.moduleId == null) {
            StringBuilder detail = new StringBuilder(row.issue);
            List<String> pending = adminService.listMoPendingSubmittedSummaryLines();
            if (!pending.isEmpty()) {
                detail.append("\n\n");
                for (String line : pending) {
                    detail.append("\u2022 ").append(line).append('\n');
                }
            }
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Attention");
            a.setContentText(detail.toString().trim());
            a.showAndWait();
            return;
        }
        ModulePosting m = findModulePosting(row.moduleId);
        if (m != null) {
            showModuleJobDialog(m);
        } else {
            new Alert(Alert.AlertType.INFORMATION, row.issue).showAndWait();
        }
    }

    private ModulePosting findModulePosting(String moduleId) {
        if (moduleId == null) {
            return null;
        }
        for (CourseCardRow cr : adminService.listCourseRecruitment(CourseFilter.ALL)) {
            ModulePosting m = cr.getModule();
            if (m != null && moduleId.equals(m.getModuleId())) {
                return m;
            }
        }
        return null;
    }

    private static final class AttentionRow {
        final String moduleId;
        final String module;
        final String mo;
        final String vacancies;
        final String waitlist;
        final String issue;
        final String severity;

        AttentionRow(String moduleId, String module, String mo, String vacancies, String waitlist, String issue,
                String severity) {
            this.moduleId = moduleId;
            this.module = module;
            this.mo = mo;
            this.vacancies = vacancies;
            this.waitlist = waitlist;
            this.issue = issue;
            this.severity = severity;
        }
    }

    private void refreshAll() {
        refreshStats();
        refreshAdjustmentFlow();
        refreshMoBanner();
        refreshCoursesOnly();
        refreshApplicantsOnly();
        refreshAttentionRows();
    }

    private void refreshStats() {
        List<CourseCardRow> courses = adminService.listCourseRecruitment(CourseFilter.ALL);
        statModules.setText(String.valueOf(courses.size()));
        long open = courses.stream().filter(r -> {
            ModulePosting m = r.getModule();
            return m != null && m.getStatus() == ModuleStatus.OPEN && r.getRemaining() > 0;
        }).count();
        statOpen.setText(String.valueOf(open));
        int apps = adminService.listApplicantDashboard(ApplicantFilter.ALL).size();
        statApps.setText(String.valueOf(apps));
        int pend = adminService.listApplicantDashboard(ApplicantFilter.WAITING_FOR_ADJUSTMENT).size();
        statPendingAdj.setText(String.valueOf(pend));
    }

    private void refreshAdjustmentFlow() {
        adjustmentFlowPane.getChildren().clear();
        if (currentAdmin == null) {
            Label empty = new Label("Login to see reassignment flows.");
            empty.setStyle("-fx-text-fill: #94a3b8;");
            adjustmentFlowPane.getChildren().add(empty);
            return;
        }
        List<AdjustmentFlowEdge> edges = adminService.listAdjustmentFlowEdges();
        if (edges.isEmpty()) {
            Label empty = new Label("No reassignment flows yet. Reassignments appear here after admin actions.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-wrap-text: true; -fx-max-width: 720;");
            adjustmentFlowPane.getChildren().add(empty);
            return;
        }
        for (AdjustmentFlowEdge e : edges) {
            HBox chip = new HBox(6);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setPadding(new Insets(8, 12, 8, 12));
            chip.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #dbe4ee; -fx-border-radius: 10; -fx-background-radius: 10;");
            Label txt = new Label(e.getFromLabel() + "  \u2192  " + e.getToLabel() + "  (+" + e.getCount() + ")");
            txt.setStyle("-fx-font-weight: 600; -fx-text-fill: #1e293b;");
            chip.getChildren().add(txt);
            adjustmentFlowPane.getChildren().add(chip);
        }
    }

    private void refreshMoBanner() {
        fillMoPendingBanner(moPendingBannerOverview);
        fillMoPendingBanner(moPendingBannerReassign);
    }

    private void fillMoPendingBanner(VBox moPendingBanner) {
        moPendingBanner.getChildren().clear();
        if (currentAdmin == null) {
            moPendingBanner.setVisible(false);
            return;
        }
        List<String> lines = adminService.listMoPendingSubmittedSummaryLines();
        if (lines.isEmpty()) {
            moPendingBanner.setVisible(false);
            return;
        }
        moPendingBanner.setVisible(true);
        moPendingBanner.setPadding(new Insets(10));
        moPendingBanner.setStyle(
                "-fx-background-color: #fff8eb; -fx-border-color: #e6c98a; -fx-border-radius: 10; -fx-background-radius: 10;");
        Label title = new Label("MOs with pending submitted applications (reassignment blocked until reviewed):");
        title.setStyle("-fx-font-weight: 800; -fx-text-fill: #92400e;");
        moPendingBanner.getChildren().add(title);
        for (String line : lines) {
            Label row = new Label(line);
            row.setWrapText(true);
            row.setStyle("-fx-text-fill: #1f2937;");
            moPendingBanner.getChildren().add(row);
        }
    }

    private void refreshCoursesOnly() {
        courseCardBox.getChildren().clear();
        if (currentAdmin == null) {
            courseCardBox.getChildren().add(hintLabel("Please login as Admin."));
            return;
        }
        List<CourseCardRow> rows = adminService.listCourseRecruitment(courseFilter);
        if (rows.isEmpty()) {
            courseCardBox.getChildren().add(hintLabel("No courses match this filter."));
            return;
        }
        for (CourseCardRow row : rows) {
            courseCardBox.getChildren().add(buildCourseCard(row));
        }
    }

    private void refreshApplicantsOnly() {
        fillApplicantBox(applicantCardBoxOverview);
        fillApplicantBox(applicantCardBoxReassign);
    }

    private void fillApplicantBox(VBox box) {
        box.getChildren().clear();
        if (currentAdmin == null) {
            box.getChildren().add(hintLabel("Please login as Admin."));
            return;
        }
        List<ApplicationCardRow> rows = adminService.listApplicantDashboard(applicantFilter);
        if (rows.isEmpty()) {
            box.getChildren().add(hintLabel("No TA applications match this filter."));
            return;
        }
        for (ApplicationCardRow row : rows) {
            box.getChildren().add(buildApplicantCard(row));
        }
    }

    private Label hintLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13;");
        return l;
    }

    private Node buildCourseCard(CourseCardRow row) {
        ModulePosting m = row.getModule();
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dbe4ee; -fx-border-radius: 12; -fx-background-radius: 12;");
        String title = (m.getModuleCode() != null ? m.getModuleCode() : "") + " - " + (m.getModuleName() != null ? m.getModuleName() : "");
        Button nameBtn = new Button(title);
        nameBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #2e7ac4; -fx-font-weight: 800; -fx-font-size: 16; -fx-padding: 0;");
        nameBtn.setOnAction(e -> showModuleJobDialog(m));
        Label mo = new Label("MO: " + row.getMoDisplayName());
        mo.setStyle("-fx-font-weight: 700; -fx-text-fill: #563070;");
        Label vac = new Label("Vacancies: " + m.getVacanciesFilled() + "/" + m.getVacanciesTotal());
        vac.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600;");
        Label st = new Label(row.getRecruitmentStatusText());
        st.setStyle("-fx-font-weight: 700; -fx-text-fill: " + colorForRemaining(row.getRemaining()) + ";");
        card.getChildren().addAll(nameBtn, mo, vac, st);
        return card;
    }

    private String colorForRemaining(int remaining) {
        if (remaining <= 0) {
            return "#237338";
        }
        if (remaining == 1) {
            return "#d99200";
        }
        return "#d64a4a";
    }

    private Node buildApplicantCard(ApplicationCardRow row) {
        boolean canReassign = row.getStatus() == ApplicationStatus.WAITING_FOR_ASSIGNMENT;
        HBox card = new HBox(12);
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dbe4ee; -fx-border-radius: 12; -fx-background-radius: 12;"
                + (canReassign ? "" : "-fx-opacity: 0.92;"));

        StackPane avatar = new StackPane(loadAvatar(canReassign));
        avatar.setPrefSize(52, 52);
        avatar.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 26;");
        VBox text = new VBox(6);
        Label name = new Label("Name: " + safe(row.getTaDisplayName()));
        name.setStyle("-fx-font-weight: 700; -fx-text-fill: #1c3558;");
        Label qm = new Label("QMID: " + safe(row.getTaUserId()));
        qm.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600; -fx-font-size: 12;");
        String modTxt = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank()) {
            modTxt = modTxt + " - " + row.getModuleName();
        }
        Label course = new Label("Course application: " + modTxt);
        course.setStyle("-fx-font-weight: 700; -fx-text-fill: #165696;");
        Label status = new Label(statusText(row.getStatus()));
        status.setStyle("-fx-font-weight: 700; -fx-text-fill: " + statusColorHex(row.getStatus()) + ";");
        text.getChildren().addAll(name, qm, course, status);

        Button action = new Button(canReassign ? "Adjust" : "Summary");
        action.setStyle(
                "-fx-background-color: #2167f7; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 8;");
        action.setOnAction(e -> {
            if (canReassign) {
                openReassignFlow(row);
            } else {
                showApplicantSummary(row);
            }
        });
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        card.getChildren().addAll(avatar, text, grow, action);
        return card;
    }

    private Node loadAvatar(boolean active) {
        File f = findIconFile("学生.png", "student.png");
        if (f != null && f.isFile()) {
            ImageView iv = new ImageView(new Image(f.toURI().toString(), 44, 44, true, true));
            iv.setOpacity(active ? 1.0 : 0.45);
            return iv;
        }
        Label l = new Label("TA");
        l.setStyle("-fx-font-weight: 800; -fx-text-fill: #2167f7;");
        return l;
    }

    private File findIconFile(String... names) {
        for (String n : names) {
            File p = new File(ICON_DIR, n);
            if (p.isFile()) {
                return p;
            }
        }
        return null;
    }

    private void showModuleJobDialog(ModulePosting m) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(m.getModuleCode() != null ? m.getModuleCode() : "Module");
        alert.setHeaderText(m.getModuleCode() + " — Job posting");
        VBox body = new VBox(10,
                detailLabel("Description", m.getDescription()),
                detailLabel("Requirements", m.getRequirements()));
        body.setPadding(new Insets(8));
        alert.getDialogPane().setContent(body);
        alert.showAndWait();
    }

    private Node detailLabel(String k, String v) {
        VBox vb = new VBox(4);
        Label key = new Label(k);
        key.setStyle("-fx-font-weight: 800; -fx-text-fill: #64748b;");
        Label val = new Label(v == null ? "-" : v);
        val.setWrapText(true);
        val.setStyle("-fx-text-fill: #0f172a;");
        vb.getChildren().addAll(key, val);
        return vb;
    }

    private void showApplicantSummary(ApplicationCardRow row) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Application summary");
        String modTxt = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank()) {
            modTxt = modTxt + " - " + row.getModuleName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(safe(row.getTaDisplayName())).append('\n');
        sb.append("QMID: ").append(safe(row.getTaUserId())).append('\n');
        sb.append("Application ID: ").append(safe(row.getApplicationId())).append('\n');
        sb.append("Course: ").append(modTxt).append('\n');
        sb.append("Status: ").append(statusText(row.getStatus())).append('\n');
        sb.append("TA accepts reassignment: ").append(row.isAllowAdjustment() ? "Yes" : "No").append("\n\n");
        sb.append("Reassign is only available when status is Waiting for adjustment.");
        if (row.getCvFilePath() != null && !row.getCvFilePath().isBlank()) {
            sb.append("\n\nCV: ").append(row.getCvFilePath());
        }
        a.setContentText(sb.toString());
        a.showAndWait();
    }

    private void openReassignFlow(ApplicationCardRow row) {
        if (adminService.hasUnreviewedApplications()) {
            Alert w = new Alert(Alert.AlertType.WARNING);
            w.setTitle("Cannot reassign");
            w.setHeaderText("MO review incomplete");
            StringBuilder msg = new StringBuilder(
                    "All MOs must review all submitted CVs before admin can start reassignment.");
            List<String> pending = adminService.listMoPendingSubmittedSummaryLines();
            if (!pending.isEmpty()) {
                msg.append("\n\nStill pending:\n");
                for (String line : pending) {
                    msg.append("\u2022 ").append(line).append('\n');
                }
            }
            w.setContentText(msg.toString().trim());
            w.showAndWait();
            return;
        }
        List<ModulePosting> targets = adminService.listReassignableCourses();
        openReassignDialog(row, targets);
    }

    private void openReassignDialog(ApplicationCardRow row, List<ModulePosting> targets) {
        Alert base = new Alert(Alert.AlertType.NONE);
        base.setTitle("TA reassign");
        base.setHeaderText(safe(row.getTaDisplayName()) + " (" + safe(row.getTaUserId()) + ")");
        VBox content = new VBox(12);
        String cv = row.getCvFilePath();
        Button dl = new Button(cv != null && !cv.isBlank() ? "Open CV" : "No CV file");
        dl.setDisable(cv == null || cv.isBlank());
        dl.setOnAction(e -> DataFileOpen.openRelativePath(cv));
        MenuButton reassign = new MenuButton("Reassign to\u2026");
        boolean canReassign = row.isAllowAdjustment() && targets != null && !targets.isEmpty();
        reassign.setDisable(!canReassign);
        if (targets != null) {
            for (ModulePosting m : targets) {
                String label = (m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId()) + " - "
                        + safe(m.getModuleName());
                MenuItem it = new MenuItem(label);
                String mid = m.getModuleId();
                it.setOnAction(e -> confirmReassign(row, mid, label));
                reassign.getItems().add(it);
            }
        }
        Button reject = new Button("Final reject");
        reject.setOnAction(e -> confirmReject(row));
        HBox actions = new HBox(12, dl, reassign, reject);
        actions.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(actions);
        base.getDialogPane().setContent(content);
        base.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        base.showAndWait();
    }

    private void confirmReassign(ApplicationCardRow row, String moduleId, String label) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Confirm");
        c.setContentText("Reassign this TA to:\n" + label);
        Optional<ButtonType> r = c.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        String adminId = currentAdmin != null ? currentAdmin.getQmId() : "";
        ActionResult res = adminService.reassignApplication(row.getApplicationId(), moduleId, adminId);
        new Alert(res.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                res.getMessage()).showAndWait();
        if (res.isSuccess()) {
            refreshAll();
        }
    }

    private void confirmReject(ApplicationCardRow row) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Confirm reject");
        c.setContentText("Reject this TA application?");
        Optional<ButtonType> r = c.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        String adminId = currentAdmin != null ? currentAdmin.getQmId() : "";
        ActionResult res = adminService.finalRejectApplication(row.getApplicationId(), adminId);
        new Alert(res.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                res.getMessage()).showAndWait();
        if (res.isSuccess()) {
            refreshAll();
        }
    }

    private String statusText(ApplicationStatus status) {
        if (status == null) {
            return "Submitted";
        }
        switch (status) {
            case ACCEPTED:
                return "Application approved";
            case REJECTED:
                return "Rejected";
            case WAITING_FOR_ASSIGNMENT:
                return "Waiting for adjustment";
            case REASSIGNED:
                return "Reassigned by admin";
            case SUBMITTED:
                return "Pending review";
            default:
                return status.name();
        }
    }

    private String statusColorHex(ApplicationStatus status) {
        if (status == null) {
            return "#64748b";
        }
        switch (status) {
            case ACCEPTED:
                return "#237338";
            case REJECTED:
                return "#d64a4a";
            case WAITING_FOR_ASSIGNMENT:
                return "#d99200";
            case REASSIGNED:
                return "#165696";
            case SUBMITTED:
            default:
                return "#64748b";
        }
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private void refreshAttentionRows() {
        attentionTable.getItems().clear();
        if (currentAdmin == null) {
            return;
        }
        List<AttentionRow> rows = new ArrayList<>();
        List<ApplicationCardRow> allApps = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        Map<String, Long> submittedByModule = allApps.stream()
                .filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED)
                .collect(Collectors.groupingBy(ApplicationCardRow::getModuleId, Collectors.counting()));

        for (CourseCardRow cr : adminService.listCourseRecruitment(CourseFilter.ALL)) {
            ModulePosting m = cr.getModule();
            if (m == null || m.getModuleId() == null) {
                continue;
            }
            String mid = m.getModuleId();
            int sub = submittedByModule.getOrDefault(mid, 0L).intValue();
            int total = Math.max(0, m.getVacanciesTotal());
            int filled = Math.max(0, m.getVacanciesFilled());
            int rem = cr.getRemaining();
            String vacTxt = filled + "/" + total;
            String moName = cr.getMoDisplayName();

            if (total > 0 && filled >= total && sub > 0) {
                rows.add(new AttentionRow(mid,
                        shortModuleLabel(m),
                        moName,
                        vacTxt,
                        String.valueOf(sub),
                        "Module full with pending submitted applications",
                        "high"));
            } else if (rem <= 0 && sub > 2) {
                rows.add(new AttentionRow(mid,
                        shortModuleLabel(m),
                        moName,
                        vacTxt,
                        String.valueOf(sub),
                        "High pending reviews relative to remaining capacity",
                        "medium"));
            } else if (m.getStatus() == ModuleStatus.OPEN && rem <= 0 && sub == 0 && total > 0) {
                rows.add(new AttentionRow(mid,
                        shortModuleLabel(m),
                        moName,
                        vacTxt,
                        "0",
                        "No vacancies remaining; confirm module closure if recruitment ended",
                        "low"));
            }
        }

        List<String> moPending = adminService.listMoPendingSubmittedSummaryLines();
        if (!moPending.isEmpty()) {
            rows.add(new AttentionRow(null,
                    "System",
                    "\u2014",
                    "\u2014",
                    String.valueOf(moPending.size()),
                    "MO(s) still reviewing submitted CVs \u2014 reassignment blocked",
                    "medium"));
        }

        attentionTable.getItems().setAll(rows);
    }

    private Node buildAiInsightBody() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(0, 0, 6, 0));
        root.getChildren().add(buildAppTitleBar());

        Label head = new Label("AI Recruitment Insight");
        head.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label sub = new Label(
                "Pick module + TA, run insight. Output is from your configured chat API only; hiring decisions stay with staff.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        aiInsightMetaChip = new Label("Awaiting insight");
        aiInsightMetaChip.setStyle(
                "-fx-padding: 6 12 6 12; -fx-background-radius: 999; -fx-background-color: #e2e8f0; -fx-text-fill: #475569;"
                        + "-fx-font-size: 12px; -fx-font-weight: 700;");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(14, new VBox(4, head, sub), titleSpacer, aiInsightMetaChip);
        titleRow.setAlignment(Pos.TOP_LEFT);

        aiModuleCombo = new ComboBox<>();
        aiModuleCombo.setMaxWidth(Double.MAX_VALUE);
        aiModuleCombo.setPromptText("Module");
        aiModuleCombo.setConverter(modulePostingConverter());
        styleAiCombo(aiModuleCombo);

        aiProfileCombo = new ComboBox<>();
        aiProfileCombo.setMaxWidth(Double.MAX_VALUE);
        aiProfileCombo.setPromptText("TA profile");
        aiProfileCombo.setConverter(taProfileConverter());
        styleAiCombo(aiProfileCombo);

        Label mLab = sectionFieldLabel("Module");
        Label pLab = sectionFieldLabel("TA profile");
        VBox modCol = new VBox(5, mLab, aiModuleCombo);
        modCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modCol, Priority.ALWAYS);
        VBox taCol = new VBox(5, pLab, aiProfileCombo);
        taCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(taCol, Priority.ALWAYS);

        aiRunButton = new Button("Run insight");
        aiRunButton.setGraphic(icon(FontAwesomeSolid.SYNC_ALT, 14, "#ffffff"));
        aiRunButton.setStyle(
                "-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 10;"
                        + "-fx-padding: 10 18 10 18; -fx-cursor: hand; -fx-font-size: 13px;");
        aiRunButton.setMinHeight(40);
        aiRunButton.setOnAction(e -> runAiInsight());
        VBox btnCol = new VBox();
        btnCol.setAlignment(Pos.BOTTOM_LEFT);
        btnCol.getChildren().add(aiRunButton);

        HBox inputRow = new HBox(14, modCol, taCol, btnCol);
        inputRow.setAlignment(Pos.BOTTOM_LEFT);
        Node inputCard = wrapAiCard(inputRow);

        aiVerdictIcon = icon(FontAwesomeSolid.QUESTION_CIRCLE, 32, "#94a3b8");
        aiVerdictTitle = new Label("—");
        aiVerdictTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #64748b;");
        aiVerdictSubline = new Label("Run insight to see a recommendation summary.");
        aiVerdictSubline.setWrapText(true);
        aiVerdictSubline.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        VBox verdictLeft = new VBox(5, new HBox(12, aiVerdictIcon, aiVerdictTitle), aiVerdictSubline);
        verdictLeft.setMaxWidth(420);

        Label scoreCap = new Label("Match score");
        scoreCap.setStyle("-fx-font-weight: 700; -fx-text-fill: #475569; -fx-font-size: 12px;");
        aiScoreValue = new Label("— / 100");
        aiScoreValue.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #64748b;");
        aiMatchProgress = new ProgressBar(0);
        aiMatchProgress.setMaxWidth(Double.MAX_VALUE);
        aiMatchProgress.setPrefHeight(9);
        aiMatchProgress.setStyle("-fx-accent: #cbd5e1;");
        Node scoreTickRow = buildScoreTickRow();
        VBox verdictRight = new VBox(5, scoreCap, aiScoreValue, aiMatchProgress, scoreTickRow);
        verdictRight.setMinWidth(176);
        verdictRight.setMaxWidth(240);
        HBox verdictInner = new HBox(20, verdictLeft, verdictRight);
        verdictInner.setAlignment(Pos.CENTER_LEFT);
        verdictInner.setPadding(new Insets(4, 0, 4, 0));

        aiVerdictCard = new BorderPane();
        aiVerdictCard.setCenter(verdictInner);
        aiVerdictCard.setPadding(new Insets(12, 16, 12, 16));
        aiVerdictCard.setStyle(
                "-fx-background-color: #f8fafc; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12;");

        aiMatchedFlow = new FlowPane(6, 6);
        aiMatchedFlow.setMaxWidth(Double.MAX_VALUE);
        Label emptyEmoji = new Label("\uD83D\uDE10");
        emptyEmoji.setStyle("-fx-font-size: 18px;");
        Label emptyTxt = new Label("No directly matched skills found");
        emptyTxt.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 600;");
        aiMatchedEmptyBox = new VBox(5, emptyEmoji, emptyTxt);
        aiMatchedEmptyBox.setAlignment(Pos.CENTER_LEFT);
        aiMatchedEmptyBox.setPadding(new Insets(8, 10, 8, 10));
        aiMatchedEmptyBox.setStyle("-fx-background-color: #ecfdf5; -fx-background-radius: 8; -fx-border-color: #bbf7d0; -fx-border-radius: 8;");
        aiMatchedStack = new StackPane(aiMatchedFlow, aiMatchedEmptyBox);
        aiMatchedStack.setAlignment(Pos.TOP_LEFT);
        aiMatchedFlow.setVisible(false);

        aiMissingFlow = new FlowPane(6, 6);
        aiMissingFlow.setMaxWidth(Double.MAX_VALUE);

        aiWorkloadVBox = new VBox(6);
        aiWorkloadVBox.setMaxWidth(Double.MAX_VALUE);
        aiWorkloadRiskBadge = new Label("Risk level: —");
        aiWorkloadRiskBadge.setStyle(
                "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #e2e8f0; -fx-text-fill: #475569;"
                        + "-fx-font-weight: 700; -fx-font-size: 12px;");

        final double skillColViewport = 108;
        Node matchedCard = aiInsightColumnCard(
                "Matched",
                FontAwesomeSolid.CHECK_CIRCLE,
                "#22c55e",
                "#ecfdf5",
                scrollCapped(aiMatchedStack, skillColViewport));
        Node missingCard = aiInsightColumnCard(
                "Missing",
                FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                "#f97316",
                "#fff7ed",
                scrollCapped(aiMissingFlow, skillColViewport));
        Node workloadCard = aiInsightColumnCard(
                "Workload",
                FontAwesomeSolid.CLOCK,
                "#2563eb",
                "#eff6ff",
                scrollCapped(new VBox(6, aiWorkloadVBox, aiWorkloadRiskBadge), skillColViewport));

        GridPane row3 = new GridPane();
        row3.setHgap(10);
        row3.setVgap(10);
        ColumnConstraints c33 = new ColumnConstraints();
        c33.setPercentWidth(33.34);
        row3.getColumnConstraints().addAll(c33, c33, c33);
        GridPane.setColumnIndex(matchedCard, 0);
        GridPane.setColumnIndex(missingCard, 1);
        GridPane.setColumnIndex(workloadCard, 2);
        row3.getChildren().addAll(matchedCard, missingCard, workloadCard);

        aiRationaleArea = new TextArea();
        aiRationaleArea.setEditable(false);
        aiRationaleArea.setWrapText(true);
        aiRationaleArea.setPrefRowCount(5);
        aiRationaleArea.setMinHeight(96);
        aiRationaleArea.setMaxHeight(128);
        aiRationaleArea.setPromptText("AI rationale will appear here.");
        aiRationaleArea.setStyle(
                "-fx-control-inner-background: #fafafa; -fx-background-color: #fafafa; -fx-border-color: #e7edf4;"
                        + "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-size: 12px;");
        VBox ratCol = new VBox(6, aiInsightSectionTitle("Rationale", FontAwesomeSolid.FILE_ALT, "#7c3aed"), aiRationaleArea);
        VBox.setVgrow(aiRationaleArea, Priority.ALWAYS);

        aiSuggestionBulletBox = new VBox(6);
        aiSuitableTagsFlow = new FlowPane(6, 6);
        aiSuitableTagsFlow.setMaxWidth(Double.MAX_VALUE);
        Label suitTitle = new Label("Upskill / other module areas:");
        suitTitle.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e40af; -fx-font-size: 11px;");
        VBox innerSuit = new VBox(6, suitTitle, aiSuitableTagsFlow);
        innerSuit.setPadding(new Insets(8, 10, 8, 10));
        innerSuit.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 10; -fx-border-color: #bfdbfe; -fx-border-radius: 10;");
        VBox sugCol = new VBox(
                6,
                aiInsightSectionTitle("Suggested action", FontAwesomeSolid.LIGHTBULB, "#2563eb"),
                aiSuggestionBulletBox,
                innerSuit);

        GridPane row2 = new GridPane();
        row2.setHgap(10);
        row2.setVgap(10);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        row2.getColumnConstraints().addAll(half, half);
        Node ratWrap = wrapAiCard(ratCol);
        Node sugWrap = wrapAiCard(sugCol);
        GridPane.setColumnIndex(ratWrap, 0);
        GridPane.setColumnIndex(sugWrap, 1);
        row2.getChildren().addAll(ratWrap, sugWrap);

        aiFooterDisclaimer = new Label(
                "AI-assisted, informational only. Apply school policy and holistic review.");
        aiFooterDisclaimer.setWrapText(true);
        aiFooterDisclaimer.setStyle(
                "-fx-text-fill: #1e40af; -fx-font-size: 11px; -fx-padding: 8 12 8 12; -fx-background-color: #eff6ff;"
                        + "-fx-background-radius: 10; -fx-border-color: #bfdbfe; -fx-border-radius: 10;");
        HBox foot = new HBox(10, icon(FontAwesomeSolid.INFO_CIRCLE, 14, "#2563eb"), aiFooterDisclaimer);
        foot.setAlignment(Pos.TOP_LEFT);

        VBox body = new VBox(10, titleRow, inputCard, aiVerdictCard, row3, row2, foot);
        root.getChildren().add(body);
        return root;
    }

    /** 0 / 50 / 100 labels aligned to bar ends and centre (not a single spaced string). */
    private static Node buildScoreTickRow() {
        String tickStyle = "-fx-text-fill: #94a3b8; -fx-font-size: 11px;";
        Label l0 = new Label("0");
        l0.setStyle(tickStyle);
        Label l50 = new Label("50");
        l50.setStyle(tickStyle);
        Label l100 = new Label("100");
        l100.setStyle(tickStyle);
        StackPane row = new StackPane(l0, l50, l100);
        row.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(l0, Pos.CENTER_LEFT);
        StackPane.setAlignment(l50, Pos.CENTER);
        StackPane.setAlignment(l100, Pos.CENTER_RIGHT);
        return row;
    }

    private static Label sectionFieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 13px;");
        return l;
    }

    private static void styleAiCombo(ComboBox<?> combo) {
        combo.setStyle(
                "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10;");
        combo.setPrefHeight(38);
    }

    private HBox aiInsightSectionTitle(String title, FontAwesomeSolid glyph, String iconColor) {
        FontIcon ic = icon(glyph, 14, iconColor);
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
        HBox row = new HBox(8, ic, t);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node aiInsightColumnCard(String title, FontAwesomeSolid glyph, String accent, String headerBg, Node body) {
        FontIcon ic = icon(glyph, 14, accent);
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
        HBox head = new HBox(8, ic, t);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(8, 10, 8, 10));
        head.setStyle("-fx-background-color: " + headerBg + "; -fx-background-radius: 10 10 0 0;");
        StackPane bodyWrap = new StackPane(body);
        bodyWrap.setPadding(new Insets(8, 10, 10, 10));
        VBox inner = new VBox(0, head, bodyWrap);
        return wrapAiCard(inner);
    }

    private static Label aiPill(String text, String bg, String border, String textColor) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; -fx-border-radius: 999; -fx-background-radius: 999;"
                + "-fx-padding: 4 10 4 10; -fx-text-fill: " + textColor + "; -fx-font-size: 11px; -fx-font-weight: 700;");
        return l;
    }

    private static void clearFlow(FlowPane flow) {
        flow.getChildren().clear();
    }

    private static String workloadRiskFromNote(String note) {
        if (note == null || note.isBlank()) {
            return "Unknown";
        }
        String n = note.toLowerCase();
        if (n.contains("could not parse")) {
            return "Unknown (parse)";
        }
        double sum = 0;
        int count = 0;
        String[] parts = note.replace("~", " ").split("[^0-9.]");
        for (String p : parts) {
            if (p.matches("\\d+(\\.\\d+)?")) {
                sum += Double.parseDouble(p);
                count++;
            }
        }
        if (count == 0) {
            return "Low (informational)";
        }
        if (sum > 22) {
            return "High";
        }
        if (sum > 14) {
            return "Medium";
        }
        return "Low";
    }

    private static String workloadRiskBadgeStyle(String level) {
        if ("High".equals(level)) {
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #fee2e2; -fx-text-fill: #b91c1c;"
                    + "-fx-font-weight: 700; -fx-font-size: 12px;";
        }
        if ("Medium".equals(level)) {
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #fef3c7; -fx-text-fill: #b45309;"
                    + "-fx-font-weight: 700; -fx-font-size: 12px;";
        }
        if (level.startsWith("Unknown")) {
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #e2e8f0; -fx-text-fill: #475569;"
                    + "-fx-font-weight: 700; -fx-font-size: 12px;";
        }
        return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #d1fae5; -fx-text-fill: #065f46;"
                + "-fx-font-weight: 700; -fx-font-size: 12px;";
    }

    private static StringConverter<ModulePosting> modulePostingConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ModulePosting m) {
                if (m == null) {
                    return "";
                }
                String code = m.getModuleCode() != null ? m.getModuleCode() : "";
                String name = m.getModuleName() != null ? m.getModuleName() : "";
                if (!code.isEmpty()) {
                    return code + (name.isEmpty() ? "" : " — " + name);
                }
                return m.getModuleId() != null ? m.getModuleId() : "";
            }

            @Override
            public ModulePosting fromString(String s) {
                return null;
            }
        };
    }

    private static StringConverter<TAProfile> taProfileConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(TAProfile p) {
                if (p == null) {
                    return "";
                }
                String n = p.getName() != null ? p.getName().trim() : "";
                String qm = p.getQmId() != null ? p.getQmId() : "";
                if (!n.isEmpty()) {
                    return n + " (" + qm + ")";
                }
                return qm;
            }

            @Override
            public TAProfile fromString(String s) {
                return null;
            }
        };
    }

    private void refreshAiSelectors() {
        if (aiModuleCombo == null || aiProfileCombo == null) {
            return;
        }
        String mid = aiModuleCombo.getValue() != null ? aiModuleCombo.getValue().getModuleId() : null;
        String qm = aiProfileCombo.getValue() != null ? aiProfileCombo.getValue().getQmId() : null;
        List<ModulePosting> mods = adminService.listAllModulesForInsight();
        List<TAProfile> profs = adminService.listAllTaProfilesForInsight();
        aiModuleCombo.getItems().setAll(mods);
        aiProfileCombo.getItems().setAll(profs);
        if (mid != null) {
            for (ModulePosting mm : mods) {
                if (mid.equals(mm.getModuleId())) {
                    aiModuleCombo.setValue(mm);
                    break;
                }
            }
        }
        if (qm != null) {
            for (TAProfile pp : profs) {
                if (qm.equals(pp.getQmId())) {
                    aiProfileCombo.setValue(pp);
                    break;
                }
            }
        }
    }

    private void runAiInsight() {
        if (currentAdmin == null) {
            new Alert(Alert.AlertType.WARNING, "Please login as Admin.").showAndWait();
            return;
        }
        ModulePosting m = aiModuleCombo.getValue();
        TAProfile p = aiProfileCombo.getValue();
        if (m == null || p == null) {
            new Alert(Alert.AlertType.WARNING, "Select a module and a TA profile.").showAndWait();
            return;
        }
        aiRunButton.setDisable(true);
        Task<RecruitmentInsightResult> task = new Task<>() {
            @Override
            protected RecruitmentInsightResult call() {
                return insightService.analyze(m, p);
            }
        };
        task.setOnSucceeded(ev -> {
            aiRunButton.setDisable(false);
            applyInsightResult(task.getValue());
        });
        task.setOnFailed(ev -> {
            aiRunButton.setDisable(false);
            Throwable t = task.getException();
            String msg = t != null ? t.getMessage() : "Unknown error";
            new Alert(Alert.AlertType.ERROR, "Insight failed: " + msg).showAndWait();
        });
        Thread th = new Thread(task, "recruitment-insight");
        th.setDaemon(true);
        th.start();
    }

    private void applyInsightResult(RecruitmentInsightResult r) {
        if (r == null) {
            return;
        }
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (!r.isSuccess()) {
            aiInsightMetaChip.setText(r.getSourceCaption() + " · " + time);
            aiInsightMetaChip.setStyle(
                    "-fx-padding: 6 12 6 12; -fx-background-radius: 999; -fx-background-color: #fef3c7; -fx-text-fill: #92400e;"
                            + "-fx-font-size: 12px; -fx-font-weight: 700;");
            boolean noConfig = r.getSource() == RecruitmentInsightResult.Source.ERROR_NO_CONFIG;
            aiVerdictTitle.setText(noConfig ? "API not configured" : "API request failed");
            aiVerdictTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #b45309;");
            aiVerdictSubline.setText("");
            aiVerdictIcon.setIconCode(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
            aiVerdictIcon.setIconColor(Paint.valueOf("#d97706"));
            aiVerdictIcon.setIconSize(32);
            aiScoreValue.setText("— / 100");
            aiScoreValue.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #64748b;");
            aiMatchProgress.setProgress(0);
            aiMatchProgress.setStyle("-fx-accent: #cbd5e1;");
            aiVerdictCard.setStyle(
                    "-fx-background-color: #f8fafc; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12;"
                            + "-fx-border-width: 1;");
            clearFlow(aiMatchedFlow);
            aiMatchedFlow.setVisible(false);
            aiMatchedEmptyBox.setVisible(true);
            clearFlow(aiMissingFlow);
            aiWorkloadVBox.getChildren().clear();
            Label errDetail = new Label(r.getRationale());
            errDetail.setWrapText(true);
            errDetail.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
            aiWorkloadVBox.getChildren().add(errDetail);
            aiWorkloadRiskBadge.setText("Risk level: —");
            aiWorkloadRiskBadge.setStyle(
                    "-fx-padding: 5 10 5 10; -fx-background-radius: 999; -fx-background-color: #e2e8f0; -fx-text-fill: #475569;"
                            + "-fx-font-weight: 700; -fx-font-size: 12px;");
            aiRationaleArea.setText(r.getRationale());
            aiSuggestionBulletBox.getChildren().clear();
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(FontAwesomeSolid.INFO_CIRCLE, "#2563eb",
                    "Fix configuration or retry after checking network and API quota."));
            clearFlow(aiSuitableTagsFlow);
            return;
        }

        String chipText = "Insight generated · " + r.getSourceCaption() + " · " + time;
        aiInsightMetaChip.setText(chipText);
        aiInsightMetaChip.setStyle(
                "-fx-padding: 6 12 6 12; -fx-background-radius: 999; -fx-background-color: #d1fae5; -fx-text-fill: #065f46;"
                        + "-fx-font-size: 12px; -fx-font-weight: 700;");

        int score = r.getMatchScore();
        aiScoreValue.setText(score + " / 100");
        aiMatchProgress.setProgress(Math.max(0, Math.min(1, score / 100.0)));

        String verdict;
        String sub;
        FontAwesomeSolid verdictIcon;
        String iconColor;
        String cardBg;
        String cardBorder;
        String scoreColor;
        String barAccent;
        if (score >= 70) {
            verdict = "Recommended";
            sub = "Model assessment: strong fit for this posting.";
            verdictIcon = FontAwesomeSolid.CHECK_CIRCLE;
            iconColor = "#22c55e";
            cardBg = "#f0fdf4";
            cardBorder = "#bbf7d0";
            scoreColor = "#15803d";
            barAccent = "#22c55e";
        } else if (score >= 45) {
            verdict = "Consider with reservations";
            sub = "Model assessment: partial fit; review with interviews and policy.";
            verdictIcon = FontAwesomeSolid.EXCLAMATION_CIRCLE;
            iconColor = "#d97706";
            cardBg = "#fffbeb";
            cardBorder = "#fde68a";
            scoreColor = "#b45309";
            barAccent = "#f59e0b";
        } else {
            verdict = "Not recommended";
            sub = "Model assessment: weak fit for this posting.";
            verdictIcon = FontAwesomeSolid.TIMES_CIRCLE;
            iconColor = "#ef4444";
            cardBg = "#fef2f2";
            cardBorder = "#fecaca";
            scoreColor = "#b91c1c";
            barAccent = "#ef4444";
        }
        aiVerdictTitle.setText(verdict);
        aiVerdictTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: " + iconColor + ";");
        aiVerdictSubline.setText(sub);
        aiVerdictIcon.setIconCode(verdictIcon);
        aiVerdictIcon.setIconColor(Paint.valueOf(iconColor));
        aiVerdictIcon.setIconSize(32);
        aiScoreValue.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: " + scoreColor + ";");
        aiVerdictCard.setStyle(
                "-fx-background-color: " + cardBg + "; -fx-background-radius: 12; -fx-border-color: " + cardBorder
                        + "; -fx-border-radius: 12; -fx-border-width: 1;");
        aiMatchProgress.setStyle("-fx-accent: " + barAccent + ";");

        List<String> matched = r.getMatchedSkills();
        clearFlow(aiMatchedFlow);
        if (matched == null || matched.isEmpty()) {
            aiMatchedFlow.setVisible(false);
            aiMatchedEmptyBox.setVisible(true);
        } else {
            aiMatchedEmptyBox.setVisible(false);
            aiMatchedFlow.setVisible(true);
            for (String s : matched) {
                if (s != null && !s.isBlank()) {
                    aiMatchedFlow.getChildren().add(aiPill(s.trim(), "#ecfdf5", "#6ee7b7", "#065f46"));
                }
            }
        }

        clearFlow(aiMissingFlow);
        for (String s : r.getMissingHints()) {
            if (s != null && !s.isBlank()) {
                aiMissingFlow.getChildren().add(aiPill(s.trim(), "#fff7ed", "#fdba74", "#9a3412"));
            }
        }

        aiWorkloadVBox.getChildren().clear();
        String wNote = r.getWorkloadNote() == null ? "" : r.getWorkloadNote();
        for (String chunk : wNote.split(";")) {
            String line = chunk.trim();
            if (!line.isEmpty()) {
                Label lineLbl = new Label(line.endsWith(".") ? line : line + ".");
                lineLbl.setWrapText(true);
                lineLbl.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
                aiWorkloadVBox.getChildren().add(lineLbl);
            }
        }
        if (aiWorkloadVBox.getChildren().isEmpty()) {
            Label ph = new Label("No workload summary from the model.");
            ph.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            aiWorkloadVBox.getChildren().add(ph);
        }
        String risk = workloadRiskFromNote(wNote);
        aiWorkloadRiskBadge.setText("Risk level: " + risk);
        aiWorkloadRiskBadge.setStyle(workloadRiskBadgeStyle(risk));

        aiRationaleArea.setText(r.getRationale() == null ? "" : r.getRationale());

        aiSuggestionBulletBox.getChildren().clear();
        if (score < 45) {
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(FontAwesomeSolid.TIMES_CIRCLE, "#dc2626",
                    "Do not prioritise for this module based on the model's current assessment."));
        } else {
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(FontAwesomeSolid.INFO_CIRCLE, "#2563eb",
                    "Use this insight alongside interviews, references, and school policy."));
        }
        aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(FontAwesomeSolid.USER_CHECK, "#2563eb",
                "Consider this TA for modules closer to the model's matched-skill list when possible."));

        clearFlow(aiSuitableTagsFlow);
        List<String> tags = new ArrayList<>(r.getSuggestedSkillsToAdd());
        int cap = 8;
        for (int i = 0; i < tags.size() && i < cap; i++) {
            String t = tags.get(i);
            if (t != null && !t.isBlank()) {
                aiSuitableTagsFlow.getChildren().add(aiPill(t.trim(), "#eff6ff", "#60a5fa", "#1e3a8a"));
            }
        }
    }

    private HBox suggestionBulletRow(FontAwesomeSolid glyph, String color, String text) {
        FontIcon ic = icon(glyph, 13, color);
        Label lb = new Label(text);
        lb.setWrapText(true);
        lb.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
        HBox row = new HBox(8, ic, lb);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private String shortModuleLabel(ModulePosting m) {
        if (m == null) {
            return "";
        }
        String code = m.getModuleCode() != null ? m.getModuleCode() : "";
        String name = m.getModuleName() != null ? m.getModuleName() : "";
        if (!code.isEmpty()) {
            return code + (name.isEmpty() ? "" : " \u2014 " + name);
        }
        return m.getModuleId() != null ? m.getModuleId() : "";
    }

    private FontIcon icon(FontAwesomeSolid glyph, int size, String color) {
        FontIcon ic = new FontIcon(glyph);
        ic.setIconSize(size);
        ic.setIconColor(Paint.valueOf(color));
        return ic;
    }

    private Button iconButton(FontAwesomeSolid glyph, String tip) {
        Button b = new Button();
        b.setGraphic(icon(glyph, 16, "#64748b"));
        b.setStyle("-fx-background-color: transparent;");
        if (tip != null) {
            b.setTooltip(new Tooltip(tip));
        }
        return b;
    }
}
