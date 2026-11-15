

package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;

import java.util.ArrayList;

public class MainActivity extends Activity {

    FrameLayout game;
    ImageView hero;
    TextView scoreText;

    Handler handler = new Handler();

    ArrayList<ImageView> objects = new ArrayList<>();

    int screenW, screenH;
    int heroX = 90;
    int heroY;
    int groundY;

    float velocityY = 0;
    float gravity = 1.1f;

    boolean jumping = false;
    boolean left = false;
    boolean right = false;
    boolean gameOver = false;

    int score = 0;
    int animationFrame = 0;

    final int HERO_SIZE = 65;

    Runnable gameLoop;

    int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        createGame();
    }

    void createGame() {

        game = new FrameLayout(this);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(90, 190, 255),
                        Color.rgb(205, 240, 255)
                }
        );

        game.setBackground(background);

        setContentView(game);

        game.post(() -> {

            screenW = game.getWidth();
            screenH = game.getHeight();

            groundY = screenH - dp(90);
            heroY = groundY - dp(HERO_SIZE);

            createBackground();
            createGround();
            createHero();
            createBlocks();
            createEnemies();
            createControls();
            createHUD();

            startGame();
        });
    }

    ImageView addImage(String name, int x, int y,
                       int width, int height) {

        ImageView image = new ImageView(this);

        int id = getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );

        if (id != 0) {
            image.setImageResource(id);
        } else {
            image.setVisibility(View.INVISIBLE);
        }

        image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(width), dp(height));

        params.leftMargin = x;
        params.topMargin = y;

        game.addView(image, params);

        return image;
    }

    void createBackground() {

        // Optional background images.
        addImage("cloud", dp(35), dp(40), 100, 55);
        addImage("cloud", dp(220), dp(75), 100, 55);

        addImage("tree", screenW - dp(120),
                groundY - dp(120), 110, 120);

        addImage("tower", screenW - dp(210),
                groundY - dp(190), 100, 190);
    }

    void createGround() {

        View ground = new View(this);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(45, 160, 55));

        ground.setBackground(drawable);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        screenW, dp(90));

        params.leftMargin = 0;
        params.topMargin = groundY;

        game.addView(ground, params);
    }

    void createHero() {

        hero = addImage(
                "hero_idle",
                heroX,
                heroY,
                HERO_SIZE,
                HERO_SIZE
        );
    }

    void createBlocks() {

        addObject("question_block",
                screenW / 2, groundY - dp(150),
                55, 55);

        addObject("question_block",
                screenW / 2 + dp(60),
                groundY - dp(150),
                55, 55);

        addObject("pipe_small",
                screenW - dp(160),
                groundY - dp(75),
                70, 75);

        addObject("pipe_large",
                screenW + dp(180),
                groundY - dp(110),
                90, 110);
    }

    void createEnemies() {

        addObject("turtle",
                screenW - dp(300),
                groundY - dp(60),
                55, 55);

        addObject("mushroom",
                screenW + dp(300),
                groundY - dp(55),
                55, 55);

        addObject("spiked_enemy",
                screenW + dp(650),
                groundY - dp(55),
                55, 55);

        addObject("red_shell",
                screenW + dp(950),
                groundY - dp(55),
                55, 55);
    }

    void addObject(String name, int x, int y,
                   int width, int height) {

        ImageView image = addImage(
                name, x, y, width, height);

        image.setTag(name);

        objects.add(image);
    }

    void createHUD() {

        scoreText = new TextView(this);

        scoreText.setTextColor(Color.WHITE);
        scoreText.setTextSize(20);
        scoreText.setText("SCORE: 0");
        scoreText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(150), dp(50),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        game.addView(scoreText, params);
    }

    void createControls() {

        addControl("LEFT", 15, false);
        addControl("RIGHT", 95, true);
        addJumpButton();
    }

    void addControl(String label, int x, boolean isRight) {

        TextView button = new TextView(this);

        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(35, 65, 120));
        bg.setCornerRadius(dp(15));
        button.setBackground(bg);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(75), dp(65),
                        Gravity.BOTTOM | Gravity.LEFT);

        params.leftMargin = dp(x);
        params.bottomMargin = dp(15);

        game.addView(button, params);

        button.setOnTouchListener((v, event) -> {

            if (isRight) {
                right = event.getAction() == MotionEvent.ACTION_DOWN;
            } else {
                left = event.getAction() == MotionEvent.ACTION_DOWN;
            }

            return true;
        });
    }

    void addJumpButton() {

        TextView button = new TextView(this);

        button.setText("JUMP");
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(200, 65, 40));
        bg.setCornerRadius(dp(40));

        button.setBackground(bg);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(90), dp(75),
                        Gravity.BOTTOM | Gravity.RIGHT);

        params.rightMargin = dp(20);
        params.bottomMargin = dp(15);

        game.addView(button, params);

        button.setOnClickListener(v -> jump());
    }

    void jump() {

        if (!jumping && !gameOver) {
            velocityY = -dp(19);
            jumping = true;

            setHeroImage("hero_jump");
        }
    }

    void setHeroImage(String name) {

        int id = getResources().getIdentifier(
                name, "drawable", getPackageName());

        if (id != 0) {
            hero.setImageResource(id);
        }
    }

    void animateHero() {

        if (jumping) {
            setHeroImage("hero_jump");
            return;
        }

        if (left || right) {

            animationFrame++;

            String[] frames = {
                    "hero_run_1",
                    "hero_run_2",
                    "hero_run_3"
            };

            setHeroImage(frames[
                    (animationFrame / 5) % 3
            ]);

        } else {
            setHeroImage("hero_idle");
        }
    }

    void startGame() {

        gameLoop = new Runnable() {

            @Override
            public void run() {

                if (gameOver) return;

                updateGame();

                handler.postDelayed(this, 30);
            }
        };

        handler.post(gameLoop);
    }

    void updateGame() {

        // Horizontal movement.

        if (right) heroX += dp(6);
        if (left) heroX -= dp(6);

        heroX = Math.max(0,
                Math.min(heroX, screenW - dp(HERO_SIZE)));

        // Jump physics.

        velocityY += gravity;
        heroY += (int) velocityY;

        if (heroY >= groundY - dp(HERO_SIZE)) {

            heroY = groundY - dp(HERO_SIZE);
            velocityY = 0;
            jumping = false;
        }

        // Move hero.

        FrameLayout.LayoutParams heroParams =
                (FrameLayout.LayoutParams) hero.getLayoutParams();

        heroParams.leftMargin = heroX;
        heroParams.topMargin = heroY;

        hero.setLayoutParams(heroParams);

        animateHero();

        // Move objects.

        for (ImageView object : objects) {

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            object.getLayoutParams();

            params.leftMargin -= dp(5);

            if (params.leftMargin < -dp(120)) {

                params.leftMargin =
                        screenW + dp(250);

                score += 10;
                scoreText.setText("SCORE: " + score);
            }

            object.setLayoutParams(params);

            checkCollision(object);
        }
    }

    void checkCollision(ImageView object) {

        if (object.getVisibility() != View.VISIBLE)
            return;

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        object.getLayoutParams();

        String name = String.valueOf(object.getTag());

        boolean dangerous =
                name.equals("turtle") ||
                name.equals("mushroom") ||
                name.equals("spiked_enemy") ||
                name.equals("red_shell");

        if (!dangerous) return;

        int objectX = params.leftMargin;
        int objectY = params.topMargin;

        boolean collision =
                heroX + dp(48) > objectX + dp(8) &&
                heroX + dp(12) < objectX + params.width - dp(8) &&
                heroY + dp(50) > objectY + dp(8) &&
                heroY + dp(10) < objectY + params.height;

        if (collision) {

            if (jumping && velocityY > 0) {

                object.setVisibility(View.INVISIBLE);
                score += 20;

                scoreText.setText("SCORE: " + score);

            } else {

                gameOver();
            }
        }
    }

    void gameOver() {

        gameOver = true;

        TextView message = new TextView(this);

        message.setText(
                "GAME OVER\n\nSCORE: " + score +
                "\n\nTAP TO RESTART"
        );

        message.setTextColor(Color.WHITE);
        message.setTextSize(26);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.rgb(30, 30, 30));

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(280), dp(220),
                        Gravity.CENTER);

        game.addView(message, params);

        message.setOnClickListener(v -> {
            handler.removeCallbacks(gameLoop);
            recreate();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (game != null && gameLoop != null && !gameOver) {
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
