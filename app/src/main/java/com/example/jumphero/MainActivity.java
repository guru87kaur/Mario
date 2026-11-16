

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Iterator;

public class MainActivity extends Activity {

    private GameView gameView;
    private AudioTrack music;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        getWindow().setBackgroundDrawableResource(
                android.R.color.black);

        gameView = new GameView(this);
        gameView.setBackgroundColor(Color.rgb(95, 190, 255));
        setContentView(gameView);

        startMusic();
    }

    private void startMusic() {
        if (music != null) {
            if (music.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                music.play();
            return;
        }

        int rate = 22050;

        double[] notes = {
            523.25, 659.25, 783.99, 659.25,
            523.25, 587.33, 659.25, 587.33,
            523.25, 783.99, 880.00, 783.99,
            659.25, 587.33, 523.25, 392.00
        };

        int each = rate / 5;
        short[] data = new short[each * notes.length];

        int k = 0;

        for (double f : notes) {
            for (int i = 0; i < each; i++) {
                double env = Math.min(1.0, i / 250.0)
                        * Math.min(1.0, (each - i) / 250.0);

                data[k++] = (short)(
                        Math.sin(2 * Math.PI * f * i / rate)
                        * env * 4200);
            }
        }

        try {
            music = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    data.length * 2,
                    AudioTrack.MODE_STATIC);

            music.write(data, 0, data.length);
            music.setLoopPoints(0, data.length, -1);
            music.setVolume(0.30f);
            music.play();

        } catch (Exception ignored) {
            if (music != null) {
                music.release();
                music = null;
            }
        }
    }

    private void stopMusic() {
        if (music != null &&
                music.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            music.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMusic();
    }

    @Override
    protected void onPause() {
        stopMusic();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (music != null) {
            try {
                music.stop();
            } catch (Exception ignored) {}

            music.release();
            music = null;
        }

        if (gameView != null)
            gameView.release();

        super.onDestroy();
    }

    private class GameView extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ArrayList<Platform> platforms = new ArrayList<>();
        private final ArrayList<Brick> bricks = new ArrayList<>();
        private final ArrayList<Coin> coins = new ArrayList<>();
        private final ArrayList<Enemy> enemies = new ArrayList<>();
        private final ArrayList<Shot> shots = new ArrayList<>();

        private ToneGenerator tones;

        private Bitmap idle, run, crawl;
        private Bitmap bomb, turtle, bullet;

        private float sw, sh;
        private float cameraX;
        private float groundTop;

        private float worldWidth = 5200;
        private float x = 100, y;
        private float vx, vy;

        private float pw = 65, ph = 78;

        private float flagX;

        private boolean left, right;
        private boolean jumpHeld, fireHeld;
        private boolean onGround;
        private boolean paused, gameOver;
        private boolean facingRight = true;

        private int score = 0;
        private int lives = 3;
        private int level = 1;

        private long lastTime;
        private long lastShot;
        private long invincibleUntil;

        private float lastGround = -1;

        GameView(Context c) {
            super(c);

            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            tones = new ToneGenerator(
                    AudioManager.STREAM_MUSIC, 55);

            idle = load("hero_idle");
            run = load("hero_run");
            crawl = load("hero_crawl");

            bomb = load("bomb");
            turtle = load("turtle");
            bullet = load("bullet");
        }

        private Bitmap load(String name) {
            int id = getResources().getIdentifier(
                    name, "drawable", getPackageName());

            if (id == 0) return null;

            try {
                Bitmap original = BitmapFactory.decodeResource(
                        getResources(), id);

                if (original == null) return null;

                return removeEdgeBackground(original);

            } catch (Exception e) {
                return null;
            }
        }

        // Removes only the background connected to the image edges.
        // This is safer than removing every blue pixel in Doraemon.
        private Bitmap removeEdgeBackground(Bitmap source) {

            if (source.hasAlpha()) return source;

            int w = source.getWidth();
            int h = source.getHeight();

            if (w * (long) h > 2000000L) return source;

            Bitmap result = source.copy(
                    Bitmap.Config.ARGB_8888, true);

            int[] pixels = new int[w * h];
            result.getPixels(pixels, 0, w, 0, 0, w, h);

            int background = pixels[0];

            int br = Color.red(background);
            int bg = Color.green(background);
            int bb = Color.blue(background);

            if (Color.alpha(background) == 0) return result;

            int[] queue = new int[w * h];
            boolean[] visited = new boolean[w * h];

            int head = 0;
            int tail = 0;

            queue[tail++] = 0;
            visited[0] = true;

            while (head < tail) {

                int index = queue[head++];

                int color = pixels[index];

                int dr = Math.abs(Color.red(color) - br);
                int dg = Math.abs(Color.green(color) - bg);
                int db = Math.abs(Color.blue(color) - bb);

                if (dr + dg + db > 65) continue;

                pixels[index] = Color.TRANSPARENT;

                int px = index % w;
                int py = index / w;

                if (px > 0 && !visited[index - 1]) {
                    visited[index - 1] = true;
                    queue[tail++] = index - 1;
                }

                if (px < w - 1 && !visited[index + 1]) {
                    visited[index + 1] = true;
                    queue[tail++] = index + 1;
                }

                if (py > 0 && !visited[index - w]) {
                    visited[index - w] = true;
                    queue[tail++] = index - w;
                }

                if (py < h - 1 && !visited[index + w]) {
                    visited[index + w] = true;
                    queue[tail++] = index + w;
                }
            }

            result.setPixels(pixels, 0, w, 0, 0, w, h);

            return result;
        }

        @Override
        protected void onSizeChanged(
                int w, int h, int oldw, int oldh) {

            super.onSizeChanged(w, h, oldw, oldh);

            sw = w;
            sh = h;

            groundTop = sh * 0.40f;

            pw = Math.max(48, sh * 0.09f);
            ph = Math.max(58, sh * 0.115f);

            if (lastGround < 0) {
                lastGround = groundTop;
                buildLevel();
            } else {
                float difference = groundTop - lastGround;
                y += difference;

                for (Platform a : platforms) a.y += difference;
                for (Brick b : bricks) b.y += difference;
                for (Coin c : coins) c.y += difference;
                for (Enemy e : enemies) e.y += difference;

                lastGround = groundTop;
            }
        }

        private void buildLevel() {

            platforms.clear();
            bricks.clear();
            coins.clear();
            enemies.clear();
            shots.clear();

            worldWidth = 5200 + (level - 1) * 600;

            flagX = worldWidth - 180;

            float floorHeight = sh > 0 ? sh * 0.09f : 65;

            platforms.add(new Platform(
                    0, groundTop, worldWidth, floorHeight));

            for (int i = 0; i < 24; i++) {

                float bx = 420 + i * 190;

                float by = groundTop - 100 - (i % 3) * 45;

                platforms.add(new Platform(
                        bx, by, 130, 20));

                if (i % 2 == 0) {
                    bricks.add(new Brick(bx + 30, by - 48));
                    bricks.add(new Brick(bx + 70, by - 48));
                }

                coins.add(new Coin(bx + 65, by - 65));

                if (i % 3 == 0) {
                    enemies.add(new Enemy(
                            bx + 95, groundTop - 55, i % 2));
                }
            }

            for (int i = 0; i < 14; i++) {

                float bx = 250 + i * 330;

                bricks.add(new Brick(
                        bx, groundTop - 140));

                coins.add(new Coin(
                        bx + 45, groundTop - 185));
            }

            x = 100;
            y = groundTop - ph;

            vx = 0;
            vy = 0;

            cameraX = 0;
            onGround = true;

            invincibleUntil =
                    System.currentTimeMillis() + 1200;

            lastTime = 0;
        }

        @Override
        protected void onDraw(Canvas c) {

            super.onDraw(c);

            sw = getWidth();
            sh = getHeight();

            if (sw <= 0 || sh <= 0) return;

            long now = System.currentTimeMillis();

            if (lastTime == 0) lastTime = now;

            float dt = Math.min(
                    0.035f, (now - lastTime) / 1000f);

            lastTime = now;

            if (!paused && !gameOver)
                update(dt, now);

            drawBackground(c);

            c.save();
            c.clipRect(0, 0, sw, sh);
            c.translate(-cameraX, 0);

            drawWorld(c, now);

            c.restore();

            drawControls(c);
            drawHud(c);

            postInvalidateDelayed(16);
        }

        private void drawBackground(Canvas c) {

            p.setShader(new LinearGradient(
                    0, 0, 0, sh,
                    Color.rgb(95, 190, 255),
                    Color.rgb(190, 230, 255),
                    Shader.TileMode.CLAMP));

            c.drawRect(0, 0, sw, sh, p);

            p.setShader(null);

            p.setColor(Color.WHITE);

            for (int i = 0; i < 6; i++) {

                float cx = (i * 270 - cameraX * 0.25f)
                        % (sw + 300);

                if (cx < -120) cx += sw + 300;

                float cy = 65 + (i % 2) * 35;

                c.drawCircle(cx + 60, cy, 23, p);
                c.drawCircle(cx + 90, cy - 8, 29, p);
                c.drawCircle(cx + 120, cy + 5, 22, p);
            }

            // Grass below the ground.
            p.setColor(Color.rgb(85, 190, 95));

            c.drawRect(
                    0, groundTop + 1,
                    sw, sh, p);
        }

        private void drawWorld(Canvas c, long now) {

            // Ground and platforms.
            for (Platform a : platforms) {

                p.setColor(Color.rgb(130, 78, 38));

                c.drawRect(
                        a.x, a.y,
                        a.x + a.w, a.y + a.h, p);

                p.setColor(Color.rgb(55, 180, 65));

                c.drawRect(
                        a.x, a.y,
                        a.x + a.w, a.y + 8, p);

                p.setColor(Color.rgb(90, 55, 30));

                for (float xx = a.x + 18;
                     xx < a.x + a.w; xx += 45) {

                    c.drawRect(
                            xx, a.y + 18,
                            xx + 3, a.y + a.h, p);
                }
            }

            // Bricks.
            for (Brick b : bricks) {

                if (b.broken) continue;

                p.setColor(Color.rgb(190, 92, 35));

                c.drawRoundRect(
                        b.x, b.y,
                        b.x + 42, b.y + 42,
                        5, 5, p);

                p.setColor(Color.rgb(255, 180, 70));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);

                c.drawRect(
                        b.x + 3, b.y + 3,
                        b.x + 39, b.y + 39, p);

                p.setStyle(Paint.Style.FILL);
            }

            // Coins.
            for (Coin coin : coins) {

                if (coin.taken) continue;

                p.setColor(Color.YELLOW);

                c.drawCircle(
                        coin.x, coin.y, 13, p);

                p.setColor(Color.rgb(255, 170, 0));

                c.drawCircle(
                        coin.x, coin.y, 7, p);
            }

            // Enemies.
            for (Enemy e : enemies) {

                if (!e.alive) continue;

                Bitmap b = e.type == 0 ? bomb : turtle;

                drawBitmapOrShape(
                        c, b, e.x, e.y,
                        60, 55,
                        e.type == 0
                                ? Color.BLACK
                                : Color.rgb(30, 150, 60));
            }

            // Bullets.
            for (Shot s : shots) {

                drawBitmapOrShape(
                        c, bullet, s.x, s.y,
                        32, 22, Color.YELLOW);
            }

            drawFlag(c);

            // Doraemon.
            Bitmap hero;

            if (Math.abs(vx) > 5)
                hero = run;
            else
                hero = idle;

            if (hero == null) hero = run;
            if (hero == null) hero = crawl;

            if (hero != null) {

                RectF destination =
                        new RectF(x, y, x + pw, y + ph);

                if (facingRight) {
                    c.drawBitmap(hero, null, destination, p);
                } else {
                    c.save();

                    c.scale(
                            -1, 1,
                            x + pw / 2, 0);

                    c.drawBitmap(hero, null, destination, p);

                    c.restore();
                }

            } else {

                p.setColor(Color.rgb(0, 125, 220));

                c.drawRoundRect(
                        x, y, x + pw, y + ph,
                        20, 20, p);
            }

            // Temporary protection flash.
            if (now < invincibleUntil &&
                    (now / 120) % 2 == 0) {

                p.setColor(0x66FFFFFF);

                c.drawRoundRect(
                        x, y, x + pw, y + ph,
                        15, 15, p);
            }
        }

        private void drawBitmapOrShape(
                Canvas c, Bitmap b,
                float bx, float by,
                float w, float h, int color) {

            if (b != null) {

                c.drawBitmap(
                        b, null,
                        new RectF(
                                bx, by,
                                bx + w, by + h),
                        p);

            } else {

                p.setColor(color);

                c.drawOval(
                        bx, by,
                        bx + w, by + h, p);
            }
        }

        private void drawFlag(Canvas c) {

            float bottom = groundTop;

            p.setColor(Color.DKGRAY);

            c.drawRect(
                    flagX, bottom - 210,
                    flagX + 8, bottom, p);

            p.setColor(Color.RED);

            Path path = new Path();

            path.moveTo(flagX + 8, bottom - 208);
            path.lineTo(flagX + 95, bottom - 180);
            path.lineTo(flagX + 8, bottom - 152);
            path.close();

            c.drawPath(path, p);

            p.setColor(Color.WHITE);
            p.setTextSize(22);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(
                    "FINISH",
                    flagX - 25,
                    bottom - 220, p);
        }

        private void update(float dt, long now) {

            if (left) {
                vx = -330;
                facingRight = false;

            } else if (right) {
                vx = 330;
                facingRight = true;

            } else {
                vx *= 0.72f;
            }

            if (jumpHeld && onGround) {

                vy = -690;
                onGround = false;

                tones.startTone(
                        ToneGenerator.TONE_PROP_BEEP, 80);
            }

            float oldY = y;

            vy += 1650 * dt;

            x += vx * dt;
            y += vy * dt;

            // World boundaries.
            x = Math.max(
                    0, Math.min(x, worldWidth - pw));

            onGround = false;

            // Landing detection.
            if (vy >= 0) {

                for (Platform a : platforms) {

                    if (x + pw > a.x &&
                            x < a.x + a.w &&
                            oldY + ph <= a.y + 12 &&
                            y + ph >= a.y) {

                        y = a.y - ph;
                        vy = 0;
                        onGround = true;
                    }
                }
            }

            // Brick breaking.
            if (vy < 0) {

                for (Brick b : bricks) {

                    if (!b.broken &&
                            x + pw > b.x &&
                            x < b.x + 42 &&
                            oldY >= b.y + 38 &&
                            y <= b.y + 42) {

                        b.broken = true;
                        vy = 80;
                        score += 5;

                        tones.startTone(
                                ToneGenerator.TONE_PROP_ACK, 70);
                    }
                }
            }

            if (y > sh + 100) {
                loseLife();
                return;
            }

            // Collect coins.
            for (Coin coin : coins) {

                if (!coin.taken &&
                        Math.abs(x + pw / 2 - coin.x) < 48 &&
                        Math.abs(y + ph / 2 - coin.y) < 55) {

                    coin.taken = true;
                    score += 10;

                    tones.startTone(
                            ToneGenerator.TONE_PROP_BEEP2, 70);
                }
            }

            // Enemy movement and collisions.
            for (Enemy e : enemies) {

                if (!e.alive) continue;

                e.x += e.dir * (85 + level * 5) * dt;

                if (e.x < e.startX - 65 ||
                        e.x > e.startX + 65) {

                    e.dir *= -1;
                }

                if (rectHit(
                        x + 8, y + 8, pw - 16, ph - 12,
                        e.x, e.y, 60, 55) &&
                        now > invincibleUntil) {

                    if (vy > 150 &&
                            y + ph - 15 < e.y + 25) {

                        e.alive = false;
                        vy = -430;
                        score += 20;

                    } else {

                        loseLife();
                        return;
                    }
                }
            }

            // Fire bullets.
            if (fireHeld && now - lastShot > 350) {

                shots.add(new Shot(
                        facingRight ? x + pw : x - 28,
                        y + 45,
                        facingRight ? 560 : -560));

                lastShot = now;

                tones.startTone(
                        ToneGenerator.TONE_PROP_BEEP, 50);
            }

            // Bullet collision.
            Iterator<Shot> it = shots.iterator();

            while (it.hasNext()) {

                Shot s = it.next();

                s.x += s.v * dt;

                if (s.x < 0 || s.x > worldWidth) {
                    it.remove();
                    continue;
                }

                boolean removed = false;

                for (Enemy e : enemies) {

                    if (e.alive &&
                            rectHit(
                                    s.x, s.y, 32, 22,
                                    e.x, e.y, 60, 55)) {

                        e.alive = false;
                        score += 20;
                        it.remove();
                        removed = true;
                        break;
                    }
                }
            }

            // Smooth camera movement.
            float target = x - sw * 0.38f;

            cameraX = Math.max(
                    0,
                    Math.min(target, worldWidth - sw));

            // Advance to the next level.
            if (x + pw >= flagX - 12) {

                level++;

                buildLevel();
            }
        }

        private boolean rectHit(
                float ax, float ay, float aw, float ah,
                float bx, float by, float bw, float bh) {

            return ax < bx + bw &&
                    ax + aw > bx &&
                    ay < by + bh &&
                    ay + ah > by;
        }

        private void loseLife() {

            long now = System.currentTimeMillis();

            if (now < invincibleUntil) return;

            lives--;

            tones.startTone(
                    ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                    180);

            if (lives <= 0) {

                gameOver = true;

                left = right = jumpHeld = fireHeld = false;

            } else {

                x = Math.max(100, cameraX + 80);

                y = groundTop - ph;

                vy = 0;
                vx = 0;
                onGround = true;

                invincibleUntil = now + 1600;
            }
        }

        private void drawControls(Canvas c) {

            float top = sh - 82;

            drawButton(
                    c, 12, top, 88, sh - 10, "◀");

            drawButton(
                    c, 95, top, 171, sh - 10, "▶");

            drawButton(
                    c, sw - 195, top, sw - 102, sh - 10,
                    "FIRE");

            drawButton(
                    c, sw - 95, top, sw - 10, sh - 10,
                    "JUMP");
        }

        private void drawButton(
                Canvas c, float l, float t,
                float r, float b, String text) {

            p.setColor(0x99505050);

            c.drawRoundRect(
                    l, t, r, b,
                    16, 16, p);

            p.setColor(Color.WHITE);

            p.setTextSize(
                    text.length() > 2 ? 19 : 36);

            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(
                    text,
                    l + (r - l - p.measureText(text)) / 2,
                    t + (b - t) / 2 + 12, p);
        }

        private void drawHud(Canvas c) {

            p.setColor(0xBB000000);

            c.drawRoundRect(
                    10, 10, 285, 58,
                    10, 10, p);

            p.setColor(Color.WHITE);

            p.setTextSize(21);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(
                    "Score: " + score +
                    "   Lives: " + lives +
                    "   Level: " + level,
                    18, 42, p);

            drawButton(
                    c, sw - 65, 10,
                    sw - 10, 58,
                    paused ? "▶" : "Ⅱ");

            if (paused || gameOver) {

                p.setColor(0xAA000000);

                c.drawRect(0, 0, sw, sh, p);

                p.setColor(Color.WHITE);
                p.setTextAlign(Paint.Align.CENTER);

                p.setTextSize(42);

                c.drawText(
                        gameOver ? "GAME OVER" : "PAUSED",
                        sw / 2, sh / 2 - 15, p);

                p.setTextSize(21);

                c.drawText(
                        "Tap the screen to " +
                        (gameOver ? "restart" : "continue"),
                        sw / 2, sh / 2 + 30, p);

                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {

            float tx = e.getX();
            float ty = e.getY();

            int action = e.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN) {

                if (gameOver) {

                    gameOver = false;
                    score = 0;
                    lives = 3;
                    level = 1;

                    buildLevel();

                    return true;
                }

                if (tx > sw - 75 && ty < 75) {

                    paused = !paused;
                    return true;
                }

                if (paused) {

                    paused = false;
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_CANCEL) {

                left = right = false;
                jumpHeld = fireHeld = false;

                return true;
            }

            // Recalculate controls from all active fingers.
            left = right = false;
            jumpHeld = fireHeld = false;

            for (int i = 0; i < e.getPointerCount(); i++) {

                if (i == e.getActionIndex() &&
                        action == MotionEvent.ACTION_POINTER_UP) {
                    continue;
                }

                setControl(e.getX(i), e.getY(i));
            }

            return true;
        }

        private void setControl(float tx, float ty) {

            if (ty < sh - 100) return;

            if (tx < 90) {
                left = true;

            } else if (tx < 180) {
                right = true;

            } else if (tx > sw - 100) {
                jumpHeld = true;

            } else if (tx > sw - 205) {
                fireHeld = true;
            }
        }

        void release() {

            try {
                tones.release();
            } catch (Exception ignored) {}
        }

        private class Platform {

            float x, y, w, h;

            Platform(float x, float y, float w, float h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }

        private class Brick {

            float x, y;
            boolean broken = false;

            Brick(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        private class Coin {

            float x, y;
            boolean taken = false;

            Coin(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        private class Enemy {

            float x, y, startX;
            float dir = 1;

            int type;
            boolean alive = true;

            Enemy(float x, float y, int type) {
                this.x = x;
                this.y = y;
                this.startX = x;
                this.type = type;
            }
        }

        private class Shot {

            float x, y, v;

            Shot(float x, float y, float v) {
                this.x = x;
                this.y = y;
                this.v = v;
            }
        }
    }
}
