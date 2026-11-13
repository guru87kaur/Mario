package com.example.jumphero;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Color;
import android.os.SystemClock;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new GameView());
    }

    class GameView extends View {
        Paint p = new Paint(3);
        long last = 0;
        float playerX = 90, playerY = 0, vy = 0;
        float camera = 0;
        float worldW = 2400;
        float groundY = 0;
        int score = 0, lives = 3;
        boolean left, right, jumpHeld, onGround = false, gameOver = false, won = false;
        boolean[] coinTaken = new boolean[12];
        float enemyX = 930, enemyDir = 1;
        float scale = 1, viewW, viewH;
        RectF player = new RectF();
        final float gravity = 1800f, moveSpeed = 310f, jumpPower = -690f;

        GameView() { super(MainActivity.this); setFocusable(true); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            viewW = getWidth(); viewH = getHeight();
            if (viewW <= 0 || viewH <= 0) return;
            scale = viewH / 540f;
            c.save();
            c.scale(scale, scale);
            float w = viewW / scale, h = 540;
            groundY = 450;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.04f, (now-last)/1000f);
            last = now;
            if (!gameOver && !won) update(dt, w);
            drawWorld(c, w, h);
            c.restore();
            postInvalidateDelayed(16);
        }

        void update(float dt, float w) {
            if (left) playerX -= moveSpeed*dt;
            if (right) playerX += moveSpeed*dt;
            playerX = Math.max(0, Math.min(worldW-70, playerX));
            if (jumpHeld && onGround) { vy = jumpPower; onGround = false; }
            vy += gravity*dt;
            playerY += vy*dt;
            float feet = playerY + 58;
            float platform = platformTop(playerX+25, playerX+55);
            if (feet >= platform && vy >= 0) {
                playerY = platform-58; vy=0; onGround=true;
            } else onGround=false;
            if (playerY > 650) {
                lives--;
                if (lives <= 0) gameOver=true;
                else { playerX=Math.max(50, playerX-160); playerY=groundY-58; vy=0; }
            }
            enemyX += enemyDir*105*dt;
            if (enemyX < 820 || enemyX > 1190) enemyDir *= -1;
            RectF pr = new RectF(playerX+8,playerY+5,playerX+52,playerY+58);
            RectF er = new RectF(enemyX,groundY-38,enemyX+42,groundY);
            if (RectF.intersects(pr,er)) {
                if (vy > 100 && playerY+58 < groundY-8) { enemyX=1800; vy=-380; score+=50; }
                else { lives--; playerX=Math.max(40,playerX-120); playerY=groundY-58; vy=0; if(lives<=0)gameOver=true; }
            }
            float[] cx={300,390,540,680,820,1030,1170,1330,1490,1650,1840,2030};
            float[] cy={370,330,365,300,360,335,370,315,360,330,370,335};
            for(int i=0;i<cx.length;i++){
                if(!coinTaken[i] && Math.abs((playerX+30)-cx[i])<34 && Math.abs((playerY+28)-cy[i])<40){
                    coinTaken[i]=true; score+=10;
                }
            }
            if(playerX > 2240) won=true;
            camera = Math.max(0, Math.min(worldW-w, playerX-w*0.36f));
        }

        float platformTop(float x1,float x2) {
            float top=groundY;
            float[][] plats={{450,520,365},{620,710,315},{880,970,350},{1120,1210,330},{1390,1480,300},{1660,1760,345},{1910,2010,315}};
            for(float[] a:plats) if(x2>a[0] && x1<a[1] && playerY+58<=a[2]+22) top=Math.min(top,a[2]);
            return top;
        }

        void drawWorld(Canvas c,float w,float h) {
            c.drawColor(Color.rgb(115,205,255));
            // distant hills
            p.setColor(Color.rgb(91,190,120));
            for(int i=-1;i<7;i++){ float x=i*440-(camera*.25f); c.drawCircle(x+170,445,155,p); }
            p.setColor(Color.rgb(255,238,145)); c.drawCircle(w-90,85,42,p);
            c.save(); c.translate(-camera,0);
            // ground
            p.setColor(Color.rgb(135,83,45)); c.drawRect(0,groundY,worldW,560,p);
            p.setColor(Color.rgb(55,175,75)); c.drawRect(0,groundY,worldW,groundY+14,p);
            // platforms
            float[][] plats={{450,520,365},{620,710,315},{880,970,350},{1120,1210,330},{1390,1480,300},{1660,1760,345},{1910,2010,315}};
            for(float[] a:plats){ p.setColor(Color.rgb(135,83,45)); c.drawRoundRect(a[0],a[2],a[1],a[2]+22,6,6,p); p.setColor(Color.rgb(63,185,78)); c.drawRect(a[0],a[2],a[1],a[2]+7,p); }
            // coins
            float[] cx={300,390,540,680,820,1030,1170,1330,1490,1650,1840,2030};
            float[] cy={370,330,365,300,360,335,370,315,360,330,370,335};
            for(int i=0;i<cx.length;i++) if(!coinTaken[i]){
                p.setColor(Color.rgb(255,193,25)); c.drawCircle(cx[i],cy[i],12,p);
                p.setColor(Color.rgb(255,239,120)); c.drawCircle(cx[i]-3,cy[i]-3,4,p);
            }
            // enemy
            p.setColor(Color.rgb(145,75,190)); c.drawRoundRect(enemyX,groundY-38,enemyX+42,groundY-4,12,12,p);
            p.setColor(Color.WHITE); c.drawCircle(enemyX+12,groundY-26,5,p); c.drawCircle(enemyX+30,groundY-26,5,p);
            p.setColor(Color.BLACK); c.drawCircle(enemyX+13,groundY-26,2,p); c.drawCircle(enemyX+31,groundY-26,2,p);
            // goal flag
            p.setColor(Color.DKGRAY); c.drawRect(2260,260,2268,450,p);
            p.setColor(Color.rgb(255,75,80)); PathTriangle(c,2268,265,2330,290,2268,315);
            // hero
            drawHero(c,playerX,playerY);
            c.restore();
            // HUD
            p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD)); p.setTextSize(25); p.setColor(Color.WHITE);
            c.drawText("JUMP HERO",22,35,p);
            p.setTextSize(20); c.drawText("COINS / SCORE: "+score+"     LIVES: "+lives,22,66,p);
            // controls
            drawButton(c,65, h-65, "◀"); drawButton(c,155,h-65,"▶"); drawButton(c,w-90,h-65,"JUMP");
            if(gameOver || won){
                p.setColor(0x99000000); c.drawRect(0,0,w,h,p);
                p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(42);
                c.drawText(won?"LEVEL COMPLETE!":"GAME OVER",w/2,h/2-15,p);
                p.setTextSize(22); c.drawText("Tap anywhere to play again",w/2,h/2+30,p); p.setTextAlign(Paint.Align.LEFT);
            }
        }

        void PathTriangle(Canvas c,float x1,float y1,float x2,float y2,float x3,float y3){
            android.graphics.Path path=new android.graphics.Path(); path.moveTo(x1,y1);path.lineTo(x2,y2);path.lineTo(x3,y3);path.close();p.setColor(Color.RED);c.drawPath(path,p);
        }
        void drawHero(Canvas c,float x,float y){
            p.setColor(Color.rgb(35,95,220)); c.drawRoundRect(x+8,y+28,x+52,y+59,8,8,p);
            p.setColor(Color.rgb(255,190,135)); c.drawCircle(x+30,y+17,17,p);
            p.setColor(Color.rgb(35,95,220)); c.drawRoundRect(x+10,y,x+50,y+8,5,5,p);
            p.setColor(Color.rgb(245,75,65)); c.drawRoundRect(x+9,y-3,x+51,y+5,5,5,p);
            p.setColor(Color.WHITE); c.drawCircle(x+24,y+16,4,p); c.drawCircle(x+37,y+16,4,p);
            p.setColor(Color.BLACK); c.drawCircle(x+25,y+16,2,p); c.drawCircle(x+38,y+16,2,p);
            p.setColor(Color.rgb(90,50,35)); c.drawRect(x+8,y+54,x+27,y+61,p); c.drawRect(x+34,y+54,x+53,y+61,p);
        }
        void drawButton(Canvas c,float x,float y,String label){
            p.setColor(0xAA17385B); c.drawCircle(x,y,39,p);
            p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(label.equals("JUMP")?15:34); c.drawText(label,x,y+(label.equals("JUMP")?5:12),p); p.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX()/scale, y=e.getY()/scale, w=getWidth()/scale;
            if(e.getAction()==MotionEvent.ACTION_DOWN && (gameOver||won)){
                reset(); invalidate(); return true;
            }
            if(e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE){
                left = x<110 && y>getHeight()/scale-125;
                right = x>=110 && x<220 && y>getHeight()/scale-125;
                jumpHeld = x>w-165 && y>getHeight()/scale-135;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                left=false; right=false; jumpHeld=false; return true;
            }
            return true;
        }
        void reset(){
            playerX=90;playerY=groundY-58;vy=0;camera=0;score=0;lives=3;
            enemyX=930;enemyDir=1;gameOver=false;won=false;
            for(int i=0;i<coinTaken.length;i++)coinTaken[i]=false;
        }
    }
                                                                                          }
