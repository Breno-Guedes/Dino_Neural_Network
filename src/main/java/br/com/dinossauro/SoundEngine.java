package br.com.dinossauro;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicBoolean;

/** Música chiptune e efeitos curtos sintetizados em tempo real; não usa arquivos de áudio. */
final class SoundEngine {
    enum Effect { JUMP, DUCK, PLANE, CRASH }
    private static final float RATE = 22050f;
    private final AtomicBoolean musicOn = new AtomicBoolean(false);
    private Thread musicThread;

    synchronized void startMusic() {
        if (musicOn.get()) return;
        musicOn.set(true);
        musicThread = new Thread(this::musicLoop, "dino-synth-music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    synchronized void stopMusic() {
        musicOn.set(false);
        if (musicThread != null) musicThread.interrupt();
        musicThread = null;
    }

    void playEffect(Effect effect) {
        Thread thread = new Thread(() -> {
            int[] notes;
            int[] lengths;
            switch (effect) {
                case JUMP -> { notes = new int[]{660, 880, 1100}; lengths = new int[]{55, 55, 75}; }
                case DUCK -> { notes = new int[]{420, 330}; lengths = new int[]{45, 60}; }
                case PLANE -> { notes = new int[]{440, 660, 880, 990}; lengths = new int[]{50, 50, 60, 90}; }
                default -> { notes = new int[]{240, 180, 120}; lengths = new int[]{80, 100, 150}; }
            }
            try (SourceDataLine line = openLine()) {
                for (int i = 0; i < notes.length; i++) line.write(tone(notes[i], lengths[i], .16, true), 0,
                        toneLength(lengths[i]));
                line.drain();
            } catch (Exception ignored) { /* Áudio opcional: jogo continua sem dispositivo de som. */ }
        }, "dino-synth-effect");
        thread.setDaemon(true);
        thread.start();
    }

    private void musicLoop() {
        // Melodia original em estilo arcade/chiptune: notas triangulares, baixo simples e pausas.
        int[] melody = {659, 784, 988, 784, 659, 523, 587, 659, 784, 880, 784, 659, 523, 587, 659, 523};
        try (SourceDataLine line = openLine()) {
            while (musicOn.get()) {
                for (int i = 0; i < melody.length && musicOn.get(); i++) {
                    int duration = (i % 4 == 3) ? 150 : 125;
                    byte[] data = tone(melody[i], duration, .055, false);
                    line.write(data, 0, data.length);
                    byte[] bass = tone(melody[i] / 2, duration, .035, false);
                    line.write(bass, 0, bass.length);
                }
                line.drain();
            }
        } catch (Exception ignored) { /* Alguns sistemas não têm saída de áudio disponível. */ }
    }

    private SourceDataLine openLine() throws Exception {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, 4096);
        line.start();
        return line;
    }

    private byte[] tone(int hz, int millis, double volume, boolean square) {
        int samples = (int) (RATE * millis / 1000.0);
        byte[] bytes = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double phase = (i * (double) hz / RATE) % 1.0;
            double wave = square ? (phase < .5 ? 1.0 : -1.0) : (phase < .5 ? phase * 4 - 1 : 3 - phase * 4);
            double envelope = Math.min(1.0, Math.min(i / 100.0, (samples - i) / 500.0));
            short sample = (short) (wave * volume * envelope * Short.MAX_VALUE);
            bytes[2 * i] = (byte) sample;
            bytes[2 * i + 1] = (byte) (sample >>> 8);
        }
        return bytes;
    }

    private int toneLength(int millis) { return (int) (RATE * millis / 1000.0) * 2; }
}
