
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(new GamePanel(this));
    }

    static class GamePanel extends FrameLayout {
        GameView game;
        GamePanel(Context c) {
            super(c);
            game = new GameView(c);
            addView(game, new LayoutParams(-1, -1));

            LinearLayout controls = new LinearLayout(c);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setPadding(18, 0, 18, 22);
            LayoutParams cp = new LayoutParams(-1, -2, Gravity.BOTTOM);
            addView(controls, cp);

            LinearLayout leftGroup = new LinearLayout(c);
            leftGroup.setOrientation(LinearLayout.HORIZONTAL);
            Button left = controlButton(c, "◀");
            Button right = controlButton(c, "▶");
            leftGroup.addView(left);
            leftGroup.addView(right);
            controls.addView(leftGroup, new LinearLayout.LayoutParams(0, -2, 1));

            Button jump = controlButton(c, "JUMP");
            controls.addView(jump);
            bindHold(left, 1);
            bindHold(right, 2);
            bindHold(jump, 3);
        }

        Button controlButton(Context c, String label) {
            Button b = new Button(c);
            b.setText(label);
            b.setTextSize(label.equals("JUMP") ? 17 : 27);
            b.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC263238);
            bg.setCornerRadius(24);
            bg.setStroke(3, Color.WHITE);
            b.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    label.equals("JUMP") ? dp(c, 112) : dp(c, 82), dp(c, 76));
            p.setMargins(dp(c, 5), 0, dp(c, 5), 0);
            b.setLayoutParams(p);
            return b;
        }

        void bindHold(Button b, final int action) {
            b.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    game.setControl(action, true);
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP ||
                    e.getAction() == MotionEvent.ACTION_CANCEL) {
                    game.setControl(action, false);
                    return true;
                }
                return true;
            });
        }
        static int dp(Context c, int n) {
            return (int)(n * c.getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    static class GameView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ArrayList<RectF> platforms = new ArrayList<>();
        ArrayList<RectF> pipes = new ArrayList<>();
        ArrayList<RectF> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();
        Bitmap heroBitmap, brickBitmap, groundBitmap, pipeBitmap, enemyBitmap, bgBitmap;
        float worldW = 3600, floorY, x = 90, y = 0, vx = 0, vy = 0, camera = 0;
        float heroW = 62, heroH = 76;
        boolean left, right, jump, onGround = false, facingRight = true;
        int level = 1, score = 0, lives = 3;
        long lastTime = System.nanoTime();
        boolean gameOver = false, won = false;
        Random random = new Random();

        static class Enemy {
            float x, y, min, max, speed = 1.4f;
            boolean alive = true;
            Enemy(float x, float y, float min, float max) {
                this.x=x; this.y=y; this.min=min; this.max=max;
            }
        }

        GameView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            loadImages(c);
            buildLevel();
            setFocusable(true);
        }

        int imageId(Context c, String... names) {
            for (String n : names) {
                int id = c.getResources().getIdentifier(n, "drawable", c.getPackageName());
                if (id != 0) return id;
            }
            return 0;
        }

        Bitmap load(Context c, String... names) {
            int id = imageId(c, names);
            if (id == 0) return null;
            try { return BitmapFactory.decodeResource(c.getResources(), id); }
            catch (Exception ex) { return null; }
        }

        void loadImages(Context c) {
            heroBitmap = load(c, "doraemon", "doremon", "player", "hero");
            brickBitmap = load(c, "brick", "bricks", "block");
            groundBitmap = load(c, "ground", "tile", "grass");
            pipeBitmap = load(c, "pipe", "mario_pipe");
            enemyBitmap = load(c, "enemy", "goomba", "monster");
            bgBitmap = load(c, "background", "bg", "level_background");
        }

        void buildLevel() {
            platforms.clear(); pipes.clear(); coins.clear(); enemies.clear();
            floorY = 0;
            worldW = 3000 + level * 260;
            // Raised brick platforms, with gaps and pipes in the ground.
            for (int i=0; i<12 + level; i++) {
                float px = 360 + i * 205;
                float py = 390 - (i % 3) * 65;
                platforms.add(new RectF(px, py, px + 130, py + 28));
                if (i % 2 == 0) coins.add(new RectF(px + 45, py - 58, px + 70, py - 33));
            }
            for (int i=0; i<5 + level/2; i++) {
                float px = 650 + i * 470;
                pipes.add(new RectF(px, 505 - (i%3)*16, px+78, 600));
            }
            for (int i=0; i<5 + level; i++) {
                float ex = 520 + i * 390;
                enemies.add(new Enemy(ex, 0, ex-90, ex+90));
            }
            x = 70; y = 0; vx = 0; vy = 0; camera = 0;
            won = false; gameOver = false;
        }

        void setControl(int action, boolean down) {
            if (action == 1) left = down;
            if (action == 2) right = down;
            if (action == 3) {
                if (down && !jump && onGround && !gameOver && !won) {
                    vy = -15.5f;
                    onGround = false;
                }
                jump = down;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
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
            float dt = Math.min(2.0f, (now-lastTime)/16_666_667f);
            lastTime = now;
            float speed = 4.5f + level*0.12f;
            vx = left ? -speed : (right ? speed : 0);
            if (left) facingRight = false;
            if (right) facingRight = true;
            x += vx*dt;
            if (x < 0) x = 0;
            vy += 0.72f*dt;
            float oldY = y;
            y += vy*dt;
            onGround = false;
            float ground = 510;
            // Land on ground or raised platforms.
            if (y + heroH >= ground && oldY + heroH <= ground + 12) {
                y = ground-heroH; vy=0; onGround=true;
            }
            for (RectF r : platforms) {
                if (x+heroW > r.left && x < r.right &&
                    oldY+heroH <= r.top+8 && y+heroH >= r.top && vy >= 0) {
                    y = r.top-heroH; vy=0; onGround=true;
                }
            }
            // Pipes act as solid obstacles; jump over them.
            for (RectF r : pipes) {
                RectF player = new RectF(x+8,y+8,x+heroW-8,y+heroH);
                if (RectF.intersects(player, r) && y+heroH > r.top+12) {
                    if (vx > 0) x = r.left-heroW;
                    else if (vx < 0) x = r.right;
                }
            }
            if (y > 700) {
                lives--;
                if (lives <= 0) gameOver = true;
                else { x = Math.max(40, x-160); y=180; vy=0; }
            }
            for (int i=coins.size()-1; i>=0; i--) {
                if (RectF.intersects(new RectF(x,y,x+heroW,y+heroH), coins.get(i))) {
                    coins.remove(i); score += 10;
                }
            }
            for (Enemy e : enemies) {
                if (!e.alive) continue;
                e.x += e.speed*dt;
                if (e.x < e.min || e.x > e.max) e.speed *= -1;
                RectF er = new RectF(e.x, 470, e.x+42, 510);
                RectF pr = new RectF(x+8,y+8,x+heroW-8,y+heroH);
                if (RectF.intersects(pr, er)) {
                    if (vy > 1 && y+heroH < 490) {
                        e.alive=false; vy=-8; score+=20;
                    } else {
                        lives--;
                        x=Math.max(40,x-130); y=220; vy=0;
                        if (lives<=0) gameOver=true;
                    }
                }
            }
            camera = Math.max(0, Math.min(worldW-900, x-350));
            if (x + heroW >= worldW-120) {
                if (level < 10) { level++; buildLevel(); }
                else won = true;
            }
        }

        void drawWorld(Canvas c) {
            c.drawColor(0xFF75C9F4);
            // Distant hills.
            p.setColor(0xFF8EDB83);
            for (int i=0; i<8; i++) {
                float hx = i*260 - (camera*0.25f)%260;
                c.drawOval(hx, 360, hx+190, 530, p);
            }
            c.save();
            c.translate(-camera, 0);

            if (bgBitmap != null) {
                p.setAlpha(150);
                c.drawBitmap(bgBitmap, null, new RectF(camera,0,camera+worldW,510), p);
                p.setAlpha(255);
            }

            // Ground.
            p.setColor(0xFF36A83B);
            c.drawRect(0,510,worldW,600,p);
            p.setColor(0xFF92552D);
            c.drawRect(0,530,worldW,600,p);
            if (groundBitmap != null) {
                for (int gx=0; gx<worldW; gx+=64)
                    c.drawBitmap(groundBitmap,null,new RectF(gx,510,gx+64,574),p);
            }
            // Brick platforms.
            for (RectF r : platforms) drawTile(c,r,brickBitmap,0xFFB7652B);
            // Pipes.
            for (RectF r : pipes) {
                if (pipeBitmap != null) c.drawBitmap(pipeBitmap,null,r,p);
                else {
                    p.setColor(0xFF168C36); c.drawRect(r,p);
                    p.setColor(0xFF39C85A); c.drawRect(r.left-8,r.top,r.right+8,r.top+18,p);
                    p.setColor(0xFF075E25); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); c.drawRect(r,p); p.setStyle(Paint.Style.FILL);
                }
            }
            // Coins.
            for (RectF r : coins) {
                p.setColor(0xFFFFD52A); c.drawOval(r,p);
                p.setColor(0xFFFFF29B); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawOval(r,p); p.setStyle(Paint.Style.FILL);
            }
            // Enemies.
            for (Enemy e : enemies) if (e.alive) {
                if (enemyBitmap != null) c.drawBitmap(enemyBitmap,null,new RectF(e.x,468,e.x+48,512),p);
                else {
                    p.setColor(0xFF9A4C25); c.drawOval(e.x,472,e.x+44,512,p);
                    p.setColor(Color.WHITE); c.drawCircle(e.x+13,488,5,p); c.drawCircle(e.x+31,488,5,p);
                    p.setColor(Color.BLACK); c.drawCircle(e.x+14,489,2,p); c.drawCircle(e.x+32,489,2,p);
                }
            }
            // Finish flag and pole.
            float flagX=worldW-105;
            p.setColor(0xFFECECEC); p.setStrokeWidth(7); c.drawLine(flagX,260,flagX,510,p);
            Path flag = new Path(); flag.moveTo(flagX,265); flag.lineTo(flagX+85,290); flag.lineTo(flagX,320); flag.close();
            p.setColor(0xFFE52E35); c.drawPath(flag,p);
            // Player.
            RectF player = new RectF(x,y,x+heroW,y+heroH);
            if (heroBitmap != null) {
                if (facingRight) c.drawBitmap(heroBitmap,null,player,p);
                else {
                    c.save(); c.scale(-1,1,x+heroW/2,y+heroH/2);
                    c.drawBitmap(heroBitmap,null,player,p); c.restore();
                }
            } else drawDoraemonPlaceholder(c,player);
            c.restore();

            p.setColor(Color.WHITE); p.setTextSize(25); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("LEVEL " + level + "/10", 24, 38, p);
            c.drawText("SCORE " + score, 250, 38, p);
            c.drawText("LIVES " + lives, 470, 38, p);
            if (gameOver || won) {
                p.setColor(0xCC000000); c.drawRect(0,0,900,600,p);
                p.setColor(Color.WHITE); p.setTextSize(48); p.setTextAlign(Paint.Align.CENTER);
                c.drawText(won ? "YOU WIN!" : "GAME OVER",450,270,p);
                p.setTextSize(23);
                c.drawText("Tap screen to restart",450,320,p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        void drawTile(Canvas c, RectF r, Bitmap bitmap, int color) {
            if (bitmap != null) c.drawBitmap(bitmap,null,r,p);
            else {
                p.setColor(color); c.drawRect(r,p);
                p.setColor(0xFFFFC078); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawRect(r,p);
                c.drawLine(r.left,r.centerY(),r.right,r.centerY(),p);
                c.drawLine(r.centerX(),r.top,r.centerX(),r.centerY(),p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void drawDoraemonPlaceholder(Canvas c, RectF r) {
            // Simple blue robot-cat fallback if no character PNG is found.
            p.setColor(0xFF168ED0); c.drawOval(r.left+3,r.top+3,r.right-3,r.bottom-2,p);
            p.setColor(Color.WHITE); c.drawOval(r.left+12,r.top+15,r.right-12,r.top+53,p);
            p.setColor(Color.WHITE); c.drawCircle(r.left+22,r.top+14,9,p); c.drawCircle(r.right-22,r.top+14,9,p);
            p.setColor(Color.BLACK); c.drawCircle(r.left+23,r.top+17,3,p); c.drawCircle(r.right-23,r.top+17,3,p);
            p.setColor(0xFFE53935); c.drawCircle(r.centerX(),r.top+29,5,p);
            p.setColor(Color.BLACK); p.setStrokeWidth(2);
            c.drawLine(r.left+15,r.top+38,r.left+2,r.top+34,p);
            c.drawLine(r.right-15,r.top+38,r.right-2,r.top+34,p);
            p.setColor(0xFFE53935); c.drawRect(r.centerX()-9,r.top+55,r.centerX()+9,r.top+61,p);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if ((gameOver || won) && e.getAction()==MotionEvent.ACTION_UP) {
                level=1; score=0; lives=3; buildLevel(); invalidate(); return true;
            }
            return true;
        }
    }
}
