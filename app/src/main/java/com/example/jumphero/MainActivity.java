
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.media.AudioManager;
import android.media.ToneGenerator;

public class MainActivity extends Activity {

    GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        game = new GameView();
        setContentView(game);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (game != null) game.pauseGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) game.resumeGame();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.releaseAudio();
        super.onDestroy();
    }

    class GameView extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Handler handler = new Handler();

        ToneGenerator audio =
            new ToneGenerator(AudioManager.STREAM_MUSIC, 70);

        float x = 100;
        float y = 200;
        float vy = 0;

        float ground;
        float coinX = 500;
        float coinY = 300;

        int score = 0;
        int lives = 3;

        boolean left = false;
        boolean right = false;
        boolean jumping = false;
        boolean running = true;
        boolean music = true;
        boolean gameOver = false;

        long frame = 0;
        int note = 0;

        int[] melody = {
            ToneGenerator.TONE_DTMF_1,
            ToneGenerator.TONE_DTMF_3,
            ToneGenerator.TONE_DTMF_5,
            ToneGenerator.TONE_DTMF_8,
            ToneGenerator.TONE_DTMF_5,
            ToneGenerator.TONE_DTMF_3
        };

        Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                updateGame();
                invalidate();

                handler.postDelayed(this, 16);
            }
        };

        Runnable musicLoop = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                if (music && !gameOver) {
                    audio.startTone(melody[note], 160);
                    note = (note + 1) % melody.length;
                }

                handler.postDelayed(this, 300);
            }
        };

        GameView() {
            super(MainActivity.this);
            setFocusable(true);

            handler.post(gameLoop);
            handler.postDelayed(musicLoop, 300);
        }

        void pauseGame() {
            running = false;
            handler.removeCallbacks(gameLoop);
            handler.removeCallbacks(musicLoop);
            audio.stopTone();
        }

        void resumeGame() {
            if (running) return;

            running = true;
            handler.post(gameLoop);
            handler.post(musicLoop);
        }

        void releaseAudio() {
            running = false;
            handler.removeCallbacksAndMessages(null);
            audio.stopTone();
            audio.release();
        }

        void updateGame() {
            float w = getWidth();
            float h = getHeight();

            if (w == 0 || h == 0 || gameOver) return;

            ground = h * 0.78f;

            if (left) x -= 5;
            if (right) x += 5;

            x = Math.max(0, Math.min(w - 55, x));

            vy += 0.65f;
            y += vy;

            if (y + 55 >= ground) {
                y = ground - 55;
                vy = 0;
                jumping = false;
            }

            // Coin collection
            if (Math.abs(x - coinX) < 45 &&
                Math.abs(y - coinY) < 55) {

                score++;
                audio.startTone(
                    ToneGenerator.TONE_PROP_ACK, 150);

                coinX = 100 + (float)Math.random() * (w - 200);
                coinY = ground - 100 -
                    (float)Math.random() * 150;
            }

            // Falling protection
            if (y > h + 100) {
                lives--;
                x = 100;
                y = ground - 55;
                vy = 0;

                if (lives <= 0) {
                    gameOver = true;
                    audio.startTone(
                        ToneGenerator.TONE_PROP_NACK, 500);
                }
            }

            frame++;
        }

        void drawHero(Canvas c) {
            float a = x;
            float b = y;

            // Shoes
            p.setColor(Color.rgb(80, 35, 20));
            c.drawRect(a + 3, b + 47, a + 25, b + 55, p);
            c.drawRect(a + 30, b + 47, a + 52, b + 55, p);

            // Blue overalls
            p.setColor(Color.BLUE);
            c.drawRect(a + 9, b + 27, a + 46, b + 49, p);

            // Red shirt
            p.setColor(Color.RED);
            c.drawRect(a + 5, b + 18, a + 50, b + 34, p);

            // Arms
            p.setColor(Color.rgb(255, 190, 140));
            c.drawRect(a, b + 20, a + 9, b + 36, p);
            c.drawRect(a + 46, b + 20, a + 55, b + 36, p);

            // Face
            p.setColor(Color.rgb(255, 200, 150));
            c.drawRoundRect(a + 11, b + 3,
                a + 44, b + 28, 7, 7, p);

            // Hair
            p.setColor(Color.rgb(90, 40, 15));
            c.drawRect(a + 8, b + 8, a + 14, b + 24, p);

            // Red cap
            p.setColor(Color.RED);
            c.drawRect(a + 7, b - 5, a + 45, b + 7, p);
            c.drawRect(a + 1, b + 3, a + 48, b + 10, p);

            // Eyes
            p.setColor(Color.WHITE);
            c.drawCircle(a + 35, b + 15, 4, p);

            p.setColor(Color.BLACK);
            c.drawCircle(a + 36, b + 15, 2, p);

            // Mustache
            p.setColor(Color.rgb(70, 30, 10));
            c.drawRect(a + 23, b + 20, a + 40, b + 24, p);

            // Overall buttons
            p.setColor(Color.YELLOW);
            c.drawCircle(a + 19, b + 31, 3, p);
            c.drawCircle(a + 37, b + 31, 3, p);
        }

        void drawButton(Canvas c, float cx, float cy,
                        String label) {
            p.setColor(Color.argb(160, 30, 30, 30));
            c.drawCircle(cx, cy, 42, p);

            p.setColor(Color.WHITE);
            p.setTextSize(25);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, cx, cy + 9, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            ground = h * 0.78f;

            // Sky
            c.drawColor(Color.rgb(100, 190, 250));

            // Sun
            p.setColor(Color.YELLOW);
            c.drawCircle(w - 75, 80, 38, p);

            // Clouds
            p.setColor(Color.WHITE);

            for (int i = 0; i < 5; i++) {
                float cx = (i * 240 + 100 - frame / 4) % (w + 100);
                if (cx < 0) cx += w + 100;

                c.drawCircle(cx, 110, 27, p);
                c.drawCircle(cx + 30, 95, 30, p);
                c.drawCircle(cx + 60, 110, 25, p);
            }

            // Hills
            p.setColor(Color.rgb(60, 175, 85));

            for (int i = 0; i < 5; i++) {
                float hx = i * 260;
                c.drawCircle(hx, ground + 5, 100, p);
            }

            // Ground
            p.setColor(Color.rgb(120, 70, 35));
            c.drawRect(0, ground, w, h, p);

            p.setColor(Color.GREEN);
            c.drawRect(0, ground, w, ground + 15, p);

            // Coins
            p.setColor(Color.YELLOW);
            c.drawCircle(coinX, coinY, 19, p);

            p.setColor(Color.rgb(220, 140, 0));
            c.drawCircle(coinX, coinY, 10, p);

            // Character
            drawHero(c);

            // Score
            p.setColor(Color.WHITE);
            p.setTextSize(30);
            c.drawText("COINS: " + score, 25, 40, p);
            c.drawText("LIVES: " + lives, 25, 78, p);

            // Controls
            drawButton(c, w * 0.12f, h * 0.88f, "<");
            drawButton(c, w * 0.29f, h * 0.88f, ">");
            drawButton(c, w * 0.88f, h * 0.88f, "J");

            // Music toggle
            p.setColor(Color.WHITE);
            p.setTextSize(20);
            c.drawText(music ? "MUSIC ON" : "MUSIC OFF",
                w - 130, 35, p);

            if (gameOver) {
                p.setColor(Color.argb(200, 0, 0, 0));
                c.drawRect(0, 0, w, h, p);

                p.setColor(Color.WHITE);
                p.setTextSize(45);
                c.drawText("GAME OVER", w / 2 - 130, h / 2, p);

                p.setTextSize(25);
                c.drawText("Tap to restart",
                    w / 2 - 85, h / 2 + 50, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float tx = event.getX();
            float ty = event.getY();

            float w = getWidth();
            float h = getHeight();

            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                if (gameOver) {
                    score = 0;
                    lives = 3;
                    x = 100;
                    y = ground - 55;
                    vy = 0;
                    gameOver = false;
                    return true;
                }

                if (ty < 65 && tx > w - 155) {
                    music = !music;
                    return true;
                }

                if (tx < w * 0.20f) {
                    left = true;
                } else if (tx < w * 0.40f) {
                    right = true;
                } else if (tx > w * 0.72f) {
                    if (!jumping) {
                        vy = -15;
                        jumping = true;
                        audio.startTone(
                            ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                            120);
                    }
                }

                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                left = false;
                right = false;
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                left = tx < w * 0.20f;
                right = tx >= w * 0.20f && tx < w * 0.40f;
                return true;
            }

            return true;
        }
    }
}



            
