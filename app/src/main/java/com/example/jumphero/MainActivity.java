
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;

public class MainActivity extends Activity {

    FrameLayout game;
    ImageView hero;
    TextView scoreText;

    Handler handler = new Handler();
    ArrayList<Sprite> sprites = new ArrayList<>();

    int screenW, screenH;
    int heroX, heroY;
    int heroW, heroH;
    int groundY;

    float velocityY = 0;
    float gravity = 1.0f;

    boolean jumping = false;
    boolean left = false;
    boolean right = false;
    boolean gameOver = false;

    int score = 0;
    int frame = 0;

    Runnable gameLoop;

    class Sprite {
        ImageView image;
        String type;
        int x, y, w, h;
        boolean active = true;
    }

    int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
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

        GradientDrawable sky = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(75, 185, 255),
                        Color.rgb(180, 230, 255)
                }
        );

        game.setBackground(sky);
        setContentView(game);

        game.post(() -> {

            screenW = game.getWidth();
            screenH = game.getHeight();

            heroW = dp(65);
            heroH = dp(90);

            groundY = screenH - dp(90);

            heroX = dp(75);
            heroY = groundY - heroH;

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

    // LOAD PNG IMAGE

    ImageView loadImage(String name, String fallback) {

        ImageView image = new ImageView(this);

        int id = getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );

        if (id == 0 && fallback != null) {
            id = getResources().getIdentifier(
                    fallback,
                    "drawable",
                    getPackageName()
            );
        }

        if (id != 0) {
            image.setImageResource(id);
        } else {
            image.setVisibility(View.INVISIBLE);
        }

        image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        return image;
    }

    ImageView addImage(
            String name,
            String fallback,
            int x, int y,
            int w, int h) {

        ImageView image = loadImage(name, fallback);

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(w, h);

        p.leftMargin = x;
        p.topMargin = y;

        game.addView(image, p);

        return image;
    }

    // BACKGROUND

    void createBackground() {

        addImage("cloud", null,
                dp(40), dp(35), dp(100), dp(55));

        addImage("cloud", null,
                screenW / 2, dp(70), dp(100), dp(55));

        addImage("tree", null,
                screenW - dp(140),
                groundY - dp(125),
                dp(120), dp(125));

        addImage("tower", null,
                screenW - dp(250),
                groundY - dp(200),
                dp(110), dp(200));
    }

    void createGround() {

        View ground = new View(this);

        ground.setBackgroundColor(
                Color.rgb(45, 155, 50)
        );

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(
                        screenW, dp(90));

        p.leftMargin = 0;
        p.topMargin = groundY;

        game.addView(ground, p);
    }

    // HERO

    void createHero() {

        hero = addImage(
                "hero_idle",
                null,
                heroX, heroY,
                heroW, heroH
        );
    }

    void changeHeroImage(String name) {

        int id = getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );

        if (id != 0) {
            hero.setImageResource(id);
        }
    }

    void animateHero() {

        if (jumping) {
            changeHeroImage("hero_jump");
        } else if (left || right) {

            frame++;

            String[] run = {
                    "hero_run_1",
                    "hero_run_2",
                    "hero_run_3"
            };

            changeHeroImage(run[(frame / 5) % 3]);

        } else {
            changeHeroImage("hero_idle");
        }
    }

    // GAME OBJECTS

    void addObject(
            String name,
            String fallback,
            String type,
            int x, int y,
            int w, int h) {

        ImageView image = addImage(
                name, fallback, x, y, w, h
        );

        Sprite s = new Sprite();

        s.image = image;
        s.type = type;
        s.x = x;
        s.y = y;
        s.w = w;
        s.h = h;

        sprites.add(s);
    }

    void createBlocks() {

        int blockY = groundY - dp(180);
        int blockSize = dp(55);

        // Breakable bricks

        addObject(
                "brick", "question_block",
                "brick",
                screenW / 2 - dp(60),
                blockY,
                blockSize, blockSize
        );

        addObject(
                "brick", "question_block",
                "brick",
                screenW / 2,
                blockY,
                blockSize, blockSize
        );

        // Question blocks

        addObject(
                "question_block", null,
                "question",
                screenW / 2 + dp(60),
                blockY,
                blockSize, blockSize
        );

        // Pipes

        addObject(
                "pipe_small", null,
                "pipe",
                screenW - dp(160),
                groundY - dp(75),
                dp(75), dp(75)
        );

        addObject(
                "pipe_large", null,
                "pipe",
                screenW + dp(150),
                groundY - dp(110),
                dp(90), dp(110)
        );
    }

    void createEnemies() {

        addObject(
                "turtle", null,
                "enemy",
                screenW - dp(300),
                groundY - dp(60),
                dp(60), dp(60)
        );

        addObject(
                "mushroom", null,
                "enemy",
                screenW + dp(300),
                groundY - dp(60),
                dp(60), dp(60)
        );

        addObject(
                "spiked_enemy", null,
                "enemy",
                screenW + dp(600),
                groundY - dp(60),
                dp(60), dp(60)
        );

        addObject(
                "red_shell", null,
                "enemy",
                screenW + dp(900),
                groundY - dp(60),
                dp(60), dp(60)
        );
    }

    // CONTROLS

    void createControls() {

        addMoveButton("LEFT", dp(15), false);
        addMoveButton("RIGHT", dp(100), true);

        TextView jumpButton = createButton(
                "JUMP",
                Color.rgb(210, 65, 40),
                dp(90), dp(80)
        );

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(
                        dp(90), dp(80),
                        Gravity.BOTTOM | Gravity.RIGHT
                );

        p.rightMargin = dp(20);
        p.bottomMargin = dp(15);

        game.addView(jumpButton, p);

        jumpButton.setOnClickListener(v -> jump());
    }

    TextView createButton(
            String text,
            int color,
            int w, int h) {

        TextView button = new TextView(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();

        bg.setColor(color);
        bg.setCornerRadius(dp(25));

        button.setBackground(bg);

        return button;
    }

    void addMoveButton(
            String label,
            int x,
            boolean isRight) {

        TextView button = createButton(
                label,
                Color.rgb(35, 65, 120),
                dp(80), dp(70)
        );

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(
                        dp(80), dp(70),
                        Gravity.BOTTOM | Gravity.LEFT
                );

        p.leftMargin = x;
        p.bottomMargin = dp(15);

        game.addView(button, p);

        button.setOnTouchListener((v, event) -> {

            boolean pressed =
                    event.getAction() == MotionEvent.ACTION_DOWN;

            if (isRight) {
                right = pressed;
            } else {
                left = pressed;
            }

            return true;
        });
    }

    void jump() {

        if (!jumping && !gameOver) {

            jumping = true;
            velocityY = -dp(19);

            changeHeroImage("hero_jump");
        }
    }

    // SCORE

    void createHUD() {

        scoreText = new TextView(this);

        scoreText.setText("SCORE: 0");
        scoreText.setTextColor(Color.WHITE);
        scoreText.setTextSize(25);
        scoreText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(
                        dp(200), dp(60),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL
                );

        game.addView(scoreText, p);
    }

    void updateScore(int points) {

        score += points;

        scoreText.setText("SCORE: " + score);
    }

    // GAME LOOP

    void startGame() {

        gameLoop = new Runnable() {

            @Override
            public void run() {

                if (gameOver) return;

                updateGame();

                handler.postDelayed(this, 16);
            }
        };

        handler.post(gameLoop);
    }

    void updateGame() {

        // Save old position for collision detection

        int oldHeroY = heroY;

        // Horizontal movement

        if (right) heroX += dp(5);
        if (left) heroX -= dp(5);

        heroX = Math.max(
                0,
                Math.min(heroX, screenW - heroW)
        );

        // Gravity

        velocityY += gravity;
        heroY += (int) velocityY;

        // Check collision with bricks

        checkBrickCollision(oldHeroY);

        // Ground collision

        if (heroY + heroH >= groundY) {

            heroY = groundY - heroH;
            velocityY = 0;
            jumping = false;
        }

        // Update hero position

        FrameLayout.LayoutParams p =
                (FrameLayout.LayoutParams)
                        hero.getLayoutParams();

        p.leftMargin = heroX;
        p.topMargin = heroY;

        hero.setLayoutParams(p);

        animateHero();

        // Enemy collision

        checkEnemyCollision();
    }

    // BRICK BREAKING PHYSICS

    void checkBrickCollision(int oldHeroY) {

        if (velocityY >= 0) return;

        for (Sprite s : sprites) {

            if (!s.active) continue;

            if (!s.type.equals("brick") &&
                !s.type.equals("question")) {
                continue;
            }

            int blockLeft = s.x;
            int blockRight = s.x + s.w;

            int blockTop = s.y;
            int blockBottom = s.y + s.h;

            // Horizontal overlap

            boolean horizontal =
                    heroX + heroW - dp(8) > blockLeft &&
                    heroX + dp(8) < blockRight;

            // Hero moving upward through block bottom

            boolean hitFromBelow =
                    oldHeroY >= blockBottom &&
                    heroY <= blockBottom;

            if (horizontal && hitFromBelow) {

                // Stop upward movement

                heroY = blockBottom;
                velocityY = 0;

                if (s.type.equals("brick")) {

                    // Break the brick

                    s.active = false;
                    s.image.setVisibility(View.INVISIBLE);

                    updateScore(50);

                } else {

                    // Question block stays visible

                    updateScore(10);

                    // Small bump animation

                    s.image.animate()
                            .translationY(-dp(12))
                            .setDuration(100)
                            .withEndAction(() ->
                                    s.image.animate()
                                            .translationY(0)
                                            .setDuration(100)
                                            .start()
                            )
                            .start();
                }

                return;
            }
        }
    }

    // ENEMY COLLISION

    void checkEnemyCollision() {

        for (Sprite s : sprites) {

            if (!s.active || !s.type.equals("enemy"))
                continue;

            boolean overlap =
                    heroX + heroW - dp(10) > s.x &&
                    heroX + dp(10) < s.x + s.w &&
                    heroY + heroH - dp(5) > s.y &&
                    heroY + dp(10) < s.y + s.h;

            if (overlap) {

                // Jumping on enemy defeats it

                if (velocityY > 0 &&
                    heroY + heroH < s.y + dp(35)) {

                    s.active = false;

                    s.image.setVisibility(View.INVISIBLE);

                    velocityY = -dp(10);

                    updateScore(20);

                } else {

                    showGameOver();
                }

                return;
            }
        }
    }

    // GAME OVER

    void showGameOver() {

        if (gameOver) return;

        gameOver = true;

        TextView message = new TextView(this);

        message.setText(
                "GAME OVER\n\nSCORE: " + score +
                "\n\nTAP TO RESTART"
        );

        message.setTextColor(Color.WHITE);
        message.setTextSize(25);
        message.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();

        bg.setColor(Color.rgb(30, 30, 30));
        bg.setCornerRadius(dp(20));

        message.setBackground(bg);

        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(
                        dp(280), dp(220),
                        Gravity.CENTER
                );

        game.addView(message, p);

        message.setOnClickListener(v -> {

            handler.removeCallbacks(gameLoop);
            recreate();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (gameLoop != null) {
            handler.removeCallbacks(gameLoop);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (gameLoop != null && !gameOver) {
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

