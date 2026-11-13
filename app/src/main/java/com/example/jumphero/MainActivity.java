

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.*;
import java.util.ArrayList;
import java.util.Iterator;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        setContentView(new GameView());
    }

    class GameView extends View {

        Canvas c;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap heroIdle, heroRun, heroCrawl;
        Bitmap bombImg, turtleImg, bulletImg;
        Bitmap cloudImg, flowerImg, castleImg;

        float playerX = 100;
        float playerY = 250;
        float playerW = 48;
        float playerH = 58;
        float velocityX = 0;
        float velocityY = 0;
        float cameraX = 0;

        boolean left = false;
        boolean right = false;
        boolean jump = false;
        boolean onGround = false;
        boolean crawling = false;
        boolean gameOver = false;
        boolean finished = false;

        int score = 0;
        int lives = 3;
        int level = 1;
        int animation = 0;
        int frame = 0;

        float worldWidth = 3600;
        final float WORLD_HEIGHT = 450;

        ArrayList<RectF> platforms = new ArrayList<>();
        ArrayList<RectF> bricks = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<RectF> clouds = new ArrayList<>();

        class Enemy {
            float x, y, w, h, speed;
            int type;
            boolean alive = true;

            Enemy(float x, float y, int type) {
                this.x = x;
                this.y = y;
                this.type = type;
                w = 38;
                h = 38;
                speed = 1.3f;
            }
        }

        GameView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            heroIdle = load("hero_idle");
            heroRun = load("hero_run");
            heroCrawl = load("hero_crawl");

            bombImg = load("bomb");
            turtleImg = load("turtle");
            bulletImg = load("bullet");

            cloudImg = load("cloud");
            flowerImg = load("flower");
            castleImg = load("castle");

            loadLevel();
        }

        Bitmap load(String name) {
            int id = getResources().getIdentifier(
                name, "drawable", getPackageName()
            );
            if (id == 0) return null;

            try {
                return BitmapFactory.decodeResource(
                    getResources(), id
                );
            } catch (Exception e) {
                return null;
            }
        }

        void loadLevel() {
            platforms.clear();
            bricks.clear();
            enemies.clear();
            clouds.clear();

            playerX = 100;
            playerY = 250;
            velocityX = 0;
            velocityY = 0;
            cameraX = 0;
            finished = false;

            worldWidth = 3600 + (level - 1) * 400;

            // Ground
            platforms.add(new RectF(0, 400, worldWidth, 450));

            // Floating platforms
            platforms.add(new RectF(350, 320, 550, 340));
            platforms.add(new RectF(750, 280, 950, 300));
            platforms.add(new RectF(1150, 330, 1370, 350));
            platforms.add(new RectF(1600, 290, 1810, 310));
            platforms.add(new RectF(2100, 320, 2310, 340));
            platforms.add(new RectF(2600, 280, 2820, 300));
            platforms.add(new RectF(3050, 330, 3260, 350));

            // Brick blocks
            for (int i = 0; i < 8; i++) {
                float x = 450 + i * 120;
                bricks.add(new RectF(x, 220, x + 48, 268));
            }

            for (int i = 0; i < 7; i++) {
                float x = 1450 + i * 110;
                bricks.add(new RectF(x, 210, x + 48, 258));
            }

            for (int i = 0; i < 7; i++) {
                float x = 2350 + i * 110;
                bricks.add(new RectF(x, 225, x + 48, 273));
            }

            // Enemies: bomb, turtle and bullet
            enemies.add(new Enemy(600, 362, 0));
            enemies.add(new Enemy(1000, 362, 1));
            enemies.add(new Enemy(1400, 362, 2));
            enemies.add(new Enemy(1900, 362, 0));
            enemies.add(new Enemy(2400, 362, 1));
            enemies.add(new Enemy(2900, 362, 2));
            enemies.add(new Enemy(3300, 362, 0));

            // Clouds
            for (int i = 0; i < 15; i++) {
                clouds.add(new RectF(
                    i * 270 + 50, 35 + (i % 3) * 30,
                    i * 270 + 150, 90 + (i % 3) * 30
                ));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            c = canvas;

            c.save();
            c.scale(getWidth() / 800f, getHeight() / 450f);

            drawBackground();

            c.save();
            c.translate(-cameraX, 0);

            drawPlatforms();
            drawBricks();
            drawFlowers();
            drawCastle();
            drawEnemies();
            drawPlayer();
            drawFlag();

            c.restore();

            drawControls();
            drawHUD();

            c.restore();

            update();
            postInvalidateDelayed(16);
        }

        void color(int col) {
            p.setColor(col);
            p.setStyle(Paint.Style.FILL);
        }

        void drawBackground() {
            p.setShader(new LinearGradient(
                0, 0, 0, 450,
                Color.rgb(80, 190, 255),
                Color.rgb(205, 240, 255),
                Shader.TileMode.CLAMP
            ));
            c.drawRect(0, 0, 800, 450, p);
            p.setShader(null);

            for (RectF cloud : clouds) {
                if (cloudImg != null) {
                    drawBitmap(cloudImg, cloud);
                } else {
                    color(Color.WHITE);
                    c.drawOval(cloud, p);
                    c.drawCircle(cloud.left + 25, cloud.top,
                        25, p);
                    c.drawCircle(cloud.left + 55, cloud.top - 5,
                        30, p);
                }
            }

            // Sun
            color(Color.YELLOW);
            c.drawCircle(720, 65, 35, p);

            // Hills
            color(Color.rgb(65, 190, 80));
            for (int i = 0; i < 6; i++) {
                float x = i * 180 - cameraX * 0.2f;
                c.drawCircle(x, 405, 110, p);
            }
        }

        void drawPlatforms() {
            for (RectF r : platforms) {
                color(Color.rgb(125, 75, 35));
                c.drawRect(r, p);

                color(Color.rgb(55, 185, 60));
                c.drawRect(r.left, r.top, r.right,
                    Math.min(r.top + 9, r.bottom), p);

                color(Color.rgb(160, 105, 50));
                for (float x = r.left; x < r.right; x += 35) {
                    c.drawRect(x, r.top + 15,
                        x + 3, r.bottom, p);
                }
            }
        }

        void drawBricks() {
            for (RectF b : bricks) {
                color(Color.rgb(220, 110, 35));
                c.drawRect(b, p);

                color(Color.rgb(255, 180, 75));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawRect(b, p);
                p.setStyle(Paint.Style.FILL);

                color(Color.rgb(120, 60, 30));
                c.drawLine(b.left, b.centerY(),
                    b.right, b.centerY(), p);
                c.drawLine(b.centerX(), b.top,
                    b.centerX(), b.centerY(), p);
                c.drawLine(b.left + 12, b.centerY(),
                    b.left + 12, b.bottom, p);
            }
        }

        void drawFlowers() {
            for (int i = 0; i < 30; i++) {
                float x = i * 125 + 60;

                if (flowerImg != null) {
                    drawBitmap(flowerImg,
                        new RectF(x, 360, x + 35, 400));
                } else {
                    color(Color.GREEN);
                    c.drawRect(x + 14, 380, x + 19, 400, p);

                    color(Color.RED);
                    c.drawCircle(x + 12, 370, 7, p);
                    c.drawCircle(x + 22, 370, 7, p);
                    c.drawCircle(x + 17, 363, 7, p);
                    color(Color.YELLOW);
                    c.drawCircle(x + 17, 370, 5, p);
                }
            }
        }

        void drawCastle() {
            float x = worldWidth - 240;

            if (castleImg != null) {
                drawBitmap(castleImg,
                    new RectF(x, 245, x + 210, 400));
                return;
            }

            color(Color.rgb(230, 175, 115));
            c.drawRect(x, 275, x + 180, 400, p);
            c.drawRect(x + 30, 230, x + 70, 400, p);
            c.drawRect(x + 115, 230, x + 155, 400, p);

            color(Color.RED);
            c.drawRect(x + 20, 210, x + 80, 235, p);
            c.drawRect(x + 105, 210, x + 165, 235, p);

            color(Color.rgb(100, 55, 30));
            c.drawRect(x + 70, 340, x + 115, 400, p);

            color(Color.YELLOW);
            c.drawRect(x + 88, 300, x + 97, 315, p);
        }

        void drawEnemies() {
            for (Enemy e : enemies) {
                if (!e.alive) continue;

                Bitmap img = e.type == 0 ? bombImg :
                             e.type == 1 ? turtleImg : bulletImg;

                RectF dst = new RectF(
                    e.x, e.y, e.x + e.w, e.y + e.h
                );

                if (img != null) {
                    drawBitmap(img, dst);
                } else {
                    color(e.type == 0 ? Color.BLACK :
                          e.type == 1 ? Color.GREEN : Color.GRAY);
                    c.drawOval(dst, p);

                    color(Color.WHITE);
                    c.drawCircle(e.x + 25, e.y + 12, 4, p);
                }
            }
        }

        void drawPlayer() {
            Bitmap img = heroIdle;

            if (crawling && heroCrawl != null) {
                img = heroCrawl;
            } else if (Math.abs(velocityX) > 0.5f &&
                       heroRun != null) {
                img = heroRun;
            }

            RectF dst = new RectF(
                playerX, playerY,
                playerX + playerW,
                playerY + playerH
            );

            if (img != null) {
                c.save();

                // Face right when moving right
                if (velocityX < -0.5f) {
                    c.scale(-1, 1,
                        playerX + playerW / 2,
                        playerY + playerH / 2);
                }

                drawBitmap(img, dst);
                c.restore();
            } else {
                // Fallback Doraemon-like character
                color(Color.rgb(30, 145, 220));
                c.drawOval(dst, p);

                color(Color.WHITE);
                c.drawCircle(playerX + 24,
                    playerY + 23, 18, p);

                color(Color.RED);
                c.drawRect(playerX + 12,
                    playerY + 38, playerX + 36,
                    playerY + 43, p);

                color(Color.BLACK);
                c.drawCircle(playerX + 18,
                    playerY + 18, 3, p);
                c.drawCircle(playerX + 30,
                    playerY + 18, 3, p);
            }
        }

        void drawFlag() {
            float x = worldWidth - 100;

            color(Color.DKGRAY);
            c.drawRect(x, 210, x + 7, 400, p);

            Path flag = new Path();
            flag.moveTo(x + 7, 210);
            flag.lineTo(x + 65, 230);
            flag.lineTo(x + 7, 250);
            flag.close();

            color(Color.RED);
            c.drawPath(flag, p);

            color(Color.YELLOW);
            c.drawCircle(x + 7, 205, 7, p);
        }

        void drawBitmap(Bitmap bitmap, RectF dst) {
            if (bitmap == null) return;
            p.setColor(Color.WHITE);
            p.setAlpha(255);
            c.drawBitmap(bitmap, null, dst, p);
        }

        void drawControls() {
            drawButton(75, 365, 165, 440, "◀");
            drawButton(175, 365, 265, 440, "▶");
            drawButton(660, 350, 790, 445, "JUMP");
            drawButton(550, 365, 645, 440, "DOWN");
        }

        void drawButton(float l, float t, float r,
                        float b, String text) {
            color(0xAA263C55);
            c.drawRoundRect(l, t, r, b, 18, 18, p);

            p.setColor(Color.WHITE);
            p.setTextSize(text.equals("JUMP") ? 23 : 32);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(text, (l + r) / 2,
                (t + b) / 2 + 10, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void drawHUD() {
            color(Color.BLACK);
            p.setTextSize(22);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText("SCORE: " + score, 20, 30, p);
            c.drawText("LIVES: " + lives, 230, 30, p);
            c.drawText("LEVEL: " + level, 390, 30, p);

            if (gameOver || finished) {
                color(0xCC000000);
                c.drawRect(0, 0, 800, 450, p);

                color(Color.WHITE);
                p.setTextSize(38);
                p.setTextAlign(Paint.Align.CENTER);

                c.drawText(gameOver ? "GAME OVER" :
                    "LEVEL COMPLETE!", 400, 190, p);

                p.setTextSize(22);
                c.drawText("TAP TO CONTINUE", 400, 245, p);

                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        void update() {
            if (gameOver || finished) return;

            frame++;

            if (left) velocityX = -4;
            else if (right) velocityX = 4;
            else velocityX *= 0.75f;

            playerX += velocityX;

            if (playerX < 0) playerX = 0;
            if (playerX > worldWidth - playerW)
                playerX = worldWidth - playerW;

            // Gravity
            velocityY += 0.55f;
            if (velocityY > 12) velocityY = 12;

            float oldBottom = playerY + playerH;
            playerY += velocityY;
            onGround = false;

            // Landing on platforms
            if (velocityY >= 0) {
                for (RectF r : platforms) {
                    if (playerX + playerW > r.left &&
                        playerX < r.right &&
                        oldBottom <= r.top + 5 &&
                        playerY + playerH >= r.top) {

                        playerY = r.top - playerH;
                        velocityY = 0;
                        onGround = true;
                    }
                }
            }

            // Hit bricks from below
            if (velocityY < 0) {
                Iterator<RectF> it = bricks.iterator();

                while (it.hasNext()) {
                    RectF b = it.next();

                    if (playerX + playerW > b.left &&
                        playerX < b.right &&
                        playerY <= b.bottom &&
                        playerY >= b.bottom - 22) {

                        it.remove();
                        velocityY = 2;
                        score += 10;
                        break;
                    }
                }
            }

            if (playerY > 470) {
                loseLife();
            }

            // Camera follows Doraemon
            cameraX = playerX - 280;
            if (cameraX < 0) cameraX = 0;
            if (cameraX > worldWidth - 800)
                cameraX = worldWidth - 800;

            updateEnemies();

            // Reach finish flag
            if (playerX >= worldWidth - 125) {
                finished = true;
            }
        }

        void updateEnemies() {
            for (Enemy e : enemies) {
                if (!e.alive) continue;

                e.x += e.speed;

                if (e.x < 300 || e.x > worldWidth - 300) {
                    e.speed = -e.speed;
                }

                RectF player = new RectF(
                    playerX + 5, playerY + 5,
                    playerX + playerW - 5,
                    playerY + playerH - 3
                );

                RectF enemy = new RectF(
                    e.x, e.y, e.x + e.w, e.y + e.h
                );

                if (RectF.intersects(player, enemy)) {

                    // Jump on enemy
                    if (velocityY > 0 &&
                        playerY + playerH - e.y < 22) {
                        e.alive = false;
                        velocityY = -8;
                        score += 50;
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
            } else {
                playerX = Math.max(100, cameraX + 100);
                playerY = 250;
                velocityY = 0;
            }
        }

        void nextLevel() {
            level++;
            lives = Math.min(3, lives + 1);
            loadLevel();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * 800f / getWidth();
            float y = event.getY() * 450f / getHeight();

            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

                if (gameOver || finished) return true;

                left = x >= 75 && x <= 165 &&
                       y >= 350;

                right = x >= 175 && x <= 265 &&
                        y >= 350;

                crawling = x >= 550 && x <= 645 &&
                           y >= 350;

                if (x >= 660 && y >= 340 &&
                    onGround && !jump) {
                    velocityY = -11;
                    onGround = false;
                    jump = true;
                }

                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;
                crawling = false;
                jump = false;

                if (gameOver) {
                    gameOver = false;
                    score = 0;
                    lives = 3;
                    level = 1;
                    loadLevel();
                } else if (finished) {
                    nextLevel();
                }

                return true;
            }

            return true;
        }
    }
}
