
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.content.pm.ActivityInfo;

import java.util.ArrayList;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        );

        setContentView(new GameView());
    }

    public class GameView extends View {

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float heroX = 100;
        float heroY = 100;
        float heroW = 58;
        float heroH = 76;

        float vx = 0;
        float vy = 0;

        float gravity = 0.75f;
        float jumpPower = -19f;
        float speed = 6f;

        float cameraX = 0;
        float ground;
        float screenW;
        float screenH;

        float levelEnd = 6000;

        boolean left = false;
        boolean right = false;
        boolean jumpPressed = false;
        boolean grounded = false;

        boolean gameOver = false;
        boolean won = false;

        int score = 0;
        int lives = 3;

        ArrayList<Platform> platforms = new ArrayList<>();
        ArrayList<Coin> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        long lastTime = 0;

        public GameView() {
            super(MainActivity.this);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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
            float x, y;
            boolean alive = true;

            Enemy(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        @Override
        protected void onSizeChanged(
                int w, int h, int oldw, int oldh) {

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

            float g = ground;

            // Elevated platforms
            platforms.add(new Platform(300, g - 115, 160, 30));
            platforms.add(new Platform(650, g - 115, 160, 30));
            platforms.add(new Platform(1000, g - 190, 170, 30));
            platforms.add(new Platform(1450, g - 115, 160, 30));
            platforms.add(new Platform(1800, g - 190, 170, 30));
            platforms.add(new Platform(2250, g - 115, 160, 30));
            platforms.add(new Platform(2650, g - 190, 170, 30));
            platforms.add(new Platform(3100, g - 115, 160, 30));
            platforms.add(new Platform(3500, g - 190, 170, 30));
            platforms.add(new Platform(3950, g - 115, 160, 30));
            platforms.add(new Platform(4400, g - 190, 170, 30));
            platforms.add(new Platform(4850, g - 115, 160, 30));

            // Coins
            for (int x = 250; x < 5500; x += 180) {
                coins.add(new Coin(x, g - 260));
            }

            // Enemies
            enemies.add(new Enemy(550, g - 35));
            enemies.add(new Enemy(1250, g - 35));
            enemies.add(new Enemy(2050, g - 35));
            enemies.add(new Enemy(2900, g - 35));
            enemies.add(new Enemy(3800, g - 35));
            enemies.add(new Enemy(4700, g - 35));
        }

        void setColor(Canvas c, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        void rectangle(
                Canvas c, float x, float y,
                float w, float h, int color) {

            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            c.drawRect(x, y, x + w, y + h, paint);
        }

        void updateGame() {

            if (gameOver || won || screenW == 0) return;

            float oldBottom = heroY + heroH;

            if (left) {
                vx = -speed;
            } else if (right) {
                vx = speed;
            } else {
                vx = 0;
            }

            heroX += vx;

            if (heroX < 0) heroX = 0;
            if (heroX > levelEnd - heroW) {
                heroX = levelEnd - heroW;
            }

            vy += gravity;
            heroY += vy;

            grounded = false;

            // Landing on elevated platforms
            if (vy >= 0) {
                for (Platform p : platforms) {

                    boolean horizontal =
                            heroX + heroW > p.x &&
                            heroX < p.x + p.w;

                    boolean vertical =
                            oldBottom <= p.y + 8 &&
                            heroY + heroH >= p.y;

                    if (horizontal && vertical) {
                        heroY = p.y - heroH;
                        vy = 0;
                        grounded = true;
                        jumpPressed = false;
                    }
                }
            }

            // Ground collision
            if (heroY + heroH >= ground) {
                heroY = ground - heroH;
                vy = 0;
                grounded = true;
                jumpPressed = false;
            }

            // Collect coins
            for (Coin coin : coins) {

                if (!coin.collected) {

                    float dx = heroX + heroW / 2 - coin.x;
                    float dy = heroY + heroH / 2 - coin.y;

                    if (Math.sqrt(dx * dx + dy * dy) < 55) {
                        coin.collected = true;
                        score += 10;
                    }
                }
            }

            // Enemy collision
            for (Enemy enemy : enemies) {

                if (!enemy.alive) continue;

                boolean hit =
                        heroX + heroW > enemy.x &&
                        heroX < enemy.x + 38 &&
                        heroY + heroH > enemy.y &&
                        heroY < enemy.y + 35;

                if (hit) {

                    if (vy > 2 && heroY + heroH < enemy.y + 25) {
                        enemy.alive = false;
                        vy = jumpPower * 0.55f;
                        score += 50;
                    } else {
                        lives--;
                        heroX = Math.max(0, heroX - 100);
                        heroY = ground - heroH;
                        vy = 0;
                        grounded = true;

                        if (lives <= 0) {
                            gameOver = true;
                        }
                    }
                }
            }

            // Falling off screen
            if (heroY > screenH + 100) {
                lives--;

                heroX = Math.max(0, heroX - 150);
                heroY = ground - heroH;
                vy = 0;
                grounded = true;
                jumpPressed = false;

                if (lives <= 0) gameOver = true;
            }

            // Camera follows hero
            cameraX = heroX - screenW * 0.35f;

            if (cameraX < 0) cameraX = 0;

            if (cameraX > levelEnd - screenW) {
                cameraX = levelEnd - screenW;
            }

            if (heroX >= levelEnd - 250) {
                won = true;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (screenW == 0) return;

            updateGame();

            // Sky
            canvas.drawColor(Color.rgb(99, 194, 239));

            canvas.save();
            canvas.translate(-cameraX, 0);

            // Sun
            paint.setColor(Color.YELLOW);
            canvas.drawCircle(560, 65, 25, paint);

            // Clouds
            for (int x = 100; x < levelEnd; x += 350) {
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, 100, 20, paint);
                canvas.drawCircle(x + 25, 90, 25, paint);
                canvas.drawCircle(x + 50, 100, 20, paint);
            }

            // Green hills
            for (int x = 0; x < levelEnd; x += 340) {
                paint.setColor(Color.rgb(65, 170, 85));
                canvas.drawCircle(
                        x + 100, ground, 90, paint
                );
            }

            // Ground
            rectangle(
                    canvas, 0, ground,
                    levelEnd, screenH - ground,
                    Color.rgb(164, 82, 35)
            );

            // Ground tile lines
            paint.setColor(Color.rgb(105, 55, 30));
            paint.setStrokeWidth(2);

            for (int x = 0; x < levelEnd; x += 35) {
                canvas.drawLine(
                        x, ground, x, screenH, paint
                );
            }

            canvas.drawLine(
                    0, ground, levelEnd, ground, paint
            );

            // Platforms
            for (Platform p : platforms) {

                rectangle(
                        canvas, p.x, p.y,
                        p.w, p.h,
                        Color.rgb(174, 91, 42)
                );

                rectangle(
                        canvas, p.x, p.y,
                        p.w, 6,
                        Color.rgb(242, 174, 54)
                );

                paint.setColor(Color.rgb(120, 65, 35));

                for (int x = (int)p.x + 30;
                     x < p.x + p.w; x += 30) {

                    canvas.drawLine(
                            x, p.y, x, p.y + p.h, paint
                    );
                }
            }

            // Coins
            for (Coin coin : coins) {
                if (coin.collected) continue;

                paint.setColor(Color.YELLOW);
                canvas.drawOval(
                        coin.x - 9, coin.y - 14,
                        coin.x + 9, coin.y + 14,
                        paint
                );
            }

            // Enemies
            for (Enemy enemy : enemies) {

                if (!enemy.alive) continue;

                paint.setColor(Color.rgb(110, 55, 35));
                canvas.drawOval(
                        enemy.x, enemy.y,
                        enemy.x + 38, enemy.y + 32,
                        paint
                );

                paint.setColor(Color.WHITE);
                canvas.drawCircle(
                        enemy.x + 12, enemy.y + 11, 5, paint
                );
                canvas.drawCircle(
                        enemy.x + 26, enemy.y + 11, 5, paint
                );

                paint.setColor(Color.BLACK);
                canvas.drawCircle(
                        enemy.x + 12, enemy.y + 11, 2, paint
                );
                canvas.drawCircle(
                        enemy.x + 26, enemy.y + 11, 2, paint
                );
            }

            // Hero
            drawHero(canvas);

            canvas.restore();

            // Score and lives
            paint.setColor(Color.WHITE);
            paint.setTextSize(22);
            canvas.drawText(
                    "SCORE: " + score, 15, 28, paint
            );
            canvas.drawText(
                    "LIVES: " + lives, 15, 55, paint
            );

            // Touch controls
            drawControls(canvas);

            // Game over
            if (gameOver) {
                drawMessage(canvas, "GAME OVER");
            }

            if (won) {
                drawMessage(canvas, "LEVEL COMPLETE!");
            }

            postInvalidateDelayed(16);
        }

        void drawHero(Canvas canvas) {

            float x = heroX;
            float y = heroY;

            // Shoes
            rectangle(
                    canvas, x + 5, y + 64,
                    22, 12, Color.rgb(90, 40, 25)
            );

            rectangle(
                    canvas, x + 34, y + 64,
                    20, 12, Color.rgb(90, 40, 25)
            );

            // Legs
            rectangle(
                    canvas, x + 10, y + 48,
                    16, 18, Color.BLUE
            );

            rectangle(
                    canvas, x + 34, y + 48,
                    15, 18, Color.BLUE
            );

            // Body
            rectangle(
                    canvas, x + 8, y + 29,
                    43, 24, Color.RED
            );

            // Face
            rectangle(
                    canvas, x + 13, y + 8,
                    38, 28, Color.rgb(255, 190, 130)
            );

            // Hair
            rectangle(
                    canvas, x + 10, y + 8,
                    9, 18, Color.rgb(100, 45, 20)
            );

            // Eyes
            rectangle(
                    canvas, x + 38, y + 16,
                    5, 6, Color.BLACK
            );

            // Nose
            rectangle(
                    canvas, x + 47, y + 23,
                    6, 6, Color.rgb(230, 130, 80)
            );

            // Red cap
            rectangle(
                    canvas, x + 10, y,
                    36, 10, Color.RED
            );

            rectangle(
                    canvas, x + 5, y + 8,
                    48, 6, Color.RED
            );

            // Overall straps
            rectangle(
                    canvas, x + 17, y + 32,
                    6, 19, Color.YELLOW
            );

            rectangle(
                    canvas, x + 36, y + 32,
                    6, 19, Color.YELLOW
            );
        }

        void drawButton(
                Canvas canvas, float x, float y, String label) {

            paint.setColor(Color.argb(130, 40, 35, 30));
            canvas.drawCircle(x, y, 32, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            paint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(label, x, y + 8, paint);

            paint.setTextAlign(Paint.Align.LEFT);
        }

        void drawControls(Canvas canvas) {

            float y = screenH * 0.91f;

            drawButton(canvas, screenW * 0.12f, y, "<");
            drawButton(canvas, screenW * 0.29f, y, ">");
            drawButton(canvas, screenW * 0.88f, y, "J");
        }

        void drawMessage(Canvas canvas, String message) {

            paint.setColor(Color.argb(190, 0, 0, 0));

            canvas.drawRect(
                    screenW * 0.20f,
                    screenH * 0.32f,
                    screenW * 0.80f,
                    screenH * 0.68f,
                    paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextSize(38);
            paint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(
                    message,
                    screenW / 2,
                    screenH / 2,
                    paint
            );

            paint.setTextSize(20);
            canvas.drawText(
                    "Tap to restart",
                    screenW / 2,
                    screenH / 2 + 40,
                    paint
            );

            paint.setTextAlign(Paint.Align.LEFT);
        }

        void restart() {

            heroX = 100;
            heroY = ground - heroH;

            vx = 0;
            vy = 0;

            cameraX = 0;

            score = 0;
            lives = 3;

            left = false;
            right = false;
            jumpPressed = false;
            grounded = true;

            gameOver = false;
            won = false;

            createLevel();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            int action = event.getActionMasked();

            if (gameOver || won) {

                if (action == MotionEvent.ACTION_DOWN) {
                    restart();
                    invalidate();
                }

                return true;
            }

            // Start jump when a new finger presses J
            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN) {

                int index = event.getActionIndex();

                float x = event.getX(index);
                float y = event.getY(index);

                if (x > screenW * 0.70f &&
                        y > screenH * 0.78f) {

                    if (grounded && !jumpPressed) {
                        vy = jumpPower;
                        grounded = false;
                        jumpPressed = true;
                    }
                }
            }

            // Read every finger currently touching the screen
            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN ||
                    action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_POINTER_UP ||
                    action == MotionEvent.ACTION_UP) {

                left = false;
                right = false;

                for (int i = 0;
                     i < event.getPointerCount(); i++) {

                    if ((action == MotionEvent.ACTION_POINTER_UP ||
                            action == MotionEvent.ACTION_UP) &&
                            i == event.getActionIndex()) {
                        continue;
                    }

                    float x = event.getX(i);
                    float y = event.getY(i);

                    if (y > screenH * 0.78f) {

                        if (x < screenW * 0.20f) {
                            left = true;
                        }

                        if (x >= screenW * 0.20f &&
                                x < screenW * 0.40f) {
                            right = true;
                        }
                    }
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                left = false;
                right = false;
            }

            return true;
        }
    }
}

