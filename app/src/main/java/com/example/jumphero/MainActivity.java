
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.AudioFormat;

import java.util.ArrayList;

public class MainActivity extends Activity {

    GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        game = new GameView();
        setContentView(game);
    }

    @Override
    protected void onPause() {
        if (game != null) game.stopMusic();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) game.startMusic();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.stopMusic();
        super.onDestroy();
    }

    public class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float heroX = 100, heroY;
        float heroW = 64, heroH = 82;
        float vx, vy;
        float gravity = 0.72f;
        float jumpPower = -19f;
        float speed = 6f;

        float ground, screenW, screenH, cameraX;
        float levelEnd = 4000;

        boolean left, right;
        boolean grounded = true;
        boolean jumpPressed = false;
        boolean gameOver = false;
        boolean won = false;
        boolean musicOn = true;

        int level = 1;
        int score = 0;
        int lives = 3;

        ArrayList<Platform> platforms = new ArrayList<>();
        ArrayList<Coin> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        AudioTrack track;
        Thread musicThread;
        volatile boolean musicRunning = false;

        final int RATE = 22050;

        final double[] notes = {
            523.25, 659.25, 783.99, 659.25,
            523.25, 523.25, 659.25, 783.99,
            880.00, 783.99, 659.25, 523.25,
            587.33, 659.25, 587.33, 523.25,
            392.00, 523.25, 659.25, 523.25,
            440.00, 523.25, 659.25, 783.99
        };

        public GameView() {
            super(MainActivity.this);
            setFocusable(true);
        }

        class Platform {
            float x, y, w, h;
            Platform(float x, float y, float w, float h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }

        class Coin {
            float x, y;
            boolean collected = false;
            Coin(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        class Enemy {
            float x, y, start, range;
            float direction = 1;
            boolean alive = true;

            Enemy(float x, float y, float range) {
                this.x = x;
                this.y = y;
                this.start = x;
                this.range = range;
            }
        }

        // MUSIC

        void startMusic() {
            if (!musicOn || musicRunning) return;

            musicRunning = true;

            musicThread = new Thread(() -> {

                int size = AudioTrack.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                );

                if (size <= 0) {
                    musicRunning = false;
                    return;
                }

                AudioTrack audio = null;

                try {
                    audio = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(size, 4096),
                        AudioTrack.MODE_STREAM
                    );

                    synchronized (GameView.this) {
                        track = audio;
                    }

                    audio.play();

                    short[] buffer = new short[1024];
                    int position = 0;

                    while (musicRunning) {

                        for (int i = 0; i < buffer.length; i++) {

                            int noteLength = RATE / 5;
                            int index = (position / noteLength) % notes.length;

                            double frequency = notes[index];

                            double phase = 2.0 * Math.PI *
                                frequency * position / RATE;

                            double fade = Math.min(
                                1.0,
                                (position % noteLength) / 300.0
                            );

                            double wave = Math.sin(phase) * 0.13 * fade;

                            buffer[i] = (short)(wave * 32767);
                            position++;
                        }

                        if (musicRunning) {
                            audio.write(buffer, 0, buffer.length);
                        }
                    }

                } catch (Exception ignored) {
                } finally {
                    if (audio != null) {
                        try {
                            audio.pause();
                            audio.flush();
                            audio.release();
                        } catch (Exception ignored) {
                        }
                    }

                    synchronized (GameView.this) {
                        if (track == audio) track = null;
                    }

                    musicRunning = false;
                }
            });

            musicThread.setDaemon(true);
            musicThread.start();
        }

        void stopMusic() {
            musicRunning = false;

            synchronized (this) {
                if (track != null) {
                    try {
                        track.pause();
                        track.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        void sound(double frequency) {
            // Short sound effects are generated separately
            // by the game music.
        }

        // LEVELS

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            screenW = w;
            screenH = h;
            ground = h * 0.82f;
            createLevel();
            heroY = ground - heroH;
        }

        void createLevel() {

            platforms.clear();
            coins.clear();
            enemies.clear();

            levelEnd = 4000 + level * 300;

            float spacing = 320 - level * 10;
            if (spacing < 200) spacing = 200;

            // Platforms
            for (int i = 0; i < 13; i++) {

                float x = 300 + i * spacing;
                float height = 100 + (i % 3) * 45 + level * 3;

                platforms.add(new Platform(
                    x, ground - height, 145, 28
                ));

                coins.add(new Coin(
                    x + 65, ground - height - 65
                ));
            }

            // Extra coins
            for (int i = 0; i < 24; i++) {
                coins.add(new Coin(
                    200 + i * 170,
                    ground - 230 - (i % 2) * 45
                ));
            }

            // Enemies
            for (int i = 0; i < 4 + level; i++) {
                float x = 600 + i * 550;
                enemies.add(new Enemy(
                    x, ground - 35, 55 + level * 7
                ));
            }
        }

        void nextLevel() {

            if (level >= 10) {
                won = true;
                return;
            }

            level++;

            heroX = 100;
            heroY = ground - heroH;
            cameraX = 0;
            vx = 0;
            vy = 0;

            grounded = true;
            jumpPressed = false;

            lives = Math.min(5, lives + 1);

            createLevel();
        }

        // GAME UPDATE

        void updateGame() {

            if (gameOver || won || screenW == 0) return;

            float oldBottom = heroY + heroH;

            if (left) vx = -speed;
            else if (right) vx = speed;
            else vx = 0;

            heroX += vx;

            heroX = Math.max(0, Math.min(levelEnd - heroW, heroX));

            vy += gravity;
            heroY += vy;

            grounded = false;

            // Platform landing
            if (vy >= 0) {
                for (Platform b : platforms) {

                    boolean horizontal =
                        heroX + heroW > b.x &&
                        heroX < b.x + b.w;

                    boolean vertical =
                        oldBottom <= b.y + 8 &&
                        heroY + heroH >= b.y;

                    if (horizontal && vertical) {
                        heroY = b.y - heroH;
                        vy = 0;
                        grounded = true;
                        jumpPressed = false;
                    }
                }
            }

            // Ground landing
            if (heroY + heroH >= ground) {
                heroY = ground - heroH;
                vy = 0;
                grounded = true;
                jumpPressed = false;
            }

            // Coins
            for (Coin c : coins) {
                if (c.collected) continue;

                float dx = heroX + heroW / 2 - c.x;
                float dy = heroY + heroH / 2 - c.y;

                if (Math.sqrt(dx * dx + dy * dy) < 55) {
                    c.collected = true;
                    score += 10;
                }
            }

            // Enemies
            for (Enemy e : enemies) {

                if (!e.alive) continue;

                e.x += e.direction * (1.5f + level * 0.15f);

                if (e.x > e.start + e.range) e.direction = -1;
                if (e.x < e.start - e.range) e.direction = 1;

                boolean hit =
                    heroX + heroW > e.x &&
                    heroX < e.x + 38 &&
                    heroY + heroH > e.y &&
                    heroY < e.y + 35;

                if (hit) {

                    if (vy > 2 && oldBottom <= e.y + 15) {
                        e.alive = false;
                        vy = jumpPower * 0.55f;
                        score += 50;
                    } else {
                        lives--;
                        heroX = Math.max(0, heroX - 120);
                        heroY = ground - heroH;
                        vy = 0;
                        grounded = true;
                        jumpPressed = false;

                        if (lives <= 0) gameOver = true;
                    }
                }
            }

            // Falling
            if (heroY > screenH + 100) {
                lives--;
                heroX = Math.max(0, heroX - 150);
                heroY = ground - heroH;
                vy = 0;
                grounded = true;
                jumpPressed = false;

                if (lives <= 0) gameOver = true;
            }

            // Camera
            cameraX = heroX - screenW * 0.35f;
            cameraX = Math.max(0, Math.min(
                levelEnd - screenW, cameraX
            ));

            // Finish
            if (heroX >= levelEnd - 160) {
                nextLevel();
            }
        }

        // DRAWING

        void rect(Canvas c, float x, float y,
                  float w, float h, int color) {
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(x, y, x + w, y + h, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            if (screenW == 0) return;

            updateGame();

            c.drawColor(Color.rgb(99, 194, 239));

            c.save();
            c.translate(-cameraX, 0);

            // Sun
            p.setColor(Color.YELLOW);
            c.drawCircle(550, 65, 28, p);

            // Clouds
            for (int x = 100; x < levelEnd; x += 350) {
                p.setColor(Color.WHITE);
                c.drawCircle(x, 100, 20, p);
                c.drawCircle(x + 25, 90, 25, p);
                c.drawCircle(x + 50, 100, 20, p);
            }

            // Hills
            for (int x = 0; x < levelEnd; x += 340) {
                p.setColor(Color.rgb(65, 170, 85));
                c.drawCircle(x + 100, ground, 90, p);
            }

            // Ground
            rect(c, 0, ground, levelEnd,
                screenH - ground, Color.rgb(165, 83, 35));

            p.setColor(Color.rgb(110, 55, 30));
            p.setStrokeWidth(2);

            for (int x = 0; x < levelEnd; x += 35) {
                c.drawLine(x, ground, x, screenH, p);
            }

            // Platforms
            for (Platform b : platforms) {
                rect(c, b.x, b.y, b.w, b.h,
                    Color.rgb(165, 85, 38));
                rect(c, b.x, b.y, b.w, 6,
                    Color.rgb(245, 177, 48));
            }

            // Coins
            for (Coin coin : coins) {
                if (coin.collected) continue;

                p.setColor(Color.YELLOW);
                c.drawOval(
                    coin.x - 9, coin.y - 15,
                    coin.x + 9, coin.y + 15, p
                );
            }

            // Enemies
            for (Enemy e : enemies) {
                if (!e.alive) continue;

                p.setColor(Color.rgb(110, 55, 35));
                c.drawOval(e.x, e.y, e.x + 38, e.y + 32, p);

                p.setColor(Color.WHITE);
                c.drawCircle(e.x + 12, e.y + 11, 5, p);
                c.drawCircle(e.x + 26, e.y + 11, 5, p);

                p.setColor(Color.BLACK);
                c.drawCircle(e.x + 12, e.y + 11, 2, p);
                c.drawCircle(e.x + 26, e.y + 11, 2, p);
            }

            // Finish flag
            float flagX = levelEnd - 120;

            rect(c, flagX, ground - 190,
                8, 190, Color.WHITE);

            rect(c, flagX + 8, ground - 190,
                75, 40, Color.RED);

            // Hero
            drawHero(c);

            c.restore();

            // HUD
            p.setColor(Color.WHITE);
            p.setTextSize(21);

            c.drawText("LEVEL: " + level + "/10", 15, 27, p);
            c.drawText("SCORE: " + score, 15, 53, p);
            c.drawText("LIVES: " + lives, 15, 79, p);

            c.drawText(
                musicOn ? "MUSIC ON" : "MUSIC OFF",
                screenW - 145, 30, p
            );

            drawControls(c);

            if (gameOver) drawMessage(c, "GAME OVER");
            if (won) drawMessage(c, "YOU WON ALL 10 LEVELS!");

            postInvalidateDelayed(16);
        }

        void drawHero(Canvas c) {

            float x = heroX;
            float y = heroY;

            // Shoes
            rect(c, x + 5, y + 70, 24, 12,
                Color.rgb(80, 35, 20));

            rect(c, x + 36, y + 70, 22, 12,
                Color.rgb(80, 35, 20));

            // Legs
            rect(c, x + 10, y + 52, 18, 20, Color.BLUE);
            rect(c, x + 36, y + 52, 17, 20, Color.BLUE);

            // Body
            rect(c, x + 8, y + 32, 45, 25, Color.RED);

            // Face
            rect(c, x + 13, y + 9, 40, 29,
                Color.rgb(255, 190, 130));

            // Hair
            rect(c, x + 10, y + 10, 9, 18,
                Color.rgb(100, 45, 20));

            // Eyes
            rect(c, x + 40, y + 17, 5, 6, Color.BLACK);

            // Nose
            rect(c, x + 48, y + 24, 6, 6,
                Color.rgb(230, 130, 80));

            // Cap
            rect(c, x + 10, y, 38, 11, Color.RED);
            rect(c, x + 5, y + 9, 50, 6, Color.RED);

            // Straps
            rect(c, x + 18, y + 35, 6, 20, Color.YELLOW);
            rect(c, x + 38, y + 35, 6, 20, Color.YELLOW);
        }

        void drawButton(Canvas c, float x, float y, String label) {
            p.setColor(Color.argb(150, 40, 35, 30));
            c.drawCircle(x, y, 34, p);

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, x, y + 9, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawControls(Canvas c) {
            float y = screenH * 0.91f;

            drawButton(c, screenW * 0.12f, y, "<");
            drawButton(c, screenW * 0.29f, y, ">");
            drawButton(c, screenW * 0.88f, y, "J");
            drawButton(c, screenW * 0.96f,
                screenH * 0.12f, "M");
        }

        void drawMessage(Canvas c, String message) {
            p.setColor(Color.argb(210, 0, 0, 0));

            c.drawRect(screenW * 0.12f,
                screenH * 0.30f,
                screenW * 0.88f,
                screenH * 0.70f, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(30);

            c.drawText(message, screenW / 2,
                screenH / 2, p);

            p.setTextSize(20);
            c.drawText("TAP TO RESTART",
                screenW / 2, screenH / 2 + 45, p);

            p.setTextAlign(Paint.Align.LEFT);
        }

        void restart() {
            level = 1;
            score = 0;
            lives = 3;

            heroX = 100;
            heroY = ground - heroH;

            vx = 0;
            vy = 0;
            cameraX = 0;

            grounded = true;
            jumpPressed = false;

            left = false;
            right = false;

            gameOver = false;
            won = false;

            createLevel();
        }

        // TOUCH CONTROLS

        @Override
        public boolean onTouchEvent(MotionEvent e) {

            int action = e.getActionMasked();

            if (gameOver || won) {
                if (action == MotionEvent.ACTION_DOWN) restart();
                return true;
            }

            // Music toggle
            if (action == MotionEvent.ACTION_DOWN) {
                float x = e.getX();
                float y = e.getY();

                if (x > screenW * 0.92f &&
                    y < screenH * 0.25f) {

                    musicOn = !musicOn;

                    if (musicOn) startMusic();
                    else stopMusic();

                    return true;
                }
            }

            // Jump button
            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN) {

                int index = e.getActionIndex();

                float x = e.getX(index);
                float y = e.getY(index);

                if (x > screenW * 0.70f &&
                    y > screenH * 0.78f) {

                    if (grounded && !jumpPressed) {
                        vy = jumpPower;
                        grounded = false;
                        jumpPressed = true;
                    }
                }
            }

            // Multitouch movement
            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN ||
                action == MotionEvent.ACTION_MOVE ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;

                for (int i = 0; i < e.getPointerCount(); i++) {

                    if ((action == MotionEvent.ACTION_POINTER_UP ||
                         action == MotionEvent.ACTION_UP) &&
                        i == e.getActionIndex()) {
                        continue;
                    }

                    float x = e.getX(i);
                    float y = e.getY(i);

                    if (y > screenH * 0.78f) {

                        if (x < screenW * 0.20f) left = true;

                        if (x >= screenW * 0.20f &&
                            x < screenW * 0.40f) right = true;
                    }
                }
            }

            return true;
        }
    }
}

