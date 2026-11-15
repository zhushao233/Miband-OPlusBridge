/*
 * Backdrop capture and rounded refraction adapted from
 * Kyant0/AndroidLiquidGlass (Apache-2.0), ported to the Android View system.
 */
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
                float2 grad = normalize(
                        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
                                + depthEffect * normalize(centeredCoord));
                return content.eval(coord + d * grad);
            }
            """;

    private final View backdropSource;
    private final boolean floating;
    private final boolean glass;
    private final boolean dark;
    private final float cornerRadius;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RenderNode renderNode = new RenderNode("liquid-glass");

    private Shader borderShader;
    private RenderEffect renderEffect;
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
        super(context);
        this.backdropSource = backdropSource;
        this.floating = floating;
        this.glass = glass;
        dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cornerRadius = dp(context, floating ? 32 : 0);
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
        drawHighlight(canvas);
        canvas.restore();
    }

    private void drawSurface(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        if (floating) drawHighlight(canvas);
        canvas.restore();
    }

    private void drawHighlight(Canvas canvas) {
        borderPaint.setShader(borderShader);
        canvas.drawPath(clipPath, borderPaint);
    }

    private RenderEffect renderEffect() {
        if (renderEffect != null) return renderEffect;
        try {
            RuntimeShader shader = new RuntimeShader(REFRACTION_SHADER);
            shader.setFloatUniform("size", getWidth(), getHeight());
            shader.setFloatUniform("offset", 0f, 0f);
            shader.setFloatUniform("cornerRadii",
                    cornerRadius, cornerRadius, cornerRadius, cornerRadius);
            shader.setFloatUniform("refractionHeight", dp(getContext(), 20));
            shader.setFloatUniform("refractionAmount", -dp(getContext(), 20));
            shader.setFloatUniform("depthEffect", 0f);
            RenderEffect lens = RenderEffect.createRuntimeShaderEffect(shader, "content");
            RenderEffect blur = RenderEffect.createBlurEffect(
                    dp(getContext(), 12), dp(getContext(), 12), Shader.TileMode.CLAMP);
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
                int[] sourceLocation = new int[2];
                int[] dockLocation = new int[2];
                backdropSource.getLocationInWindow(sourceLocation);
                getLocationInWindow(dockLocation);
                recording.translate(
                        sourceLocation[0] - dockLocation[0],
                        sourceLocation[1] - dockLocation[1]);
                backdropSource.draw(recording);
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
        int tint = dark
                ? (base | (glass ? 0x86000000 : 0xFF000000))
                : (base | (glass ? 0x70FFFFFF : 0xFFFFFFFF));
        fillPaint.setShader(null);
        fillPaint.setColor(tint);

        if (glass && floating) {
            int top = dark ? 0x20FFFFFF : 0x55FFFFFF;
            int middle = dark ? 0x0CFFFFFF : 0x20FFFFFF;
            int bottom = dark ? 0x18000000 : 0x12000000;
            Shader fillShader = new LinearGradient(0, 0, width, height,
                    new int[]{top, middle, bottom}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
            borderShader = new LinearGradient(0, 0, width, height,
                    new int[]{0x66FFFFFF, 0x16FFFFFF, 0x26000000},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
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
