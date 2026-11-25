package br.com.dinossauro;

import java.util.concurrent.ThreadLocalRandom;

/** MLP de topologia fixa, usada como genoma da população evolutiva. */
final class NeuralNetwork {
    private final int[] sizes;
    private final double[][][] weights;
    private final double[][] values;

    NeuralNetwork(int... sizes) {
        if (sizes.length < 2) throw new IllegalArgumentException("A rede precisa de duas camadas");
        this.sizes = sizes.clone();
        weights = new double[sizes.length - 1][][];
        values = new double[sizes.length][];
        for (int layer = 0; layer < sizes.length; layer++) values[layer] = new double[sizes[layer]];
        for (int layer = 0; layer < weights.length; layer++) {
            weights[layer] = new double[sizes[layer + 1]][sizes[layer] + 1];
            double limit = Math.sqrt(6.0 / (sizes[layer] + sizes[layer + 1]));
            for (double[] neuron : weights[layer])
                for (int i = 0; i < neuron.length; i++)
                    neuron[i] = ThreadLocalRandom.current().nextDouble(-limit, limit);
        }
    }

    double[] forward(double... inputs) {
        if (inputs.length != sizes[0]) throw new IllegalArgumentException("Quantidade de entradas inválida");
        System.arraycopy(inputs, 0, values[0], 0, inputs.length);
        for (int layer = 0; layer < weights.length; layer++) {
            boolean outputLayer = layer == weights.length - 1;
            for (int n = 0; n < weights[layer].length; n++) {
                double sum = weights[layer][n][sizes[layer]];
                for (int i = 0; i < sizes[layer]; i++) sum += values[layer][i] * weights[layer][n][i];
                values[layer + 1][n] = outputLayer ? sum : Math.tanh(sum);
            }
        }
        return values[values.length - 1].clone();
    }

    double train(double[] inputs, double[] target, double learningRate) {
        double[] prediction = forward(inputs);
        if (target.length != prediction.length) throw new IllegalArgumentException("Alvo inválido");
        double[][] deltas = new double[weights.length][];
        int outputLayer = weights.length - 1;
        deltas[outputLayer] = new double[sizes[outputLayer + 1]];
        double loss = 0;
        for (int n = 0; n < prediction.length; n++) {
            deltas[outputLayer][n] = prediction[n] - target[n];
            loss += .5 * deltas[outputLayer][n] * deltas[outputLayer][n];
        }
        for (int layer = outputLayer - 1; layer >= 0; layer--) {
            deltas[layer] = new double[sizes[layer + 1]];
            for (int n = 0; n < sizes[layer + 1]; n++) {
                double propagated = 0;
                for (int next = 0; next < sizes[layer + 2]; next++)
                    propagated += weights[layer + 1][next][n] * deltas[layer + 1][next];
                deltas[layer][n] = (1 - values[layer + 1][n] * values[layer + 1][n]) * propagated;
            }
        }
        for (int layer = 0; layer < weights.length; layer++) {
            for (int n = 0; n < weights[layer].length; n++) {
                for (int i = 0; i < sizes[layer]; i++)
                    weights[layer][n][i] -= learningRate * deltas[layer][n] * values[layer][i];
                weights[layer][n][sizes[layer]] -= learningRate * deltas[layer][n];
            }
        }
        return loss / prediction.length;
    }

    double activation(int layer, int neuron) { return values[layer][neuron]; }
    int layerCount() { return sizes.length; }
    int layerSize(int layer) { return sizes[layer]; }
    double weight(int layer, int neuron, int input) { return weights[layer][neuron][input]; }

    NeuralNetwork copy() {
        NeuralNetwork clone = new NeuralNetwork(sizes);
        for (int l = 0; l < weights.length; l++)
            for (int n = 0; n < weights[l].length; n++)
                System.arraycopy(weights[l][n], 0, clone.weights[l][n], 0, weights[l][n].length);
        return clone;
    }

    NeuralNetwork crossover(NeuralNetwork other) {
        if (!java.util.Arrays.equals(sizes, other.sizes))
            throw new IllegalArgumentException("As redes precisam ter a mesma topologia");
        NeuralNetwork child = new NeuralNetwork(sizes);
        for (int layer = 0; layer < weights.length; layer++) {
            for (int neuron = 0; neuron < weights[layer].length; neuron++) {
                for (int gene = 0; gene < weights[layer][neuron].length; gene++) {
                    child.weights[layer][neuron][gene] = ThreadLocalRandom.current().nextBoolean()
                            ? weights[layer][neuron][gene] : other.weights[layer][neuron][gene];
                }
            }
        }
        return child;
    }

    void mutate(double rate) {
        mutate(rate, .08);
    }

    int mutate(double rate, double standardDeviation) {
        int changed = 0;
        for (double[][] layer : weights) for (double[] neuron : layer) for (int i = 0; i < neuron.length; i++) {
            if (ThreadLocalRandom.current().nextDouble() < rate) {
                neuron[i] = Math.max(-5, Math.min(5,
                        neuron[i] + ThreadLocalRandom.current().nextGaussian() * standardDeviation));
                changed++;
            }
        }
        return changed;
    }

    double parameterChecksum() {
        double checksum = 0;
        for (double[][] layer : weights) for (double[] neuron : layer)
            for (double value : neuron) checksum += value * value;
        return checksum;
    }
}
