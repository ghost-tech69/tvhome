package com.tvhome.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
  static final int COLS = 4; // dock slots = columns
  static final String[] DOCK = {"com.google.android.youtube.tv", "com.android.chrome", "com.android.vending", "com.android.tv.settings"};
  PackageManager pm;
  List<ResolveInfo> items = new ArrayList<>();
  Adapter ad = new Adapter();
  Map<String, Integer> colors = new HashMap<>();
  FrameLayout root;
  RecyclerView grid;
  View glass, glow;
  GradientDrawable glowBg;
  TextView dockName, pWifi;
  View focusedCard;
  Typeface f100, f300, f400;
  int barId, lastPos = 0;

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  Typeface font(int w) {
    try {
      Typeface t = new Typeface.Builder(getAssets(), "Outfit.ttf").setFontVariationSettings("'wght' " + w).build();
      return t != null ? t : Typeface.DEFAULT;
    } catch (Exception e) { return Typeface.DEFAULT; }
  }

  TextView text(String s, float sp, int color) {
    TextView t = new TextView(this);
    t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(f400);
    return t;
  }

  TextView pill(String s) {
    final TextView t = text(s, 16, Color.WHITE);
    t.setPadding(dp(18), dp(8), dp(18), dp(8));
    t.setFocusable(true);
    t.setId(View.generateViewId());
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(40)); bg.setColor(0x33FFFFFF);
    t.setBackground(bg);
    t.setOnFocusChangeListener((v, f) -> {
      bg.setColor(f ? 0xEEFFFFFF : 0x33FFFFFF); t.setTextColor(f ? Color.BLACK : Color.WHITE);
      if (f && glass != null) glass.animate().alpha(0f).setDuration(150).start();
    });
    t.setOnKeyListener((v, k, e) -> {
      if (k == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN) { focusTile(lastPos); return true; }
      return false;
    });
    return t;
  }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    pm = getPackageManager();
    f100 = font(100); f300 = font(300); f400 = font(400);
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    root.setClipChildren(false);

    glow = new View(this);
    glowBg = new GradientDrawable();
    glowBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    glowBg.setGradientRadius(dp(380));
    glowBg.setGradientCenter(0.5f, 0.45f);
    glowBg.setColors(new int[]{0, 0});
    glow.setBackground(glowBg);
    FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(900), dp(420), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    glp.topMargin = dp(120);
    root.addView(glow, glp);

    grid = new RecyclerView(this);
    grid.setId(View.generateViewId());
    grid.setLayoutManager(new GridLayoutManager(this, COLS));
    grid.setAdapter(ad);
    grid.addItemDecoration(new Band());
    grid.setClipToPadding(false); grid.setClipChildren(false);
    grid.setPadding(0, dp(14), 0, dp(60));
    grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
        if (focusedCard != null && focusedCard.isAttachedToWindow()) moveGlass(focusedCard, false);
      }
    });
    FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(800), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    gp.topMargin = dp(222);
    root.addView(grid, gp);

    // one translucent glass highlight that springs from tile to tile
    glass = new View(this);
    GradientDrawable gd = new GradientDrawable();
    gd.setCornerRadius(dp(18)); gd.setColor(0x33FFFFFF); gd.setStroke(dp(1), 0x66FFFFFF);
    glass.setBackground(gd);
    glass.setAlpha(0f);
    root.addView(glass, new FrameLayout.LayoutParams(dp(176), dp(99), Gravity.TOP | Gravity.START));

    dockName = text("", 30, Color.WHITE);
    dockName.setTypeface(f300);
    FrameLayout.LayoutParams dp1 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
    dp1.setMargins(dp(86), dp(178), 0, 0);
    root.addView(dockName, dp1);

    LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.VERTICAL);
    int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    head.addView(text(h < 11 ? "Dobro jutro" : h < 18 ? "Dobar dan" : "Dobro veče", 20, 0xAAFFFFFF));
    TextClock clock = new TextClock(this);
    clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
    clock.setTextSize(64); clock.setTextColor(Color.WHITE); clock.setTypeface(f100);
    head.addView(clock);
    TextClock date = new TextClock(this);
    date.setFormat24Hour("EEEE, d. MMMM"); date.setFormat12Hour("EEEE, d. MMMM");
    date.setTextSize(18); date.setTextColor(0x99FFFFFF); date.setTypeface(f300);
    head.addView(date);
    FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    hp.setMargins(dp(60), dp(30), 0, 0);
    root.addView(head, hp);

    LinearLayout bar = new LinearLayout(this);
    final TextView pIn = pill("Inputs"), pSet = pill("Settings");
    pWifi = pill("Wi-Fi");
    barId = pWifi.getId();
    TextView[] ps = {pIn, pWifi, pSet};
    for (TextView p : ps) {
      p.setNextFocusUpId(p.getId());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = dp(12);
      bar.addView(p, lp);
    }
    pIn.setNextFocusLeftId(pIn.getId());
    pSet.setNextFocusRightId(pSet.getId());
    pIn.setOnClickListener(v -> showInputs(pIn));
    pWifi.setOnClickListener(v -> open(Settings.ACTION_WIFI_SETTINGS));
    pSet.setOnClickListener(v -> open(Settings.ACTION_SETTINGS));
    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
    bp.setMargins(0, dp(40), dp(60), 0);
    root.addView(bar, bp);
    setContentView(root);
  }

  @Override protected void onResume() {
    super.onResume();
    load(); ad.notifyDataSetChanged(); updateWifi();
    grid.postDelayed(() -> { if (!getWindow().getDecorView().hasFocus() || focusedCard == null || !focusedCard.isFocused()) focusTile(lastPos); }, 200);
  }
  @Override public void onBackPressed() { }

  void focusTile(final int pos) {
    if (pos >= items.size()) return;
    grid.scrollToPosition(pos);
    grid.post(() -> {
      RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
      if (vh != null) ((ViewGroup) vh.itemView).getChildAt(0).requestFocus();
    });
  }

  void open(String action) { try { startActivity(new Intent(action)); } catch (Exception e) { } }

  void updateWifi() {
    try {
      WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      String s = wm.getConnectionInfo().getSSID();
      if (s == null || s.contains("unknown")) s = "Wi-Fi"; else s = s.replace("\"", "");
      if (s.length() > 18) s = s.substring(0, 18) + "…";
      pWifi.setText(s);
    } catch (Exception e) { }
  }

  void moveGlass(View card, boolean anim) {
    int[] a = new int[2], r = new int[2];
    card.getLocationOnScreen(a); root.getLocationOnScreen(r);
    float cx = a[0] - r[0] + card.getWidth() * card.getScaleX() / 2f;
    float cy = a[1] - r[1] + card.getHeight() * card.getScaleY() / 2f;
    float tx = cx - glass.getWidth() / 2f, ty = cy - glass.getHeight() / 2f;
    if (anim) {
      glass.animate().alpha(1f).translationX(tx).translationY(ty).scaleX(1.15f).scaleY(1.15f)
          .setInterpolator(new OvershootInterpolator(1.3f)).setDuration(280).start();
    } else {
      glass.animate().cancel();
      glass.setTranslationX(tx); glass.setTranslationY(ty); glass.setScaleX(1.15f); glass.setScaleY(1.15f); glass.setAlpha(1f);
    }
  }

  int colorOf(ResolveInfo r) {
    String p = r.activityInfo.packageName;
    Integer c = colors.get(p);
    if (c != null) return c;
    Drawable d = r.activityInfo.loadBanner(pm);
    if (d == null) d = r.activityInfo.loadIcon(pm);
    Bitmap bm = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
    d.setBounds(0, 0, 16, 16); d.draw(new Canvas(bm));
    long R = 0, G = 0, B = 0, n = 0;
    for (int i = 0; i < 256; i++) {
      int px = bm.getPixel(i % 16, i / 16);
      if (Color.alpha(px) < 128) continue;
      R += Color.red(px); G += Color.green(px); B += Color.blue(px); n++;
    }
    int col = n == 0 ? 0xFF888888 : Color.rgb((int) (R / n), (int) (G / n), (int) (B / n));
    float[] hsv = new float[3];
    Color.colorToHSV(col, hsv);
    hsv[2] = Math.max(hsv[2], 0.85f);
    col = Color.HSVToColor(hsv);
    bm.recycle();
    colors.put(p, col);
    return col;
  }

  void showDock(ResolveInfo r) {
    if (r == null) { dockName.setText(""); glowBg.setColors(new int[]{0, 0}); glow.invalidate(); return; }
    dockName.setText(r.loadLabel(pm));
    int c = colorOf(r) & 0xFFFFFF;
    glowBg.setColors(new int[]{(0x66 << 24) | c, c});
    glow.invalidate();
  }

  void load() {
    Map<String, ResolveInfo> m = new LinkedHashMap<>();
    for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
      Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
      for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
        String p = r.activityInfo.packageName;
        if (!p.equals(getPackageName()) && !m.containsKey(p)) m.put(p, r);
      }
    }
    items.clear();
    for (String d : DOCK) { ResolveInfo r = m.remove(d); if (r != null) items.add(r); }
    List<ResolveInfo> rest = new ArrayList<>(m.values());
    Collections.sort(rest, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
    items.addAll(rest);
  }

  void launch(ResolveInfo r) {
    String p = r.activityInfo.packageName;
    Intent i = pm.getLeanbackLaunchIntentForPackage(p);
    if (i == null) i = pm.getLaunchIntentForPackage(p);
    if (i != null) startActivity(i);
  }

  void showInputs(View anchor) {
    TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
    final List<TvInputInfo> l = tm.getTvInputList();
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(8), dp(8), dp(8), dp(8));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(18)); bg.setColor(0xF01C1C1E); bg.setStroke(dp(1), 0x33FFFFFF);
    box.setBackground(bg);
    final PopupWindow pw = new PopupWindow(box, dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, true);
    pw.setBackgroundDrawable(new ColorDrawable(0));
    for (final TvInputInfo t : l) {
      final TextView it = text(String.valueOf(t.loadLabel(this)), 16, Color.WHITE);
      it.setPadding(dp(16), dp(10), dp(16), dp(10));
      it.setFocusable(true);
      final GradientDrawable ib = new GradientDrawable();
      ib.setCornerRadius(dp(12)); ib.setColor(0);
      it.setBackground(ib);
      it.setOnFocusChangeListener((v, f) -> { ib.setColor(f ? 0xEEFFFFFF : 0); it.setTextColor(f ? Color.BLACK : Color.WHITE); });
      it.setOnClickListener(v -> { pw.dismiss(); switchInput(t); });
      box.addView(it);
    }
    pw.showAsDropDown(anchor, 0, dp(8));
    if (box.getChildCount() > 0) box.getChildAt(0).requestFocus();
  }

  void switchInput(TvInputInfo t) {
    try {
      if (t.isPassthroughInput()) startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId())));
      else { Intent i = pm.getLeanbackLaunchIntentForPackage("com.android.tv"); if (i != null) startActivity(i); }
    } catch (Exception e) { }
  }

  class Band extends RecyclerView.ItemDecoration {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Band() { p.setColor(0xFF161618); }
    @Override public void onDraw(Canvas c, RecyclerView rv, RecyclerView.State s) {
      for (int i = 0; i < rv.getChildCount(); i++) {
        View v = rv.getChildAt(i);
        if (rv.getChildAdapterPosition(v) == 0) {
          c.drawRoundRect(new RectF(v.getLeft() - dp(6), v.getTop() - dp(8), v.getLeft() + COLS * dp(200) + dp(6), v.getBottom() + dp(4)), dp(24), dp(24), p);
        }
      }
    }
  }

  class H extends RecyclerView.ViewHolder {
    FrameLayout card; ImageView img; TextView label;
    H(View v, FrameLayout c, ImageView i, TextView l) { super(v); card = c; img = i; label = l; }
  }

  class Adapter extends RecyclerView.Adapter<H> {
    @Override public H onCreateViewHolder(ViewGroup p, int t) {
      LinearLayout cell = new LinearLayout(MainActivity.this);
      cell.setOrientation(LinearLayout.VERTICAL);
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      cell.setClipChildren(false);
      cell.setLayoutParams(new RecyclerView.LayoutParams(dp(200), dp(138)));
      FrameLayout card = new FrameLayout(MainActivity.this);
      card.setFocusable(true);
      card.setClipToOutline(true);
      ImageView img = new ImageView(MainActivity.this);
      card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      cell.addView(card, new LinearLayout.LayoutParams(dp(176), dp(99)));
      TextView label = text("", 14, Color.WHITE);
      label.setVisibility(View.INVISIBLE); label.setSingleLine(true);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = dp(10);
      cell.addView(label, lp);
      return new H(cell, card, img, label);
    }

    @Override public void onBindViewHolder(final H h, final int pos) {
      final ResolveInfo r = items.get(pos);
      Drawable d = r.activityInfo.loadBanner(pm);
      if (d != null) { h.img.setScaleType(ImageView.ScaleType.CENTER_CROP); h.img.setPadding(0, 0, 0, 0); }
      else { d = r.activityInfo.loadIcon(pm); h.img.setScaleType(ImageView.ScaleType.FIT_CENTER); h.img.setPadding(dp(24), dp(12), dp(24), dp(12)); }
      h.img.setImageDrawable(d);
      h.label.setText(r.loadLabel(pm));
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(14)); bg.setColor(0xFF2C2C2E);
      h.card.setBackground(bg);
      h.card.setNextFocusUpId(pos < COLS ? barId : View.NO_ID);
      h.card.setOnFocusChangeListener((v, f) -> {
        v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).setDuration(120).start();
        h.label.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
        if (f) {
          focusedCard = v;
          int ap = h.getAdapterPosition();
          if (ap >= 0) lastPos = ap;
          moveGlass(v, true);
          showDock(ap >= 0 && ap < COLS ? r : null);
        }
      });
      h.card.setOnClickListener(v -> launch(r));
    }

    @Override public int getItemCount() { return items.size(); }
  }
}
