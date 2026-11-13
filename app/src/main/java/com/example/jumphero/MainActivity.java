
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
        ArrayList<Boolean> platformBreakable = new ArrayList<>();
        ArrayList<BrickPiece> brickPieces = new ArrayList<>();
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

        static class BrickPiece {
            float x, y, vx, vy, size, rotation;
            BrickPiece(float x, float y, float vx, float vy) {
                this.x=x; this.y=y; this.vx=vx; this.vy=vy; size=13;
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
            platforms.clear(); platformBreakable.clear(); brickPieces.clear();
            pipes.clear(); coins.clear(); enemies.clear();
            floorY = 0;
            worldW = 3300 + level * 190;

            // Every level gets a different obstacle rhythm and platform shape.
            for (int i=0; i<10 + level; i++) {
                float px, py, width;
                switch (level % 5) {
                    case 1: // Classic: floating brick steps
                        px = 340 + i * 225; py = 395 - (i % 3) * 58; width = 125;
                        break;
                    case 2: // High jumps: narrow, tall platforms
                        px = 310 + i * 238; py = 365 - (i % 4) * 48; width = 92;
                        break;
                    case 3: // Long platforms and low tunnels
                        px = 390 + i * 215; py = 415 - (i % 2) * 82; width = 165;
                        break;
                    case 4: // Zig-zag staircase
                        px = 330 + i * 220; py = 445 - (i % 4) * 66; width = 112;
                        break;
                    default: // Sky challenge
                        px = 360 + i * 230; py = 350 - (i % 3) * 75; width = 105;
                        break;
                }
                platforms.add(new RectF(px, py, px + width, py + 26));
                platformBreakable.add((i % 3 != 0));
                if ((i + level) % 2 == 0)
                    coins.add(new RectF(px + width/2 - 12, py - 55, px + width/2 + 12, py - 31));
            }

            // Pipe count, spacing and height change from level to level.
            int pipeCount = 3 + level % 5;
            for (int i=0; i<pipeCount; i++) {
                float px = 600 + i * (390 + (level % 3) * 45);
                float height = 65 + ((i + level) % 3) * 22;
                pipes.add(new RectF(px, 510-height, px+72, 600));
            }

            // Enemy patrol areas are different on every level.
            for (int i=0; i<4 + level; i++) {
                float ex = 480 + i * (310 + (level % 4) * 28);
                enemies.add(new Enemy(ex, 0, ex-65-(level*3), ex+75+(level*4)));
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
            for (int i=platforms.size()-1; i>=0; i--) {
                RectF r = platforms.get(i);
                boolean horizontalHit = x+heroW-7 > r.left && x+7 < r.right;
                // Land on top of a platform.
                if (horizontalHit && oldY+heroH <= r.top+8 &&
                    y+heroH >= r.top && vy >= 0) {
                    y = r.top-heroH; vy=0; onGround=true;
                }
                // Hit the underside while moving upward: break breakable bricks.
                else if (horizontalHit && vy < 0 && oldY >= r.bottom-5 &&
                         y <= r.bottom && platformBreakable.get(i)) {
                    makeBrickPieces(r);
                    platforms.remove(i);
                    platformBreakable.remove(i);
                    score += 5;
                    y = r.bottom;
                    vy = 1.5f;
                }
            }
            updateBrickPieces(dt);
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

        void makeBrickPieces(RectF r) {
            float mid = r.centerX();
            brickPieces.add(new BrickPiece(mid-12, r.centerY(), -3.2f, -6.5f));
            brickPieces.add(new BrickPiece(mid+2, r.centerY(), 3.0f, -7.2f));
            brickPieces.add(new BrickPiece(mid-12, r.centerY()-9, -2.5f, -3.5f));
            brickPieces.add(new BrickPiece(mid+2, r.centerY()-9, 2.8f, -4.2f));
        }

        void updateBrickPieces(float dt) {
            for (int i=brickPieces.size()-1; i>=0; i--) {
                BrickPiece b = brickPieces.get(i);
                b.x += b.vx*dt;
                b.y += b.vy*dt;
                b.vy += 0.55f*dt;
                b.rotation += 8*dt;
                if (b.y > 620) brickPieces.remove(i);
            }
        }

        void drawWorld(Canvas c) {
            // Different visual theme for each of the ten stages.
            int[] skies = {0xFF75C9F4, 0xFFFFC56E, 0xFF25345F, 0xFFB8E6F5, 0xFFFF9B70,
                           0xFF6C9CE8, 0xFFB4D8A0, 0xFF252B4A, 0xFFFFD98B, 0xFF9AD9D2};
            int[] hills = {0xFF8EDB83, 0xFFC58A43, 0xFF4A5B91, 0xFFECF8FF, 0xFFB84F42,
                           0xFF4767A8, 0xFF4C9B62, 0xFF55506F, 0xFFDAA64B, 0xFF397F83};
            int[] soils = {0xFF92552D, 0xFFB97835, 0xFF343B55, 0xFF9AA8B0, 0xFF71382E,
                           0xFF55442F, 0xFF6A4930, 0xFF292638, 0xFF8B5A2B, 0xFF315B54};
            int theme = (level-1) % 10;
            c.drawColor(skies[theme]);

            // Theme-specific sky details: sun/moon and stars.
            p.setStyle(Paint.Style.FILL);
            if (theme == 2 || theme == 7) {
                p.setColor(0xFFFFF2B0);
                c.drawCircle(735, 105, 35, p);
                p.setColor(0xFFFFFFFF);
                for (int i=0; i<22; i++) {
                    float sx = (i*137 + 35) % 880;
                    float sy = 35 + (i*47)%230;
                    c.drawCircle(sx, sy, 2.5f, p);
                }
            } else {
                p.setColor(0xFFFFE17A);
                c.drawCircle(760, 105, 42, p);
                p.setColor(0xFFFFFFFF);
                for (int i=0; i<4; i++) {
                    float cx = 110 + i*230 - (camera*0.12f)%920;
                    c.drawOval(cx, 95+(i%2)*45, cx+85, 125+(i%2)*45, p);
                }
            }

            // Detailed distant mountains and trees, drawn behind the playable world.
            for (int i=0; i<7; i++) {
                float mx = i*185 - (camera*0.18f)%185 - 40;
                drawMountain(c, mx, 500, 95 + (i%3)*22, 265 + (i%2)*38,
                        hills[theme], theme == 3 || theme == 5 || theme == 7);
            }
            for (int i=0; i<10; i++) {
                float tx = i*112 - (camera*0.32f)%112 - 20;
                float base = 510;
                if (theme == 1 || theme == 4 || theme == 8) {
                    drawTree(c, tx, base, 0.78f + (i%3)*0.12f, 0xFF79502D,
                            0xFF3D8C43, true);
                } else if (theme == 2 || theme == 7) {
                    drawTree(c, tx, base, 0.72f + (i%3)*0.10f, 0xFF3B354A,
                            0xFF343D68, true);
                } else {
                    drawTree(c, tx, base, 0.82f + (i%3)*0.12f, 0xFF76502E,
                            theme == 3 ? 0xFFB9D9E8 : 0xFF237B3C, false);
                }
            }
            c.save();
            c.translate(-camera, 0);

            if (bgBitmap != null) {
                p.setAlpha(150);
                c.drawBitmap(bgBitmap, null, new RectF(camera,0,camera+worldW,510), p);
                p.setAlpha(255);
            }

            // Ground.
            int[] grass = {0xFF36A83B,0xFFB9A04A,0xFF4D5D79,0xFFE9F5F8,0xFF8E4939,
                           0xFF567B39,0xFF2D8B48,0xFF3C344F,0xFFB58A36,0xFF28756B};
            p.setColor(grass[theme]);
            c.drawRect(0,510,worldW,600,p);
            p.setColor(soils[theme]);
            c.drawRect(0,530,worldW,600,p);
            if (groundBitmap != null) {
                for (int gx=0; gx<worldW; gx+=64)
                    c.drawBitmap(groundBitmap,null,new RectF(gx,510,gx+64,574),p);
            }
            // Brick platforms.
            for (RectF r : platforms) drawTile(c,r,brickBitmap,0xFFB7652B);
            // Animated fragments from broken bricks.
            for (BrickPiece b : brickPieces) {
                c.save();
                c.rotate(b.rotation, b.x+7, b.y+7);
                p.setColor(0xFFB7652B);
                c.drawRect(b.x, b.y, b.x+14, b.y+14, p);
                p.setColor(0xFFFFC078);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
                c.drawRect(b.x, b.y, b.x+14, b.y+14, p);
                p.setStyle(Paint.Style.FILL);
                c.restore();
            }
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

        void drawMountain(Canvas c, float centerX, float baseY, float halfWidth,
                          float height, int mountainColor, boolean snowy) {
            Path mountain = new Path();
            mountain.moveTo(centerX-halfWidth, baseY);
            mountain.lineTo(centerX, baseY-height);
            mountain.lineTo(centerX+halfWidth, baseY);
            mountain.close();
            p.setColor(mountainColor);
            c.drawPath(mountain, p);

            // Lit face gives the mountain a more natural 3D appearance.
            Path litFace = new Path();
            litFace.moveTo(centerX, baseY-height);
            litFace.lineTo(centerX-halfWidth*0.12f, baseY-height*0.48f);
            litFace.lineTo(centerX+halfWidth*0.02f, baseY-height*0.55f);
            litFace.lineTo(centerX+halfWidth*0.42f, baseY);
            litFace.close();
            p.setColor(snowy ? 0xFF9CB7C9 : 0xFF6B9A69);
            c.drawPath(litFace, p);

            if (snowy) {
                Path snow = new Path();
                snow.moveTo(centerX, baseY-height);
                snow.lineTo(centerX-halfWidth*0.22f, baseY-height*0.68f);
                snow.lineTo(centerX-halfWidth*0.08f, baseY-height*0.70f);
                snow.lineTo(centerX, baseY-height*0.78f);
                snow.lineTo(centerX+halfWidth*0.10f, baseY-height*0.67f);
                snow.lineTo(centerX+halfWidth*0.23f, baseY-height*0.70f);
                snow.close();
                p.setColor(0xFFF7FCFF);
                c.drawPath(snow, p);
            }
        }

        void drawTree(Canvas c, float centerX, float baseY, float scale,
                      int trunkColor, int leafColor, boolean pine) {
            float trunkW = 13*scale;
            float trunkH = 54*scale;
            p.setColor(trunkColor);
            c.drawRect(centerX-trunkW/2, baseY-trunkH, centerX+trunkW/2, baseY, p);

            if (pine) {
                p.setColor(leafColor);
                for (int layer=0; layer<3; layer++) {
                    float top = baseY-trunkH-66*scale + layer*28*scale;
                    float half = (25+layer*13)*scale;
                    Path leaves = new Path();
                    leaves.moveTo(centerX, top);
                    leaves.lineTo(centerX-half, top+48*scale);
                    leaves.lineTo(centerX+half, top+48*scale);
                    leaves.close();
                    c.drawPath(leaves, p);
                }
            } else {
                p.setColor(leafColor);
                c.drawCircle(centerX, baseY-trunkH-28*scale, 30*scale, p);
                c.drawCircle(centerX-22*scale, baseY-trunkH-12*scale, 23*scale, p);
                c.drawCircle(centerX+22*scale, baseY-trunkH-12*scale, 23*scale, p);
                p.setColor(0x55FFFFFF);
                c.drawCircle(centerX-10*scale, baseY-trunkH-34*scale, 9*scale, p);
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
