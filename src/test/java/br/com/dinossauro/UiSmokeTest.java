package br.com.dinossauro;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class UiSmokeTest {
    public static void main(String[] args) throws Exception {
        try { SwingUtilities.invokeAndWait(() -> {
            Trainer trainer = new Trainer();
            for (int i = 0; i < 15; i++) trainer.tick();
            trainer.champion.planeTicks = 20;
            trainer.champion.y = 115;
            GamePanel panel = new GamePanel(trainer);
            panel.setSize(1200, 678);
            BufferedImage image = new BufferedImage(1200, 678, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            try { ImageIO.write(image, "png", new File("/tmp/dino-java-fixed.png")); }
            catch (Exception exception) { throw new RuntimeException(exception); }
            if (image.getRGB(20, 30) == 0) throw new AssertionError("Canvas não renderizado");
            int magentaPixels = 0, blueCurvePixels = 0, redCurvePixels = 0, coloredSkyPixels = 0;
            Set<Integer> dinosaurColors = new HashSet<>();
            for (int y = 0; y < 678; y++) for (int x = 0; x < 1200; x++) {
                int rgb = image.getRGB(x, y), r = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, b = rgb & 255;
                if (r > 225 && green > 75 && green < 220 && b > 175 && b - green > 30) magentaPixels++;
            }
            int[] palette = {new java.awt.Color(235,55,65).getRGB(), new java.awt.Color(250,125,35).getRGB(),
                    new java.awt.Color(235,185,30).getRGB(), new java.awt.Color(55,165,75).getRGB(),
                    new java.awt.Color(20,165,195).getRGB(), new java.awt.Color(55,105,225).getRGB(),
                    new java.awt.Color(140,75,205).getRGB(), new java.awt.Color(225,65,155).getRGB()};
            for (int y = 480; y < 630; y++) for (int x = 150; x < 400; x++) {
                int rgb = image.getRGB(x, y), r = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, b = rgb & 255;
                for (int color : palette) {
                    int cr = (color >>> 16) & 255, cg = (color >>> 8) & 255, cb = color & 255;
                    if (Math.abs(r-cr) < 5 && Math.abs(green-cg) < 5 && Math.abs(b-cb) < 5) dinosaurColors.add(color);
                }
            }
            for (int y = 24; y < 334; y++) for (int x = 18; x < 548; x++) {
                int rgb = image.getRGB(x, y), r = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, b = rgb & 255;
                if (b > 150 && r < 100 && green < 125) blueCurvePixels++;
                if (r > 150 && green < 100 && b < 100) redCurvePixels++;
            }
            for (int y = 450; y < 630; y++) for (int x = 0; x < 1200; x++) {
                int rgb = image.getRGB(x, y), r = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, b = rgb & 255;
                if (b > r + 8 && green > r + 5) coloredSkyPixels++;
            }
            if (magentaPixels != 0) throw new AssertionError("Halo magenta residual no avião: " + magentaPixels);
            if (dinosaurColors.size() < 3) throw new AssertionError("Os indivíduos não estão aparecendo em cores variadas");
            if (blueCurvePixels == 0 || redCurvePixels == 0) throw new AssertionError("As curvas azul/vermelha não foram desenhadas");
            if (coloredSkyPixels < 1000) throw new AssertionError("O cenário continua sem cor");

            for (Dino dino : trainer.population) dino.alive = false;
            BufferedImage afterDeaths = new BufferedImage(1200, 678, BufferedImage.TYPE_INT_RGB);
            Graphics2D deadGraphics = afterDeaths.createGraphics();
            panel.paint(deadGraphics);
            deadGraphics.dispose();
            int changedDinoPixels = 0;
            for (int y = 480; y < 630; y++) for (int x = 150; x < 400; x++) {
                if (image.getRGB(x, y) != afterDeaths.getRGB(x, y)) changedDinoPixels++;
            }
            if (changedDinoPixels < 50) throw new AssertionError("Indivíduos eliminados continuam visíveis");
            System.out.println("UI smoke test passed");
        }); } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
