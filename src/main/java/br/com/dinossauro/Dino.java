package br.com.dinossauro;

import java.util.concurrent.ThreadLocalRandom;

final class Dino {
    final NeuralNetwork brain;
    final double x;
    final int colorIndex = ThreadLocalRandom.current().nextInt(8);
    double fitness;
    double y;
    double vy;
    boolean alive = true;
    boolean ducking;
    int duckTicks;
    int planeTicks;
    int planeCooldownTicks;
    int obstaclesCleared;
    int clearedObstacleSerial = -1;
    int lastAction = -1;
    final double[] lastInputs = new double[Trainer.INPUTS];
    final double[] displayedDecisionInputs = new double[Trainer.INPUTS];
    int displayedDecisionAction = Trainer.RUN;
    boolean hasDisplayedDecision;

    Dino(NeuralNetwork brain, double x) {
        this.brain = brain;
        this.x = x;
    }
}
