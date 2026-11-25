package br.com.dinossauro;

import javax.swing.*;
// import java.awt.*;

public final class DinoNeuralApp {
    private DinoNeuralApp() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Trainer trainer = new Trainer();
            GamePanel panel = new GamePanel(trainer);
            JFrame frame = new JFrame("Dino Neural Network");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setContentPane(panel);
            frame.pack();
            frame.setResizable(true);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            SwingUtilities.invokeLater(panel::requestFocusInWindow);
        });
    }

}
