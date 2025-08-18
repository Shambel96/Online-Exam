package client;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import shared.Exam;
import shared.ExamResult;
import shared.RemoteExamService;

public class StudentDashboardController implements Initializable {
    
    @FXML private Label studentInfoLabel;
    
    @FXML private TableView<Exam> availableExamsTable;
    @FXML private TableColumn<Exam, Integer> examIdColumn;
    @FXML private TableColumn<Exam, String> examTitleColumn;
    @FXML private TableColumn<Exam, String> examDescriptionColumn;
    @FXML private TableColumn<Exam, Integer> examDurationColumn;
    
    @FXML private TableView<ExamResult> resultsTable;
    @FXML private TableColumn<ExamResult, Integer> resultExamIdColumn;
    @FXML private TableColumn<ExamResult, String> resultExamTitleColumn;
    @FXML private TableColumn<ExamResult, Integer> resultScoreColumn;
    @FXML private TableColumn<ExamResult, Integer> resultTotalColumn;
    @FXML private TableColumn<ExamResult, Double> resultPercentageColumn;
    @FXML private TableColumn<ExamResult, String> resultSubmissionTimeColumn;
    
    private RemoteExamService examService;
    private String studentId;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize table columns
        examIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        examTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        examDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        examDurationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        
        resultExamIdColumn.setCellValueFactory(new PropertyValueFactory<>("examId"));
        resultExamTitleColumn.setCellValueFactory(new PropertyValueFactory<>("examTitle"));
        resultScoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        resultTotalColumn.setCellValueFactory(new PropertyValueFactory<>("totalPossible"));
        resultPercentageColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        resultSubmissionTimeColumn.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));
    }
    
    public void initialize() {
        studentInfoLabel.setText("Student: " + studentId);
        refreshExams();
        refreshResults();
    }
    
    public void setExamService(RemoteExamService examService) {
        this.examService = examService;
    }
    
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
    
    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Load the login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/Login.fxml"));
            Parent root = loader.load();
            
            // Get the controller and set the exam service
            LoginController controller = loader.getController();
            controller.setExamService(examService);
            
            // Get the current stage and set the new scene
            Stage stage = (Stage) studentInfoLabel.getScene().getWindow();
            stage.setTitle("Online Exam System - Student Client");
            stage.setScene(new Scene(root, 400, 300));
            stage.centerOnScreen();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not logout", e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleRefreshExams(ActionEvent event) {
        refreshExams();
    }
    
    @FXML
    private void handleStartExam(ActionEvent event) {
        Exam selectedExam = availableExamsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected", 
                     "Please select an exam to start.");
            return;
        }
        
        try {
            // Get the exam with questions
            Exam examWithQuestions = examService.getExamQuestions(selectedExam.getId(), studentId);
            
            // Load the exam screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/ExamSession.fxml"));
            Parent root = loader.load();
            
            // Get the controller and set up the exam
            ExamSessionController controller = loader.getController();
            controller.setExamService(examService);
            controller.setStudentId(studentId);
            controller.setExam(examWithQuestions);
            controller.startExam();
            
            // Open in a new window
            Stage examStage = new Stage();
            examStage.setTitle("Exam: " + examWithQuestions.getTitle());
            examStage.setScene(new Scene(root, 800, 600));
            examStage.setOnCloseRequest(e -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Exit");
                alert.setHeaderText("Exit Exam");
                alert.setContentText("Are you sure you want to exit the exam? Your progress will be lost.");
                
                if (alert.showAndWait().get() != ButtonType.OK) {
                    e.consume();
                }
            });
            examStage.showAndWait();
            
            // Refresh the exams and results after the exam window is closed
            refreshExams();
            refreshResults();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not start exam", e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleRefreshResults(ActionEvent event) {
        refreshResults();
    }
    
    private void refreshExams() {
        try {
            List<Exam> exams = examService.getAvailableExams(studentId);
            availableExamsTable.setItems(FXCollections.observableArrayList(exams));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh exams", e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void refreshResults() {
        try {
            // This would normally get all results for the student
            // For now, we'll use dummy data
            ObservableList<ExamResult> results = FXCollections.observableArrayList();
            // Add some dummy results
            results.add(new ExamResult(1, 1, studentId, studentId, 85, 100, null));
            
            resultsTable.setItems(results);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh results", e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}