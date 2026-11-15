/*
 * Backdrop capture, refraction and edge highlight adapted from
 * Kyant0/AndroidLiquidGlass (Apache-2.0), ported to the Android View system.
 */
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RecordingCanvas;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Draws the dock as a real backdrop effect. The page behind the dock is copied
 * into a RenderNode, saturated, blurred and refracted through a rounded-rectangle
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

            half4 sampleContent(float2 coord) {
                return content.eval(clamp(coord, float2(0.5), size - 0.5));
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = (coord + offset) - halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);
                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                if (-sd >= refractionHeight) return sampleContent(coord);
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
                    half4 red = sampleContent(sampleCoord + dispersedCoord);
                    color.r += red.r / 3.5;
                    color.a += red.a / 7.0;
                    half4 orange = sampleContent(sampleCoord + dispersedCoord * (2.0 / 3.0));
                    color.r += orange.r / 3.5;
                    color.g += orange.g / 7.0;
                    color.a += orange.a / 7.0;
                    half4 yellow = sampleContent(sampleCoord + dispersedCoord * (1.0 / 3.0));
                    color.r += yellow.r / 3.5;
                    color.g += yellow.g / 3.5;
                    color.a += yellow.a / 7.0;
                    half4 green = sampleContent(sampleCoord);
                    color.g += green.g / 3.5;
                    color.a += green.a / 7.0;
                    half4 cyan = sampleContent(sampleCoord - dispersedCoord * (1.0 / 3.0));
                    color.g += cyan.g / 3.5;
                    color.b += cyan.b / 3.0;
                    color.a += cyan.a / 7.0;
                    half4 blue = sampleContent(sampleCoord - dispersedCoord * (2.0 / 3.0));
                    color.b += blue.b / 3.0;
                    color.a += blue.a / 7.0;
                    half4 purple = sampleContent(sampleCoord - dispersedCoord);
                    color.r += purple.r / 7.0;
                    color.b += purple.b / 3.0;
                    color.a += purple.a / 7.0;
                    return color;
                }
                return sampleContent(sampleCoord);
            }
            """;

    private static final String HIGHLIGHT_SHADER = """
            uniform float2 size;
            uniform float4 cornerRadii;
            uniform float angle;
            uniform float falloff;

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

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = coord - halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);
                float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
                float2 normal = float2(cos(angle), sin(angle));
                float intensity = pow(abs(dot(grad, normal)), falloff);
                return half4(1.0, 1.0, 1.0, 1.0) * intensity;
            }
            """;

    private static final ColorMatrixColorFilter VIBRANCY_FILTER =
            new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    1.6065f, 0.3575f, 0.0360f, 0f, 0f,
                    0.1065f, 1.8575f, 0.0360f, 0f, 0f,
                    0.1065f, 0.3575f, 1.5360f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            }));

    private final View backdropSource;
    private final boolean floating;
    private final boolean glass;
    private final boolean selection;
    private final boolean dark;
    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint interactionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dockContentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RenderNode renderNode = new RenderNode("liquid-glass");

    private View refractionOverlay;
    private View dockContent;
    private LiquidGlassContainer dockSurface;
    private RuntimeShader lensShader;
    private RuntimeShader highlightShader;
    private RenderEffect renderEffect;
    private float cornerRadius;
    private float interactionProgress;
    private float interactionX;
    private float interactionY;
    private int solidTint;
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
        setWillNotDraw(false);
        setClipChildren(false);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setBlendMode(BlendMode.PLUS);
        outlinePaint.setStyle(Paint.Style.STROKE);
        interactionPaint.setBlendMode(BlendMode.PLUS);
        glowPaint.setBlendMode(BlendMode.PLUS);
    }

    public void setRefractionOverlay(View overlay) {
        refractionOverlay = overlay;
    }

    public void setDockSurface(LiquidGlassContainer dock) {
        if (dock != this) dockSurface = dock;
    }

    public void setDockContent(View content, int tintColor) {
        if (content == this) return;
        dockContent = content;
        dockContentPaint.setColorFilter(new PorterDuffColorFilter(
                tintColor, PorterDuff.Mode.SRC_IN));
    }

    public void setFloatingPressScale(float progress) {
        if (!floating || selection) return;
        float value = Math.max(0f, Math.min(1f, progress));
        float width = Math.max(1f, getWidth());
        float scale = 1f + dp(getContext(), 16) / width * value;
        setScaleX(scale);
        setScaleY(scale);
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
                    interactionX, interactionY,
                    Math.min(width, height) * 1.5f,
                    new int[]{0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            glowPaint.setAlpha(Math.round(38f * interactionProgress));
            if (lensShader != null && selection) {
                lensShader.setFloatUniform("refractionAmount",
                        -dp(getContext(), 14) * interactionProgress);
                lensShader.setFloatUniform("chromaticAberration", interactionProgress);
            }
        }
        if (selection) {
            if (interactionProgress > 0f) startTicker();
            else stopTicker();
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
        if (selection) {
            canvas.save();
            canvas.clipPath(clipPath);
            if (interactionProgress > 0.001f && renderNode.hasDisplayList()) {
                canvas.drawRenderNode(renderNode);
            }
            drawSelectionSurface(canvas);
            drawOutline(canvas);
            drawHighlight(canvas);
            canvas.restore();
            return;
        }

        canvas.save();
        canvas.clipPath(clipPath);
        if (renderNode.hasDisplayList()) canvas.drawRenderNode(renderNode);
        drawDockSurface(canvas);
        drawOutline(canvas);
        drawHighlight(canvas);
        drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawSurface(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        if (selection) drawSelectionSurface(canvas);
        else drawDockSurface(canvas);
        if (floating) drawOutline(canvas);
        if (!selection) drawInteractionHighlight(canvas);
        canvas.restore();
    }

    private void drawDockSurface(Canvas canvas) {
        surfacePaint.setShader(null);
        if (!glass) {
            surfacePaint.setColor(solidTint);
        } else {
            surfacePaint.setColor(dark ? 0x66121212 : 0x66FAFAFA);
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
    }

    private void drawSelectionSurface(Canvas canvas) {
        if (!glass) {
            surfacePaint.setShader(null);
            surfacePaint.setColor(solidTint);
            canvas.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
            return;
        }

        surfacePaint.setShader(null);
        surfacePaint.setColor(withAlpha(0x1A000000,
                Math.round(0x1A * (1f - interactionProgress))));
        canvas.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
        if (interactionProgress > 0f) {
            surfacePaint.setColor(withAlpha(0x08000000,
                    Math.round(0x08 * interactionProgress)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
            innerShadowPaint.setShader(new LinearGradient(
                    0, 0, 0, dp(getContext(), 10),
                    new int[]{0x26000000, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            innerShadowPaint.setAlpha(Math.round(255f * interactionProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), innerShadowPaint);
        }
    }

    private void drawHighlight(Canvas canvas) {
        if (highlightShader == null) return;
        if (selection && interactionProgress <= 0f) return;
        highlightPaint.setShader(highlightShader);
        highlightPaint.setAlpha(selection
                ? Math.round(128f * interactionProgress) : 128);
        canvas.drawPath(clipPath, highlightPaint);
    }

    private void drawOutline(Canvas canvas) {
        int alpha;
        if (selection) {
            alpha = dark ? 26 : 18;
        } else {
            alpha = dark ? 64 : 28;
        }
        outlinePaint.setShader(null);
        outlinePaint.setColor(withAlpha(dark ? 0xFFFFFFFF : 0xFF000000, alpha));
        outlinePaint.setStrokeWidth(dp(getContext(), 1.25f));
        canvas.drawPath(clipPath, outlinePaint);
    }

    private void drawInteractionHighlight(Canvas canvas) {
        if (interactionProgress <= 0f) return;
        interactionPaint.setShader(null);
        interactionPaint.setColor(withAlpha(0xFFFFFFFF,
                Math.round(20f * interactionProgress)));
        canvas.drawRect(0, 0, getWidth(), getHeight(), interactionPaint);
        if (glowPaint.getShader() != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
        }
    }

    private RenderEffect renderEffect() {
        if (renderEffect != null) return renderEffect;
        try {
            RenderEffect saturated =
                    RenderEffect.createColorFilterEffect(VIBRANCY_FILTER);
            if (selection) {
                lensShader = createLensShader(dp(getContext(), 10),
                        -dp(getContext(), 14) * interactionProgress, true);
                renderEffect = RenderEffect.createRuntimeShaderEffect(
                        lensShader, "content");
            } else {
                RenderEffect blurred = RenderEffect.createChainEffect(
                        RenderEffect.createBlurEffect(
                                dp(getContext(), 8), dp(getContext(), 8),
                                Shader.TileMode.CLAMP),
                        saturated);
                lensShader = createLensShader(dp(getContext(), 24), -dp(getContext(), 24), false);
                renderEffect = RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(lensShader, "content"),
                        blurred);
            }
        } catch (RuntimeException unsupported) {
            effectFailed = true;
        }
        return renderEffect;
    }

    private RuntimeShader createLensShader(float height, float amount, boolean chromatic) {
        RuntimeShader shader = new RuntimeShader(REFRACTION_SHADER);
        shader.setFloatUniform("size", getWidth(), getHeight());
        shader.setFloatUniform("offset", 0f, 0f);
        shader.setFloatUniform("cornerRadii",
                cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        shader.setFloatUniform("refractionHeight", height);
        shader.setFloatUniform("refractionAmount", amount);
        shader.setFloatUniform("depthEffect", 0f);
        shader.setFloatUniform("chromaticAberration", chromatic ? interactionProgress : 0f);
        return shader;
    }

    private void captureBackdrop() {
        if (!glass || effectFailed || backdropSource == null
                || getWidth() <= 0 || getHeight() <= 0
                || !backdropSource.isAttachedToWindow()) {
            return;
        }
        if (selection && interactionProgress <= 0f) return;
        try {
            int width = getWidth();
            int height = getHeight();
            RecordingCanvas recording = renderNode.beginRecording(width, height);
            try {
                int[] glassLocation = new int[2];
                getLocationInWindow(glassLocation);
                applyInverseScale(recording);
                drawLayer(recording, backdropSource, glassLocation);
                if (dockSurface != null) {
                    drawLayer(recording, dockSurface, glassLocation);
                } else if (refractionOverlay != null) {
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

    private void applyInverseScale(Canvas canvas) {
        float scaleX = getScaleX();
        float scaleY = getScaleY();
        if (Math.abs(scaleX - 1f) < 0.0001f && Math.abs(scaleY - 1f) < 0.0001f) {
            return;
        }
        canvas.scale(1f / scaleX, 1f / scaleY);
    }

    private void drawLayer(RecordingCanvas recording, View layer, int[] glassLocation) {
        int[] location = new int[2];
        layer.getLocationInWindow(location);
        recording.save();
        recording.translate(location[0] - glassLocation[0], location[1] - glassLocation[1]);
        if (layer == dockSurface) {
            dockSurface.drawDockComposition(recording);
        } else {
            layer.draw(recording);
        }
        recording.restore();
    }

    private void drawDockComposition(RecordingCanvas recording) {
        recording.save();
        recording.clipPath(clipPath);
        if (renderNode.hasDisplayList()) recording.drawRenderNode(renderNode);
        drawDockSurface(recording);
        drawOutline(recording);
        drawHighlight(recording);
        if (dockContent != null) {
            int[] dockLocation = new int[2];
            int[] contentLocation = new int[2];
            getLocationInWindow(dockLocation);
            dockContent.getLocationInWindow(contentLocation);
            recording.saveLayer(null, dockContentPaint);
            recording.translate(contentLocation[0] - dockLocation[0],
                    contentLocation[1] - dockLocation[1]);
            float scaleX = getScaleX();
            float scaleY = getScaleY();
            recording.translate(getPivotX() * (scaleX - 1f) * 0.5f,
                    getPivotY() * (scaleY - 1f) * 0.5f);
            dockContent.draw(recording);
            recording.restore();
        }
        recording.restore();
    }

    private void startTicker() {
        if (!glass || ticking || getWindowVisibility() != VISIBLE
                || !isAttachedToWindow() || (selection && interactionProgress <= 0f)) return;
        ticking = true;
        postOnAnimation(backdropTick);
    }

    private void stopTicker() {
        ticking = false;
        removeCallbacks(backdropTick);
    }

    private void updateGeometry(int width, int height) {
        cornerRadius = floating || selection
                ? Math.min(width, height) * 0.5f : 0f;
        clipPath.reset();
        clipPath.addRoundRect(0, 0, width, height,
                cornerRadius, cornerRadius, Path.Direction.CW);

        int surface = getResources().getColor(
                io.github.miam1ku.mibandoplusbridge.R.color.surface_container_low,
                getContext().getTheme());
        int base = surface & 0x00FFFFFF;
        if (selection) {
            solidTint = dark ? 0xE6000000 : 0xF2FFFFFF;
        } else {
            solidTint = base | 0xFF000000;
        }

        highlightShader = createHighlightShader();
        highlightPaint.setStrokeWidth(
                (float) Math.ceil(dp(getContext(), 0.5f)) * 2f);
        renderEffect = null;
        lensShader = null;
        effectFailed = false;
    }

    private RuntimeShader createHighlightShader() {
        RuntimeShader shader = new RuntimeShader(HIGHLIGHT_SHADER);
        shader.setFloatUniform("size", getWidth(), getHeight());
        shader.setFloatUniform("cornerRadii",
                cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        shader.setFloatUniform("angle", (float) Math.toRadians(45f));
        shader.setFloatUniform("falloff", 1f);
        return shader;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
