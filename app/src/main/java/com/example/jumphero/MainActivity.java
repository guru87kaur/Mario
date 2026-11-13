
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.content.Context;

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
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float playerX = 100, playerY = 300;
        float velocityY = 0;
        float camera = 0;
        int score = 0;
        int lives = 3;
        boolean left = false, right = false;
        boolean jump = false;
        boolean grounded = false;
        boolean won = false;

        final float ground = 390;
        final float playerSize = 42;

        float[] platforms = {
            0,390,650, 760,340,180, 1030,300,180,
            1320,360,180, 1630,310,220,
            1990,390,700
        };

        float[] coins = {
            400,330, 820,280, 1100,240,
            1400,300, 1710,250, 2050,330
        };
        boolean[] collected = new boolean[6];

        GameView(Context c) {
            super(c);
            setFocusable(true);
        }

        void drawRect(Canvas c, int color, float x, float y,
                      float w, float h) {
            p.setColor(color);
            c.drawRect(x, y, x+w, y+h, p);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();

            canvas.save();
            canvas.scale(w/900f, h/500f);

            drawRect(canvas, Color.rgb(120,205,255),0,0,900,500);

            // Clouds
            p.setColor(Color.WHITE);
            for(int i=0;i<7;i++) {
                float x = (i*180-camera*0.25f)%1100;
                canvas.drawCircle(x,75,24,p);
                canvas.drawCircle(x+25,65,30,p);
                canvas.drawCircle(x+52,78,22,p);
            }

            // Ground and platforms
            for(int i=0;i<platforms.length;i+=3) {
                float x=platforms[i]-camera;
                float y=platforms[i+1];
                float width=platforms[i+2];
                drawRect(canvas,Color.rgb(130,75,35),x,y,width,500-y);
                drawRect(canvas,Color.rgb(50,180,70),x,y,width,12);
            }

            // Coins
            for(int i=0;i<6;i++) {
                if(!collected[i]) {
                    p.setColor(Color.YELLOW);
                    canvas.drawCircle(coins[i*2]-camera,
                        coins[i*2+1],13,p);
                    p.setColor(Color.rgb(220,150,0));
                    canvas.drawCircle(coins[i*2]-camera,
                        coins[i*2+1],6,p);
                }
            }

            // Finish flag
            drawRect(canvas,Color.DKGRAY,2600-camera,245,7,145);
            p.setColor(Color.RED);
            PathFlag(canvas,2607-camera,245);

            // Player
            drawRect(canvas,Color.rgb(220,45,45),
                playerX-camera,playerY,playerSize,playerSize);
            p.setColor(Color.WHITE);
            canvas.drawCircle(playerX-camera+29,playerY+12,5,p);
            p.setColor(Color.BLACK);
            canvas.drawCircle(playerX-camera+30,playerY+12,2,p);

            // Score
            p.setColor(Color.BLACK);
            p.setTextSize(27);
            canvas.drawText("Coins: "+score,25,35,p);
            canvas.drawText("Lives: "+lives,25,68,p);

            // Touch controls
            p.setColor(Color.argb(130,30,30,30));
            canvas.drawCircle(75,425, forty(),p);
            canvas.drawCircle(180,425, forty(),p);
            canvas.drawCircle(810,425, fifty(),p);
            p.setColor(Color.WHITE);
            p.setTextSize(30);
            canvas.drawText("<",63,435,p);
            canvas.drawText(">",168,435,p);
            canvas.drawText("JUMP",770,435,p);

            if(won || lives<=0) {
                p.setColor(Color.BLACK);
                p.setTextSize(45);
                canvas.drawText(won ? "YOU WIN!" : "GAME OVER",
                    300,220,p);
                p.setTextSize(22);
                canvas.drawText("Tap to play again",350,260,p);
            }

            canvas.restore();
        }

        float forty() { return 42; }
        float fifty() { return 55; }

        void PathFlag(Canvas c,float x,float y) {
            PathTriangle(c,x,y);
        }

        void PathTriangle(Canvas c,float x,float y) {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x,y);
            path.lineTo(x+65,y+20);
            path.lineTo(x,y+42);
            path.close();
            p.setColor(Color.RED);
            c.drawPath(path,p);
        }

        void reset() {
            playerX=100; playerY=300;
            velocityY=0; camera=0;
            score=0; lives=3;
            won=false; grounded=false;
            for(int i=0;i<6;i++) collected[i]=false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX()*900f/getWidth();
            float y=e.getY()*500f/getHeight();

            if(won || lives<=0) {
                if(e.getAction()==MotionEvent.ACTION_DOWN) reset();
                return true;
            }

            if(e.getAction()==MotionEvent.ACTION_DOWN ||
               e.getAction()==MotionEvent.ACTION_MOVE) {
                left=x<125 && y>350;
                right=x>=125 && x<250 && y>350;
                if(x>700 && y>350 && grounded) {
                    velocityY=-15;
                    grounded=false;
                }
            } else if(e.getAction()==MotionEvent.ACTION_UP ||
                      e.getAction()==MotionEvent.ACTION_CANCEL) {
                left=false; right=false;
            }
            return true;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(gameLoop);
        }

        Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                updateGame();
                invalidate();
                postDelayed(this,16);
            }
        };

        void updateGame() {
            if(won || lives<=0) return;

            if(left) playerX-=5;
            if(right) playerX+=5;
            if(playerX<0) playerX=0;

            float oldY=playerY;
            velocityY+=0.7f;
            playerY+=velocityY;
            grounded=false;

            for(int i=0;i<platforms.length;i+=3) {
                float x=platforms[i];
                float y=platforms[i+1];
                float width=platforms[i+2];
                if(playerX+playerSize>x &&
                   playerX<x+width &&
                   oldY+playerSize<=y &&
                   playerY+playerSize>=y &&
                   velocityY>=0) {
                    playerY=y-playerSize;
                    velocityY=0;
                    grounded=true;
                }
            }

            for(int i=0;i<6;i++) {
                if(!collected[i] &&
                   Math.abs(playerX+20-coins[i*2])<30 &&
                   Math.abs(playerY+20-coins[i*2+1])<30) {
                    collected[i]=true;
                    score++;
                }
            }

            if(playerY>550) {
                lives--;
                playerX=Math.max(100,playerX-120);
                playerY=200;
                velocityY=0;
            }

            camera=Math.max(0,playerX-350);
            if(playerX>2580) won=true;
        }
    }
}
