
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioManager;

import java.util.ArrayList;

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
        if (gameView != null) gameView.stopMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null && gameView.musicOn &&
                !gameView.flagClimbing && !gameView.levelComplete) {
            gameView.startMusic();
        }
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) gameView.stopMusic();
        super.onDestroy();
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float sw, sh;
        float groundY;
        float worldWidth = 5200f;
        float cameraX = 0;

        float heroX = 100;
        float heroY;
        float heroW = 65;
        float heroH = 85;

        float velocityY = 0;
        float gravity = 0.75f;
        float jumpPower = -16f;

        float speed = 6f;
        float normalSpeed = 6f;
        float turboSpeed = 11f;

        boolean left = false;
        boolean right = false;
        boolean down = false;
        boolean jump = false;
        boolean turboMode = false;

        boolean onGround = true;
        boolean flagClimbing = false;
        boolean levelComplete = false;
        boolean victoryMusicPlayed = false;

        boolean musicOn = true;
        boolean jumpWasPressed = false;
        boolean downWasPressed = false;

        long levelCompleteTime = 0;

        int level = 1;
        int score = 0;
        int lives = 3;

        float flagX;

        ArrayList<Float> brickX = new ArrayList<>();
        ArrayList<Float> brickY = new ArrayList<>();
        ArrayList<Boolean> brickBroken = new ArrayList<>();

        ArrayList<Float> pipeX = new ArrayList<>();

        ArrayList<Float> coinX = new ArrayList<>();
        ArrayList<Float> coinY = new ArrayList<>();
        ArrayList<Boolean> coinTaken = new ArrayList<>();

        ArrayList<Float> enemyX = new ArrayList<>();
        ArrayList<Float> enemyY = new ArrayList<>();
        ArrayList<Boolean> enemyAlive = new ArrayList<>();

        AudioTrack musicTrack;
        AudioTrack effectTrack;

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setupSize(float width, float height) {
            if (sw == width && sh == height) return;

            sw = width;
            sh = height;

            groundY = sh - 100;
            heroY = groundY - heroH;

            createLevel();
        }

        void createLevel() {

            worldWidth = 5200f + (level - 1) * 450f;
            flagX = worldWidth - 250;

            heroX = 100;
            heroY = groundY - heroH;
            velocityY = 0;
            cameraX = 0;

            left = false;
            right = false;
            down = false;
            jump = false;
            turboMode = false;

            flagClimbing = false;
            levelComplete = false;
            victoryMusicPlayed = false;

            jumpWasPressed = false;
            downWasPressed = false;

            brickX.clear();
            brickY.clear();
            brickBroken.clear();

            pipeX.clear();

            coinX.clear();
            coinY.clear();
            coinTaken.clear();

            enemyX.clear();
            enemyY.clear();
            enemyAlive.clear();

            for (int i = 0; i < 35 + level * 2; i++) {
                float x = 350 + i * 125f;
                float y = groundY - 210 - (i % 3) * 25f;

                brickX.add(x);
                brickY.add(y);
                brickBroken.add(false);
            }

            for (int i = 0; i < 7; i++) {
                pipeX.add(700f + i * 650f);
            }

            for (int i = 0; i < 25; i++) {
                coinX.add(450f + i * 180f);
                coinY.add(groundY - 160f - (i % 3) * 55f);
                coinTaken.add(false);
            }

            for (int i = 0; i < 9; i++) {
                enemyX.add(900f + i * 480f);
                enemyY.add(groundY - 42f);
                enemyAlive.add(true);
            }

            if (musicOn) startMusic();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            setupSize(getWidth(), getHeight());

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

            drawInformation(canvas);
            drawControls(canvas);

            if (levelComplete) {
                drawLevelComplete(canvas);
            }

            if (!levelComplete) {
                updateGame();
            }

            postInvalidateDelayed(16);
        }

        void updateGame() {

            if (flagClimbing) {
                heroY -= 1.8f;

                if (heroY < groundY - heroH - 110) {
                    heroY = groundY - heroH - 110;
                    flagClimbing = false;
                    levelComplete = true;
                    levelCompleteTime = System.currentTimeMillis();
                }
                return;
            }

            speed = turboMode ? turboSpeed : normalSpeed;

            if (right) heroX += speed;
            if (left) heroX -= normalSpeed;

            if (heroX < 0) heroX = 0;
            if (heroX > worldWidth - heroW) {
                heroX = worldWidth - heroW;
            }

            if (jump && !jumpWasPressed && onGround) {
                velocityY = jumpPower;
                onGround = false;
            }

            jumpWasPressed = jump;

            float previousHeroY = heroY;

            velocityY += gravity;
            if (velocityY > 18) velocityY = 18;

            heroY += velocityY;

            checkBrickCollision(previousHeroY);

            if (heroY + heroH >= groundY) {
                heroY = groundY - heroH;
                velocityY = 0;
                onGround = true;
            } else {
                onGround = false;
            }

            checkPipeEntry();

            collectCoins();
            updateEnemies();

            if (heroX > flagX - 100 && !flagClimbing) {
                reachFlag();
            }

            float targetCamera = heroX - sw * 0.35f;

            if (targetCamera < 0) targetCamera = 0;
            if (targetCamera > worldWidth - sw) {
                targetCamera = worldWidth - sw;
            }

            cameraX += (targetCamera - cameraX) * 0.12f;
        }

        void checkBrickCollision(float previousHeroY) {

            if (velocityY >= 0) return;

            for (int i = 0; i < brickX.size(); i++) {

                if (brickBroken.get(i)) continue;

                float bx = brickX.get(i);
                float by = brickY.get(i);

                boolean horizontal =
                        heroX + heroW > bx &&
                        heroX < bx + 44;

                boolean hitFromBelow =
                        previousHeroY >= by + 40 &&
                        heroY <= by + 40;

                if (horizontal && hitFromBelow) {

                    brickBroken.set(i, true);

                    heroY = by + 40;
                    velocityY = 2f;

                    score += 5;

                    playBrickSound();
                    break;
                }
            }
        }

        void checkPipeEntry() {

            if (!down || downWasPressed) {
                downWasPressed = down;
                return;
            }

            downWasPressed = true;

            if (!onGround) return;

            for (int i = 0; i < pipeX.size(); i++) {

                float px = pipeX.get(i);

                boolean nearPipe =
                        heroX + heroW > px &&
                        heroX < px + 90;

                if (nearPipe) {

                    float destination;

                    if (i + 1 < pipeX.size()) {
                        destination = pipeX.get(i + 1) + 100;
                    } else {
                        destination = px + 550;
                    }

                    heroX = Math.min(destination, flagX - 180);
                    heroY = groundY - heroH;
                    velocityY = 0;

                    cameraX = Math.max(0, heroX - sw * 0.35f);

                    break;
                }
            }
        }

        void collectCoins() {

            for (int i = 0; i < coinX.size(); i++) {

                if (coinTaken.get(i)) continue;

                float cx = coinX.get(i);
                float cy = coinY.get(i);

                if (heroX + heroW > cx &&
                        heroX < cx + 28 &&
                        heroY + heroH > cy &&
                        heroY < cy + 28) {

                    coinTaken.set(i, true);
                    score += 10;
                }
            }
        }

        void updateEnemies() {

            for (int i = 0; i < enemyX.size(); i++) {

                if (!enemyAlive.get(i)) continue;

                float ex = enemyX.get(i);
                float ey = enemyY.get(i);

                if (heroX + heroW > ex &&
                        heroX < ex + 45 &&
                        heroY + heroH > ey &&
                        heroY < ey + 40) {

                    if (velocityY > 1 && heroY + heroH < ey + 25) {
                        enemyAlive.set(i, false);
                        velocityY = -10;
                        score += 20;
                    } else {
                        lives--;

                        heroX = Math.max(0, heroX - 150);
                        heroY = groundY - heroH;
                        velocityY = 0;

                        if (lives <= 0) {
                            lives = 3;
                            score = Math.max(0, score - 30);
                            createLevel();
                        }
                    }
                }
            }
        }

        void reachFlag() {

            flagClimbing = true;
            heroX = flagX - heroW + 10;

            right = false;
            left = false;
            jump = false;
            down = false;
            turboMode = false;

            if (!victoryMusicPlayed) {
                victoryMusicPlayed = true;
                stopMusic();

                if (musicOn) {
                    playVictorySound();
                }
            }
        }

        void drawBackground(Canvas c) {

            int sky;

            switch ((level - 1) % 10) {
                case 1: sky = Color.rgb(245, 190, 100); break;
                case 2: sky = Color.rgb(190, 225, 250); break;
                case 3: sky = Color.rgb(120, 200, 170); break;
                case 4: sky = Color.rgb(70, 160, 210); break;
                case 5: sky = Color.rgb(255, 150, 90); break;
                case 6: sky = Color.rgb(35, 40, 90); break;
                case 7: sky = Color.rgb(230, 160, 220); break;
                case 8: sky = Color.rgb(100, 170, 230); break;
                case 9: sky = Color.rgb(170, 190, 250); break;
                default: sky = Color.rgb(100, 190, 245); break;
            }

            c.drawColor(sky);

            p.setColor(Color.YELLOW);
            c.drawCircle(cameraX + sw - 100, 90, 42, p);

            for (int i = 0; i < 15; i++) {

                float x = i * 430f;

                p.setColor(Color.WHITE);
                c.drawOval(x + 30, 80, x + 130, 120, p);
                c.drawOval(x + 75, 55, x + 160, 115, p);
                c.drawOval(x + 120, 80, x + 205, 120, p);
            }

            for (int i = 0; i < 30; i++) {

                float x = i * 190f;

                if (level % 3 == 1) {
                    p.setColor(Color.rgb(70, 170, 75));
                    c.drawCircle(x, groundY - 40, 90, p);
                    c.drawCircle(x + 90, groundY - 30, 70, p);
                } else if (level % 3 == 2) {
                    p.setColor(Color.rgb(190, 150, 85));
                    c.drawRect(x, groundY - 100, x + 45, groundY, p);
                    c.drawCircle(x + 20, groundY - 110, 48, p);
                } else {
                    p.setColor(Color.rgb(40, 130, 80));
                    c.drawRect(x + 25, groundY - 125, x + 45, groundY, p);
                    c.drawCircle(x + 35, groundY - 140, 65, p);
                }
            }

            drawThemeDetails(c);
        }

        void drawThemeDetails(Canvas c) {

            for (int i = 0; i < 18; i++) {

                float x = 180 + i * 300f;

                if (level == 2) {
                    p.setColor(Color.rgb(30, 160, 50));
                    c.drawRect(x, groundY - 120, x + 20, groundY, p);
                    c.drawCircle(x + 10, groundY - 130, 35, p);
                }

                if (level == 3) {
                    p.setColor(Color.WHITE);
                    c.drawCircle(x, groundY - 100, 35, p);
                    c.drawCircle(x + 25, groundY - 120, 35, p);
                }

                if (level == 4) {
                    p.setColor(Color.CYAN);
                    c.drawCircle(x, groundY - 80, 20, p);
                    c.drawCircle(x + 30, groundY - 110, 20, p);
                }

                if (level == 5) {
                    p.setColor(Color.RED);
                    c.drawCircle(x, groundY - 90, 25, p);
                    p.setColor(Color.YELLOW);
                    c.drawCircle(x + 30, groundY - 120, 20, p);
                }

                if (level == 6) {
                    p.setColor(Color.YELLOW);
                    c.drawCircle(x, groundY - 130, 4, p);
                    c.drawCircle(x + 35, groundY - 160, 4, p);
                }

                if (level >= 7) {
                    p.setColor(Color.MAGENTA);
                    c.drawCircle(x, groundY - 90, 24, p);
                    p.setColor(Color.WHITE);
                    c.drawCircle(x + 20, groundY - 115, 15, p);
                }
            }
        }

        void drawGround(Canvas c) {

            p.setColor(Color.rgb(115, 70, 35));
            c.drawRect(0, groundY, worldWidth, sh, p);

            p.setColor(Color.rgb(50, 180, 55));
            c.drawRect(0, groundY, worldWidth, groundY + 18, p);

            p.setColor(Color.rgb(80, 55, 30));

            for (int i = 0; i < worldWidth / 80; i++) {
                float x = i * 80f;
                c.drawRect(x, groundY + 35, x + 45, groundY + 42, p);
            }
        }

        void drawBricks(Canvas c) {

            for (int i = 0; i < brickX.size(); i++) {

                if (brickBroken.get(i)) continue;

                drawBrick(c, brickX.get(i), brickY.get(i));
            }

            for (int i = 0; i < 12; i++) {

                float x = 500 + i * 350f;
                float y = groundY - 300 - (i % 2) * 35;

                p.setColor(Color.rgb(180, 80, 30));
                c.drawRect(x, y, x + 45, y + 40, p);

                p.setColor(Color.YELLOW);
                c.drawRect(x + 8, y + 8, x + 37, y + 33, p);

                p.setColor(Color.rgb(110, 55, 20));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawRect(x, y, x + 45, y + 40, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawBrick(Canvas c, float x, float y) {

            p.setColor(Color.rgb(175, 75, 25));
            c.drawRect(x, y, x + 44, y + 40, p);

            p.setColor(Color.rgb(240, 145, 70));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            c.drawRect(x, y, x + 44, y + 40, p);
            c.drawLine(x, y + 20, x + 44, y + 20, p);
            c.drawLine(x + 22, y, x + 22, y + 20, p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawPipes(Canvas c) {

            for (float x : pipeX) {

                p.setColor(Color.rgb(0, 120, 20));
                c.drawRect(x, groundY - 95, x + 80, groundY, p);

                p.setColor(Color.rgb(0, 190, 40));
                c.drawRect(x - 10, groundY - 105, x + 90, groundY - 85, p);

                p.setColor(Color.rgb(0, 75, 15));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4);
                c.drawRect(x, groundY - 95, x + 80, groundY, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawCoins(Canvas c) {

            for (int i = 0; i < coinX.size(); i++) {

                if (coinTaken.get(i)) continue;

                p.setColor(Color.YELLOW);
                c.drawOval(
                        coinX.get(i), coinY.get(i),
                        coinX.get(i) + 27, coinY.get(i) + 32, p
                );

                p.setColor(Color.rgb(220, 140, 0));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawOval(
                        coinX.get(i), coinY.get(i),
                        coinX.get(i) + 27, coinY.get(i) + 32, p
                );
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawEnemies(Canvas c) {

            for (int i = 0; i < enemyX.size(); i++) {

                if (!enemyAlive.get(i)) continue;

                float x = enemyX.get(i);
                float y = enemyY.get(i);

                p.setColor(Color.rgb(130, 70, 35));
                c.drawOval(x, y, x + 45, y + 40, p);

                p.setColor(Color.WHITE);
                c.drawCircle(x + 13, y + 14, 7, p);
                c.drawCircle(x + 32, y + 14, 7, p);

                p.setColor(Color.BLACK);
                c.drawCircle(x + 15, y + 15, 3, p);
                c.drawCircle(x + 33, y + 15, 3, p);

                p.setColor(Color.BLACK);
                c.drawRect(x + 5, y + 35, x + 18, y + 42, p);
                c.drawRect(x + 28, y + 35, x + 41, y + 42, p);
            }
        }

        void drawFlag(Canvas c) {

            p.setColor(Color.DKGRAY);
            c.drawRect(flagX, groundY - 290, flagX + 9, groundY, p);

            p.setColor(Color.YELLOW);
            c.drawCircle(flagX + 4, groundY - 295, 12, p);

            p.setColor(Color.RED);

            Path flag = new Path();
            flag.moveTo(flagX + 9, groundY - 285);
            flag.lineTo(flagX + 115, groundY - 250);
            flag.lineTo(flagX + 9, groundY - 215);
            flag.close();

            c.drawPath(flag, p);

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            c.drawText("FINISH", flagX + 20, groundY - 248, p);
        }

        void drawDoraemon(Canvas c) {

            float x = heroX;
            float y = heroY;

            // Body
            p.setColor(Color.rgb(0, 145, 225));
            c.drawOval(x + 5, y + 35, x + 63, y + 85, p);

            // Face
            p.setColor(Color.rgb(0, 150, 235));
            c.drawCircle(x + 34, y + 27, 34, p);

            // White face
            p.setColor(Color.WHITE);
            c.drawOval(x + 8, y + 13, x + 61, y + 58, p);

            // Eyes
            p.setColor(Color.WHITE);
            c.drawOval(x + 31, y + 1, x + 45, y + 23, p);
            c.drawOval(x + 44, y + 1, x + 58, y + 23, p);

            p.setColor(Color.BLACK);
            c.drawCircle(x + 40, y + 13, 3, p);
            c.drawCircle(x + 52, y + 13, 3, p);

            // Nose
            p.setColor(Color.RED);
            c.drawCircle(x + 56, y + 27, 7, p);

            // Nose line
            p.setColor(Color.BLACK);
            p.setStrokeWidth(2);
            c.drawLine(x + 55, y + 34, x + 55, y + 45, p);

            // Whiskers
            p.setStrokeWidth(2);
            c.drawLine(x + 12, y + 27, x + 32, y + 30, p);
            c.drawLine(x + 10, y + 37, x + 32, y + 37, p);
            c.drawLine(x + 12, y + 46, x + 32, y + 42, p);

            // Mouth
            c.drawArc(x + 23, y + 31, x + 57, y + 57,
                    15, 150, false, p);

            // Collar
            p.setColor(Color.RED);
            c.drawRect(x + 8, y + 55, x + 61, y + 63, p);

            // Bell
            p.setColor(Color.YELLOW);
            c.drawCircle(x + 36, y + 65, 8, p);

            p.setColor(Color.rgb(150, 100, 0));
            c.drawCircle(x + 36, y + 65, 3, p);

            // Feet
            p.setColor(Color.WHITE);
            c.drawOval(x + 3, y + 76, x + 32, y + 88, p);
            c.drawOval(x + 35, y + 76, x + 66, y + 88, p);

            // Pocket
            p.setColor(Color.WHITE);
            c.drawOval(x + 20, y + 68, x + 51, y + 84, p);
        }

        void drawInformation(Canvas c) {

            p.setColor(Color.BLACK);
            p.setTextSize(24);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText("LEVEL " + level + "/10", 20, 35, p);
            c.drawText("SCORE " + score, 190, 35, p);
            c.drawText("LIVES " + lives, 370, 35, p);

            p.setColor(musicOn ? Color.GREEN : Color.RED);
            c.drawCircle(sw - 35, 35, 15, p);

            p.setColor(Color.BLACK);
            p.setTextSize(16);
            c.drawText("MUSIC", sw - 100, 70, p);

            if (turboMode) {
                p.setColor(Color.RED);
                p.setTextSize(26);
                c.drawText("TURBO!", sw / 2 - 50, 50, p);
            }
        }

        void drawControls(Canvas c) {

            float y = sh - 75;
            float r = 48;

            drawButton(c, 75, y, r, "LEFT");
            drawButton(c, sw * 0.22f, y, r, "RUN");
            drawButton(c, sw * 0.40f, y, r, "DOWN");
            drawButton(c, sw - 85, y, r, "JUMP");

            if (turboMode) {
                p.setColor(Color.YELLOW);
                p.setTextSize(18);
                c.drawText("FAST", sw * 0.22f - 22, y - 55, p);
            }
        }

        void drawButton(Canvas c, float x, float y,
                        float radius, String label) {

            p.setColor(Color.argb(180, 20, 30, 40));
            c.drawCircle(x, y, radius, p);

            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            c.drawCircle(x, y, radius, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextSize(15);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            float textWidth = p.measureText(label);
            c.drawText(label, x - textWidth / 2, y + 5, p);
        }

        void drawLevelComplete(Canvas c) {

            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRoundRect(
                    new RectF(sw / 2 - 190, sh / 2 - 100,
                            sw / 2 + 190, sh / 2 + 100),
                    25, 25, p
            );

            p.setColor(Color.YELLOW);
            p.setTextSize(34);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("LEVEL COMPLETE!",
                    sw / 2 - 155, sh / 2 - 30, p);

            p.setColor(Color.WHITE);
            p.setTextSize(23);

            if (level >= 10) {
                c.drawText("YOU WIN!", sw / 2 - 65, sh / 2 + 15, p);
            } else {
                c.drawText("Tap to play next level",
                        sw / 2 - 125, sh / 2 + 15, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            int action = event.getActionMasked();

            if (levelComplete) {

                if (action == MotionEvent.ACTION_DOWN) {

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

                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;
                down = false;
                jump = false;
                turboMode = false;

                return true;
            }

            left = false;
            right = false;
            down = false;
            jump = false;
            turboMode = false;

            for (int i = 0; i < event.getPointerCount(); i++) {

                if (action == MotionEvent.ACTION_POINTER_UP &&
                        i == event.getActionIndex()) {
                    continue;
                }

                float x = event.getX(i);
                float y = event.getY(i);

                // Music toggle
                if (action == MotionEvent.ACTION_DOWN &&
                        x > sw - 75 && y < 85) {

                    musicOn = !musicOn;

                    if (musicOn && !flagClimbing && !levelComplete) {
                        startMusic();
                    } else {
                        stopMusic();
                    }

                    return true;
                }

                if (y > sh - 160) {

                    if (x < 150) {
                        left = true;
                    } else if (x < sw * 0.32f) {

                        right = true;

                        if (event.getEventTime() -
                                event.getDownTime() > 350) {
                            turboMode = true;
                        }

                    } else if (x < sw * 0.52f) {
                        down = true;
                    } else if (x > sw * 0.75f) {
                        jump = true;
                    }
                }
            }

            return true;
        }

        void startMusic() {

            if (!musicOn || flagClimbing || levelComplete) return;

            stopMusic();

            try {
                int sampleRate = 22050;
                int length = sampleRate * 3;

                short[] samples = new short[length];

                double[] notes = {
                        523.25, 659.25, 783.99, 659.25,
                        587.33, 698.46, 880.00, 698.46
                };

                int noteLength = length / notes.length;

                for (int i = 0; i < length; i++) {

                    int n = (i / noteLength) % notes.length;
                    double frequency = notes[n];

                    double wave = Math.sin(
                            2 * Math.PI * frequency * i / sampleRate
                    );

                    samples[i] = (short)(wave * 3000);
                }

                musicTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        length * 2,
                        AudioTrack.MODE_STATIC
                );

                musicTrack.write(samples, 0, length);
                musicTrack.setLoopPoints(0, length - 1, -1);
                musicTrack.play();

            } catch (Exception e) {
                stopMusic();
            }
        }

        synchronized void stopMusic() {

            if (musicTrack != null) {

                try {
                    musicTrack.stop();
                } catch (Exception ignored) {}

                try {
                    musicTrack.release();
                } catch (Exception ignored) {}

                musicTrack = null;
            }
        }

        void playBrickSound() {

            playEffect(new double[]{
                    180, 130, 90
            }, 350);
        }

        void playVictorySound() {

            playEffect(new double[]{
                    523, 659, 784, 1047, 784, 1047
            }, 450);
        }

        void playEffect(double[] notes, int noteDurationMs) {

            new Thread(() -> {

                AudioTrack track = null;

                try {

                    int sampleRate = 22050;
                    int noteSamples =
                            sampleRate * noteDurationMs / 1000;

                    int length = noteSamples * notes.length;

                    short[] samples = new short[length];

                    for (int n = 0; n < notes.length; n++) {

                        for (int j = 0; j < noteSamples; j++) {

                            int index = n * noteSamples + j;

                            double fade =
                                    1.0 - ((double) j / noteSamples);

                            double wave = Math.sin(
                                    2 * Math.PI * notes[n] *
                                            index / sampleRate
                            );

                            samples[index] = (short)(
                                    wave * 6500 * fade
                            );
                        }
                    }

                    track = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            length * 2,
                            AudioTrack.MODE_STATIC
                    );

                    track.write(samples, 0, length);
                    track.play();

                    Thread.sleep(
                            noteDurationMs * notes.length + 150
                    );

                } catch (Exception ignored) {

                } finally {

                    if (track != null) {
                        try {
                            track.release();
                        } catch (Exception ignored) {}
                    }
                }

            }).start();
        }
    }
}

