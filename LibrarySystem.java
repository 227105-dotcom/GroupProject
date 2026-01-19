package Groupproject;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.sql.*;

public class LibrarySystem extends Application {
    private static final String DB_URL = "jdbc:oracle:thin:@fsktmdbora.upm.edu.my:1521:fsktm";
    private static final String DB_USER = "E227105";
    private static final String DB_PASSWORD = "227105";

    private TableView<Book> bookTable = new TableView<>();
    private TextField tfUserId = new TextField();
    private TextField tfUserName = new TextField();
    private TextField tfBookId = new TextField();
    private TextField tfBookTitle = new TextField();

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // --- 表格定义：增加了 Borrower 列 ---
        TableColumn<Book, String> colId = new TableColumn<>("Book ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<Book, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        TableColumn<Book, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        TableColumn<Book, String> colBorrower = new TableColumn<>("Borrower ID"); // 新增
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("borrower"));

        bookTable.getColumns().addAll(colId, colTitle, colStatus, colBorrower);

        // --- 输入表单 ---
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("User ID (Required):"), 0, 0);
        grid.add(tfUserId, 1, 0);
        grid.add(new Label("User Name (Register):"), 0, 1);
        grid.add(tfUserName, 1, 1);
        grid.add(new Label("Book ID:"), 0, 2);
        grid.add(tfBookId, 1, 2);
        grid.add(new Label("Book Title (Add):"), 0, 3);
        grid.add(tfBookTitle, 1, 3);

        // --- 按钮组 ---
        Button btnReg = new Button("Register");
        Button btnAdd = new Button("Admin: Add Book");
        Button btnBorrow = new Button("Borrow");
        Button btnReturn = new Button("Return");
        Button btnRefresh = new Button("Refresh");

        btnReg.setOnAction(e -> handleRegister());
        btnAdd.setOnAction(e -> handleAdminInsert());
        btnBorrow.setOnAction(e -> handleTransaction("Borrow"));
        btnReturn.setOnAction(e -> handleTransaction("Return"));
        btnRefresh.setOnAction(e -> loadBooks());

        HBox buttons = new HBox(10, btnReg, btnAdd, btnBorrow, btnReturn, btnRefresh);
        root.getChildren().addAll(new Label("Library System - User Tracking Mode"), bookTable, grid, buttons);

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.setTitle("UPM Library - Admin & User Console");
        primaryStage.show();
        loadBooks();
    }

    private void loadBooks() {
        ObservableList<Book> list = FXCollections.observableArrayList();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM books_227105 ORDER BY book_id")) {
            while (rs.next()) {
                list.add(new Book(rs.getString("book_id"), rs.getString("title"),
                        rs.getString("status"), rs.getString("borrower_id")));
            }
            bookTable.setItems(list);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void handleRegister() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO users_227105 VALUES (?, ?, 'STUDENT')")) {
            ps.setString(1, tfUserId.getText());
            ps.setString(2, tfUserName.getText());
            ps.executeUpdate();
            new Alert(Alert.AlertType.INFORMATION, "Registered!").show();
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void handleAdminInsert() {
        String uid = tfUserId.getText();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            PreparedStatement ck = conn.prepareStatement("SELECT role FROM users_227105 WHERE user_id = ?");
            ck.setString(1, uid);
            ResultSet rs = ck.executeQuery();
            if (rs.next() && rs.getString("role").equals("ADMIN")) {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO books_227105 (book_id, title, status) VALUES (?, ?, 'Available')");
                ps.setString(1, tfBookId.getText());
                ps.setString(2, tfBookTitle.getText());
                ps.executeUpdate();
                loadBooks();
            } else { showAlert("Only Admins can add books!"); }
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void handleTransaction(String type) {
        String uid = tfUserId.getText();
        String bid = tfBookId.getText();
        String status = type.equals("Borrow") ? "Borrowed" : "Available";
        String borrower = type.equals("Borrow") ? uid : null; // 归还时清空借阅人

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);

            // 更新书籍状态和借阅人
            PreparedStatement up = conn.prepareStatement("UPDATE books_227105 SET status = ?, borrower_id = ? WHERE book_id = ?");
            up.setString(1, status);
            up.setString(2, borrower);
            up.setString(3, bid);
            if (up.executeUpdate() == 0) throw new Exception("Book not found!");

            // 写入审计日志
            PreparedStatement log = conn.prepareStatement("INSERT INTO records_227105 (user_id, book_id, action) VALUES (?, ?, ?)");
            log.setString(1, uid); log.setString(2, bid); log.setString(3, type);
            log.executeUpdate();

            conn.commit();
            loadBooks();
            new Alert(Alert.AlertType.INFORMATION, type + " Successful!").show();
        } catch (Exception e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String c) { new Alert(Alert.AlertType.ERROR, c).show(); }

    public static class Book {
        private String id, title, status, borrower;
        public Book(String id, String title, String status, String borrower) {
            this.id = id; this.title = title; this.status = status; this.borrower = (borrower == null) ? "-" : borrower;
        }
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getStatus() { return status; }
        public String getBorrower() { return borrower; }
    }

    public static void main(String[] args) { launch(args); }
}