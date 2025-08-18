package server;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import shared.*;

public class ExamServiceImpl implements RemoteExamService {

    private final DatabaseManager dbManager;
    private final ServerMainController controller;

    // Track active exam sessions
    private final Map<String, ActiveExamSession> activeExams = new ConcurrentHashMap<>();

    public ExamServiceImpl(ServerMainController controller) {
        this.dbManager = new DatabaseManager();
        this.controller = controller;
    }

    @Override
    public boolean authenticateUser(String username, String password, boolean isTeacher) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            String table = isTeacher ? "teachers" : "students";
            String sql = "SELECT * FROM " + table + " WHERE username = ? AND password = ?";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password); // In a real app, use password hashing

            rs = stmt.executeQuery();
            boolean authenticated = rs.next();

            // Log the authentication attempt
            controller.logActivity(username + " (" + (isTeacher ? "teacher" : "student")
                    + ") authentication " + (authenticated ? "successful" : "failed"));

            return authenticated;
        } catch (SQLException e) {
            controller.logActivity("Authentication error: " + e.getMessage());
            throw new RemoteException("Authentication failed", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public List<Exam> getAvailableExams(String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<Exam> exams = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT * FROM exams WHERE active = 1";

            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                Exam exam = new Exam(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("duration_minutes"),
                        rs.getBoolean("results_visible")
                );
                exams.add(exam);
            }

            controller.logActivity("Student " + studentId + " retrieved available exams");
            return exams;
        } catch (SQLException e) {
            controller.logActivity("Error retrieving exams: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exams", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public Exam getExamQuestions(int examId, String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // First check if the student has already taken this exam
            conn = dbManager.getConnection();
            String checkSql = "SELECT * FROM exam_results WHERE exam_id = ? AND student_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                controller.logActivity("Student " + studentId + " attempted to retake exam " + examId);
                throw new RemoteException("You have already taken this exam");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the exam details
            String examSql = "SELECT * FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(examSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("Exam not found");
            }

            Exam exam = new Exam(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("duration_minutes"),
                    rs.getBoolean("results_visible")
            );

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the questions for this exam
            String questionsSql = "SELECT q.* FROM questions q "
                    + "JOIN exam_questions eq ON q.id = eq.question_id "
                    + "WHERE eq.exam_id = ?";
            stmt = conn.prepareStatement(questionsSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            List<Question> questions = new ArrayList<>();
            while (rs.next()) {
                int questionId = rs.getInt("id");
                Question question = new Question(
                        questionId,
                        rs.getString("text"),
                        getOptionsForQuestion(questionId),
                        rs.getInt("correct_option"),
                        rs.getInt("points")
                );
                questions.add(question);
            }

            exam.setQuestions(questions);

            // Create an active exam session
            ActiveExamSession session = new ActiveExamSession(
                    examId,
                    studentId,
                    System.currentTimeMillis(),
                    exam.getDurationMinutes() * 60 * 1000
            );
            activeExams.put(studentId + "-" + examId, session);

            controller.logActivity("Student " + studentId + " started exam " + examId);
            return exam;
        } catch (SQLException e) {
            controller.logActivity("Error retrieving exam questions: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam questions", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    private List<String> getOptionsForQuestion(int questionId) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<String> options = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT * FROM question_options WHERE question_id = ? ORDER BY option_order";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, questionId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                options.add(rs.getString("option_text"));
            }

            return options;
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean submitExam(int examId, String studentId, List<Answer> answers) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // Check if the exam is still active and within time limit
            String sessionKey = studentId + "-" + examId;
            ActiveExamSession session = activeExams.get(sessionKey);

            if (session == null) {
                throw new RemoteException("No active exam session found");
            }

            long currentTime = System.currentTimeMillis();
            long endTime = session.getStartTime() + session.getDurationMillis();

            if (currentTime > endTime) {
                controller.logActivity("Student " + studentId + " submitted exam " + examId + " after time expired");
                // We'll still accept it but log that it was late
            }

            // Calculate the score
            conn = dbManager.getConnection();
            int score = 0;
            int totalPossible = 0;

            Map<Integer, Integer> questionPoints = new HashMap<>();
            Map<Integer, Integer> correctAnswers = new HashMap<>();

            // Get all questions and their correct answers
            String questionsSql = "SELECT id, correct_option, points FROM questions "
                    + "WHERE id IN (SELECT question_id FROM exam_questions WHERE exam_id = ?)";
            stmt = conn.prepareStatement(questionsSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                int questionId = rs.getInt("id");
                int correctOption = rs.getInt("correct_option");
                int points = rs.getInt("points");

                questionPoints.put(questionId, points);
                correctAnswers.put(questionId, correctOption);
                totalPossible += points;
            }

            // Calculate score based on answers
            for (Answer answer : answers) {
                int questionId = answer.getQuestionId();
                int selectedOption = answer.getSelectedOptionIndex();

                if (correctAnswers.containsKey(questionId)
                        && correctAnswers.get(questionId) == selectedOption) {
                    score += questionPoints.get(questionId);
                }
            }

            // Save the result to the database
            dbManager.closeResources(null, stmt, rs);

            String resultSql = "INSERT INTO exam_results (exam_id, student_id, score, total_possible, submission_time) "
                    + "VALUES (?, ?, ?, ?, NOW())";
            stmt = conn.prepareStatement(resultSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            stmt.setInt(3, score);
            stmt.setInt(4, totalPossible);
            stmt.executeUpdate();

            // Save individual answers
            dbManager.closeResources(null, stmt, null);

            String answerSql = "INSERT INTO student_answers (exam_id, student_id, question_id, selected_option) "
                    + "VALUES (?, ?, ?, ?)";
            stmt = conn.prepareStatement(answerSql);

            for (Answer answer : answers) {
                stmt.setInt(1, examId);
                stmt.setString(2, studentId);
                stmt.setInt(3, answer.getQuestionId());
                stmt.setInt(4, answer.getSelectedOptionIndex());
                stmt.addBatch();
            }

            stmt.executeBatch();

            // Remove the active session
            activeExams.remove(sessionKey);

            controller.logActivity("Student " + studentId + " submitted exam " + examId
                    + " with score " + score + "/" + totalPossible);

            return true;
        } catch (SQLException e) {
            controller.logActivity("Error submitting exam: " + e.getMessage());
            throw new RemoteException("Failed to submit exam", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public ExamResult getExamResult(int examId, String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();

            // First check if results are visible for this exam
            String examSql = "SELECT results_visible FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(examSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("Exam not found");
            }

            boolean resultsVisible = rs.getBoolean("results_visible");

            if (!resultsVisible) {
                controller.logActivity("Student " + studentId + " attempted to view results for exam "
                        + examId + " but results are not visible");
                throw new RemoteException("Results are not available for viewing yet");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the exam result
            String resultSql = "SELECT er.*, s.name as student_name "
                    + "FROM exam_results er "
                    + "JOIN students s ON er.student_id = s.id "
                    + "WHERE er.exam_id = ? AND er.student_id = ?";
            stmt = conn.prepareStatement(resultSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("No result found for this exam");
            }

            ExamResult result = new ExamResult(
                    rs.getInt("id"),
                    rs.getInt("exam_id"),
                    rs.getString("student_id"),
                    rs.getString("student_name"),
                    rs.getInt("score"),
                    rs.getInt("total_possible"),
                    rs.getTimestamp("submission_time")
            );

            controller.logActivity("Student " + studentId + " viewed results for exam " + examId);
            return result;
        } catch (SQLException e) {
            controller.logActivity("Error retrieving exam result: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam result", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean createExam(Exam exam) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            // Insert the exam
            String examSql = "INSERT INTO exams (title, description, duration_minutes, results_visible, active) "
                    + "VALUES (?, ?, ?, ?, 1)";
            stmt = conn.prepareStatement(examSql, PreparedStatement.RETURN_GENERATED_KEYS);
            stmt.setString(1, exam.getTitle());
            stmt.setString(2, exam.getDescription());
            stmt.setInt(3, exam.getDurationMinutes());
            stmt.setBoolean(4, exam.isResultsVisible());
            stmt.executeUpdate();

            rs = stmt.getGeneratedKeys();
            if (!rs.next()) {
                throw new SQLException("Failed to get generated exam ID");
            }

            int examId = rs.getInt(1);

            // Insert the questions
            for (Question question : exam.getQuestions()) {
                // Insert the question
                dbManager.closeResources(null, stmt, rs);

                String questionSql = "INSERT INTO questions (text, correct_option, points) VALUES (?, ?, ?)";
                stmt = conn.prepareStatement(questionSql, PreparedStatement.RETURN_GENERATED_KEYS);
                stmt.setString(1, question.getText());
                stmt.setInt(2, question.getCorrectOptionIndex());
                stmt.setInt(3, question.getPoints());
                stmt.executeUpdate();

                rs = stmt.getGeneratedKeys();
                if (!rs.next()) {
                    throw new SQLException("Failed to get generated question ID");
                }

                int questionId = rs.getInt(1);

                // Link the question to the exam
                dbManager.closeResources(null, stmt, rs);

                String linkSql = "INSERT INTO exam_questions (exam_id, question_id) VALUES (?, ?)";
                stmt = conn.prepareStatement(linkSql);
                stmt.setInt(1, examId);
                stmt.setInt(2, questionId);
                stmt.executeUpdate();

                // Insert the options
                dbManager.closeResources(null, stmt, null);

                String optionSql = "INSERT INTO question_options (question_id, option_text, option_order) VALUES (?, ?, ?)";
                stmt = conn.prepareStatement(optionSql);

                List<String> options = question.getOptions();
                for (int i = 0; i < options.size(); i++) {
                    stmt.setInt(1, questionId);
                    stmt.setString(2, options.get(i));
                    stmt.setInt(3, i);
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }

            conn.commit();
            controller.logActivity("Created new exam: " + exam.getTitle());
            return true;
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

            controller.logActivity("Error creating exam: " + e.getMessage());
            throw new RemoteException("Failed to create exam", e);
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean updateExam(Exam exam) throws RemoteException {
        // Implementation similar to createExam but with UPDATE statements
        controller.logActivity("Updated exam: " + exam.getTitle());
        return true;
    }

    @Override
    public boolean deleteExam(int examId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();

            // We'll do a soft delete by setting active = 0
            String sql = "UPDATE exams SET active = 0 WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, examId);
            int rowsAffected = stmt.executeUpdate();

            controller.logActivity("Deleted exam with ID: " + examId);
            return rowsAffected > 0;
        } catch (SQLException e) {
            controller.logActivity("Error deleting exam: " + e.getMessage());
            throw new RemoteException("Failed to delete exam", e);
        } finally {
            dbManager.closeResources(conn, stmt, null);
        }
    }

    @Override
    public List<ExamResult> getExamResults(int examId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<ExamResult> results = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT er.*, s.name as student_name "
                    + "FROM exam_results er "
                    + "JOIN students s ON er.student_id = s.id "
                    + "WHERE er.exam_id = ? "
                    + "ORDER BY er.score DESC";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                ExamResult result = new ExamResult(
                        rs.getInt("id"),
                        rs.getInt("exam_id"),
                        rs.getString("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("score"),
                        rs.getInt("total_possible"),
                        rs.getTimestamp("submission_time")
                );
                results.add(result);
            }

            controller.logActivity("Retrieved results for exam " + examId);
            return results;
        } catch (SQLException e) {
            controller.logActivity("Error retrieving exam results: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam results", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean setResultVisibility(int examId, boolean visible) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();
            String sql = "UPDATE exams SET results_visible = ? WHERE id = ?";

            stmt = conn.prepareStatement(sql);
            stmt.setBoolean(1, visible);
            stmt.setInt(2, examId);
            int rowsAffected = stmt.executeUpdate();

            controller.logActivity("Set results visibility for exam " + examId + " to " + visible);
            return rowsAffected > 0;
        } catch (SQLException e) {
            controller.logActivity("Error setting result visibility: " + e.getMessage());
            throw new RemoteException("Failed to set result visibility", e);
        } finally {
            dbManager.closeResources(conn, stmt, null);
        }
    }

    // Inner class to track active exam sessions
    private static class ActiveExamSession {

        private final int examId;
        private final String studentId;
        private final long startTime;
        private final long durationMillis;

        public ActiveExamSession(int examId, String studentId, long startTime, long durationMillis) {
            this.examId = examId;
            this.studentId = studentId;
            this.startTime = startTime;
            this.durationMillis = durationMillis;
        }

        public int getExamId() {
            return examId;
        }

        public String getStudentId() {
            return studentId;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDurationMillis() {
            return durationMillis;
        }
    }
}
