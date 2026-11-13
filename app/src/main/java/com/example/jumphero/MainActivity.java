

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
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
    protected void onPause() {
        super.onPause();
        if (game != null) game.stopMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null && game.musicOn) game.startMusic();
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float sw, sh;
        float groundY;
        float worldWidth;
        float cameraX = 0;

        float heroX = 100;
        float heroY;
        float heroW = 68;
        float heroH = 82;
        float velocityY = 0;

        float speed = 6;
        float gravity = 0.85f;
        float jumpPower = -16;

        boolean left = false;
        boolean right = false;
        boolean jump = false;
        boolean grounded = true;

        boolean flagClimbing = false;
        int flagFrames = 0;
        boolean levelWon = false;

        int level = 1;
        int score = 0;

        boolean musicOn = true;
        volatile boolean musicRunning = false;

        ArrayList<Float> coinX = new ArrayList<>();
        ArrayList<Float> coinY = new ArrayList<>();
        ArrayList<Boolean> coinTaken = new ArrayList<>();

        ArrayList<Float> enemyX = new ArrayList<>();
        ArrayList<Float> pipeX = new ArrayList<>();

        float flagX;

        int skyColor = Color.rgb(100, 190, 255);
        int groundColor = Color.rgb(70, 180, 55);

        long lastTime = 0;

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            sw = w;
            sh = h;
            groundY = sh * 0.80f;
            worldWidth = sw * 5.5f;
            createLevel();
            if (musicOn) startMusic();
        }

        void createLevel() {
            heroX = 100;
            heroY = groundY - heroH;
            velocityY = 0;
            cameraX = 0;

            left = false;
            right = false;
            jump = false;
            grounded = true;

            flagClimbing = false;
            flagFrames = 0;
            levelWon = false;

            worldWidth = sw * 5.5f;
            flagX = worldWidth - 170;

            coinX.clear();
            coinY.clear();
            coinTaken.clear();
            enemyX.clear();
            pipeX.clear();

            for (int i = 0; i < 18; i++) {
                float x = 380f + i * 230f;
                coinX.add(x);
                coinY.add(groundY - 160f - (i % 3) * 30f);
                coinTaken.add(false);
            }

            for (int i = 0; i < 8; i++) {
                float x = 700f + i * 500f;
                enemyX.add(x);
            }

            // FIXED: Float values
            for (int i = 0; i < 8; i++) {
                pipeX.add(550f + i * 650f);
            }

            setTheme();
            invalidate();
        }

        void setTheme() {
            switch (level) {
                case 1:
                    skyColor = Color.rgb(100, 190, 255);
                    groundColor = Color.rgb(70, 180, 55);
                    break;
                case 2:
                    skyColor = Color.rgb(255, 190, 100);
                    groundColor = Color.rgb(220, 170, 75);
                    break;
                case 3:
                    skyColor = Color.rgb(150, 210, 255);
                    groundColor = Color.rgb(190, 235, 255);
                    break;
                case 4:
                    skyColor = Color.rgb(80, 180, 220);
                    groundColor = Color.rgb(40, 140, 65);
                    break;
                case 5:
                    skyColor = Color.rgb(30, 130, 220);
                    groundColor = Color.rgb(20, 100, 180);
                    break;
                case 6:
                    skyColor = Color.rgb(100, 45, 50);
                    groundColor = Color.rgb(180, 65, 30);
                    break;
                case 7:
                    skyColor = Color.rgb(25, 25, 75);
                    groundColor = Color.rgb(75, 65, 130);
                    break;
                case 8:
                    skyColor = Color.rgb(255, 170, 210);
                    groundColor = Color.rgb(210, 100, 170);
                    break;
                case 9:
                    skyColor = Color.rgb(100, 170, 255);
                    groundColor = Color.rgb(140, 190, 245);
                    break;
                case 10:
                    skyColor = Color.rgb(65, 75, 120);
                    groundColor = Color.rgb(105, 105, 120);
                    break;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (sw == 0 || sh == 0) return;

            long now = System.currentTimeMillis();
            if (now - lastTime > 15) {
                updateGame();
                lastTime = now;
            }

            canvas.drawColor(skyColor);
            canvas.save();
            canvas.translate(-cameraX, 0);

            drawBackground(canvas);
            drawGround(canvas);
            drawBricks(canvas);
            drawPipes(canvas);
            drawCoins(canvas);
            drawEnemies(canvas);
            drawFlag(canvas);
            drawDoraemon(canvas);

            canvas.restore();

            drawControls(canvas);
            drawInformation(canvas);

            if (levelWon) drawWinMessage(canvas);

            postInvalidateDelayed(16);
        }

        void updateGame() {
            if (levelWon) return;

            if (flagClimbing) {
                flagFrames++;
                heroY -= 2.5f;
                if (flagFrames > 65) levelWon = true;
                return;
            }

            if (right) heroX += speed;
            if (left) heroX -= speed;
            if (heroX < 0) heroX = 0;

            if (jump && grounded) {
                velocityY = jumpPower;
                grounded = false;
            }

            velocityY += gravity;
            heroY += velocityY;

            if (heroY + heroH >= groundY) {
                heroY = groundY - heroH;
                velocityY = 0;
                grounded = true;
            }

            // Pipe collision
            for (float px : pipeX) {
                if (heroX + heroW > px &&
                        heroX < px + 90 &&
                        heroY + heroH > groundY - 90) {
                    if (heroX + heroW - speed <= px) {
                        heroX = px - heroW;
                    } else {
                        heroX = px + 90;
                    }
                }
            }

            // Coin collection
            for (int i = 0; i < coinX.size(); i++) {
                if (!coinTaken.get(i)) {
                    float cx = coinX.get(i);
                    float cy = coinY.get(i);

                    if (heroX + heroW > cx - 18 &&
                            heroX < cx + 18 &&
                            heroY < cy + 18 &&
                            heroY + heroH > cy - 18) {
                        coinTaken.set(i, true);
                        score += 10;
                    }
                }
            }

            // Enemy collision
            for (int i = 0; i < enemyX.size(); i++) {
                float ex = enemyX.get(i);
                if (ex < 0) continue;

                if (Math.abs(heroX - ex) < 45 &&
                        heroY + heroH > groundY - 55) {
                    if (velocityY > 2) {
                        enemyX.set(i, -1000f);
                        velocityY = -10;
                        score += 20;
                    } else {
                        heroX = Math.max(0, heroX - 30);
                    }
                }
            }

            // Reach finish flag
            if (heroX > flagX - 110) {
                flagClimbing = true;
                flagFrames = 0;
                heroX = flagX - heroW + 10;
                right = false;
                left = false;
                jump = false;
            }

            cameraX = heroX - sw * 0.35f;
            if (cameraX < 0) cameraX = 0;

            float maxCamera = worldWidth - sw;
            if (cameraX > maxCamera) cameraX = maxCamera;
        }

        void drawBackground(Canvas c) {
            // Sun or moon
            p.setColor(level == 7 ? Color.YELLOW : Color.rgb(255, 235, 90));
            c.drawCircle(cameraX + sw * 0.82f, 85, 38, p);

            // Clouds
            for (int i = -2; i < 30; i++) {
                float x = i * 330f + 100f;
                drawCloud(c, x, 70 + (i % 3) * 45);
            }

            // Distant hills
            for (int i = -2; i < 30; i++) {
                float x = i * 360f;
                p.setColor(level == 7 ?
                        Color.rgb(45, 45, 100) :
                        Color.rgb(90, 175, 100));
                c.drawOval(x, groundY - 160,
                        x + 430, groundY + 40, p);
            }

            // Trees and level scenery across the whole world
            for (int i = 0; i < 35; i++) {
                float x = 150 + i * 300f;

                if (level == 2) drawCactus(c, x, groundY);
                else if (level == 3) drawSnowTree(c, x, groundY);
                else if (level == 5) drawCoral(c, x, groundY);
                else if (level == 6) drawFire(c, x, groundY);
                else if (level == 8) drawCandyTree(c, x, groundY);
                else drawTree(c, x, groundY);
            }

            if (level == 10) {
                for (int i = 0; i < 4; i++) {
                    drawCastle(c, 900 + i * 700f, groundY);
                }
            }

            if (level == 9) {
                for (int i = 0; i < 20; i++) {
                    float x = 250 + i * 300f;
                    p.setColor(Color.WHITE);
                    c.drawOval(x, groundY - 260,
                            x + 180, groundY - 215, p);
                }
            }
        }

        void drawCloud(Canvas c, float x, float y) {
            p.setColor(Color.WHITE);
            c.drawCircle(x, y, 22, p);
            c.drawCircle(x + 28, y - 12, 27, p);
            c.drawCircle(x + 58, y, 22, p);
            c.drawRect(x, y, x + 58, y + 18, p);
        }

        void drawTree(Canvas c, float x, float base) {
            p.setColor(Color.rgb(110, 65, 30));
            c.drawRect(x, base - 110, x + 22, base, p);
            p.setColor(Color.rgb(20, 130, 35));
            c.drawCircle(x + 10, base - 130, 43, p);
            c.drawCircle(x - 18, base - 105, 30, p);
            c.drawCircle(x + 40, base - 105, 30, p);
        }

        void drawCactus(Canvas c, float x, float base) {
            p.setColor(Color.rgb(35, 145, 50));
            c.drawRoundRect(x, base - 110, x + 28, base, 12, 12, p);
            c.drawRoundRect(x - 25, base - 75, x, base - 55, 10, 10, p);
            c.drawRoundRect(x - 25, base - 85, x - 8, base - 45, 8, 8, p);
            c.drawRoundRect(x + 28, base - 90, x + 50, base - 70, 10, 10, p);
        }

        void drawSnowTree(Canvas c, float x, float base) {
            p.setColor(Color.rgb(100, 65, 35));
            c.drawRect(x, base - 100, x + 18, base, p);
            p.setColor(Color.WHITE);

            for (int i = 0; i < 3; i++) {
                float y = base - 110 - i * 35;
                Path path = new Path();
                path.moveTo(x - 35, y + 35);
                path.lineTo(x + 9, y - 30);
                path.lineTo(x + 53, y + 35);
                path.close();
                c.drawPath(path, p);
            }
        }

        void drawCoral(Canvas c, float x, float base) {
            p.setColor(Color.rgb(255, 110, 130));
            c.drawRoundRect(x, base - 100, x + 20, base, 10, 10, p);
            c.drawCircle(x + 10, base - 105, 25, p);
            p.setColor(Color.YELLOW);
            c.drawCircle(x + 45, base - 60, 13, p);
        }

        void drawFire(Canvas c, float x, float base) {
            p.setColor(Color.rgb(255, 100, 20));
            c.drawOval(x, base - 70, x + 45, base, p);
            p.setColor(Color.YELLOW);
            c.drawOval(x + 12, base - 50, x + 32, base, p);
        }

        void drawCandyTree(Canvas c, float x, float base) {
            p.setColor(Color.rgb(120, 75, 35));
            c.drawRect(x, base - 100, x + 16, base, p);
            p.setColor(Color.MAGENTA);
            c.drawCircle(x + 8, base - 120, 38, p);
            p.setColor(Color.WHITE);
            c.drawCircle(x - 5, base - 130, 9, p);
            c.drawCircle(x + 20, base - 110, 9, p);
        }

        void drawCastle(Canvas c, float x, float base) {
            p.setColor(Color.rgb(150, 150, 170));
            c.drawRect(x, base - 180, x + 210, base, p);
            c.drawRect(x - 30, base - 220, x + 40, base, p);
            c.drawRect(x + 170, base - 220, x + 240, base, p);
            p.setColor(Color.rgb(90, 90, 120));
            c.drawRect(x + 75, base - 90, x + 135, base, p);
            p.setColor(Color.RED);
            c.drawRect(x + 10, base - 250, x + 55, base - 220, p);
            c.drawRect(x + 190, base - 250, x + 235, base - 220, p);
        }

        void drawGround(Canvas c) {
            p.setColor(groundColor);
            c.drawRect(0, groundY, worldWidth, sh, p);
            p.setColor(Color.rgb(80, 45, 25));
            c.drawRect(0, groundY + 24, worldWidth, sh, p);
            p.setColor(Color.rgb(40, 130, 35));
            c.drawRect(0, groundY, worldWidth, groundY + 12, p);

            for (int i = 0; i < 100; i++) {
                float x = i * 110f;
                p.setColor(Color.rgb(110, 75, 35));
                c.drawRect(x, groundY + 40, x + 60, groundY + 45, p);
            }
        }

        void drawBricks(Canvas c) {
            // Brick rows
            for (int i = 0; i < 40; i++) {
                float x = 300 + i * 140f;
                float y = groundY - 210 - (i % 3) * 25f;
                drawBrick(c, x, y);
            }

            // Question blocks
            for (int i = 0; i < 12; i++) {
                float x = 450 + i * 380f;
                float y = groundY - 300;
                drawQuestionBlock(c, x, y);
            }

            // Floating brick platforms
            for (int i = 0; i < 8; i++) {
                float x = 800 + i * 580f;
                float y = groundY - 135;
                for (int j = 0; j < 4; j++) {
                    drawBrick(c, x + j * 45f, y);
                }
            }
        }

        void drawBrick(Canvas c, float x, float y) {
            p.setColor(Color.rgb(175, 75, 30));
            c.drawRect(x, y, x + 44, y + 40, p);
            p.setColor(Color.rgb(245, 150, 65));
            c.drawRect(x + 3, y + 3, x + 41, y + 18, p);
            p.setColor(Color.rgb(80, 35, 20));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            c.drawRect(x, y, x + 44, y + 40, p);
            c.drawLine(x + 22, y, x + 22, y + 40, p);
            c.drawLine(x, y + 20, x + 44, y + 20, p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawQuestionBlock(Canvas c, float x, float y) {
            p.setColor(Color.rgb(255, 190, 30));
            c.drawRoundRect(x, y, x + 48, y + 48, 5, 5, p);
            p.setColor(Color.rgb(150, 75, 10));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            c.drawRect(x, y, x + 48, y + 48, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTextSize(34);
            p.setFakeBoldText(true);
            c.drawText("?", x + 12, y + 36, p);
            p.setFakeBoldText(false);
        }

        void drawPipes(Canvas c) {
            for (float x : pipeX) {
                p.setColor(Color.rgb(0, 135, 30));
                c.drawRect(x, groundY - 95, x + 90, groundY, p);
                p.setColor(Color.rgb(30, 220, 50));
                c.drawRect(x + 10, groundY - 85, x + 30, groundY - 5, p);
                p.setColor(Color.rgb(0, 100, 20));
                c.drawRect(x - 8, groundY - 110, x + 98, groundY - 88, p);
                p.setColor(Color.rgb(50, 240, 60));
                c.drawRect(x, groundY - 110, x + 15, groundY - 90, p);
            }
        }

        void drawCoins(Canvas c) {
            for (int i = 0; i < coinX.size(); i++) {
                if (coinTaken.get(i)) continue;
                float x = coinX.get(i);
                float y = coinY.get(i);

                p.setColor(Color.rgb(255, 205, 0));
                c.drawCircle(x, y, 17, p);
                p.setColor(Color.rgb(255, 245, 100));
                c.drawCircle(x, y, 11, p);
                p.setColor(Color.rgb(180, 115, 0));
                p.setTextSize(19);
                c.drawText("$", x - 6, y + 7, p);
            }
        }

        void drawEnemies(Canvas c) {
            for (float x : enemyX) {
                if (x < 0) continue;
                p.setColor(Color.rgb(140, 50, 30));
                c.drawOval(x, groundY - 48, x + 48, groundY, p);
                p.setColor(Color.WHITE);
                c.drawCircle(x + 14, groundY - 30, 7, p);
                c.drawCircle(x + 33, groundY - 30, 7, p);
                p.setColor(Color.BLACK);
                c.drawCircle(x + 16, groundY - 30, 3, p);
                c.drawCircle(x + 35, groundY - 30, 3, p);
            }
        }

        void drawFlag(Canvas c) {
            p.setColor(Color.DKGRAY);
            c.drawRect(flagX, groundY - 270, flagX + 10, groundY, p);
            p.setColor(Color.YELLOW);
            c.drawCircle(flagX + 5, groundY - 275, 12, p);
            p.setColor(Color.rgb(30, 200, 50));
            c.drawRect(flagX + 10, groundY - 265,
                    flagX + 110, groundY - 205, p);
            p.setColor(Color.WHITE);
            p.setTextSize(28);
            c.drawText("GO!", flagX + 28, groundY - 225, p);
        }

        void drawDoraemon(Canvas c) {
            float x = heroX;
            float y = heroY;

            // Feet
            p.setColor(Color.WHITE);
            c.drawOval(x + 5, y + 68, x + 32, y + 83, p);
            c.drawOval(x + 38, y + 68, x + 65, y + 83, p);

            // Blue body
            p.setColor(Color.rgb(0, 115, 220));
            c.drawOval(x + 3, y + 12, x + 64, y + 78, p);

            // Belly
            p.setColor(Color.WHITE);
            c.drawOval(x + 15, y + 42, x + 55, y + 75, p);

            // Head
            p.setColor(Color.rgb(0, 145, 235));
            c.drawOval(x, y, x + 68, y + 65, p);

            // Face
            p.setColor(Color.WHITE);
            c.drawOval(x + 12, y + 12, x + 61, y + 59, p);

            // Eyes facing right
            p.setColor(Color.WHITE);
            c.drawOval(x + 36, y + 7, x + 51, y + 30, p);
            c.drawOval(x + 51, y + 9, x + 63, y + 29, p);
            p.setColor(Color.BLACK);
            c.drawCircle(x + 46, y + 20, 3, p);
            c.drawCircle(x + 58, y + 20, 3, p);

            // Nose
            p.setColor(Color.RED);
            c.drawCircle(x + 56, y + 34, 7, p);
            p.setColor(Color.WHITE);
            c.drawCircle(x + 58, y + 31, 2, p);

            // Mouth
            p.setColor(Color.BLACK);
            p.setStrokeWidth(2);
            c.drawLine(x + 40, y + 43, x + 61, y + 43, p);

            // Whiskers
            p.setStrokeWidth(2);
            c.drawLine(x + 13, y + 32, x + 34, y + 36, p);
            c.drawLine(x + 12, y + 40, x + 33, y + 40, p);
            c.drawLine(x + 14, y + 48, x + 34, y + 44, p);
            c.drawLine(x + 58, y + 36, x + 68, y + 31, p);
            c.drawLine(x + 58, y + 42, x + 69, y + 42, p);
            c.drawLine(x + 57, y + 47, x + 68, y + 52, p);

            // Collar and bell
            p.setColor(Color.RED);
            c.drawRect(x + 16, y + 62, x + 56, y + 68, p);
            p.setColor(Color.YELLOW);
            c.drawCircle(x + 37, y + 69, 7, p);
            p.setColor(Color.BLACK);
            c.drawCircle(x + 37, y + 69, 2, p);
        }

        void drawControls(Canvas c) {
            drawButton(c, 80, sh - 85, "◀");
            drawButton(c, 220, sh - 85, "▶");
            drawButton(c, sw - 95, sh - 85, "JUMP");
        }

        void drawButton(Canvas c, float x, float y, String text) {
            p.setColor(Color.argb(155, 20, 20, 20));
            c.drawCircle(x, y, 64, p);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            c.drawCircle(x, y, 64, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(text.equals("JUMP") ? 20 : 42);
            p.setFakeBoldText(true);
            c.drawText(text, x, y + 12, p);
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawInformation(Canvas c) {
            p.setColor(Color.argb(180, 0, 0, 0));
            c.drawRoundRect(15, 12, 390, 65, 15, 15, p);
            p.setColor(Color.WHITE);
            p.setTextSize(24);
            p.setFakeBoldText(true);
            c.drawText("LEVEL " + level + "/10", 30, 46, p);
            c.drawText("SCORE: " + score, 175, 46, p);
            p.setFakeBoldText(false);

            p.setColor(Color.argb(190, 0, 0, 0));
            c.drawCircle(sw - 45, 40, 28, p);
            p.setColor(Color.WHITE);
            p.setTextSize(18);
            c.drawText(musicOn ? "♪" : "X", sw - 53, 47, p);
        }

        void drawWinMessage(Canvas c) {
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRoundRect(sw / 2 - 190, sh / 2 - 75,
                    sw / 2 + 190, sh / 2 + 75, 20, 20, p);
            p.setColor(Color.YELLOW);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(32);
            p.setFakeBoldText(true);

            if (level == 10) {
                c.drawText("YOU WIN!", sw / 2, sh / 2 - 5, p);
                p.setTextSize(19);
                c.drawText("Tap to play again", sw / 2, sh / 2 + 35, p);
            } else {
                c.drawText("LEVEL COMPLETED!", sw / 2, sh / 2 - 5, p);
                p.setTextSize(19);
                c.drawText("Tap for next level", sw / 2, sh / 2 + 35, p);
            }

            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (levelWon && action == MotionEvent.ACTION_DOWN) {
                if (level < 10) {
                    level++;
                } else {
                    level = 1;
                    score = 0;
                }
                createLevel();
                return true;
            }

            if (action == MotionEvent.ACTION_DOWN) {
                float tx = event.getX();
                float ty = event.getY();

                if (tx > sw - 85 && ty < 85) {
                    musicOn = !musicOn;
                    if (musicOn) startMusic();
                    else stopMusic();
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                left = false;
                right = false;
                jump = false;
                return true;
            }

            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN ||
                    action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_POINTER_UP ||
                    action == MotionEvent.ACTION_UP) {
                updateTouchControls(event);
            }

            return true;
        }

        void updateTouchControls(MotionEvent event) {
            left = false;
            right = false;
            jump = false;

            if (event.getActionMasked() == MotionEvent.ACTION_UP) return;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP &&
                        i == event.getActionIndex()) continue;

                float x = event.getX(i);
                float y = event.getY(i);

                if (y > sh - 175) {
                    if (x < 155) {
                        left = true;
                    } else if (x < 300) {
                        right = true;
                    } else if (x > sw - 185) {
                        jump = true;
                    }
                }
            }
        }

        void startMusic() {
            if (musicRunning || !musicOn) return;

            musicRunning = true;

            new Thread(() -> {
                int sampleRate = 22050;
                int bufferSize = 1024;
                AudioTrack track = null;

                try {
                    track = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                    );

                    track.play();
                    short[] buffer = new short[bufferSize];
                    double phase = 0;
                    int note = 0;

                    double[] melody = {
                            523.25, 659.25, 783.99, 659.25,
                            587.33, 698.46, 880.00, 698.46,
                            523.25, 587.33, 659.25, 783.99
                    };

                    while (musicRunning) {
                        double frequency = melody[note];

                        for (int i = 0; i < bufferSize; i++) {
                            buffer[i] = (short)(Math.sin(phase) * 2500);
                            phase += 2 * Math.PI * frequency / sampleRate;
                            if (phase > 2 * Math.PI) {
                                phase -= 2 * Math.PI;
                            }
                        }

                        track.write(buffer, 0, buffer.length);
                        note = (note + 1) % melody.length;
                    }

                } catch (Exception e) {
                    // Game continues if audio is unavailable.
                } finally {
                    if (track != null) {
                        try {
                            track.stop();
                            track.release();
                        } catch (Exception ignored) {
                        }
                    }
                    musicRunning = false;
                }
            }).start();
        }

        void stopMusic() {
            musicRunning = false;
        }
    }
}
