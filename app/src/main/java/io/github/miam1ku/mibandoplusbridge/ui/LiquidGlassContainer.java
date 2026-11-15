/*
 * Backdrop capture and rounded refraction adapted from
 * Kyant0/AndroidLiquidGlass (Apache-2.0), ported to the Android View system.
 */
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
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
                float radius = radiusAt(centeredCoord, cornerRadii);
                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                if (-sd >= refractionHeight) return content.eval(coord);
                sd = min(sd, 0.0);
                float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
                float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
                grad = normalize(grad + depthEffect * normalize(centeredCoord));
                float2 sampleCoord = coord + d * grad;
                if (chromaticAberration > 0.0) {
                    float dispersionIntensity = chromaticAberration
                            * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
                    float2 dispersedCoord = d * grad * dispersionIntensity;
                    half4 color = half4(0.0);
                    half4 red = content.eval(sampleCoord + dispersedCoord);
                    color.r += red.r / 3.5;
                    color.a += red.a / 7.0;
                    half4 orange = content.eval(sampleCoord + dispersedCoord * (2.0 / 3.0));
                    color.r += orange.r / 3.5;
                    color.g += orange.g / 7.0;
                    color.a += orange.a / 7.0;
                    half4 yellow = content.eval(sampleCoord + dispersedCoord * (1.0 / 3.0));
                    color.r += yellow.r / 3.5;
                    color.g += yellow.g / 3.5;
                    color.a += yellow.a / 7.0;
                    half4 green = content.eval(sampleCoord);
                    color.g += green.g / 3.5;
                    color.a += green.a / 7.0;
                    half4 cyan = content.eval(sampleCoord - dispersedCoord * (1.0 / 3.0));
                    color.g += cyan.g / 3.5;
                    color.b += cyan.b / 3.0;
                    color.a += cyan.a / 7.0;
                    half4 blue = content.eval(sampleCoord - dispersedCoord * (2.0 / 3.0));
                    color.b += blue.b / 3.0;
                    color.a += blue.a / 7.0;
                    half4 purple = content.eval(sampleCoord - dispersedCoord);
                    color.r += purple.r / 7.0;
                    color.b += purple.b / 3.0;
                    color.a += purple.a / 7.0;
                    return color;
                }
                return content.eval(sampleCoord);
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
    private final Paint innerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RenderNode renderNode = new RenderNode("liquid-glass");

    private View refractionOverlay;
    private LiquidGlassContainer dockSurface;
    private RuntimeShader lensShader;
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
            postOnAnimationDelayed(this, selection && interactionProgress > 0f ? 16 : 32);
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
        borderPaint.setStrokeWidth(dp(context, 0.65f));
        borderPaint.setBlendMode(BlendMode.PLUS);
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
        if (renderNode.hasDisplayList()) {
            canvas.drawRenderNode(renderNode);
        } else {
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        if (floating) drawHighlight(canvas);
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
            glowPaint.setShader(new RadialGradient(
                    interactionX, interactionY, Math.max(width, height) * 1.15f,
                    new int[]{0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            glowPaint.setAlpha(Math.round(40f * interactionProgress));
            if (lensShader != null && selection) {
                lensShader.setFloatUniform("refractionAmount",
                        -dp(getContext(), 10) * interactionProgress);
                lensShader.setFloatUniform("chromaticAberration",
                        interactionProgress);
            }
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
        if (selection) drawSelectionSurface(canvas);
        else canvas.drawRect(0, 0, width, height, fillPaint);
        drawHighlight(canvas);
        drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawSurface(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        if (selection) drawSelectionSurface(canvas);
        else canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        if (floating || selection) drawHighlight(canvas);
        drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawSelectionSurface(Canvas canvas) {
        int rest = dark ? 0x1AFFFFFF : 0x1A000000;
        fillPaint.setShader(null);
        fillPaint.setColor(withAlpha(rest,
                Math.round((rest >>> 24) * (1f - interactionProgress))));
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        if (interactionProgress > 0f) {
            fillPaint.setColor(withAlpha(0x08000000,
                    Math.round(0x08 * interactionProgress)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
            innerShadowPaint.setShader(new LinearGradient(
                    0, 0, 0, dp(getContext(), 10),
                    new int[]{0x26000000, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            innerShadowPaint.setAlpha(Math.round(255f * interactionProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), innerShadowPaint);
        }
    }

    private void drawHighlight(Canvas canvas) {
        if (borderShader == null) return;
        if (selection && interactionProgress <= 0f) return;
        borderPaint.setShader(borderShader);
        borderPaint.setAlpha(selection
                ? Math.round(145f * interactionProgress) : 128);
        canvas.drawPath(clipPath, borderPaint);
    }

    private void drawInteractionHighlight(Canvas canvas) {
        if (interactionProgress <= 0f || glowPaint.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
    }

    private RenderEffect renderEffect() {
        if (renderEffect != null) return renderEffect;
        try {
            lensShader = new RuntimeShader(REFRACTION_SHADER);
            lensShader.setFloatUniform("size", getWidth(), getHeight());
            lensShader.setFloatUniform("offset", 0f, 0f);
            lensShader.setFloatUniform("cornerRadii",
                    cornerRadius, cornerRadius, cornerRadius, cornerRadius);
            lensShader.setFloatUniform("refractionHeight",
                    dp(getContext(), selection ? 14 : 24));
            lensShader.setFloatUniform("refractionAmount",
                    selection
                            ? -dp(getContext(), 10) * interactionProgress
                            : -dp(getContext(), 24));
            lensShader.setFloatUniform("depthEffect", 0f);
            lensShader.setFloatUniform("chromaticAberration",
                    selection ? interactionProgress : 0);
            RenderEffect lens = RenderEffect.createRuntimeShaderEffect(lensShader, "content");
            if (selection) {
                renderEffect = lens;
            } else {
                RenderEffect blur = RenderEffect.createBlurEffect(
                        dp(getContext(), 4), dp(getContext(), 4), Shader.TileMode.CLAMP);
                renderEffect = RenderEffect.createChainEffect(lens, blur);
            }
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
                    ? (glass ? 0x1AFFFFFF : 0xE6000000)
                    : (glass ? 0x1A000000 : 0xF2FFFFFF);
        } else {
            tint = dark
                    ? (base | (glass ? 0x66000000 : 0xFF000000))
                    : (base | (glass ? 0x66FFFFFF : 0xFFFFFFFF));
        }
        fillPaint.setShader(null);
        fillPaint.setColor(tint);

        if (glass && selection) {
            fillPaint.setShader(null);
            fillPaint.setColor(dark ? 0x1AFFFFFF : 0x1A000000);
            borderShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0xFFFFFFFF, 0x54FFFFFF, 0xE6FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        } else if (glass && floating) {
            int top = dark ? 0x26FFFFFF : 0x52FFFFFF;
            int middle = dark ? 0x08FFFFFF : 0x14FFFFFF;
            int bottom = dark ? 0x20FFFFFF : 0x3CFFFFFF;
            Shader fillShader = new LinearGradient(0, 0, 0, height,
                    new int[]{top, middle, bottom}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
            borderShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0xFFFFFFFF, 0x54FFFFFF, 0xE6FFFFFF},
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
