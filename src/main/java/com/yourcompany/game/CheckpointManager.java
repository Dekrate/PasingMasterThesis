package com.yourcompany.game;

import java.io.*;
import java.util.Properties;

public class CheckpointManager {

    private final String checkpointFile;
    private Properties properties;

    public CheckpointManager(String repositoryPath) {
        // Create a unique checkpoint file name based on the repository path
        String repoName = new File(repositoryPath).getName();
        this.checkpointFile = repoName + "_checkpoint.properties";
        this.properties = new Properties();
        loadCheckpoint();
    }

    public void saveCheckpoint(String lastCommit, boolean isScanned) {
        properties.setProperty("lastCommit", lastCommit);
        properties.setProperty("isScanned", String.valueOf(isScanned));

        try (OutputStream output = new FileOutputStream(checkpointFile)) {
            properties.store(output, "Checkpoint for repository analysis");
        } catch (IOException io) {
            System.err.println("Błąd podczas zapisu checkpointu do pliku " + checkpointFile);
            io.printStackTrace();
        }
    }

    public void loadCheckpoint() {
        try (InputStream input = new FileInputStream(checkpointFile)) {
            properties.load(input);
            System.out.println("Znaleziono plik checkpointu dla repozytorium. Ostatni commit: " + getLastCommit() + ", Skanowanie zakończone: " + isScanned());
        } catch (FileNotFoundException e) {
            System.out.println("Nie znaleziono pliku checkpointu dla repozytorium. Rozpoczynam od początku.");
        } catch (IOException ex) {
            System.err.println("Błąd podczas odczytu checkpointu z pliku " + checkpointFile);
            ex.printStackTrace();
        }
    }

    public String getLastCommit() {
        return properties.getProperty("lastCommit");
    }

    public boolean isScanned() {
        return Boolean.parseBoolean(properties.getProperty("isScanned", "false"));
    }

    public void setStartingCommit(String commit) {
        properties.setProperty("lastCommit", commit);
        properties.setProperty("isScanned", "false");
        try (OutputStream output = new FileOutputStream(checkpointFile)) {
            properties.store(output, "Debug checkpoint starting from commit: " + commit);
        } catch (IOException io) {
            System.err.println("Błąd podczas zapisu checkpointu do pliku " + checkpointFile);
            io.printStackTrace();
        }
    }
}
