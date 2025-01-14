/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.common.function.PDFunction;
import com.tom_roush.pdfbox.pdmodel.font.PDCIDFontType0;
import com.tom_roush.pdfbox.pdmodel.font.PDCIDFontType2;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDTrueTypeFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.pdmodel.font.PDType1CFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.PDLineDashPattern;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage;
import com.tom_roush.pdfbox.pdmodel.graphics.shading.PDShading;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDSoftMask;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import com.tom_roush.pdfbox.util.Matrix;
import com.tom_roush.pdfbox.util.Vector;

/**
 * Paints a page in a PDF document to a Canvas context. May be subclassed to provide custom
 * rendering.
 *
 * <p>If you want to do custom graphics processing rather than Canvas rendering, then you should
 * subclass PDFGraphicsStreamEngine instead. Subclassing PageDrawer is only suitable for cases
 * where the goal is to render onto a Canvas surface.
 *
 * @author Ben Litchfield
 */
public class PageDrawer extends PDFGraphicsStreamEngine
{
    // parent document renderer - note: this is needed for not-yet-implemented resource caching
    private final PDFRenderer renderer;

    // the graphics device to draw to, xform is the initial transform of the device (i.e. DPI)
    Paint paint;
    Canvas canvas;
    private AffineTransform xform;

    // the page box to draw (usually the crop box but may be another)
    private PDRectangle pageSize;

    // clipping winding rule used for the clipping path
    private Path.FillType clipWindingRule = null;
    private Path linePath = new Path();

    // last clipping path
    private Region lastClip;

    // buffered clipping area for text being drawn
    private Region textClippingArea;

    // glyph cache
    private final Map<PDFont, Glyph2D> fontGlyph2D = new HashMap<PDFont, Glyph2D>();

    private PointF currentPoint = new PointF();

    /**
     * Constructor.
     *
     * @param parameters Parameters for page drawing.
     * @throws IOException If there is an error loading properties from the file.
     */
    public PageDrawer(PageDrawerParameters parameters) throws IOException
    {
        super(parameters.getPage());
        this.renderer = parameters.getRenderer();
    }

    /**
     * Returns the parent renderer.
     */
    public final PDFRenderer getRenderer()
    {
        return renderer;
    }

    /**
     * Returns the underlying Canvas. May be null if drawPage has not yet been called.
     */
    protected final Canvas getCanvas()
    {
        return canvas;
    }

    /**
     * Returns the current line path. This is reset to empty after each fill/stroke.
     */
    protected final Path getLinePath()
    {
        return linePath;
    }

    /**
     * Sets high-quality rendering hints on the current Graphics2D.
     */
    private void setRenderingHints()
    {
//        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                                  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
//                                  RenderingHints.VALUE_RENDER_QUALITY);
        paint.setAntiAlias(true);
    }

    /**
     * Draws the page to the requested canvas.
     *
     * @param p The paint.
     * @param c The canvas to draw onto.
     * @param pageSize The size of the page to draw.
     * @throws IOException If there is an IO error while drawing the page.
     */
    public void drawPage(Paint p, Canvas c, PDRectangle pageSize) throws IOException
    {
        paint = p;
        canvas = c;
        xform = new AffineTransform(canvas.getMatrix());
        this.pageSize = pageSize;

        setRenderingHints();

        canvas.translate(0, pageSize.getHeight());
        canvas.scale(1, -1);

        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeWidth(1.0f); // FIXME: PdfBox-Android: create set stroke method?

        // adjust for non-(0,0) crop box
        canvas.translate(-pageSize.getLowerLeftX(), -pageSize.getLowerLeftY());
        canvas.save();

        processPage(getPage());

        for (PDAnnotation annotation : getPage().getAnnotations())
        {
            showAnnotation(annotation);
        }

//		graphics = null;
    }

    /**
     * Draws the pattern stream to the requested context.
     *
     * @param g The graphics context to draw onto.
     * @param pattern The tiling pattern to be used.
     * @param colorSpace color space for this tiling.
     * @param color color for this tiling.
     * @param patternMatrix the pattern matrix
     * @throws IOException If there is an IO error while drawing the page.
     */
//    void drawTilingPattern(Graphics2D g, PDTilingPattern pattern, PDColorSpace colorSpace,
//                                  PDColor color, Matrix patternMatrix) throws IOException
//    {
//        Graphics2D oldGraphics = graphics;
//        graphics = g;
//
//        Path oldLinePath = linePath;
//        linePath = new GeneralPath();
//
//        Area oldLastClip = lastClip;
//        lastClip = null;
//
//        setRenderingHints();
//        processTilingPattern(pattern, color, colorSpace, patternMatrix);
//
//        graphics = oldGraphics;
//        linePath = oldLinePath;
//        lastClip = oldLastClip;
//    } TODO: PdfBox-Android

    /**
     * Returns an AWT paint for the given PDColor.
     */
//    protected Paint getPaint(PDColor color) throws IOException
//    {
//        PDColorSpace colorSpace = color.getColorSpace();
//        if (!(colorSpace instanceof PDPattern))
//        {
//            float[] rgb = colorSpace.toRGB(color.getComponents());
//            return new Color(rgb[0], rgb[1], rgb[2]);
//        }
//        else
//        {
//            PDPattern patternSpace = (PDPattern)colorSpace;
//            PDAbstractPattern pattern = patternSpace.getPattern(color);
//            if (pattern instanceof PDTilingPattern)
//            {
//                PDTilingPattern tilingPattern = (PDTilingPattern) pattern;
//
//                if (tilingPattern.getPaintType() == PDTilingPattern.PAINT_COLORED)
//                {
//                    // colored tiling pattern
//                    return new TilingPaint(this, tilingPattern, xform);
//                }
//                else
//                {
//                    // uncolored tiling pattern
//                    return new TilingPaint(this, tilingPattern,
//                            patternSpace.getUnderlyingColorSpace(), color, xform);
//                }
//            }
//            else
//            {
//                PDShadingPattern shadingPattern = (PDShadingPattern)pattern;
//                PDShading shading = shadingPattern.getShading();
//                if (shading == null)
//                {
//                    LOG.error("shadingPattern is null, will be filled with transparency");
//                    return new Color(0,0,0,0);
//                }
//                return shading.toPaint(Matrix.concatenate(getInitialMatrix(),
//					shadingPattern.getMatrix()));
//            }
//        }
//    } TODO: PdfBox-Android

    // returns an integer for color that Android understands from the PDColor
    // TODO: alpha?
    private int getColor(PDColor color) throws IOException {
        PDColorSpace colorSpace = color.getColorSpace();
        float[] cvalue = color.getComponents();
        if (cvalue.length>0){
            float[] floats = colorSpace.toRGB(cvalue);
            int r = Math.round(floats[0] * 255);
            int g = Math.round(floats[1] * 255);
            int b = Math.round(floats[2] * 255);
            return Color.rgb(r, g, b);
        }else {
            int r = Math.round(255);
            int g = Math.round(255);
            int b = Math.round(255);
            return Color.rgb(r, g, b);
        }
    }

    // sets the clipping path using caching for performance, we track lastClip manually because
    // Graphics2D#getClip() returns a new object instead of the same one passed to setClip
    private void setClip()
    {
        Region clippingPath = getGraphicsState().getCurrentClippingPath();
        if (clippingPath != lastClip)
        {
//            canvas.clipPath(clippingPath.getBoundaryPath()); TODO: PdfBox-Android
            lastClip = clippingPath;
        }
    }

    @Override
    public void beginText() throws IOException
    {
        setClip();
        beginTextClip();
    }

    @Override
    public void endText() throws IOException
    {
        endTextClip();
    }

    /**
     * Begin buffering the text clipping path, if any.
     */
    private void beginTextClip()
    {
        // buffer the text clip because it represents a single clipping area
        textClippingArea = new Region();
    }

    /**
     * End buffering the text clipping path, if any.
     */
    private void endTextClip()
    {
        PDGraphicsState state = getGraphicsState();
        RenderingMode renderingMode = state.getTextState().getRenderingMode();

        // apply the buffered clip as one area
        if (renderingMode.isClip() && !textClippingArea.isEmpty())
        {
            state.intersectClippingPath(textClippingArea);
            textClippingArea = null;
        }
    }

    @Override
    protected void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode,
        Vector displacement) throws IOException
    {
        AffineTransform at = textRenderingMatrix.createAffineTransform();
        at.concatenate(font.getFontMatrix().createAffineTransform());

        Glyph2D glyph2D = createGlyph2D(font);
        drawGlyph2D(glyph2D, font, code, displacement, at);
    }

    /**
     * Render the font using the Glyph2D interface.
     *
     * @param glyph2D the Glyph2D implementation provided a GeneralPath for each glyph
     * @param font the font
     * @param code character code
     * @param displacement the glyph's displacement (advance)
     * @param at the transformation
     * @throws IOException if something went wrong
     */
    private void drawGlyph2D(Glyph2D glyph2D, PDFont font, int code, Vector displacement,
        AffineTransform at) throws IOException
    {
        PDGraphicsState state = getGraphicsState();
        RenderingMode renderingMode = state.getTextState().getRenderingMode();

        Path path = glyph2D.getPathForCharacterCode(code);
        if (path != null)
        {
            // stretch non-embedded glyph if it does not match the width contained in the PDF
            if (!font.isEmbedded())
            {
                float fontWidth = font.getWidthFromFont(code);
                if (fontWidth > 0 && // ignore spaces
                    Math.abs(fontWidth - displacement.getX() * 1000) > 0.0001)
                {
                    float pdfWidth = displacement.getX() * 1000;
                    at.scale(pdfWidth / fontWidth, 1);
                }
            }

            // render glyph
//            Shape glyph = at.createTransformedShape(path);
            path.transform(at.toMatrix());

            if (renderingMode.isFill())
            {
//                graphics.setComposite(state.getNonStrokingJavaComposite());
//                graphics.setPaint(getNonStrokingPaint());
                paint.setColor(getNonStrokingColor());
                setClip();
//                graphics.fill(glyph);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
//                canvas.clipPath(path);
//                canvas.drawRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
            }

            if (renderingMode.isStroke())
            {
//                graphics.setComposite(state.getStrokingJavaComposite());
//                graphics.setPaint(getStrokingPaint());
                paint.setColor(getStrokingColor());
//                graphics.setStroke(getStroke());
                setClip();
//                graphics.draw(glyph);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(path, paint);
//                canvas.clipPath(path);
//                canvas.drawRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
            }

            if (renderingMode.isClip())
            {
//                textClippingArea.add(new Area(glyph));
            } // FIXME: PdfBox-Android: check this commented out stuff
        }
    }

    /**
     * Provide a Glyph2D for the given font.
     *
     * @param font the font
     * @return the implementation of the Glyph2D interface for the given font
     * @throws IOException if something went wrong
     */
    private Glyph2D createGlyph2D(PDFont font) throws IOException
    {
        Glyph2D glyph2D = fontGlyph2D.get(font);
        // Is there already a Glyph2D for the given font?
        if (glyph2D != null)
        {
            return glyph2D;
        }

        if (font instanceof PDTrueTypeFont)
        {
            PDTrueTypeFont ttfFont = (PDTrueTypeFont)font;
            glyph2D = new TTFGlyph2D(ttfFont);  // TTF is never null
        }
        else if (font instanceof PDType1Font)
        {
            PDType1Font pdType1Font = (PDType1Font)font;
            glyph2D = new Type1Glyph2D(pdType1Font); // T1 is never null
        }
        else if (font instanceof PDType1CFont)
        {
            PDType1CFont type1CFont = (PDType1CFont)font;
            glyph2D = new Type1Glyph2D(type1CFont);
        }
        else if (font instanceof PDType0Font)
        {
            PDType0Font type0Font = (PDType0Font) font;
            if (type0Font.getDescendantFont() instanceof PDCIDFontType2)
            {
                glyph2D = new TTFGlyph2D(type0Font); // TTF is never null
            }
            else if (type0Font.getDescendantFont() instanceof PDCIDFontType0)
            {
                // a Type0 CIDFont contains CFF font
                PDCIDFontType0 cidType0Font = (PDCIDFontType0)type0Font.getDescendantFont();
                glyph2D = new CIDType0Glyph2D(cidType0Font); // todo: could be null (need incorporate fallback)
            }
        }
        else
        {
            throw new IllegalStateException("Bad font type: " + font.getClass().getSimpleName());
        }

        // cache the Glyph2D instance
        if (glyph2D != null)
        {
            fontGlyph2D.put(font, glyph2D);
        }

        if (glyph2D == null)
        {
            // todo: make sure this never happens
            throw new UnsupportedOperationException("No font for " + font.getName());
        }

        return glyph2D;
    }

    @Override
    public void appendRectangle(PointF p0, PointF p1, PointF p2, PointF p3)
    {
        // to ensure that the path is created in the right direction, we have to create
        // it by combining single lines instead of creating a simple rectangle
        linePath.moveTo(p0.x, p0.y);
        linePath.lineTo(p1.x, p1.y);
        linePath.lineTo(p2.x, p2.y);
        linePath.lineTo(p3.x, p3.y);

        // close the subpath instead of adding the last line so that a possible set line
        // cap style isn't taken into account at the "beginning" of the rectangle
        linePath.close();
    }

    /**
     * Generates AWT raster for a soft mask
     *
     * @param softMask soft mask
     * @return AWT raster for soft mask
     * @throws IOException
     */
//    private Raster createSoftMaskRaster(PDSoftMask softMask) throws IOException
//    {
//        TransparencyGroup transparencyGroup = new TransparencyGroup(softMask.getGroup(), true);
//        COSName subtype = softMask.getSubType();
//        if (COSName.ALPHA.equals(subtype))
//        {
//            return transparencyGroup.getAlphaRaster();
//        }
//        else if (COSName.LUMINOSITY.equals(subtype))
//        {
//            return transparencyGroup.getLuminosityRaster();
//        }
//        else
//        {
//            throw new IOException("Invalid soft mask subtype.");
//        }
//    } TODO: PdfBox-Android

//    private Paint applySoftMaskToPaint(Paint parentPaint, PDSoftMask softMask) throws IOException
//    {
//        if (softMask != null)
//        {
//            //TODO PDFBOX-2934
//            if (COSName.ALPHA.equals(softMask.getSubType()))
//            {
//                Log.i("PdfBox-Android", "alpha smask not implemented yet, is ignored");
//                return parentPaint;
//            }
//            return new SoftMaskPaint(parentPaint, createSoftMaskRaster(softMask));
//        }
//        else
//        {
//            return parentPaint;
//        }
//    } TODO: PdfBox-Android

    // returns the stroking AWT Paint
//    private Paint getStrokingPaint() throws IOException
//    {
//        return applySoftMaskToPaint(
//                getPaint(getGraphicsState().getStrokingColor()),
//                getGraphicsState().getSoftMask());
//    } TODO: PdfBox-Android

    private int getStrokingColor() throws IOException {
        return getColor(getGraphicsState().getStrokingColor());
    }

    // returns the non-stroking AWT Paint
//    private Paint getNonStrokingPaint() throws IOException
//    {
//        return getPaint(getGraphicsState().getNonStrokingColor());
//    } TODO: PdfBox-Android

    private int getNonStrokingColor() throws IOException {
        return getColor(getGraphicsState().getNonStrokingColor());
    }

    // set stroke based on the current CTM and the current stroke
    private void setStroke()
    {
        PDGraphicsState state = getGraphicsState();

        // apply the CTM
        float lineWidth = transformWidth(state.getLineWidth());

        // minimum line width as used by Adobe Reader
        if (lineWidth < 0.25)
        {
            lineWidth = 0.25f;
        }

        PDLineDashPattern dashPattern = state.getLineDashPattern();
        int phaseStart = dashPattern.getPhase();
        float[] dashArray = dashPattern.getDashArray();
        if (dashArray != null)
        {
            // apply the CTM
            for (int i = 0; i < dashArray.length; ++i)
            {
                // minimum line dash width avoids JVM crash, see PDFBOX-2373, PDFBOX-2929, PDFBOX-3204
                float w = transformWidth(dashArray[i]);
                if (w != 0)
                {
                    dashArray[i] = Math.max(w, 0.035f);
                }
            }
            phaseStart = (int) transformWidth(phaseStart);

            // empty dash array is illegal
            if (dashArray.length == 0)
            {
                dashArray = null;
            }
        }

        paint.setStrokeWidth(lineWidth);
        paint.setStrokeCap(state.getLineCap());
        paint.setStrokeJoin(state.getLineJoin());
        if (dashArray != null)
        {
            paint.setPathEffect(new DashPathEffect(dashArray, phaseStart));
        }
    }

    @Override
    public void strokePath() throws IOException
    {

//        graphics.setComposite(getGraphicsState().getStrokingJavaComposite());

        setStroke();
        setClip();
        paint.setARGB(255, 0, 0, 0); // TODO set the correct color from graphics state. FIXME: 2.0, isn't this done below?
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getStrokingColor());
        setClip();
        canvas.drawPath(linePath, paint);
        linePath.reset();
    }

    @Override
    public void fillPath(Path.FillType windingRule) throws IOException
    {
//        graphics.setComposite(getGraphicsState().getNonStrokingJavaComposite());
        paint.setColor(getNonStrokingColor());
        setClip();
        linePath.setFillType(windingRule);

        // disable anti-aliasing for rectangular paths, this is a workaround to avoid small stripes
        // which occur when solid fills are used to simulate piecewise gradients, see PDFBOX-2302
        // note that we ignore paths with a width/height under 1 as these are fills used as strokes,
        // see PDFBOX-1658 for an example
        RectF bounds = new RectF();
        linePath.computeBounds(bounds, true);
        boolean noAntiAlias = false;//isRectangular(linePath) && bounds.width() > 1 && bounds.height() > 1; FIXME
        if (noAntiAlias)
        {
//            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                                      RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(linePath, paint);
        linePath.reset();

        if (noAntiAlias)
        {
            // JDK 1.7 has a bug where rendering hints are reset by the above call to
            // the setRenderingHint method, so we re-set all hints, see PDFBOX-2302
            setRenderingHints();
        }
    }

    /**
     * Returns true if the given path is rectangular.
     */
    private boolean isRectangular(Path path)
    {
        RectF rect = null;
        return path.isRect(rect);
    }

    /**
     * Fills and then strokes the path.
     *
     * @param windingRule The winding rule this path will use.
     * @throws IOException If there is an IO error while filling the path.
     */
    @Override
    public void fillAndStrokePath(Path.FillType windingRule) throws IOException
    {
        // TODO can we avoid cloning the path?
        Path path = new Path(linePath);
        fillPath(windingRule);
        linePath = path;
        strokePath();
    }

    @Override
    public void clip(Path.FillType windingRule)
    {
        // the clipping path will not be updated until the succeeding painting operator is called
        clipWindingRule = windingRule;
    }

    @Override
    public void moveTo(float x, float y)
    {
        currentPoint.x = x;
        currentPoint.y = y;
        linePath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y)
    {
        currentPoint.x = x;
        currentPoint.y = y;
        linePath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3)
    {
        currentPoint.x = x3;
        currentPoint.y = y3;
        linePath.cubicTo(x1, y1, x2, y2, x3, y3); // TODO: check if this should be relative
    }

    @Override
    public PointF getCurrentPoint()
    {
        return currentPoint;
    }

    @Override
    public void closePath()
    {
        linePath.close();
    }

    @Override
    public void endPath()
    {
//        if (clipWindingRule != null) FIXME: 2.0, makes things worse
//        {
//            linePath.setFillType(clipWindingRule);
//            getGraphicsState().intersectClippingPath(linePath);
//            clipWindingRule = null;
//        }
        linePath.reset();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException
    {
        com.tom_roush.pdfbox.util.Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        AffineTransform at = ctm.createAffineTransform();

        if (!pdImage.getInterpolate())
        {
            boolean isScaledUp = pdImage.getWidth() < Math.round(at.getScaleX()) ||
                pdImage.getHeight() < Math.round(at.getScaleY());

            // if the image is scaled down, we use smooth interpolation, eg PDFBOX-2364
            // only when scaled up do we use nearest neighbour, eg PDFBOX-2302 / mori-cvpr01.pdf
            // stencils are excluded from this rule (see survey.pdf)
            if (isScaledUp || pdImage.isStencil())
            {
//        		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//        				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            }
        }

        if (pdImage.isStencil())
        {
            // fill the image with paint
//            Bitmap image = pdImage.getStencilImage(getNonStrokingPaint());

            // draw the image
//            drawBufferedImage(image, at);
        }
        else
        {
            // draw the image
            drawBitmap(pdImage.getImage(), at);
        }

        if (!pdImage.getInterpolate())
        {
            // JDK 1.7 has a bug where rendering hints are reset by the above call to
            // the setRenderingHint method, so we re-set all hints, see PDFBOX-2302
            setRenderingHints();
        }
    }

    private void drawBitmap(Bitmap image, AffineTransform at) throws IOException
    {
//        graphics.setComposite(getGraphicsState().getNonStrokingJavaComposite());
        setClip();
        PDSoftMask softMask = getGraphicsState().getSoftMask();
        if( softMask != null )
        {
            AffineTransform imageTransform = new AffineTransform(at);
            imageTransform.scale(1, -1);
            imageTransform.translate(0, -1);
//            Paint awtPaint = new TexturePaint(image,
//                    new Rectangle2D.Double(imageTransform.getTranslateX(), imageTransform.getTranslateY(),
//                            imageTransform.getScaleX(), imageTransform.getScaleY()));
//            awtPaint = applySoftMaskToPaint(awtPaint, softMask);
//            graphics.setPaint(awtPaint);
            RectF unitRect = new RectF(0, 0, 1, 1);
//            graphics.fill(at.createTransformedShape(unitRect));
        }
        else
        {
            COSBase transfer = getGraphicsState().getTransfer();
            if (transfer instanceof COSArray || transfer instanceof COSDictionary)
            {
                image = applyTransferFunction(image, transfer);
            }

            int width = image.getWidth();
            int height = image.getHeight();
            AffineTransform imageTransform = new AffineTransform(at);
            imageTransform.scale(1.0f / width, -1.0f / height);
            imageTransform.translate(0, -height);
            canvas.drawBitmap(image, imageTransform.toMatrix(), paint);
        }
    }

    private Bitmap applyTransferFunction(Bitmap image, COSBase transfer) throws IOException
    {
        Bitmap bim = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);

        // prepare transfer functions (either one per color or one for all)
        // and maps (actually arrays[256] to be faster) to avoid calculating values several times
        Integer rMap[], gMap[], bMap[];
        PDFunction rf, gf, bf;
        if (transfer instanceof COSArray)
        {
            COSArray ar = (COSArray) transfer;
            rf = PDFunction.create(ar.getObject(0));
            gf = PDFunction.create(ar.getObject(1));
            bf = PDFunction.create(ar.getObject(2));
            rMap = new Integer[256];
            gMap = new Integer[256];
            bMap = new Integer[256];
        }
        else
        {
            rf = PDFunction.create(transfer);
            gf = rf;
            bf = rf;
            rMap = new Integer[256];
            gMap = rMap;
            bMap = rMap;
        }

        // apply the transfer function to each color, but keep alpha
        float input[] = new float[1];
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        for (int pixelIdx = 0; pixelIdx < image.getWidth() * image.getHeight(); pixelIdx++)
        {
            int rgb = pixels[pixelIdx];
            int ri = (rgb >> 16) & 0xFF;
            int gi = (rgb >> 8) & 0xFF;
            int bi = rgb & 0xFF;
            int ro, go, bo;
            if (rMap[ri] != null)
            {
                ro = rMap[ri];
            }
            else
            {
                input[0] = (ri & 0xFF) / 255f;
                ro = (int) (rf.eval(input)[0] * 255);
                rMap[ri] = ro;
            }
            if (gMap[gi] != null)
            {
                go = gMap[gi];
            }
            else
            {
                input[0] = (gi & 0xFF) / 255f;
                go = (int) (gf.eval(input)[0] * 255);
                gMap[gi] = go;
            }
            if (bMap[bi] != null)
            {
                bo = bMap[bi];
            }
            else
            {
                input[0] = (bi & 0xFF) / 255f;
                bo = (int) (bf.eval(input)[0] * 255);
                bMap[bi] = bo;
            }
            pixels[pixelIdx] = (rgb & 0xFF000000) | (ro << 16) | (go << 8) | bo;
        }
        bim.setPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        return bim;
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException
    {
        PDShading shading = getResources().getShading(shadingName);
        com.tom_roush.pdfbox.util.Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
//        Paint paint = shading.toPaint(ctm);

//        graphics.setComposite(getGraphicsState().getNonStrokingJavaComposite());
//        graphics.setPaint(paint);
//        graphics.setClip(null);
//        lastClip = null;
//        graphics.fill(getGraphicsState().getCurrentClippingPath());
    }

    @Override
    public void showAnnotation(PDAnnotation annotation) throws IOException
    {
        lastClip = null;
        // TODO support more annotation flags (Invisible, NoZoom, NoRotate)
        // Example for NoZoom can be found in p5 of PDFBOX-2348
//    	int deviceType = graphics.getDeviceConfiguration().getDevice().getType();
//    	if (deviceType == GraphicsDevice.TYPE_PRINTER && !annotation.isPrinted())
//    	{
//    		return;
//    	} Shouldn't be needed
        if (/*deviceType == GraphicsDevice.TYPE_RASTER_SCREEN && */annotation.isNoView())
        {
            return;
        }
        if (annotation.isHidden())
        {
            return;
        }
        super.showAnnotation(annotation);

        if (annotation.getAppearance() == null)
        {
            if (annotation instanceof PDAnnotationLink)
            {
                drawAnnotationLinkBorder((PDAnnotationLink) annotation);
            }

            if (annotation instanceof PDAnnotationMarkup && annotation.getSubtype().equals(PDAnnotationMarkup.SUB_TYPE_INK))
            {
                drawAnnotationInk((PDAnnotationMarkup) annotation);
            }
        }
    }

    private static class AnnotationBorder
    {
        private float[] dashArray = null;
        private boolean underline = false;
        private float width = 0;
        private PDColor color;
    }

    // return border info. BorderStyle must be provided as parameter because
    // method is not available in the base class
    private AnnotationBorder getAnnotationBorder(PDAnnotation annotation,
        PDBorderStyleDictionary borderStyle)
    {
        AnnotationBorder ab = new AnnotationBorder();
        COSArray border = annotation.getBorder();
        if (borderStyle == null)
        {
            if (border.get(2) instanceof COSNumber)
            {
                ab.width = ((COSNumber) border.getObject(2)).floatValue();
            }
            if (border.size() > 3)
            {
                COSBase base3 = border.getObject(3);
                if (base3 instanceof COSArray)
                {
                    ab.dashArray = ((COSArray) base3).toFloatArray();
                }
            }
        }
        else
        {
            ab.width = borderStyle.getWidth();
            if (borderStyle.getStyle().equals(PDBorderStyleDictionary.STYLE_DASHED))
            {
                ab.dashArray = borderStyle.getDashStyle().getDashArray();
            }
            if (borderStyle.getStyle().equals(PDBorderStyleDictionary.STYLE_UNDERLINE))
            {
                ab.underline = true;
            }
        }
        ab.color = annotation.getColor();
        if (ab.color == null)
        {
            // spec is unclear, but black seems to be the right thing to do
            ab.color = new PDColor(new float[] { 0 }, PDDeviceGray.INSTANCE);
        }
        if (ab.dashArray != null)
        {
            boolean allZero = true;
            for (float f : ab.dashArray)
            {
                if (f != 0)
                {
                    allZero = false;
                    break;
                }
            }
            if (allZero)
            {
                ab.dashArray = null;
            }
        }
        return ab;
    }

    private void drawAnnotationLinkBorder(PDAnnotationLink link) throws IOException
    {
        Log.e("PdfBox-Android", "Hey! We drew an annotation link border!");
        AnnotationBorder ab = getAnnotationBorder(link, link.getBorderStyle());
        if (ab.width == 0)
        {
            return;
        }
        PDRectangle rectangle = link.getRectangle();

        Paint strokePaint = new Paint(paint);
        strokePaint.setColor(getColor(ab.color));
        setStroke(strokePaint, ab.width, Paint.Cap.BUTT, Paint.Join.MITER, 10, ab.dashArray, 0);
        canvas.restore();
        if (ab.underline)
        {
            canvas.drawLine(rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                rectangle.getLowerLeftX() + rectangle.getWidth(), rectangle.getLowerLeftY(),
                strokePaint);
        }
        else
        {
            canvas.drawRect(rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                rectangle.getWidth(), rectangle.getHeight(), strokePaint);
        }
    }

    private void drawAnnotationInk(PDAnnotationMarkup inkAnnotation) throws IOException
    {
        Log.e("PdfBox-Android", "Hey! We drew an annotation ink!");
        if (!inkAnnotation.getCOSObject().containsKey(COSName.INKLIST))
        {
            return;
        }
        //TODO there should be an InkAnnotation class with a getInkList method
        COSBase base = inkAnnotation.getCOSObject().getDictionaryObject(COSName.INKLIST);
        if (!(base instanceof COSArray))
        {
            return;
        }
        // PDF spec does not mention /Border for ink annotations, but it is used if /BS is not available
        AnnotationBorder ab = getAnnotationBorder(inkAnnotation, inkAnnotation.getBorderStyle());
        if (ab.width == 0)
        {
            return;
        }
        Paint strokePaint = new Paint(paint);
        strokePaint.setColor(getColor(ab.color));
        setStroke(strokePaint, ab.width, Paint.Cap.BUTT, Paint.Join.MITER, 10, ab.dashArray, 0);
        canvas.restore();
        COSArray pathsArray = (COSArray) base;
        for (COSBase baseElement : (Iterable<? extends COSBase>) pathsArray.toList())
        {
            if (!(baseElement instanceof COSArray))
            {
                continue;
            }
            COSArray pathArray = (COSArray) baseElement;
            int nPoints = pathArray.size() / 2;

            // "When drawn, the points shall be connected by straight lines or curves
            // in an implementation-dependent way" - we do lines.
            Path path = new Path();
            for (int i = 0; i < nPoints; ++i)
            {
                COSBase bx = pathArray.getObject(i * 2);
                COSBase by = pathArray.getObject(i * 2 + 1);
                if (bx instanceof COSNumber && by instanceof COSNumber)
                {
                    float x = ((COSNumber) bx).floatValue();
                    float y = ((COSNumber) by).floatValue();
                    if (i == 0)
                    {
                        path.moveTo(x, y);
                    }
                    else
                    {
                        path.lineTo(x, y);
                    }
                }
            }
            canvas.drawPath(path, strokePaint);
        }
    }

    public void setStroke(Paint p, float width, Paint.Cap cap, Paint.Join join, float miterLimit, float[] dash, float dash_phase) {
        p.setStrokeWidth(width);
        p.setStrokeCap(cap);
        p.setStrokeJoin(join);
        p.setStrokeMiter(miterLimit);
        p.setPathEffect(new DashPathEffect(dash, dash_phase));
    }

    @Override
    public void showTransparencyGroup(PDTransparencyGroup form) throws IOException
    {
        TransparencyGroup group = new TransparencyGroup(form, false);

//        graphics.setComposite(getGraphicsState().getNonStrokingJavaComposite());
        setClip();

        // both the DPI xform and the CTM were already applied to the group, so all we do
        // here is draw it directly onto the Graphics2D device at the appropriate position
//        PDRectangle bbox = group.getBBox();
//        AffineTransform prev = graphics.getTransform();
//        float x = bbox.getLowerLeftX();
//        float y = pageSize.getHeight() - bbox.getLowerLeftY() - bbox.getHeight();
//        graphics.setTransform(AffineTransform.getTranslateInstance(x * xform.getScaleX(),
//                                                                   y * xform.getScaleY()));

        PDSoftMask softMask = getGraphicsState().getSoftMask();
        if (softMask != null)
        {
//            Bitmap image = group.getImage();
//            Paint awtPaint = new TexturePaint(image,
//                    new Rectangle2D.Float(0, 0, image.getWidth(), image.getHeight()));
//            awtPaint = applySoftMaskToPaint(awtPaint, softMask); // todo: PDFBOX-994 problem here?
//            graphics.setPaint(awtPaint);
//            graphics.fill(new Rectangle2D.Float(0, 0, bbox.getWidth() * (float)xform.getScaleX(),
//                                                bbox.getHeight() * (float)xform.getScaleY()));
        }
        else
        {
//            graphics.drawImage(group.getImage(), null, null);
        }

//        graphics.setTransform(prev);
    }

    /**
     * Transparency group.
     **/
    private final class TransparencyGroup
    {
//        private final Bitmap image;
//        private final PDRectangle bbox;

//        private final int minX;
//        private final int minY;
//        private final int width;
//        private final int height; TODO: PdfBox-Android

        /**
         * Creates a buffered image for a transparency group result.
         */
        private TransparencyGroup(PDTransparencyGroup form, boolean isSoftMask) throws IOException
        {
//            Graphics2D g2dOriginal = graphics;
//            Area lastClipOriginal = lastClip;

            // get the CTM x Form Matrix transform
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Matrix transform = Matrix.concatenate(ctm, form.getMatrix());

            // transform the bbox
            Path transformedBox = form.getBBox().transform(transform);

            // clip the bbox to prevent giant bboxes from consuming all memory
//            Area clip = (Area)getGraphicsState().getCurrentClippingPath().clone();
//            clip.intersect(new Area(transformedBox));
//            Rectangle2D clipRect = clip.getBounds2D();
//            this.bbox = new PDRectangle((float)clipRect.getX(), (float)clipRect.getY(),
//                                        (float)clipRect.getWidth(), (float)clipRect.getHeight());

            // apply the underlying Graphics2D device's DPI transform
//            Shape deviceClip = xform.createTransformedShape(clip);
//            Rectangle2D bounds = deviceClip.getBounds2D();

//            minX = (int) Math.floor(bounds.getMinX());
//            minY = (int) Math.floor(bounds.getMinY());
//            int maxX = (int) Math.floor(bounds.getMaxX()) + 1;
//            int maxY = (int) Math.floor(bounds.getMaxY()) + 1;

//            width = maxX - minX;
//            height = maxY - minY;

//            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); // FIXME - color space
//            Graphics2D g = image.createGraphics();

            // flip y-axis
//            g.translate(0, height);
//            g.scale(1, -1);

            // apply device transform (DPI)
//            g.transform(xform);

            // adjust the origin
//            g.translate(-clipRect.getX(), -clipRect.getY());

//            graphics = g;
            try
            {
                if (isSoftMask)
                {
                    processSoftMask(form);
                }
                else
                {
                    processTransparencyGroup(form);
                }
            }
            finally
            {
//                lastClip = lastClipOriginal;
//                graphics.dispose();
//                graphics = g2dOriginal;
            }
        }

//        public Bitmap getImage()
//        {
//            return image;
//        }

//        public PDRectangle getBBox()
//        {
//            return bbox;
//        }

//        public Raster getAlphaRaster()
//        {
//            return image.getAlphaRaster();
//        }

//        public Raster getLuminosityRaster()
//        {
//            BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
//            Graphics g = gray.getGraphics();
//            g.drawImage(image, 0, 0, null);
//            g.dispose();
//
//            return gray.getRaster();
//        }
    }
}
