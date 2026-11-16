

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(new GameView(this));
    }

    class GameView extends View {

        final int SW = 800;
        final int SH = 450;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint spritePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG |
            Paint.FILTER_BITMAP_FLAG
        );

        Bitmap heroIdle, heroRun, heroCrawl;
        Bitmap bombImage, turtleImage, bulletImage;

        float playerX = 80;
        float playerY = 322;
        float playerW = 58;
        float playerH = 68;

        float vx = 0;
        float vy = 0;
        float gravity = 0.65f;
        float speed = 4.2f;

        float cameraX = 0;

        boolean left, right, jumping;
        boolean onGround = true;
        boolean facingRight = true;

        int score = 0;
        int lives = 3;

        long lastTime = System.currentTimeMillis();

        ArrayList<RectF> bricks = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        class Enemy {
            float x, y, vx;
            int type;
            boolean alive = true;

            Enemy(float x, float y, int type) {
                this.x = x;
                this.y = y;
                this.type = type;
                this.vx = 1.5f;
            }
        }

        GameView(Context c) {
            super(c);
            loadImages();
            createLevel();
        }

        // ---------------- IMAGE LOADING ----------------

        Bitmap loadImage(int id) {
            Bitmap original = BitmapFactory.decodeResource(
                getResources(), id
            );

            if (original == null) return null;

            return removeBackground(original);
        }

        void loadImages() {

            heroIdle = loadImage(R.drawable.hero_idle);
            heroRun = loadImage(R.drawable.hero_run);
            heroCrawl = loadImage(R.drawable.hero_crawl);

            bombImage = loadImage(R.drawable.bomb);
            turtleImage = loadImage(R.drawable.turtle);
            bulletImage = loadImage(R.drawable.bullet);
        }

        // Flood-fill removal of background connected to edges.
        Bitmap removeBackground(Bitmap source) {

            Bitmap result = source.copy(
                Bitmap.Config.ARGB_8888, true
            );

            int w = result.getWidth();
            int h = result.getHeight();

            int[] pixels = new int[w * h];
            result.getPixels(pixels, 0, w, 0, 0, w, h);

            boolean[] visited = new boolean[w * h];

            ArrayDeque<Integer> queue = new ArrayDeque<>();

            int[] corners = {
                pixels[0],
                pixels[w - 1],
                pixels[(h - 1) * w],
                pixels[h * w - 1]
            };

            for (int x = 0; x < w; x++) {
                add(queue, visited, x);
                add(queue, visited, (h - 1) * w + x);
            }

            for (int y = 0; y < h; y++) {
                add(queue, visited, y * w);
                add(queue, visited, y * w + w - 1);
            }

            while (!queue.isEmpty()) {

                int index = queue.removeFirst();

                int color = pixels[index];

                if (!matchesBackground(color, corners)) {
                    continue;
                }

                pixels[index] = Color.TRANSPARENT;

                int x = index % w;
                int y = index / w;

                if (x > 0)
                    add(queue, visited, index - 1);

                if (x < w - 1)
                    add(queue, visited, index + 1);

                if (y > 0)
                    add(queue, visited, index - w);

                if (y < h - 1)
                    add(queue, visited, index + w);
            }

            result.setPixels(pixels, 0, w, 0, 0, w, h);

            return result;
        }

        boolean matchesBackground(int color, int[] corners) {

            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            // Only remove near-neutral light backgrounds.
            if (Math.min(r, Math.min(g, b)) < 175) {
                return false;
            }

            for (int bg : corners) {

                int br = Color.red(bg);
                int bgc = Color.green(bg);
                int bb = Color.blue(bg);

                // Avoid treating coloured backgrounds as neutral.
                int max = Math.max(br, Math.max(bgc, bb));
                int min = Math.min(br, Math.min(bgc, bb));

                if (max - min > 35) continue;
                if (br < 175 || bgc < 175 || bb < 175) continue;

                if (Math.abs(r - br) < 45 &&
                    Math.abs(g - bgc) < 45 &&
                    Math.abs(b - bb) < 45) {
                    return true;
                }
            }

            return false;
        }

        void add(ArrayDeque<Integer> q,
                 boolean[] visited, int index) {

            if (index >= 0 &&
                index < visited.length &&
                !visited[index]) {

                visited[index] = true;
                q.add(index);
            }
        }

        // ---------------- LEVEL ----------------

        void createLevel() {

            bricks.clear();
            enemies.clear();

            for (int i = 0; i < 30; i++) {

                bricks.add(new RectF(
                    300 + i * 85,
                    275,
                    340 + i * 85,
                    315
                ));
            }

            bricks.add(new RectF(600, 190, 640, 230));
            bricks.add(new RectF(640, 190, 680, 230));

            bricks.add(new RectF(1100, 235, 1140, 275));
            bricks.add(new RectF(1140, 235, 1180, 275));

            enemies.add(new Enemy(550, 342, 1));
            enemies.add(new Enemy(900, 342, 2));
            enemies.add(new Enemy(1400, 342, 1));
        }

        // ---------------- DRAW ----------------

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float sx = getWidth() / (float) SW;
            float sy = getHeight() / (float) SH;

            canvas.save();
            canvas.scale(sx, sy);

            updateGame();

            drawBackground(canvas);
            drawWorld(canvas);
            drawPlayer(canvas);
            drawEnemies(canvas);
            drawControls(canvas);
            drawScore(canvas);

            canvas.restore();

            postInvalidateDelayed(16);
        }

        void drawBackground(Canvas c) {

            p.setShader(new LinearGradient(
                0, 0, 0, SH,
                Color.rgb(65, 170, 255),
                Color.rgb(190, 235, 255),
                Shader.TileMode.CLAMP
            ));

            c.drawRect(0, 0, SW, SH, p);
            p.setShader(null);

            p.setColor(Color.WHITE);

            for (int i = 0; i < 12; i++) {

                float x = (i * 180 - cameraX * 0.3f)
                    % 1000;

                c.drawCircle(x, 75, 22, p);
                c.drawCircle(x + 25, 65, 28, p);
                c.drawCircle(x + 50, 78, 20, p);
            }
        }

        void drawWorld(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            p.setColor(Color.rgb(75, 185, 60));
            c.drawRect(0, 390, 2500, 450, p);

            p.setColor(Color.rgb(125, 75, 35));
            c.drawRect(0, 405, 2500, 450, p);

            for (RectF b : bricks) {

                p.setColor(Color.rgb(180, 85, 35));
                c.drawRoundRect(b, 3, 3, p);

                p.setColor(Color.rgb(255, 170, 70));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                c.drawRoundRect(b, 3, 3, p);
                p.setStyle(Paint.Style.FILL);

                p.setColor(Color.rgb(100, 40, 15));

                c.drawLine(
                    b.left, b.centerY(),
                    b.right, b.centerY(), p
                );
            }

            c.restore();
        }

        void drawSprite(Canvas c, Bitmap image,
                        float x, float y, float w, float h,
                        boolean flip) {

            if (image == null) return;

            c.save();

            if (flip) {
                c.scale(-1, 1, x + w / 2, y + h / 2);
            }

            c.drawBitmap(
                image,
                null,
                new RectF(x, y, x + w, y + h),
                spritePaint
            );

            c.restore();
        }

        void drawPlayer(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            Bitmap image = heroIdle;

            if (Math.abs(vx) > 0.1f)
                image = heroRun;

            if (image == null)
                image = heroIdle;

            drawSprite(
                c, image,
                playerX, playerY,
                playerW, playerH,
                !facingRight
            );

            c.restore();
        }

        void drawEnemies(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            for (Enemy e : enemies) {

                if (!e.alive) continue;

                Bitmap image = e.type == 1 ?
                    turtleImage : bombImage;

                drawSprite(
                    c, image,
                    e.x, e.y,
                    48, 48,
                    e.vx < 0
                );
            }

            c.restore();
        }

        // ---------------- PHYSICS ----------------

        void updateGame() {

            long now = System.currentTimeMillis();

            float dt = Math.min(
                (now - lastTime) / 16.67f, 2.5f
            );

            lastTime = now;

            float oldY = playerY;
            float oldBottom = playerY + playerH;

            vx = 0;

            if (left) {
                vx = -speed;
                facingRight = false;
            }

            if (right) {
                vx = speed;
                facingRight = true;
            }

            playerX += vx * dt;
            playerX = Math.max(0, playerX);

            vy += gravity * dt;
            playerY += vy * dt;

            onGround = false;

            if (playerY + playerH >= 390) {

                playerY = 390 - playerH;
                vy = 0;
                onGround = true;
            }

            // Break bricks from underneath.
            if (vy < 0) {

                for (int i = bricks.size() - 1; i >= 0; i--) {

                    RectF b = bricks.get(i);

                    boolean horizontal =
                        playerX + playerW > b.left &&
                        playerX < b.right;

                    boolean hit =
                        oldY >= b.bottom &&
                        playerY <= b.bottom;

                    if (horizontal && hit) {

                        bricks.remove(i);
                        vy = 3;
                        score += 10;
                        break;
                    }
                }
            }

            // Land on bricks.
            if (vy >= 0) {

                for (RectF b : bricks) {

                    boolean horizontal =
                        playerX + playerW > b.left &&
                        playerX < b.right;

                    boolean landing =
                        oldBottom <= b.top &&
                        playerY + playerH >= b.top;

                    if (horizontal && landing) {

                        playerY = b.top - playerH;
                        vy = 0;
                        onGround = true;
                    }
                }
            }

            // Enemies.
            for (Enemy e : enemies) {

                if (!e.alive) continue;

                e.x += e.vx * dt;

                if (e.x < 450 || e.x > 1900)
                    e.vx = -e.vx;

                RectF player = new RectF(
                    playerX + 8,
                    playerY + 8,
                    playerX + playerW - 8,
                    playerY + playerH
                );

                RectF enemy = new RectF(
                    e.x, e.y, e.x + 45, e.y + 48
                );

                if (RectF.intersects(player, enemy)) {

                    if (vy > 1 && oldBottom <= e.y + 15) {

                        e.alive = false;
                        vy = -8;
                        score += 50;

                    } else {

                        lives--;

                        playerX = Math.max(80, playerX - 80);
                        playerY = 322;
                        vy = 0;

                        if (lives <= 0) {
                            score = 0;
                            lives = 3;
                            playerX = 80;
                            playerY = 322;
                            createLevel();
                        }

                        break;
                    }
                }
            }

            cameraX = Math.max(
                0, Math.min(playerX - 220, 1900)
            );
        }

        // ---------------- CONTROLS ----------------

        void drawButton(Canvas c, float l, float t,
                        float r, float b, String text) {

            p.setColor(0xAA222222);
            c.drawRoundRect(l, t, r, b, 18, 18, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(text.equals("JUMP") ? 22 : 42);

            c.drawText(
                text,
                (l + r) / 2,
                (t + b) / 2 + 12,
                p
            );
        }

        void drawControls(Canvas c) {

            drawButton(c, 35, 340, 140, 435, "◀");
            drawButton(c, 150, 340, 255, 435, "▶");
            drawButton(c, 665, 335, 780, 435, "JUMP");
        }

        void drawScore(Canvas c) {

            p.setColor(Color.WHITE);
            p.setTextSize(22);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.LEFT);

            c.drawText("SCORE: " + score, 20, 35, p);
            c.drawText("LIVES: " + lives, 20, 65, p);
        }

        // ---------------- TOUCH ----------------

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            float sx = SW / (float) getWidth();
            float sy = SH / (float) getHeight();

            int action = event.getActionMasked();
            int index = event.getActionIndex();

            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN) {

                updateControls(event, index, sx, sy);
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {

                left = false;
                right = false;

                for (int i = 0; i < event.getPointerCount(); i++) {
                    updateControls(event, i, sx, sy);
                }

                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;

                for (int i = 0; i < event.getPointerCount(); i++) {

                    if (i == index) continue;

                    updateControls(event, i, sx, sy);
                }

                return true;
            }

            return true;
        }

        void updateControls(MotionEvent e, int i,
                            float sx, float sy) {

            float x = e.getX(i) * sx;
            float y = e.getY(i) * sy;

            if (x >= 35 && x <= 140 && y >= 340) {
                left = true;
            }

            if (x >= 150 && x <= 255 && y >= 340) {
                right = true;
            }

            if (x >= 650 && y >= 330) {

                if (onGround) {
                    vy = -12.5f;
                    onGround = false;
                }
            }
        }
    }
}
