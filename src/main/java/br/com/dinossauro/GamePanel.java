package br.com.dinossauro;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import javax.swing.JFrame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


final class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 1200, HEIGHT = 678;
    private static final Font VT323 = loadRetroFont("assets/fontes/VT323-Regular.ttf");
    private static final Font COURIER_PRIME = loadRetroFont("assets/fontes/CourierPrime-Regular.ttf");
    private static final Color BLUE = new Color(151, 94, 25);
    private static final Color[] DINO_COLORS = {
            new Color(169, 81, 62), new Color(184, 113, 52), new Color(180, 147, 66), new Color(94, 118, 73),
            new Color(67, 112, 111), new Color(75, 90, 129), new Color(125, 91, 130), new Color(162, 91, 121)
    };
    private final Trainer trainer;
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, BufferedImage> coloredDinos = new HashMap<>();
    private final double[] cloudX = {80, 240, 390, 590, 755, 960, 1080};
    private final double[] cloudY = {505, 535, 490, 555, 515, 545, 495};
    private final double[] mountainOffsets = {0, 0, 0};
    private boolean paused;
    private int speedMultiplier = 1;
    private int animationTick;
    private int birdFrame;
    private final SoundEngine sound = new SoundEngine();
    private int lastChampionAction = -1;
    private int lastGeneration = 1;
    private boolean soundEnabled = true;

    GamePanel(Trainer trainer) {
        this.trainer = trainer;
        setBackground(new Color(213, 197, 160));
        setPreferredSize(new java.awt.Dimension(WIDTH, HEIGHT));
        loadImages();
        sound.startMusic();
        new Timer(35, e -> {
            if (!paused) for (int i = 0; i < speedMultiplier; i++) trainer.tick();
            if (!paused && soundEnabled && trainer.champion != null) {
                int action = trainer.champion.lastAction;
                if (action != lastChampionAction && action != Trainer.RUN) {
                    sound.playEffect(action == Trainer.JUMP ? SoundEngine.Effect.JUMP
                            : action == Trainer.DUCK ? SoundEngine.Effect.DUCK : SoundEngine.Effect.PLANE);
                }
                if (trainer.generation != lastGeneration) sound.playEffect(SoundEngine.Effect.CRASH);
                lastChampionAction = action;
                lastGeneration = trainer.generation;
            }
            if (!paused) {
                animationTick++;
                for (int layer = 0; layer < mountainOffsets.length; layer++)
                    mountainOffsets[layer] = (mountainOffsets[layer] + trainer.speed * speedMultiplier * (.04 + layer * .06)) % WIDTH;
                for (int i = 0; i < cloudX.length; i++) {
                    cloudX[i] -= trainer.speed * speedMultiplier * .08;
                    if (cloudX[i] < -46) cloudX[i] = WIDTH + 20;
                }
                if (animationTick % 6 == 0) birdFrame = 1 - birdFrame;
            }
            repaint();
        }).start();
        setFocusable(true);
        bindKey("SPACE", "togglePause", () -> paused = !paused);
        bindKey("UP", "increaseSpeed", () -> speedMultiplier = Math.min(8, speedMultiplier * 2));
        bindKey("DOWN", "decreaseSpeed", () -> speedMultiplier = Math.max(1, speedMultiplier / 2));
        bindKey("R", "reset", this::reset);
        bindKey("M", "toggleSound", () -> {
            soundEnabled = !soundEnabled;
            if (soundEnabled) sound.startMusic(); else sound.stopMusic();
        });
        bindKey("F11", "toggleFullscreen", this::toggleFullscreen);
    }

    private void bindKey(String keyStroke, String actionName, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(keyStroke), actionName);
        getActionMap().put(actionName, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    private static Font loadRetroFont(String path) {
        try { return Font.createFont(Font.TRUETYPE_FONT, new File(path)); }
        catch (Exception ignored) { return new Font(Font.MONOSPACED, Font.PLAIN, 12); }
    }

    private static Font retroFont(int style, int size) {
        Font base = size >= 13 ? VT323 : COURIER_PRIME;
        float scaledSize = size >= 13 ? size * 1.25f : size;
        return base.deriveFont(style, scaledSize);
    }

    private void loadImages() {
        String[] names = {"dino0.bmp", "dino1.bmp", "dino2.bmp", "dino3.bmp", "dino4.bmp",
                "dino5.bmp", "dino6.bmp", "dino7.bmp", "dino8.bmp", "dino9.bmp", "nuvem.bmp",
                "fundo0.bmp", "fundo1.bmp", "fundo2.bmp", "fundo3.bmp", "fundo4.bmp", "fundo5.bmp",
                "chao0.bmp", "chao1.bmp", "chao2.bmp", "chao3.bmp", "chao4.bmp", "chao5.bmp",
                "obs0.bmp", "obs1.bmp", "obs2.bmp", "obs3.bmp", "obs4.bmp", "obs5.bmp", "obs6.bmp",
                "aviao0.bmp", "aviao1.bmp"};
        for (String name : names) {
            try {
                BufferedImage source = ImageIO.read(new File("assets/imagens/" + name));
                if (name.startsWith("aviao")) source = makePlaneTransparent(source);
                else if (name.matches("chao[0-5]\\.bmp")) source = makeFloorSprite(source);
                else {
                    source = makeKeyTransparent(source, 255, 174, 201, 12);
                    if (name.matches("fundo[0-5]\\.bmp")) {
                        int layer = (name.charAt(5) - '0') / 2;
                        Color[] tints = {new Color(128, 113, 82), new Color(141, 121, 78), new Color(164, 135, 82)};
                        source = colorizeNeutralSprite(source, tints[layer], .66);
                    } else if (name.matches("obs[0-4]\\.bmp")) {
                        source = colorizeNeutralSprite(source, new Color(110, 122, 68), .78);
                    }
                }
                images.put(name, source);
            }
            catch (Exception ignored) { /* Permite rodar quando um asset opcional não existir. */ }
        }
    }

    private BufferedImage makeKeyTransparent(BufferedImage source, int kr, int kg, int kb, int tolerance) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int argb = source.getRGB(x, y);
            int r = (argb >>> 16) & 255, green = (argb >>> 8) & 255, b = argb & 255;
            boolean key = Math.abs(r-kr) <= tolerance && Math.abs(green-kg) <= tolerance && Math.abs(b-kb) <= tolerance;
            result.setRGB(x, y, key ? 0 : (argb | 0xff000000));
        }
        return result;
    }

    private BufferedImage makeFloorSprite(BufferedImage source) {
        BufferedImage transparent = makeKeyTransparent(source, 255, 174, 201, 40);
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            result.setRGB(x, y, ((transparent.getRGB(x, y) >>> 24) & 255) == 0 ? 0 : 0xff000000);
        }
        return result;
    }

    private BufferedImage makePlaneTransparent(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int argb = source.getRGB(x, y);
            int r = (argb >>> 16) & 255, green = (argb >>> 8) & 255, b = argb & 255;
            // Os BMPs do avião usam magenta #FF91F9 (diferente do rosa dos outros sprites).
            boolean pinkFringe = r > 225 && green > 75 && green < 220 && b > 175 && b - green > 30;
            result.setRGB(x, y, pinkFringe ? 0 : (argb | 0xff000000));
        }
        return result;
    }

    private BufferedImage colorizeNeutralSprite(BufferedImage source, Color tint, double strength) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int argb = source.getRGB(x, y), alpha = (argb >>> 24) & 255;
            if (alpha == 0) { result.setRGB(x, y, 0); continue; }
            int r = (argb >>> 16) & 255, green = (argb >>> 8) & 255, b = argb & 255;
            int luminance = (r + green + b) / 3;
            if (luminance > 35) {
                r = (int) (r * (1-strength) + tint.getRed() * strength);
                green = (int) (green * (1-strength) + tint.getGreen() * strength);
                b = (int) (b * (1-strength) + tint.getBlue() * strength);
            }
            result.setRGB(x, y, (alpha << 24) | (r << 16) | (green << 8) | b);
        }
        return result;
    }

    private BufferedImage coloredDino(int frame, int colorIndex) {
        String key = frame + ":" + colorIndex;
        return coloredDinos.computeIfAbsent(key, ignored -> {
            BufferedImage source = images.get("dino" + frame + ".bmp");
            if (source == null) return null;
            Color tint = DINO_COLORS[colorIndex % DINO_COLORS.length];
            BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y), alpha = (argb >>> 24) & 255;
                int r = (argb >>> 16) & 255, green = (argb >>> 8) & 255, b = argb & 255;
                if (alpha > 0 && r > 200 && green > 200 && b > 200) {
                    int brightness = (r + green + b) / 3;
                    r = tint.getRed() * brightness / 255;
                    green = tint.getGreen() * brightness / 255;
                    b = tint.getBlue() * brightness / 255;
                    argb = (alpha << 24) | (r << 16) | (green << 8) | b;
                }
                result.setRGB(x, y, argb);
            }
            return result;
        });
    }

    private void toggleFullscreen() {
        Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (!(window instanceof JFrame frame)) return;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (device.getFullScreenWindow() == frame) {
            device.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);
            frame.setResizable(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.setSize(WIDTH, HEIGHT);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        } else {
            frame.dispose();
            frame.setUndecorated(true);
            frame.setResizable(false);
            frame.setBounds(device.getDefaultConfiguration().getBounds());
            frame.setVisible(true);
            device.setFullScreenWindow(frame);
            frame.setBounds(device.getDefaultConfiguration().getBounds());
        }
        requestFocusInWindow();
        frame.requestFocus();
    }

    boolean isPaused() { return paused; }
    void togglePause() { paused = !paused; }
    int cycleSpeed() { speedMultiplier = speedMultiplier == 1 ? 2 : speedMultiplier == 2 ? 4 : 1; return speedMultiplier; }
    void reset() { trainer.resetTraining(); paused = false; speedMultiplier = 1; requestFocusInWindow(); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double scale = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
        int offsetX = (int) ((getWidth() - WIDTH * scale) / 2);
        int offsetY = (int) ((getHeight() - HEIGHT * scale) / 2);
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.setColor(new Color(213, 197, 160)); g.fillRect(0, 0, WIDTH, HEIGHT);
        drawFitnessChart(g);
        drawNeuralNetwork(g);
        drawStatistics(g);
        drawChampionSummary(g);
        drawGameWorld(g);
        g.dispose();
    }

    private void drawFitnessChart(Graphics2D g) {
        int cardX = 18, cardY = 18, cardW = 522, cardH = 346;
        g.setColor(new Color(238, 226, 195)); g.fillRect(cardX, cardY, cardW, cardH);
        g.setColor(new Color(120, 96, 61)); g.drawRect(cardX, cardY, cardW, cardH);
        g.setColor(new Color(54, 39, 22)); g.setFont(retroFont( Font.BOLD, 16));
        g.drawString("Desempenho da geração " + trainer.generation, cardX + 18, cardY + 27);
        //g.setColor(new Color(111, 91, 61)); g.setFont(retroFont( Font.PLAIN, 11));
        //g.drawString("Fitness acumulado durante esta geração. Mais alto = melhor.", cardX + 18, cardY + 46);
        int px = cardX + 52, py = cardY + 66, pw = cardW - 72, ph = 238;
        double max = Math.max(10, Math.max(trainer.bestFitness(), trainer.meanFitness()));
        for (double value : trainer.generationBestHistory) max = Math.max(max, value);
        for (double value : trainer.generationMeanHistory) max = Math.max(max, value);
        for (int i = 0; i <= 4; i++) {
            int gy = py + i * ph / 4;
            g.setColor(new Color(211, 197, 162)); g.drawLine(px, gy, px + pw, gy);
            g.setColor(new Color(111, 91, 61)); g.setFont(retroFont( Font.PLAIN, 10));
            g.drawString(String.format("%.0f", max * (4 - i) / 4.0), cardX + 10, gy + 4);
        }
        for (int i = 0; i <= 5; i++) {
            int gx = px + i * pw / 5;
            g.setColor(new Color(226, 214, 183)); g.drawLine(gx, py, gx, py + ph);
        }
        g.setColor(new Color(111, 91, 61)); g.setFont(retroFont( Font.PLAIN, 10));
        g.drawString("início", px, py + ph + 15);
        g.drawString("tempo da geração", px + pw / 2 - 38, py + ph + 15);
        g.drawString("agora", px + pw - 28, py + ph + 15);
        Color blue = new Color(170, 105, 29), coral = new Color(125, 76, 46);
        drawHistory(g, trainer.generationBestHistory, trainer.bestFitness(), 0, max, px, py, pw, ph, blue, false);
        drawHistory(g, trainer.generationMeanHistory, trainer.meanFitness(), 0, max, px, py, pw, ph, coral, true);
        int legendY = cardY + cardH - 17 + 8;
        g.setColor(blue); g.fillRoundRect(cardX + 20, legendY - 7, 14, 4, 4, 4);
        g.setColor(new Color(70, 51, 30)); g.setFont(retroFont( Font.PLAIN, 11));
        g.drawString("Melhor indivíduo", cardX + 40, legendY);
        g.setColor(coral); g.fillRoundRect(cardX + 192, legendY - 7, 14, 4, 4, 4);
        g.setColor(new Color(70, 51, 30)); g.drawString("Média da população", cardX + 212, legendY);
    }
    private void drawHistory(Graphics2D g, java.util.List<Double> history, double current, double min, double max,
                             int x, int y, int w, int h, Color color, boolean dashed) {
        double range = Math.max(1, max - min);
        int n = history.size();
        if (n == 0) {
            int py = y + h - 3 - (int) (Math.max(0, Math.min(1, (current - min) / range)) * (h - 6));
            g.setColor(color); g.fillOval(x + w - 3, py - 3, 6, 6);
            return;
        }
        if (n == 1) {
            int py = y + h - 3 - (int) (Math.max(0, Math.min(1, (history.get(0) - min) / range)) * (h - 6));
            g.setColor(color); g.fillOval(x + w - 3, py - 3, 6, 6);
            return;
        }
        g.setColor(color);
        g.setStroke(dashed ? new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{4, 3}, 0)
                : new BasicStroke(1.8f));
        for (int i = 1; i < n; i++) {
            double a = history.get(i - 1), b = history.get(i);
            int x1 = x + (i - 1) * w / (n - 1), x2 = x + i * w / (n - 1);
            int y1 = y + h - 3 - (int)(Math.max(0, Math.min(1, (a - min) / range)) * (h - 6));
            int y2 = y + h - 3 - (int)(Math.max(0, Math.min(1, (b - min) / range)) * (h - 6));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawNeuralNetwork(Graphics2D g) {
        int cx = 554, cy = 18, cw = 628, ch = 346;
        g.setColor(new Color(238, 226, 195)); g.fillRoundRect(cx, cy, cw, ch, 18, 18);
        g.setColor(new Color(120, 96, 61)); g.drawRoundRect(cx, cy, cw, ch, 18, 18);
        g.setColor(new Color(54, 39, 22)); g.setFont(retroFont( Font.BOLD, 16));
        g.drawString("Rede Neural", cx + 18, cy + 27);
        //g.setColor(new Color(111, 91, 61)); g.setFont(retroFont( Font.PLAIN, 11));
        //g.drawString("Durante um movimento, entradas e saídas mostram a decisão que iniciou o estado ativo.", cx + 18, cy + 46);
        Dino champion = trainer.champion;
        if (champion == null) return;
        NeuralNetwork net = champion.brain;
        int[] x = {822, 930, 1038};
        int[] inputY = {82, 106, 130, 154, 178, 202, 226, 250, 274, 298, 322};
        int[] hiddenY = {78, 100, 122, 144, 166, 188, 210, 232, 254, 276, 298, 320};
        int[] outputY = {112, 182, 252, 322};
        String[] labels = {"Distância do obstáculo", "Largura do obstáculo", "Altura do obstáculo",
                "Altura acima do chão", "Velocidade do jogo", "Altura do dinossauro",
                "Velocidade vertical", "Tipo: -1, 0, 1", "Avião ativo", "Recarga do avião",
                "Tempo abaixado"};
        double[] state = trainer.championInputs();
        double[] raw = {state[0] * 600, state[1] * 220, state[2] * 120, state[3] * 120,
                state[4] * 12, state[5] * 150, state[6] * 12, state[7],
                state[8], state[9] * 300, state[10] * 24};
        g.setFont(retroFont( Font.PLAIN, 10));
        for (int i = 0; i < labels.length; i++) {
            g.setColor(new Color(87, 66, 39)); g.drawString(labels[i], cx + 16, inputY[i] + 4);
            g.setColor(new Color(146, 91, 30)); g.setFont(retroFont( Font.BOLD, 10));
            g.drawString(String.format("%.1f", raw[i]), cx + 179, inputY[i] + 4);
            g.setFont(retroFont( Font.PLAIN, 10));
        }
        // Conexões discretas: valores positivos em azul, negativos em coral.
        for (int layer = 0; layer < 2; layer++) {
            for (int j = 0; j < net.layerSize(layer + 1); j++) {
                for (int i = 0; i < net.layerSize(layer); i++) {
                    int ya = layer == 0 ? inputY[i] : hiddenY[i];
                    int yb = layer == 0 ? hiddenY[j] : outputY[j];
                    double weight = net.weight(layer, j, i);
                    g.setColor(weight >= 0 ? new Color(76, 137, 184, 44) : new Color(210, 112, 100, 42));
                    g.setStroke(new BasicStroke(.8f)); g.drawLine(x[layer], ya, x[layer + 1], yb);
                }
            }
        }
        int selected = trainer.championAction();
        if (selected < 0 || selected >= net.layerSize(2)) selected = 0;
        Color choice = new Color(151, 94, 25);
        int selectedHidden = 0;
        double bestContribution = Double.NEGATIVE_INFINITY;
        for (int h = 0; h < net.layerSize(1); h++) {
            double contribution = net.activation(1, h) * net.weight(1, selected, h);
            if (contribution > bestContribution) { bestContribution = contribution; selectedHidden = h; }
        }
        int selectedInput = 0;
        bestContribution = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < net.layerSize(0); i++) {
            double contribution = net.activation(0, i) * net.weight(0, selectedHidden, i);
            if (contribution > bestContribution) { bestContribution = contribution; selectedInput = i; }
        }
        g.setColor(choice); g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x[0], inputY[selectedInput], x[1], hiddenY[selectedHidden]);
        g.drawLine(x[1], hiddenY[selectedHidden], x[2], outputY[selected]);
        for (int i = 0; i < net.layerSize(0); i++) drawNeuron(g, x[0], inputY[i], net.activation(0, i), 6);
        for (int i = 0; i < net.layerSize(1); i++) drawNeuron(g, x[1], hiddenY[i], net.activation(1, i), 6);
        String[] actions = {"Correr", "Pular", "Abaixar", "Avião"};
        for (int i = 0; i < actions.length; i++) {
            g.setColor(i == selected ? choice : new Color(95, 108, 124));
            g.fillOval(x[2] - 7, outputY[i] - 7, 14, 14);
            g.setFont(retroFont( i == selected ? Font.BOLD : Font.PLAIN, 11));
            g.drawString(actions[i], 1054, outputY[i] - 1);
            g.setFont(retroFont( Font.PLAIN, 10)); g.setColor(new Color(111, 121, 135));
            g.drawString(String.format("%.2f", net.activation(2, i)), 1054, outputY[i] + 12);
        }
        g.setColor(new Color(151, 94, 25)); g.setFont(retroFont( Font.BOLD, 10));
        g.drawString("ESTADO ATUAL", cx + 18, cy + ch - 14);
        String activeState = actions[selected];
        if (selected == Trainer.PLANE && champion.planeTicks > 0)
            activeState = "Avião em voo — " + champion.planeTicks + " ticks restantes (decisão da rede)";
        else if (selected == Trainer.DUCK && champion.duckTicks > 0)
            activeState = "Abaixado — " + champion.duckTicks + " ticks restantes (decisão da rede)";
        else if (selected == Trainer.JUMP && champion.y > 1)
            activeState = "Pulando (decisão da rede)";
        g.drawString(activeState, cx + 105, cy + ch - 14);
    }
    private void drawNeuron(Graphics2D g, int x, int y, double activation, int radius) {
        Color fill = activation >= 0 ? new Color(173, 116, 38) : new Color(119, 94, 59);
        g.setColor(new Color(238, 226, 195)); g.fillOval(x - radius - 1, y - radius - 1, (radius + 1) * 2, (radius + 1) * 2);
        g.setColor(fill); g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }
    private void drawStatistics(Graphics2D g) {
        g.setColor(new Color(238, 226, 195)); g.fillRect(18, 375, 522, 76);
        g.setColor(new Color(120, 96, 61)); g.drawRect(18, 375, 522, 76);
        g.setColor(new Color(54, 39, 22)); g.setFont(retroFont( Font.BOLD, 13));
        g.drawString("Geração " + trainer.generation, 34, 398);
        g.setColor(new Color(87, 66, 39)); g.setFont(retroFont( Font.PLAIN, 11));
        g.drawString(String.format("Distância  %.0f px", trainer.distance), 34, 419);
        g.drawString(String.format("Recorde  %.0f px", trainer.record), 190, 419);
        g.drawString(String.format("Vivos  %d / %d", trainer.aliveCount(), Trainer.POPULATION), 34, 439);
        g.setFont(retroFont(Font.PLAIN, 8));
        g.drawString("M som | Espaço pausa | Cima/Baixo vel. | R reset | F11 tela cheia", 190, 439);
    }
    private void drawChampionSummary(Graphics2D g) {
        Dino d = trainer.champion;
        if (d == null) return;
        int x = 554, y = 375, w = 628, h = 76;
        g.setColor(new Color(238, 226, 195)); g.fillRect(x, y, w, h);
        g.setColor(new Color(120, 96, 61)); g.drawRect(x, y, w, h);
        g.setColor(new Color(54, 39, 22)); g.setFont(retroFont( Font.BOLD, 13));
        g.drawString("MELHOR INDIVÍDUO", x + 17, y + 22);
        g.setColor(new Color(87, 66, 39)); g.setFont(retroFont( Font.PLAIN, 11));
        int[] actions = trainer.actionCounts();
        g.drawString(String.format("Fitness %.0f    Obstáculos %.0f    Estado %s", d.fitness, (double)d.obstaclesCleared, d.alive ? "em jogo" : "eliminado"), x + 17, y + 44);
        g.drawString(String.format("Ações neste instante: correr %d  ·  pular %d  ·  abaixar %d  ·  avião %d",
                actions[Trainer.RUN], actions[Trainer.JUMP], actions[Trainer.DUCK], actions[Trainer.PLANE]), x + 17, y + 63);
    }
    private void drawGameWorld(Graphics2D g) {
        java.awt.Shape previousClip = g.getClip();
        g.clipRect(9, 467, WIDTH - 18, HEIGHT - 468);
        int ground = 630;
        g.setPaint(new GradientPaint(0, 468, new Color(225, 209, 169), 0, ground, new Color(235, 222, 190)));
        g.fillRect(0, 468, WIDTH, ground - 468);
        g.setPaint(new Color(238, 226, 195));
        // Original mountain artwork is 1200x180; preserve its native scale and tile it seamlessly.
        for (int layer = 0; layer < 3; layer++) {
            int x = -(int) mountainOffsets[layer];
            drawImage(g, "fundo" + (layer * 2) + ".bmp", x, 450, WIDTH, 180);
            drawImage(g, "fundo" + (layer * 2 + 1) + ".bmp", x + WIDTH, 450, WIDTH, 180);
            if (x > 0) drawImage(g, "fundo" + (layer * 2 + 1) + ".bmp", x - WIDTH, 450, WIDTH, 180);
        }
        for (int i = 0; i < cloudX.length; i++) drawImage(g, "nuvem.bmp", (int) cloudX[i], (int) cloudY[i], 46, 13);

        g.setColor(new Color(105, 72, 38));
        g.fillRect(0, ground, WIDTH, HEIGHT - ground);
        g.setColor(new Color(83, 62, 35));
        g.drawLine(9, ground, WIDTH - 9, ground);

        for (Obstacle obstacle : trainer.obstacles) {
            if (obstacle.x > WIDTH || obstacle.x + obstacle.width < -60) continue;
            int obstacleX = (int) Math.round(obstacle.x);
            int obstacleY = ground - (int) obstacle.y - (int) obstacle.height;
            if (obstacle.type == 2) {
                drawCactusRow(g, obstacleX, obstacleY);
            } else {
                String sprite = obstacle.type == 1
                        ? "obs" + (birdFrame == 0 ? 5 : 6) + ".bmp"
                        : "obs" + obstacle.sprite + ".bmp";
                drawImage(g, sprite, obstacleX, obstacleY, (int) obstacle.width,
                        (int) obstacle.height);
            }
        }

        int walkFrame = (animationTick / 3) % 2;
        for (Dino d : trainer.population) {
            if (!d.alive || d.x < -60 || d.x > WIDTH + 60) continue;
            int frame = d.planeTicks > 0 ? 8 + walkFrame
                    : d.ducking ? 2 + walkFrame
                    : d.y > 1 ? 4 + walkFrame
                    : walkFrame;
            int spriteW = frame == 2 || frame == 3 ? 56 : frame >= 8 ? 39 : 40;
            int spriteH = frame == 2 ? 25 : frame == 3 ? 26 : frame >= 8 ? 37 : 43;
            int dx = (int) Math.round(d.x);
            int dy = ground - (int) d.y - spriteH;
            if (d.alive && d.planeTicks > 0) drawImage(g, "aviao" + walkFrame + ".bmp", dx - 15, dy, 70, 37);
            BufferedImage colored = coloredDino(frame, d.colorIndex);
            if (colored != null) g.drawImage(colored, dx, dy, null);
            if (d == trainer.champion) { g.setColor(BLUE); g.drawRect(dx - 2, dy - 2, spriteW + 3, spriteH + 3); }
        }
        g.setClip(previousClip);
        g.setColor(new Color(83, 62, 35));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(8, 466, WIDTH - 17, HEIGHT - 467);
    }

    private void drawImage(Graphics2D g, String name, int x, int y, int w, int h) {
        BufferedImage image = images.get(name);
        if (image != null) g.drawImage(image, x, y, w, h, null);
    }

    private void drawCactusRow(Graphics2D g, int x, int y) {
        for (int i = 0; i < 5; i++) drawImage(g, "obs4.bmp", x + i * 40, y, 54, 47);
    }
}
