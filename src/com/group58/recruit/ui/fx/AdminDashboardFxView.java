package com.group58.recruit.ui.fx;

import java.io.File;
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
import com.group58.recruit.model.User;
import com.group58.recruit.service.AdminService;
import com.group58.recruit.service.AdminService.ActionResult;
import com.group58.recruit.service.AdminService.ApplicantFilter;
import com.group58.recruit.service.AdminService.ApplicationCardRow;
import com.group58.recruit.service.AdminService.CourseCardRow;
import com.group58.recruit.service.AdminService.CourseFilter;
import com.group58.recruit.service.AdminService.AdjustmentFlowEdge;
import com.group58.recruit.util.DataFileOpen;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
    private final Label attentionSubtitle = new Label();
    private final Label attentionEmptyHint = new Label();
    private final VBox analyseChartsBox = new VBox(14);

    private CourseFilter courseFilter = CourseFilter.ALL;
    private ApplicantFilter applicantFilter = ApplicantFilter.ALL;

    private final ToggleGroup courseTabGroup = new ToggleGroup();
    private final ToggleGroup applicantTabGroup = new ToggleGroup();

    private final VBox overviewPage = new VBox(14);
    private final VBox analysePage = new VBox(16);
    private final VBox reassignmentPage = new VBox(14);
    private final VBox aiPage = new VBox(16);
    private final StackPane mainStack = new StackPane();

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

        analysePage.setPadding(new Insets(0, 18, 18, 18));
        VBox.setVgrow(analysePage, Priority.ALWAYS);
        ScrollPane analyseScroll = new ScrollPane(buildAnalyseBody());
        analyseScroll.setFitToWidth(true);
        analyseScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(analyseScroll, Priority.ALWAYS);
        analysePage.getChildren().add(analyseScroll);

        reassignmentPage.setPadding(new Insets(0, 18, 18, 18));
        BorderPane reassignRoot = new BorderPane();
        VBox reTop = new VBox(14);
        Label rsTitle = new Label("Reassignment");
        rsTitle.setStyle("-fx-font-size: 18; -fx-font-weight: 800; -fx-text-fill: #1f2937;");
        reTop.getChildren().addAll(buildAppTitleBar(), rsTitle, buildAdjustmentSection());
        reassignRoot.setTop(reTop);
        ScrollPane reApplicantScroll = buildApplicantScrollFill(applicantCardBoxReassign);
        VBox reCenter = new VBox(10);
        reCenter.getChildren().addAll(buildApplicantTabs(), moPendingBannerReassign, reApplicantScroll);
        VBox.setVgrow(reApplicantScroll, Priority.ALWAYS);
        reassignRoot.setCenter(reCenter);
        reassignmentPage.getChildren().setAll(reassignRoot);
        VBox.setVgrow(reassignRoot, Priority.ALWAYS);

        aiPage.setPadding(new Insets(48));
        aiPage.setAlignment(Pos.TOP_LEFT);
        aiPage.getChildren().add(wrapCard(buildPlaceholder(
                "AI assistant",
                "AI-assisted screening is not enabled in this build.")));

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

    private Node wrapCard(Node inner) {
        VBox card = new VBox(inner);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #e7edf4; -fx-border-radius: 16;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.15, 0, 4);");
        return card;
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
        if (page == Page.OVERVIEW || page == Page.REASSIGNMENT || page == Page.ANALYSE) {
            refreshAll();
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

    /** Mirror of {@code AdminDashboard} finished heuristic without exposing repository. */
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
            // listApplicantDashboard runs reconcile; refresh full dashboard so Attention matches disk.
            refreshAll();
        });
        wait.setOnAction(e -> {
            applicantFilter = ApplicantFilter.WAITING_FOR_ADJUSTMENT;
            refreshAll();
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

    /** Scroll list that grows with parent height (Reassignment page). */
    private ScrollPane buildApplicantScrollFill(VBox cardBox) {
        cardBox.setPadding(new Insets(4));
        cardBox.setFillWidth(true);
        ScrollPane sp = new ScrollPane(cardBox);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setMinHeight(180);
        sp.setStyle("-fx-background-color: #f8fafc;");
        cardBox.setStyle("-fx-background-color: #f8fafc;");
        return sp;
    }

    private Node buildAnalyseBody() {
        VBox root = new VBox(14);
        Label intro = new Label(
                "Analytics are computed from applications, module postings, and reassignment audit logs — same sources as Overview.");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13;");
        root.getChildren().addAll(
                buildAppTitleBar(),
                buildAdminHeaderCard(),
                buildStatsRow(),
                intro,
                analyseChartsBox);
        return root;
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
        Hyperlink all = new Hyperlink("Refresh list");
        all.setOnAction(e -> refreshAttentionRows());
        head.getChildren().addAll(t, g, all);
        attentionSubtitle.setWrapText(true);
        attentionSubtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12;");
        attentionEmptyHint.setWrapText(true);
        attentionEmptyHint.setVisible(false);
        attentionEmptyHint.setManaged(false);
        attentionEmptyHint.setStyle("-fx-text-fill: #237338; -fx-font-size: 12; -fx-font-weight: 600;");
        attentionTable.setFixedCellSize(34);
        attentionTable.setPrefHeight(200);
        attentionTable.setMinHeight(120);
        attentionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        box.getChildren().addAll(head, attentionSubtitle, attentionTable, attentionEmptyHint);
        return box;
    }

    private void bindAttentionTable() {
        TableColumn<AttentionRow, String> colModule = new TableColumn<>("Module");
        colModule.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().module));
        TableColumn<AttentionRow, String> colMo = new TableColumn<>("MO");
        colMo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().mo));
        colMo.setCellFactory(tc -> new TableCell<AttentionRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setWrapText(false);
                } else {
                    setText(item);
                    setWrapText(true);
                }
            }
        });
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
            // Reassignment queue row: show issue only. Global MO row: append per-MO pending summary.
            if (!row.reassignmentQueueSummary) {
                List<String> pending = adminService.listMoPendingSubmittedSummaryLines();
                if (!pending.isEmpty()) {
                    detail.append("\n\n");
                    for (String line : pending) {
                        detail.append("\u2022 ").append(line).append('\n');
                    }
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
        /**
         * When {@code moduleId} is null, {@link #openAttentionReview} skips appending global MO pending lines
         * (used for unmapped waiting applications and similar).
         */
        final boolean reassignmentQueueSummary;

        AttentionRow(String moduleId, String module, String mo, String vacancies, String waitlist, String issue,
                String severity) {
            this(moduleId, module, mo, vacancies, waitlist, issue, severity, false);
        }

        AttentionRow(String moduleId, String module, String mo, String vacancies, String waitlist, String issue,
                String severity, boolean reassignmentQueueSummary) {
            this.moduleId = moduleId;
            this.module = module;
            this.mo = mo;
            this.vacancies = vacancies;
            this.waitlist = waitlist;
            this.issue = issue;
            this.severity = severity;
            this.reassignmentQueueSummary = reassignmentQueueSummary;
        }
    }

    /**
     * Each line from {@code listMoPendingSubmittedSummaryLines()} is {@code Name (id): modules...};
     * keep only the MO label for the table cell.
     */
    private static String formatMoLabelsFromPendingSummaryLines(List<String> summaryLines) {
        if (summaryLines == null || summaryLines.isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (String line : summaryLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int sep = line.indexOf(": ");
            labels.add(sep > 0 ? line.substring(0, sep).trim() : line.trim());
        }
        labels.sort(String.CASE_INSENSITIVE_ORDER);
        final int maxShow = 8;
        if (labels.size() <= maxShow) {
            return String.join("\n", labels);
        }
        List<String> head = new ArrayList<>(labels.subList(0, maxShow));
        head.add("(+" + (labels.size() - maxShow) + " more)");
        return String.join("\n", head);
    }

    private static String shortModuleLabelStatic(ModulePosting m) {
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

    private void refreshAll() {
        refreshStats();
        refreshAdjustmentFlow();
        refreshMoBanner();
        refreshCoursesOnly();
        refreshApplicantsOnly();
        refreshAttentionRows();
        refreshAnalyseCharts();
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
        dl.setOnAction(e -> DataFileOpen.openRelativePath(null, cv));
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

    private void refreshAnalyseCharts() {
        analyseChartsBox.getChildren().clear();
        if (currentAdmin == null) {
            analyseChartsBox.getChildren().add(hintLabel("Log in as Admin to load analytics."));
            return;
        }
        analyseChartsBox.getChildren().addAll(
                wrapAnalyseSubcard("Application status mix", buildStatusMixChart()),
                wrapAnalyseSubcard("Module fill spotlight (top vacancies)", buildModuleFillChart()),
                wrapAnalyseSubcard("Admin audit actions (reassign vs final reject)", buildAuditBarChart()),
                wrapAnalyseSubcard("Hot reassignment routes", buildTopRoutesChart()));
    }

    private VBox wrapAnalyseSubcard(String title, Node content) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e7edf4; -fx-border-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 14, 0.12, 0, 3);");
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: 800; -fx-text-fill: #1f2937; -fx-font-size: 14;");
        card.getChildren().addAll(t, content);
        return card;
    }

    private Node buildStatusMixChart() {
        List<ApplicationCardRow> apps = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (ApplicationCardRow r : apps) {
            ApplicationStatus s = r.getStatus() != null ? r.getStatus() : ApplicationStatus.SUBMITTED;
            counts.merge(s, 1L, Long::sum);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        VBox root = new VBox(10);
        if (total == 0) {
            root.getChildren().add(hintLabel("No applications in the system."));
            return root;
        }
        final double barWidth = 420;
        HBox bar = new HBox(0);
        bar.setPrefWidth(barWidth);
        bar.setMaxWidth(barWidth);
        bar.setMinHeight(32);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #e8eef8; -fx-background-radius: 8;");
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            if (c <= 0) {
                continue;
            }
            double frac = c / (double) total;
            Region seg = new Region();
            double w = Math.max(4, frac * barWidth);
            seg.setPrefWidth(w);
            seg.setMinWidth(Math.min(w, barWidth));
            seg.setMaxHeight(32);
            seg.setStyle("-fx-background-radius: 6; -fx-background-color: " + statusColorHex(s) + ";");
            bar.getChildren().add(seg);
        }
        FlowPane legend = new FlowPane(12, 8);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            if (c <= 0) {
                continue;
            }
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Region dot = new Region();
            dot.setPrefSize(10, 10);
            dot.setStyle("-fx-background-color: " + statusColorHex(s) + "; -fx-background-radius: 5;");
            double pct = 100.0 * c / total;
            Label lb = new Label(shortStatusName(s) + ": " + c + " (" + String.format("%.0f%%", pct) + ")");
            lb.setStyle("-fx-font-size: 12; -fx-text-fill: #334155;");
            row.getChildren().addAll(dot, lb);
            legend.getChildren().add(row);
        }
        Label cap = new Label("Total applications: " + total);
        cap.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12; -fx-font-weight: 600;");
        root.getChildren().addAll(bar, legend, cap);
        return root;
    }

    private static String shortStatusName(ApplicationStatus s) {
        return switch (s) {
            case SUBMITTED -> "Submitted";
            case ACCEPTED -> "Accepted";
            case REJECTED -> "Rejected";
            case WAITING_FOR_ASSIGNMENT -> "Waiting";
            case REASSIGNED -> "Reassigned";
        };
    }

    private Node buildModuleFillChart() {
        List<CourseCardRow> courses = adminService.listCourseRecruitment(CourseFilter.ALL);
        courses.sort(Comparator.comparingInt(CourseCardRow::getRemaining).reversed());
        VBox root = new VBox(8);
        int shown = 0;
        for (CourseCardRow cr : courses) {
            if (shown++ >= 10) {
                break;
            }
            ModulePosting m = cr.getModule();
            if (m == null) {
                continue;
            }
            int t = Math.max(1, m.getVacanciesTotal());
            int f = Math.min(Math.max(0, m.getVacanciesFilled()), t);
            String label = (m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId()) + " — "
                    + (m.getModuleName() != null ? m.getModuleName() : "");
            Label name = new Label(label);
            name.setStyle("-fx-font-size: 12; -fx-font-weight: 600; -fx-text-fill: #1e293b;");
            name.setMaxWidth(380);
            ProgressBar pb = new ProgressBar(f / (double) t);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle("-fx-accent: #2e7ac4; -fx-control-inner-background: #e8eef8;");
            Label sub = new Label(f + " / " + m.getVacanciesTotal() + " filled · " + cr.getRecruitmentStatusText());
            sub.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");
            VBox row = new VBox(4, name, pb, sub);
            root.getChildren().add(row);
        }
        if (root.getChildren().isEmpty()) {
            root.getChildren().add(hintLabel("No modules to chart."));
        }
        return root;
    }

    private Node buildAuditBarChart() {
        Map<ReassignActionType, Long> map = adminService.countReassignLogsByActionType();
        long r = map.getOrDefault(ReassignActionType.REASSIGN, 0L);
        long f = map.getOrDefault(ReassignActionType.FINAL_REJECT, 0L);
        long sum = r + f;
        VBox root = new VBox(10);
        if (sum == 0) {
            root.getChildren().add(hintLabel("No admin audit entries yet — actions will appear after reassign or final reject."));
            return root;
        }
        double pctR = 100.0 * r / sum;
        double pctF = 100.0 * f / sum;
        Label summary = new Label(String.format(
                "Total logged actions: %d  ·  Reassign: %d (%.0f%%)  ·  Final reject: %d (%.0f%%)",
                sum, r, pctR, f, pctF));
        summary.setWrapText(true);
        summary.setStyle("-fx-font-weight: 700; -fx-text-fill: #0f172a; -fx-font-size: 13;");

        final double barWidth = 420;
        HBox bar = new HBox(0);
        bar.setPrefWidth(barWidth);
        bar.setMaxWidth(barWidth);
        bar.setMinHeight(40);
        bar.setStyle("-fx-background-color: #e8eef8; -fx-background-radius: 8;");
        if (r > 0) {
            double w = Math.max(8, (r / (double) sum) * barWidth);
            bar.getChildren().add(auditBarSegment(w, "#2563eb", r, pctR));
        }
        if (f > 0) {
            double w = Math.max(8, (f / (double) sum) * barWidth);
            bar.getChildren().add(auditBarSegment(w, "#dc2626", f, pctF));
        }

        String legendStyle = "-fx-font-size: 12; -fx-text-fill: #334155;";
        FlowPane legend = new FlowPane(16, 8);
        HBox leg1 = new HBox(6);
        leg1.setAlignment(Pos.CENTER_LEFT);
        Region d1 = new Region();
        d1.setPrefSize(10, 10);
        d1.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 5;");
        Label l1 = new Label("Reassign: " + r + " (" + String.format("%.0f%%", pctR) + ")");
        l1.setStyle(legendStyle);
        leg1.getChildren().addAll(d1, l1);
        HBox leg2 = new HBox(6);
        leg2.setAlignment(Pos.CENTER_LEFT);
        Region d2 = new Region();
        d2.setPrefSize(10, 10);
        d2.setStyle("-fx-background-color: #dc2626; -fx-background-radius: 5;");
        Label l2 = new Label("Final reject: " + f + " (" + String.format("%.0f%%", pctF) + ")");
        l2.setStyle(legendStyle);
        leg2.getChildren().addAll(d2, l2);
        legend.getChildren().addAll(leg1, leg2);

        Label note = new Label("Source: reassign_logs.json (each row is one admin action).");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");
        root.getChildren().addAll(summary, bar, legend, note);
        return root;
    }

    /** One coloured bar segment with count + % visible on wide segments. */
    private StackPane auditBarSegment(double width, String colorHex, long count, double pct) {
        StackPane sp = new StackPane();
        sp.setAlignment(Pos.CENTER);
        sp.setPrefWidth(width);
        sp.setMinWidth(width);
        sp.setMaxHeight(40);
        Region bg = new Region();
        bg.setMaxHeight(40);
        bg.setStyle("-fx-background-radius: 6; -fx-background-color: " + colorHex + ";");
        Label onBar = new Label(width >= 52 ? (count + " · " + String.format("%.0f%%", pct)) : "");
        onBar.setStyle("-fx-text-fill: white; -fx-font-weight: 800; -fx-font-size: 10;");
        sp.getChildren().addAll(bg, onBar);
        StackPane.setAlignment(onBar, Pos.CENTER);
        return sp;
    }

    private Node buildTopRoutesChart() {
        List<AdjustmentFlowEdge> edges = new ArrayList<>(adminService.listAdjustmentFlowEdges());
        edges.sort(Comparator.comparingInt(AdjustmentFlowEdge::getCount).reversed());
        VBox root = new VBox(8);
        int n = 0;
        for (AdjustmentFlowEdge e : edges) {
            if (n++ >= 8) {
                break;
            }
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label flow = new Label(e.getFromLabel() + "  \u2192  " + e.getToLabel());
            flow.setStyle("-fx-font-size: 12; -fx-text-fill: #0f172a;");
            flow.setMaxWidth(320);
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            Label cnt = new Label(String.valueOf(e.getCount()));
            cnt.setStyle("-fx-font-weight: 800; -fx-text-fill: #2167f7; -fx-font-size: 13;");
            row.getChildren().addAll(flow, grow, cnt);
            ProgressBar heat = new ProgressBar(Math.min(1, e.getCount() / 10.0));
            heat.setMaxWidth(Double.MAX_VALUE);
            heat.setPrefHeight(6);
            heat.setStyle("-fx-accent: #93c5fd; -fx-control-inner-background: #f1f5f9;");
            VBox block = new VBox(4, row, heat);
            root.getChildren().add(block);
        }
        if (root.getChildren().isEmpty()) {
            root.getChildren().add(hintLabel("No reassignment routes recorded yet."));
        } else {
            Label foot = new Label("Ranked by number of TA moves logged between module pairs.");
            foot.setWrapText(true);
            foot.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");
            root.getChildren().add(foot);
        }
        return root;
    }

    private void refreshAttentionRows() {
        attentionTable.getItems().clear();
        attentionSubtitle.setText("");
        attentionEmptyHint.setVisible(false);
        attentionEmptyHint.setManaged(false);
        if (currentAdmin == null) {
            attentionSubtitle.setText("Log in to see routing risk, MO backlog, and reassignment queue.");
            return;
        }
        List<AttentionRow> rows = new ArrayList<>();
        Set<String> attentionKeys = new HashSet<>();
        List<ApplicationCardRow> allApps = adminService.listApplicantDashboard(ApplicantFilter.ALL);
        List<ApplicationCardRow> waitingList = adminService.listApplicantDashboard(ApplicantFilter.WAITING_FOR_ADJUSTMENT);
        int waitingTa = waitingList.size();
        Map<String, Long> submittedByModule = allApps.stream()
                .filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED)
                .collect(Collectors.groupingBy(ApplicationCardRow::getModuleId, Collectors.counting()));
        long totalSubmitted = allApps.stream().filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED).count();
        long modulesWithSubmittedQueue = submittedByModule.entrySet().stream().filter(e -> e.getValue() > 0).count();

        List<CourseCardRow> allCourses = adminService.listCourseRecruitment(CourseFilter.ALL);
        Map<String, CourseCardRow> courseByModuleId = new HashMap<>();
        for (CourseCardRow cr : allCourses) {
            ModulePosting mp = cr.getModule();
            if (mp != null && mp.getModuleId() != null) {
                courseByModuleId.put(mp.getModuleId(), cr);
            }
        }

        if (waitingTa > 0) {
            int openReassignSeats = 0;
            for (ModulePosting m : adminService.listReassignableCourses()) {
                int t = Math.max(0, m.getVacanciesTotal());
                int f = Math.max(0, m.getVacanciesFilled());
                openReassignSeats += Math.max(0, t - f);
            }
            String vacCol = openReassignSeats + " open seat(s) (reassign targets)";

            Map<String, Integer> waitingCountByModule = new HashMap<>();
            for (ApplicationCardRow w : waitingList) {
                String mid = w.getModuleId();
                if (mid == null || mid.isBlank()) {
                    continue;
                }
                waitingCountByModule.merge(mid, 1, Integer::sum);
            }
            int mappedWaiting = waitingCountByModule.values().stream().mapToInt(Integer::intValue).sum();
            int unmappedWaiting = waitingTa - mappedWaiting;
            if (unmappedWaiting > 0 && attentionKeys.add("UNMAPPED|WAITING_ADJUST")) {
                rows.add(new AttentionRow(null,
                        "(Application(s) without module)",
                        "(No MO mapped)",
                        vacCol,
                        String.valueOf(unmappedWaiting),
                        unmappedWaiting + " TA(s) in \"Waiting for adjustment\" with no module id \u2014 check data",
                        "high",
                        true));
            }

            List<String> moduleOrder = new ArrayList<>(waitingCountByModule.keySet());
            moduleOrder.sort(Comparator.comparing(mid -> {
                CourseCardRow cr = courseByModuleId.get(mid);
                ModulePosting mp = cr != null ? cr.getModule() : null;
                return mp != null ? shortModuleLabelStatic(mp) : mid;
            }, String.CASE_INSENSITIVE_ORDER));

            for (String mid : moduleOrder) {
                String key = mid + "|WAITING_ADJUST";
                if (!attentionKeys.add(key)) {
                    continue;
                }
                int n = waitingCountByModule.get(mid);
                CourseCardRow cr = courseByModuleId.get(mid);
                ModulePosting m = cr != null ? cr.getModule() : null;
                String moduleLabel = m != null ? shortModuleLabel(m) : mid;
                String moName = cr != null ? cr.getMoDisplayName() : "(No MO mapped)";
                String issue = n + " TA(s) in \"Waiting for adjustment\" for this module \u2014 assign on the Reassignment tab";
                rows.add(new AttentionRow(mid, moduleLabel, moName, vacCol, String.valueOf(n), issue, "medium"));
            }
        }

        List<CourseCardRow> openWithSub = new ArrayList<>();
        for (CourseCardRow cr : allCourses) {
            ModulePosting m = cr.getModule();
            if (m == null || m.getModuleId() == null) {
                continue;
            }
            int sub = submittedByModule.getOrDefault(m.getModuleId(), 0L).intValue();
            if (m.getStatus() == ModuleStatus.OPEN && sub > 0) {
                openWithSub.add(cr);
            }
        }
        openWithSub.sort(Comparator.comparingInt((CourseCardRow cr) -> submittedByModule
                .getOrDefault(cr.getModule().getModuleId(), 0L).intValue()).reversed());
        int cap = 0;
        for (CourseCardRow cr : openWithSub) {
            if (cap++ >= 10) {
                break;
            }
            ModulePosting m = cr.getModule();
            String mid = m.getModuleId();
            String key = mid + "|MO_BACKLOG";
            if (!attentionKeys.add(key)) {
                continue;
            }
            int sub = submittedByModule.getOrDefault(mid, 0L).intValue();
            int total = Math.max(0, m.getVacanciesTotal());
            int filled = Math.max(0, m.getVacanciesFilled());
            int rem = cr.getRemaining();
            String vacTxt = filled + "/" + total;
            String moName = cr.getMoDisplayName();
            String sev = sub >= 6 ? "medium" : "low";
            rows.add(new AttentionRow(mid,
                    shortModuleLabel(m),
                    moName,
                    vacTxt,
                    String.valueOf(sub),
                    "MO review backlog: " + sub + " submitted CV(s) while module is OPEN (" + rem + " seat(s) left)",
                    sev));
        }

        for (CourseCardRow cr : allCourses) {
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
                String key = mid + "|FULL_PENDING";
                if (attentionKeys.add(key)) {
                    rows.add(new AttentionRow(mid,
                            shortModuleLabel(m),
                            moName,
                            vacTxt,
                            String.valueOf(sub),
                            "Capacity full but " + sub + " application(s) still SUBMITTED \u2014 data needs MO action",
                            "high"));
                }
            } else if (rem <= 0 && sub > 2) {
                String key = mid + "|NOSEAT_MANY";
                if (attentionKeys.add(key)) {
                    rows.add(new AttentionRow(mid,
                            shortModuleLabel(m),
                            moName,
                            vacTxt,
                            String.valueOf(sub),
                            "No seats left with multiple pending reviews \u2014 check MO decisions",
                            "medium"));
                }
            } else if (m.getStatus() == ModuleStatus.OPEN && rem <= 0 && sub == 0 && total > 0) {
                String key = mid + "|CLOSE_HINT";
                if (attentionKeys.add(key)) {
                    rows.add(new AttentionRow(mid,
                            shortModuleLabel(m),
                            moName,
                            vacTxt,
                            "0",
                            "No vacancies left while status is OPEN \u2014 consider closing the posting",
                            "low"));
                }
            }
        }

        List<String> moPending = adminService.listMoPendingSubmittedSummaryLines();
        if (!moPending.isEmpty() && attentionKeys.add("GLOBAL|MO_BLOCK")) {
            String moCol = formatMoLabelsFromPendingSummaryLines(moPending);
            String vacCol = modulesWithSubmittedQueue + " module(s) · " + totalSubmitted + " CV(s) pending";
            rows.add(new AttentionRow(null,
                    "MO review (global)",
                    moCol,
                    vacCol,
                    String.valueOf(totalSubmitted),
                    "Reassignment blocked until all " + totalSubmitted + " submitted CV(s) are reviewed by MOs",
                    "high"));
        }

        attentionTable.getItems().setAll(rows);
        double headerAllowance = 38;
        attentionTable.setPrefHeight(Math.min(360, headerAllowance + attentionTable.getFixedCellSize() * (rows.size() + 1)));
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

    private String shortModuleLabel(ModulePosting m) {
        return shortModuleLabelStatic(m);
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
