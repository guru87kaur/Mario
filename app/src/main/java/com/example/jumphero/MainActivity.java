

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.view.*;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private MediaPlayer backgroundMusic;
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        gameView = new GameView(this);
        setContentView(gameView);

        startMusic();
    }

    private void startMusic() {
        if (backgroundMusic == null) {
            backgroundMusic = MediaPlayer.create(
                    this, R.raw.background_music
            );

            if (backgroundMusic != null) {
                backgroundMusic.setLooping(true);
                backgroundMusic.setVolume(0.4f, 0.4f);
            }
        }

        if (backgroundMusic != null &&
                !backgroundMusic.isPlaying()) {
            backgroundMusic.start();
        }
    }

    private void pauseMusic() {
        if (backgroundMusic != null &&
                backgroundMusic.isPlaying()) {
            backgroundMusic.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMusic();
    }

    @Override
    protected void onPause() {
        pauseMusic();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (backgroundMusic != null) {
            backgroundMusic.release();
            backgroundMusic = null;
        }
        super.onDestroy();
    }

    // ==================================================
    // GAME VIEW
    // ==================================================

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bitmap heroIdle;
        Bitmap heroRun;
        Bitmap heroCrawl;

        Bitmap bombImage;
        Bitmap turtleImage;
        Bitmap bulletImage;

        float screenW, screenH;
        float cameraX = 0;

        final float WORLD_WIDTH = 3000;
        final float GROUND = 430;

        float playerX = 100;
        float playerY = 300;
        float playerW = 85;
        float playerH = 100;

        float velocityY = 0;

        final float GRAVITY = 1500;
        final float JUMP = -700;
        final float SPEED = 330;

        boolean left = false;
        boolean right = false;
        boolean shoot = false;
        boolean onGround = false;
        boolean facingRight = true;

        boolean started = false;
        boolean gameOver = false;
        boolean completed = false;
        boolean paused = false;

        int score = 0;
        int lives = 3;
        int level = 1;

        long lastTime = 0;
        long animationTime = 0;
        long lastShot = 0;

        ToneGenerator sound;

        ArrayList<Platform> platforms = new ArrayList<>();
        ArrayList<Brick> bricks = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        ArrayList<Coin> coins = new ArrayList<>();

        GameView(Context context) {
            super(context);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            heroIdle = load(R.drawable.hero_idle);
            heroRun = load(R.drawable.hero_run);
            heroCrawl = load(R.drawable.hero_crawl);

            bombImage = load(R.drawable.bomb);
            turtleImage = load(R.drawable.turtle);
            bulletImage = load(R.drawable.bullet);

            sound = new ToneGenerator(
                    AudioManager.STREAM_MUSIC, 80
            );

            createLevel();
        }

        Bitmap load(int resource) {
            try {
                return BitmapFactory.decodeResource(
                        getResources(), resource
                );
            } catch (Exception e) {
                return null;
            }
        }

        // ==================================================
        // LEVEL
        // ==================================================

        void createLevel() {

            platforms.clear();
            bricks.clear();
            enemies.clear();
            bullets.clear();
            coins.clear();

            platforms.add(new Platform(
                    0, GROUND, WORLD_WIDTH, 100
            ));

            platforms.add(new Platform(350, 330, 180, 25));
            platforms.add(new Platform(650, 270, 180, 25));
            platforms.add(new Platform(1000, 340, 180, 25));
            platforms.add(new Platform(1350, 280, 180, 25));
            platforms.add(new Platform(1750, 330, 180, 25));
            platforms.add(new Platform(2150, 270, 180, 25));

            for (int i = 0; i < 6; i++) {
                bricks.add(new Brick(500 + i * 55, 230));
            }

            for (int i = 0; i < 5; i++) {
                bricks.add(new Brick(1200 + i * 55, 200));
            }

            for (int i = 0; i < 15; i++) {
                coins.add(new Coin(
                        300 + i * 150,
                        350 - (i % 3) * 65
                ));
            }

            enemies.add(new Enemy(600, 375, 65, 55, 0));
            enemies.add(new Enemy(950, 375, 70, 55, 1));
            enemies.add(new Enemy(1450, 375, 65, 55, 0));
            enemies.add(new Enemy(1850, 375, 70, 55, 1));
            enemies.add(new Enemy(2300, 375, 65, 55, 2));

            playerX = 100;
            playerY = 300;
            velocityY = 0;
            cameraX = 0;
            onGround = false;
        }

        void resetGame() {
            score = 0;
            lives = 3;
            level = 1;

            gameOver = false;
            completed = false;
            paused = false;

            createLevel();
        }

        // ==================================================
        // DRAWING
        // ==================================================

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            screenW = getWidth();
            screenH = getHeight();

            drawBackground(c);

            if (!started) {
                drawButtonScreen(c, "DORAEMON ADVENTURE",
                        "TAP TO START");
                postInvalidateDelayed(30);
                return;
            }

            if (gameOver) {
                drawButtonScreen(c, "GAME OVER",
                        "TAP TO RESTART");
                postInvalidateDelayed(30);
                return;
            }

            if (completed) {
                drawButtonScreen(c, "LEVEL COMPLETE!",
                        "TAP FOR NEXT LEVEL");
                postInvalidateDelayed(30);
                return;
            }

            if (paused) {
                drawWorld(c);
                drawHUD(c);
                drawButtonScreen(c, "PAUSED",
                        "TAP TO RESUME");
                postInvalidateDelayed(30);
                return;
            }

            updateGame();

            drawWorld(c);
            drawHUD(c);
            drawControls(c);

            postInvalidateDelayed(16);
        }

        void drawBackground(Canvas c) {
            p.setShader(new LinearGradient(
                    0, 0, 0, screenH,
                    Color.rgb(90, 190, 255),
                    Color.rgb(210, 240, 255),
                    Shader.TileMode.CLAMP
            ));

            c.drawRect(0, 0, screenW, screenH, p);
            p.setShader(null);

            p.setColor(Color.WHITE);

            for (int i = 0; i < 6; i++) {
                float x = (i * 240 - cameraX * 0.25f)
                        % (screenW + 300);

                if (x < -100) x += screenW + 300;

                float y = 80 + (i % 3) * 45;

                c.drawCircle(x, y, 28, p);
                c.drawCircle(x + 30, y - 15, 35, p);
                c.drawCircle(x + 65, y, 28, p);
            }

            p.setColor(Color.rgb(80, 190, 95));

            c.drawOval(-100, screenH - 190,
                    screenW * 0.55f, screenH + 100, p);

            c.drawOval(screenW * 0.4f, screenH - 160,
                    screenW + 100, screenH + 100, p);
        }

        void drawWorld(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            p.setColor(Color.rgb(120, 75, 35));
            c.drawRect(0, GROUND, WORLD_WIDTH, 600, p);

            p.setColor(Color.rgb(50, 190, 65));
            c.drawRect(0, GROUND - 10, WORLD_WIDTH,
                    GROUND + 10, p);

            for (Platform platform : platforms) {
                p.setColor(Color.rgb(150, 95, 45));

                c.drawRect(platform.x, platform.y,
                        platform.x + platform.w,
                        platform.y + platform.h, p);

                p.setColor(Color.rgb(75, 200, 70));

                c.drawRect(platform.x, platform.y,
                        platform.x + platform.w,
                        platform.y + 7, p);
            }

            drawBricks(c);
            drawCoins(c);
            drawEnemies(c);
            drawBullets(c);
            drawFlag(c);
            drawHero(c);

            c.restore();
        }

        void drawHero(Canvas c) {

            Bitmap image = heroIdle;

            if (right || left) {
                image = heroRun;
            }

            if (image == null) return;

            RectF destination = new RectF(
                    playerX, playerY,
                    playerX + playerW,
                    playerY + playerH
            );

            imagePaint.setAlpha(255);

            if (facingRight) {
                c.drawBitmap(image, null, destination, imagePaint);
            } else {
                c.save();
                c.scale(-1, 1,
                        playerX + playerW / 2,
                        playerY + playerH / 2);

                c.drawBitmap(image, null, destination, imagePaint);
                c.restore();
            }
        }

        void drawEnemies(Canvas c) {

            for (Enemy enemy : enemies) {

                Bitmap image;

                if (enemy.type == 0) {
                    image = bombImage;
                } else if (enemy.type == 1) {
                    image = turtleImage;
                } else {
                    image = bulletImage;
                }

                if (image == null) continue;

                RectF destination = new RectF(
                        enemy.x, enemy.y,
                        enemy.x + enemy.w,
                        enemy.y + enemy.h
                );

                c.drawBitmap(image, null,
                        destination, imagePaint);
            }
        }

        void drawBricks(Canvas c) {
            for (Brick b : bricks) {
                if (b.broken) continue;

                p.setColor(Color.rgb(185, 100, 35));
                c.drawRect(b.x, b.y, b.x + 50,
                        b.y + 35, p);

                p.setColor(Color.YELLOW);
                p.setStyle(Paint.Style.STROKE);
                c.drawRect(b.x, b.y, b.x + 50,
                        b.y + 35, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawCoins(Canvas c) {
            for (Coin coin : coins) {
                if (coin.collected) continue;

                p.setColor(Color.YELLOW);
                c.drawCircle(coin.x, coin.y, 13, p);

                p.setColor(Color.rgb(255, 180, 0));
                c.drawCircle(coin.x, coin.y, 7, p);
            }
        }

        void drawBullets(Canvas c) {
            for (Bullet b : bullets) {

                if (bulletImage != null) {
                    RectF dst = new RectF(
                            b.x, b.y, b.x + 35, b.y + 25
                    );

                    c.drawBitmap(
                            bulletImage, null, dst, imagePaint
                    );
                } else {
                    p.setColor(Color.YELLOW);
                    c.drawCircle(b.x, b.y, 10, p);
                }
            }
        }

        void drawFlag(Canvas c) {
            float x = WORLD_WIDTH - 150;

            p.setColor(Color.WHITE);
            p.setStrokeWidth(8);

            c.drawLine(x, 180, x, GROUND, p);

            Path flag = new Path();
            flag.moveTo(x + 4, 185);
            flag.lineTo(x + 105, 215);
            flag.lineTo(x + 4, 250);
            flag.close();

            p.setColor(Color.RED);
            c.drawPath(flag, p);

            p.setColor(Color.YELLOW);
            p.setStrokeWidth(4);

            c.drawLine(x + 15, 207,
                    x + 75, 225, p);

            p.setColor(Color.WHITE);
            p.setTextSize(22);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);

            c.drawText("FINISH", x + 45, 165, p);
        }

        // ==================================================
        // GAME UPDATE
        // ==================================================

        void updateGame() {

            long now = System.nanoTime();

            float dt = lastTime == 0 ? 0.016f :
                    (now - lastTime) / 1000000000f;

            lastTime = now;

            if (dt > 0.04f) dt = 0.04f;

            animationTime += (long)(dt * 1000);

            float oldY = playerY;

            if (left) {
                playerX -= SPEED * dt;
                facingRight = false;
            }

            if (right) {
                playerX += SPEED * dt;
                facingRight = true;
            }

            playerX = Math.max(
                    0, Math.min(playerX,
                            WORLD_WIDTH - playerW)
            );

            velocityY += GRAVITY * dt;
            playerY += velocityY * dt;

            onGround = false;

            for (Platform platform : platforms) {

                boolean horizontal =
                        playerX + playerW > platform.x &&
                        playerX < platform.x + platform.w;

                boolean falling =
                        velocityY >= 0 &&
                        oldY + playerH <= platform.y &&
                        playerY + playerH >= platform.y;

                if (horizontal && falling) {
                    playerY = platform.y - playerH;
                    velocityY = 0;
                    onGround = true;
                }
            }

            // Break bricks by jumping into them
            if (velocityY < 0) {
                for (Brick b : bricks) {
                    if (b.broken) continue;

                    boolean hit =
                            playerX + playerW > b.x &&
                            playerX < b.x + 50 &&
                            playerY <= b.y + 35 &&
                            playerY + playerH >= b.y;

                    if (hit) {
                        b.broken = true;
                        velocityY = 100;
                        score += 10;
                        playSound(ToneGenerator.TONE_PROP_ACK);
                    }
                }
            }

            if (playerY > screenH + 200) {
                loseLife();
            }

            // Collect coins
            for (Coin coin : coins) {
                if (coin.collected) continue;

                if (intersects(playerX, playerY,
                        playerW, playerH,
                        coin.x - 15, coin.y - 15,
                        30, 30)) {

                    coin.collected = true;
                    score += 10;
                    playSound(ToneGenerator.TONE_PROP_BEEP);
                }
            }

            // Enemy collisions
            ArrayList<Enemy> removeEnemies =
                    new ArrayList<>();

            for (Enemy enemy : enemies) {

                enemy.x += enemy.speed * dt;

                if (enemy.x < enemy.startX - 80 ||
                        enemy.x > enemy.startX + 80) {
                    enemy.speed = -enemy.speed;
                }

                if (intersects(playerX, playerY,
                        playerW, playerH,
                        enemy.x, enemy.y,
                        enemy.w, enemy.h)) {

                    if (velocityY > 100 &&
                            playerY + playerH - 15 < enemy.y + 20) {

                        removeEnemies.add(enemy);
                        score += 50;
                        velocityY = JUMP * 0.55f;

                    } else {
                        loseLife();
                        break;
                    }
                }
            }

            enemies.removeAll(removeEnemies);

            // Shoot
            if (shoot && now - lastShot > 400000000L) {

                float bx = facingRight ?
                        playerX + playerW :
                        playerX - 30;

                bullets.add(new Bullet(
                        bx, playerY + 45,
                        facingRight ? 600 : -600
                ));

                lastShot = now;
                playSound(ToneGenerator.TONE_PROP_BEEP);
            }

            // Update bullets
            ArrayList<Bullet> removeBullets =
                    new ArrayList<>();

            for (Bullet b : bullets) {

                b.x += b.speed * dt;

                if (b.x < 0 || b.x > WORLD_WIDTH) {
                    removeBullets.add(b);
                    continue;
                }

                for (Enemy enemy : enemies) {
                    if (intersects(
                            b.x, b.y, 35, 25,
                            enemy.x, enemy.y,
                            enemy.w, enemy.h)) {

                        removeEnemies.add(enemy);
                        removeBullets.add(b);
                        score += 50;
                        break;
                    }
                }
            }

            enemies.removeAll(removeEnemies);
            bullets.removeAll(removeBullets);

            // Camera
            cameraX = Math.max(
                    0,
                    Math.min(
                            playerX - screenW * 0.35f,
                            WORLD_WIDTH - screenW
                    )
            );

            // Finish
            if (playerX + playerW >= WORLD_WIDTH - 190) {
                left = false;
                right = false;
                shoot = false;

                completed = true;
                playSound(ToneGenerator.TONE_PROP_ACK);
            }
        }

        boolean intersects(
                float x1, float y1, float w1, float h1,
                float x2, float y2, float w2, float h2) {

            return x1 < x2 + w2 &&
                    x1 + w1 > x2 &&
                    y1 < y2 + h2 &&
                    y1 + h1 > y2;
        }

        void loseLife() {
            lives--;
            playSound(ToneGenerator.TONE_PROP_NACK);

            if (lives <= 0) {
                gameOver = true;
            } else {
                playerX = Math.max(100, playerX - 200);
                playerY = 250;
                velocityY = 0;
            }
        }

        // ==================================================
        // HUD
        // ==================================================

        void drawHUD(Canvas c) {

            p.setColor(Color.BLACK);
            p.setTextSize(24);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.LEFT);

            c.drawText("Score: " + score,
                    20, 35, p);

            c.drawText("Lives: " + lives,
                    20, 68, p);

            c.drawText("Level: " + level,
                    20, 101, p);

            p.setColor(Color.WHITE);
            c.drawRoundRect(
                    screenW - 65, 10,
                    screenW - 10, 55,
                    10, 10, p
            );

            p.setColor(Color.BLACK);
            c.drawText("II",
                    screenW - 48, 42, p);
        }

        void drawControls(Canvas c) {

            drawControl(c, 25, screenH - 100, "◀");
            drawControl(c, 125, screenH - 100, "▶");
            drawControl(c, screenW - 210,
                    screenH - 100, "FIRE");
            drawControl(c, screenW - 105,
                    screenH - 100, "JUMP");
        }

        void drawControl(
                Canvas c, float x, float y, String label) {

            p.setColor(Color.argb(170, 30, 30, 30));

            c.drawRoundRect(
                    x, y, x + 85, y + 70,
                    15, 15, p
            );

            p.setColor(Color.WHITE);
            p.setTextSize(23);
            p.setTextAlign(Paint.Align.CENTER);

            c.drawText(label,
                    x + 42, y + 44, p);
        }

        void drawButtonScreen(
                Canvas c, String title, String subtitle) {

            p.setColor(Color.argb(190, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            p.setTextSize(38);
            c.drawText(title,
                    screenW / 2, screenH / 2 - 30, p);

            p.setTextSize(23);
            c.drawText(subtitle,
                    screenW / 2, screenH / 2 + 35, p);
        }

        // ==================================================
        // TOUCH
        // ==================================================

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {

            float x = event.getX();
            float y = event.getY();

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {

                if (!started) {
                    started = true;
                    resetGame();
                    return true;
                }

                if (gameOver) {
                    resetGame();
                    started = true;
                    return true;
                }

                if (completed) {
                    level++;
                    completed = false;
                    createLevel();
                    return true;
                }

                if (paused) {
                    paused = false;
                    lastTime = 0;
                    return true;
                }

                if (x > screenW - 70 && y < 65) {
                    paused = true;
                    return true;
                }

                updateControls(x, y);
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                updateControls(x, y);
                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;
                shoot = false;
                return true;
            }

            return true;
        }

        void updateControls(float x, float y) {

            left = false;
            right = false;
            shoot = false;

            if (y > screenH - 115) {

                if (x < 115) {
                    left = true;
                } else if (x < 225) {
                    right = true;
                } else if (x > screenW - 220 &&
                        x < screenW - 110) {
                    shoot = true;
                } else if (x >= screenW - 110) {
                    if (onGround) {
                        velocityY = JUMP;
                        onGround = false;
                    }
                }
            }
        }

        void playSound(int tone) {
            if (sound != null) {
                sound.startTone(tone, 100);
            }
        }

        // ==================================================
        // DATA CLASSES
        // ==================================================

        class Platform {
            float x, y, w, h;

            Platform(float x, float y, float w, float h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }

        class Brick {
            float x, y;
            boolean broken = false;

            Brick(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        class Enemy {
            float x, y, w, h;
            float speed = 70;
            float startX;
            int type;

            Enemy(float x, float y,
                  float w, float h, int type) {

                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.type = type;
                this.startX = x;
            }
        }

        class Bullet {
            float x, y, speed;

            Bullet(float x, float y, float speed) {
                this.x = x;
                this.y = y;
                this.speed = speed;
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
    }
}
