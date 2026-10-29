package com.student.signit;

import java.sql.Connection;

public class TestConnection {
    public static void main(String[] args) throws Exception {
        try (Connection c = Database.getConnection()) {
            System.out.println("Connected: " + c.getMetaData().getURL());
        }
    }
}