package uz.bahodirbek.nfsstreetrush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public final class NightRushView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final int MENU = 0;
    private static final int PLAYING = 1;
    private static final int PAUSED = 2;
    private static final int GAME_OVER = 3;

    private final SurfaceHolder holder;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(20261023L);
    private final ArrayList<TrafficCar> traffic = new ArrayList<>();
    private volatile boolean running;
    private Thread loopThread;
    private volatile int state = MENU;

    private volatile boolean leftPressed;
    private volatile boolean rightPressed;
    private volatile boolean gasPressed;
    private volatile boolean brakePressed;
    private volatile boolean nitroPressed;

    private float density;
    private float speed;
    private float playerLane;
    private float nitro = 1f;
    private float spawnTimer;
    private float crashFlash;
    private float distance;
    private float score;
    private float elapsed;
    private int crashes;
    private int dodged;
    private int bestScore;
    private int frameWidth;
    private int frameHeight;

    private final RectF leftRect = new RectF();
    private final RectF rightRect = new RectF();
    private final RectF gasRect = new RectF();
    private final RectF brakeRect = new RectF();
    private final RectF nitroRect = new RectF();
    private final RectF pauseRect = new RectF();
    private final RectF startRect = new RectF();
    private final RectF resumeRect = new RectF();
    private final RectF restartRect = new RectF();

    private static final int CYAN = Color.rgb(0, 229, 255);
    private static final int PINK = Color.rgb(255, 48, 125);
    private static final int GOLD = Color.rgb(255, 206, 73);
    private static final int WHITE = Color.rgb(242, 247, 255);

    public NightRushView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        resumeLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        frameWidth = width;
        frameHeight = height;
        layoutControls(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        pauseLoop();
    }

    public synchronized void resumeLoop() {
        if (running) return;
        running = true;
        loopThread = new Thread(this, "StreetRush-GameLoop");
        loopThread.start();
    }

    public synchronized void pauseLoop() {
        running = false;
        Thread old = loopThread;
        loopThread = null;
        if (old != null && old != Thread.currentThread()) {
            try {
                old.join(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        clearControls();
    }

    public boolean handleBack() {
        if (state == PLAYING) {
            state = PAUSED;
            clearControls();
            return true;
        }
        if (state == PAUSED) {
            state = PLAYING;
            return true;
        }
        if (state == GAME_OVER) {
            state = MENU;
            return true;
        }
        return false;
    }

    private void clearControls() {
        leftPressed = false;
        rightPressed = false;
        gasPressed = false;
        brakePressed = false;
        nitroPressed = false;
    }

    @Override
    public void run() {
        long previous = System.nanoTime();
        while (running) {
            long start = System.nanoTime();
            float dt = Math.min(0.04f, (start - previous) / 1_000_000_000f);
            previous = start;
            if (dt < 0f) dt = 0f;

            updateGame(dt);

            Canvas canvas = null;
            try {
                if (holder.getSurface().isValid()) {
                    canvas = holder.lockCanvas();
                    if (canvas != null) drawFrame(canvas);
                }
            } catch (IllegalArgumentException ignored) {
                // Surface was recreated while the game loop was drawing.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (IllegalArgumentException ignored) {
                        // The OS can invalidate a surface between lock and post.
                    }
                }
            }

            long spent = (System.nanoTime() - start) / 1_000_000L;
            long sleepMs = 16L - spent;
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void startRace() {
        state = PLAYING;
        speed = 72f;
        playerLane = 0f;
        nitro = 1f;
        spawnTimer = 0.65f;
        crashFlash = 0f;
        distance = 0f;
        score = 0f;
        elapsed = 0f;
        crashes = 0;
        dodged = 0;
        traffic.clear();
        clearControls();
    }

    private void updateGame(float dt) {
        if (crashFlash > 0f) crashFlash = Math.max(0f, crashFlash - dt);
        if (state != PLAYING) return;

        elapsed += dt;
        float steering = (leftPressed ? -1f : 0f) + (rightPressed ? 1f : 0f);
        playerLane += steering * dt * (0.90f + speed / 330f);
        if (steering == 0f) playerLane *= Math.max(0f, 1f - dt * 0.055f);
        playerLane = Math.max(-1.08f, Math.min(1.08f, playerLane));

        float maxSpeed = 285f;
        if (nitroPressed && nitro > 0.01f && !brakePressed) {
            maxSpeed = 365f;
            nitro = Math.max(0f, nitro - dt * 0.23f);
        } else {
            nitro = Math.min(1f, nitro + dt * 0.055f);
        }

        if (gasPressed) speed += dt * 78f;
        else speed -= dt * 14f;
        if (brakePressed) speed -= dt * 145f;
        if (nitroPressed && nitro > 0f && !brakePressed) speed += dt * 48f;
        speed = Math.max(0f, Math.min(maxSpeed, speed));

        if (Math.abs(playerLane) > 0.89f) {
            speed = Math.max(35f, speed - dt * 62f);
        }

        distance += speed * dt / 3.6f;
        score += speed * dt * 0.72f;
        spawnTimer -= dt;
        if (spawnTimer <= 0f && speed > 25f) {
            spawnTraffic();
            spawnTimer = 0.85f + random.nextFloat() * 0.55f;
        }

        Iterator<TrafficCar> iterator = traffic.iterator();
        while (iterator.hasNext()) {
            TrafficCar car = iterator.next();
            car.depth += dt * (0.045f + speed / 690f);
            if (!car.counted && car.depth > 0.91f) {
                car.counted = true;
                if (Math.abs(car.lane - playerLane) > 0.29f) {
                    dodged++;
                    score += 90f;
                }
            }
            if (car.depth > 0.86f && car.depth < 1.02f
                    && Math.abs(car.lane - playerLane) < 0.20f && crashFlash <= 0f) {
                crashes++;
                crashFlash = 1.05f;
                speed = Math.max(42f, speed * 0.42f);
                playerLane += (playerLane < car.lane ? -0.22f : 0.22f);
                iterator.remove();
                if (crashes >= 3) {
                    bestScore = Math.max(bestScore, (int) score);
                    state = GAME_OVER;
                    clearControls();
                }
                continue;
            }
            if (car.depth > 1.10f) iterator.remove();
        }
    }

    private void spawnTraffic() {
        float[] lanes = new float[] {-0.66f, 0f, 0.66f};
        float lane = lanes[random.nextInt(lanes.length)];
        for (TrafficCar car : traffic) {
            if (car.depth < 0.30f && Math.abs(car.lane - lane) < 0.3f) {
                lane = lanes[(random.nextInt(lanes.length))];
            }
        }
        int[] colors = new int[] {
                Color.rgb(250, 55, 81), Color.rgb(30, 190, 255),
                Color.rgb(255, 190, 65), Color.rgb(217, 225, 243),
                Color.rgb(132, 103, 255), Color.rgb(60, 225, 157)
        };
        traffic.add(new TrafficCar(lane, colors[random.nextInt(colors.length)], random.nextInt(3)));
    }

    private void drawFrame(Canvas c) {
        float w = c.getWidth();
        float h = c.getHeight();
        if (w <= 0f || h <= 0f) return;
        if ((int) w != frameWidth || (int) h != frameHeight) {
            frameWidth = (int) w;
            frameHeight = (int) h;
            layoutControls(frameWidth, frameHeight);
        }
        float horizon = h * 0.335f;

        drawSky(c, w, h, horizon);
        drawCity(c, w, horizon);
        drawRoad(c, w, h, horizon);

        for (TrafficCar car : traffic) drawTrafficCar(c, w, h, horizon, car);
        drawPlayerCar(c, w, h, horizon);

        if (state == PLAYING) drawHud(c, w, h);
        if (state == MENU) drawMenu(c, w, h);
        else if (state == PAUSED) drawPause(c, w, h);
        else if (state == GAME_OVER) drawGameOver(c, w, h);

        if (crashFlash > 0f && state == PLAYING) {
            p.setColor(Color.argb((int) (45f * crashFlash), 255, 35, 75));
            c.drawRect(0f, 0f, w, h, p);
            text(c, "IMPACT!", w * 0.5f, h * 0.43f, h * 0.075f, PINK, Paint.Align.CENTER);
        }
    }

    private void drawSky(Canvas c, float w, float h, float horizon) {
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, horizon,
                new int[] {Color.rgb(4, 8, 28), Color.rgb(22, 18, 55), Color.rgb(80, 28, 78)},
                null, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon + 2, p);
        p.setShader(null);

        // Stars are deterministic and cheap to render on entry-level hardware.
        for (int i = 0; i < 42; i++) {
            float x = ((i * 173) % 997) / 997f * w;
            float y = ((i * 67 + 19) % 263) / 263f * horizon * 0.78f;
            p.setColor(Color.argb(95 + (i % 4) * 35, 206, 225, 255));
            c.drawCircle(x, y, (i % 5 == 0 ? 1.45f : 0.8f) * density, p);
        }

        float moonX = w * 0.78f;
        float moonY = horizon * 0.32f;
        p.setColor(Color.argb(25, 80, 210, 255));
        c.drawCircle(moonX, moonY, horizon * 0.23f, p);
        p.setColor(Color.argb(80, 80, 210, 255));
        c.drawCircle(moonX, moonY, horizon * 0.145f, p);
        p.setColor(Color.rgb(222, 239, 255));
        c.drawCircle(moonX, moonY, horizon * 0.092f, p);

        p.setColor(Color.rgb(37, 27, 62));
        c.drawRect(0, horizon * 0.78f, w, horizon + 2f, p);
        for (int i = 0; i < 16; i++) {
            float bx = i * w / 15f - w * 0.018f;
            float bw = w * (0.025f + ((i * 19) % 5) * 0.004f);
            float bh = horizon * (0.16f + ((i * 31) % 6) * 0.025f);
            p.setColor(i % 3 == 0 ? Color.rgb(41, 33, 75) : Color.rgb(25, 28, 54));
            c.drawRect(bx, horizon - bh, bx + bw, horizon, p);
            p.setColor(i % 2 == 0 ? Color.argb(140, 0, 225, 255) : Color.argb(140, 255, 42, 128));
            float winW = Math.max(1f, bw * 0.11f);
            for (float wx = bx + bw * 0.2f; wx < bx + bw * 0.9f; wx += winW * 2.7f) {
                for (float wy = horizon - bh + 5f * density; wy < horizon - 2f * density; wy += 9f * density) {
                    if (((int) wx + (int) wy + i) % 3 != 0) c.drawRect(wx, wy, wx + winW, wy + 3f * density, p);
                }
            }
        }
        p.setColor(Color.rgb(7, 21, 27));
        c.drawRect(0, horizon, w, h, p);
    }

    private void drawCity(Canvas c, float w, float horizon) {
        // Neon signboards on the skyline give the road a street-racing identity.
        p.setColor(Color.argb(165, 0, 4, 18));
        c.drawRoundRect(new RectF(w * 0.035f, horizon * 0.57f, w * 0.21f, horizon * 0.80f),
                4f * density, 4f * density, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f * density);
        p.setColor(Color.argb(185, 0, 229, 255));
        c.drawRoundRect(new RectF(w * 0.035f, horizon * 0.57f, w * 0.21f, horizon * 0.80f),
                4f * density, 4f * density, p);
        p.setStyle(Paint.Style.FILL);
        text(c, "NIGHT", w * 0.122f, horizon * 0.66f, horizon * 0.105f, CYAN, Paint.Align.CENTER);
        text(c, "CITY", w * 0.122f, horizon * 0.75f, horizon * 0.075f, WHITE, Paint.Align.CENTER);

        p.setColor(Color.argb(150, 0, 2, 12));
        c.drawRoundRect(new RectF(w * 0.79f, horizon * 0.60f, w * 0.96f, horizon * 0.81f),
                4f * density, 4f * density, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f * density);
        p.setColor(Color.argb(190, 255, 48, 125));
        c.drawRoundRect(new RectF(w * 0.79f, horizon * 0.60f, w * 0.96f, horizon * 0.81f),
                4f * density, 4f * density, p);
        p.setStyle(Paint.Style.FILL);
        text(c, "NITRO", w * 0.875f, horizon * 0.69f, horizon * 0.095f, PINK, Paint.Align.CENTER);
        text(c, "RACING", w * 0.875f, horizon * 0.76f, horizon * 0.06f, WHITE, Paint.Align.CENTER);
    }

    private void drawRoad(Canvas c, float w, float h, float horizon) {
        int steps = 72;
        float prevZ = 0f;
        for (int i = 0; i < steps; i++) {
            float z1 = i / (float) steps;
            float z2 = (i + 1) / (float) steps;
            float y1 = horizon + z1 * z1 * (h - horizon);
            float y2 = horizon + z2 * z2 * (h - horizon);
            float cx1 = roadCenter(w, z1);
            float cx2 = roadCenter(w, z2);
            float half1 = roadHalf(w, z1);
            float half2 = roadHalf(w, z2);

            p.setColor((i % 2 == 0) ? Color.rgb(10, 30, 34) : Color.rgb(8, 26, 31));
            poly(c, p, 0, y1, w, y1, w, y2, 0, y2);

            p.setColor((i % 2 == 0) ? Color.rgb(47, 48, 63) : Color.rgb(42, 44, 58));
            poly(c, p, cx1 - half1, y1, cx1 + half1, y1, cx2 + half2, y2, cx2 - half2, y2);

            p.setColor(Color.rgb(242, 244, 247));
            float edge1 = Math.max(1f, w * (0.0016f + z1 * 0.0045f));
            float edge2 = Math.max(1f, w * (0.0016f + z2 * 0.0045f));
            poly(c, p,
                    cx1 - half1, y1, cx1 - half1 + edge1, y1,
                    cx2 - half2 + edge2, y2, cx2 - half2, y2);
            poly(c, p,
                    cx1 + half1 - edge1, y1, cx1 + half1, y1,
                    cx2 + half2, y2, cx2 + half2 - edge2, y2);

            int phase = (i * 3 + (int) (distance * 0.08f)) % 17;
            if (phase < 6) {
                p.setColor(Color.argb(225, 225, 234, 246));
                for (int laneMark = -1; laneMark <= 1; laneMark += 2) {
                    float mx1 = cx1 + laneMark * half1 * 0.34f;
                    float mx2 = cx2 + laneMark * half2 * 0.34f;
                    float mw1 = Math.max(1f, w * (0.0012f + z1 * 0.0026f));
                    float mw2 = Math.max(1f, w * (0.0012f + z2 * 0.0026f));
                    poly(c, p, mx1 - mw1, y1, mx1 + mw1, y1, mx2 + mw2, y2, mx2 - mw2, y2);
                }
            }
            prevZ = z2;
        }

        // Guard rails and perspective streetlights.
        for (int i = 0; i < 9; i++) {
            float z = ((i * 0.135f + (distance * 0.0026f)) % 0.94f) + 0.04f;
            float y = horizon + z * z * (h - horizon);
            float cx = roadCenter(w, z);
            float half = roadHalf(w, z);
            float poleX1 = cx - half - w * (0.028f + z * 0.018f);
            float poleX2 = cx + half + w * (0.028f + z * 0.018f);
            float poleHeight = h * (0.018f + z * 0.095f);
            float stroke = density * (0.6f + z * 2.2f);

            p.setStrokeWidth(stroke);
            p.setColor(Color.argb(175, 106, 122, 154));
            c.drawLine(cx - half, y, cx - half - w * 0.018f * z, y - h * 0.011f * z, p);
            c.drawLine(cx + half, y, cx + half + w * 0.018f * z, y - h * 0.011f * z, p);
            p.setColor(Color.rgb(80, 96, 121));
            c.drawLine(poleX1, y, poleX1, y - poleHeight, p);
            c.drawLine(poleX2, y, poleX2, y - poleHeight, p);
            p.setColor(Color.argb(100, 0, 229, 255));
            c.drawCircle(poleX1, y - poleHeight, stroke * 2.7f, p);
            c.drawCircle(poleX2, y - poleHeight, stroke * 2.7f, p);
            p.setColor(CYAN);
            c.drawCircle(poleX1, y - poleHeight, stroke * 1.1f, p);
            p.setColor(PINK);
            c.drawCircle(poleX2, y - poleHeight, stroke * 1.1f, p);
        }
    }

    private float roadCenter(float w, float z) {
        double a = distance * 0.0044 + (1f - z) * 2.3;
        double b = distance * 0.0017 - (1f - z) * 1.2;
        return w * 0.5f + (float) (Math.sin(a) * w * 0.075 * z + Math.sin(b) * w * 0.035 * z);
    }

    private float roadHalf(float w, float z) {
        return w * (0.024f + 0.395f * z);
    }

    private float projectY(float horizon, float h, float z) {
        return horizon + z * z * (h - horizon);
    }

    private void drawTrafficCar(Canvas c, float w, float h, float horizon, TrafficCar car) {
        float z = Math.max(0.025f, Math.min(1.02f, car.depth));
        float cx = roadCenter(w, z) + car.lane * roadHalf(w, z) * 0.67f;
        float cy = projectY(horizon, h, z);
        float cw = w * (0.013f + z * 0.072f);
        float ch = h * (0.010f + z * 0.105f);
        drawCar(c, cx, cy - ch * 0.05f, cw, ch, car.color, false, car.kind);
    }

    private void drawPlayerCar(Canvas c, float w, float h, float horizon) {
        float z = 0.96f;
        float cx = roadCenter(w, z) + playerLane * roadHalf(w, z) * 0.68f;
        float cy = h * 0.79f;
        float cw = w * 0.105f;
        float ch = h * 0.195f;

        if (nitroPressed && nitro > 0.01f && state == PLAYING) {
            p.setColor(Color.argb(85, 0, 229, 255));
            c.drawOval(new RectF(cx - cw * 0.22f, cy + ch * 0.23f, cx + cw * 0.22f, cy + ch * 0.69f), p);
            poly(c, p, cx - cw * 0.18f, cy + ch * 0.30f,
                    cx - cw * 0.06f, cy + ch * 0.30f,
                    cx - cw * 0.13f, cy + ch * 0.74f);
            p.setColor(GOLD);
            poly(c, p, cx + cw * 0.04f, cy + ch * 0.30f,
                    cx + cw * 0.18f, cy + ch * 0.30f,
                    cx + cw * 0.13f, cy + ch * 0.73f);
        }
        drawCar(c, cx, cy, cw, ch, Color.rgb(0, 154, 210), true, 0);
    }

    private void drawCar(Canvas c, float cx, float cy, float cw, float ch, int body, boolean player, int kind) {
        p.setColor(Color.argb(95, 0, 0, 0));
        c.drawOval(new RectF(cx - cw * 0.58f, cy + ch * 0.29f,
                cx + cw * 0.58f, cy + ch * 0.58f), p);

        // Tires.
        p.setColor(Color.rgb(8, 9, 14));
        c.drawRoundRect(new RectF(cx - cw * 0.49f, cy - ch * 0.12f, cx - cw * 0.31f, cy + ch * 0.31f),
                cw * 0.035f, cw * 0.035f, p);
        c.drawRoundRect(new RectF(cx + cw * 0.31f, cy - ch * 0.12f, cx + cw * 0.49f, cy + ch * 0.31f),
                cw * 0.035f, cw * 0.035f, p);

        Path shell = new Path();
        shell.moveTo(cx - cw * 0.30f, cy - ch * 0.43f);
        shell.lineTo(cx + cw * 0.30f, cy - ch * 0.43f);
        shell.lineTo(cx + cw * 0.43f, cy - ch * 0.10f);
        shell.lineTo(cx + cw * 0.48f, cy + ch * 0.31f);
        shell.lineTo(cx + cw * 0.35f, cy + ch * 0.45f);
        shell.lineTo(cx - cw * 0.35f, cy + ch * 0.45f);
        shell.lineTo(cx - cw * 0.48f, cy + ch * 0.31f);
        shell.lineTo(cx - cw * 0.43f, cy - ch * 0.10f);
        shell.close();
        p.setColor(body);
        c.drawPath(shell, p);

        p.setColor(adjustColor(body, 1.25f));
        Path hood = new Path();
        hood.moveTo(cx - cw * 0.26f, cy - ch * 0.35f);
        hood.lineTo(cx + cw * 0.26f, cy - ch * 0.35f);
        hood.lineTo(cx + cw * 0.34f, cy - ch * 0.06f);
        hood.lineTo(cx - cw * 0.34f, cy - ch * 0.06f);
        hood.close();
        c.drawPath(hood, p);

        p.setColor(Color.rgb(13, 25, 42));
        Path rearGlass = new Path();
        rearGlass.moveTo(cx - cw * 0.25f, cy - ch * 0.31f);
        rearGlass.lineTo(cx + cw * 0.25f, cy - ch * 0.31f);
        rearGlass.lineTo(cx + cw * 0.30f, cy - ch * 0.13f);
        rearGlass.lineTo(cx - cw * 0.30f, cy - ch * 0.13f);
        rearGlass.close();
        c.drawPath(rearGlass, p);

        p.setColor(Color.argb(220, 135, 224, 255));
        c.drawRect(cx - cw * 0.37f, cy + ch * 0.13f, cx - cw * 0.23f, cy + ch * 0.19f, p);
        c.drawRect(cx + cw * 0.23f, cy + ch * 0.13f, cx + cw * 0.37f, cy + ch * 0.19f, p);
        p.setColor(Color.rgb(255, 28, 66));
        c.drawRoundRect(new RectF(cx - cw * 0.39f, cy + ch * 0.29f, cx - cw * 0.12f, cy + ch * 0.37f),
                ch * 0.018f, ch * 0.018f, p);
        c.drawRoundRect(new RectF(cx + cw * 0.12f, cy + ch * 0.29f, cx + cw * 0.39f, cy + ch * 0.37f),
                ch * 0.018f, ch * 0.018f, p);

        p.setColor(Color.argb(130, 255, 255, 255));
        c.drawRect(cx - cw * 0.03f, cy - ch * 0.37f, cx + cw * 0.03f, cy + ch * 0.28f, p);
        if (player) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, density * 1.4f));
            p.setColor(CYAN);
            c.drawPath(shell, p);
            p.setStyle(Paint.Style.FILL);
        } else if (kind == 1) {
            p.setColor(Color.argb(210, GOLD));
            c.drawRect(cx - cw * 0.16f, cy + ch * 0.43f, cx + cw * 0.16f, cy + ch * 0.48f, p);
        }
    }

    private int adjustColor(int color, float multiplier) {
        int r = Math.min(255, (int) (Color.red(color) * multiplier));
        int g = Math.min(255, (int) (Color.green(color) * multiplier));
        int b = Math.min(255, (int) (Color.blue(color) * multiplier));
        return Color.rgb(r, g, b);
    }

    private void drawHud(Canvas c, float w, float h) {
        float margin = 18f * density;
        panel(c, new RectF(margin, margin, margin + 188f * density, margin + 68f * density),
                Color.argb(155, 4, 8, 23), CYAN);
        text(c, String.format(java.util.Locale.US, "%03d", (int) speed),
                margin + 12f * density, margin + 38f * density, 33f * density, WHITE, Paint.Align.LEFT);
        text(c, "KM/H", margin + 83f * density, margin + 35f * density,
                10f * density, CYAN, Paint.Align.LEFT);
        text(c, "SCORE  " + (int) score, margin + 12f * density, margin + 55f * density,
                11f * density, Color.rgb(206, 216, 240), Paint.Align.LEFT);

        float barX = w * 0.43f;
        float barW = w * 0.17f;
        text(c, "NITRO", barX, margin + 17f * density, 11f * density, CYAN, Paint.Align.LEFT);
        p.setColor(Color.argb(160, 9, 15, 31));
        c.drawRoundRect(new RectF(barX, margin + 23f * density, barX + barW, margin + 34f * density),
                5f * density, 5f * density, p);
        p.setColor(nitro > 0.25f ? CYAN : GOLD);
        c.drawRoundRect(new RectF(barX, margin + 23f * density, barX + barW * nitro,
                margin + 34f * density), 5f * density, 5f * density, p);
        text(c, String.format(java.util.Locale.US, "%.2f KM", distance / 1000f),
                barX, margin + 54f * density, 11f * density, WHITE, Paint.Align.LEFT);

        panel(c, pauseRect, Color.argb(160, 4, 8, 23), PINK);
        p.setColor(WHITE);
        c.drawRoundRect(new RectF(pauseRect.centerX() - 7f * density,
                pauseRect.centerY() - 10f * density,
                pauseRect.centerX() - 2f * density,
                pauseRect.centerY() + 10f * density), 2f, 2f, p);
        c.drawRoundRect(new RectF(pauseRect.centerX() + 2f * density,
                pauseRect.centerY() - 10f * density,
                pauseRect.centerX() + 7f * density,
                pauseRect.centerY() + 10f * density), 2f, 2f, p);

        text(c, "TRAFFIC PASSED: " + dodged, w * 0.5f, h * 0.115f,
                12f * density, Color.argb(210, 229, 239, 255), Paint.Align.CENTER);
        drawControls(c);
    }

    private void drawControls(Canvas c) {
        button(c, leftRect, "◀", leftPressed, CYAN);
        button(c, rightRect, "▶", rightPressed, CYAN);
        button(c, gasRect, "GO", gasPressed, CYAN);
        button(c, brakeRect, "BRAKE", brakePressed, PINK);
        button(c, nitroRect, "N₂O", nitroPressed, GOLD);
    }

    private void drawMenu(Canvas c, float w, float h) {
        p.setColor(Color.argb(142, 2, 4, 17));
        c.drawRect(0, 0, w, h, p);
        text(c, "NFS", w * 0.5f, h * 0.24f, h * 0.105f, CYAN, Paint.Align.CENTER);
        text(c, "STREET RUSH", w * 0.5f, h * 0.35f, h * 0.112f, WHITE, Paint.Align.CENTER);
        text(c, "NIGHT CITY  //  ARCADE RACING", w * 0.5f, h * 0.415f,
                h * 0.031f, PINK, Paint.Align.CENTER);
        panel(c, startRect, Color.argb(235, 0, 35, 55), CYAN);
        text(c, "START RACE", startRect.centerX(), startRect.centerY() + h * 0.012f,
                h * 0.045f, WHITE, Paint.Align.CENTER);
        text(c, "TOUCH TO DRIVE  •  DODGE TRAFFIC  •  USE NITRO",
                w * 0.5f, h * 0.73f, h * 0.026f, Color.rgb(198, 209, 236), Paint.Align.CENTER);
        text(c, "FAN-MADE ARCADE RACER", w * 0.5f, h * 0.785f,
                h * 0.022f, Color.rgb(117, 140, 181), Paint.Align.CENTER);
    }

    private void drawPause(Canvas c, float w, float h) {
        p.setColor(Color.argb(175, 2, 4, 17));
        c.drawRect(0, 0, w, h, p);
        text(c, "PAUSED", w * 0.5f, h * 0.28f, h * 0.09f, WHITE, Paint.Align.CENTER);
        panel(c, resumeRect, Color.argb(235, 0, 35, 55), CYAN);
        text(c, "RESUME", resumeRect.centerX(), resumeRect.centerY() + h * 0.012f,
                h * 0.040f, WHITE, Paint.Align.CENTER);
        panel(c, restartRect, Color.argb(235, 47, 14, 40), PINK);
        text(c, "RESTART", restartRect.centerX(), restartRect.centerY() + h * 0.012f,
                h * 0.040f, WHITE, Paint.Align.CENTER);
    }

    private void drawGameOver(Canvas c, float w, float h) {
        p.setColor(Color.argb(182, 2, 4, 17));
        c.drawRect(0, 0, w, h, p);
        text(c, "RACE OVER", w * 0.5f, h * 0.27f, h * 0.09f, PINK, Paint.Align.CENTER);
        text(c, "SCORE  " + (int) score, w * 0.5f, h * 0.39f, h * 0.055f, WHITE, Paint.Align.CENTER);
        text(c, "DISTANCE  " + String.format(java.util.Locale.US, "%.2f KM", distance / 1000f),
                w * 0.5f, h * 0.46f, h * 0.032f, CYAN, Paint.Align.CENTER);
        text(c, "TRAFFIC PASSED  " + dodged + "   •   CRASHES  " + crashes,
                w * 0.5f, h * 0.52f, h * 0.029f, Color.rgb(200, 211, 235), Paint.Align.CENTER);
        panel(c, restartRect, Color.argb(240, 0, 35, 55), CYAN);
        text(c, "RACE AGAIN", restartRect.centerX(), restartRect.centerY() + h * 0.012f,
                h * 0.041f, WHITE, Paint.Align.CENTER);
        text(c, "BEST  " + bestScore, w * 0.5f, h * 0.76f, h * 0.028f, GOLD, Paint.Align.CENTER);
    }

    private void layoutControls(int w, int h) {
        float button = Math.max(58f * density, Math.min(92f * density, h * 0.145f));
        float edge = 24f * density;
        float bottom = h - 25f * density;
        leftRect.set(edge, bottom - button, edge + button, bottom);
        rightRect.set(edge + button + 13f * density, bottom - button,
                edge + button * 2f + 13f * density, bottom);
        gasRect.set(w - edge - button, bottom - button,
                w - edge, bottom);
        brakeRect.set(w - edge - button * 2f - 13f * density, bottom - button,
                w - edge - button - 13f * density, bottom);
        nitroRect.set(w - edge - button * 1.50f - 7f * density,
                bottom - button * 2f - 20f * density,
                w - edge - button * 0.50f - 7f * density,
                bottom - button - 20f * density);
        float top = 18f * density;
        pauseRect.set(w - edge - 50f * density, top,
                w - edge, top + 45f * density);
        startRect.set(w * 0.5f - 112f * density, h * 0.51f,
                w * 0.5f + 112f * density, h * 0.62f);
        resumeRect.set(w * 0.5f - 108f * density, h * 0.44f,
                w * 0.5f + 108f * density, h * 0.54f);
        restartRect.set(w * 0.5f - 108f * density, h * 0.57f,
                w * 0.5f + 108f * density, h * 0.67f);
    }

    private void button(Canvas c, RectF r, String label, boolean active, int accent) {
        p.setColor(active ? Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent))
                : Color.argb(92, 3, 8, 23));
        c.drawRoundRect(r, 17f * density, 17f * density, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.7f * density);
        p.setColor(active ? WHITE : Color.argb(155, Color.red(accent), Color.green(accent), Color.blue(accent)));
        c.drawRoundRect(r, 17f * density, 17f * density, p);
        p.setStyle(Paint.Style.FILL);
        text(c, label, r.centerX(), r.centerY() + 5f * density,
                label.length() > 3 ? 12f * density : 18f * density, WHITE, Paint.Align.CENTER);
    }

    private void panel(Canvas c, RectF rect, int fill, int outline) {
        p.setColor(fill);
        c.drawRoundRect(rect, 12f * density, 12f * density, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * density);
        p.setColor(outline);
        c.drawRoundRect(rect, 12f * density, 12f * density, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color, Paint.Align align) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(Math.max(7f * density, size));
        p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setShadowLayer(2f * density, 0f, 1f * density, Color.argb(110, 0, 0, 0));
        c.drawText(value, x, y, p);
        p.clearShadowLayer();
    }

    private void poly(Canvas c, Paint paint, float... points) {
        if (points.length < 6 || points.length % 2 != 0) return;
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.close();
        c.drawPath(path, paint);
    }

    private void recalcControls(MotionEvent event) {
        boolean l = false, r = false, g = false, b = false, n = false;
        int action = event.getActionMasked();
        int skip = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                ? event.getActionIndex() : -1;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skip) continue;
            float x = event.getX(i);
            float y = event.getY(i);
            l |= leftRect.contains(x, y);
            r |= rightRect.contains(x, y);
            g |= gasRect.contains(x, y);
            b |= brakeRect.contains(x, y);
            n |= nitroRect.contains(x, y);
        }
        leftPressed = l;
        rightPressed = r;
        gasPressed = g;
        brakePressed = b;
        nitroPressed = n;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            clearControls();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(event.getActionIndex());
            float y = event.getY(event.getActionIndex());
            if (state == MENU) {
                startRace();
                return true;
            }
            if (state == GAME_OVER) {
                startRace();
                return true;
            }
            if (state == PAUSED) {
                if (resumeRect.contains(x, y)) state = PLAYING;
                else if (restartRect.contains(x, y)) startRace();
                else state = PLAYING;
                return true;
            }
            if (pauseRect.contains(x, y)) {
                state = PAUSED;
                clearControls();
                return true;
            }
        }
        recalcControls(event);
        return true;
    }

    private static final class TrafficCar {
        final float lane;
        final int color;
        final int kind;
        float depth = 0.02f;
        boolean counted;

        TrafficCar(float lane, int color, int kind) {
            this.lane = lane;
            this.color = color;
            this.kind = kind;
        }
    }
}
