/*
 * Backdrop capture and rounded refraction adapted from
 * Kyant0/AndroidLiquidGlass (Apache-2.0), ported to the Android View system.
 */
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RecordingCanvas;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;

/**
 * Draws the dock as a real backdrop effect: the content beneath the dock is
 * copied into a RenderNode, blurred and refracted through a rounded-rectangle
 * AGSL shader before the translucent surface is painted on top.
 */
public final class LiquidGlassContainer extends FrameLayout {
    private static final String REFRACTION_SHADER = """
            uniform shader content;
            uniform float2 size;
            uniform float2 offset;
            uniform float4 cornerRadii;
            uniform float refractionHeight;
            uniform float refractionAmount;
            uniform float depthEffect;
            uniform float chromaticAberration;

            float radiusAt(float2 centeredCoord, float4 radii) {
                if (centeredCoord.x >= 0.0) {
                    if (centeredCoord.y <= 0.0) return radii.y;
                    else return radii.z;
                } else {
                    if (centeredCoord.y <= 0.0) return radii.x;
                    else return radii.w;
                }
            }

            float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                    return sign(coord) * normalize(max(cornerCoord, 0.0));
                } else {
                    float gradX = step(cornerCoord.y, cornerCoord.x);
                    return sign(coord) * float2(gradX, 1.0 - gradX);
                }
            }

            float circleMap(float x) {
                return 1.0 - sqrt(1.0 - x * x);
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = (coord + offset) - halfSize;
                float2 normalized = centeredCoord / max(halfSize, float2(1.0));
                float radial = length(normalized);
                float lensZoom = 0.20 * (1.0 - smoothstep(0.0, 1.0, radial));
                float2 sampleCoord = centeredCoord * (1.0 - lensZoom) + halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);
                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                float verticalRim = 0.0;
                float horizontalRim = 0.0;
                if (-sd < refractionHeight) {
                    float inside = max(-sd, 0.0);
                    float d = circleMap(1.0 - inside / refractionHeight) * refractionAmount;
                    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
                    float2 normal = grad / max(length(grad), 0.0001);
                    float edge = 1.0 - smoothstep(0.0, refractionHeight, inside);
                    verticalRim = edge * pow(abs(normal.y), 1.15);
                    horizontalRim = edge * pow(abs(normal.x), 1.5);
                    sampleCoord += d * normal;
                }
                half4 color = content.eval(sampleCoord);
                if (chromaticAberration > 0.0) {
                    float2 fringe = normal * chromaticAberration
                            * (verticalRim + 0.55 * horizontalRim);
                    color.r = content.eval(sampleCoord + fringe).r;
                    color.b = content.eval(sampleCoord - fringe).b;
                }
                color.rgb *= 1.0 + 0.24 * verticalRim - 0.08 * horizontalRim;
                return color;
            }
            """;

    private final View backdropSource;
    private final boolean floating;
    private final boolean glass;
    private final boolean selection;
    private final boolean dark;
    private final float cornerRadius;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RenderNode renderNode = new RenderNode("liquid-glass");

    private View refractionOverlay;
    private LiquidGlassContainer dockSurface;
    private Shader borderShader;
    private RenderEffect renderEffect;
    private float interactionProgress;
    private float interactionX;
    private float interactionY;
    private boolean effectFailed;
    private boolean ticking;
    private final Runnable backdropTick = new Runnable() {
        @Override public void run() {
            if (!ticking || effectFailed || !isAttachedToWindow()
                    || getWindowVisibility() != VISIBLE) {
                ticking = false;
                return;
            }
            captureBackdrop();
            invalidate();
            postOnAnimationDelayed(this, 32);
        }
    };

    public LiquidGlassContainer(Context context, View backdropSource,
                                boolean floating, boolean glass) {
        this(context, backdropSource, floating, glass, false);
    }

    public LiquidGlassContainer(Context context, View backdropSource,
                                boolean floating, boolean glass, boolean selection) {
        super(context);
        this.backdropSource = backdropSource;
        this.floating = floating;
        this.glass = glass;
        this.selection = selection;
        dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cornerRadius = dp(context, selection ? 38.4f : (floating ? 32 : 0));
        setWillNotDraw(false);
        setClipChildren(false);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(context, 1));
    }

    public void setRefractionOverlay(View overlay) {
        refractionOverlay = overlay;
    }

    public void setDockSurface(LiquidGlassContainer dock) {
        if (dock != this) dockSurface = dock;
    }

    void drawBackdropLayer(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        canvas.restore();
    }

    public void setFloatingPressScale(float progress) {
        if (!floating || selection) return;
        float value = Math.max(0f, Math.min(1f, progress));
        setScaleX(1f + 0.018f * value);
        setScaleY(1f + 0.024f * value);
    }

    public void setDragOffset(float offset) {
        if (floating) setTranslationX(offset);
    }

    public void setInteraction(float progress, float x, float y) {
        interactionProgress = Math.max(0f, Math.min(1f, progress));
        interactionX = x;
        interactionY = y;
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            int color = selection
                    ? ContextCompat.getColor(getContext(),
                    io.github.miam1ku.mibandoplusbridge.R.color.accent)
                    : Color.WHITE;
            int center = (color & 0x00FFFFFF) | 0xFF000000;
            int edge = color & 0x00FFFFFF;
            glowPaint.setShader(new RadialGradient(
                    interactionX, interactionY, Math.max(width, height) * 1.15f,
                    new int[]{center, edge}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            glowPaint.setAlpha(Math.round(64f * interactionProgress));
        }
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTicker();
    }

    @Override protected void onDetachedFromWindow() {
        stopTicker();
        renderNode.discardDisplayList();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startTicker();
        else stopTicker();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        setPivotX(width * 0.5f);
        setPivotY(height * 0.5f);
        updateGeometry(width, height);
        captureBackdrop();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (glass && canvas.isHardwareAccelerated() && !effectFailed) {
            drawGlass(canvas);
        } else {
            drawSurface(canvas);
        }
    }

    private void drawGlass(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (!renderNode.hasDisplayList()) {
            drawSurface(canvas);
            return;
        }
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRenderNode(renderNode);
        canvas.drawRect(0, 0, width, height, fillPaint);
        if (!selection) drawHighlight(canvas);
        drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawSurface(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        if (floating) drawHighlight(canvas);
        drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawHighlight(Canvas canvas) {
        if (borderShader == null) return;
        borderPaint.setShader(borderShader);
        canvas.drawPath(clipPath, borderPaint);
    }

    private void drawInteractionHighlight(Canvas canvas) {
        if (interactionProgress <= 0f || glowPaint.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
    }

    private RenderEffect renderEffect() {
        if (renderEffect != null) return renderEffect;
        try {
            RuntimeShader shader = new RuntimeShader(REFRACTION_SHADER);
            shader.setFloatUniform("size", getWidth(), getHeight());
            shader.setFloatUniform("offset", 0f, 0f);
            shader.setFloatUniform("cornerRadii",
                    cornerRadius, cornerRadius, cornerRadius, cornerRadius);
            shader.setFloatUniform("refractionHeight",
                    dp(getContext(), selection ? 12 : 20));
            shader.setFloatUniform("refractionAmount",
                    -dp(getContext(), selection ? 18 : 18));
            shader.setFloatUniform("depthEffect", 0f);
            shader.setFloatUniform("chromaticAberration",
                    dp(getContext(), selection ? 1.2f : 0f));
            RenderEffect lens = RenderEffect.createRuntimeShaderEffect(shader, "content");
            RenderEffect blur = RenderEffect.createBlurEffect(
                    dp(getContext(), selection ? 1 : 4),
                    dp(getContext(), selection ? 1 : 4), Shader.TileMode.CLAMP);
            renderEffect = RenderEffect.createChainEffect(lens, blur);
        } catch (RuntimeException unsupported) {
            effectFailed = true;
        }
        return renderEffect;
    }

    private void captureBackdrop() {
        if (!glass || effectFailed || backdropSource == null
                || getWidth() <= 0 || getHeight() <= 0
                || !backdropSource.isAttachedToWindow()) {
            return;
        }
        try {
            int width = getWidth();
            int height = getHeight();
            RecordingCanvas recording = renderNode.beginRecording(width, height);
            try {
                int[] glassLocation = new int[2];
                getLocationInWindow(glassLocation);
                drawLayer(recording, backdropSource, glassLocation);
                if (dockSurface != null) drawLayer(recording, dockSurface, glassLocation);
                if (refractionOverlay != null) {
                    drawLayer(recording, refractionOverlay, glassLocation);
                }
            } finally {
                renderNode.endRecording();
            }
            renderNode.setPosition(0, 0, width, height);
            renderNode.setRenderEffect(renderEffect());
        } catch (RuntimeException unavailable) {
            effectFailed = true;
            stopTicker();
        }
    }

    private void drawLayer(RecordingCanvas recording, View layer, int[] glassLocation) {
        int[] location = new int[2];
        layer.getLocationInWindow(location);
        recording.save();
        recording.translate(location[0] - glassLocation[0], location[1] - glassLocation[1]);
        if (layer == dockSurface) {
            dockSurface.drawBackdropLayer(recording);
        } else {
            layer.draw(recording);
        }
        recording.restore();
    }

    private void startTicker() {
        if (!glass || ticking || getWindowVisibility() != VISIBLE
                || !isAttachedToWindow()) return;
        ticking = true;
        postOnAnimation(backdropTick);
    }

    private void stopTicker() {
        ticking = false;
        removeCallbacks(backdropTick);
    }

    private void updateGeometry(int width, int height) {
        clipPath.reset();
        clipPath.addRoundRect(0, 0, width, height,
                cornerRadius, cornerRadius, Path.Direction.CW);

        int surface = ContextCompat.getColor(getContext(),
                io.github.miam1ku.mibandoplusbridge.R.color.surface_container_low);
        int base = surface & 0x00FFFFFF;
        int tint;
        if (selection) {
            tint = dark
                    ? (glass ? 0x1CFFFFFF : 0xE6000000)
                    : (glass ? 0x16000000 : 0xF2FFFFFF);
        } else {
            tint = dark
                    ? (base | (glass ? 0x86000000 : 0xFF000000))
                    : (base | (glass ? 0x70FFFFFF : 0xFFFFFFFF));
        }
        fillPaint.setShader(null);
        fillPaint.setColor(tint);

        if (glass && selection) {
            int top = dark ? 0x40FFFFFF : 0x2C000000;
            int middle = dark ? 0x0CFFFFFF : 0x08000000;
            int bottom = dark ? 0x34FFFFFF : 0x22000000;
            Shader fillShader = new LinearGradient(0, 0, 0, height,
                    new int[]{top, middle, bottom}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
            borderShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0xE6FFFFFF, 0x48FFFFFF, 0xC0FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        } else if (glass && floating) {
            int top = dark ? 0x22FFFFFF : 0x58FFFFFF;
            int middle = dark ? 0x08FFFFFF : 0x18FFFFFF;
            int bottom = dark ? 0x1CFFFFFF : 0x34FFFFFF;
            Shader fillShader = new LinearGradient(0, 0, 0, height,
                    new int[]{top, middle, bottom}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
            borderShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0x8AFFFFFF, 0x12FFFFFF, 0x62FFFFFF},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        } else {
            borderShader = null;
        }
        renderEffect = null;
        effectFailed = false;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
