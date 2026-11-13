
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioManager;

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
    protected void onResume() {
        super.onResume();
        if (game != null) game.startMusicIfEnabled();
    }

    @Override
    protected void onPause() {
        if (game != null) game.stopMusic();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.stopMusic();
        super.onDestroy();
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float screenW, screenH, groundY;
        float heroX = 100, heroY;
        float heroW = 55, heroH = 75;

        float velocityY = 0;
        float speed = 6.5f;
        float gravity = 0.75f;
        float jumpPower = 17;

        float cameraX = 0;
        float levelEnd = 4500;

        boolean left = false;
        boolean right = false;
        boolean jump = false;
        boolean grounded = false;

        int level = 1;
        int score = 0;
        int lives = 3;

        boolean gameOver = false;
        boolean finished = false;

        boolean musicEnabled = true;
        volatile boolean musicRunning = false;

        Thread musicThread;

        ArrayList<Pipe> pipes = new ArrayList<>();
        ArrayList<Spring> springs = new ArrayList<>();
        ArrayList<Coin> coins = new ArrayList<>();
        ArrayList<Platform> platforms = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        class Pipe {
            float x, y, w, h;
            Pipe(float x, float y, float h) {
                this.x = x;
                this.y = y;
                this.w = 75;
                this.h = h;
            }
        }

        class Spring {
            float x, y;
            Spring(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        class Coin {
            float x, y;
            boolean taken = false;
            Coin(float x, float y) {
                this.x = x;
                this.y = y;
            }
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

        class Enemy {
            float x, y, startX;
            float range = 100;
            float speed = 1.5f;
            boolean alive = true;

            Enemy(float x, float y) {
                this.x = x;
                this.y = y;
                this.startX = x;
            }
        }

        void createLevel() {
            pipes.clear();
            springs.clear();
            coins.clear();
            platforms.clear();
            enemies.clear();

            levelEnd = 4500 + level * 350;

            for (int i = 0; i < 10 + level; i++) {
                float x = 450 + i * 350;
                coins.add(new Coin(x, groundY - 140 - (i % 3) * 25));
            }

            for (int i = 0; i < 5 + level; i++) {
                float x = 850 + i * 850;
                float h = 65 + (i % 3) * 30;
                pipes.add(new Pipe(x, groundY - h, h));
            }

            for (int i = 0; i < 4 + level / 2; i++) {
                springs.add(new Spring(1200 + i * 1100, groundY - 22));
            }

            for (int i = 0; i < 7 + level; i++) {
                float x = 600 + i * 550;
                platforms.add(new Platform(
                    x, groundY - 150 - (i % 3) * 35, 130, 22));
            }

            for (int i = 0; i < 4 + level; i++) {
                enemies.add(new Enemy(1100 + i * 800, groundY - 38));
            }

            heroX = 100;
            heroY = groundY - heroH;
            velocityY = 0;
            cameraX = 0;
            grounded = true;
            finished = false;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            screenW = w;
            screenH = h;
            groundY = h * 0.78f;
            heroY = groundY - heroH;
            createLevel();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            if (screenW == 0 || screenH == 0) return;

            updateGame();
            drawBackground(c);

            c.save();
            c.translate(-cameraX, 0);

            drawGround(c);
            drawPlatforms(c);
            drawPipes(c);
            drawSprings(c);
            drawCoins(c);
            drawEnemies(c);
            drawFinish(c);
            drawHero(c);

            c.restore();

            drawControls(c);
            drawScore(c);

            if (gameOver) drawMessage(c, "GAME OVER - TAP TO RESTART");
            if (finished) drawMessage(c, "YOU WIN!");

            postInvalidateDelayed(16);
        }

        void updateGame() {
            if (gameOver || finished) return;

            float oldX = heroX;
            float oldBottom = heroY + heroH;

            float dx = 0;
            if (left) dx -= speed;
            if (right) dx += speed;

            heroX += dx;

            if (heroX < 0) heroX = 0;
            if (heroX > levelEnd) heroX = levelEnd;

            velocityY += gravity;
            if (velocityY > 20) velocityY = 20;
            heroY += velocityY;

            grounded = false;

            if (heroY + heroH >= groundY) {
                heroY = groundY - heroH;
                velocityY = 0;
                grounded = true;
            }

            for (Platform platform : platforms) {
                boolean horizontal =
                    heroX + heroW > platform.x &&
                    heroX < platform.x + platform.w;

                if (horizontal && oldBottom <= platform.y &&
                    heroY + heroH >= platform.y && velocityY >= 0) {
                    heroY = platform.y - heroH;
                    velocityY = 0;
                    grounded = true;
                }
            }

            for (Pipe pipe : pipes) {
                boolean horizontal =
                    heroX + heroW > pipe.x &&
                    heroX < pipe.x + pipe.w;

                boolean vertical =
                    heroY + heroH > pipe.y &&
                    heroY < groundY;

                if (horizontal && vertical) {
                    if (oldBottom <= pipe.y + 8 && velocityY >= 0) {
                        heroY = pipe.y - heroH;
                        velocityY = 0;
                        grounded = true;
                    } else if (oldX + heroW <= pipe.x) {
                        heroX = pipe.x - heroW;
                    } else if (oldX >= pipe.x + pipe.w) {
                        heroX = pipe.x + pipe.w;
                    }
                }
            }

            for (Spring s : springs) {
                boolean horizontal =
                    heroX + heroW > s.x &&
                    heroX < s.x + 55;

                boolean landing =
                    oldBottom <= s.y + 25 &&
                    heroY + heroH >= s.y &&
                    velocityY >= 0;

                if (horizontal && landing) {
                    heroY = s.y - heroH;
                    velocityY = -jumpPower * 1.5f;
                    grounded = false;
                    score += 5;
                }
            }

            for (Coin coin : coins) {
                if (!coin.taken &&
                    heroX + heroW > coin.x - 18 &&
                    heroX < coin.x + 18 &&
                    heroY + heroH > coin.y - 18 &&
                    heroY < coin.y + 18) {
                    coin.taken = true;
                    score += 10;
                }
            }

            for (Enemy enemy : enemies) {
                if (!enemy.alive) continue;

                enemy.x += enemy.speed;

                if (enemy.x > enemy.startX + enemy.range ||
                    enemy.x < enemy.startX - enemy.range) {
                    enemy.speed *= -1;
                }

                boolean hit =
                    heroX + heroW > enemy.x &&
                    heroX < enemy.x + 45 &&
                    heroY + heroH > enemy.y &&
                    heroY < enemy.y + 40;

                if (hit) {
                    if (velocityY > 2 && oldBottom <= enemy.y + 15) {
                        enemy.alive = false;
                        velocityY = -jumpPower * 0.65f;
                        score += 20;
                    } else {
                        lives--;
                        heroX = Math.max(0, heroX - 120);
                        heroY = groundY - heroH;
                        velocityY = 0;

                        if (lives <= 0) gameOver = true;
                    }
                }
            }

            cameraX = heroX - screenW * 0.35f;
            if (cameraX < 0) cameraX = 0;

            float maxCamera = levelEnd - screenW;
            if (maxCamera < 0) maxCamera = 0;
            if (cameraX > maxCamera) cameraX = maxCamera;

            if (heroX >= levelEnd - 100) {
                if (level < 10) {
                    level++;
                    createLevel();
                } else {
                    finished = true;
                }
            }
        }

        void drawBackground(Canvas c) {
            c.drawColor(Color.rgb(100, 190, 255));

            p.setColor(Color.WHITE);
            c.drawCircle(screenW * 0.8f, screenH * 0.17f, 38, p);
            c.drawCircle(screenW * 0.2f, screenH * 0.2f, 25, p);
            c.drawCircle(screenW * 0.24f, screenH * 0.2f, 32, p);
            c.drawCircle(screenW * 0.28f, screenH * 0.2f, 23, p);
        }

        void drawGround(Canvas c) {
            p.setColor(Color.rgb(120, 75, 35));
            c.drawRect(0, groundY, levelEnd + 500, screenH, p);

            p.setColor(Color.rgb(30, 170, 50));
            c.drawRect(0, groundY, levelEnd + 500, groundY + 15, p);
        }

        void drawPlatforms(Canvas c) {
            for (Platform platform : platforms) {
                p.setColor(Color.rgb(160, 90, 35));
                c.drawRect(platform.x, platform.y,
                    platform.x + platform.w, platform.y + platform.h, p);

                p.setColor(Color.YELLOW);
                c.drawRect(platform.x, platform.y,
                    platform.x + platform.w, platform.y + 6, p);
            }
        }

        void drawPipes(Canvas c) {
            for (Pipe pipe : pipes) {
                p.setColor(Color.rgb(0, 130, 30));
                c.drawRect(pipe.x, pipe.y + 15,
                    pipe.x + pipe.w, groundY, p);

                p.setColor(Color.rgb(0, 200, 50));
                c.drawRect(pipe.x - 8, pipe.y,
                    pipe.x + pipe.w + 8, pipe.y + 22, p);

                p.setColor(Color.rgb(80, 230, 90));
                c.drawRect(pipe.x + 12, pipe.y + 25,
                    pipe.x + 22, groundY, p);
            }
        }

        void drawSprings(Canvas c) {
            for (Spring s : springs) {
                p.setColor(Color.RED);
                c.drawRect(s.x, s.y + 12, s.x + 55, s.y + 22, p);

                p.setColor(Color.YELLOW);
                c.drawRect(s.x + 5, s.y + 5, s.x + 50, s.y + 13, p);

                p.setColor(Color.WHITE);
                c.drawRect(s.x + 10, s.y, s.x + 45, s.y + 6, p);
            }
        }

        void drawCoins(Canvas c) {
            for (Coin coin : coins) {
                if (!coin.taken) {
                    p.setColor(Color.YELLOW);
                    c.drawCircle(coin.x, coin.y, 15, p);

                    p.setColor(Color.rgb(255, 180, 0));
                    c.drawCircle(coin.x, coin.y, 8, p);
                }
            }
        }

        void drawEnemies(Canvas c) {
            for (Enemy enemy : enemies) {
                if (!enemy.alive) continue;

                p.setColor(Color.rgb(130, 50, 30));
                c.drawOval(enemy.x, enemy.y,
                    enemy.x + 45, enemy.y + 38, p);

                p.setColor(Color.WHITE);
                c.drawCircle(enemy.x + 12, enemy.y + 12, 5, p);
                c.drawCircle(enemy.x + 32, enemy.y + 12, 5, p);

                p.setColor(Color.BLACK);
                c.drawCircle(enemy.x + 12, enemy.y + 12, 2, p);
                c.drawCircle(enemy.x + 32, enemy.y + 12, 2, p);
            }
        }

        void drawFinish(Canvas c) {
            p.setColor(Color.DKGRAY);
            c.drawRect(levelEnd, groundY - 200,
                levelEnd + 8, groundY, p);

            p.setColor(Color.RED);
            c.drawRect(levelEnd + 8, groundY - 200,
                levelEnd + 73, groundY - 165, p);
        }

        void drawHero(Canvas c) {
            p.setColor(Color.RED);
            c.drawRect(heroX + 5, heroY,
                heroX + 50, heroY + 25, p);

            p.setColor(Color.rgb(255, 190, 130));
            c.drawRect(heroX + 10, heroY + 22,
                heroX + 47, heroY + 48, p);

            p.setColor(Color.BLUE);
            c.drawRect(heroX + 5, heroY + 48,
                heroX + 50, heroY + 70, p);

            p.setColor(Color.BLACK);
            c.drawRect(heroX + 5, heroY + 68,
                heroX + 25, heroY + 75, p);
            c.drawRect(heroX + 32, heroY + 68,
                heroX + 52, heroY + 75, p);

            p.setColor(Color.BLACK);
            c.drawCircle(heroX + 35, heroY + 32, 3, p);
        }

        void drawControls(Canvas c) {
            float y = screenH - 90;

            drawButton(c, 85, y, 62,
                Color.rgb(30, 80, 200), "<");

            drawButton(c, 225, y, 62,
                Color.rgb(30, 80, 200), ">");

            drawButton(c, screenW - 95, y, 68,
                Color.rgb(20, 160, 50), "JUMP");

            drawButton(c, screenW - 55, 55, 30,
                musicEnabled ? Color.GREEN : Color.GRAY,
                musicEnabled ? "♪" : "X");
        }

        void drawButton(Canvas c, float x, float y,
                        float radius, int color, String label) {
            p.setColor(Color.argb(150, 0, 0, 0));
            c.drawCircle(x + 3, y + 4, radius + 3, p);

            p.setColor(color);
            c.drawCircle(x, y, radius, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(label.equals("JUMP") ? 19 : 38);
            p.setFakeBoldText(true);
            c.drawText(label, x, y + 12, p);
            p.setFakeBoldText(false);
        }

        void drawScore(Canvas c) {
            p.setColor(Color.BLACK);
            p.setTextSize(25);
            p.setTextAlign(Paint.Align.LEFT);

            c.drawText("LEVEL: " + level + "/10", 20, 35, p);
            c.drawText("SCORE: " + score, 20, 65, p);
            c.drawText("LIVES: " + lives, 20, 95, p);
        }

        void drawMessage(Canvas c, String message) {
            p.setColor(Color.argb(190, 0, 0, 0));
            c.drawRoundRect(new RectF(
                screenW / 2 - 220, screenH / 2 - 55,
                screenW / 2 + 220, screenH / 2 + 55),
                20, 20, p);

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(message, screenW / 2,
                screenH / 2 + 8, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN) {

                int index = event.getActionIndex();

                if (event.getY(index) < 100 &&
                    event.getX(index) > screenW - 100) {

                    musicEnabled = !musicEnabled;

                    if (musicEnabled) startMusicIfEnabled();
                    else stopMusic();

                    invalidate();
                    return true;
                }

                if (gameOver || finished) {
                    level = 1;
                    score = 0;
                    lives = 3;
                    gameOver = false;
                    finished = false;
                    createLevel();
                    invalidate();
                    return true;
                }
            }

            boolean released =
                action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL;

            updateTouchControls(event, released);
            return true;
        }

        void updateTouchControls(MotionEvent event, boolean skipReleased) {
            boolean newLeft = false;
            boolean newRight = false;
            boolean newJump = false;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (skipReleased && i == event.getActionIndex()) continue;

                float x = event.getX(i);
                float y = event.getY(i);

                if (y > screenH - 180) {
                    if (x < 160) {
                        newLeft = true;
                    } else if (x < 320) {
                        newRight = true;
                    } else if (x > screenW - 190) {
                        newJump = true;
                    }
                }
            }

            if (newJump && !jump && grounded) {
                velocityY = -jumpPower;
                grounded = false;
            }

            left = newLeft;
            right = newRight;
            jump = newJump;
        }

        void startMusicIfEnabled() {
            if (!musicEnabled || musicRunning) return;

            musicRunning = true;

            musicThread = new Thread(() -> {
                int sampleRate = 22050;
                int bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

                if (bufferSize < 2048) bufferSize = 2048;

                AudioTrack track = null;

                try {
                    track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM);

                    track.play();

                    short[] buffer = new short[1024];
                    double phase = 0;
                    int note = 0;

                    double[] melody = {
                        523.25, 659.25, 783.99, 659.25,
                        587.33, 698.46, 880.00, 698.46,
                        523.25, 659.25, 783.99, 1046.5
                    };

                    while (musicRunning) {
                        double frequency = melody[note];

                        for (int i = 0; i < buffer.length; i++) {
                            buffer[i] = (short)(Math.sin(phase) * 3500);
                            phase += 2.0 * Math.PI * frequency / sampleRate;

                            if (phase > 2.0 * Math.PI) {
                                phase -= 2.0 * Math.PI;
                            }
                        }

                        track.write(buffer, 0, buffer.length);
                        note = (note + 1) % melody.length;
                    }

                } catch (Exception ignored) {
                } finally {
                    if (track != null) {
                        try {
                            track.pause();
                            track.flush();
                            track.stop();
                            track.release();
                        } catch (Exception ignored) {
                        }
                    }
                    musicRunning = false;
                }
            });

            musicThread.setDaemon(true);
            musicThread.start();
        }

        void stopMusic() {
            musicRunning = false;
        }
    }
}

