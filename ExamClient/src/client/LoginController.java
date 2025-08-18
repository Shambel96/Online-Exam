package client;

import java.io.IOException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import shared.RemoteExamService;

public class LoginController {
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    
    private RemoteExamService examService;
    
    public void setExamService(RemoteExamService examService) {
        this.examService = examService;
    }
    
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password");
            return;
        }
        
        try {
            boolean authenticated = examService.authenticateUser(username, password, false);
            
            if (authenticated) {
                // Load the student dashboard
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/StudentDashboard.fxml"));
                Parent root = loader.load();
                
                StudentDashboardController controller = loader.getController();
                controller.setExamService(examService);
                controller.setStudentId(username);
                controller.initialize();
                
                // Get the current stage and set the new scene
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setTitle("Student Dashboard - " + username);
                stage.setScene(new Scene(root, 800, 600));
                stage.centerOnScreen();
            } else {
                statusLabel.setText("Invalid username or password");
            }
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}