package com.student.signit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String HOST = env("SIGNIT_DB_HOST", "localhost");
    private static final String PORT = env("SIGNIT_DB_PORT", "3306");
    private static final String NAME = env("SIGNIT_DB_NAME", "signit");
    private static final String USER = env("SIGNIT_DB_USER", "root");
    private static final String PASSWORD = env("SIGNIT_DB_PASSWORD", "");   // XAMPP / default MySQL: empty

    private static final String PARAMS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&useUnicode=true&characterEncoding=utf8";

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private static void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /** Connection to the SignIT database. */
    public static Connection getConnection() throws SQLException {
        loadDriver();
        return DriverManager.getConnection("jdbc:mysql://" + HOST + ":" + PORT + "/" + NAME + PARAMS, USER, PASSWORD);
    }

    /** Creates the database itself if it does not exist yet (tables are created by DbStore). */
    static void ensureDatabase() throws SQLException {
        loadDriver();
        try (Connection c = DriverManager.getConnection("jdbc:mysql://" + HOST + ":" + PORT + "/" + PARAMS, USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + NAME + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }
}