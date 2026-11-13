

package com.example.jumphero;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;

public class MainActivity extends Activity {

    GameView gameView;

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
        if (gameView != null) gameView.pauseGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeGame();
    }

    class Block {
        RectF r;
        int type;
        boolean used = false;

        Block(float x, float y, int type) {
            r = new RectF(x, y, x + 48, y + 42);
            this.type = type;
        }
    }

    class Enemy {
        RectF r;
        float speed;
        float leftLimit, rightLimit;
        boolean alive = true;

        Enemy(float x, float y, float range) {
            r = new RectF(x, y, x + 42, y + 38);
            leftLimit = x - range;
            rightLimit = x + range;
            speed = 95;
        }
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ArrayList<RectF> platforms = new ArrayList<>();
        ArrayList<RectF> pipes = new ArrayList<>();
        ArrayList<Block> blocks = new ArrayList<>();
        ArrayList<RectF> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        float screenW, screenH;
        float groundY;
        float worldWidth;
        float cameraX = 0;

        float heroX = 100;
        float heroY;
        float heroW = 48;
        float heroH = 66;

        float velocityX = 0;
        float velocityY = 0;

        float gravity = 1450;
        float jumpPower = -620;
        float walkSpeed = 260;
        float turboSpeed = 410;

        boolean leftPressed = false;
        boolean rightPressed = false;
        boolean jumpPressed = false;
        boolean downPressed = false;
        boolean grounded = false;
        boolean gameOver = false;
        boolean levelComplete = false;
        boolean climbingFlag = false;

        float flagX;
        float climbY = 0;
        float levelTimer = 0;

        int level = 1;
        int score = 0;
        int lives = 3;
        int collectedCoins = 0;

        long lastTime = 0;
        boolean running = true;

        ToneGenerator tone;

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            try {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 65);
            } catch (Exception ignored) {
            }
        }

        void pauseGame() {
            running = false;
        }

        void resumeGame() {
            running = true;
            lastTime = 0;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenW = w;
            screenH = h;
            groundY = h - 95;
            createLevel();
        }

        void sound(int type) {
            if (tone == null) return;

            try {
                if (type == 1) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                } else if (type == 2) {
                    tone.startTone(ToneGenerator.TONE_DTMF_5, 130);
                } else if (type == 3) {
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 180);
                }
            } catch (Exception ignored) {
            }
        }

        void createLevel() {
            platforms.clear();
            pipes.clear();
            blocks.clear();
            coins.clear();
            enemies.clear();

            heroX = 100;
            heroY = groundY - heroH;
            velocityX = 0;
            velocityY = 0;
            cameraX = 0;
            grounded = true;
            levelComplete = false;
            climbingFlag = false;
            gameOver = false;
            climbY = 0;

            worldWidth = 3000 + level * 350;
            flagX = worldWidth - 180;

            // Floating platforms
            for (int i = 0; i < 8 + level; i++) {
                float x = 420 + i * 300;
                float y = groundY - 145 - (i % 3) * 65;
                platforms.add(new RectF(x, y, x + 150, y + 22));
            }

            // Pipes
            for (int i = 0; i < 7 + level / 2; i++) {
                float x = 650 + i * 390;
                float height = 75 + (i % 3) * 25;
                pipes.add(new RectF(x, groundY - height, x + 85, groundY));
            }

            // Brick blocks and question blocks
            for (int i = 0; i < 24 + level * 2; i++) {
                float x = 300 + i * 105;
                float y = groundY - 165 - (i % 3) * 45;
                blocks.add(new Block(x, y, i % 4 == 0 ? 1 : 0));
            }

            // Coin rows
            for (int i = 0; i < 50; i++) {
                float x = 240 + i * 55;
                float y = groundY - 220 - (i % 3) * 25;
                coins.add(new RectF(x, y, x + 22, y + 22));
            }

            // Enemies
            for (int i = 0; i < 7 + level; i++) {
                float x = 800 + i * 350;
                enemies.add(new Enemy(x, groundY - 38, 90 + level * 5));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (screenW == 0) return;

            long now = System.nanoTime();

            float dt = 0.016f;
            if (lastTime != 0) {
                dt = (now - lastTime) / 1000000000f;
                dt = Math.min(dt, 0.035f);
            }
            lastTime = now;

            if (running && !gameOver && !levelComplete) {
                updateGame(dt);
            }

            drawBackground(canvas);
            drawWorld(canvas);
            drawHero(canvas);
            drawHUD(canvas);
            drawControls(canvas);

            if (gameOver) drawGameOver(canvas);
            if (levelComplete) drawLevelComplete(canvas);

            postInvalidateDelayed(16);
        }

        void updateGame(float dt) {
            if (climbingFlag) {
                climbY -= 100 * dt;

                if (climbY < -100) {
                    levelComplete = true;
                    climbingFlag = false;
                    sound(3);
                }
                return;
            }

            float speed = rightPressed ? turboSpeed : walkSpeed;

            if (leftPressed) velocityX = -speed;
            else if (rightPressed) velocityX = speed;
            else velocityX = 0;

            float oldY = heroY;

            heroX += velocityX * dt;
            heroX = Math.max(0, Math.min(heroX, worldWidth - heroW));

            velocityY += gravity * dt;
            heroY += velocityY * dt;

            grounded = false;

            // Ground collision
            if (heroY + heroH >= groundY) {
                heroY = groundY - heroH;
                velocityY = 0;
                grounded = true;
            }

            // Platform and pipe collision
            ArrayList<RectF> allPlatforms = new ArrayList<>();
            allPlatforms.addAll(platforms);
            allPlatforms.addAll(pipes);

            for (Block b : blocks) {
                allPlatforms.add(b.r);
            }

            for (RectF platform : allPlatforms) {
                boolean horizontal =
                        heroX + heroW > platform.left &&
                        heroX < platform.right;

                if (!horizontal) continue;

                if (velocityY >= 0 &&
                        oldY + heroH <= platform.top + 8 &&
                        heroY + heroH >= platform.top) {

                    heroY = platform.top - heroH;
                    velocityY = 0;
                    grounded = true;
                }
            }

            // Hit bricks from underneath
            if (velocityY < 0) {
                for (int i = 0; i < blocks.size(); i++) {
                    Block b = blocks.get(i);

                    boolean horizontal =
                            heroX + heroW - 6 > b.r.left &&
                            heroX + 6 < b.r.right;

                    boolean crossedBottom =
                            oldY >= b.r.bottom - 4 &&
                            heroY <= b.r.bottom;

                    if (horizontal && crossedBottom) {
                        velocityY = 80;

                        if (b.type == 1 && !b.used) {
                            b.used = true;
                            score += 20;
                            collectedCoins++;
                            sound(2);
                        } else if (b.type == 0) {
                            blocks.remove(i);
                            score += 10;
                            sound(1);
                        }
                        break;
                    }
                }
            }

            // Jump
            if (jumpPressed && grounded) {
                velocityY = jumpPower;
                grounded = false;
                sound(2);
            }
            jumpPressed = false;

            // Collect coins
            RectF heroRect = new RectF(
                    heroX, heroY, heroX + heroW, heroY + heroH
            );

            Iterator<RectF> coinIterator = coins.iterator();
            while (coinIterator.hasNext()) {
                RectF coin = coinIterator.next();
                if (RectF.intersects(heroRect, coin)) {
                    coinIterator.remove();
                    collectedCoins++;
                    score += 50;
                    sound(1);
                }
            }

            // Enemy movement and collision
            for (Enemy e : enemies) {
                if (!e.alive) continue;

                e.r.left += e.speed * dt;
                e.r.right += e.speed * dt;

                if (e.r.left < e.leftLimit || e.r.right > e.rightLimit) {
                    e.speed = -e.speed;
                }

                if (RectF.intersects(heroRect, e.r)) {
                    if (velocityY > 0 && heroY + heroH - 12 < e.r.top + 15) {
                        e.alive = false;
                        velocityY = -380;
                        score += 100;
                        sound(2);
                    } else {
                        loseLife();
                    }
                }
            }

            // Enter pipe with DOWN button
            if (downPressed && grounded) {
                for (RectF pipe : pipes) {
                    if (heroX + heroW > pipe.left &&
                            heroX < pipe.right &&
                            Math.abs(heroY + heroH - groundY) < 5) {

                        heroX = Math.min(heroX + 480, worldWidth - 250);
                        heroY = groundY - heroH;
                        cameraX = Math.max(0, heroX - screenW * 0.35f);
                        sound(3);
                        break;
                    }
                }
                downPressed = false;
            }

            // Camera follows player
            cameraX = heroX - screenW * 0.38f;
            cameraX = Math.max(0, Math.min(cameraX, worldWidth - screenW));

            // Finish flag
            if (heroX + heroW >= flagX - 35) {
                climbingFlag = true;
                climbY = 0;
                sound(3);
            }

            if (heroY > screenH + 100) loseLife();
        }

        void loseLife() {
            lives--;
            if (lives <= 0) {
                gameOver = true;
            } else {
                heroX = Math.max(100, heroX - 250);
                heroY = groundY - heroH;
                velocityY = 0;
                velocityX = 0;
            }
        }

        void drawBackground(Canvas c) {
            // Sky
            c.drawColor(level % 3 == 0
                    ? Color.rgb(25, 30, 90)
                    : Color.rgb(95, 190, 255));

            float shift = cameraX * 0.25f;

            // Sun
            p.setColor(Color.YELLOW);
            c.drawCircle(screenW - 100, 85, 38, p);

            // Clouds
            p.setColor(Color.WHITE);
            for (int i = 0; i < 12; i++) {
                float x = i * 260 - shift % 260;
                float y = 65 + (i % 3) * 45;

                c.drawCircle(x, y, 24, p);
                c.drawCircle(x + 27, y - 12, 30, p);
                c.drawCircle(x + 55, y, 23, p);
                c.drawRect(x, y, x + 55, y + 18, p);
            }

            // Mountains
            p.setColor(level % 2 == 0
                    ? Color.rgb(80, 100, 180)
                    : Color.rgb(70, 175, 100));

            for (int i = 0; i < 9; i++) {
                float x = i * 370 - cameraX * 0.45f % 370;
                Path mountain = new Path();
                mountain.moveTo(x, groundY);
                mountain.lineTo(x + 130, groundY - 190);
                mountain.lineTo(x + 280, groundY);
                mountain.close();
                c.drawPath(mountain, p);
            }

            // Hills
            p.setColor(Color.rgb(40, 175, 65));
            for (int i = 0; i < 10; i++) {
                float x = i * 330 - cameraX * 0.65f % 330;
                c.drawOval(x, groundY - 105, x + 220, groundY + 35, p);
            }
        }

        void drawWorld(Canvas c) {
            c.save();
            c.translate(-cameraX, 0);

            // Ground
            p.setColor(Color.rgb(150, 80, 35));
            c.drawRect(cameraX, groundY, cameraX + screenW, screenH, p);

            p.setColor(Color.rgb(50, 190, 55));
            c.drawRect(cameraX, groundY, cameraX + screenW, groundY + 13, p);

            // Ground pattern
            p.setColor(Color.rgb(105, 55, 25));
            for (int i = 0; i < worldWidth / 55; i++) {
                float x = i * 55;
                c.drawRect(x, groundY + 25, x + 4, groundY + 29, p);
                c.drawRect(x + 25, groundY + 55, x + 29, groundY + 59, p);
            }

            // Platforms
            for (RectF r : platforms) {
                drawPlatform(c, r);
            }

            // Pipes
            for (RectF r : pipes) {
                drawPipe(c, r);
            }

            // Bricks
            for (Block b : blocks) {
                drawBlock(c, b);
            }

            // Coins
            for (RectF coin : coins) {
                drawCoin(c, coin);
            }

            // Enemies
            for (Enemy e : enemies) {
                if (e.alive) drawEnemy(c, e);
            }

            drawFlag(c);

            c.restore();
        }

        void drawPlatform(Canvas c, RectF r) {
            p.setColor(Color.rgb(180, 90, 35));
            c.drawRect(r, p);

            p.setColor(Color.rgb(235, 150, 65));
            c.drawRect(r.left, r.top, r.right, r.top + 8, p);

            p.setColor(Color.rgb(90, 45, 20));
            for (float x = r.left; x < r.right; x += 38) {
                c.drawLine(x, r.top, x, r.bottom, p);
            }
            c.drawLine(r.left, r.top + 21, r.right, r.top + 21, p);
        }

        void drawBlock(Canvas c, Block b) {
            RectF r = b.r;

            if (b.type == 1) {
                p.setColor(b.used
                        ? Color.rgb(130, 100, 70)
                        : Color.rgb(255, 175, 20));
                c.drawRoundRect(r, 4, 4, p);

                p.setColor(Color.rgb(130, 65, 15));
                p.setTextSize(30);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText(b.used ? "·" : "?", r.left + 14, r.top + 32, p);
            } else {
                p.setColor(Color.rgb(190, 85, 35));
                c.drawRect(r, p);

                p.setColor(Color.rgb(255, 175, 85));
                c.drawRect(r.left + 3, r.top + 3,
                        r.right - 3, r.top + 7, p);

                p.setColor(Color.rgb(100, 40, 15));
                c.drawLine(r.left, r.top + 21, r.right, r.top + 21, p);
                c.drawLine(r.left + 24, r.top, r.left + 24, r.top + 21, p);
                c.drawLine(r.left + 12, r.top + 21,
                        r.left + 12, r.bottom, p);
                c.drawLine(r.left + 36, r.top + 21,
                        r.left + 36, r.bottom, p);
            }
        }

        void drawPipe(Canvas c, RectF r) {
            p.setColor(Color.rgb(0, 135, 35));
            c.drawRect(r.left + 8, r.top + 15, r.right - 8, r.bottom, p);

            p.setColor(Color.rgb(0, 195, 50));
            c.drawRoundRect(r.left, r.top, r.right, r.top + 25, 5, 5, p);

            p.setColor(Color.rgb(110, 240, 90));
            c.drawRect(r.left + 7, r.top + 5,
                    r.left + 17, r.bottom - 5, p);
        }

        void drawCoin(Canvas c, RectF r) {
            p.setColor(Color.rgb(255, 190, 0));
            c.drawOval(r, p);

            p.setColor(Color.YELLOW);
            c.drawOval(r.left + 5, r.top + 3,
                    r.right - 5, r.bottom - 3, p);
        }

        void drawEnemy(Canvas c, Enemy e) {
            RectF r = e.r;

            p.setColor(Color.rgb(135, 70, 35));
            c.drawOval(r, p);

            p.setColor(Color.WHITE);
            c.drawCircle(r.left + 13, r.top + 13, 6, p);
            c.drawCircle(r.left + 29, r.top + 13, 6, p);

            p.setColor(Color.BLACK);
            c.drawCircle(r.left + 15, r.top + 14, 3, p);
            c.drawCircle(r.left + 31, r.top + 14, 3, p);

            p.setColor(Color.BLACK);
            c.drawLine(r.left + 8, r.bottom - 2,
                    r.left + 34, r.bottom - 2, p);
        }

        void drawFlag(Canvas c) {
            p.setColor(Color.DKGRAY);
            c.drawRect(flagX, groundY - 260, flagX + 9, groundY, p);

            p.setColor(Color.YELLOW);
            c.drawCircle(flagX + 5, groundY - 265, 10, p);

            p.setColor(Color.RED);
            Path flag = new Path();
            flag.moveTo(flagX + 9, groundY - 255);
            flag.lineTo(flagX + 105, groundY - 220);
            flag.lineTo(flagX + 9, groundY - 185);
            flag.close();
            c.drawPath(flag, p);

            p.setColor(Color.WHITE);
            p.setTextSize(22);
            c.drawText("GOAL", flagX + 18, groundY - 218, p);
        }

        void drawHero(Canvas c) {
            float x = heroX - cameraX;
            float y = heroY;

            if (climbingFlag) {
                x = flagX - cameraX - 55;
                y = groundY - heroH - 100 + climbY;
            }

            // Blue body
            p.setColor(Color.rgb(0, 115, 230));
            c.drawOval(x + 5, y + 30, x + 62, y + 76, p);

            // Legs
            p.setColor(Color.BLUE);
            c.drawRoundRect(x + 12, y + 62, x + 28, y + 82, 7, 7, p);
            c.drawRoundRect(x + 39, y + 62, x + 55, y + 82, 7, 7, p);

            // Feet
            p.setColor(Color.WHITE);
            c.drawOval(x + 4, y + 76, x + 32, y + 89, p);
            c.drawOval(x + 35, y + 76, x + 64, y + 89, p);

            // Head
            p.setColor(Color.rgb(0, 110, 225));
            c.drawOval(x + 3, y, x + 66, y + 64, p);

            // Face
            p.setColor(Color.WHITE);
            c.drawOval(x + 10, y + 8, x + 65, y + 59, p);

            // Eyes looking right
            p.setColor(Color.BLACK);
            c.drawOval(x + 30, y + 17, x + 36, y + 29, p);
            c.drawOval(x + 49, y + 17, x + 55, y + 29, p);

            // Nose
            p.setColor(Color.RED);
            c.drawCircle(x + 44, y + 33, 6, p);

            // Mouth and whiskers
            p.setColor(Color.BLACK);
            p.setStrokeWidth(2);
            c.drawLine(x + 44, y + 39, x + 44, y + 49, p);
            c.drawArc(x + 30, y + 39, x + 59, y + 55,
                    10, 160, false, p);

            c.drawLine(x + 12, y + 35, x + 32, y + 38, p);
            c.drawLine(x + 12, y + 44, x + 32, y + 44, p);
            c.drawLine(x + 52, y + 38, x + 65, y + 35, p);
            c.drawLine(x + 52, y + 44, x + 65, y + 44, p);

            // Bell
            p.setColor(Color.YELLOW);
            c.drawCircle(x + 38, y + 65, 8, p);
            p.setColor(Color.BLACK);
            c.drawCircle(x + 38, y + 65, 2, p);
        }

        void drawHUD(Canvas c) {
            p.setColor(Color.BLACK);
            p.setTextSize(23);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText("LEVEL " + level + "/10", 20, 32, p);
            c.drawText("SCORE " + score, 180, 32, p);
            c.drawText("COINS " + collectedCoins, 350, 32, p);
            c.drawText("LIVES " + lives, 500, 32, p);
        }

        void drawControls(Canvas c) {
            float y = screenH - 75;
            float radius = 32;

            drawButton(c, 65, y, radius, "◀");
            drawButton(c, 155, y, radius, "▶");
            drawButton(c, screenW - 155, y, radius, "▼");
            drawButton(c, screenW - 65, y, radius, "JUMP");
        }

        void drawButton(Canvas c, float x, float y,
                        float radius, String label) {

            p.setColor(Color.argb(155, 30, 30, 30));
            c.drawCircle(x, y, radius, p);

            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            c.drawCircle(x, y, radius, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(label.equals("JUMP") ? 13 : 25);
            c.drawText(label, x, y + 8, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawGameOver(Canvas c) {
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.WHITE);
            p.setTextSize(42);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("GAME OVER", screenW / 2, screenH / 2, p);

            p.setTextSize(23);
            c.drawText("Tap to restart", screenW / 2,
                    screenH / 2 + 50, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawLevelComplete(Canvas c) {
            p.setColor(Color.argb(190, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.YELLOW);
            p.setTextSize(38);
            p.setTextAlign(Paint.Align.CENTER);

            if (level >= 10) {
                c.drawText("YOU WIN!", screenW / 2, screenH / 2, p);
                p.setTextSize(22);
                c.drawText("You completed all 10 levels!",
                        screenW / 2, screenH / 2 + 45, p);
            } else {
                c.drawText("LEVEL " + level + " COMPLETE!",
                        screenW / 2, screenH / 2, p);
                p.setTextSize(22);
                c.drawText("Tap to play next level",
                        screenW / 2, screenH / 2 + 45, p);
            }

            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN ||
                    action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_POINTER_UP ||
                    action == MotionEvent.ACTION_UP) {

                if (gameOver || levelComplete) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        if (gameOver) {
                            lives = 3;
                            score = 0;
                            collectedCoins = 0;
                            level = 1;
                            createLevel();
                        } else if (level < 10) {
                            level++;
                            createLevel();
                        } else {
                            level = 1;
                            score = 0;
                            collectedCoins = 0;
                            lives = 3;
                            createLevel();
                        }
                    }
                    invalidate();
                    return true;
                }

                leftPressed = false;
                rightPressed = false;
                downPressed = false;
                boolean jumpNow = false;

                for (int i = 0; i < event.getPointerCount(); i++) {
                    if ((action == MotionEvent.ACTION_POINTER_UP ||
                            action == MotionEvent.ACTION_UP) &&
                            i == event.getActionIndex()) {
                        continue;
                    }

                    float x = event.getX(i);
                    float y = event.getY(i);

                    if (y > screenH - 120) {
                        if (x < screenW * 0.23f) {
                            leftPressed = true;
                        } else if (x < screenW * 0.43f) {
                            rightPressed = true;
                        } else if (x < screenW * 0.70f) {
                            downPressed = true;
                        } else {
                            jumpNow = true;
                        }
                    }
                }

                if (jumpNow) jumpPressed = true;

                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                leftPressed = false;
                rightPressed = false;
                downPressed = false;
                jumpPressed = false;
                return true;
            }

            return true;
        }
    }
}
