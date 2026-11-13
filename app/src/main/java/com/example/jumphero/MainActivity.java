

package com.example.jumphero;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        gameView = new GameView();
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.stopMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.startMusic();
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) gameView.stopMusic();
        super.onDestroy();
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float screenW, screenH;
        float groundY;
        float cameraX = 0;

        float heroX = 100;
        float heroY;
        float heroW = 65;
        float heroH = 85;

        float velocityX = 0;
        float velocityY = 0;

        float worldWidth = 5200;

        boolean leftPressed = false;
        boolean rightPressed = false;
        boolean jumpPressed = false;
        boolean downPressed = false;

        boolean grounded = false;
        boolean gameOver = false;
        boolean levelComplete = false;
        boolean climbingFlag = false;

        boolean musicOn = true;
        boolean victoryMusicPlayed = false;

        int level = 1;
        int score = 0;
        int lives = 3;

        long lastFrame = System.currentTimeMillis();
        long jumpTime = 0;

        float flagX = 0;
        float climbY = 0;

        ArrayList<RectF> bricks = new ArrayList<>();
        ArrayList<RectF> pipes = new ArrayList<>();
        ArrayList<RectF> coins = new ArrayList<>();
        ArrayList<RectF> enemies = new ArrayList<>();
        ArrayList<Boolean> coinTaken = new ArrayList<>();
        ArrayList<Float> enemyDirection = new ArrayList<>();

        AudioTrack musicTrack;
        volatile boolean musicPlaying = false;
        Thread musicThread;

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setupSize(float w, float h) {
            screenW = w;
            screenH = h;
            groundY = h - 95;

            if (heroY == 0) heroY = groundY - heroH;

            if (flagX == 0) createLevel();
        }

        void createLevel() {
            bricks.clear();
            pipes.clear();
            coins.clear();
            enemies.clear();
            coinTaken.clear();
            enemyDirection.clear();

            worldWidth = 5200 + level * 250;
            flagX = worldWidth - 300;

            heroX = 100;
            heroY = groundY - heroH;
            velocityX = 0;
            velocityY = 0;
            cameraX = 0;

            levelComplete = false;
            gameOver = false;
            climbingFlag = false;
            victoryMusicPlayed = false;

            for (int i = 0; i < 25; i++) {
                float x = 400 + i * 175;

                bricks.add(new RectF(x, groundY - 180, x + 55, groundY - 125));

                if (i % 2 == 0) {
                    bricks.add(new RectF(x + 65, groundY - 180,
                            x + 120, groundY - 125));
                }

                if (i % 3 == 0) {
                    coins.add(new RectF(x + 15, groundY - 270,
                            x + 45, groundY - 240));
                    coinTaken.add(false);
                }

                if (i % 4 == 0) {
                    enemies.add(new RectF(x + 120, groundY - 55,
                            x + 170, groundY - 5));
                    enemyDirection.add(1f);
                }
            }

            for (int i = 0; i < 7; i++) {
                float x = 750 + i * 650;
                float height = 75 + (i % 3) * 25;

                pipes.add(new RectF(x, groundY - height,
                        x + 100, groundY));
            }

            startMusic();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (screenW == 0) setupSize(getWidth(), getHeight());

            long now = System.currentTimeMillis();
            float dt = Math.min((now - lastFrame) / 16.0f, 2.0f);
            lastFrame = now;

            updateGame(dt);
            drawBackground(canvas);
            drawGround(canvas);
            drawBricks(canvas);
            drawPipes(canvas);
            drawCoins(canvas);
            drawEnemies(canvas);
            drawFlag(canvas);
            drawDoraemon(canvas);
            drawInformation(canvas);
            drawControls(canvas);

            if (gameOver) drawGameOver(canvas);
            if (levelComplete) drawLevelComplete(canvas);

            postInvalidateDelayed(16);
        }

        void updateGame(float dt) {
            if (gameOver || levelComplete) return;

            if (climbingFlag) {
                climbY += 2.0f * dt;

                if (climbY > 110) {
                    climbingFlag = false;
                    levelComplete = true;
                }
                return;
            }

            float speed = 6.0f;
            if (rightPressed && System.currentTimeMillis() - jumpTime > 350) {
                speed = 10.5f;
            }

            if (leftPressed) velocityX = -speed;
            else if (rightPressed) velocityX = speed;
            else velocityX *= 0.75f;

            heroX += velocityX * dt;

            if (heroX < 0) heroX = 0;
            if (heroX > worldWidth - heroW) heroX = worldWidth - heroW;

            if (jumpPressed && grounded) {
                velocityY = -19;
                grounded = false;
                jumpPressed = false;
            }

            velocityY += 0.85f * dt;
            heroY += velocityY * dt;

            if (heroY + heroH >= groundY) {
                heroY = groundY - heroH;
                velocityY = 0;
                grounded = true;
            }

            checkBrickCollision();
            checkPipeEntry();
            checkCoins();
            updateEnemies();

            cameraX = heroX - screenW * 0.35f;

            if (cameraX < 0) cameraX = 0;
            if (cameraX > worldWidth - screenW)
                cameraX = worldWidth - screenW;

            if (heroX >= flagX - 50) reachFlag();
        }

        void checkBrickCollision() {
            if (velocityY >= 0) return;

            for (int i = 0; i < bricks.size(); i++) {
                RectF b = bricks.get(i);

                float x = heroX + heroW / 2;

                if (x > b.left && x < b.right &&
                        heroY < b.bottom && heroY > b.top - 30) {

                    velocityY = 3;
                    score += 10;
                    playEffect(new double[]{700, 500}, 90);
                    return;
                }
            }
        }

        void checkPipeEntry() {
            if (!downPressed || !grounded) return;

            for (int i = 0; i < pipes.size(); i++) {
                RectF pipe = pipes.get(i);

                if (heroX + heroW > pipe.left &&
                        heroX < pipe.right &&
                        heroY + heroH >= pipe.top) {

                    float nextX = pipe.right + 350;

                    if (nextX < worldWidth - 250) {
                        heroX = nextX;
                        cameraX = Math.max(0, heroX - screenW * 0.35f);
                        downPressed = false;
                        playEffect(new double[]{350, 450, 550}, 180);
                    }
                    return;
                }
            }
        }

        void checkCoins() {
            for (int i = 0; i < coins.size(); i++) {
                if (coinTaken.get(i)) continue;

                RectF c = coins.get(i);

                RectF hero = new RectF(heroX, heroY,
                        heroX + heroW, heroY + heroH);

                if (RectF.intersects(hero, c)) {
                    coinTaken.set(i, true);
                    score += 50;
                    playEffect(new double[]{800, 1000, 1200}, 100);
                }
            }
        }

        void updateEnemies(float dt) {
            for (int i = 0; i < enemies.size(); i++) {
                RectF e = enemies.get(i);

                float dir = enemyDirection.get(i);
                e.offset(dir * 1.5f * dt, 0);

                if (e.left < 350 || e.left > worldWidth - 450) {
                    enemyDirection.set(i, -dir);
                }

                RectF hero = new RectF(heroX, heroY,
                        heroX + heroW, heroY + heroH);

                if (RectF.intersects(hero, e)) {
                    if (velocityY > 0 && heroY + heroH - e.top < 35) {
                        enemies.remove(i);
                        enemyDirection.remove(i);
                        velocityY = -12;
                        score += 100;
                        playEffect(new double[]{600, 900}, 100);
                        i--;
                    } else {
                        loseLife();
                        return;
                    }
                }
            }
        }

        void loseLife() {
            lives--;

            if (lives <= 0) {
                gameOver = true;
                stopMusic();
            } else {
                heroX = Math.max(100, heroX - 300);
                heroY = groundY - heroH;
                velocityY = 0;
            }
        }

        void reachFlag() {
            if (climbingFlag || levelComplete) return;

            climbingFlag = true;
            climbY = 0;
            velocityX = 0;

            stopMusic();

            if (!victoryMusicPlayed && musicOn) {
                victoryMusicPlayed = true;
                playVictorySound();
            }
        }

        void drawBackground(Canvas c) {
            int sky;
            int mountain;

            switch (level % 10) {
                case 1:
                    sky = Color.rgb(100, 190, 255);
                    mountain = Color.rgb(40, 150, 70);
                    break;
                case 2:
                    sky = Color.rgb(255, 180, 100);
                    mountain = Color.rgb(160, 100, 60);
                    break;
                case 3:
                    sky = Color.rgb(70, 100, 170);
                    mountain = Color.rgb(45, 65, 110);
                    break;
                case 4:
                    sky = Color.rgb(160, 220, 255);
                    mountain = Color.rgb(60, 160, 130);
                    break;
                case 5:
                    sky = Color.rgb(255, 210, 120);
                    mountain = Color.rgb(170, 120, 55);
                    break;
                case 6:
                    sky = Color.rgb(100, 170, 230);
                    mountain = Color.rgb(80, 120, 170);
                    break;
                case 7:
                    sky = Color.rgb(40, 50, 90);
                    mountain = Color.rgb(30, 40, 60);
                    break;
                case 8:
                    sky = Color.rgb(170, 220, 255);
                    mountain = Color.rgb(90, 170, 90);
                    break;
                case 9:
                    sky = Color.rgb(255, 160, 130);
                    mountain = Color.rgb(170, 90, 90);
                    break;
                default:
                    sky = Color.rgb(100, 190, 255);
                    mountain = Color.rgb(40, 150, 70);
                    break;
            }

            c.drawColor(sky);

            p.setColor(Color.WHITE);
            c.drawCircle(100, 90, 35, p);

            drawCloud(c, 250, 100);
            drawCloud(c, 550, 145);
            drawCloud(c, 850, 90);

            p.setColor(mountain);

            for (int i = 0; i < 12; i++) {
                float x = i * 300 - cameraX * 0.35f;
                Path path = new Path();
                path.moveTo(x, groundY);
                path.lineTo(x + 150, groundY - 210 - (i % 3) * 45);
                path.lineTo(x + 300, groundY);
                path.close();
                c.drawPath(path, p);
            }

            drawTrees(c);
        }

        void drawCloud(Canvas c, float x, float y) {
            p.setColor(Color.WHITE);
            c.drawCircle(x, y, 25, p);
            c.drawCircle(x + 25, y - 12, 30, p);
            c.drawCircle(x + 55, y, 24, p);
        }

        void drawTrees(Canvas c) {
            for (int i = 0; i < 18; i++) {
                float x = i * 330 - cameraX * 0.65f;

                p.setColor(Color.rgb(115, 65, 25));
                c.drawRect(x + 20, groundY - 100, x + 45, groundY, p);

                p.setColor(Color.rgb(20, 130, 50));
                c.drawCircle(x + 30, groundY - 120, 48, p);
                c.drawCircle(x, groundY - 95, 32, p);
                c.drawCircle(x + 60, groundY - 95, 32, p);
            }
        }

        void drawGround(Canvas c) {
            p.setColor(Color.rgb(100, 65, 30));
            c.drawRect(0, groundY, screenW, screenH, p);

            p.setColor(Color.rgb(30, 180, 55));
            c.drawRect(0, groundY, screenW, groundY + 15, p);

            p.setColor(Color.rgb(70, 45, 20));

            for (int i = 0; i < screenW / 35 + 2; i++) {
                float x = i * 35 - (cameraX % 35);
                c.drawRect(x, groundY + 25, x + 20, groundY + 45, p);
            }
        }

        void drawBricks(Canvas c) {
            for (RectF b : bricks) {
                float x = b.left - cameraX;
                if (x < -100 || x > screenW + 100) continue;

                p.setColor(Color.rgb(190, 80, 30));
                c.drawRect(x, b.top, x + b.width(), b.bottom, p);

                p.setColor(Color.rgb(255, 170, 70));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawRect(x, b.top, x + b.width(), b.bottom, p);

                c.drawLine(x, b.top + 27, x + b.width(),
                        b.top + 27, p);
                c.drawLine(x + b.width() / 2, b.top,
                        x + b.width() / 2, b.top + 27, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawPipes(Canvas c) {
            for (RectF pipe : pipes) {
                float x = pipe.left - cameraX;
                if (x < -150 || x > screenW + 150) continue;

                p.setColor(Color.rgb(0, 125, 20));
                c.drawRect(x + 8, pipe.top + 15,
                        x + pipe.width() - 8, pipe.bottom, p);

                p.setColor(Color.rgb(20, 210, 40));
                c.drawRect(x, pipe.top, x + pipe.width(),
                        pipe.top + 22, p);

                p.setColor(Color.rgb(0, 95, 15));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4);
                c.drawRect(x, pipe.top, x + pipe.width(),
                        pipe.bottom, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawCoins(Canvas c) {
            for (int i = 0; i < coins.size(); i++) {
                if (coinTaken.get(i)) continue;

                RectF coin = coins.get(i);
                float x = coin.left - cameraX;

                if (x < -50 || x > screenW + 50) continue;

                p.setColor(Color.YELLOW);
                c.drawOval(x, coin.top, x + coin.width(),
                        coin.bottom, p);

                p.setColor(Color.rgb(255, 160, 0));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawOval(x + 7, coin.top + 5,
                        x + coin.width() - 7,
                        coin.bottom - 5, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawEnemies(Canvas c) {
            for (RectF e : enemies) {
                float x = e.left - cameraX;
                if (x < -80 || x > screenW + 80) continue;

                p.setColor(Color.rgb(140, 70, 30));
                c.drawOval(x, e.top, x + e.width(),
                        e.bottom, p);

                p.setColor(Color.WHITE);
                c.drawCircle(x + 15, e.top + 18, 7, p);
                c.drawCircle(x + 35, e.top + 18, 7, p);

                p.setColor(Color.BLACK);
                c.drawCircle(x + 17, e.top + 18, 3, p);
                c.drawCircle(x + 37, e.top + 18, 3, p);
            }
        }

        void drawFlag(Canvas c) {
            float x = flagX - cameraX;

            p.setColor(Color.DKGRAY);
            c.drawRect(x, groundY - 300, x + 10, groundY, p);

            p.setColor(Color.GREEN);

            Path flag = new Path();
            flag.moveTo(x + 10, groundY - 295 + climbY);
            flag.lineTo(x + 115, groundY - 260 + climbY);
            flag.lineTo(x + 10, groundY - 225 + climbY);
            flag.close();

            c.drawPath(flag, p);

            p.setColor(Color.YELLOW);
            c.drawCircle(x + 5, groundY - 305, 12, p);
        }

        void drawDoraemon(Canvas c) {
            float x = heroX - cameraX;
            float y = heroY;

            if (climbingFlag) {
                y = groundY - heroH - 100 + climbY;
                x = flagX - cameraX - 55;
            }

            // Legs
            p.setColor(Color.BLUE);
            c.drawRoundRect(x + 12, y + 63,
                    x + 28, y + 83, 7, 7, p);
            c.drawRoundRect(x + 38, y + 63,
                    x + 53, y + 83, 7, 7, p);

            // Feet
            p.setColor(Color.WHITE);
            c.drawOval(x + 5, y + 76, x + 32, y + 88, p);
            c.drawOval(x + 34, y + 76, x + 62, y + 88, p);

            // Blue body
            p.setColor(Color.rgb(0, 125, 230));
            c.drawOval(x + 5, y + 25, x + 62, y + 75, p);

            // White face
            p.setColor(Color.WHITE);
            c.drawOval(x + 4, y, x + 65, y + 62, p);

            // Blue head outline
            p.setColor(Color.rgb(0, 110, 220));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            c.drawOval(x + 4, y, x + 65, y + 62, p);
            p.setStyle(Paint.Style.FILL);

            // Face
            p.setColor(Color.BLACK);
            c.drawOval(x + 19, y + 18, x + 25, y + 27, p);
            c.drawOval(x + 43, y + 18, x + 49, y + 27, p);

            p.setColor(Color.RED);
            c.drawCircle(x + 34, y + 31, 5, p);

            p.setColor(Color.BLACK);
            p.setStrokeWidth(2);
            c.drawLine(x + 34, y + 36, x + 34, y + 46, p);
            c.drawArc(x + 20, y + 35, x + 49,
                    y + 54, 10, 160, false, p);

            // Whiskers
            c.drawLine(x + 5, y + 34, x + 23, y + 37, p);
            c.drawLine(x + 5, y + 43, x + 23, y + 43, p);
            c.drawLine(x + 45, y + 37, x + 63, y + 34, p);
            c.drawLine(x + 45, y + 43, x + 63, y + 43, p);

            // Bell
            p.setColor(Color.YELLOW);
            c.drawCircle(x + 34, y + 65, 8, p);
            p.setColor(Color.BLACK);
            c.drawCircle(x + 34, y + 65, 2, p);
        }

        void drawInformation(Canvas c) {
            p.setColor(Color.argb(190, 0, 0, 0));
            c.drawRoundRect(10, 10, 260, 85, 15, 15, p);

            p.setColor(Color.WHITE);
            p.setTextSize(23);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText("LEVEL: " + level + "/10", 25, 40, p);
            c.drawText("SCORE: " + score, 25, 70, p);

            c.drawText("LIVES: " + lives, screenW - 150, 40, p);
        }

        void drawButton(Canvas c, float x, float y,
                        float radius, String label) {
            p.setColor(Color.argb(150, 0, 0, 0));
            c.drawCircle(x, y, radius, p);

            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            c.drawCircle(x, y, radius, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextSize(30);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, x, y + 11, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawControls(Canvas c) {
            float y = screenH - 65;

            drawButton(c, 65, y, 42, "<");
            drawButton(c, 165, y, 42, ">");
            drawButton(c, screenW * 0.42f, y, 42, "DOWN");
            drawButton(c, screenW - 75, y, 48, "JUMP");
        }

        void drawGameOver(Canvas c) {
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.RED);
            p.setTextSize(55);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("GAME OVER", screenW / 2,
                    screenH / 2, p);

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            c.drawText("Tap to restart",
                    screenW / 2, screenH / 2 + 55, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawLevelComplete(Canvas c) {
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.YELLOW);
            p.setTextSize(48);
            p.setTextAlign(Paint.Align.CENTER);

            if (level < 10) {
                c.drawText("LEVEL " + level + " COMPLETE!",
                        screenW / 2, screenH / 2 - 10, p);
            } else {
                c.drawText("YOU WIN!",
                        screenW / 2, screenH / 2 - 10, p);
            }

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            c.drawText("Tap to continue",
                    screenW / 2, screenH / 2 + 50, p);

            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN) {

                if (gameOver) {
                    gameOver = false;
                    score = 0;
                    lives = 3;
                    level = 1;
                    createLevel();
                    return true;
                }

                if (levelComplete) {
                    if (level < 10) {
                        level++;
                        createLevel();
                    } else {
                        level = 1;
                        score = 0;
                        lives = 3;
                        createLevel();
                    }
                    return true;
                }

                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);

                if (y > screenH - 125) {
                    if (x < 115) {
                        leftPressed = true;
                    } else if (x < 250) {
                        rightPressed = true;
                        jumpTime = event.getEventTime();
                    } else if (x < screenW * 0.58f) {
                        downPressed = true;
                    } else {
                        jumpPressed = true;
                    }
                }
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_POINTER_UP ||
                    action == MotionEvent.ACTION_CANCEL) {

                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);

                if (y > screenH - 125) {
                    if (x < 115) leftPressed = false;
                    else if (x < 250) rightPressed = false;
                    else if (x < screenW * 0.58f) downPressed = false;
                    else jumpPressed = false;
                }

                if (action == MotionEvent.ACTION_CANCEL) {
                    leftPressed = false;
                    rightPressed = false;
                    jumpPressed = false;
                    downPressed = false;
                }

                return true;
            }

            return true;
        }

        void startMusic() {
            if (!musicOn || musicPlaying || levelComplete || gameOver) return;

            musicPlaying = true;

            musicThread = new Thread(() -> {
                int sampleRate = 22050;
                int bufferSize = 1024;

                try {
                    musicTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                    );

                    musicTrack.play();

                    double[] notes = {
                            523, 659, 784, 659,
                            587, 698, 880, 698,
                            523, 659, 784, 1047
                    };

                    int n = 0;

                    while (musicPlaying) {
                        short[] samples = new short[bufferSize];

                        for (int i = 0; i < samples.length; i++) {
                            double angle = 2.0 * Math.PI *
                                    notes[n] * i / sampleRate;

                            samples[i] = (short)
                                    (Math.sin(angle) * 2500);
                        }

                        musicTrack.write(samples, 0, samples.length);
                        n = (n + 1) % notes.length;
                    }

                    musicTrack.stop();
                    musicTrack.release();
                    musicTrack = null;

                } catch (Exception ignored) {
                    musicPlaying = false;
                }
            });

            musicThread.start();
        }

        void stopMusic() {
            musicPlaying = false;

            try {
                if (musicTrack != null) {
                    musicTrack.pause();
                    musicTrack.flush();
                    musicTrack.stop();
                    musicTrack.release();
                    musicTrack = null;
                }
            } catch (Exception ignored) {
            }
        }

        void playVictorySound() {
            playEffect(new double[]{
                    523, 659, 784, 1047, 1175, 1047, 1318
            }, 350);
        }

        void playEffect(double[] notes, int duration) {
            new Thread(() -> {
                int sampleRate = 22050;

                try {
                    for (double note : notes) {
                        short[] samples =
                                new short[sampleRate * duration / 1000];

                        for (int i = 0; i < samples.length; i++) {
                            double angle = 2.0 * Math.PI *
                                    note * i / sampleRate;

                            samples[i] = (short)
                                    (Math.sin(angle) * 5000);
                        }

                        AudioTrack track = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                sampleRate,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                samples.length * 2,
                                AudioTrack.MODE_STATIC
                        );

                        track.write(samples, 0, samples.length);
                        track.play();

                        Thread.sleep(duration);

                        track.stop();
                        track.release();
                    }
                } catch (Exception ignored) {
                }
            }).start();
        }
    }
}
