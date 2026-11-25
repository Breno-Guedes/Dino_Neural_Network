package br.com.dinossauro;

/**
 * Roteiro determinístico de obstáculos. Cada episódio reinicia no mesmo ponto,
 * de modo que todas as gerações recebem os mesmos tipos, alturas e intervalos.
 *
 * Tipos usados pelo Trainer: 0=cacto, 1=pássaro, 2=fileira larga de cactos.
 */
final class ObstacleGenerator {
    private static final int CACTUS = 0;
    private static final int BIRD = 1;
    private static final int WIDE_CACTUS = 2;
    private static final int WIDE_CACTUS_WIDTH = 210;
    private static final int WIDE_CACTUS_BASE_GAP = 2_100;
    static final int MIN_JUMP_OBSTACLE_SEPARATION = 540;
    private static final double PREVIOUS_SPACING_SCALE = 0.8;
    private static final double ADDITIONAL_FREQUENCY_SCALE = 1.1;
    private static final double MAXIMUM_GAP_SCALE = 0.8;
    private static final int MAXIMUM_GAP = (int) Math.round(
            (Math.round((WIDE_CACTUS_WIDTH + WIDE_CACTUS_BASE_GAP) * PREVIOUS_SPACING_SCALE)
                    - WIDE_CACTUS_WIDTH) * MAXIMUM_GAP_SCALE);
    private static final int[] CACTUS_WIDTHS = {32, 23, 15, 49, 73};
    private static final int[] CACTUS_HEIGHTS = {33, 46, 33, 33, 47};

    /*
     * Cada item registra o obstáculo e a distância livre após seu fim até o
     * próximo. A distância entre os pontos de surgimento foi reduzida para
     * 80% e recebe mais 10% de aumento de frequência; o vão livre máximo
     * também é limitado a 80% do máximo anterior. A lista se repete
     * indefinidamente e não usa aleatoriedade.
     */
    private static final Pattern[] COURSE = {
            new Pattern(CACTUS, 0, 0, 500),
            new Pattern(CACTUS, 3, 0, 470),
            new Pattern(BIRD, 5, 8, 520),       // baixo: salto
            new Pattern(CACTUS, 1, 0, 480),
            new Pattern(BIRD, 6, 30, 540),      // médio: abaixar
            new Pattern(CACTUS, 4, 0, 500),
            new Pattern(WIDE_CACTUS, 7, 0, 2_100), // avião ou salto antecipado
            new Pattern(BIRD, 5, 94, 520),      // alto: passagem livre
            new Pattern(CACTUS, 2, 0, 480),
            new Pattern(BIRD, 6, 8, 550),
            new Pattern(CACTUS, 0, 0, 500),
            new Pattern(BIRD, 5, 30, 520),
            new Pattern(CACTUS, 3, 0, 480),
            new Pattern(WIDE_CACTUS, 7, 0, 2_100),
            new Pattern(CACTUS, 4, 0, 520),
            new Pattern(BIRD, 6, 94, 500)
    };

    private int obstacleSerial;
    private int patternIndex;

    void reset() {
        obstacleSerial = 0;
        patternIndex = 0;
    }

    Obstacle generate(double x) {
        Pattern pattern = COURSE[patternIndex];
        patternIndex = (patternIndex + 1) % COURSE.length;
        Obstacle obstacle = new Obstacle();
        obstacleSerial++;
        obstacle.type = pattern.type;
        obstacle.sprite = pattern.sprite;
        obstacle.y = pattern.y;
        obstacle.x = x;
        obstacle.serial = obstacleSerial;
        switch (pattern.type) {
            case BIRD -> {
                obstacle.width = 42;
                obstacle.height = 36;
                obstacle.collisionHeight = obstacle.height;
            }
            case WIDE_CACTUS -> {
                obstacle.width = WIDE_CACTUS_WIDTH;
                obstacle.height = 47;
                obstacle.collisionHeight = obstacle.height;
            }
            default -> {
                obstacle.width = CACTUS_WIDTHS[pattern.sprite];
                obstacle.height = CACTUS_HEIGHTS[pattern.sprite];
                obstacle.collisionHeight = obstacle.height;
            }
        }
        return obstacle;
    }

    double nextGap() {
        Pattern previous = COURSE[(patternIndex + COURSE.length - 1) % COURSE.length];
        Pattern next = COURSE[patternIndex];
        int width = obstacleWidth(previous);
        int gap = (int) Math.round((width + previous.gapAfter)
                * PREVIOUS_SPACING_SCALE / ADDITIONAL_FREQUENCY_SCALE) - width;
        if (requiresJump(previous) && requiresJump(next))
            gap = Math.max(gap, MIN_JUMP_OBSTACLE_SEPARATION - width);
        return Math.min(gap, MAXIMUM_GAP);
    }

    private static boolean requiresJump(Pattern pattern) {
        return pattern.type == CACTUS || pattern.type == BIRD && pattern.y <= 18;
    }

    private static int obstacleWidth(Pattern pattern) {
        return switch (pattern.type) {
            case BIRD -> 42;
            case WIDE_CACTUS -> WIDE_CACTUS_WIDTH;
            default -> CACTUS_WIDTHS[pattern.sprite];
        };
    }

    private record Pattern(int type, int sprite, int y, int gapAfter) { }
}
