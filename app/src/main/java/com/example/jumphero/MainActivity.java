
package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.media.ToneGenerator;
import android.media.AudioManager;
import java.util.ArrayList;

public class MainActivity extends Activity {

    GameView game;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        game = new GameView();
        setContentView(game);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (game != null) game.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) game.resume();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.close();
        super.onDestroy();
    }

    class GameView extends View {

        Paint p = new Paint(3);
        Handler handler = new Handler();
        ToneGenerator sound = new ToneGenerator(
            AudioManager.STREAM_MUSIC, 70);

        class Platform {
            float x, y, w, h;
            Platform(float a,float b,float c,float d) {
                x=a; y=b; w=c; h=d;
            }
        }

        class Coin {
            float x,y;
            boolean taken=false;
            Coin(float a,float b) { x=a; y=b; }
        }

        class Enemy {
            float x,y;
            boolean alive=true;
            int direction=1;
            Enemy(float a,float b) { x=a; y=b; }
        }

        ArrayList<Platform> platforms = new ArrayList<>();
        ArrayList<Coin> coins = new ArrayList<>();
        ArrayList<Enemy> enemies = new ArrayList<>();

        float heroX=100, heroY=200;
        float vx=0, vy=0;
        float cameraX=0, ground;
        float levelEnd=4200;

        int score=0, lives=3;
        int animation=0;
        int musicNote=0;

        boolean left=false, right=false;
        boolean running=true, gameOver=false;
        boolean won=false, music=true;
        boolean jumpPressed=false;

        final float gravity=0.65f;
        final float jumpPower=-17f;
        final float heroW=42, heroH=55;

        int[] melody={
            ToneGenerator.TONE_DTMF_1,
            ToneGenerator.TONE_DTMF_3,
            ToneGenerator.TONE_DTMF_5,
            ToneGenerator.TONE_DTMF_8,
            ToneGenerator.TONE_DTMF_5,
            ToneGenerator.TONE_DTMF_3
        };

        Runnable loop=new Runnable() {
            public void run() {
                if(!running) return;
                updateGame();
                invalidate();
                handler.postDelayed(this,16);
            }
        };

        Runnable musicLoop=new Runnable() {
            public void run() {
                if(!running) return;
                if(music && !gameOver && !won) {
                    sound.startTone(melody[musicNote],130);
                    musicNote=(musicNote+1)%melody.length;
                }
                handler.postDelayed(this,350);
            }
        };

        GameView() {
            super(MainActivity.this);
            setFocusable(true);
            createLevel();
            handler.post(loop);
            handler.postDelayed(musicLoop,400);
        }

        void createLevel() {
            platforms.clear();
            coins.clear();
            enemies.clear();

            platforms.add(new Platform(0,500,650,200));
            platforms.add(new Platform(760,500,600,200));
            platforms.add(new Platform(1470,500,650,200));
            platforms.add(new Platform(2250,500,650,200));
            platforms.add(new Platform(3050,500,1250,200));

            platforms.add(new Platform(330,390,120,30));
            platforms.add(new Platform(850,390,150,30));
            platforms.add(new Platform(1120,320,130,30));
            platforms.add(new Platform(1580,390,130,30));
            platforms.add(new Platform(1850,320,130,30));
            platforms.add(new Platform(2370,390,160,30));
            platforms.add(new Platform(2700,320,140,30));
            platforms.add(new Platform(3300,390,160,30));
            platforms.add(new Platform(3650,320,160,30));

            for(int i=0;i<24;i++) {
                float cx=260+i*145;
                float cy=(i%3==0)?330:430;
                coins.add(new Coin(cx,cy));
            }

            enemies.add(new Enemy(560,455));
            enemies.add(new Enemy(1020,455));
            enemies.add(new Enemy(1700,455));
            enemies.add(new Enemy(2480,455));
            enemies.add(new Enemy(3400,455));
        }

        void pause() {
            running=false;
            handler.removeCallbacks(loop);
            handler.removeCallbacks(musicLoop);
            sound.stopTone();
        }

        void resume() {
            if(running) return;
            running=true;
            handler.post(loop);
            handler.post(musicLoop);
        }

        void close() {
            running=false;
            handler.removeCallbacksAndMessages(null);
            sound.stopTone();
            sound.release();
        }

        void restart() {
            heroX=100;
            heroY=250;
            vy=0;
            cameraX=0;
            score=0;
            lives=3;
            gameOver=false;
            won=false;
            createLevel();
        }

        void updateGame() {
            if(gameOver || won || getWidth()==0) return;

            float w=getWidth();
            float h=getHeight();
            ground=h*0.82f;

            float oldBottom=heroY+heroH;

            if(left) vx=-4.5f;
            else if(right) vx=4.5f;
            else vx=0;

            heroX+=vx;
            heroX=Math.max(0,Math.min(levelEnd-heroW,heroX));

            vy+=gravity;
            heroY+=vy;

            // Platform landing
            boolean landed=false;

            for(Platform b:platforms) {
                float by=ground/500f*b.y;
                float bh=ground/500f*b.h;

                if(vy>=0 &&
                   oldBottom<=by+5 &&
                   heroY+heroH>=by &&
                   heroX+heroW>b.x &&
                   heroX<b.x+b.w) {
                    heroY=by-heroH;
                    vy=0;
                    landed=true;
                }
            }

            // Enemy movement
            for(Enemy e:enemies) {
                if(!e.alive) continue;
                e.x+=e.direction*1.3f;
                if(e.x<80 || e.x>levelEnd-100)
                    e.direction*=-1;

                if(Math.abs(heroX-e.x)<38 &&
                   Math.abs(heroY-e.y)<45) {
                    if(vy>1 && heroY+heroH<e.y+22) {
                        e.alive=false;
                        vy=-10;
                        score+=100;
                        sound.startTone(
                            ToneGenerator.TONE_PROP_ACK,130);
                    } else {
                        loseLife();
                    }
                }
            }

            // Coin collection
            for(Coin c:coins) {
                if(!c.taken &&
                   Math.abs(heroX-c.x)<35 &&
                   Math.abs(heroY-c.y)<45) {
                    c.taken=true;
                    score+=10;
                    sound.startTone(
                        ToneGenerator.TONE_PROP_ACK,130);
                }
            }

            // Falling
            if(heroY>h+100) loseLife();

            // Camera follows hero
            cameraX=Math.max(0,heroX-w*0.35f);
            cameraX=Math.min(cameraX,levelEnd-w);

            if(heroX>=levelEnd-100) won=true;

            animation++;
        }

        void loseLife() {
            lives--;
            if(lives<=0) {
                gameOver=true;
                sound.startTone(
                    ToneGenerator.TONE_PROP_NACK,400);
            } else {
                heroX=Math.max(100,heroX-150);
                heroY=150;
                vy=0;
            }
        }

        void rect(Canvas c,float x,float y,
                  float w,float h,int color) {
            p.setColor(color);
            c.drawRect(x,y,x+w,y+h,p);
        }

        void drawHero(Canvas c) {
            float x=heroX;
            float y=heroY;

            // Shoes
            rect(c,x+2,y+46,19,9,Color.rgb(90,35,15));
            rect(c,x+25,y+46,19,9,Color.rgb(90,35,15));

            // Legs
            rect(c,x+8,y+34,12,14,Color.BLUE);
            rect(c,x+27,y+34,12,14,Color.BLUE);

            // Overalls
            rect(c,x+7,y+26,34,17,Color.rgb(20,70,220));

            // Shirt and arms
            rect(c,x+5,y+16,34,15,Color.RED);
            rect(c,x,y+18,8,17,0xffffcc99);
            rect(c,x+38,y+18,8,17,0xffffcc99);

            // Face
            rect(c,x+10,y+3,29,25,0xffffcc99);

            // Cap
            rect(c,x+5,y-5,38,10,Color.RED);
            rect(c,x+1,y+2,44,7,Color.RED);

            // Eye
            p.setColor(Color.WHITE);
            c.drawCircle(x+32,y+13,4,p);
            p.setColor(Color.BLACK);
            c.drawCircle(x+33,y+13,2,p);

            // Mustache
            rect(c,x+22,y+20,17,4,Color.BLACK);

            // Buttons
            p.setColor(Color.YELLOW);
            c.drawCircle(x+17,y+31,3,p);
            c.drawCircle(x+34,y+31,3,p);
        }

        void drawWorld(Canvas c) {
            float h=getHeight();
            ground=h*0.82f;

            c.save();
            c.translate(-cameraX,0);

            // Hills
            for(int i=0;i<20;i++) {
                p.setColor(0xff49ae59);
                c.drawCircle(i*260+80,ground,105,p);
            }

            // Ground
            rect(c,0,ground,levelEnd,200,0xff98532d);
            rect(c,0,ground,levelEnd,13,0xff35b83d);

            // Platforms and brick blocks
            for(Platform b:platforms) {
                float by=ground/500f*b.y;
                float bh=ground/500f*b.h;

                rect(c,b.x,by,b.w,bh,0xffb96a30);
                rect(c,b.x,by,b.w,7,0xffffc04d);

                p.setColor(0xff713a20);
                for(float bx=b.x+35;bx<b.x+b.w;bx+=40) {
                    c.drawLine(bx,by,bx,by+bh,p);
                }
            }

            // Question blocks
            for(int i=0;i<7;i++) {
                float bx=500+i*500;
                rect(c,bx,ground*0.58f,42,42,0xffffb521);
                p.setColor(Color.WHITE);
                p.setTextSize(30);
                c.drawText("?",bx+11,ground*0.58f+32,p);
            }

            // Coins
            for(Coin coin:coins) {
                if(coin.taken) continue;
                p.setColor(Color.YELLOW);
                c.drawOval(coin.x-9,coin.y-15,
                    coin.x+9,coin.y+15,p);
                p.setColor(0xffffe98a);
                c.drawLine(coin.x,coin.y-9,
                    coin.x,coin.y+9,p);
            }

            // Enemies
            for(Enemy e:enemies) {
                if(!e.alive) continue;
                p.setColor(0xff91451f);
                c.drawOval(e.x-19,e.y-17,
                    e.x+19,e.y+17,p);
                p.setColor(Color.WHITE);
                c.drawCircle(e.x-7,e.y-4,5,p);
                c.drawCircle(e.x+7,e.y-4,5,p);
                p.setColor(Color.BLACK);
                c.drawCircle(e.x-7,e.y-4,2,p);
                c.drawCircle(e.x+7,e.y-4,2,p);
            }

            // Finish flag
            rect(c,levelEnd-70,ground-160,7,160,Color.WHITE);
            p.setColor(Color.RED);
            PathFlag(c,levelEnd-63,ground-160);

            drawHero(c);
            c.restore();
        }

        void PathFlag(Canvas c,float x,float y) {
            p.setColor(Color.RED);
            c.drawRect(x,y,x+65,y+35,p);
        }

        void button(Canvas c,float x,float y,String text) {
            p.setColor(0xaa222222);
            c.drawCircle(x,y,39,p);
            p.setColor(Color.WHITE);
            p.setTextSize(27);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(text,x,y+9,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth();
            float h=getHeight();

            c.drawColor(0xff69c5f5);

            // Sun
            p.setColor(Color.YELLOW);
            c.drawCircle(w-65,65,32,p);

            // Clouds
            p.setColor(Color.WHITE);
            for(int i=0;i<6;i++) {
                float cx=(i*220-cameraX*0.35f)%(w+120);
                if(cx<0) cx+=w+120;
                c.drawCircle(cx,105,23,p);
                c.drawCircle(cx+25,90,28,p);
                c.drawCircle(cx+50,105,23,p);
            }

            drawWorld(c);

            // Fixed HUD
            p.setColor(Color.WHITE);
            p.setTextSize(25);
            c.drawText("SCORE: "+score,20,35,p);
            c.drawText("LIVES: "+lives,20,68,p);

            button(c,w*0.12f,h*0.90f,"<");
            button(c,w*0.29f,h*0.90f,">");
            button(c,w*0.88f,h*0.90f,"J");

            if(gameOver || won) {
                p.setColor(0xcc000000);
                c.drawRect(0,0,w,h,p);
                p.setColor(Color.WHITE);
                p.setTextSize(38);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(won?"LEVEL COMPLETE!":"GAME OVER",
                    w/2,h/2,p);
                p.setTextSize(23);
                c.drawText("TAP TO RESTART",w/2,h/2+45,p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float tx=e.getX();
            float ty=e.getY();
            float w=getWidth();
            float h=getHeight();

            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                if(gameOver || won) {
                    restart();
                    invalidate();
                    return true;
                }

                if(ty>h*0.78f) {
                    if(tx<w*0.20f) left=true;
                    else if(tx<w*0.40f) right=true;
                    else if(tx>w*0.70f) {
                        if(!jumpPressed) {
                            vy=jumpPower;
                            jumpPressed=true;
                            sound.startTone(
                                ToneGenerator.TONE_PROP_BEEP,130);
                        }
                    }
                }
                return true;
            }

            if(e.getAction()==MotionEvent.ACTION_MOVE) {
                left=false;
                right=false;

                if(ty>h*0.78f) {
                    if(tx<w*0.20f) left=true;
                    else if(tx<w*0.40f) right=true;
                }
                return true;
            }

            if(e.getAction()==MotionEvent.ACTION_UP ||
               e.getAction()==MotionEvent.ACTION_CANCEL) {
                left=false;
                right=false;
                jumpPressed=false;
                return true;
            }

            return true;
        }
    }
}

