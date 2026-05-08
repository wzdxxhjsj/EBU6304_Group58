package com.group58.recruit.ui.fx;

import java.io.File;
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
