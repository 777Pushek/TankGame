# 🎮 TankGame - Gra Sieciowa Multiplayer

Projekt przedstawiający realizację gry wieloosobowej w czasie rzeczywistym (typu "Tanks") z wykorzystaniem architektury rozproszonej Klient-Serwer.

## 📋 Opis Projektu

System umożliwia rozgrywkę wieloosobową, w której gracze sterują czołgami na wspólnej mapie. Projekt składa się z dwóch niezależnych aplikacji:
1.  **Klient (Desktop):** Aplikacja okienkowa napisana w **JavaFX**, odpowiedzialna za wizualizację gry, animacje i obsługę sterowania.
2.  **Serwer (Backend):** Aplikacja **Spring Boot** działająca w kontenerze **Docker**, pełniąca rolę autorytatywnego źródła prawdy (oblicza fizykę, kolizje i synchronizuje stan gry między graczami).

## 🚀 Zastosowane Technologie

### Klient (Frontend)
Aplikacja desktopowa odpowiadająca za warstwę prezentacji.
* **Język:** Java 25
* **GUI:** JavaFX 21 (z wykorzystaniem FXML)
* **Zarządzanie projektem:** Apache Maven
* **Komunikacja:** Jackson (JSON), WebSocket Client
* **Narzędzia:** Project Lombok, JUnit 5

### Serwer (Backend)
Serwer gry uruchamiany w środowisku kontenerowym.
* **Język:** Java 21 (LTS)
* **Framework:** Spring Boot 3.5.6
* **Baza Danych:** MySQL 9.4 (przechowywanie kont i statystyk)
* **Komunikacja Real-time:** Spring WebSockets (protokół STOMP)
* **API:** Spring Web (REST)
* **Bezpieczeństwo:** Spring Security + JWT (JSON Web Token)
* **Infrastruktura:** Docker, Gradle

---

## ⚙️ Wymagania Systemowe

Aby uruchomić projekt lokalnie, wymagane jest zainstalowanie następującego oprogramowania:

1.  **Java Development Kit (JDK):**
    * Wersja 25 (wymagana dla Klienta).
    * Wersja 21 (wymagana dla Serwera, jeśli uruchamiany bez Dockera).
2.  **Docker & Docker Compose:** Niezbędne do uruchomienia serwera i bazy danych.
3.  **Maven:** Do zbudowania i uruchomienia aplikacji klienckiej.
4.  **Git:** Do pobrania kodu źródłowego.

---

## 🛠️ Instrukcja Uruchomienia

### Krok 1: Uruchomienie Serwera (Backend)

Serwer jest skonfigurowany do pracy w kontenerach Docker, co eliminuje konieczność ręcznej instalacji bazy danych MySQL.

1.  Otwórz terminal w katalogu serwera (tam, gdzie znajduje się plik `build.gradle`):
    ```bash
    cd server
    ```
2.  Zbuduj i uruchom środowisko serwerowe:
    ```bash
    docker-compose up --build
    ```
3.  Po poprawnym uruchomieniu:
    * Serwer nasłuchuje na porcie: `8080`
    * Baza danych MySQL jest dostępna wewnętrznie w sieci Dockera.

### Krok 2: Uruchomienie Klienta (Gra)

1.  Otwórz **nowe** okno terminala w katalogu klienta (tam, gdzie znajduje się plik `pom.xml`):
    ```bash
    cd client
    ```
2.  Uruchom aplikację za pomocą Mavena:
    ```bash
    mvn clean javafx:run
    ```

---

## 🏗️ Architektura i Komunikacja

System wykorzystuje hybrydowy model komunikacji:

1.  **REST API (HTTP):**
    * Służy do operacji jednorazowych, takich jak: Logowanie, Rejestracja, Pobieranie listy pokoi.
    * Zabezpieczone tokenami **JWT**.

2.  **WebSockets (TCP):**
    * Utrzymuje stałe połączenie podczas rozgrywki.
    * **Klient -> Serwer:** Wysyła intencje ruchu (np. `KEY_UP`, `SHOOT`).
    * **Serwer -> Klient:** Wysyła zaktualizowany stan świata (pozycje wszystkich czołgów, pocisków, stan mapy) w formacie JSON.

## 👥 Autorzy

* **Mikołaj Kosmala** - Interfejs JavaFX, Widoki FXML, Renderowanie grafiki, Obsługa sterowania.
* **Kacper Kowalczyk** - Komunikacja WebSocket, Synchronizacja stanu gry, Fizyka/Logika serwera, Docker.
* **Przemysław Kłos** - Architektura Spring Boot, Baza danych MySQL, Autoryzacja JWT, REST API.
