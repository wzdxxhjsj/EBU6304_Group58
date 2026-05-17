package com.group58.recruit.ui.fx;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.ReassignActionType;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AdminDashboardDataService;
import com.group58.recruit.service.AdminDashboardDataService.AttentionRow;
import com.group58.recruit.service.AdminDashboardDataService.DashboardStats;
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
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
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
 * Admin dashboard (JavaFX) — Analyse page fully redesigned with:
 *   • KPI cards row (with delta hints)
 *   • PieChart for application status breakdown
 *   • BarChart for admin audit actions
 *   • Table with progress bars for module fill rates
 *   • Hot-routes list with heat bars
 *   • AreaChart for weekly reassignment trend
 */
public final class AdminDashboardFxView extends BorderPane {

    private static final String SIDEBAR_LOGO_PATH = "assets/icons/qmul-logo.png";
    private static final String ICON_DIR = "assets/icons";

    private final AdminService adminService = new AdminService();
    private final AdminDashboardDataService dashboardDataService =
            new AdminDashboardDataService(adminService);
    private final RecruitmentInsightService insightService = new RecruitmentInsightService();
    private final Runnable logoutAction;

    private User currentAdmin;

    // ── Title / header labels ────────────────────────────────────────────────
    private final Label adminIdentityLabel = new Label("Admin: -");

    // ── Overview page KPI labels ─────────────────────────────────────────────
    private final Label statModules   = new Label("0");
    private final Label statOpen      = new Label("0");
    private final Label statApps      = new Label("0");
    private final Label statPendingAdj= new Label("0");

    // ── Analyse page KPI labels (separate set so both pages refresh correctly)
    private final Label kpiModules    = new Label("0");
    private final Label kpiOpen       = new Label("0");
    private final Label kpiApps       = new Label("0");
    private final Label kpiPending    = new Label("0");
    private final Label kpiModDelta   = new Label("");
    private final Label kpiAppsDelta  = new Label("");

    // ── Overview side-panel summary (refreshed with refreshOverviewPanelSummaries) ──
    private final Label overviewCourseSummaryLabel = new Label();
    private final ProgressBar overviewCourseProgressBar = new ProgressBar(0);
    private final Label overviewApplicantSummaryLabel = new Label();
    private final ProgressBar overviewApplicantProgressBar = new ProgressBar(0);
    private final Label overviewApplicantProgressCaption = new Label();
    private final Label overviewCoursePercentLabel = new Label("0%");
    private final Label overviewApplicantPercentLabel = new Label("0%");

    // ── Card boxes & flow panes ──────────────────────────────────────────────
    private final VBox courseCardBox            = new VBox(12);
    private final VBox applicantCardBoxOverview = new VBox(12);
    private final VBox applicantCardBoxReassign = new VBox(12);
    private final FlowPane adjustmentFlowPane   = new FlowPane(12, 10);
    private final TableView<AttentionRow> attentionTable = new TableView<>();
    private final Label attentionSubtitle  = new Label();
    private final Label attentionEmptyHint = new Label();

    // ── Analyse page dynamic container ───────────────────────────────────────
    /** Root VBox of the Analyse page body; rebuilt on every refresh. */
    private final VBox analyseBodyRoot = new VBox(16);

    // ── Filters ──────────────────────────────────────────────────────────────
    private CourseFilter    courseFilter    = CourseFilter.ALL;
    private ApplicantFilter applicantFilter = ApplicantFilter.ALL;

    private final ToggleGroup courseTabGroup    = new ToggleGroup();
    private final ToggleGroup applicantTabGroup = new ToggleGroup();

    // ── Pages ────────────────────────────────────────────────────────────────
    private final VBox overviewPage    = new VBox(14);
    private final VBox analysePage     = new VBox(16);
    private final VBox reassignmentPage= new VBox(14);
    private final VBox aiPage          = new VBox(16);
    private final StackPane mainStack  = new StackPane();

    // ── AI insight widgets ───────────────────────────────────────────────────
    private ComboBox<ModulePosting> aiModuleCombo;
    private ComboBox<TAProfile>     aiProfileCombo;
    private Button   aiRunButton;
    private Label    aiInsightMetaChip;
    private BorderPane aiVerdictCard;
    private FontIcon aiVerdictIcon;
    private Label    aiVerdictTitle;
    private Label    aiVerdictSubline;
    private Label    aiScoreValue;
    private ProgressBar aiMatchProgress;
    private StackPane   aiMatchedStack;
    private FlowPane    aiMatchedFlow;
    private VBox        aiMatchedEmptyBox;
    private FlowPane    aiMissingFlow;
    private VBox        aiWorkloadVBox;
    private Label       aiWorkloadRiskBadge;
    private TextArea    aiRationaleArea;
    private VBox        aiSuggestionBulletBox;
    private FlowPane    aiSuitableTagsFlow;
    private Label       aiFooterDisclaimer;

    // ════════════════════════════════════════════════════════════════════════
    //  Constructor
    // ════════════════════════════════════════════════════════════════════════

    public AdminDashboardFxView(Runnable logoutAction) {
        this.logoutAction = logoutAction == null ? () -> {} : logoutAction;
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
            refreshAll();
            return;
        }
        currentAdmin = user;
        String display = (user.getName() == null || user.getName().isBlank())
                ? user.getQmId() : user.getName();
        adminIdentityLabel.setText("Admin: " + display + " (" + user.getQmId() + ")");
        refreshAll();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Page wiring
    // ════════════════════════════════════════════════════════════════════════

    private void buildPages() {
        // Overview
        overviewPage.setPadding(new Insets(0, 18, 18, 18));
        VBox.setVgrow(overviewPage, Priority.ALWAYS);
        ScrollPane overviewScroll = new ScrollPane(buildOverviewBody());
        overviewScroll.setFitToWidth(true);
        overviewScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(overviewScroll, Priority.ALWAYS);
        overviewPage.getChildren().add(overviewScroll);

        // Analyse
        analysePage.setPadding(new Insets(0, 18, 18, 18));
        VBox.setVgrow(analysePage, Priority.ALWAYS);
        ScrollPane analyseScroll = new ScrollPane(buildAnalyseBody());
        analyseScroll.setFitToWidth(true);
        analyseScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(analyseScroll, Priority.ALWAYS);
        analysePage.getChildren().add(analyseScroll);

        // Reassignment
        reassignmentPage.setPadding(new Insets(0, 18, 18, 18));
        VBox.setVgrow(reassignmentPage, Priority.ALWAYS);
        rebuildReassignmentPage();

        // AI
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

    private enum Page { OVERVIEW, ANALYSE, REASSIGNMENT, AI }

    private void showPage(Page page) {
        overviewPage.setVisible(page == Page.OVERVIEW);
        analysePage.setVisible(page == Page.ANALYSE);
        reassignmentPage.setVisible(page == Page.REASSIGNMENT);
        aiPage.setVisible(page == Page.AI);
        if (page == Page.REASSIGNMENT) {
            rebuildReassignmentPage();
        }
        if (page == Page.OVERVIEW || page == Page.REASSIGNMENT || page == Page.ANALYSE) {
            refreshAll();
        }
        if (page == Page.AI) refreshAiSelectors();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Sidebar
    // ════════════════════════════════════════════════════════════════════════

    private Node buildSidebar() {
        VBox bar = new VBox(14);
        bar.setPadding(new Insets(16, 12, 12, 12));
        bar.setPrefWidth(216);
        bar.setMinWidth(216);
        bar.setStyle("-fx-background-color: linear-gradient(to bottom, #0d63f3, #003c95);");

        Node logo = createSidebarLogo();
        Button overview = navRow(FontAwesomeSolid.HOME,         "Overview",      () -> showPage(Page.OVERVIEW));
        Button analyse  = navRow(FontAwesomeSolid.CHART_BAR,    "Analyse",       () -> showPage(Page.ANALYSE));
        Button reassign = navRow(FontAwesomeSolid.EXCHANGE_ALT, "Reassignment",  () -> showPage(Page.REASSIGNMENT));
        Button ai       = navRow(FontAwesomeSolid.ROBOT,        "AI",            () -> showPage(Page.AI));
        VBox nav = new VBox(8, overview, analyse, reassign, ai);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setGraphic(icon(FontAwesomeSolid.SIGN_OUT_ALT, 12, "#ffffff"));
        logout.setStyle(
                "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.6); "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: white; "
                + "-fx-font-size: 12; -fx-padding: 10 12 10 12;");
        logout.setOnAction(e -> { setCurrentUser(null); logoutAction.run(); });

        bar.getChildren().addAll(logo, nav, spacer, logout);
        return bar;
    }

    private Button navRow(FontAwesomeSolid glyph, String text, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(icon(glyph, 14, "#ffffff"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        String base = "-fx-background-color: transparent; -fx-text-fill: white; "
                    + "-fx-font-weight: 600; -fx-font-size: 13; "
                    + "-fx-padding: 10 12 10 12; -fx-background-radius: 10;";
        String hover= "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; "
                    + "-fx-font-weight: 600; -fx-font-size: 13; "
                    + "-fx-padding: 10 12 10 12; -fx-background-radius: 10;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited (e -> b.setStyle(base));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node createSidebarLogo() {
        StackPane frame = new StackPane();
        frame.setPrefSize(68, 68);
        File logoFile = new File(SIDEBAR_LOGO_PATH);
        if (logoFile.isFile()) {
            ImageView iv = new ImageView(new Image(logoFile.toURI().toString(), true));
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(64);
            iv.setFitHeight(64);
            frame.getChildren().add(iv);
            return frame;
        }
        Label fallback = new Label("\uD83C\uDF93");
        fallback.setStyle("-fx-font-size: 28; -fx-text-fill: white;");
        frame.getChildren().add(fallback);
        return frame;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Overview page
    // ════════════════════════════════════════════════════════════════════════

    private Node buildOverviewBody() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(8, 4, 12, 4));
        root.setFillWidth(true);
        root.getChildren().addAll(
                buildOverviewTopBar(),
                buildMainSplit(),
                buildAttentionSection());
        return root;
    }

    private Node buildOverviewTopBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(0, 4, 4, 4));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        adminIdentityLabel.setStyle("-fx-font-size: 14; -fx-font-weight: 700; -fx-text-fill: #334155;");
        StackPane avatar = new StackPane(icon(FontAwesomeSolid.USER, 14, "#2563eb"));
        avatar.setPrefSize(34, 34);
        avatar.setMinSize(34, 34);
        avatar.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 17;");
        bar.getChildren().addAll(grow, adminIdentityLabel, avatar,
                icon(FontAwesomeSolid.CHEVRON_DOWN, 12, "#94a3b8"));
        return bar;
    }


    // ════════════════════════════════════════════════════════════════════════
    //  Reassignment page — enhanced UI
    // ════════════════════════════════════════════════════════════════════════

    private void rebuildReassignmentPage() {
        ScrollPane reassignScroll = new ScrollPane(buildReassignmentBody());
        reassignScroll.setFitToWidth(true);
        reassignScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reassignScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reassignScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(reassignScroll, Priority.ALWAYS);
        reassignmentPage.getChildren().setAll(reassignScroll);
    }

    private Node buildReassignmentBody() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(0, 0, 18, 0));
        root.setFillWidth(true);
        root.getChildren().addAll(
                buildReassignmentHero(),
                buildReassignmentKpiStrip(),
                buildReassignmentContentGrid());
        return root;
    }

    private Node buildReassignmentHero() {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setStyle("-fx-background-color: linear-gradient(to right, #eff6ff, #ffffff); "
                + "-fx-background-radius: 16; -fx-border-color: #dbeafe; -fx-border-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");

        StackPane iconBox = new StackPane(icon(FontAwesomeSolid.EXCHANGE_ALT, 22, "#2563eb"));
        iconBox.setPrefSize(50, 50);
        iconBox.setMinSize(50, 50);
        iconBox.setStyle("-fx-background-color: #dbeafe; -fx-background-radius: 16;");

        VBox text = new VBox(5);
        Label title = new Label("Reassignment Control Centre");
        title.setStyle("-fx-font-size: 21; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label sub = new Label("Review TAs waiting for adjustment, check MO review blockers, "
                + "and move eligible applicants to available modules.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13;");
        text.getChildren().addAll(title, sub);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button refresh = new Button("Refresh");
        refresh.setGraphic(icon(FontAwesomeSolid.SYNC_ALT, 12, "#ffffff"));
        refresh.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; "
                + "-fx-font-weight: 800; -fx-background-radius: 10; -fx-padding: 9 14 9 14;");
        refresh.setOnAction(e -> refreshAll());

        card.getChildren().addAll(iconBox, text, refresh);
        return card;
    }

    private Node buildReassignmentKpiStrip() {
        FlowPane row = new FlowPane(12, 12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWrapLength(760);
        row.setMaxWidth(Double.MAX_VALUE);

        List<ApplicationCardRow> allApps = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        List<ApplicationCardRow> waiting = adminService.listApplicantDashboard(ApplicantFilter.WAITING_FOR_ADJUSTMENT);
        List<ModulePosting> targets = adminService.listReassignableCourses();

        int waitingCount = waiting.size();
        int targetSeatCount = targets.stream()
                .mapToInt(m -> Math.max(0, m.getVacanciesTotal() - m.getVacanciesFilled()))
                .sum();
        boolean blocked = adminService.hasUnreviewedApplications();

        row.getChildren().addAll(
                reassignmentMiniStat("Waiting TAs", String.valueOf(waitingCount),
                        "Applicants ready for admin decision", FontAwesomeSolid.HOURGLASS_HALF,
                        waitingCount > 0 ? "#d97706" : "#16a34a"),
                reassignmentMiniStat("Available seats", String.valueOf(targetSeatCount),
                        targets.size() + " target module(s)", FontAwesomeSolid.DOOR_OPEN,
                        targetSeatCount > 0 ? "#2563eb" : "#dc2626"),
                reassignmentMiniStat("MO review gate", blocked ? "Blocked" : "Clear",
                        blocked ? "Submitted CVs still need MO review" : "Reassignment can proceed",
                        blocked ? FontAwesomeSolid.LOCK : FontAwesomeSolid.CHECK_CIRCLE,
                        blocked ? "#dc2626" : "#16a34a"),
                reassignmentMiniStat("Total applications", String.valueOf(allApps.size()),
                        "Across all modules", FontAwesomeSolid.USERS, "#64748b"));

        return row;
    }

    private Node reassignmentMiniStat(String title, String value, String hint,
                                      FontAwesomeSolid glyph, String accent) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setPrefWidth(240);
        card.setMinWidth(180);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 14; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 12, 0.10, 0, 3);");

        StackPane ic = new StackPane(icon(glyph, 15, accent));
        ic.setPrefSize(38, 38);
        ic.setMinSize(38, 38);
        ic.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12;");

        VBox txt = new VBox(3);
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 11; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 22; -fx-font-weight: 900; -fx-text-fill: " + accent + ";");
        Label h = new Label(hint);
        h.setWrapText(true);
        h.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        txt.getChildren().addAll(t, v, h);

        card.getChildren().addAll(ic, txt);
        return card;
    }

    private Node buildReassignmentContentGrid() {
        VBox layout = new VBox(14);
        layout.setFillWidth(true);
        layout.setMaxWidth(Double.MAX_VALUE);

        Node applicantPanel = buildReassignmentApplicantPanel();
        Node sidePanel = buildReassignmentSidePanel();
        VBox.setVgrow(applicantPanel, Priority.ALWAYS);

        layout.getChildren().addAll(applicantPanel, sidePanel);
        return layout;
    }

    private Node buildReassignmentApplicantPanel() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("TA reassignment queue");
        title.setStyle("-fx-font-size: 15; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label chip = new Label("Click Adjust to move eligible TA");
        chip.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1d4ed8; "
                + "-fx-background-radius: 999; -fx-padding: 5 10 5 10; "
                + "-fx-font-size: 11; -fx-font-weight: 700;");
        head.getChildren().addAll(icon(FontAwesomeSolid.USERS, 14, "#2563eb"), title, grow, chip);

        Node tabs = buildApplicantTabs();

        ScrollPane listScroll = buildApplicantScrollFill(applicantCardBoxReassign);
        listScroll.setPrefHeight(520);
        listScroll.setMinHeight(360);
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        card.getChildren().addAll(head, tabs, listScroll);
        return card;
    }

    private Node buildReassignmentSidePanel() {
        VBox side = new VBox(14);
        side.setFillWidth(true);
        side.setMaxWidth(Double.MAX_VALUE);
        side.getChildren().addAll(buildReassignmentRulesCard(), buildReassignmentFlowCard());
        return side;
    }

    private Node buildReassignmentRulesCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Reassignment rules");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        head.getChildren().addAll(icon(FontAwesomeSolid.INFO_CIRCLE, 14, "#2563eb"), title);

        card.getChildren().addAll(
                head,
                ruleRow(FontAwesomeSolid.CHECK_CIRCLE, "#16a34a",
                        "Only TAs with status ‘Waiting for adjustment’ can be reassigned."),
                ruleRow(FontAwesomeSolid.LOCK, "#dc2626",
                        "Admin reassignment is blocked until all submitted CVs are reviewed by MOs."),
                ruleRow(FontAwesomeSolid.DOOR_OPEN, "#2563eb",
                        "Target modules must still have open vacancies."),
                ruleRow(FontAwesomeSolid.FILE_ALT, "#7c3aed",
                        "Use CV review before confirming reassignment or final rejection."));
        return card;
    }

    private Node ruleRow(FontAwesomeSolid glyph, String color, String text) {
        HBox row = new HBox(9);
        row.setAlignment(Pos.TOP_LEFT);
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12; -fx-text-fill: #475569;");
        row.getChildren().addAll(icon(glyph, 13, color), label);
        return row;
    }

    private Node buildReassignmentFlowCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Recent route map");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        head.getChildren().addAll(icon(FontAwesomeSolid.EXCHANGE_ALT, 14, "#2563eb"), title);

        adjustmentFlowPane.setPrefWrapLength(320);
        adjustmentFlowPane.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().addAll(head, adjustmentFlowPane);
        return card;
    }

    private Node buildAdminHeaderCard() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 14; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");
        adminIdentityLabel.setStyle("-fx-font-size: 20; -fx-font-weight: 800; -fx-text-fill: #1c3558;");
        card.getChildren().add(adminIdentityLabel);
        return card;
    }

    private Node buildStatsRow() {
        HBox row = new HBox(12);
        row.getChildren().addAll(
                miniStatCard("Total modules",      statModules,    "All modules published",   FontAwesomeSolid.BOOK),
                miniStatCard("Open recruitment",   statOpen,       "Modules with vacancies",  FontAwesomeSolid.DOOR_OPEN),
                miniStatCard("Total applications", statApps,       "All student applications",FontAwesomeSolid.USERS),
                miniStatCard("Pending adjustments",statPendingAdj, "Need admin attention",    FontAwesomeSolid.HOURGLASS_HALF));
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private Node miniStatCard(String title, Label value, String hint, FontAwesomeSolid glyph) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(14));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 14; "
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

    private static final String OVERVIEW_PANEL_STYLE =
            "-fx-background-color: white; -fx-background-radius: 14; "
                    + "-fx-border-color: #e7edf4; -fx-border-radius: 14; "
                    + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 12, 0.10, 0, 2);";
    private static final String OVERVIEW_SUMMARY_LINE_STYLE =
            "-fx-font-weight: 700; -fx-text-fill: #1f2937; -fx-font-size: 13;";
    private static final String OVERVIEW_PROGRESS_BAR_STYLE =
            "-fx-accent: #2e7ac4; -fx-control-inner-background: #e8eef8;";
    private static final String OVERVIEW_PROGRESS_CAPTION_STYLE =
            "-fx-text-fill: #64748b; -fx-font-size: 12;";
    private static final double OVERVIEW_SUMMARY_SECTION_MIN_HEIGHT = 108;
    private static final double OVERVIEW_PROGRESS_CAPTION_HEIGHT = 18;
    private static final double OVERVIEW_FILTER_ROW_HEIGHT = 36;

    private VBox buildOverviewSideCard(FontAwesomeSolid iconGlyph, String titleText, Node... sections) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setFillWidth(true);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(OVERVIEW_PANEL_STYLE);
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox.setMargin(card, Insets.EMPTY);

        StackPane iconBox = new StackPane(icon(iconGlyph, 14, "#2563eb"));
        iconBox.setPrefSize(30, 30);
        iconBox.setMinSize(30, 30);
        iconBox.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 8;");
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 15; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        HBox heading = new HBox(10, iconBox, title);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setMinHeight(30);
        card.getChildren().add(heading);
        card.getChildren().addAll(sections);
        return card;
    }

    private Node wrapOverviewFilterRow(Node row) {
        HBox slot = new HBox(row);
        slot.setAlignment(Pos.CENTER_LEFT);
        slot.setMinHeight(OVERVIEW_FILTER_ROW_HEIGHT);
        slot.setPrefHeight(OVERVIEW_FILTER_ROW_HEIGHT);
        slot.setMaxHeight(OVERVIEW_FILTER_ROW_HEIGHT);
        return slot;
    }

    private Node buildMainSplit() {
        HBox columns = new HBox(16);
        columns.setAlignment(Pos.TOP_LEFT);
        columns.setFillHeight(false);

        ScrollPane courseScroll = buildOverviewListScroll(courseCardBox);
        ScrollPane applicantScroll = buildOverviewListScroll(applicantCardBoxOverview);

        VBox left = buildOverviewSideCard(
                FontAwesomeSolid.BOOK,
                "Course recruitment",
                buildCourseProgressOverview(),
                wrapOverviewFilterRow(buildOverviewCourseTabs()),
                courseScroll);
        VBox right = buildOverviewSideCard(
                FontAwesomeSolid.USERS,
                "TA applications",
                buildApplicantProgressOverview(),
                wrapOverviewFilterRow(buildOverviewApplicantTabs()),
                applicantScroll);

        columns.getChildren().addAll(left, right);
        return columns;
    }

    private ScrollPane buildOverviewListScroll(VBox cardBox) {
        cardBox.setPadding(new Insets(0));
        cardBox.setSpacing(10);
        cardBox.setFillWidth(true);
        ScrollPane sp = new ScrollPane(cardBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(340);
        sp.setMinHeight(260);
        sp.setStyle("-fx-background: #f8fafc; -fx-background-color: #f8fafc; "
                + "-fx-border-color: #e7edf4; -fx-border-radius: 10; -fx-background-radius: 10;");
        cardBox.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    private VBox buildOverviewSummarySection(
            Label summaryLine,
            ProgressBar progressBar,
            Label percentLabel,
            Node progressCaptionOrSpacer,
            Hyperlink actionLink) {
        VBox box = new VBox(8);
        box.setMinHeight(OVERVIEW_SUMMARY_SECTION_MIN_HEIGHT);
        summaryLine.setWrapText(true);
        summaryLine.setStyle(OVERVIEW_SUMMARY_LINE_STYLE);
        progressBar.setPrefHeight(8);
        progressBar.setStyle(OVERVIEW_PROGRESS_BAR_STYLE);
        HBox progressRow = new HBox(10);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        percentLabel.setMinWidth(40);
        percentLabel.setAlignment(Pos.CENTER_RIGHT);
        percentLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #2e7ac4; -fx-font-size: 13;");
        progressRow.getChildren().addAll(progressBar, percentLabel);
        actionLink.setStyle("-fx-font-size: 13;");
        box.getChildren().addAll(summaryLine, progressRow, progressCaptionOrSpacer, actionLink);
        return box;
    }

    private Node buildOverviewProgressCaptionSpacer() {
        Region spacer = new Region();
        spacer.setMinHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        spacer.setPrefHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        spacer.setMaxHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        return spacer;
    }

    private Node buildCourseProgressOverview() {
        Hyperlink allM = new Hyperlink("View all modules");
        allM.setOnAction(e -> showModulesSummaryAlert(
                adminService.listCourseRecruitment(CourseFilter.ALL)));
        return buildOverviewSummarySection(
                overviewCourseSummaryLabel,
                overviewCourseProgressBar,
                overviewCoursePercentLabel,
                buildOverviewProgressCaptionSpacer(),
                allM);
    }

    private Node buildApplicantProgressOverview() {
        overviewApplicantProgressCaption.setStyle(OVERVIEW_PROGRESS_CAPTION_STYLE);
        overviewApplicantProgressCaption.setMinHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        overviewApplicantProgressCaption.setPrefHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        overviewApplicantProgressCaption.setMaxHeight(OVERVIEW_PROGRESS_CAPTION_HEIGHT);
        Hyperlink allApps = new Hyperlink("View all applications");
        allApps.setOnAction(e -> showAllApplicationsSummaryAlert());
        return buildOverviewSummarySection(
                overviewApplicantSummaryLabel,
                overviewApplicantProgressBar,
                overviewApplicantPercentLabel,
                overviewApplicantProgressCaption,
                allApps);
    }

    private void refreshOverviewPanelSummaries() {
        List<CourseCardRow> courses = adminService.listCourseRecruitment(CourseFilter.ALL);
        long finished = courses.stream().filter(this::adminServiceIsFinished).count();
        int moduleTotal = courses.size();
        double moduleRatio = moduleTotal <= 0 ? 0 : (double) finished / moduleTotal;
        long openCount = courses.stream().filter(r -> {
            ModulePosting m = r.getModule();
            return m != null && m.getStatus() == ModuleStatus.OPEN && r.getRemaining() > 0;
        }).count();
        overviewCourseSummaryLabel.setText(moduleTotal == 0
                ? "No modules loaded."
                : finished + " / " + moduleTotal + " modules completed  \u00b7  " + openCount + " modules open");
        overviewCourseProgressBar.setProgress(moduleRatio);
        overviewCoursePercentLabel.setText(Math.round(moduleRatio * 100) + "%");

        DashboardStats stats = dashboardDataService.loadStats();
        int appTotal = stats.getApplicationCount();
        int waitingAdj = stats.getPendingAdjustmentCount();
        double waitingRatio = appTotal <= 0 ? 0 : (double) waitingAdj / appTotal;
        overviewApplicantSummaryLabel.setText(appTotal == 0
                ? "No applications loaded."
                : appTotal + " applications total  \u00b7  " + waitingAdj + " waiting for adjustment");
        overviewApplicantProgressBar.setProgress(waitingRatio);
        overviewApplicantPercentLabel.setText(Math.round(waitingRatio * 100) + "%");
        overviewApplicantProgressCaption.setText(appTotal == 0
                ? "No applications waiting for adjustment"
                : waitingAdj + " / " + appTotal + " applications waiting for adjustment");
    }

    private ToggleButton overviewTabBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(selected);
        applyOverviewTabStyle(b, selected);
        b.selectedProperty().addListener((obs, was, sel) -> applyOverviewTabStyle(b, sel));
        return b;
    }

    private void applyOverviewTabStyle(ToggleButton b, boolean selected) {
        if (selected) {
            b.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-font-size: 12; -fx-padding: 7 14 7 14; -fx-background-radius: 8;");
        } else {
            b.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: 700; "
                    + "-fx-font-size: 12; -fx-padding: 7 14 7 14; -fx-background-radius: 8;");
        }
    }

    private Node buildOverviewCourseTabs() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        ToggleButton all = overviewTabBtn("All", courseTabGroup, true);
        ToggleButton fin = overviewTabBtn("Finished", courseTabGroup, false);
        ToggleButton unf = overviewTabBtn("Unfinished", courseTabGroup, false);
        all.setOnAction(e -> { courseFilter = CourseFilter.ALL;        refreshCoursesOnly(); });
        fin.setOnAction(e -> { courseFilter = CourseFilter.FINISHED;   refreshCoursesOnly(); });
        unf.setOnAction(e -> { courseFilter = CourseFilter.UNFINISHED; refreshCoursesOnly(); });
        row.getChildren().addAll(all, fin, unf);
        return row;
    }

    private Node buildOverviewApplicantTabs() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        ToggleButton all = overviewTabBtn("All", applicantTabGroup, true);
        ToggleButton wait = overviewTabBtn("Waiting for adjustment", applicantTabGroup, false);
        all.setOnAction(e -> { applicantFilter = ApplicantFilter.ALL;                    refreshAll(); });
        wait.setOnAction(e -> { applicantFilter = ApplicantFilter.WAITING_FOR_ADJUSTMENT; refreshAll(); });
        row.getChildren().addAll(all, wait);
        return row;
    }

    private void showAllApplicationsSummaryAlert() {
        List<ApplicationCardRow> list = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Applications");
        a.setContentText(list.size() + " application(s) in total.");
        a.showAndWait();
    }

    private boolean adminServiceIsFinished(CourseCardRow row) {
        ModulePosting m = row.getModule();
        if (m == null) return true;
        if (m.getStatus() == ModuleStatus.FINISHED) return true;
        int t = Math.max(0, m.getVacanciesTotal());
        int f = Math.max(0, m.getVacanciesFilled());
        return t > 0 && f >= t;
    }

    private void showModulesSummaryAlert(List<CourseCardRow> rows) {
        String body = rows.stream().map(r -> {
            ModulePosting m = r.getModule();
            String code = m != null && m.getModuleCode() != null ? m.getModuleCode() : "";
            return code + " — " + (m != null && m.getModuleName() != null ? m.getModuleName() : "");
        }).collect(Collectors.joining("\n"));
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("All modules");
        a.setContentText(body.isBlank() ? "No modules." : body);
        a.showAndWait();
    }

    /** Reassignment page: tabs plus inline view-all link. */
    private Node buildApplicantTabs() {
        HBox wrap = new HBox(12, buildApplicantTabsRow());
        wrap.setAlignment(Pos.CENTER_LEFT);
        Hyperlink link = new Hyperlink("View all applications");
        link.setOnAction(e -> showAllApplicationsSummaryAlert());
        wrap.getChildren().add(link);
        return wrap;
    }

    private Node buildApplicantTabsRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: #e8eef8; -fx-background-radius: 8;");
        ToggleButton all  = tabBtn("All",                    applicantTabGroup, true);
        ToggleButton wait = tabBtn("Waiting for adjustment", applicantTabGroup, false);
        all.setOnAction (e -> { applicantFilter = ApplicantFilter.ALL;                    refreshAll(); });
        wait.setOnAction(e -> { applicantFilter = ApplicantFilter.WAITING_FOR_ADJUSTMENT; refreshAll(); });
        row.getChildren().addAll(all, wait);
        return row;
    }

    private ToggleButton tabBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.setStyle("-fx-font-weight: 700; -fx-font-size: 12; -fx-padding: 6 12 6 12;");
        return b;
    }

    private ScrollPane buildApplicantScrollFill(VBox cardBox) {
        cardBox.setPadding(new Insets(4));
        cardBox.setFillWidth(true);
        ScrollPane sp = new ScrollPane(cardBox);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setMinHeight(180);
        sp.setMaxHeight(Double.MAX_VALUE);
        sp.setStyle("-fx-background-color: #f8fafc;");
        cardBox.setStyle("-fx-background-color: #f8fafc;");
        return sp;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ANALYSE PAGE — redesigned
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Builds the static skeleton of the Analyse page.
     * Dynamic content is populated by {@link #refreshAnalyseCharts()}.
     */
    private Node buildAnalyseBody() {
        analyseBodyRoot.setPadding(new Insets(0, 0, 18, 0));
        // Content is filled by refreshAnalyseCharts() on first showPage(ANALYSE)
        return analyseBodyRoot;
    }

    /** Called from refreshAll() — rebuilds all Analyse content in place. */
    private void refreshAnalyseCharts() {
        analyseBodyRoot.getChildren().clear();

        // ── Top bar ──────────────────────────────────────────────────────────
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label pageTitle = new Label("Analyse");
        pageTitle.setStyle("-fx-font-size: 20; -fx-font-weight: 800; -fx-text-fill: #1f2937;");
        Region topGrow = new Region();
        HBox.setHgrow(topGrow, Priority.ALWAYS);
        Label refreshChip = new Label("Refreshed just now");
        refreshChip.setStyle(chipStyle());
        topBar.getChildren().addAll(pageTitle, topGrow, refreshChip);

        // ── KPI row ──────────────────────────────────────────────────────────
        List<CourseCardRow> courses = adminService.listCourseRecruitment(CourseFilter.ALL);
        List<ApplicationCardRow> allApps = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        DashboardStats stats = dashboardDataService.loadStats();

        kpiModules.setText(String.valueOf(stats.getModuleCount()));
        kpiOpen.setText(String.valueOf(stats.getOpenModuleCount()));
        kpiApps.setText(String.valueOf(stats.getApplicationCount()));
        kpiPending.setText(String.valueOf(stats.getPendingAdjustmentCount()));
        kpiModDelta.setText(stats.getOpenModuleCount() + " open this cycle");
        kpiModDelta.setStyle(deltaStyleNeutral());
        kpiAppsDelta.setText(stats.getPendingAdjustmentCount() > 0
                ? stats.getPendingAdjustmentCount() + " pending review" : "All reviewed");
        kpiAppsDelta.setStyle(stats.getPendingAdjustmentCount() > 0 ? deltaStyleWarn() : deltaStyleOk());

        HBox kpiRow = new HBox(10);
        kpiRow.getChildren().addAll(
            buildAnalyseKpiCard("Total modules",      kpiModules,  kpiModDelta,  FontAwesomeSolid.BOOK),
            buildAnalyseKpiCard("Open recruitment",   kpiOpen,     null,         FontAwesomeSolid.DOOR_OPEN),
            buildAnalyseKpiCard("Total applications", kpiApps,     kpiAppsDelta, FontAwesomeSolid.USERS),
            buildAnalyseKpiCard("Pending adjustments",kpiPending,  null,         FontAwesomeSolid.HOURGLASS_HALF)
        );
        for (Node n : kpiRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // ── Section 1: Application pipeline ─────────────────────────────────
        Label sec1 = sectionDivider("Application pipeline");

        GridPane row1 = twoColGrid();
        Node statusCard = buildStatusPieCard(allApps);
        Node auditCard  = buildAuditBarCard();
        GridPane.setColumnIndex(statusCard, 0);
        GridPane.setColumnIndex(auditCard,  1);
        row1.getChildren().addAll(statusCard, auditCard);

        // ── Section 2: Module fill rates ─────────────────────────────────────
        Label sec2 = sectionDivider("Module fill rates");
        Node fillCard = buildModuleFillTableCard(courses);

        // ── Section 3: Reassignment activity ────────────────────────────────
        Label sec3 = sectionDivider("Reassignment activity");

        GridPane row3 = twoColGrid();
        Node routesCard = buildHotRoutesCard();
        Node trendCard  = buildReassignTrendCard();
        GridPane.setColumnIndex(routesCard, 0);
        GridPane.setColumnIndex(trendCard,  1);
        row3.getChildren().addAll(routesCard, trendCard);

        // ── Disclaimer ────────────────────────────────────────────────────────
        HBox disclaimer = new HBox(8);
        disclaimer.setPadding(new Insets(8, 12, 8, 12));
        disclaimer.setStyle("-fx-background-color: #eff6ff; -fx-border-color: #bfdbfe; "
                + "-fx-border-radius: 8; -fx-background-radius: 8;");
        disclaimer.setAlignment(Pos.TOP_LEFT);
        FontIcon infoIc = icon(FontAwesomeSolid.INFO_CIRCLE, 14, "#2563eb");
        Label disclaimerTxt = new Label(
            "Analytics computed from applications, module postings, and reassignment audit logs. "
            + "Hiring decisions remain with staff.");
        disclaimerTxt.setWrapText(true);
        disclaimerTxt.setStyle("-fx-text-fill: #1e40af; -fx-font-size: 11;");
        HBox.setHgrow(disclaimerTxt, Priority.ALWAYS);
        disclaimer.getChildren().addAll(infoIc, disclaimerTxt);

        if (currentAdmin == null) {
            analyseBodyRoot.getChildren().addAll(topBar,
                    hintLabel("Log in as Admin to load analytics."));
            return;
        }

        analyseBodyRoot.getChildren().addAll(
            topBar, kpiRow,
            sec1, row1,
            sec2, fillCard,
            sec3, row3,
            disclaimer
        );
    }

    // ── Analyse KPI card ─────────────────────────────────────────────────────

    private Node buildAnalyseKpiCard(String title, Label value, Label delta,
                                      FontAwesomeSolid glyph) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setStyle("-fx-background-color: white; -fx-border-color: #e7edf4; "
                + "-fx-border-radius: 12; -fx-background-radius: 12; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.12, 0, 3);");
        HBox labelRow = new HBox(6);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280; -fx-font-weight: 600;");
        labelRow.getChildren().addAll(icon(glyph, 13, "#64748b"), titleLbl);
        value.setStyle("-fx-font-size: 26; -fx-font-weight: 800; -fx-text-fill: #111827;");
        card.getChildren().addAll(labelRow, value);
        if (delta != null) card.getChildren().add(delta);
        return card;
    }

    // ── Status pie card ──────────────────────────────────────────────────────

    private Node buildStatusPieCard(List<ApplicationCardRow> apps) {
        long total = apps.size();
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) counts.put(s, 0L);
        for (ApplicationCardRow r : apps) {
            ApplicationStatus s = r.getStatus() != null ? r.getStatus() : ApplicationStatus.SUBMITTED;
            counts.merge(s, 1L, Long::sum);
        }

        VBox card = analyseCard();
        card.getChildren().add(buildCardHead(FontAwesomeSolid.CHART_PIE,
                "Status breakdown", total + " total"));

        if (total == 0) {
            card.getChildren().add(hintLabel("No applications in the system."));
            return card;
        }

        // Custom legend
        FlowPane legend = new FlowPane(12, 6);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            if (c == 0) continue;
            legend.getChildren().add(
                legendItem(statusColorHex(s),
                    shortStatusName(s) + ": " + c + " (" + safePct(c, total) + "%)"));
        }
        card.getChildren().add(legend);

        // PieChart
        PieChart pie = new PieChart();
        pie.setLegendVisible(false);
        pie.setLabelsVisible(false);
        pie.setAnimated(false);
        pie.setPrefHeight(210);
        pie.setMinHeight(180);

        for (ApplicationStatus s : ApplicationStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            if (c == 0) continue;
            PieChart.Data slice = new PieChart.Data(shortStatusName(s), c);
            pie.getData().add(slice);
        }
        // Apply colors after data nodes exist
        for (PieChart.Data d : pie.getData()) {
            String hex = statusColorHexByName(d.getName());
            if (d.getNode() != null) {
                d.getNode().setStyle("-fx-pie-color: " + hex + ";");
            }
            Tooltip.install(d.getNode() != null ? d.getNode() : new Region(),
                new Tooltip(d.getName() + ": " + (long) d.getPieValue()
                    + " (" + safePct((long) d.getPieValue(), total) + "%)"));
        }
        // Fallback: apply colors once scene is attached
        pie.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            for (PieChart.Data d : pie.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-pie-color: " + statusColorHexByName(d.getName()) + ";");
                }
            }
        });

        card.getChildren().add(pie);
        return card;
    }

    // ── Audit bar card ───────────────────────────────────────────────────────

    private Node buildAuditBarCard() {
        Map<ReassignActionType, Long> map = adminService.countReassignLogsByActionType();
        long r = map.getOrDefault(ReassignActionType.REASSIGN, 0L);
        long f = map.getOrDefault(ReassignActionType.FINAL_REJECT, 0L);
        long sum = r + f;

        VBox card = analyseCard();
        card.getChildren().add(buildCardHead(FontAwesomeSolid.CHART_BAR,
                "Admin audit actions", sum + " logged"));

        if (sum == 0) {
            card.getChildren().add(hintLabel(
                "No admin audit entries yet — actions appear after reassign or final reject."));
            return card;
        }

        FlowPane legend = new FlowPane(12, 6);
        legend.getChildren().addAll(
            legendItem("#378ADD", "Reassign: "    + r + " (" + safePct(r, sum) + "%)"),
            legendItem("#E24B4A", "Final reject: " + f + " (" + safePct(f, sum) + "%)")
        );
        card.getChildren().add(legend);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setTickUnit(1);
        yAxis.setMinorTickVisible(false);

        BarChart<String, Number> bar = new BarChart<>(xAxis, yAxis);
        bar.setLegendVisible(false);
        bar.setAnimated(false);
        bar.setPrefHeight(210);
        bar.setMinHeight(180);
        bar.setCategoryGap(40);
        bar.setBarGap(4);
        bar.setStyle("-fx-background-color: transparent;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        XYChart.Data<String, Number> dR = new XYChart.Data<>("Reassign",    r);
        XYChart.Data<String, Number> dF = new XYChart.Data<>("Final reject", f);
        series.getData().addAll(List.of(dR, dF));
        bar.getData().add(series);

        String styleR = "-fx-bar-fill: #378ADD; -fx-background-radius: 4 4 0 0;";
        String styleF = "-fx-bar-fill: #E24B4A; -fx-background-radius: 4 4 0 0;";
        if (dR.getNode() != null) dR.getNode().setStyle(styleR);
        if (dF.getNode() != null) dF.getNode().setStyle(styleF);
        bar.sceneProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (dR.getNode() != null) dR.getNode().setStyle(styleR);
            if (dF.getNode() != null) dF.getNode().setStyle(styleF);
        });

        card.getChildren().add(bar);
        return card;
    }

    // ── Module fill table card ───────────────────────────────────────────────

    private Node buildModuleFillTableCard(List<CourseCardRow> rawCourses) {
        List<CourseCardRow> courses = new ArrayList<>(rawCourses);
        courses.sort(Comparator.comparingInt(CourseCardRow::getRemaining).reversed());

        VBox card = analyseCard();
        card.getChildren().add(buildCardHead(FontAwesomeSolid.LIST_ALT,
                "Top vacancies spotlight",
                Math.min(10, courses.size()) + " modules shown"));

        // Table header
        HBox header = new HBox();
        header.setPadding(new Insets(0, 0, 6, 0));
        header.setStyle("-fx-border-color: #e7edf4; -fx-border-width: 0 0 0.5 0;");
        header.getChildren().addAll(
            fillTableColHeader("Module",    220),
            fillTableColHeader("MO",         90),
            fillTableColHeader("Fill rate",  180),
            fillTableColHeader("Remaining",   70),
            fillTableColHeader("Status",      90)
        );
        card.getChildren().add(header);

        int shown = 0;
        for (CourseCardRow cr : courses) {
            if (shown++ >= 10) break;
            ModulePosting m = cr.getModule();
            if (m == null) continue;
            card.getChildren().add(buildModuleFillRow(m, cr));
        }
        if (shown == 0) card.getChildren().add(hintLabel("No modules to display."));
        return card;
    }

    private Label fillTableColHeader(String text, double width) {
        Label l = new Label(text);
        l.setMinWidth(width);
        l.setPrefWidth(width);
        l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11; -fx-font-weight: 600;");
        return l;
    }

    private Node buildModuleFillRow(ModulePosting m, CourseCardRow cr) {
        int total   = Math.max(1, m.getVacanciesTotal());
        int filled  = Math.min(Math.max(0, m.getVacanciesFilled()), total);
        int rem     = total - filled;
        double pct  = filled / (double) total;
        String barColor = pct >= 1.0 ? "#639922" : rem >= 2 ? "#E24B4A" : "#BA7517";

        HBox row = new HBox();
        row.setPadding(new Insets(7, 0, 7, 0));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-color: #f1f5f9; -fx-border-width: 0 0 0.5 0;");

        // Module code + name
        VBox modCol = new VBox(2);
        modCol.setMinWidth(220);
        modCol.setPrefWidth(220);
        Label code = new Label(m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId());
        code.setStyle("-fx-font-size: 12; -fx-font-weight: 700; -fx-text-fill: #1e293b;");
        Label name = new Label(m.getModuleName() != null ? m.getModuleName() : "");
        name.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        modCol.getChildren().addAll(code, name);

        // MO
        Label moLbl = new Label(cr.getMoDisplayName());
        moLbl.setMinWidth(90);
        moLbl.setPrefWidth(90);
        moLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");

        // Fill rate col
        VBox fillCol = new VBox(3);
        fillCol.setMinWidth(180);
        fillCol.setPrefWidth(180);
        Label fillTxt = new Label(filled + " / " + total + " filled");
        fillTxt.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");
        ProgressBar pb = new ProgressBar(pct);
        pb.setPrefWidth(150);
        pb.setMaxWidth(150);
        pb.setStyle("-fx-accent: " + barColor + "; -fx-control-inner-background: #e8eef8;");
        fillCol.getChildren().addAll(fillTxt, pb);

        // Remaining
        Label remLbl = new Label(String.valueOf(rem));
        remLbl.setMinWidth(70);
        remLbl.setPrefWidth(70);
        remLbl.setStyle("-fx-font-size: 13; -fx-font-weight: 800; -fx-text-fill: " + barColor + ";");

        // Status badge
        Label badge;
        if (pct >= 1.0) badge = colorBadge("Filled", "#EAF3DE", "#27500A");
        else if (rem == 1) badge = colorBadge("1 left", "#FAEEDA", "#633806");
        else badge = colorBadge("Open", "#FCEBEB", "#791F1F");
        badge.setMinWidth(90);

        row.getChildren().addAll(modCol, moLbl, fillCol, remLbl, badge);
        return row;
    }

    // ── Hot routes card ──────────────────────────────────────────────────────

    private Node buildHotRoutesCard() {
        List<AdjustmentFlowEdge> edges = new ArrayList<>(adminService.listAdjustmentFlowEdges());
        edges.sort(Comparator.comparingInt(AdjustmentFlowEdge::getCount).reversed());

        VBox card = analyseCard();
        card.getChildren().add(buildCardHead(FontAwesomeSolid.EXCHANGE_ALT,
                "Hot reassignment routes", null));

        if (edges.isEmpty()) {
            card.getChildren().add(hintLabel("No reassignment routes recorded yet."));
            return card;
        }

        int maxCount = edges.get(0).getCount();
        int shown = 0;
        for (AdjustmentFlowEdge e : edges) {
            if (shown++ >= 8) break;
            card.getChildren().add(buildRouteRow(e, maxCount));
        }

        Label foot = new Label("Ranked by number of TA moves logged between module pairs.");
        foot.setWrapText(true);
        foot.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");
        card.getChildren().add(foot);
        return card;
    }

    private Node buildRouteRow(AdjustmentFlowEdge e, int maxCount) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        row.setStyle("-fx-border-color: #f1f5f9; -fx-border-width: 0 0 0.5 0;");

        Label from = new Label(e.getFromLabel());
        from.setMinWidth(80);
        from.setMaxWidth(80);
        from.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");

        Label arrow = new Label("\u2192");
        arrow.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12;");

        Label to = new Label(e.getToLabel());
        to.setMinWidth(80);
        to.setMaxWidth(80);
        to.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");

        ProgressBar heat = new ProgressBar(
                Math.min(1.0, e.getCount() / (double) Math.max(1, maxCount)));
        heat.setPrefWidth(80);
        heat.setPrefHeight(5);
        heat.setStyle("-fx-accent: #85B7EB; -fx-control-inner-background: #e8eef8;");

        Label cnt = new Label(String.valueOf(e.getCount()));
        cnt.setStyle("-fx-font-size: 13; -fx-font-weight: 800; "
                + "-fx-text-fill: #2167f7; -fx-min-width: 20;");

        row.getChildren().addAll(from, arrow, to, heat, cnt);
        return row;
    }

    // ── Reassignment trend card ──────────────────────────────────────────────

    private Node buildReassignTrendCard() {
        VBox card = analyseCard();
        card.getChildren().add(buildCardHead(FontAwesomeSolid.CHART_AREA,
                "Reassignments over time", "Last 8 weeks"));

        List<Long> weekCounts = dashboardDataService.approximateWeeklyReassignCounts(8);
        boolean hasData = weekCounts.stream().anyMatch(c -> c > 0);
        if (!hasData) {
            card.getChildren().add(hintLabel("No reassignment activity yet."));
            return card;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setAnimated(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(1);
        yAxis.setAnimated(false);

        AreaChart<String, Number> area = new AreaChart<>(xAxis, yAxis);
        area.setLegendVisible(false);
        area.setAnimated(false);
        area.setPrefHeight(210);
        area.setMinHeight(180);
        area.setStyle("-fx-background-color: transparent;");
        area.setCreateSymbols(true);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < weekCounts.size(); i++) {
            series.getData().add(new XYChart.Data<>("W" + (i + 1), weekCounts.get(i)));
        }
        area.getData().add(series);

        // Style the area once scene is available
        area.sceneProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            Node line = series.getNode().lookup(".chart-series-area-line");
            Node fill = series.getNode().lookup(".chart-series-area-fill");
            if (line != null) line.setStyle("-fx-stroke: #378ADD; -fx-stroke-width: 2;");
            if (fill != null) fill.setStyle("-fx-fill: rgba(55,138,221,0.12);");
        });

        card.getChildren().add(area);
        return card;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Attention section (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    private Node buildAttentionSection() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle(OVERVIEW_PANEL_STYLE);
        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);
        StackPane bell = new StackPane(icon(FontAwesomeSolid.BELL, 15, "#dc2626"));
        bell.setPrefSize(32, 32);
        bell.setStyle("-fx-background-color: #fef2f2; -fx-background-radius: 16;");
        Label t = new Label("Attention needed");
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #1f2937; -fx-font-size: 15;");
        attentionSubtitle.setWrapText(true);
        attentionSubtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12;");
        VBox titles = new VBox(2, t, attentionSubtitle);
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        Button refresh = new Button("Refresh list");
        refresh.setGraphic(icon(FontAwesomeSolid.SYNC_ALT, 11, "#2563eb"));
        refresh.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; "
                + "-fx-font-weight: 700; -fx-font-size: 12; -fx-padding: 4 8 4 8;");
        refresh.setOnAction(e -> refreshAttentionRows());
        head.getChildren().addAll(bell, titles, g, refresh);
        attentionEmptyHint.setWrapText(true);
        attentionEmptyHint.setVisible(false);
        attentionEmptyHint.setManaged(false);
        attentionEmptyHint.setStyle("-fx-text-fill: #237338; -fx-font-size: 12; -fx-font-weight: 600;");
        attentionTable.setFixedCellSize(44);
        attentionTable.setPrefHeight(220);
        attentionTable.setMinHeight(140);
        attentionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attentionTable.setStyle("-fx-background-color: white;");
        box.getChildren().addAll(head, attentionTable, attentionEmptyHint);
        return box;
    }

    private void bindAttentionTable() {
        TableColumn<AttentionRow, String> colModule = new TableColumn<>("Module");
        colModule.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModule()));

        TableColumn<AttentionRow, String> colMo = new TableColumn<>("MO");
        colMo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMo()));
        colMo.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setWrapText(false); }
                else { setText(item); setWrapText(true); }
            }
        });

        TableColumn<AttentionRow, String> colVac = new TableColumn<>("Vacancies");
        colVac.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVacancies()));

        TableColumn<AttentionRow, String> colWl = new TableColumn<>("Waiting list");
        colWl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getWaitlist()));

        TableColumn<AttentionRow, String> colIssue = new TableColumn<>("Issue");
        colIssue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIssue()));
        colIssue.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                AttentionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                setGraphic(attentionIssueBadge(row != null ? row.getIssue() : item, row));
            }
        });

        TableColumn<AttentionRow, AttentionRow> colAct = new TableColumn<>("Action");
        colAct.setCellValueFactory(cdf -> new SimpleObjectProperty<>(cdf.getValue()));
        colAct.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(AttentionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Button review = new Button("Review");
                review.setStyle("-fx-background-color: #eef4ff; -fx-text-fill: #1e4a8c; "
                        + "-fx-font-weight: 700; -fx-background-radius: 8;");
                review.setOnAction(e -> openAttentionReview(item));
                setGraphic(review);
            }
        });

        attentionTable.getColumns().setAll(
                List.of(colModule, colMo, colVac, colWl, colIssue, colAct));

        attentionTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(AttentionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                if ("high".equals(item.getSeverity()))        setStyle("-fx-background-color: #fff1f1;");
                else if ("medium".equals(item.getSeverity())) setStyle("-fx-background-color: #fff8eb;");
                else                                     setStyle("");
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Refresh helpers
    // ════════════════════════════════════════════════════════════════════════

    private void refreshAll() {
        refreshStats();
        refreshOverviewPanelSummaries();
        refreshAdjustmentFlow();
        refreshCoursesOnly();
        refreshApplicantsOnly();
        refreshAttentionRows();
        refreshAnalyseCharts();
    }

    private void refreshStats() {
        DashboardStats stats = dashboardDataService.loadStats();
        statModules.setText(String.valueOf(stats.getModuleCount()));
        statOpen.setText(String.valueOf(stats.getOpenModuleCount()));
        statApps.setText(String.valueOf(stats.getApplicationCount()));
        statPendingAdj.setText(String.valueOf(stats.getPendingAdjustmentCount()));
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
            Label empty = new Label(
                "No reassignment flows yet. Reassignments appear here after admin actions.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-wrap-text: true; -fx-max-width: 720;");
            adjustmentFlowPane.getChildren().add(empty);
            return;
        }
        for (AdjustmentFlowEdge e : edges) {
            HBox chip = new HBox(6);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setPadding(new Insets(8, 12, 8, 12));
            chip.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #dbe4ee; "
                    + "-fx-border-radius: 10; -fx-background-radius: 10;");
            Label txt = new Label(e.getFromLabel() + "  \u2192  " + e.getToLabel()
                    + "  (+" + e.getCount() + ")");
            txt.setStyle("-fx-font-weight: 600; -fx-text-fill: #1e293b;");
            chip.getChildren().add(txt);
            adjustmentFlowPane.getChildren().add(chip);
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
        for (CourseCardRow row : rows) courseCardBox.getChildren().add(buildOverviewCourseCard(row));
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
            box.getChildren().add(box == applicantCardBoxOverview
                    ? buildOverviewApplicantCard(row)
                    : buildApplicantCard(row));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Overview list cards
    // ════════════════════════════════════════════════════════════════════════

    private Node buildOverviewCourseCard(CourseCardRow row) {
        ModulePosting m = row.getModule();
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-border-color: #e7edf4; "
                + "-fx-border-radius: 12; -fx-background-radius: 12;");
        VBox text = new VBox(4);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label(safe(m.getModuleCode()) + " - " + safe(m.getModuleName()));
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: 700; -fx-text-fill: #0f172a; -fx-font-size: 14;");
        Label meta = new Label("MO: " + row.getMoDisplayName()
                + "  \u00b7  Vacancies: " + m.getVacanciesFilled() + "/" + m.getVacanciesTotal());
        meta.setWrapText(true);
        meta.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12; -fx-font-weight: 600;");
        text.getChildren().addAll(title, meta);
        boolean completed = row.getRemaining() <= 0 || adminServiceIsFinished(row);
        String statusTxt = completed ? "Recruitment completed" : "Recruitment in progress";
        String statusColor = completed ? "#16a34a" : "#d97706";
        FontAwesomeSolid statusIcon = completed ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.CLOCK;
        Label statusLbl = new Label(statusTxt);
        statusLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: " + statusColor + "; -fx-font-size: 12;");
        HBox status = new HBox(6, icon(statusIcon, 12, statusColor), statusLbl);
        status.setAlignment(Pos.CENTER_LEFT);
        status.setMinWidth(Region.USE_PREF_SIZE);
        Button open = new Button();
        open.setGraphic(icon(FontAwesomeSolid.CHEVRON_RIGHT, 12, "#94a3b8"));
        open.setStyle("-fx-background-color: transparent; -fx-padding: 4;");
        open.setOnAction(e -> showModuleJobDialog(m));
        card.getChildren().addAll(text, status, open);
        return card;
    }

    private Node buildOverviewApplicantCard(ApplicationCardRow row) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-border-color: #e7edf4; "
                + "-fx-border-radius: 12; -fx-background-radius: 12;");
        StackPane avatar = new StackPane(loadAvatar(true));
        avatar.setPrefSize(48, 48);
        avatar.setMinSize(48, 48);
        avatar.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 24;");
        VBox text = new VBox(5);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label name = new Label("Name: " + safe(row.getTaDisplayName()));
        name.setWrapText(true);
        name.setStyle("-fx-font-weight: 700; -fx-text-fill: #1c3558;");
        Label qm = new Label("QMID: " + safe(row.getTaUserId()));
        qm.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600; -fx-font-size: 12;");
        String modTxt = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank()) {
            modTxt = modTxt + " - " + row.getModuleName();
        }
        Label course = new Label("Course application: " + modTxt);
        course.setWrapText(true);
        course.setStyle("-fx-font-weight: 700; -fx-text-fill: #165696; -fx-font-size: 12;");
        Label status = colorBadge(
                statusText(row.getStatus()),
                statusBadgeBg(row.getStatus()),
                statusColorHex(row.getStatus()));
        text.getChildren().addAll(name, qm, course, status);
        Button summary = new Button("Summary");
        summary.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; "
                + "-fx-font-weight: 700; -fx-background-radius: 8; -fx-padding: 8 16 8 16;");
        summary.setOnAction(e -> showApplicantSummary(row));
        card.getChildren().addAll(avatar, text, summary);
        return card;
    }

    private Node attentionIssueBadge(String issueText, AttentionRow row) {
        String issue = issueText != null ? issueText : "";
        String lower = issue.toLowerCase();
        FontAwesomeSolid glyph;
        String bg;
        String fg;
        String shortLabel;
        if (lower.contains("waiting for adjustment")) {
            glyph = FontAwesomeSolid.EXCLAMATION_TRIANGLE;
            bg = "#fef3c7";
            fg = "#b45309";
            shortLabel = row != null && row.getWaitlist() != null && !row.getWaitlist().isBlank()
                    ? "Waiting for adjustment (" + row.getWaitlist() + " TA)"
                    : "Waiting for adjustment";
        } else if (lower.contains("mo review backlog") || lower.contains("submitted cv")) {
            glyph = FontAwesomeSolid.INFO_CIRCLE;
            bg = "#dbeafe";
            fg = "#1d4ed8";
            shortLabel = row != null && row.getWaitlist() != null
                    ? "MO review backlog (" + row.getWaitlist() + " submitted)"
                    : "MO review backlog";
        } else if (lower.contains("reassignment") || lower.contains("reassign")) {
            glyph = FontAwesomeSolid.EXCHANGE_ALT;
            bg = "#ede9fe";
            fg = "#6d28d9";
            shortLabel = "Reassignment queue";
        } else if (lower.contains("capacity full")) {
            glyph = FontAwesomeSolid.EXCLAMATION_CIRCLE;
            bg = "#fee2e2";
            fg = "#b91c1c";
            shortLabel = issue.length() > 42 ? issue.substring(0, 40) + "\u2026" : issue;
        } else {
            glyph = FontAwesomeSolid.FLAG;
            bg = "#f1f5f9";
            fg = "#475569";
            shortLabel = issue.length() > 48 ? issue.substring(0, 46) + "\u2026" : issue;
        }
        HBox badge = new HBox(6, icon(glyph, 11, fg));
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.setPadding(new Insets(4, 10, 4, 8));
        badge.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 999;");
        Label lbl = new Label(shortLabel);
        lbl.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 11; -fx-font-weight: 700;");
        badge.getChildren().add(lbl);
        return badge;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Course & Applicant cards (reassignment / legacy)
    // ════════════════════════════════════════════════════════════════════════

    private Node buildCourseCard(CourseCardRow row) {
        ModulePosting m = row.getModule();
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dbe4ee; "
                + "-fx-border-radius: 12; -fx-background-radius: 12;");
        String title = safe(m.getModuleCode()) + " - " + safe(m.getModuleName());
        Button nameBtn = new Button(title);
        nameBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2e7ac4; "
                + "-fx-font-weight: 800; -fx-font-size: 16; -fx-padding: 0;");
        nameBtn.setOnAction(e -> showModuleJobDialog(m));
        Label mo  = new Label("MO: " + row.getMoDisplayName());
        mo.setStyle("-fx-font-weight: 700; -fx-text-fill: #563070;");
        Label vac = new Label("Vacancies: " + m.getVacanciesFilled() + "/" + m.getVacanciesTotal());
        vac.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600;");
        Label st  = new Label(row.getRecruitmentStatusText());
        st.setStyle("-fx-font-weight: 700; -fx-text-fill: " + colorForRemaining(row.getRemaining()) + ";");
        card.getChildren().addAll(nameBtn, mo, vac, st);
        return card;
    }

    private String colorForRemaining(int remaining) {
        if (remaining <= 0) return "#237338";
        if (remaining == 1) return "#d99200";
        return "#d64a4a";
    }

    private Node buildApplicantCard(ApplicationCardRow row) {
        boolean canReassign = row.getStatus() == ApplicationStatus.WAITING_FOR_ASSIGNMENT;
        HBox card = new HBox(12);
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dbe4ee; "
                + "-fx-border-radius: 12; -fx-background-radius: 12;"
                + (canReassign ? "" : "-fx-opacity: 0.92;"));
        StackPane avatar = new StackPane(loadAvatar(canReassign));
        avatar.setPrefSize(52, 52);
        avatar.setStyle("-fx-background-color: #eef4ff; -fx-background-radius: 26;");
        VBox text = new VBox(6);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label name   = new Label("Name: " + safe(row.getTaDisplayName()));
        name.setWrapText(true);
        name.setStyle("-fx-font-weight: 700; -fx-text-fill: #1c3558;");
        Label qm     = new Label("QMID: " + safe(row.getTaUserId()));
        qm.setWrapText(true);
        qm.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600; -fx-font-size: 12;");
        String modTxt = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank())
            modTxt = modTxt + " - " + row.getModuleName();
        Label course = new Label("Course application: " + modTxt);
        course.setWrapText(true);
        course.setStyle("-fx-font-weight: 700; -fx-text-fill: #165696;");
        Label status = colorBadge(
                statusText(row.getStatus()),
                statusBadgeBg(row.getStatus()),
                statusColorHex(row.getStatus()));
        text.getChildren().addAll(name, qm, course, status);
        Button action = new Button(canReassign ? "Adjust" : "Summary");
        action.setStyle("-fx-background-color: " + (canReassign ? "#2563eb" : "#f1f5f9") + ";"
                + "-fx-text-fill: " + (canReassign ? "white" : "#475569") + ";"
                + "-fx-font-weight: 800; -fx-background-radius: 10; "
                + "-fx-padding: 8 14 8 14;");
        action.setOnAction(e -> {
            if (canReassign) openReassignFlow(row);
            else             showApplicantSummary(row);
        });
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        card.getChildren().addAll(avatar, text, action);
        return card;
    }

    private Node loadAvatar(boolean active) {
        File f = findIconFile("学生.png", "student.png");
        if (f != null && f.isFile()) {
            ImageView iv = new ImageView(
                    new Image(f.toURI().toString(), 44, 44, true, true));
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
            if (p.isFile()) return p;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Module / applicant dialogs (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    private void showModuleJobDialog(ModulePosting m) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(m.getModuleCode() != null ? m.getModuleCode() : "Module");
        alert.setHeaderText(m.getModuleCode() + " \u2014 Job posting");
        VBox body = new VBox(10,
                detailLabel("Description",  m.getDescription()),
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
        if (row.getModuleName() != null && !row.getModuleName().isBlank())
            modTxt = modTxt + " - " + row.getModuleName();
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(safe(row.getTaDisplayName())).append('\n');
        sb.append("QMID: ").append(safe(row.getTaUserId())).append('\n');
        sb.append("Application ID: ").append(safe(row.getApplicationId())).append('\n');
        sb.append("Course: ").append(modTxt).append('\n');
        sb.append("Status: ").append(statusText(row.getStatus())).append('\n');
        sb.append("TA accepts reassignment: ").append(row.isAllowAdjustment() ? "Yes" : "No")
          .append("\n\nReassign is only available when status is Waiting for adjustment.");
        if (row.getCvFilePath() != null && !row.getCvFilePath().isBlank())
            sb.append("\n\nCV: ").append(row.getCvFilePath());
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
                for (String line : pending) msg.append("\u2022 ").append(line).append('\n');
            }
            w.setContentText(msg.toString().trim());
            w.showAndWait();
            return;
        }
        openReassignDialog(row, adminService.listReassignableCourses());
    }

    private void openReassignDialog(ApplicationCardRow row, List<ModulePosting> targets) {
        Alert base = new Alert(Alert.AlertType.NONE);
        base.setTitle("TA reassign");
        base.setHeaderText(safe(row.getTaDisplayName()) + " (" + safe(row.getTaUserId()) + ")");
        VBox content = new VBox(12);
        String cv  = row.getCvFilePath();
        Button dl  = new Button(cv != null && !cv.isBlank() ? "Open CV" : "No CV file");
        dl.setDisable(cv == null || cv.isBlank());
        dl.setOnAction(e -> DataFileOpen.openRelativePath(cv));
        MenuButton reassign = new MenuButton("Reassign to\u2026");
        boolean canReassign = row.isAllowAdjustment() && targets != null && !targets.isEmpty();
        reassign.setDisable(!canReassign);
        if (targets != null) {
            for (ModulePosting m : targets) {
                String label = safe(m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId())
                             + " - " + safe(m.getModuleName());
                MenuItem it = new MenuItem(label);
                String mid  = m.getModuleId();
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
        if (r.isEmpty() || r.get() != ButtonType.OK) return;
        String adminId = currentAdmin != null ? currentAdmin.getQmId() : "";
        ActionResult res = adminService.reassignApplication(
                row.getApplicationId(), moduleId, adminId);
        new Alert(res.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                res.getMessage()).showAndWait();
        if (res.isSuccess()) refreshAll();
    }

    private void confirmReject(ApplicationCardRow row) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Confirm reject");
        c.setContentText("Reject this TA application?");
        Optional<ButtonType> r = c.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;
        String adminId = currentAdmin != null ? currentAdmin.getQmId() : "";
        ActionResult res = adminService.finalRejectApplication(
                row.getApplicationId(), adminId);
        new Alert(res.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                res.getMessage()).showAndWait();
        if (res.isSuccess()) refreshAll();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Attention rows (unchanged logic)
    // ════════════════════════════════════════════════════════════════════════

    private void openAttentionReview(AttentionRow row) {
        if (row == null) return;
        if (row.getModuleId() == null) {
            StringBuilder detail = new StringBuilder(row.getIssue());
            if (!row.isReassignmentQueueSummary()) {
                List<String> pending = adminService.listMoPendingSubmittedSummaryLines();
                if (!pending.isEmpty()) {
                    detail.append("\n\n");
                    for (String line : pending) detail.append("\u2022 ").append(line).append('\n');
                }
            }
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Attention");
            a.setContentText(detail.toString().trim());
            a.showAndWait();
            return;
        }
        ModulePosting m = findModulePosting(row.getModuleId());
        if (m != null) showModuleJobDialog(m);
        else new Alert(Alert.AlertType.INFORMATION, row.getIssue()).showAndWait();
    }

    private ModulePosting findModulePosting(String moduleId) {
        if (moduleId == null) return null;
        for (CourseCardRow cr : adminService.listCourseRecruitment(CourseFilter.ALL)) {
            ModulePosting m = cr.getModule();
            if (m != null && moduleId.equals(m.getModuleId())) return m;
        }
        return null;
    }

    private void refreshAttentionRows() {
        attentionTable.getItems().clear();
        attentionSubtitle.setText("");
        attentionEmptyHint.setVisible(false);
        attentionEmptyHint.setManaged(false);
        if (currentAdmin == null) {
            attentionSubtitle.setText(
                "Log in to see routing risk, MO backlog, and reassignment queue.");
            return;
        }
        List<AttentionRow> rows = dashboardDataService.listAttentionRows();
        attentionTable.getItems().setAll(rows);
        attentionTable.setPrefHeight(Math.min(360,
                38 + attentionTable.getFixedCellSize() * (rows.size() + 1)));
        attentionSubtitle.setText(rows.isEmpty()
                ? "No open issues detected from current rules."
                : rows.size() + " item(s) \u2014 review MO backlog, capacity, or reassignment queue.");
        if (rows.isEmpty()) {
            attentionEmptyHint.setText(
                "All clear: no reassignment queue, no MO backlog rows, and no capacity warnings matched. "
                + "Use Analyse for workload charts.");
            attentionEmptyHint.setVisible(true);
            attentionEmptyHint.setManaged(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AI Insight page (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    private Node buildAiInsightBody() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(0, 0, 6, 0));

        Label head = new Label("AI Recruitment Insight");
        head.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label sub  = new Label(
            "Pick module + TA, run insight. Output is from your configured chat API only; "
            + "hiring decisions stay with staff.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        aiInsightMetaChip = new Label("Awaiting insight");
        aiInsightMetaChip.setStyle(
            "-fx-padding: 6 12 6 12; -fx-background-radius: 999; "
            + "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; "
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

        VBox modCol = new VBox(5, sectionFieldLabel("Module"), aiModuleCombo);
        modCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modCol, Priority.ALWAYS);
        VBox taCol  = new VBox(5, sectionFieldLabel("TA profile"), aiProfileCombo);
        taCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(taCol, Priority.ALWAYS);

        aiRunButton = new Button("Run insight");
        aiRunButton.setGraphic(icon(FontAwesomeSolid.SYNC_ALT, 14, "#ffffff"));
        aiRunButton.setStyle(
            "-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-font-weight: 700; "
            + "-fx-background-radius: 10; -fx-padding: 10 18 10 18; -fx-cursor: hand; "
            + "-fx-font-size: 13px;");
        aiRunButton.setMinHeight(40);
        aiRunButton.setOnAction(e -> runAiInsight());
        VBox btnCol = new VBox();
        btnCol.setAlignment(Pos.BOTTOM_LEFT);
        btnCol.getChildren().add(aiRunButton);

        HBox inputRow  = new HBox(14, modCol, taCol, btnCol);
        inputRow.setAlignment(Pos.BOTTOM_LEFT);
        Node inputCard = wrapAiCard(inputRow);

        aiVerdictIcon    = icon(FontAwesomeSolid.QUESTION_CIRCLE, 32, "#94a3b8");
        aiVerdictTitle   = new Label("\u2014");
        aiVerdictTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #64748b;");
        aiVerdictSubline = new Label("Run insight to see a recommendation summary.");
        aiVerdictSubline.setWrapText(true);
        aiVerdictSubline.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        VBox verdictLeft = new VBox(5,
                new HBox(12, aiVerdictIcon, aiVerdictTitle), aiVerdictSubline);
        verdictLeft.setMaxWidth(420);

        Label scoreCap = new Label("Match score");
        scoreCap.setStyle("-fx-font-weight: 700; -fx-text-fill: #475569; -fx-font-size: 12px;");
        aiScoreValue   = new Label("\u2014 / 100");
        aiScoreValue.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #64748b;");
        aiMatchProgress= new ProgressBar(0);
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
            "-fx-background-color: #f8fafc; -fx-background-radius: 12; "
            + "-fx-border-color: #e2e8f0; -fx-border-radius: 12;");

        aiMatchedFlow     = new FlowPane(6, 6);
        aiMatchedFlow.setMaxWidth(Double.MAX_VALUE);
        Label emptyEmoji  = new Label("\uD83D\uDE10");
        emptyEmoji.setStyle("-fx-font-size: 18px;");
        Label emptyTxt    = new Label("No directly matched skills found");
        emptyTxt.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 600;");
        aiMatchedEmptyBox = new VBox(5, emptyEmoji, emptyTxt);
        aiMatchedEmptyBox.setAlignment(Pos.CENTER_LEFT);
        aiMatchedEmptyBox.setPadding(new Insets(8, 10, 8, 10));
        aiMatchedEmptyBox.setStyle(
            "-fx-background-color: #ecfdf5; -fx-background-radius: 8; "
            + "-fx-border-color: #bbf7d0; -fx-border-radius: 8;");
        aiMatchedStack = new StackPane(aiMatchedFlow, aiMatchedEmptyBox);
        aiMatchedStack.setAlignment(Pos.TOP_LEFT);
        aiMatchedFlow.setVisible(false);

        aiMissingFlow = new FlowPane(6, 6);
        aiMissingFlow.setMaxWidth(Double.MAX_VALUE);

        aiWorkloadVBox      = new VBox(6);
        aiWorkloadVBox.setMaxWidth(Double.MAX_VALUE);
        aiWorkloadRiskBadge = new Label("Risk level: \u2014");
        aiWorkloadRiskBadge.setStyle(
            "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
            + "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; "
            + "-fx-font-weight: 700; -fx-font-size: 12px;");

        final double skillColViewport = 108;
        Node matchedCard  = aiInsightColumnCard("Matched",  FontAwesomeSolid.CHECK_CIRCLE,
                "#22c55e", "#ecfdf5", scrollCapped(aiMatchedStack, skillColViewport));
        Node missingCard  = aiInsightColumnCard("Missing",  FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                "#f97316", "#fff7ed", scrollCapped(aiMissingFlow, skillColViewport));
        Node workloadCard = aiInsightColumnCard("Workload", FontAwesomeSolid.CLOCK,
                "#2563eb", "#eff6ff",
                scrollCapped(new VBox(6, aiWorkloadVBox, aiWorkloadRiskBadge), skillColViewport));

        GridPane row3 = new GridPane();
        row3.setHgap(10); row3.setVgap(10);
        ColumnConstraints c33 = new ColumnConstraints();
        c33.setPercentWidth(33.34);
        row3.getColumnConstraints().addAll(c33, c33, c33);
        GridPane.setColumnIndex(matchedCard,  0);
        GridPane.setColumnIndex(missingCard,  1);
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
            "-fx-control-inner-background: #fafafa; -fx-background-color: #fafafa; "
            + "-fx-border-color: #e7edf4; -fx-border-radius: 10; "
            + "-fx-background-radius: 10; -fx-font-size: 12px;");
        VBox ratCol = new VBox(6,
                aiInsightSectionTitle("Rationale", FontAwesomeSolid.FILE_ALT, "#7c3aed"),
                aiRationaleArea);
        VBox.setVgrow(aiRationaleArea, Priority.ALWAYS);

        aiSuggestionBulletBox = new VBox(6);
        aiSuitableTagsFlow    = new FlowPane(6, 6);
        aiSuitableTagsFlow.setMaxWidth(Double.MAX_VALUE);
        Label suitTitle = new Label("Upskill / other module areas:");
        suitTitle.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e40af; -fx-font-size: 11px;");
        VBox innerSuit = new VBox(6, suitTitle, aiSuitableTagsFlow);
        innerSuit.setPadding(new Insets(8, 10, 8, 10));
        innerSuit.setStyle(
            "-fx-background-color: #eff6ff; -fx-background-radius: 10; "
            + "-fx-border-color: #bfdbfe; -fx-border-radius: 10;");
        VBox sugCol = new VBox(6,
                aiInsightSectionTitle("Suggested action", FontAwesomeSolid.LIGHTBULB, "#2563eb"),
                aiSuggestionBulletBox, innerSuit);

        GridPane row2 = new GridPane();
        row2.setHgap(10); row2.setVgap(10);
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
            "-fx-text-fill: #1e40af; -fx-font-size: 11px; -fx-padding: 8 12 8 12; "
            + "-fx-background-color: #eff6ff; -fx-background-radius: 10; "
            + "-fx-border-color: #bfdbfe; -fx-border-radius: 10;");
        HBox foot = new HBox(10,
                icon(FontAwesomeSolid.INFO_CIRCLE, 14, "#2563eb"), aiFooterDisclaimer);
        foot.setAlignment(Pos.TOP_LEFT);

        VBox body = new VBox(10, titleRow, inputCard, aiVerdictCard, row3, row2, foot);
        root.getChildren().add(body);
        return root;
    }

    private void refreshAiSelectors() {
        if (aiModuleCombo == null || aiProfileCombo == null) return;
        String mid = aiModuleCombo.getValue() != null
                ? aiModuleCombo.getValue().getModuleId() : null;
        String qm  = aiProfileCombo.getValue() != null
                ? aiProfileCombo.getValue().getQmId()  : null;
        List<ModulePosting> mods  = adminService.listAllModulesForInsight();
        List<TAProfile>     profs = adminService.listAllTaProfilesForInsight();
        aiModuleCombo.getItems().setAll(mods);
        aiProfileCombo.getItems().setAll(profs);
        if (mid != null) mods.stream().filter(m -> mid.equals(m.getModuleId()))
                .findFirst().ifPresent(aiModuleCombo::setValue);
        if (qm  != null) profs.stream().filter(p -> qm.equals(p.getQmId()))
                .findFirst().ifPresent(aiProfileCombo::setValue);
    }

    private void runAiInsight() {
        if (currentAdmin == null) {
            new Alert(Alert.AlertType.WARNING, "Please login as Admin.").showAndWait();
            return;
        }
        ModulePosting m = aiModuleCombo.getValue();
        TAProfile     p = aiProfileCombo.getValue();
        if (m == null || p == null) {
            new Alert(Alert.AlertType.WARNING, "Select a module and a TA profile.")
                    .showAndWait();
            return;
        }
        aiRunButton.setDisable(true);
        Task<RecruitmentInsightResult> task = new Task<>() {
            @Override protected RecruitmentInsightResult call() {
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
            new Alert(Alert.AlertType.ERROR,
                    "Insight failed: " + (t != null ? t.getMessage() : "Unknown error"))
                    .showAndWait();
        });
        Thread th = new Thread(task, "recruitment-insight");
        th.setDaemon(true);
        th.start();
    }

    private void applyInsightResult(RecruitmentInsightResult r) {
        if (r == null) return;
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        if (!r.isSuccess()) {
            aiInsightMetaChip.setText(r.getSourceCaption() + " · " + time);
            aiInsightMetaChip.setStyle(
                "-fx-padding: 6 12 6 12; -fx-background-radius: 999; "
                + "-fx-background-color: #fef3c7; -fx-text-fill: #92400e; "
                + "-fx-font-size: 12px; -fx-font-weight: 700;");
            boolean noConfig = r.getSource() == RecruitmentInsightResult.Source.ERROR_NO_CONFIG;
            aiVerdictTitle.setText(noConfig ? "API not configured" : "API request failed");
            aiVerdictTitle.setStyle(
                "-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #b45309;");
            aiVerdictSubline.setText("");
            aiVerdictIcon.setIconCode(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
            aiVerdictIcon.setIconColor(Paint.valueOf("#d97706"));
            aiVerdictIcon.setIconSize(32);
            aiScoreValue.setText("\u2014 / 100");
            aiMatchProgress.setProgress(0);
            aiMatchProgress.setStyle("-fx-accent: #cbd5e1;");
            aiVerdictCard.setStyle(
                "-fx-background-color: #f8fafc; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
            clearFlow(aiMatchedFlow);
            aiMatchedFlow.setVisible(false);
            aiMatchedEmptyBox.setVisible(true);
            clearFlow(aiMissingFlow);
            aiWorkloadVBox.getChildren().clear();
            Label errDetail = new Label(r.getRationale());
            errDetail.setWrapText(true);
            errDetail.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
            aiWorkloadVBox.getChildren().add(errDetail);
            aiWorkloadRiskBadge.setText("Risk level: \u2014");
            aiWorkloadRiskBadge.setStyle(
                "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
                + "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; "
                + "-fx-font-weight: 700; -fx-font-size: 12px;");
            aiRationaleArea.setText(r.getRationale());
            aiSuggestionBulletBox.getChildren().clear();
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(
                FontAwesomeSolid.INFO_CIRCLE, "#2563eb",
                "Fix configuration or retry after checking network and API quota."));
            clearFlow(aiSuitableTagsFlow);
            return;
        }

        String chipText = "Insight generated · " + r.getSourceCaption() + " · " + time;
        aiInsightMetaChip.setText(chipText);
        aiInsightMetaChip.setStyle(
            "-fx-padding: 6 12 6 12; -fx-background-radius: 999; "
            + "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; "
            + "-fx-font-size: 12px; -fx-font-weight: 700;");

        int score = r.getMatchScore();
        aiScoreValue.setText(score + " / 100");
        aiMatchProgress.setProgress(Math.max(0, Math.min(1, score / 100.0)));

        String verdict, sub, iconColor, cardBg, cardBorder, scoreColor, barAccent;
        FontAwesomeSolid verdictIcon;
        if (score >= 70) {
            verdict = "Recommended"; sub = "Model assessment: strong fit for this posting.";
            verdictIcon = FontAwesomeSolid.CHECK_CIRCLE;   iconColor = "#22c55e";
            cardBg = "#f0fdf4"; cardBorder = "#bbf7d0"; scoreColor = "#15803d"; barAccent = "#22c55e";
        } else if (score >= 45) {
            verdict = "Consider with reservations";
            sub = "Model assessment: partial fit; review with interviews and policy.";
            verdictIcon = FontAwesomeSolid.EXCLAMATION_CIRCLE; iconColor = "#d97706";
            cardBg = "#fffbeb"; cardBorder = "#fde68a"; scoreColor = "#b45309"; barAccent = "#f59e0b";
        } else {
            verdict = "Not recommended"; sub = "Model assessment: weak fit for this posting.";
            verdictIcon = FontAwesomeSolid.TIMES_CIRCLE;   iconColor = "#ef4444";
            cardBg = "#fef2f2"; cardBorder = "#fecaca"; scoreColor = "#b91c1c"; barAccent = "#ef4444";
        }
        aiVerdictTitle.setText(verdict);
        aiVerdictTitle.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: " + iconColor + ";");
        aiVerdictSubline.setText(sub);
        aiVerdictIcon.setIconCode(verdictIcon);
        aiVerdictIcon.setIconColor(Paint.valueOf(iconColor));
        aiVerdictIcon.setIconSize(32);
        aiScoreValue.setStyle(
            "-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: " + scoreColor + ";");
        aiVerdictCard.setStyle(
            "-fx-background-color: " + cardBg + "; -fx-background-radius: 12; "
            + "-fx-border-color: " + cardBorder + "; -fx-border-radius: 12; -fx-border-width: 1;");
        aiMatchProgress.setStyle("-fx-accent: " + barAccent + ";");

        List<String> matched = r.getMatchedSkills();
        clearFlow(aiMatchedFlow);
        if (matched == null || matched.isEmpty()) {
            aiMatchedFlow.setVisible(false);
            aiMatchedEmptyBox.setVisible(true);
        } else {
            aiMatchedEmptyBox.setVisible(false);
            aiMatchedFlow.setVisible(true);
            matched.stream().filter(s -> s != null && !s.isBlank())
                   .forEach(s -> aiMatchedFlow.getChildren().add(
                           aiPill(s.trim(), "#ecfdf5", "#6ee7b7", "#065f46")));
        }

        clearFlow(aiMissingFlow);
        r.getMissingHints().stream().filter(s -> s != null && !s.isBlank())
         .forEach(s -> aiMissingFlow.getChildren().add(
                 aiPill(s.trim(), "#fff7ed", "#fdba74", "#9a3412")));

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
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(
                FontAwesomeSolid.TIMES_CIRCLE, "#dc2626",
                "Do not prioritise for this module based on the model's current assessment."));
        } else {
            aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(
                FontAwesomeSolid.INFO_CIRCLE, "#2563eb",
                "Use this insight alongside interviews, references, and school policy."));
        }
        aiSuggestionBulletBox.getChildren().add(suggestionBulletRow(
            FontAwesomeSolid.USER_CHECK, "#2563eb",
            "Consider this TA for modules closer to the model's matched-skill list when possible."));

        clearFlow(aiSuitableTagsFlow);
        List<String> tags = new ArrayList<>(r.getSuggestedSkillsToAdd());
        for (int i = 0; i < Math.min(8, tags.size()); i++) {
            String t = tags.get(i);
            if (t != null && !t.isBlank())
                aiSuitableTagsFlow.getChildren().add(
                        aiPill(t.trim(), "#eff6ff", "#60a5fa", "#1e3a8a"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Shared layout / styling utilities
    // ════════════════════════════════════════════════════════════════════════

    /** Two-column 50/50 grid. */
    private static GridPane twoColGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        g.getColumnConstraints().addAll(half, half);
        return g;
    }

    /** White rounded card for Analyse sections. */
    private static VBox analyseCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setStyle(
            "-fx-background-color: white; -fx-border-color: #e7edf4; "
            + "-fx-border-radius: 14; -fx-background-radius: 14; "
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");
        return card;
    }

    /** Card header: icon + title + optional right-aligned subtitle. */
    private HBox buildCardHead(FontAwesomeSolid glyph, String title, String subtitle) {
        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 13; -fx-font-weight: 800; -fx-text-fill: #1f2937;");
        head.getChildren().addAll(icon(glyph, 15, "#64748b"), t);
        if (subtitle != null) {
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            Label sub = new Label(subtitle);
            sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");
            head.getChildren().addAll(g, sub);
        }
        return head;
    }

    private static Label sectionDivider(String text) {
        Label l = new Label(text.toUpperCase());
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle(
            "-fx-font-size: 11; -fx-font-weight: 700; -fx-text-fill: #94a3b8; "
            + "-fx-border-color: #e7edf4; -fx-border-width: 0 0 0.5 0; "
            + "-fx-padding: 0 0 6 0;");
        return l;
    }

    private static HBox legendItem(String colorHex, String text) {
        HBox item = new HBox(5);
        item.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region();
        dot.setPrefSize(9, 9);
        dot.setStyle("-fx-background-color: " + colorHex + "; -fx-background-radius: 2;");
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");
        item.getChildren().addAll(dot, lbl);
        return item;
    }

    private static Label colorBadge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; "
                + "-fx-font-size: 10; -fx-padding: 3 8 3 8; -fx-background-radius: 999;");
        return l;
    }

    private static String chipStyle() {
        return "-fx-background-color: #f1f5f9; -fx-border-color: #e7edf4; "
             + "-fx-border-radius: 999; -fx-background-radius: 999; "
             + "-fx-text-fill: #64748b; -fx-font-size: 11; -fx-padding: 4 10 4 10;";
    }

    private static String deltaStyleOk()      { return "-fx-font-size: 11; -fx-text-fill: #3B6D11;"; }
    private static String deltaStyleWarn()    { return "-fx-font-size: 11; -fx-text-fill: #854F0B;"; }
    private static String deltaStyleNeutral() { return "-fx-font-size: 11; -fx-text-fill: #64748b;"; }

    private static long safePct(long part, long total) {
        return total == 0 ? 0 : Math.round(100.0 * part / total);
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

    // ════════════════════════════════════════════════════════════════════════
    //  AI helper methods (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    private static Node buildScoreTickRow() {
        String s = "-fx-text-fill: #94a3b8; -fx-font-size: 11px;";
        Label l0 = new Label("0");   l0.setStyle(s);
        Label l50= new Label("50");  l50.setStyle(s);
        Label l100=new Label("100"); l100.setStyle(s);
        StackPane row = new StackPane(l0, l50, l100);
        row.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(l0,   Pos.CENTER_LEFT);
        StackPane.setAlignment(l50,  Pos.CENTER);
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
            "-fx-background-color: white; -fx-border-color: #e2e8f0; "
            + "-fx-border-radius: 10; -fx-background-radius: 10;");
        combo.setPrefHeight(38);
    }

    private HBox aiInsightSectionTitle(String title, FontAwesomeSolid glyph, String iconColor) {
        HBox row = new HBox(8, icon(glyph, 14, iconColor));
        row.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
        row.getChildren().add(t);
        return row;
    }

    private Node aiInsightColumnCard(String title, FontAwesomeSolid glyph,
                                      String accent, String headerBg, Node body) {
        HBox head = new HBox(8, icon(glyph, 14, accent));
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(8, 10, 8, 10));
        head.setStyle("-fx-background-color: " + headerBg
                + "; -fx-background-radius: 10 10 0 0;");
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
        head.getChildren().add(t);
        StackPane bodyWrap = new StackPane(body);
        bodyWrap.setPadding(new Insets(8, 10, 10, 10));
        return wrapAiCard(new VBox(0, head, bodyWrap));
    }

    private Node wrapAiCard(Node inner) {
        VBox card = new VBox(inner);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14; "
            + "-fx-border-color: #e7edf4; -fx-border-radius: 14; "
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0.12, 0, 3);");
        return card;
    }

    private static Label aiPill(String text, String bg, String border, String textColor) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-background-color: " + bg + "; -fx-border-color: " + border + "; "
            + "-fx-border-radius: 999; -fx-background-radius: 999; "
            + "-fx-padding: 4 10 4 10; -fx-text-fill: " + textColor + "; "
            + "-fx-font-size: 11px; -fx-font-weight: 700;");
        return l;
    }

    private static void clearFlow(FlowPane flow) { flow.getChildren().clear(); }

    private static String workloadRiskFromNote(String note) {
        if (note == null || note.isBlank()) return "Unknown";
        String n = note.toLowerCase();
        if (n.contains("could not parse")) return "Unknown (parse)";
        double sum = 0; int count = 0;
        for (String p : note.replace("~", " ").split("[^0-9.]")) {
            if (p.matches("\\d+(\\.\\d+)?")) { sum += Double.parseDouble(p); count++; }
        }
        if (count == 0) return "Low (informational)";
        if (sum > 22)   return "High";
        if (sum > 14)   return "Medium";
        return "Low";
    }

    private static String workloadRiskBadgeStyle(String level) {
        if ("High".equals(level))
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
                 + "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; "
                 + "-fx-font-weight: 700; -fx-font-size: 12px;";
        if ("Medium".equals(level))
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
                 + "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; "
                 + "-fx-font-weight: 700; -fx-font-size: 12px;";
        if (level.startsWith("Unknown"))
            return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
                 + "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; "
                 + "-fx-font-weight: 700; -fx-font-size: 12px;";
        return "-fx-padding: 5 10 5 10; -fx-background-radius: 999; "
             + "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; "
             + "-fx-font-weight: 700; -fx-font-size: 12px;";
    }

    private HBox suggestionBulletRow(FontAwesomeSolid glyph, String color, String text) {
        HBox row = new HBox(8, icon(glyph, 13, color));
        row.setAlignment(Pos.TOP_LEFT);
        Label lb = new Label(text);
        lb.setWrapText(true);
        lb.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
        row.getChildren().add(lb);
        return row;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Status helpers
    // ════════════════════════════════════════════════════════════════════════

    private static String statusText(ApplicationStatus status) {
        if (status == null) return "Submitted";
        return switch (status) {
            case ACCEPTED            -> "Application approved";
            case REJECTED            -> "Rejected";
            case WAITING_FOR_ASSIGNMENT -> "Waiting for adjustment";
            case REASSIGNED          -> "Reassigned by admin";
            case SUBMITTED           -> "Pending review";
        };
    }

    private static String statusColorHex(ApplicationStatus status) {
        if (status == null) return "#64748b";
        return switch (status) {
            case ACCEPTED            -> "#237338";
            case REJECTED            -> "#d64a4a";
            case WAITING_FOR_ASSIGNMENT -> "#d99200";
            case REASSIGNED          -> "#165696";
            default                  -> "#64748b";
        };
    }

    private static String statusBadgeBg(ApplicationStatus status) {
        if (status == null) return "#f1f5f9";
        return switch (status) {
            case ACCEPTED               -> "#dcfce7";
            case REJECTED               -> "#fee2e2";
            case WAITING_FOR_ASSIGNMENT -> "#fef3c7";
            case REASSIGNED             -> "#dbeafe";
            case SUBMITTED              -> "#f1f5f9";
        };
    }

    private static String statusColorHexByName(String name) {
        if (name == null) return "#888780";
        return switch (name) {
            case "Accepted"   -> "#639922";
            case "Rejected"   -> "#E24B4A";
            case "Waiting"    -> "#BA7517";
            case "Reassigned" -> "#378ADD";
            default           -> "#888780";
        };
    }

    private static String shortStatusName(ApplicationStatus s) {
        if (s == null) return "Unknown";
        return switch (s) {
            case SUBMITTED              -> "Submitted";
            case ACCEPTED               -> "Accepted";
            case REJECTED               -> "Rejected";
            case WAITING_FOR_ASSIGNMENT -> "Waiting";
            case REASSIGNED             -> "Reassigned";
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    //  StringConverters
    // ════════════════════════════════════════════════════════════════════════

    private static StringConverter<ModulePosting> modulePostingConverter() {
        return new StringConverter<>() {
            @Override public String toString(ModulePosting m) {
                if (m == null) return "";
                String code = m.getModuleCode() != null ? m.getModuleCode() : "";
                String name = m.getModuleName() != null ? m.getModuleName() : "";
                return !code.isEmpty() ? code + (name.isEmpty() ? "" : " \u2014 " + name)
                        : m.getModuleId() != null ? m.getModuleId() : "";
            }
            @Override public ModulePosting fromString(String s) { return null; }
        };
    }

    private static StringConverter<TAProfile> taProfileConverter() {
        return new StringConverter<>() {
            @Override public String toString(TAProfile p) {
                if (p == null) return "";
                String n  = p.getName()  != null ? p.getName().trim() : "";
                String qm = p.getQmId()  != null ? p.getQmId()        : "";
                return !n.isEmpty() ? n + " (" + qm + ")" : qm;
            }
            @Override public TAProfile fromString(String s) { return null; }
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Low-level helpers
    // ════════════════════════════════════════════════════════════════════════

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
        if (tip != null) b.setTooltip(new Tooltip(tip));
        return b;
    }

    private Label hintLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13;");
        return l;
    }

    private static String safe(String v) { return v == null ? "" : v; }

}
