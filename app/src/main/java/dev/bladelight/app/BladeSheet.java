package dev.bladelight.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Shared, scrollable modal surface for choices, confirmations and help. */
public final class BladeSheet extends Dialog {
    private final Activity activity;
    private final LinearLayout content;
    private final int ink, muted, rowColor, sheetColor;
    private final boolean dark;
    public BladeSheet(Activity activity, boolean dark, String icon, String title, String subtitle) {
        super(activity); this.activity=activity; this.dark=dark;
        ink=dark?0xFFF4F6FA:0xFF171A20; muted=dark?0xFFADB4C0:0xFF636A77;
        sheetColor=dark?0xFF171A20:0xFFF8F9FB; rowColor=dark?0xFF232730:Color.WHITE;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(24),dp(22),dp(20));
        LinearLayout header=new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView symbol=icon(icon,ink); symbol.setPadding(dp(10),dp(10),dp(10),dp(10)); symbol.setBackground(shape(rowColor,14));
        header.addView(symbol,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView heading=text(title,22,ink,true); heading.setAccessibilityHeading(true); heading.setPadding(dp(14),0,dp(8),0);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton close=new ImageButton(activity); close.setImageDrawable(new BladeIcon("close",muted));
        close.setScaleType(ImageView.ScaleType.FIT_CENTER);
        close.setPadding(dp(14),dp(14),dp(14),dp(14)); close.setBackground(ripple(Color.TRANSPARENT)); close.setContentDescription(activity.getString(R.string.close));
        close.setOnClickListener(v->dismiss()); header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48))); content.addView(header);
        if(!subtitle.isEmpty()) { TextView sub=text(subtitle,14,muted,false); sub.setPadding(0,dp(18),0,dp(6)); content.addView(sub); }
        space(18);
        ScrollView scroll=new ScrollView(activity) {
            @Override protected void onMeasure(int w,int h){
                int max=(int)(activity.getWindowManager().getCurrentWindowMetrics().getBounds().height()*.80f);
                super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false); scroll.setBackground(shape(sheetColor,30)); scroll.setClipToOutline(true);
        scroll.addView(content); setContentView(scroll); setCanceledOnTouchOutside(true);
    }
    public void addChoice(String label,String detail,String icon,boolean selected,Runnable action) {
        LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(74));
        row.setPadding(dp(16),dp(16),dp(16),dp(16)); row.setBackground(ripple(rowColor));
        row.addView(icon(icon,selected?ink:muted),new LinearLayout.LayoutParams(dp(24),dp(24)));
        LinearLayout labels=new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(14),0,dp(12),0);
        labels.addView(text(label,16,ink,true));
        if(!detail.isEmpty()) { TextView sub=text(detail,12,muted,false); sub.setPadding(0,dp(5),0,0);labels.addView(sub); }
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        ImageView check=icon(selected?"check":"next",selected?ink:muted); row.addView(check,new LinearLayout.LayoutParams(dp(20),dp(20)));
        row.setFocusable(true); row.setContentDescription(label+(detail.isEmpty()?"":", "+detail));
        labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);info.setSelected(selected);
            }
        });
        row.setOnClickListener(v->{dismiss();action.run();}); content.addView(row,new LinearLayout.LayoutParams(-1,-2)); space(10);
    }
    public void addMessage(String message) {
        TextView body=text(message,15,muted,false); body.setLineSpacing(dp(5),1); body.setPadding(0,0,0,dp(18));content.addView(body);
    }
    public void addButton(String label,boolean primary,Runnable action) {
        Button button=new Button(activity); button.setText(label); button.setAllCaps(false);button.setTextSize(16);
        button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        button.setTextColor(primary?(dark?0xFF192231:Color.WHITE):ink);
        button.setBackground(ripple(primary?(dark?0xFFE4EBF5:0xFF202B3D):rowColor));button.setStateListAnimator(null);
        button.setMinHeight(dp(54));button.setPadding(dp(16),dp(12),dp(16),dp(12));
        button.setOnClickListener(v->{dismiss();if(action!=null)action.run();});
        content.addView(button,new LinearLayout.LayoutParams(-1,-2));space(10);
    }
    @Override public void show(){
        super.show();Window window=getWindow(); if(window==null)return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs=window.getAttributes();attrs.width=Math.min(dp(560),activity.getWindowManager().getCurrentWindowMetrics().getBounds().width()-dp(24));
        attrs.height=WindowManager.LayoutParams.WRAP_CONTENT;attrs.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;attrs.y=dp(12);attrs.dimAmount=.56f;window.setAttributes(attrs);
    }
    private ImageView icon(String name,int color){ImageView view=new ImageView(activity);view.setImageDrawable(new BladeIcon(name,color));view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private TextView text(String value,int size,int color,boolean medium){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;}
    private void space(int h){content.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Drawable ripple(int color){return new RippleDrawable(ColorStateList.valueOf(dark?0x24FFFFFF:0x14000000),shape(color,18),shape(Color.WHITE,18));}
}
