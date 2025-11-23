package com.example.tanksclient;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;

public class AuthState {
    private static final StringProperty token = new SimpleStringProperty("");
    private static final StringProperty username = new SimpleStringProperty("");
    private static final IntegerProperty wins = new SimpleIntegerProperty(0);
    private static final IntegerProperty loses = new SimpleIntegerProperty(0);
    private static final IntegerProperty draws = new SimpleIntegerProperty(0);
    private static final BooleanProperty loggedIn = new SimpleBooleanProperty(false);

    public static String getToken() { return token.get(); }
    public static void setToken(String t) {
        token.set(t == null ? "" : t);
        loggedIn.set(token.get() != null && !token.get().isBlank());
    }
    public static StringProperty tokenProperty() { return token; }

    public static String getUsername() { return username.get(); }
    public static void setUsername(String u) { username.set(u == null ? "" : u); }
    public static StringProperty usernameProperty() { return username; }

    public static int getWins() { return wins.get(); }
    public static void setWins(int w) { wins.set(w); }
    public static IntegerProperty winsProperty() { return wins; }

    public static int getLoses() { return loses.get(); }
    public static void setLoses(int l) { loses.set(l); }
    public static IntegerProperty losesProperty() { return loses; }

    public static int getDraws() { return draws.get(); }
    public static void setDraws(int d) { draws.set(d); }
    public static IntegerProperty drawsProperty() { return draws; }

    public static boolean isLoggedIn() { return loggedIn.get(); }
    public static BooleanProperty loggedInProperty() { return loggedIn; }

    public static void setProfile(Map<String, Object> profile) {
        if (profile == null) return;
        Object u = profile.get("username");
        Object w = profile.get("wins");
        Object l = profile.get("loses");
        Object d = profile.get("draws");
        setUsername(u == null ? "" : u.toString());
        setWins(w instanceof Number ? ((Number) w).intValue() : 0);
        setLoses(l instanceof Number ? ((Number) l).intValue() : 0);
        setDraws(d instanceof Number ? ((Number) d).intValue() : 0);
    }

    public static void clear() {
        setToken("");
        setUsername("");
        setWins(0);
        setLoses(0);
        setDraws(0);
    }
}
