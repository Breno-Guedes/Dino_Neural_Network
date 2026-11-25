package br.com.dinossauro;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public final class GamePanelKeyboardTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel(new Trainer());
            assertKeyBinding(panel, "SPACE", "togglePause");
            assertKeyBinding(panel, "UP", "increaseSpeed");
            assertKeyBinding(panel, "DOWN", "decreaseSpeed");
            assertKeyBinding(panel, "R", "reset");
            assertKeyBinding(panel, "M", "toggleSound");
            assertKeyBinding(panel, "F11", "toggleFullscreen");

            invokeKeyAction(panel, "SPACE");
            if (!panel.isPaused()) throw new AssertionError("Espaço não pausou o jogo");
            invokeKeyAction(panel, "R");
            if (panel.isPaused()) throw new AssertionError("R não retomou o jogo após reset");
            System.out.println("Game panel keyboard test passed");
        });
        System.exit(0);
    }

    private static void assertKeyBinding(GamePanel panel, String keyStroke, String actionName) {
        Object mappedAction = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(keyStroke));
        if (!actionName.equals(mappedAction) || panel.getActionMap().get(actionName) == null)
            throw new AssertionError("Atalho não registrado para " + keyStroke);
    }

    private static void invokeKeyAction(GamePanel panel, String keyStroke) {
        Object actionName = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(keyStroke));
        Action action = panel.getActionMap().get(actionName);
        action.actionPerformed(new java.awt.event.ActionEvent(panel,
                java.awt.event.ActionEvent.ACTION_PERFORMED, null));
    }
}
