package server;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import javafx.application.Platform;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import shared.Exam;
import shared.ExamResult;

public class ServerMainController implements Initializable {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Exam> examsTable;
    @FXML
    private TableColumn<Exam, Integer> examIdColumn;
    @FXML
    private TableColumn<Exam, String> examTitleColumn;
    @FXML
    private TableColumn<Exam, String> examDescriptionColumn;
    @FXML
    private TableColumn<Exam, Integer> examDurationColumn;
    @FXML
    private TableColumn<Exam, Boolean> examResultsVisibleColumn;
    @FXML
    private TableColumn<Exam, Boolean> examActiveColumn;

    @FXML
    private ComboBox<Exam> examSelector;
    @FXML
    private TableView<ExamResult> resultsTable;
    @FXML
    private TableColumn<ExamResult, String> resultStudentIdColumn;
    @FXML
    private TableColumn<ExamResult, String> resultStudentNameColumn;
    @FXML
    private TableColumn<ExamResult, Integer> resultScoreColumn;
    @FXML
    private TableColumn<ExamResult, Integer> resultTotalColumn;
    @FXML
    private TableColumn<ExamResult, Double> resultPercentageColumn;
    @FXML
    private TableColumn<ExamResult, Date> resultSubmissionTimeColumn;

    @FXML
    private TableView<ActiveSessionDisplay> sessionsTable;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionStudentIdColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, Integer> sessionExamIdColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionExamTitleColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionStartTimeColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionTimeRemainingColumn;

    @FXML
    private TextArea logTextArea;

    private ExamServiceImpl examService;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private String loggedInTeacher;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize table columns
        examIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        examTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        examDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        examDurationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        examResultsVisibleColumn.setCellValueFactory(new PropertyValueFactory<>("resultsVisible"));

        resultStudentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        resultStudentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        resultScoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        resultTotalColumn.setCellValueFactory(new PropertyValueFactory<>("totalPossible"));
        resultPercentageColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        resultSubmissionTimeColumn.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));

        sessionStudentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        sessionExamIdColumn.setCellValueFactory(new PropertyValueFactory<>("examId"));
        sessionExamTitleColumn.setCellValueFactory(new PropertyValueFactory<>("examTitle"));
        sessionStartTimeColumn.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        sessionTimeRemainingColumn.setCellValueFactory(new PropertyValueFactory<>("timeRemaining"));

        // Log server start
        logActivity("Server started");
    }

    public void setExamService(ExamServiceImpl examService) {
        this.examService = examService;
        refreshExams();
    }

    public void setLoggedInTeacher(String username) {
        this.loggedInTeacher = username;
        logActivity("Teacher " + username + " logged in");
        statusLabel.setText("Logged in as: " + username);
    }

    public void logActivity(String message) {
        Platform.runLater(() -> {
            String timestamp = timeFormat.format(new Date());
            logTextArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    @FXML
    private void handleCreateExam(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/ExamEditor.fxml"));
            Parent root = loader.load();

            ExamEditorController controller = loader.getController();
            controller.setExamService(examService);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Create New Exam");
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshExams();
        } catch (Exception e) {
            logActivity("Error opening exam editor: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open exam editor", e.getMessage());
        }
    }

    @FXML
    private void handleEditExam(ActionEvent event) {
        Exam selectedExam = examsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to edit.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/ExamEditor.fxml"));
            Parent root = loader.load();

            ExamEditorController controller = loader.getController();
            controller.setExamService(examService);
            controller.loadExam(selectedExam);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Edit Exam");
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshExams();
        } catch (Exception e) {
            logActivity("Error opening exam editor: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open exam editor", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteExam(ActionEvent event) {
        Exam selectedExam = examsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Exam");
        alert.setContentText("Are you sure you want to delete the exam: " + selectedExam.getTitle() + "?");

        if (alert.showAndWait().get() == ButtonType.OK) {
            try {
                examService.deleteExam(selectedExam.getId());
                refreshExams();
            } catch (Exception e) {
                logActivity("Error deleting exam: " + e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Error", "Could not delete exam", e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefreshExams(ActionEvent event) {
        refreshExams();
    }

    @FXML
    private void handleExamSelected(ActionEvent event) {
        refreshResults();
    }

    @FXML
    private void handleToggleResultsVisibility(ActionEvent event) {
        Exam selectedExam = examSelector.getValue();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to toggle results visibility.");
            return;
        }

        try {
            boolean newVisibility = !selectedExam.isResultsVisible();
            examService.setResultVisibility(selectedExam.getId(), newVisibility);
            selectedExam.setResultsVisible(newVisibility);

            refreshExams();
            refreshResults();

            String message = "Results for exam '" + selectedExam.getTitle() + "' are now "
                    + (newVisibility ? "visible" : "hidden") + " to students.";
            showAlert(Alert.AlertType.INFORMATION, "Results Visibility", "Visibility Updated", message);
        } catch (Exception e) {
            logActivity("Error toggling results visibility: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not toggle results visibility", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshResults(ActionEvent event) {
        refreshResults();
    }

    @FXML
    private void handleRefreshSessions(ActionEvent event) {
        refreshSessions();
    }

    @FXML
    private void handleClearLog(ActionEvent event) {
        logTextArea.clear();
        logActivity("Log cleared");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Load the login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/TeacherLogin.fxml"));
            Parent root = loader.load();

            // Get the current stage and set the new scene
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setTitle("Online Exam System - Teacher Login");
            stage.setScene(new Scene(root, 400, 300));
            stage.centerOnScreen();

            // Log the logout
            logActivity("Teacher " + loggedInTeacher + " logged out");
        } catch (Exception e) {
            logActivity("Error during logout: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not logout", e.getMessage());
        }
    }

    private void refreshExams() {
        try {
            // This would normally call examService.getAllExams()
            // For now, we'll use dummy data
            ObservableList<Exam> exams = FXCollections.observableArrayList();
            // Add some dummy exams
            exams.add(new Exam(1, "Java Basics", "Introduction to Java programming", 60, true));
            exams.add(new Exam(2, "Networking Fundamentals", "Basic networking concepts", 45, false));

            examsTable.setItems(exams);
            examSelector.setItems(exams);

            logActivity("Refreshed exams list");
        } catch (Exception e) {
            logActivity("Error refreshing exams: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh exams", e.getMessage());
        }
    }

    private void refreshResults() {
        Exam selectedExam = examSelector.getValue();
        if (selectedExam == null) {
            resultsTable.setItems(FXCollections.observableArrayList());
            return;
        }

        try {
            // This would normally call examService.getExamResults(selectedExam.getId())
            // For now, we'll use dummy data
            ObservableList<ExamResult> results = FXCollections.observableArrayList();
            // Add some dummy results
            results.add(new ExamResult(1, selectedExam.getId(), "S001", "John Doe", 85, 100, new Date()));
            results.add(new ExamResult(2, selectedExam.getId(), "S002", "Jane Smith", 92, 100, new Date()));

            resultsTable.setItems(results);

            logActivity("Refreshed results for exam: " + selectedExam.getTitle());
        } catch (Exception e) {
            logActivity("Error refreshing results: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh results", e.getMessage());
        }
    }

    private void refreshSessions() {
        try {
            // This would normally get active sessions from the exam service
            // For now, we'll use dummy data
            ObservableList<ActiveSessionDisplay> sessions = FXCollections.observableArrayList();
            // Add some dummy sessions
            sessions.add(new ActiveSessionDisplay("S003", 1, "Java Basics",
                    "2023-05-10 10:30:00", "25:30"));
            sessions.add(new ActiveSessionDisplay("S004", 2, "Networking Fundamentals",
                    "2023-05-10 10:45:00", "40:15"));

            sessionsTable.setItems(sessions);

            logActivity("Refreshed active sessions");
        } catch (Exception e) {
            logActivity("Error refreshing sessions: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh sessions", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Class to display active sessions in the table
    public static class ActiveSessionDisplay {

        private final String studentId;
        private final int examId;
        private final String examTitle;
        private final String startTime;
        private final String timeRemaining;

        public ActiveSessionDisplay(String studentId, int examId, String examTitle,
                String startTime, String timeRemaining) {
            this.studentId = studentId;
            this.examId = examId;
            this.examTitle = examTitle;
            this.startTime = startTime;
            this.timeRemaining = timeRemaining;
        }

        public String getStudentId() {
            return studentId;
        }

        public int getExamId() {
            return examId;
        }

        public String getExamTitle() {
            return examTitle;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getTimeRemaining() {
            return timeRemaining;
        }
    }
}
