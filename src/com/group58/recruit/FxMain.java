package com.group58.recruit;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AuthService;
import com.group58.recruit.service.DemoDataResetService;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.TAService;
import com.group58.recruit.ui.fx.AdminDashboardFxView;
import com.group58.recruit.ui.fx.LoginFxView;
import com.group58.recruit.ui.fx.MODashboardFxView;
import com.group58.recruit.ui.fx.TADashboardFxView;

/**
 * JavaFX desktop entry for TA / MO / Admin dashboards.
 * Keeps existing business bootstrap logic unchanged.
 */
public final class FxMain extends Application {

    private AuthService authService;

    public static void main(String[] args) {
        DemoDataResetService.resetAll();
        new MOService().reconcileOpenModulesThatAreFullOnDisk();
        new TAService().reconcileAutoRejectWhenTaAcceptanceCapReached();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        authService = new AuthService();
        stage.setTitle("BUPT International School — TA Recruitment");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        showLogin(stage, bounds.getWidth(), bounds.getHeight());
        stage.setMaximized(true);
        stage.show();
    }

    private void showLogin(Stage stage, double w, double h) {
        LoginFxView login = new LoginFxView(authService, user -> onLoginSuccess(stage, user, w, h));
        Scene scene = new Scene(login, w, h);
        stage.setScene(scene);
    }

    private void onLoginSuccess(Stage stage, User user, double w, double h) {
        if (user.getRole() == Role.TA) {
            TADashboardFxView dash = new TADashboardFxView(() -> {
                authService.logout();
                showLogin(stage, stage.getWidth(), stage.getHeight());
            });
            dash.setCurrentUser(user);
            Scene scene = new Scene(dash, w, h);
            stage.setScene(scene);
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            return;
        }

        if (user.getRole() == Role.MO) {
            MODashboardFxView dash = new MODashboardFxView(() -> {
                authService.logout();
                showLogin(stage, stage.getWidth(), stage.getHeight());
            });
            dash.setCurrentUser(user);
            Scene scene = new Scene(dash, w, h);
            stage.setScene(scene);
            stage.setMinWidth(1200);
            stage.setMinHeight(760);
            return;
        }

        if (user.getRole() == Role.ADMIN) {
            AdminDashboardFxView dash = new AdminDashboardFxView(() -> {
                authService.logout();
                showLogin(stage, stage.getWidth(), stage.getHeight());
            });
            dash.setCurrentUser(user);
            Scene scene = new Scene(dash, w, h);
            stage.setScene(scene);
            stage.setMinWidth(1200);
            stage.setMinHeight(760);
            return;
        }

        BorderPane fallback = new BorderPane();
        VBox box = new VBox(16,
                new Label("Logged in as " + user.getRole().name() + ": " + user.getName() + " (" + user.getQmId() + ")"),
                new Label("No JavaFX dashboard is registered for this role."));
        box.setStyle("-fx-padding: 32; -fx-font-size: 14px;");
        Button back = new Button("Back to login");
        back.setOnAction(e -> {
            authService.logout();
            showLogin(stage, stage.getWidth(), stage.getHeight());
        });
        box.getChildren().add(back);
        fallback.setCenter(box);
        Scene scene = new Scene(fallback, w, h);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
    }
}
