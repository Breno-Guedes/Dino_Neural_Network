package br.com.dinossauro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Ambiente do jogo e população de agentes treinada por neuroevolução. */
final class Trainer {
    static final int POPULATION = 160;
    static final int INPUTS = 11;
    static final int ACTIONS = 4;
    static final int RUN = 0;
    static final int JUMP = 1;
    static final int DUCK = 2;
    static final int PLANE = 3;
    static final double SCREEN_WIDTH = 1200;
    static final double FIRST_DINO_X = 40;
    static final double CROWD_END_X = 180;
    private static final double FIRST_OBSTACLE_X = SCREEN_WIDTH * .80;
    private static final long POSITION_LAYOUT_SEED = 0xD1A05EEDL;

    private static final double ACTION_DISTANCE_MAX = 130;
    private static final int PLANE_DURATION = 70;
    private static final int ELITE_COUNT = 4;
    private static final int PARENT_POOL_SIZE = POPULATION / 5;
    private static final int TOURNAMENT_SIZE = 3;
    private static final int RANDOM_IMMIGRANTS = POPULATION / 20;
    private static final double MUTATION_RATE = .12;
    private static final double MUTATION_DEVIATION = .12;

    final List<Dino> population = new ArrayList<>(POPULATION);
    final List<Obstacle> obstacles = new ArrayList<>();
    final List<Double> bestHistory = new ArrayList<>();
    final List<Double> meanHistory = new ArrayList<>();
    final List<Double> generationBestHistory = new ArrayList<>();
    final List<Double> generationMeanHistory = new ArrayList<>();
    int generation = 1;
    int chartCounter;
    double distance, speed = 5, obstacleX = 900, obstacleHeight, obstacleWidth, obstacleY, record;
    double obstacleCollisionHeight;
    private double farthestDinoX;
    int obstacleType;
    int obstacleSprite;
    Dino champion;

    private final ObstacleGenerator obstacleGenerator = new ObstacleGenerator();
    private long evolvedWeights;
    private double bestEverFitness = Double.NEGATIVE_INFINITY;
    private double bestGenerationFitness = Double.NEGATIVE_INFINITY;
    private int stagnantGenerations;
    private NeuralNetwork bestEverBrain;
    private double[] championInputs = new double[INPUTS];
    private final int[] actionCounts = new int[ACTIONS];


    Trainer() { resetTraining(); }

    void resetTraining() {
        bestHistory.clear();
        meanHistory.clear();
        generationBestHistory.clear();
        generationMeanHistory.clear();
        generation = 1;
        chartCounter = 0;
        evolvedWeights = 0;
        bestEverFitness = Double.NEGATIVE_INFINITY;
        bestGenerationFitness = Double.NEGATIVE_INFINITY;
        stagnantGenerations = 0;
        bestEverBrain = null;
        record = 0;
        startEpisode(false);
    }

    private void startEpisode(boolean evolve) {
        generationBestHistory.clear();
        generationMeanHistory.clear();
        chartCounter = 0;
        List<NeuralNetwork> genomes;
        if (evolve) {
            genomes = nextGeneration();
            generation++;
        } else {
            genomes = new ArrayList<>(POPULATION);
            for (int i = 0; i < POPULATION; i++)
                genomes.add(new NeuralNetwork(INPUTS, 12, ACTIONS));
        }

        distance = 0;
        speed = 5;
        obstacleGenerator.reset();
        List<Double> startPositions = buildStartingPositions();
        farthestDinoX = startPositions.stream().mapToDouble(Double::doubleValue).max()
                .orElse(FIRST_DINO_X);

        population.clear();
        for (int i = 0; i < genomes.size(); i++) {
            Dino dino = new Dino(genomes.get(i), startPositions.get(i));
            for (Obstacle obstacle : obstacles)
                if (obstacle.x + obstacle.width < dino.x)
                    dino.clearedObstacleSerial = obstacle.serial;
            population.add(dino);
        }
        syncCurrentObstacle();
        Arrays.fill(actionCounts, 0);
        champion = population.get(0);
        for (Dino dino : population) {
            double[] inputs = networkInputs(dino);
            System.arraycopy(inputs, 0, dino.lastInputs, 0, INPUTS);
            dino.brain.forward(inputs);
        }
        boolean actionInProgress = champion.planeTicks > 0 || champion.duckTicks > 0 || champion.y > 1;
        championInputs = actionInProgress && champion.hasDisplayedDecision
                ? champion.displayedDecisionInputs.clone() : champion.lastInputs.clone();
        // Recalcular a rede com a mesma entrada que produziu a ação destacada no estado atual.
        champion.brain.forward(championInputs);
    }

    private List<Double> buildStartingPositions() {
        List<Double> positions = new ArrayList<>(POPULATION);
        Set<Long> uniquePositions = new HashSet<>(POPULATION * 2);
        obstacles.clear();
        double nextObstacleX = FIRST_OBSTACLE_X;
        double maxStartX = CROWD_END_X;
        while (obstacles.isEmpty()
                || obstacles.get(obstacles.size() - 1).x < SCREEN_WIDTH + ACTION_DISTANCE_MAX + 40) {
            Obstacle generated = obstacleGenerator.generate(nextObstacleX);
            obstacles.add(generated);
            nextObstacleX += generated.width + obstacleGenerator.nextGap();
        }

        // A amostra fixa em uma faixa curta mantém a multidão muito compacta e evita ilhas.
        Random layoutRandom = new Random(POSITION_LAYOUT_SEED);
        while (positions.size() < POPULATION) {
            double candidateX = FIRST_DINO_X
                    + layoutRandom.nextDouble() * (maxStartX - FIRST_DINO_X);
            if (candidateX < FIRST_DINO_X || candidateX > maxStartX) continue;
            boolean overlapsObstacle = false;
            for (Obstacle obstacle : obstacles) {
                if (candidateX < obstacle.x + obstacle.width && candidateX + 40 > obstacle.x) {
                    overlapsObstacle = true;
                    break;
                }
            }
            long positionBits = Double.doubleToLongBits(candidateX);
            if (!overlapsObstacle && uniquePositions.add(positionBits)) positions.add(candidateX);
        }
        return positions;
    }

    private List<NeuralNetwork> nextGeneration() {
        List<Dino> ranked = new ArrayList<>(population);
        ranked.sort(Comparator.comparingDouble((Dino dino) -> dino.fitness).reversed());
        Dino generationChampion = ranked.get(0);
        if (generationChampion.fitness > bestGenerationFitness + 1e-6) {
            bestGenerationFitness = generationChampion.fitness;
            stagnantGenerations = 0;
        } else {
            stagnantGenerations++;
        }
        if (generationChampion.fitness > bestEverFitness) {
            bestEverFitness = generationChampion.fitness;
            bestEverBrain = generationChampion.brain.copy();
        }

        List<NeuralNetwork> next = new ArrayList<>(POPULATION);
        if (bestEverBrain != null) next.add(bestEverBrain.copy());
        for (Dino candidate : ranked) {
            if (next.size() >= ELITE_COUNT) break;
            if (bestEverBrain != null
                    && candidate.brain.parameterChecksum() == bestEverBrain.parameterChecksum()) continue;
            next.add(candidate.brain.copy());
        }

        int parentPoolSize = Math.min(PARENT_POOL_SIZE, ranked.size());
        int immigrants = stagnantGenerations >= 8 ? POPULATION / 10 : RANDOM_IMMIGRANTS;
        double mutationRate = stagnantGenerations >= 8 ? .20 : MUTATION_RATE;
        double mutationDeviation = stagnantGenerations >= 8 ? .20 : MUTATION_DEVIATION;
        int offspringLimit = POPULATION - immigrants;
        while (next.size() < offspringLimit) {
            Dino first = tournamentSelect(ranked, parentPoolSize);
            Dino second = tournamentSelect(ranked, parentPoolSize);
            NeuralNetwork child = first.brain.crossover(second.brain);
            evolvedWeights += child.mutate(mutationRate, mutationDeviation);
            next.add(child);
        }
        while (next.size() < POPULATION) next.add(new NeuralNetwork(INPUTS, 12, ACTIONS));
        return next;
    }

    private Dino tournamentSelect(List<Dino> ranked, int parentPoolSize) {
        Dino best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Dino candidate = ranked.get(ThreadLocalRandom.current().nextInt(parentPoolSize));
            if (best == null || candidate.fitness > best.fitness) best = candidate;
        }
        return best;
    }

    void tick() {
        distance += speed;
        for (Obstacle obstacle : obstacles) obstacle.x -= speed;
        while (!obstacles.isEmpty() && obstacles.get(0).x + obstacles.get(0).width < -60) {
            obstacles.remove(0);
            appendNextObstacleAtCourseEnd();
        }
        ensureObstacleCourseAhead();
        syncCurrentObstacle();
        Arrays.fill(actionCounts, 0);

        for (Dino dino : population) {
            if (!dino.alive) continue;
            if (dino.planeCooldownTicks > 0) dino.planeCooldownTicks--;
            if (dino.duckTicks > 0) dino.duckTicks--;

            Obstacle nextObstacle = nextObstacle(dino);
            double[] state = networkInputs(dino, nextObstacle);
            System.arraycopy(state, 0, dino.lastInputs, 0, INPUTS);
            double distanceToObstacle = nextObstacle == null ? Double.POSITIVE_INFINITY
                    : nextObstacle.x - dino.x;
            double[] outputs = dino.brain.forward(state);
            int action = selectAction(dino, nextObstacle, distanceToObstacle, outputs);
            boolean actionAlreadyInProgress = dino.planeTicks > 0 || dino.duckTicks > 0 || dino.y > 1;
            
            // Durante uma ação temporizada, mantenha no painel o estado que a rede viu quando a iniciou.
            // RUN emitido enquanto o avião ainda voa não deve sobrescrever a decisão PLANE que o acionou.
            if (!actionAlreadyInProgress || action != RUN) {
                System.arraycopy(state, 0, dino.displayedDecisionInputs, 0, INPUTS);
                dino.displayedDecisionAction = action;
                dino.hasDisplayedDecision = true;
            }
            dino.lastAction = action;
            actionCounts[action]++;

            boolean planeWasActive = dino.planeTicks > 0;
            double reward = speed * .05;
            if (action == JUMP) {
                dino.vy = 11;
                reward -= .25;
            } else if (action == DUCK) {
                dino.duckTicks = 24;
                reward -= .15;
            } else if (action == PLANE) {
                dino.planeTicks = PLANE_DURATION;
                dino.planeCooldownTicks = 150;
                dino.y = 115;
                dino.vy = 0;
                reward -= .4;
                // Atualiza o estado exibido no painel para indicar imediatamente que o avião está ativo (1.0)
                dino.displayedDecisionInputs[8] = 1.0;
            }

            dino.ducking = dino.duckTicks > 0;
            if (dino.planeTicks > 0) {
                dino.planeTicks--;
                dino.y = 115;
                dino.vy = 0;
            } else {
                dino.vy -= .55;
                dino.y = Math.max(0, dino.y + dino.vy);
                if (dino.y == 0) dino.vy = 0;
            }

            for (Obstacle obstacle : obstacles) {
                if (obstacle.serial > dino.clearedObstacleSerial && obstacle.x + obstacle.width < dino.x) {
                    dino.clearedObstacleSerial = obstacle.serial;
                    dino.obstaclesCleared++;
                    reward += 25;
                    break;
                }
            }

            boolean collision = dino.planeTicks == 0 && collidesWithAnyObstacle(dino, planeWasActive);
            if (collision) {
                dino.alive = false;
                reward -= 100;
            }
            dino.fitness += reward;
        }

        updateChampion();
        record = Math.max(record, distance);
        if (++chartCounter % 5 == 0) {
            bestHistory.add(bestFitness());
            meanHistory.add(meanFitness());
            generationBestHistory.add(bestFitness());
            generationMeanHistory.add(meanFitness());
            if (bestHistory.size() > 200) {
                bestHistory.remove(0);
                meanHistory.remove(0);
            }
            if (generationBestHistory.size() > 200) {
                generationBestHistory.remove(0);
                generationMeanHistory.remove(0);
            }
        }
        speed = Math.min(12, 5 + distance / 16000);
        if (aliveCount() == 0) startEpisode(true);
    }

    private void ensureObstacleCourseAhead() {
        while (obstacles.isEmpty()
                || obstacles.get(obstacles.size() - 1).x <= farthestDinoX + ACTION_DISTANCE_MAX)
            appendNextObstacleAtCourseEnd();
    }

    private void appendNextObstacleAtCourseEnd() {
        double nextX;
        if (obstacles.isEmpty()) {
            nextX = Math.max(SCREEN_WIDTH + 40, farthestDinoX + SCREEN_WIDTH);
        } else {
            Obstacle last = obstacles.get(obstacles.size() - 1);
            nextX = Math.max(last.x + last.width + obstacleGenerator.nextGap(),
                    farthestDinoX + SCREEN_WIDTH);
        }
        obstacles.add(obstacleGenerator.generate(nextX));
    }

    private int selectAction(Dino dino, Obstacle obstacle, double distanceToObstacle, double[] outputs) {
        int[] available = availableActions(dino, obstacle, distanceToObstacle);
        int selected = available[0];
        for (int i = 1; i < available.length; i++)
            if (outputs[available[i]] > outputs[selected]) selected = available[i];
        return selected;
    }

    private int[] availableActions(Dino dino, Obstacle obstacle, double distanceToObstacle) {
        int[] actions = new int[ACTIONS];
        int count = 0;
        actions[count++] = RUN;
        if (obstacle == null || distanceToObstacle < 0 || distanceToObstacle > ACTION_DISTANCE_MAX)
            return Arrays.copyOf(actions, count);

        if ((obstacle.type == 0 || obstacle.type == 1 && obstacle.y <= 18)
                && dino.y <= .01 && dino.planeTicks == 0)
            actions[count++] = JUMP;
        if (obstacle.type == 1 && obstacle.y > 18 && obstacle.y <= 48 && dino.planeTicks == 0)
            actions[count++] = DUCK;
        if (canUsePlane(dino, distanceToObstacle, obstacle)) actions[count++] = PLANE;
        return Arrays.copyOf(actions, count);
    }

    boolean canUsePlane(Dino dino, double distanceToObstacle) {
        return canUsePlane(dino, distanceToObstacle, null);
    }

    private boolean canUsePlane(Dino dino, double distanceToObstacle, Obstacle obstacle) {
        int type = obstacle == null ? obstacleType : obstacle.type;
        return (type == 1 || type == 2) && distanceToObstacle >= 60
                && distanceToObstacle <= ACTION_DISTANCE_MAX
                && dino.planeTicks == 0 && dino.planeCooldownTicks == 0;
    }

    boolean collidesWithObstacle(Dino dino) {
        return collidesWithObstacle(dino, obstacleType, obstacleX, obstacleWidth, obstacleY,
                effectiveCollisionHeight(obstacleType, obstacleY, obstacleHeight, obstacleCollisionHeight));
    }

    private boolean collidesWithAnyObstacle(Dino dino, boolean airplaneState) {
        for (Obstacle obstacle : obstacles) {
            if (obstacle.x + obstacle.width < dino.x || obstacle.x > dino.x + 48) continue;
            if (collidesWithObstacle(dino, obstacle.type, obstacle.x, obstacle.width, obstacle.y,
                    effectiveCollisionHeight(obstacle.type, obstacle.y, obstacle.height, obstacle.collisionHeight),
                    airplaneState))
                return true;
        }
        return false;
    }

    private boolean collidesWithObstacle(Dino dino, int type, double x, double width, double y,
                                         double collisionHeight) {
        return collidesWithObstacle(dino, type, x, width, y, collisionHeight, dino.planeTicks > 0);
    }

    private boolean collidesWithObstacle(Dino dino, int type, double x, double width, double y,
                                         double collisionHeight, boolean airplaneState) {
        double dinoLeft;
        double dinoRight;
        double dinoBottom;
        double dinoTop;
        if (airplaneState) {
            dinoLeft = dino.x - 10;
            dinoRight = dino.x + 55;
            dinoBottom = dino.y + 8;
            dinoTop = dino.y + 35;
        } else if (dino.ducking) {
            dinoLeft = dino.x + 4;
            dinoRight = dino.x + 52;
            dinoBottom = dino.y + 3;
            dinoTop = dino.y + 20;
        } else {
            dinoLeft = dino.x + 3;
            dinoRight = dino.x + 37;
            dinoBottom = dino.y + 5;
            dinoTop = dino.y + 38;
        }
        double obstacleBottom = type == 1 ? y : 0;
        double obstacleTop = obstacleBottom + collisionHeight;
        boolean horizontalHit = dinoLeft < x + width && dinoRight > x;
        boolean verticalHit = dinoBottom < obstacleTop && dinoTop > obstacleBottom;
        return horizontalHit && verticalHit;
    }

    private double effectiveCollisionHeight(int type, double y, double height, double configuredHeight) {
        if (type == 2 || type == 1 && y > 18 && y <= 48)
            return configuredHeight > 0 ? configuredHeight : height;
        return height;
    }

    private void updateChampion() {
        Dino generationBest = population.stream().max(Comparator.comparingDouble(dino -> dino.fitness))
                .orElse(population.get(0));
        if (generationBest.fitness > bestEverFitness) {
            bestEverFitness = generationBest.fitness;
            bestEverBrain = generationBest.brain.copy();
        }
        // A rede exibida precisa acompanhar um agente que ainda está jogando.
        // Um campeão eliminado conserva a última ação (muitas vezes RUN) e deixava o painel congelado.
        champion = population.stream().filter(dino -> dino.alive)
                .max(Comparator.comparingDouble(dino -> dino.fitness))
                .orElse(generationBest);
        boolean actionInProgress = champion.planeTicks > 0 || champion.duckTicks > 0 || champion.y > 1;
        championInputs = actionInProgress && champion.hasDisplayedDecision
                ? champion.displayedDecisionInputs.clone() : champion.lastInputs.clone();
        // Recalcular a rede com a mesma entrada que produziu a ação destacada no estado atual.
        champion.brain.forward(championInputs);
    }

    private Obstacle nextObstacle(Dino dino) {
        for (Obstacle obstacle : obstacles)
            if (obstacle.x + obstacle.width >= dino.x) return obstacle;
        return obstacles.isEmpty() ? null : obstacles.get(obstacles.size() - 1);
    }

    private double[] networkInputs(Dino dino) {
        return networkInputs(dino, nextObstacle(dino));
    }

    private double[] networkInputs(Dino dino, Obstacle obstacle) {
        if (obstacle == null) return new double[INPUTS];
        double type = obstacle.type == 0 ? -1 : obstacle.type == 1 ? 0 : 1;
        return new double[]{
                Math.max(-1, Math.min(1, (obstacle.x - dino.x) / 600)),
                obstacle.width / 220,
                obstacle.height / 120,
                obstacle.y / 120,
                speed / 12,
                Math.max(0, Math.min(1, dino.y / 150)),
                Math.max(-1, Math.min(1, dino.vy / 12)),
                type,
                dino.planeTicks > 0 ? 1.0 : 0.0, // Valor binário: 1.0 quando o avião está ativo, 0.0 quando inativo
                dino.planeCooldownTicks / 150.0,
                dino.duckTicks / 24.0
        };
    }

    
    private void syncCurrentObstacle() {
        if (obstacles.isEmpty()) return;
        Obstacle current = obstacles.get(obstacles.size() - 1);
        for (Obstacle candidate : obstacles) {
            if (candidate.x + candidate.width >= 180) {
                current = candidate;
                break;
            }
        }
        obstacleX = current.x;
        obstacleType = current.type;
        obstacleSprite = current.sprite;
        obstacleWidth = current.width;
        obstacleHeight = current.height;
        obstacleY = current.y;
        obstacleCollisionHeight = current.collisionHeight;
    }

    int aliveCount() { return (int) population.stream().filter(dino -> dino.alive).count(); }
    double bestFitness() { return population.stream().mapToDouble(dino -> dino.fitness).max().orElse(0); }
    double meanFitness() { return population.stream().mapToDouble(dino -> dino.fitness).average().orElse(0); }
    double[] championInputs() { return championInputs.clone(); }
    int championAction() {
        if (champion == null) return RUN;
        // A ação escolhida pode voltar a RUN enquanto uma ação temporizada ainda está em curso.
        // A interface deve refletir o comportamento visível do campeão, não apenas o último comando.
        if (champion.planeTicks > 0) return PLANE;
        if (champion.duckTicks > 0 || champion.ducking) return DUCK;
        if (champion.y > 1) return JUMP;
        return champion.lastAction < 0 ? RUN : champion.lastAction;
    }
    int[] actionCounts() { return actionCounts.clone(); }
    long evolutionUpdates() { return evolvedWeights; }
    double parameterChecksum() { return champion == null ? 0 : champion.brain.parameterChecksum(); }
}
