
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        gameView = new GameView(this);
        setContentView(gameView);
        startMusic();
    }

    private void startMusic() {
        if (music != null) { if (music.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) music.play(); return; }
        int rate = 22050;
        double[] notes = {523.25,659.25,783.99,659.25,523.25,587.33,659.25,587.33,523.25,783.99,880.00,783.99,659.25,587.33,523.25,392.00};
        int each = rate / 5; short[] data = new short[each * notes.length]; int k = 0;
        for (double f : notes) for (int i = 0; i < each; i++) {
            double env = Math.min(1.0, i / 250.0) * Math.min(1.0, (each-i) / 250.0);
            data[k++] = (short)(Math.sin(2 * Math.PI * f * i / rate) * env * 4200);
        }
        try {
            music = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, data.length * 2, AudioTrack.MODE_STATIC);
            music.write(data, 0, data.length); music.setLoopPoints(0, data.length, -1); music.setVolume(0.30f); music.play();
        } catch (Exception ignored) { if (music != null) { music.release(); music = null; } }
    }
    private void stopMusic() { if (music != null && music.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) music.pause(); }
    @Override protected void onResume() { super.onResume(); startMusic(); }
    @Override protected void onPause() { stopMusic(); super.onPause(); }
    @Override protected void onDestroy() {
        if (music != null) { try { music.stop(); } catch (Exception ignored) {} music.release(); music = null; }
        if (gameView != null) gameView.release(); super.onDestroy();
    }

    private class GameView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Platform> platforms = new ArrayList<>();
        private final ArrayList<Brick> bricks = new ArrayList<>();
        private final ArrayList<Coin> coins = new ArrayList<>();
        private final ArrayList<Enemy> enemies = new ArrayList<>();
        private final ArrayList<Shot> shots = new ArrayList<>();
        private ToneGenerator tones;
        private float sw, sh, cameraX, groundTop, worldWidth = 5200, x = 100, y, vx, vy, pw = 65, ph = 78, flagX;
        private boolean left, right, jumpHeld, fireHeld, onGround, paused, gameOver, facingRight = true;
        private int score = 0, lives = 3, level = 1;
        private long lastTime, lastShot, invincibleUntil;

        GameView(Context c) { super(c); setFocusable(true); setLayerType(View.LAYER_TYPE_SOFTWARE, null); tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 55); }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh); sw = w; sh = h; groundTop = sh * 0.68f;
            pw = Math.max(48, sh * 0.09f); ph = Math.max(58, sh * 0.115f);
            buildLevel();
        }

        private int theme() { return (level - 1) % 4; }
        private float difficulty() { return Math.min(2.8f, 1.0f + (level - 1) * 0.16f); }

        private void buildLevel() {
            platforms.clear(); bricks.clear(); coins.clear(); enemies.clear(); shots.clear();
            worldWidth = 5200 + Math.min(level - 1, 12) * 420; flagX = worldWidth - 180;
            float floorHeight = sh > 0 ? sh * 0.09f : 65;
            platforms.add(new Platform(0, groundTop, worldWidth, floorHeight));
            int platformCount = 22 + Math.min(level, 12);
            float spacing = Math.max(145, 205 - Math.min(level - 1, 10) * 4);
            for (int i = 0; i < platformCount; i++) {
                float bx = 390 + i * spacing;
                float by = groundTop - 105 - (i % 3) * (38 + Math.min(level, 8) * 2);
                platforms.add(new Platform(bx, by, 125, 20));
                if (i % 2 == 0) { bricks.add(new Brick(bx + 28, by - 48)); bricks.add(new Brick(bx + 70, by - 48)); }
                if (i % 2 == 0) coins.add(new Coin(bx + 62, by - 67));
                if (i % 2 == 0 || level >= 4 && i % 3 == 0) addEnemy(bx + 82, groundTop - 58, i % 3);
            }
            for (int i = 0; i < 13 + Math.min(level, 8); i++) {
                float bx = 240 + i * 315; bricks.add(new Brick(bx, groundTop - 140));
                if (i % 2 == 0) coins.add(new Coin(bx + 45, groundTop - 185));
            }
            // Higher levels add extra patrols and flying enemies.
            for (int i = 0; i < Math.min(10, (level - 1) * 2); i++) {
                float ex = 700 + i * (worldWidth - 1100) / Math.max(1, Math.min(10, (level - 1) * 2));
                addEnemy(ex, groundTop - (i % 2 == 0 ? 58 : 155), i % 3);
            }
            x = 100; y = groundTop - ph; vx = vy = 0; cameraX = 0; onGround = true;
            invincibleUntil = System.currentTimeMillis() + 1200; lastTime = 0;
        }

        private void addEnemy(float ex, float ey, int type) {
            enemies.add(new Enemy(ex, ey, type));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); sw = getWidth(); sh = getHeight(); if (sw <= 0 || sh <= 0) return;
            long now = System.currentTimeMillis(); if (lastTime == 0) lastTime = now;
            float dt = Math.min(0.035f, (now - lastTime) / 1000f); lastTime = now;
            if (!paused && !gameOver) update(dt, now);
            drawBackground(c, now);
            c.save(); c.clipRect(0, 0, sw, sh); c.translate(-cameraX, 0); drawWorld(c, now); c.restore();
            drawControls(c); drawHud(c); postInvalidateDelayed(16);
        }

        private void drawBackground(Canvas c, long now) {
            int t = theme(); int top, bottom, far, near, ground;
            if (t == 0) { top = Color.rgb(85,180,250); bottom = Color.rgb(205,235,255); far = Color.rgb(105,190,115); near = Color.rgb(55,150,80); ground = Color.rgb(75,175,70); }
            else if (t == 1) { top = Color.rgb(255,155,75); bottom = Color.rgb(255,220,145); far = Color.rgb(205,130,65); near = Color.rgb(170,95,45); ground = Color.rgb(190,130,65); }
            else if (t == 2) { top = Color.rgb(28,27,70); bottom = Color.rgb(92,65,125); far = Color.rgb(75,55,100); near = Color.rgb(48,40,75); ground = Color.rgb(75,65,85); }
            else { top = Color.rgb(75,160,220); bottom = Color.rgb(220,245,255); far = Color.rgb(155,205,225); near = Color.rgb(100,160,195); ground = Color.rgb(205,230,240); }
            p.setShader(new LinearGradient(0, 0, 0, sh, top, bottom, Shader.TileMode.CLAMP)); c.drawRect(0, 0, sw, sh, p); p.setShader(null);
            if (t == 2) {
                p.setColor(Color.WHITE);
                for (int i = 0; i < 32; i++) { float sx = (i * 83 + 20 - cameraX * 0.08f) % (sw + 30); if (sx < 0) sx += sw + 30; float sy = 25 + (i * 47) % Math.max(60, (int)(groundTop * .65f)); c.drawCircle(sx, sy, i % 4 == 0 ? 2.2f : 1.2f, p); }
                p.setColor(Color.rgb(245,240,195)); c.drawCircle(sw * .78f, 82, 30, p);
            } else {
                p.setColor(Color.WHITE);
                for (int i = 0; i < 5; i++) { float cx = (i * 260 - cameraX * 0.22f) % (sw + 240); if (cx < -120) cx += sw + 240; float cy = 55 + (i % 2) * 42; c.drawCircle(cx + 35, cy, 19, p); c.drawCircle(cx + 62, cy - 9, 26, p); c.drawCircle(cx + 88, cy + 3, 18, p); }
            }
            p.setColor(far); Path hills = new Path(); hills.moveTo(0, groundTop); for (int i = 0; i <= 8; i++) { float hx = i * sw / 8f; float hy = groundTop - 70 - (float)Math.sin(i * 1.7 + t) * 32; hills.lineTo(hx, hy); } hills.lineTo(sw, groundTop); hills.close(); c.drawPath(hills, p);
            p.setColor(near); c.drawRect(0, groundTop - 24, sw, groundTop + 2, p);
            p.setColor(ground); c.drawRect(0, groundTop + 1, sw, sh, p);
            if (t == 0) { p.setColor(Color.rgb(35,125,55)); for (int i=0;i<8;i++){float tx=(i*150-cameraX*.35f)%(sw+50);if(tx<0)tx+=sw+50;c.drawRect(tx,groundTop-80,tx+9,groundTop-25,p);c.drawCircle(tx+4,groundTop-88,22,p);} }
            if (t == 1) { p.setColor(Color.rgb(160,95,45)); for (int i=0;i<7;i++){float cx=(i*180-cameraX*.3f)%(sw+80);if(cx<0)cx+=sw+80;c.drawOval(cx,groundTop-35,cx+50,groundTop-8,p);} }
            if (t == 3) { p.setColor(Color.WHITE); for (int i=0;i<6;i++){float cx=(i*190-cameraX*.3f)%(sw+80);if(cx<0)cx+=sw+80;Path peak=new Path();peak.moveTo(cx,groundTop-20);peak.lineTo(cx+45,groundTop-115);peak.lineTo(cx+90,groundTop-20);peak.close();c.drawPath(peak,p);} }
        }

        private void drawWorld(Canvas c, long now) {
            int t = theme(); int platformColor = t == 1 ? Color.rgb(155,95,48) : t == 2 ? Color.rgb(80,65,100) : t == 3 ? Color.rgb(125,170,190) : Color.rgb(130,78,38);
            for (Platform a : platforms) {
                p.setColor(platformColor); c.drawRect(a.x,a.y,a.x+a.w,a.y+a.h,p);
                p.setColor(t == 3 ? Color.WHITE : t == 2 ? Color.rgb(150,115,180) : Color.rgb(55,180,65)); c.drawRect(a.x,a.y,a.x+a.w,a.y+8,p);
                p.setColor(t == 3 ? Color.rgb(165,205,220) : Color.rgb(90,55,30)); for(float xx=a.x+18;xx<a.x+a.w;xx+=45)c.drawRect(xx,a.y+18,xx+3,a.y+a.h,p);
            }
            for (Brick b : bricks) if (!b.broken) {
                p.setColor(t == 2 ? Color.rgb(105,80,135) : t == 3 ? Color.rgb(165,205,220) : Color.rgb(190,92,35)); c.drawRoundRect(b.x,b.y,b.x+42,b.y+42,5,5,p);
                p.setColor(t == 3 ? Color.WHITE : Color.rgb(255,180,70)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawRect(b.x+3,b.y+3,b.x+39,b.y+39,p); p.setStyle(Paint.Style.FILL);
            }
            for (Coin coin : coins) if (!coin.taken) { p.setColor(Color.YELLOW); c.drawCircle(coin.x,coin.y,13,p); p.setColor(Color.rgb(255,170,0)); c.drawCircle(coin.x,coin.y,7,p); }
            for (Enemy e : enemies) if (e.alive) drawEnemy(c, e, now);
            for (Shot s : shots) { p.setColor(Color.YELLOW); c.drawRoundRect(s.x,s.y,s.x+28,s.y+12,6,6,p); p.setColor(Color.WHITE); c.drawCircle(s.x+22,s.y+6,3,p); }
            drawFlag(c);
            c.save(); if (!facingRight) c.scale(-1,1,x+pw/2f,0); drawNova(c,now); c.restore();
            if (now < invincibleUntil && (now/120)%2==0) { p.setColor(0x66FFFFFF); c.drawRoundRect(x,y,x+pw,y+ph,15,15,p); }
        }

        // Three original, vector-drawn enemies: bouncing slime, walking robot and flying sky bat.
        private void drawEnemy(Canvas c, Enemy e, long now) {
            float bob = e.type == 2 ? (float)Math.sin(now / 180.0 + e.x) * 9 : (float)Math.abs(Math.sin(now / 150.0 + e.x)) * 3;
            c.save(); c.translate(e.x, e.y + bob);
            if (e.type == 0) { // Slime Monster
                p.setColor(Color.rgb(150,55,205)); c.drawOval(3,12,58,55,p); p.setColor(Color.rgb(205,125,245)); c.drawOval(12,16,30,32,p);
                p.setColor(Color.WHITE); c.drawCircle(22,34,7,p); c.drawCircle(42,34,7,p); p.setColor(Color.rgb(35,25,45)); c.drawCircle(24,35,3,p); c.drawCircle(44,35,3,p);
                p.setColor(Color.rgb(65,25,105)); c.drawArc(27,38,42,49,10,150,false,p);
            } else if (e.type == 1) { // Robo Beast
                p.setColor(Color.rgb(225,105,35)); c.drawRoundRect(5,8,56,51,10,10,p); p.setColor(Color.rgb(255,180,65)); c.drawRect(13,14,48,22,p);
                p.setColor(Color.rgb(40,45,55)); c.drawRoundRect(14,26,47,40,5,5,p); p.setColor(Color.RED); c.drawCircle(23,32,4,p); c.drawCircle(39,32,4,p);
                p.setColor(Color.rgb(70,75,85)); c.drawRect(11,48,22,57,p); c.drawRect(40,48,51,57,p); p.setColor(Color.YELLOW); c.drawRect(0,19,7,37,p); c.drawRect(55,19,62,37,p);
            } else { // Sky Bat
                p.setColor(Color.rgb(55,185,125)); Path wing=new Path(); wing.moveTo(30,28); wing.quadTo(8,-4,0,17); wing.lineTo(15,39); wing.lineTo(30,34); wing.quadTo(50,-4,62,17); wing.lineTo(47,39); wing.close(); c.drawPath(wing,p);
                p.setColor(Color.rgb(35,105,100)); c.drawOval(17,12,45,51,p); p.setColor(Color.WHITE); c.drawCircle(25,28,5,p); c.drawCircle(38,28,5,p); p.setColor(Color.rgb(25,35,35)); c.drawCircle(26,29,2,p); c.drawCircle(39,29,2,p);
                p.setColor(Color.WHITE); Path teeth=new Path(); teeth.moveTo(25,43); teeth.lineTo(30,51); teeth.lineTo(34,43); teeth.close(); c.drawPath(teeth,p);
            }
            c.restore();
        }

        private void drawNova(Canvas c, long now) {
            float w=pw,h=ph; float bob=(Math.abs(vx)>5&&onGround)?(float)Math.sin(now/75.0)*2.5f:0; c.save(); c.translate(x,y+bob);
            p.setColor(Color.rgb(235,130,35)); c.drawRoundRect(w*.12f,h*.40f,w*.34f,h*.77f,7,7,p);
            p.setColor(Color.rgb(35,55,85)); float step=(Math.abs(vx)>5&&onGround)?(float)Math.sin(now/65.0)*5:0;
            c.drawRoundRect(w*.34f,h*.72f,w*.49f,h*.91f+step,5,5,p); c.drawRoundRect(w*.54f,h*.72f,w*.69f,h*.91f-step,5,5,p);
            p.setColor(Color.rgb(45,35,30)); c.drawRoundRect(w*.27f,h*.87f+step,w*.51f,h*.98f+step,5,5,p); c.drawRoundRect(w*.52f,h*.87f-step,w*.77f,h*.98f-step,5,5,p);
            p.setColor(Color.rgb(20,155,145)); c.drawRoundRect(w*.22f,h*.37f,w*.79f,h*.77f,10,10,p); p.setColor(Color.rgb(245,205,75)); c.drawRect(w*.47f,h*.42f,w*.52f,h*.72f,p);
            p.setColor(Color.rgb(20,155,145)); c.drawRoundRect(w*.08f,h*.43f,w*.27f,h*.67f,7,7,p); c.drawRoundRect(w*.73f,h*.43f,w*.91f,h*.65f,7,7,p);
            p.setColor(Color.rgb(238,183,137)); c.drawCircle(w*.16f,h*.68f,w*.09f,p); c.drawCircle(w*.85f,h*.66f,w*.09f,p);
            p.setColor(Color.rgb(245,115,35)); Path scarf=new Path(); scarf.moveTo(w*.38f,h*.35f); scarf.lineTo(w*.67f,h*.38f); scarf.lineTo(w*.59f,h*.51f); scarf.lineTo(w*.40f,h*.47f); scarf.close(); c.drawPath(scarf,p);
            Path tail=new Path(); tail.moveTo(w*.62f,h*.43f); tail.lineTo(w*.98f,h*.52f); tail.lineTo(w*.72f,h*.57f); tail.close(); c.drawPath(tail,p);
            p.setColor(Color.rgb(245,198,155)); c.drawOval(w*.25f,h*.05f,w*.78f,h*.45f,p); p.setColor(Color.rgb(55,38,30));
            Path hair=new Path(); hair.moveTo(w*.24f,h*.25f); hair.quadTo(w*.20f,h*.02f,w*.48f,h*.03f); hair.quadTo(w*.77f,h*.00f,w*.81f,h*.25f); hair.lineTo(w*.68f,h*.18f); hair.lineTo(w*.57f,h*.25f); hair.lineTo(w*.47f,h*.17f); hair.lineTo(w*.34f,h*.28f); hair.close(); c.drawPath(hair,p);
            p.setColor(Color.rgb(35,35,40)); c.drawCircle(w*.62f,h*.29f,w*.035f,p); p.setStrokeWidth(1.8f); c.drawLine(w*.57f,h*.23f,w*.68f,h*.22f,p);
            p.setColor(Color.rgb(160,65,55)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); c.drawArc(w*.52f,h*.30f,w*.69f,h*.39f,15,65,false,p); p.setStyle(Paint.Style.FILL); c.restore();
        }

        private void drawFlag(Canvas c) {
            float bottom=groundTop; p.setColor(Color.DKGRAY); c.drawRect(flagX,bottom-210,flagX+8,bottom,p);
            p.setColor(Color.RED); Path path=new Path(); path.moveTo(flagX+8,bottom-208); path.lineTo(flagX+95,bottom-180); path.lineTo(flagX+8,bottom-152); path.close(); c.drawPath(path,p);
            p.setColor(Color.WHITE); p.setTextSize(22); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("LEVEL " + level,flagX-25,bottom-220,p);
        }

        private void update(float dt,long now) {
            float moveSpeed = 330 + Math.min(level-1,12)*13;
            if(left){vx=-moveSpeed;facingRight=false;} else if(right){vx=moveSpeed;facingRight=true;} else vx*=0.72f;
            if(jumpHeld&&onGround){vy=-(690+Math.min(level-1,8)*8);onGround=false;tones.startTone(ToneGenerator.TONE_PROP_BEEP,80);}
            float oldY=y; vy+=1650*dt; x+=vx*dt; y+=vy*dt; x=Math.max(0,Math.min(x,worldWidth-pw)); onGround=false;
            if(vy>=0) for(Platform a:platforms) if(x+pw>a.x&&x<a.x+a.w&&oldY+ph<=a.y+12&&y+ph>=a.y){y=a.y-ph;vy=0;onGround=true;}
            if(vy<0) for(Brick b:bricks) if(!b.broken&&x+pw>b.x&&x<b.x+42&&oldY>=b.y+38&&y<=b.y+42){b.broken=true;vy=80;score+=5;tones.startTone(ToneGenerator.TONE_PROP_ACK,70);}
            if(y>sh+100){loseLife();return;}
            for(Coin coin:coins) if(!coin.taken&&Math.abs(x+pw/2-coin.x)<48&&Math.abs(y+ph/2-coin.y)<55){coin.taken=true;score+=10;tones.startTone(ToneGenerator.TONE_PROP_BEEP2,70);}
            float enemySpeed = (85 + level*5) * difficulty();
            for(Enemy e:enemies) if(e.alive){
                if(e.type==2){e.x+=e.dir*enemySpeed*1.12f*dt;e.y=e.baseY+(float)Math.sin(now/190.0+e.startX)*18;}
                else {e.x+=e.dir*enemySpeed*dt; if(e.x<e.startX-65||e.x>e.startX+65)e.dir*=-1;}
                if(rectHit(x+8,y+8,pw-16,ph-12,e.x,e.y,62,58)&&now>invincibleUntil){if(vy>150&&y+ph-15<e.y+25){e.alive=false;vy=-430;score+=20;}else{loseLife();return;}}
            }
            if(fireHeld&&now-lastShot>Math.max(180,350-(level-1)*10)){shots.add(new Shot(facingRight?x+pw:x-28,y+45,facingRight?560+level*12:-560-level*12));lastShot=now;tones.startTone(ToneGenerator.TONE_PROP_BEEP,50);}
            Iterator<Shot> it=shots.iterator(); while(it.hasNext()){Shot s=it.next();s.x+=s.v*dt;if(s.x<0||s.x>worldWidth){it.remove();continue;}boolean hit=false;for(Enemy e:enemies)if(e.alive&&rectHit(s.x,s.y,32,22,e.x,e.y,62,58)){e.alive=false;score+=20;hit=true;break;}if(hit)it.remove();}
            float target=x-sw*0.38f;cameraX=Math.max(0,Math.min(target,worldWidth-sw));
            if(x+pw>=flagX-12){level++;buildLevel();}
        }

        private boolean rectHit(float ax,float ay,float aw,float ah,float bx,float by,float bw,float bh){return ax<bx+bw&&ax+aw>bx&&ay<by+bh&&ay+ah>by;}
        private void loseLife(){long now=System.currentTimeMillis();if(now<invincibleUntil)return;lives--;tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,180);if(lives<=0){gameOver=true;left=right=jumpHeld=fireHeld=false;}else{x=Math.max(100,cameraX+80);y=groundTop-ph;vy=0;vx=0;onGround=true;invincibleUntil=now+1600;}}

        // Larger, clearly separated touch controls. Total height is 104 px (or a screen-relative minimum).
        private float controlTop(){return sh-Math.max(112,sh*0.19f);}
        private void drawControls(Canvas c){float top=controlTop(),bottom=sh-10;float h=bottom-top;
            drawButton(c,12,top,12+Math.min(112,sw*.19f),bottom,"◀",0xAA263B55,38);
            drawButton(c,132,top,132+Math.min(112,sw*.19f),bottom,"▶",0xAA263B55,38);
            float jumpL=sw-12-Math.min(120,sw*.21f),fireR=jumpL-12,fireL=fireR-Math.min(120,sw*.21f);
            drawButton(c,fireL,top,fireR,bottom,"FIRE",0xAA9A4A16,23);
            drawButton(c,jumpL,top,sw-12,bottom,"JUMP",0xAA176D9A,23);
        }
        private void drawButton(Canvas c,float l,float t,float r,float b,String label,int color,float textSize){
            p.setColor(0x66000000);c.drawRoundRect(l+3,t+4,r+3,b+4,22,22,p);p.setColor(color);c.drawRoundRect(l,t,r,b,22,22,p);
            p.setColor(0x66FFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(l+2,t+2,r-2,b-2,20,20,p);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);p.setTextSize(textSize);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,(l+r)/2,(t+b)/2-textSize*.0f+textSize*.35f,p);p.setTextAlign(Paint.Align.LEFT);
        }
        private void drawHud(Canvas c){
            p.setColor(0xBB000000);c.drawRoundRect(10,10,Math.min(sw-80,330),60,10,10,p);p.setColor(Color.WHITE);p.setTextSize(19);p.setTypeface(Typeface.DEFAULT_BOLD);
            String name=theme()==0?"FOREST":theme()==1?"DESERT":theme()==2?"CASTLE":"ICE MOUNTAIN";
            c.drawText("Score: "+score+"  Lives: "+lives,20,32,p);c.drawText("Level "+level+" - "+name,20,53,p);
            drawButton(c,sw-65,10,sw-10,60,paused?"▶":"Ⅱ",0xAA303030,25);
            if(paused||gameOver){p.setColor(0xAA000000);c.drawRect(0,0,sw,sh,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(42);c.drawText(gameOver?"GAME OVER":"PAUSED",sw/2,sh/2-15,p);p.setTextSize(21);c.drawText("Tap the screen to "+(gameOver?"restart":"continue"),sw/2,sh/2+30,p);p.setTextAlign(Paint.Align.LEFT);}
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();float tx=e.getX(),ty=e.getY();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                if(gameOver){gameOver=false;score=0;lives=3;level=1;buildLevel();return true;}
                if(tx>sw-75&&ty<75){paused=!paused;return true;}
                if(paused){paused=false;return true;}
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){left=right=jumpHeld=fireHeld=false;return true;}
            left=right=jumpHeld=fireHeld=false;
            for(int i=0;i<e.getPointerCount();i++){if(i==e.getActionIndex()&&action==MotionEvent.ACTION_POINTER_UP)continue;setControl(e.getX(i),e.getY(i));}
            return true;
        }
        private void setControl(float tx,float ty){
            float top=controlTop(),leftW=Math.min(112,sw*.19f),jumpW=Math.min(120,sw*.21f);
            if(ty<top)return;
            if(tx>=12&&tx<=12+leftW)left=true;
            else if(tx>=132&&tx<=132+leftW)right=true;
            else {float jumpL=sw-12-jumpW,fireR=jumpL-12,fireL=fireR-jumpW;
                if(tx>=jumpL&&tx<=sw-12)jumpHeld=true;else if(tx>=fireL&&tx<=fireR)fireHeld=true;
            }
        }
        void release(){try{tones.release();}catch(Exception ignored){}}

        private class Platform{float x,y,w,h;Platform(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;}}
        private class Brick{float x,y;boolean broken=false;Brick(float x,float y){this.x=x;this.y=y;}}
        private class Coin{float x,y;boolean taken=false;Coin(float x,float y){this.x=x;this.y=y;}}
        private class Enemy{float x,y,startX,baseY,dir=1;int type;boolean alive=true;Enemy(float x,float y,int type){this.x=x;this.y=y;this.baseY=y;this.startX=x;this.type=type;}}
        private class Shot{float x,y,v;Shot(float x,float y,float v){this.x=x;this.y=y;this.v=v;}}
    }
}
