package dev.bladelight.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** One optical grid and stroke weight for every interface icon. */
public final class BladeIcon extends Drawable {
    private final String name;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public BladeIcon(String name, int color) {
        this.name = name; p.setColor(color); p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.65f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }
    private void line(Canvas c,float... xy) {
        Path path = new Path(); path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2) path.lineTo(xy[i],xy[i+1]);
        c.drawPath(path,p);
    }
    @Override public void draw(Canvas c) {
        c.save(); c.translate(getBounds().left,getBounds().top);
        c.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch(name) {
            case "sun":
                c.drawCircle(12,12,3.5f,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*7.4f,12+(float)Math.sin(a)*7.4f,12+(float)Math.cos(a)*9.2f,12+(float)Math.sin(a)*9.2f);} break;
            case "power": c.drawArc(4,4,20,20,-48,276,false,p); line(c,12,3,12,11); break;
            case "pattern":
                Path wave=new Path();wave.moveTo(3,12);wave.cubicTo(6,12,6,5,9,5);
                wave.cubicTo(12,5,12,19,15,19);wave.cubicTo(18,19,18,12,21,12);c.drawPath(wave,p);break;
            case "steady": line(c,4,12,20,12);break;
            case "blink": line(c,3,12,7,12);line(c,10,12,14,12);line(c,17,12,21,12);break;
            case "settings":
                line(c,4,6,8,6); line(c,12,6,20,6); c.drawCircle(10,6,2,p);
                line(c,4,12,14,12); line(c,18,12,20,12); c.drawCircle(16,12,2,p);
                line(c,4,18,6,18); line(c,10,18,20,18); c.drawCircle(8,18,2,p); break;
            case "back": line(c,14.5f,5.5f,8,12,14.5f,18.5f); break;
            case "next": line(c,9.5f,6,15.5f,12,9.5f,18); break;
            case "close": line(c,6,6,18,18); line(c,18,6,6,18); break;
            case "check": line(c,5,12,10,17,19,7); break;
            case "auto":
                // Two open links and one joining stroke; no nested arrows or emblem.
                c.rotate(-40,12,12);
                c.drawArc(2.5f,8,13.5f,16,60,240,false,p);
                c.drawArc(10.5f,8,21.5f,16,240,240,false,p);
                line(c,8.5f,12,15.5f,12); break;
            case "buds":
                c.drawRoundRect(4.5f,4,9,20,1.1f,1.1f,p); c.drawRoundRect(15,4,19.5f,20,1.1f,1.1f,p);
                line(c,6.7f,7.5f,6.7f,16.5f); line(c,17.2f,7.5f,17.2f,16.5f); break;
            case "theme":
                c.drawCircle(12,12,8,p); line(c,12,4,12,20);
                line(c,15,7,15,17); line(c,18,10,18,14); break;
            case "moon":
                Path moon=new Path(); moon.moveTo(19.5f,14.5f); moon.cubicTo(10,18,6,8,10.5f,4);
                moon.cubicTo(1,6,2,20,12,20); moon.cubicTo(16,20,18.5f,17.5f,19.5f,14.5f); c.drawPath(moon,p); break;
            case "phone": c.drawRoundRect(6,3,18,21,2,2,p); line(c,10,6,14,6); line(c,11,18,13,18); break;
            case "bluetooth": line(c,8,7,17,16,12,21,12,3,17,8,8,17); break;
            default: c.drawCircle(12,12,8.5f,p); line(c,12,11,12,16.5f); c.drawPoint(12,7.5f,p);
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
}
