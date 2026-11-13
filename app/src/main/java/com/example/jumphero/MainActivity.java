

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.util.ArrayList;

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(new GamePanel(this));
    }

    class GamePanel extends FrameLayout {
        GameView game;

        GamePanel(Context c) {
            super(c);

            game = new GameView(c);
            addView(game, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout controls = new LinearLayout(c);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            controls.setPadding(15, 0, 15, 20);

            LayoutParams cp = new LayoutParams(-1, -2, Gravity.BOTTOM);
            addView(controls, cp);

            LinearLayout movement = new LinearLayout(c);
            Button left = makeButton(c, "◀");
            Button right = makeButton(c, "▶");
            Button jump = makeButton(c, "JUMP");

            movement.addView(left);
            movement.addView(right);

            controls.addView(movement,
                new LinearLayout.LayoutParams(0, -2, 1));

            controls.addView(jump);

            bind(left, 1);
            bind(right, 2);
            bind(jump, 3);
        }

        Button makeButton(Context c, String text) {
            Button b = new Button(c);
            b.setText(text);
            b.setTextSize(22);
            b.setTextColor(Color.WHITE);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xDD263238);
            bg.setCornerRadius(22);
            bg.setStroke(3, Color.WHITE);
            b.setBackground(bg);

            LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                    text.equals("JUMP") ? 125 : 95, 85
                );

            lp.setMargins(6, 0, 6, 0);
            b.setLayoutParams(lp);
            return b;
        }

        void bind(Button b, int action) {
            b.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    game.control(action, true);
                    return true;
                }

                if (e.getAction() == MotionEvent.ACTION_UP ||
                    e.getAction() == MotionEvent.ACTION_CANCEL) {
                    game.control(action, false);
                    return true;
                }
                return true;
            });
        }
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bitmap hero, cloud, castle, flower;
        Bitmap brick, pipe, enemy, tree, mountain, ground;
        Bitmap[] levelBackgrounds = new Bitmap[10];

        ArrayList<RectF> platforms = new ArrayList<>();
        ArrayList<Boolean> breakable = new ArrayList<>();
        ArrayList<RectF> pipes = new ArrayList<>();
        ArrayList<RectF> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Piece> pieces = new ArrayList<>();

        float x = 80, y = 350;
        float vx, vy, camera;
        float heroW = 65, heroH = 80;
        float worldW = 3500;

        boolean left, right, onGround, facingRight = true;
        boolean gameOver, won;

        int level = 1, score = 0, lives = 3;
        long lastTime = System.nanoTime();

        class Enemy {
            float x, min, max, speed = 1.5f;
            boolean alive = true;

            Enemy(float x) {
                this.x = x;
                min = x - 80;
                max = x + 80;
            }
        }

        class Piece {
            float x, y, vx, vy;

            Piece(float x, float y, float vx, float vy) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
            }
        }

        GameView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            loadImages(c);
            buildLevel();
        }

        Bitmap load(Context c, String name) {
            int id = getResources().getIdentifier(
                name, "drawable", c.getPackageName());

            if (id == 0) return null;

            try {
                return BitmapFactory.decodeResource(getResources(), id);
            } catch (Exception e) {
                return null;
            }
        }

        void loadImages(Context c) {
            hero = load(c, "doraemon");
            if (hero == null) hero = load(c, "doremon");

            cloud = load(c, "cloud");
            castle = load(c, "castle");
            flower = load(c, "flower");
            brick = load(c, "brick");
            pipe = load(c, "pipe");
            enemy = load(c, "enemy");
            tree = load(c, "tree");
            mountain = load(c, "mountain");
            ground = load(c, "ground");

            for (int i = 0; i < 10; i++) {
                levelBackgrounds[i] =
                    load(c, "level" + (i + 1) + "_background");
            }
        }

        void buildLevel() {
            platforms.clear();
            breakable.clear();
            pipes.clear();
            coins.clear();
            enemies.clear();
            pieces.clear();

            worldW = 3400 + level * 150;

            for (int i = 0; i < 12 + level; i++) {
                float px = 350 + i * 225;
                float py;
                float width;

                switch (level % 5) {
                    case 1:
                        py = 390 - (i % 3) * 55;
                        width = 125;
                        break;
                    case 2:
                        py = 365 - (i % 4) * 45;
                        width = 95;
                        break;
                    case 3:
                        py = 415 - (i % 2) * 80;
                        width = 160;
                        break;
                    case 4:
                        py = 445 - (i % 4) * 65;
                        width = 110;
                        break;
                    default:
                        py = 350 - (i % 3) * 70;
                        width = 105;
                        break;
                }

                platforms.add(new RectF(px, py, px + width, py + 28));
                breakable.add(i % 3 != 0);

                if (i % 2 == 0) {
                    coins.add(new RectF(
                        px + 40, py - 55, px + 65, py - 30));
                }
            }

            for (int i = 0; i < 4 + level % 4; i++) {
                float px = 650 + i * (420 + level * 5);
                pipes.add(new RectF(
                    px, 420 - (i % 3) * 15, px + 75, 510));
            }

            for (int i = 0; i < 4 + level; i++) {
                enemies.add(new Enemy(550 + i * 380));
            }

            x = 70;
            y = 250;
            vx = 0;
            vy = 0;
            camera = 0;
            onGround = false;
            left = false;
            right = false;
            facingRight = true;

            won = false;
            gameOver = false;
            lastTime = System.nanoTime();
        }

        void control(int action, boolean pressed) {
            if (action == 1) left = pressed;
            if (action == 2) right = pressed;

            if (action == 3 && pressed &&
                onGround && !gameOver && !won) {
                vy = -17;
                onGround = false;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float sx = getWidth() / 900f;
            float sy = getHeight() / 600f;

            canvas.save();
            canvas.scale(sx, sy);

            updateGame();
            drawWorld(canvas);

            canvas.restore();
            postInvalidateDelayed(16);
        }

        void updateGame() {
            if (gameOver || won) return;

            long now = System.nanoTime();
            float dt = Math.min(2,
                (now - lastTime) / 16666667f);
            lastTime = now;

            float speed = 4.5f + level * 0.15f;

            vx = left ? -speed : right ? speed : 0;

            if (left) facingRight = false;
            if (right) facingRight = true;

            x += vx * dt;
            if (x < 0) x = 0;

            float oldY = y;

            vy += 0.72f * dt;
            y += vy * dt;

            onGround = false;
            float floor = 510;

            if (y + heroH >= floor &&
                oldY + heroH <= floor + 12) {
                y = floor - heroH;
                vy = 0;
                onGround = true;
            }

            for (int i = platforms.size() - 1; i >= 0; i--) {
                RectF r = platforms.get(i);

                boolean hit = x + heroW - 7 > r.left &&
                              x + 7 < r.right;

                if (hit && vy >= 0 &&
                    oldY + heroH <= r.top + 8 &&
                    y + heroH >= r.top) {
                    y = r.top - heroH;
                    vy = 0;
                    onGround = true;
                } else if (hit && vy < 0 &&
                    oldY >= r.bottom - 5 &&
                    y <= r.bottom &&
                    breakable.get(i)) {

                    breakBrick(r);
                    platforms.remove(i);
                    breakable.remove(i);
                    score += 5;
                    y = r.bottom;
                    vy = 1.5f;
                }
            }

            updatePieces(dt);

            RectF player = new RectF(
                x + 8, y + 8, x + heroW - 8, y + heroH);

            for (int i = coins.size() - 1; i >= 0; i--) {
                if (RectF.intersects(player, coins.get(i))) {
                    coins.remove(i);
                    score += 10;
                }
            }

            for (Enemy e : enemies) {
                if (!e.alive) continue;

                e.x += e.speed * dt;

                if (e.x < e.min || e.x > e.max) {
                    e.speed *= -1;
                }

                RectF er = new RectF(e.x, 470, e.x + 45, 510);
                player.set(x + 8, y + 8,
                    x + heroW - 8, y + heroH);

                if (RectF.intersects(player, er)) {
                    if (vy > 1 && y + heroH < 490) {
                        e.alive = false;
                        vy = -9;
                        score += 20;
                    } else {
                        lives--;
                        x = Math.max(40, x - 150);
                        y = 200;
                        vy = 0;

                        if (lives <= 0) gameOver = true;
                    }
                }
            }

            for (RectF r : pipes) {
                player.set(x + 8, y + 8,
                    x + heroW - 8, y + heroH);

                if (RectF.intersects(player, r) &&
                    y + heroH > r.top + 12) {
                    if (vx > 0) x = r.left - heroW;
                    if (vx < 0) x = r.right;
                }
            }

            if (y > 650) {
                lives--;
                x = Math.max(40, x - 150);
                y = 200;
                vy = 0;
                if (lives <= 0) gameOver = true;
            }

            camera = Math.max(0,
                Math.min(worldW - 900, x - 350));

            if (x + heroW >= worldW - 120) {
                if (level < 10) {
                    level++;
                    buildLevel();
                } else {
                    won = true;
                }
            }
        }

        void breakBrick(RectF r) {
            float cx = r.centerX();
            float cy = r.centerY();

            pieces.add(new Piece(cx - 10, cy, -3, -6));
            pieces.add(new Piece(cx + 5, cy, 3, -7));
            pieces.add(new Piece(cx - 10, cy - 10, -2, -4));
            pieces.add(new Piece(cx + 5, cy - 10, 2, -5));
        }

        void updatePieces(float dt) {
            for (int i = pieces.size() - 1; i >= 0; i--) {
                Piece b = pieces.get(i);
                b.x += b.vx * dt;
                b.y += b.vy * dt;
                b.vy += 0.6f * dt;

                if (b.y > 620) pieces.remove(i);
            }
        }

        void drawBitmap(Canvas c, Bitmap bitmap,
                        float x, float y, float w, float h) {
            if (bitmap != null) {
                c.drawBitmap(bitmap, null,
                    new RectF(x, y, x + w, y + h), p);
            }
        }

        void drawWorld(Canvas c) {
            c.drawColor(0xFF75C9F4);

            Bitmap bg = levelBackgrounds[level - 1];

            if (bg != null) {
                drawBitmap(c, bg, 0, 0, 900, 510);
            } else {
                drawMountains(c);
            }

            c.save();
            c.translate(-camera, 0);

            if (cloud != null) {
                for (int i = 0; i < 12; i++) {
                    drawBitmap(c, cloud,
                        i * 280, 55 + (i % 3) * 45, 115, 65);
                }
            }

            if (castle != null) {
                for (int i = 0; i < 4; i++) {
                    drawBitmap(c, castle,
                        500 + i * 1050, 310, 220, 200);
                }
            }

            if (tree != null) {
                for (int i = 0; i < 30; i++) {
                    drawBitmap(c, tree,
                        i * 135, 390, 95, 125);
                }
            }

            if (mountain != null) {
                for (int i = 0; i < 12; i++) {
                    drawBitmap(c, mountain,
                        i * 330, 285, 250, 225);
                }
            }

            p.setColor(0xFF36A83B);
            c.drawRect(0, 510, worldW, 600, p);

            p.setColor(0xFF92552D);
            c.drawRect(0, 535, worldW, 600, p);

            if (ground != null) {
                for (int i = 0; i < worldW; i += 64) {
                    drawBitmap(c, ground, i, 510, 64, 70);
                }
            }

            if (flower != null) {
                for (int i = 0; i < worldW / 100; i++) {
                    drawBitmap(c, flower,
                        i * 100 + 15, 475, 35, 40);
                }
            }

            for (RectF r : platforms) {
                if (brick != null) {
                    c.drawBitmap(brick, null, r, p);
                } else {
                    p.setColor(0xFFB7652B);
                    c.drawRect(r, p);
                    p.setColor(0xFFFFC078);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3);
                    c.drawRect(r, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }

            for (Piece b : pieces) {
                p.setColor(0xFFB7652B);
                c.drawRect(b.x, b.y,
                    b.x + 14, b.y + 14, p);
            }

            for (RectF r : pipes) {
                if (pipe != null) {
                    c.drawBitmap(pipe, null, r, p);
                } else {
                    p.setColor(0xFF168C36);
                    c.drawRect(r, p);
                }
            }

            for (RectF r : coins) {
                p.setColor(0xFFFFD52A);
                c.drawOval(r, p);
            }

            for (Enemy e : enemies) {
                if (!e.alive) continue;

                if (enemy != null) {
                    drawBitmap(c, enemy, e.x, 465, 48, 45);
                } else {
                    p.setColor(0xFF9A4C25);
                    c.drawOval(e.x, 470, e.x + 45, 510, p);
                }
            }

            float fx = worldW - 105;

            p.setColor(Color.WHITE);
            p.setStrokeWidth(7);
            c.drawLine(fx, 260, fx, 510, p);

            Path flag = new Path();
            flag.moveTo(fx, 265);
            flag.lineTo(fx + 85, 290);
            flag.lineTo(fx, 320);
            flag.close();

            p.setColor(Color.RED);
            c.drawPath(flag, p);

            if (hero != null) {
                RectF dest = new RectF(
                    x, y, x + heroW, y + heroH);

                if (facingRight) {
                    c.drawBitmap(hero, null, dest, p);
                } else {
                    c.save();
                    c.scale(-1, 1,
                        x + heroW / 2, y + heroH / 2);
                    c.drawBitmap(hero, null, dest, p);
                    c.restore();
                }
            } else {
                p.setColor(0xFF168ED0);
                c.drawOval(x, y, x + heroW, y + heroH, p);
                p.setColor(Color.WHITE);
                c.drawCircle(x + 23, y + 20, 9, p);
                c.drawCircle(x + 43, y + 20, 9, p);
            }

            c.restore();

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText("LEVEL " + level + "/10", 25, 40, p);
            c.drawText("SCORE " + score, 250, 40, p);
            c.drawText("LIVES " + lives, 470, 40, p);

            if (gameOver || won) {
                p.setColor(0xCC000000);
                c.drawRect(0, 0, 900, 600, p);

                p.setColor(Color.WHITE);
                p.setTextSize(48);
                p.setTextAlign(Paint.Align.CENTER);

                c.drawText(
                    won ? "YOU WIN!" : "GAME OVER",
                    450, 270, p);

                p.setTextSize(23);
                c.drawText("Tap screen to restart",
                    450, 320, p);

                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        void drawMountains(Canvas c) {
            p.setColor(0xFF477C9C);

            for (int i = 0; i < 8; i++) {
                float mx = i * 150;
                Path path = new Path();

                path.moveTo(mx, 510);
                path.lineTo(mx + 90, 275 + (i % 3) * 30);
                path.lineTo(mx + 180, 510);
                path.close();

                c.drawPath(path, p);

                p.setColor(Color.WHITE);
                c.drawCircle(
                    mx + 90, 290 + (i % 3) * 30, 14, p);

                p.setColor(0xFF477C9C);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if ((gameOver || won) &&
                e.getAction() == MotionEvent.ACTION_UP) {
                level = 1;
                score = 0;
                lives = 3;
                buildLevel();
                invalidate();
                return true;
            }
            return true;
        }
    }
}
