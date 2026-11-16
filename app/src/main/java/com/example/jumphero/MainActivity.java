
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.graphics.drawable.*;
import java.util.*;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(new GameView(this));
    }

    class GameView extends View {

        final int SW = 800, SH = 450;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG |
                                      Paint.FILTER_BITMAP_FLAG);

        Bitmap heroIdle, heroRun, heroCrawl;
        Bitmap bombImage, turtleImage, bulletImage;

        float playerX = 80;
        float playerY = 310;
        float playerW = 54;
        float playerH = 68;

        float vx = 0;
        float vy = 0;

        float gravity = 0.65f;
        float jumpPower = -12.5f;
        float speed = 4.2f;

        boolean left = false;
        boolean right = false;
        boolean jumping = false;
        boolean crawling = false;
        boolean facingRight = true;
        boolean onGround = false;

        float cameraX = 0;
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
                this.vx = type == 1 ? 1.5f : 2f;
            }
        }

        GameView(Context context) {
            super(context);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            loadImages();
            createLevel();
        }

        // Load PNG images and remove connected white background.
        void loadImages() {
            heroIdle = loadImage(R.drawable.hero_idle);
            heroRun = loadImage(R.drawable.hero_run);
            heroCrawl = loadImage(R.drawable.hero_crawl);

            bombImage = loadImage(R.drawable.bomb);
            turtleImage = loadImage(R.drawable.turtle);
            bulletImage = loadImage(R.drawable.bullet);
        }

        Bitmap loadImage(int id) {
            Bitmap original = BitmapFactory.decodeResource(
                getResources(), id
            );

            if (original == null) return null;

            return removeWhiteBackground(original);
        }

        // Removes only white pixels connected to image edges.
        Bitmap removeWhiteBackground(Bitmap source) {

            Bitmap result = source.copy(
                Bitmap.Config.ARGB_8888, true
            );

            int w = result.getWidth();
            int h = result.getHeight();

            int[] pixels = new int[w * h];

            result.getPixels(pixels, 0, w, 0, 0, w, h);

            boolean[] visited = new boolean[w * h];
            ArrayDeque<Integer> queue = new ArrayDeque<>();

            for (int x = 0; x < w; x++) {
                addPixel(queue, visited, x, w);
                addPixel(queue, visited, (h - 1) * w + x, w);
            }

            for (int y = 0; y < h; y++) {
                addPixel(queue, visited, y * w, w);
                addPixel(queue, visited, y * w + w - 1, w);
            }

            while (!queue.isEmpty()) {

                int index = queue.removeFirst();

                int color = pixels[index];

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                if (r > 235 && g > 235 && b > 235) {

                    pixels[index] = Color.TRANSPARENT;

                    int x = index % w;
                    int y = index / w;

                    if (x > 0)
                        addPixel(queue, visited, index - 1, w);

                    if (x < w - 1)
                        addPixel(queue, visited, index + 1, w);

                    if (y > 0)
                        addPixel(queue, visited, index - w, w);

                    if (y < h - 1)
                        addPixel(queue, visited, index + w, w);
                }
            }

            result.setPixels(pixels, 0, w, 0, 0, w, h);

            return result;
        }

        void addPixel(ArrayDeque<Integer> queue,
                      boolean[] visited, int index, int width) {

            if (index >= 0 && index < visited.length &&
                !visited[index]) {

                visited[index] = true;
                queue.add(index);
            }
        }

        void createLevel() {

            bricks.clear();
            enemies.clear();

            // Floating brick blocks
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

            enemies.add(new Enemy(550, 322, 1));
            enemies.add(new Enemy(900, 322, 2));
            enemies.add(new Enemy(1400, 322, 1));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float scaleX = getWidth() / (float) SW;
            float scaleY = getHeight() / (float) SH;

            canvas.save();
            canvas.scale(scaleX, scaleY);

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

        void updateGame() {

            long now = System.currentTimeMillis();

            float dt = Math.min(
                (now - lastTime) / 16.67f, 2.5f
            );

            lastTime = now;

            float oldY = playerY;

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

            if (playerX < 0) playerX = 0;

            float oldBottom = playerY + playerH;

            vy += gravity * dt;
            playerY += vy * dt;

            onGround = false;

            // Ground collision
            if (playerY + playerH >= 390) {
                playerY = 390 - playerH;
                vy = 0;
                onGround = true;
            }

            // Brick collision from below: BREAK BRICK.
            if (vy < 0) {

                for (int i = bricks.size() - 1; i >= 0; i--) {

                    RectF b = bricks.get(i);

                    boolean horizontal =
                        playerX + playerW > b.left &&
                        playerX < b.right;

                    boolean hitBottom =
                        oldY >= b.bottom &&
                        playerY <= b.bottom;

                    if (horizontal && hitBottom) {

                        bricks.remove(i);

                        vy = 3f;
                        score += 10;

                        break;
                    }
                }
            }

            // Land on top of bricks.
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

            // Enemy movement and collision
            for (Enemy e : enemies) {

                if (!e.alive) continue;

                e.x += e.vx * dt;

                if (e.x < 450 || e.x > 1900)
                    e.vx = -e.vx;

                RectF player = new RectF(
                    playerX + 7, playerY + 8,
                    playerX + playerW - 7,
                    playerY + playerH
                );

                RectF enemy = new RectF(
                    e.x, e.y, e.x + 45, e.y + 48
                );

                if (RectF.intersects(player, enemy)) {

                    if (vy > 1 && oldBottom <= e.y + 12) {
                        e.alive = false;
                        vy = -8;
                        score += 50;
                    } else {
                        lives--;

                        playerX = Math.max(80, playerX - 70);
                        playerY = 310;
                        vy = 0;

                        if (lives <= 0) {
                            playerX = 80;
                            playerY = 310;
                            lives = 3;
                            score = 0;
                            createLevel();
                        }
                    }
                }
            }

            cameraX = Math.max(
                0, Math.min(playerX - 220, 1900)
            );
        }

        void drawBackground(Canvas c) {

            p.setShader(new LinearGradient(
                0, 0, 0, SH,
                Color.rgb(80, 190, 255),
                Color.rgb(190, 235, 255),
                Shader.TileMode.CLAMP
            ));

            c.drawRect(0, 0, SW, SH, p);
            p.setShader(null);

            // Clouds
            p.setColor(Color.WHITE);

            for (int i = 0; i < 12; i++) {
                float x = (i * 180 - cameraX * 0.3f) % 1000;

                c.drawCircle(x, 75, 22, p);
                c.drawCircle(x + 25, 65, 28, p);
                c.drawCircle(x + 50, 78, 20, p);
            }
        }

        void drawWorld(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            // Ground
            p.setColor(Color.rgb(80, 180, 65));
            c.drawRect(0, 390, 2500, 450, p);

            p.setColor(Color.rgb(120, 75, 35));
            c.drawRect(0, 402, 2500, 450, p);

            // Bricks
            for (RectF b : bricks) {

                p.setColor(Color.rgb(175, 80, 30));
                c.drawRoundRect(b, 3, 3, p);

                p.setColor(Color.rgb(255, 165, 70));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                c.drawRoundRect(b, 3, 3, p);
                p.setStyle(Paint.Style.FILL);

                p.setColor(Color.rgb(110, 45, 20));
                c.drawLine(
                    b.left, b.centerY(),
                    b.right, b.centerY(), p
                );
            }

            c.restore();
        }

        void drawSprite(Canvas c, Bitmap bitmap,
                        float x, float y, float w, float h) {

            if (bitmap == null) return;

            RectF destination = new RectF(x, y, x + w, y + h);

            c.drawBitmap(bitmap, null, destination, spritePaint);
        }

        void drawPlayer(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            Bitmap current;

            if (crawling) {
                current = heroCrawl;
            } else if (Math.abs(vx) > 0.1f) {
                current = heroRun;
            } else {
                current = heroIdle;
            }

            if (current == null) current = heroIdle;

            if (current != null && !facingRight) {

                c.save();
                c.scale(-1, 1,
                    playerX + playerW / 2,
                    playerY + playerH / 2
                );

                drawSprite(c, current,
                    playerX, playerY, playerW, playerH);

                c.restore();

            } else {

                drawSprite(c, current,
                    playerX, playerY, playerW, playerH);
            }

            c.restore();
        }

        void drawEnemies(Canvas c) {

            c.save();
            c.translate(-cameraX, 0);

            for (Enemy e : enemies) {

                if (!e.alive) continue;

                Bitmap image = e.type == 1 ?
                    turtleImage : bombImage;

                drawSprite(c, image,
                    e.x, e.y, 48, 48);
            }

            c.restore();
        }

        void drawControls(Canvas c) {

            // Left button
            drawButton(c, 35, 340, 105, 435, "◀");

            // Right button
            drawButton(c, 150, 340, 220, 435, "▶");

            // Jump button
            drawButton(c, 665, 335, 780, 435, "JUMP");
        }

        void drawButton(Canvas c, float l, float t,
                        float r, float b, String text) {

            p.setColor(0xAA222222);
            c.drawRoundRect(l, t, r, b, 18, 18, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(text.equals("JUMP") ? 23 : 42);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(text, (l + r) / 2,
                (t + b) / 2 + 12, p);
        }

        void drawScore(Canvas c) {

            p.setColor(Color.WHITE);
            p.setTextSize(22);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.LEFT);

            c.drawText("SCORE: " + score, 20, 35, p);
            c.drawText("LIVES: " + lives, 20, 65, p);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {

            float sx = SW / (float) getWidth();
            float sy = SH / (float) getHeight();

            float x = event.getX() * sx;
            float y = event.getY() * sy;

            int action = event.getActionMasked();
            int index = event.getActionIndex();
            int pointerId = event.getPointerId(index);

            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN) {

                setControl(pointerId, x, y, true);
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {

                left = false;
                right = false;
                jumping = false;

                for (int i = 0; i < event.getPointerCount(); i++) {
                    setControl(
                        event.getPointerId(i),
                        event.getX(i) * sx,
                        event.getY(i) * sy,
                        true
                    );
                }

                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL) {

                left = false;
                right = false;
                jumping = false;

                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (i == index) continue;

                    setControl(
                        event.getPointerId(i),
                        event.getX(i) * sx,
                        event.getY(i) * sy,
                        true
                    );
                }

                return true;
            }

            return true;
        }

        void setControl(int id, float x, float y,
                        boolean pressed) {

            if (!pressed) return;

            if (x >= 35 && x <= 140 && y >= 340) {
                left = true;
            }

            if (x >= 150 && x <= 225 && y >= 340) {
                right = true;
            }

            if (x >= 650 && y >= 330) {
                jumping = true;

                if (onGround) {
                    vy = jumpPower;
                    onGround = false;
                }
            }
        }
    }
}

