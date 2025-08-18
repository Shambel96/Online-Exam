package client;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import shared.Answer;
import shared.Exam;
import shared.Question;
import shared.RemoteExamService;

public class ExamSessionController {

    @FXML
    private Label examTitleLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private VBox questionsContainer;

    private RemoteExamService examService;
    private String studentId;
    private Exam exam;
    private Timeline timer;
    private int secondsRemaining;

    private List<ToggleGroup> answerGroups = new ArrayList<>();

    public void setExamService(RemoteExamService examService) {
        this.examService = examService;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
        examTitleLabel.setText(exam.getTitle());

        // Set up the timer
        secondsRemaining = exam.getDurationMinutes() * 60;
        updateTimerLabel();
    }

    public void startExam() {
        // Create the question UI
        createQuestionUI();

        // Start the timer
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsRemaining--;
            updateTimerLabel();

            if (secondsRemaining <= 0) {
                timer.stop();
                handleTimeUp();
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private void createQuestionUI() {
        questionsContainer.getChildren().clear();
        answerGroups.clear();

        int questionNumber = 1;
        for (Question question : exam.getQuestions()) {
            // Create a container for this question
            VBox questionBox = new VBox(10);
            questionBox.getStyleClass().add("question-box");

            // Add the question text
            Label questionLabel = new Label(questionNumber + ". " + question.getText());
            questionLabel.setWrapText(true);
            questionLabel.setStyle("-fx-font-weight: bold;");
            questionBox.getChildren().add(questionLabel);

            // Create a toggle group for the options
            ToggleGroup group = new ToggleGroup();
            answerGroups.add(group);

            // Add the options
            List<String> options = question.getOptions();
            for (int i = 0; i < options.size(); i++) {
                RadioButton option = new RadioButton(options.get(i));
                option.setWrapText(true);
                option.setToggleGroup(group);
                option.setUserData(i); // Store the option index
                questionBox.getChildren().add(option);
            }

            // Add this question to the container
            questionsContainer.getChildren().add(questionBox);
            questionNumber++;
        }
    }

    private void updateTimerLabel() {
        int hours = secondsRemaining / 3600;
        int minutes = (secondsRemaining % 3600) / 60;
        int seconds = secondsRemaining % 60;

        timerLabel.setText(String.format("Time Remaining: %02d:%02d:%02d", hours, minutes, seconds));

        // Change color to red when less than 5 minutes remaining
        if (secondsRemaining < 300) {
            timerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }
    }

    private void handleTimeUp() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Time's Up");
        alert.setHeaderText("Exam Time Expired");
        alert.setContentText("Your time is up. The exam will be submitted automatically.");
        alert.showAndWait();

        submitExam();
    }

    @FXML
    private void handleSubmitExam(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Submission");
        alert.setHeaderText("Submit Exam");
        alert.setContentText("Are you sure you want to submit your exam? You cannot change your answers after submission.");

        if (alert.showAndWait().get() == ButtonType.OK) {
            submitExam();
        }
    }

    private void submitExam() {
        try {
            // Stop the timer
            if (timer != null) {
                timer.stop();
            }

            // Collect the answers
            List<Answer> answers = new ArrayList<>();
            List<Question> questions = exam.getQuestions();

            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                ToggleGroup group = answerGroups.get(i);
                Toggle selectedToggle = group.getSelectedToggle();

                int selectedOption = -1; // -1 means no answer
                if (selectedToggle != null) {
                    selectedOption = (int) selectedToggle.getUserData();
                }

                answers.add(new Answer(question.getId(), selectedOption));
            }

            // Submit the exam
            boolean success = examService.submitExam(exam.getId(), studentId, answers);

            if (success) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Exam Submitted");
                alert.setHeaderText("Exam Successfully Submitted");
                alert.setContentText("Your exam has been submitted successfully. You can view your results when the teacher makes them available.");
                alert.showAndWait();

                // Close the exam window
                Stage stage = (Stage) examTitleLabel.getScene().getWindow();
                stage.close();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Submission Failed",
                        "There was an error submitting your exam. Please try again.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Submission Failed", e.getMessage());
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
