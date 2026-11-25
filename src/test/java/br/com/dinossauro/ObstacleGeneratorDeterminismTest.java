package br.com.dinossauro;

import java.util.HashSet;
import java.util.Set;

/** Teste executável sem JUnit: valida repetibilidade do roteiro e intervalos. */
public final class ObstacleGeneratorDeterminismTest {
    private static final int SAMPLE_COUNT = 48;
    private static final int[] EXPECTED_GAPS = {
            508, 491, 498, 343, 381, 344, 1_310, 367,
            525, 498, 355, 367, 336, 1_310, 358, 352
    };

    public static void main(String[] args) {
        verifyInitialPopulationIsRandomAndQueued();
        ObstacleGenerator first = new ObstacleGenerator();
        ObstacleGenerator second = new ObstacleGenerator();
        first.reset();
        second.reset();
        double x1 = 1_240;
        double x2 = 1_240;
        double previousWideCactus = Double.NEGATIVE_INFINITY;
        Obstacle previousObstacle = null;
        double previousGap = 0;
        boolean sawCactus = false, sawBird = false, sawWideCactus = false;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            Obstacle a = first.generate(x1);
            Obstacle b = second.generate(x2);
            require(a.type == b.type && a.sprite == b.sprite && a.y == b.y
                    && a.width == b.width && a.height == b.height && a.x == b.x,
                    "O roteiro divergiu após reset no índice " + i);
            sawCactus |= a.type == 0;
            sawBird |= a.type == 1;
            sawWideCactus |= a.type == 2;
            if (a.type == 2) {
                if (previousWideCactus != Double.NEGATIVE_INFINITY)
                    require(a.x - previousWideCactus >= 1_800,
                            "Fileiras largas violam a recarga máxima do avião");
                previousWideCactus = a.x;
            }
            double gapA = first.nextGap();
            double gapB = second.nextGap();
            require(gapA == gapB && gapA == EXPECTED_GAPS[i % EXPECTED_GAPS.length],
                    "Espaçamento inválido no índice " + i + ": esperado "
                            + EXPECTED_GAPS[i % EXPECTED_GAPS.length] + ", recebido " + gapA);
            if (previousObstacle != null && requiresJump(previousObstacle) && requiresJump(a))
                require(previousObstacle.width + previousGap >= ObstacleGenerator.MIN_JUMP_OBSTACLE_SEPARATION,
                        "Obstáculos consecutivos de salto não deixam pousar antes do próximo");
            previousObstacle = a;
            previousGap = gapA;
            x1 += a.width + gapA;
            x2 += b.width + gapB;
        }
        require(sawCactus && sawBird && sawWideCactus, "O roteiro deve conter todos os tipos");
        System.out.println("OK: população inteira visível em posições irregulares reproduzíveis; "
                + SAMPLE_COUNT + " obstáculos repetíveis com tipos e espaçamentos verificados.");
    }

    private static void verifyInitialPopulationIsRandomAndQueued() {
        Trainer trainer = new Trainer();
        Trainer repeatedTrainer = new Trainer();
        double[] state = {0.3, 0.2, 0.4, 0.1, 0.5, 0.0, 0.0, -1, 0, 0, 0};
        Set<String> outputSignatures = new HashSet<>();
        Set<Long> positionSignatures = new HashSet<>();
        boolean somePreferRun = false;
        boolean somePreferJump = false;
        boolean sawOutOfOrderPosition = false;
        double previousX = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < trainer.population.size(); index++) {
            Dino dino = trainer.population.get(index);
            if (dino.x < previousX) sawOutOfOrderPosition = true;
            require(dino.x >= Trainer.FIRST_DINO_X && dino.x + 40 <= Trainer.SCREEN_WIDTH,
                    "Cada agente deve aparecer dentro do campo visível");
            require(positionSignatures.add(Double.doubleToLongBits(dino.x)),
                    "Cada agente deve ter uma posição X própria");
            require(dino.y == 0, "Todos os indivíduos devem começar na mesma altura do chão");
            require(dino.x == repeatedTrainer.population.get(index).x,
                    "As posições iniciais precisam se repetir entre execuções");
            for (Obstacle obstacle : trainer.obstacles)
                require(!(dino.x < obstacle.x + obstacle.width && dino.x + 40 > obstacle.x),
                        "Um indivíduo não pode iniciar sobreposto a um obstáculo");
            double[] output = dino.brain.forward(state);
            outputSignatures.add(java.util.Arrays.toString(output));
            somePreferRun |= output[Trainer.RUN] > output[Trainer.JUMP];
            somePreferJump |= output[Trainer.JUMP] > output[Trainer.RUN];
            previousX = dino.x;
        }
        double minimumX = trainer.population.stream().mapToDouble(dino -> dino.x).min().orElseThrow();
        double farthestX = trainer.population.stream().mapToDouble(dino -> dino.x).max().orElseThrow();
        double[] sortedPositions = trainer.population.stream().mapToDouble(dino -> dino.x).sorted().toArray();
        double largestPositionGap = 0;
        for (int i = 1; i < sortedPositions.length; i++)
            largestPositionGap = Math.max(largestPositionGap, sortedPositions[i] - sortedPositions[i - 1]);
        long visiblePopulation = trainer.population.stream()
                .filter(dino -> dino.x >= 0 && dino.x + 40 <= Trainer.SCREEN_WIDTH).count();
            require(positionSignatures.size() == Trainer.POPULATION
                        && visiblePopulation == Trainer.POPULATION
                        && farthestX - minimumX > 80
                        && farthestX - minimumX <= Trainer.CROWD_END_X - Trainer.FIRST_DINO_X
                        && sawOutOfOrderPosition && largestPositionGap < 40,
                "Os 1.000 agentes devem formar um bloco compacto, irregular, contínuo e sobreposto");
        Obstacle firstObstacle = trainer.obstacles.get(0);
        require(firstObstacle.x == Trainer.SCREEN_WIDTH * .80 && farthestX + 40 < firstObstacle.x,
                "O primeiro obstáculo deve ficar depois do bloco inteiro, sem dividi-lo");
        require(outputSignatures.size() > Trainer.POPULATION * .99,
                "As redes iniciais precisam ter pesos aleatórios independentes");
        require(somePreferRun && somePreferJump,
                "A população aleatória deve conter preferências iniciais distintas");
        require(trainer.population.size() == Trainer.POPULATION, "Tamanho da população inicial inválido");
        require(trainer.obstacles.get(trainer.obstacles.size() - 1).x > farthestX,
                "O roteiro deve continuar além do indivíduo mais à frente");

        boolean sawSpecialAction = false;
        for (int tick = 0; tick < 200 && !sawSpecialAction; tick++) {
            trainer.tick();
            int[] actions = trainer.actionCounts();
            sawSpecialAction = actions[Trainer.JUMP] + actions[Trainer.DUCK] + actions[Trainer.PLANE] > 0;
        }
        require(trainer.generation == 1 && sawSpecialAction,
                "Pesos aleatórios devem permitir ações especiais ao se aproximar dos obstáculos na primeira geração");
        for (int tick = 0; tick < 600; tick++) trainer.tick();
        require(trainer.obstacles.get(trainer.obstacles.size() - 1).x
                        > farthestX + 130,
                "O roteiro determinístico deve continuar adiante da fila durante a simulação");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean requiresJump(Obstacle obstacle) {
        return obstacle.type == 0 || obstacle.type == 1 && obstacle.y <= 18;
    }
}
