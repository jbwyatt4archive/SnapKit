/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.pdf.read;
import java.awt.Graphics2D;
//import java.awt.Image;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.*;
import snap.gfx.*;
import snap.gfx.Image;
import snap.pdf.*;

/**
 * This paints a PDFPage to a given Painter by parsing the page marking operators.
 *
 *  Currently unsupported:
 *       - Ignores hyperlinks (annotations)
 *       - Type 1 & Type 3 fonts
 *       - Transparency blend modes other than /Normal
 *       ...
 */
public class PDFPagePainter {
    
    // The page being painted
    PDFPage              _page;
    
    // The PDF file for the page
    PDFFile              _pfile;
    
    // The page bytes holding page operators and data to be painted
    byte                 _pageBytes[];
    
    // A helper class to handle text processing
    PDFPageText          _text;
    
    // The gstates of the page being parsed
    Stack <PDFGState>    _gstates;
    
    // The current GState
    PDFGState            _gstate;
    
    // The bounds rect
    Rect                 _destRect;
    
    // The transform to flip coordinates from Painter (origin is top-left) to PDF (origin is bottom-left)
    AffineTransform      _flipXForm;
    
    // The start clip
    java.awt.Shape       _initialClip;
    
    // The image, if painting to image
    Image                _image;

    // The bounds of the area being parsed
    Rect                 _bounds;
    
    // The tokens of the page being parsed
    List <PageToken>     _tokens;
    
    // The current token index
    int                  _index;
    
    // Current path
    GeneralPath          _path = null, _futureClip = null;
    
    // save away the factory callback handler objects
    int                  _compatibilitySections = 0;
    
    // The graphics state
    PDFGState            gs;
   
    // The painter
    Painter              _pntr;
    
    // The graphics
    Graphics2D           _gfx;
    
/**
 * Creates a new PDFPagePainter.
 */
public PDFPagePainter(Painter aPntr, Rect aRect, PDFPage aPage)
{
    // Cache given page and page file
    _page = aPage;
    _pfile = aPage.getFile();
    
    // Initialize a text object
    _text = new PDFPageText(this);
    
    // Create the gstate list and the default gstate
    _gstates = new Stack();
    _gstates.push(_gstate = new PDFGState());
    
    // Set Painter
    _pntr = aPntr;
    _destRect = aRect;
    
    // Get Media box and crop box and set bounds of the area we're drawing into (interseciont of media and crop)
    Rect media = _page.getMediaBox(), crop = _page.getCropBox();
    _bounds = media.getIntersectRect(crop);
    
    // Set Painter and initialize gstate to bounds. TODO need to set transform for pages with "/Rotate" key
    _gstate.trans.setToTranslation(-_bounds.getX(),-_bounds.getY());
    //Transform  t = getGState().trans; t.setToTranslation(-_bounds.getX(),-_bounds.getY());
    //t.rotate(-Math.PI/2); t.translate(0,_bounds.getWidth());
    //TODO also need to make sure PDFPage returns right rect (ImageShape initialized from file) 
}

/**
 * Paint the given page.
 */
public void paint(PDFPage aPage)
{
    // Get page contents stream and stream bytes (decompressed/decoded)
    PDFStream pstream = _page.getPageContentsStream(); if(pstream==null) return;
    byte pbytes[] = pstream.decodeStream();
    
    // Create top-level list of tokens and run the lexer to fill the list
    List pageTokens = PageToken.getTokens(pbytes);
 
    // Start the markup handler
    beginPage(_bounds.getWidth(), _bounds.getHeight());
    
    // Parse the tokens
    paintTokens(pageTokens, pbytes);
}

/**
 * Set the bounds of the page.  This will be called before any marking operations.
 */
public void beginPage(double width, double height)
{
    // If no painter, create image and painter
    if(_pntr==null) {
        _image = Image.get((int)Math.ceil(width), (int)Math.ceil(height), true);
        _pntr = _image.getPainter();
    }

    // If no destination rect has been set, draw unscaled & untranslated
    if(_destRect==null) _destRect = new Rect(0, 0, width, height);
    
    // Set Graphics
    _gfx = _pntr.getNative(Graphics2D.class);
    _text._renderContext = _gfx.getFontRenderContext();
    
    // Save away the initial user clip
    _initialClip = _gfx.getClip();
    
    // Sets the clip for the destination to the page size
    _pntr.clip(_destRect);
    
    // The PDF space has (0,0) at the top, awt has it at the bottom
    _flipXForm = _gfx.getTransform();
    _flipXForm.concatenate(new AffineTransform(_destRect.width/width, 0, 0, -_destRect.height/height,
        _destRect.x, _destRect.getMaxY()));
}

/**
 * The meat and potatoes of the pdf parser. Translates the token list into a series of calls to either a Factory class,
 * which creates a Java2D object (like GeneralPath, Font, Image, GlyphVector, etc.), or the markup handler, which does
 * the actual drawing.
 */
protected void paintTokens(List tokenList, byte pageBytes[]) 
{
    // save away the factory callback handler objects
    _compatibilitySections = 0;
    
    // Cache old tokens and set token list that will be used by methods like getToken() (this method can be recursive)
    List oldTokens = _tokens; _tokens = tokenList;
    byte oldPageBytes[] = _pageBytes; _pageBytes = pageBytes;
    
    // Initialize current path. Note: path is not part of GState and so is not saved/restored by gstate ops
    _path = null; _futureClip = null;
    
    // Get the current gstate
    gs = _gstate;
    
    // Iterate over page contents tokens
    for(int i=0, iMax=_tokens.size(); i<iMax; i++) { PageToken token = getToken(i); _index = i;
    
        if(token.type==PageToken.PDFOperatorToken)
            paintOp(token);
            
        // It not an op, it must be an operand
        // Catch up on that clipping.  Plus be anal and return an error, just like Acrobat.
        //if(didDraw) { if(_futureClip!=null) establishClip(_futureClip, true); _futureClip = null;
        //    _path = null; }  // The current path and the current point are undefined after a draw
        //else if(_futureClip != null) { } // TODO: an error unless the last token was W or W*
    }

    // restore previous token list
    _tokens = oldTokens; _pageBytes = oldPageBytes;
}

/**
 * Returns the token at the given index.
 */
private PageToken getToken(int index) { return _tokens.get(index); }

/**
 * The meat and potatoes of the pdf parser. Translates the token list into a series of calls to either a Factory class,
 * which creates a Java2D object (like GeneralPath, Font, Image, GlyphVector, etc.), or the markup handler, which does
 * the actual drawing.
 */
public void paintOp(PageToken aToken)
{
    String op = aToken.getString();
    
    switch(op) {
        case "b": b(); break;      // Closepath, fill, stroke
        case "b*": b_x(); break;   // Closepath, fill, stroke (EO)
        case "B": B(); break;      // Fill, stroke
        case "B*": B_x(); break;   // Fill, stroke (EO)
        case "BT": BT(); break;    // Begin Text
        case "BX": BX(); break;
        case "BI": BI(); break;
        case "BDC": case "BMC": BDC(); break;
        case "c": c(); break;      // Curveto
        case "cm": cm(); break;    // Concat matrix
        case "cs": cs(); break;    // Set colorspace
        case "CS": CS(); break;    // Set stroke colorspace
        case "d": d(); break;      // Set dash
        case "Do": Do(); break;    // Do xobject
        case "DP": DP(); break;    // Marked content
        case "ET": ET(); break;    // End text
        case "EX": EX(); break;
        case "EMC": EMC(); break;
        case "f": case "F": f(); break;     // Fill
        case "f*": case "F*": f_x(); break; // Fill (EO)
        case "g": g(); break;      // Set gray
        case "gs": gs(); break;    // Extended graphics state
        case "G": G(); break;      // Set stroke gray
        case "h": h(); break;      // Closepath
        case "i": i(); break;      // Set flatness
        case "ID": ID(); break;
        case "j": j(); break;      // Set linejoin
        case "J": J(); break;      // Set linecap
        case "k": k(); break;      // Set cmyk
        case "K": K(); break;      // Set stroke cmyk
        case "l": l(); break;      // Lineto
        case "m": m(); break;      // Moveto
        case "M": M(); break;      // Set miterlimit
        case "MP": MP(); break;
        case "n": n(); break;      // Endpath
        case "q": q(); break;      // GSave
        case "Q": Q(); break;      // GRestore
        case "re": re(); break;    // Append rect
        case "rg": rg(); break;    // Set rgb color
        case "ri": ri(); break;    // Set render intent
        case "RG": RG(); break;    // Set stroke rgb color
        case "s": s(); break;      // Closepath
        case "sc": sc(); break;    // Set color in colorspace
        case "scn": scn(); break;  // Set color in colorspace
        case "sh": sh(); break;    // Set shader
        case "S": S(); break;      // Stroke path
        case "SC": SC(); break;    // Set stroke color in colorspace
        case "SCN": SCN(); break;  // Set stroke color in colorspace
        case "T*": T_x(); break;   // Move to next line
        case "Tc": Tc(); break;    // Set character spacing
        case "Td": Td(); break;    // Move relative to current line start
        case "TD": TD(); break;    // Move relative to current line start and set leading to -ty
        case "Tf": Tf(); break;    // Set font
        case "Tj": Tj(); break;    // Show text
        case "TJ": TJ(); break;    // Show text array
        case "TL": TL(); break;    // Set text leading
        case "Tm": Tm(); break;    // Set text matrix
        case "Tr": Tr(); break;    // Set text rendering mode
        case "Ts": Ts(); break;    // Set text rise
        case "Tw": Tw(); break;    // Set text word spacing
        case "Tz": Tz(); break;    // Set text horizontal scale factor
        //case "\'": case "\"": quote(); break;
        case "v": v(); break;      // Curveto
        case "w": w(); break;      // Set linewidth
        case "W": W(); break;      // Set clip
        case "W*": W_x(); break;   // Set clip (EO)
        case "y": y(); break;      // Curveto
        default: System.err.println("PDFPagePainter: Unknown op: " + op);
    }
}
                
/**
 * Closepath, fill, stroke
 */
void b()
{
    _path.setWindingRule(GeneralPath.WIND_NON_ZERO);
    _path.closePath();
    fillPath(gs, _path);
    strokePath(gs, _path);
    didDraw();
}

/**
 * Closepath, fill, stroke (EO)
 */
void b_x()
{
    _path.setWindingRule(GeneralPath.WIND_EVEN_ODD);
    _path.closePath();
    fillPath(gs, _path);
    strokePath(gs, _path);
    didDraw();
}

/**
 * Fill, stroke
 */
void B()
{
    _path.setWindingRule(GeneralPath.WIND_NON_ZERO);
    fillPath(gs, _path);
    strokePath(gs, _path);
    didDraw();
}

/**
 * Fill, stroke (EO)
 */
void B_x()
{
    _path.setWindingRule(GeneralPath.WIND_EVEN_ODD);
    fillPath(gs, _path);
    strokePath(gs, _path);
    didDraw();
}

/**
 * Begin Text
 */
void BT()  { _text.begin(); }

/**
 * BX start (possibly nested) compatibility section
 */
void BX()  { ++_compatibilitySections; }

/**
 * BI inline images
 */
void BI()  { _index = parseInlineImage(_index+1, _pageBytes); }

/**
 * BDC, BMC
 */
void BDC()  { }

/**
 * Curveto.
 */
void c()
{
    getPoint(_index, gs.cp);
    _path.curveTo(getFloat(_index-6), getFloat(_index-5), getFloat(_index-4), getFloat(_index-3), gs.cp.x, gs.cp.y);
}

/**
 * Concat matrix
 */
void cm()
{
    AffineTransform xfm = getTransform(_index);
    gs.trans.concatenate(xfm);
}

/**
 * Set colorspace
 */
void cs()
{
    String space = getToken(_index-1).getName();
    gs.colorSpace = PDFColorSpace.getColorspace(space, _pfile, _page);
}

/**
 * Set stroke colorspace
 */
void CS()
{
    String space = getToken(_index-1).getName();
    gs.scolorSpace = PDFColorSpace.getColorspace(space, _pfile, _page);
}

/**
 * Set dash
 */
void d()
{
    gs.lineDash = getFloatArray(_index-2);
    gs.dashPhase = getFloat(_index-1);
    gs.lineStroke = gs.createStroke();
}

/**
 * Do xobject
 */
void Do()
{
    String name = getToken(_index-1).getName();
    Object xobj = getXObject(name);
    if(xobj instanceof java.awt.Image)
        drawImage((java.awt.Image)xobj);
    else if(xobj instanceof PDFForm)
        executeForm((PDFForm)xobj);
    else throw new PDFException("Error reading XObject");
}

/**
 * Marked content
 */
void DP() { }

/**
 * ET - End text
 */
void ET()  { _text.end(); }

/**
 * EMC
 */
void EMC() { }

/**
 * EX
 */
void EX()  { if(--_compatibilitySections<0) throw new PDFException("Unbalanced BX/EX operators"); }

/**
 * Fill
 */
void f()
{
    _path.setWindingRule(GeneralPath.WIND_NON_ZERO);
    fillPath(gs, _path);
    didDraw();
}

/**
 * Fill (EO)
 */
void f_x()
{
    _path.setWindingRule(GeneralPath.WIND_EVEN_ODD);
    fillPath(gs, _path);
    didDraw();
}

/**
 * Set gray
 */
void g()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceGray", _pfile, _page);
    gs.color = getColor(cspace, _index); gs.colorSpace = cspace;
}

/**
 * Extended graphics state
 */
void gs()
{
    Map exg = getExtendedGStateNamed(getToken(_index-1).getName());
    readExtendedGState(gs, exg);
}

/**
 * Set stroke gray
 */
void G()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceGray", _pfile, _page);
    gs.scolor = getColor(cspace, _index); gs.scolorSpace = cspace;
}

/**
 * Closepath
 */
void h()
{
    _path.closePath();
    Point2D lastPathPoint = _path.getCurrentPoint(); 
    gs.cp.x = (float)lastPathPoint.getX();
    gs.cp.y = (float)lastPathPoint.getY();
}

/**
 * Set flatness
 */
void i()  { gs.flatness = getFloat(_index-1); }

/**
 * ID
 */
void ID()  { }

/**
 * Set linejoin
 */
void j()
{
    gs.lineJoin = getInt(_index-1);
    gs.lineStroke = gs.createStroke();
}

/**
 * Set linecap
 */
void J()
{
    gs.lineCap = getInt(_index-1);
    gs.lineStroke = gs.createStroke();
}

/**
 * Set cmyk
 */
void k()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceCMYK", _pfile, _page);
    Color acolor = getColor(cspace, _index);
    gs.colorSpace = cspace; gs.color = acolor;
}

/**
 * Set stroke cmyk
 */
void K()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceCMYK", _pfile, _page);
    Color acolor = getColor(cspace, _index);
    gs.scolorSpace = cspace; gs.scolor = acolor;
}

/**
 * Lineto
 */
void l()
{
    getPoint(_index, gs.cp);
    _path.lineTo(gs.cp.x, gs.cp.y);
}

/**
 * Moveto
 */
void m()
{
    getPoint(_index, gs.cp);
    if(_path==null) _path = new GeneralPath();
    _path.moveTo(gs.cp.x, gs.cp.y);
}

/**
 * Set miterlimit
 */
void M()
{
    gs.miterLimit = getFloat(_index-1);
    gs.lineStroke = gs.createStroke();
}

/**
 * Marked content point
 */
void MP()  { }

/**
 * Endpath
 */
void n()  { didDraw(); }

/**
 * gsave
 */
void q()  { gs = gsave(); }

/**
 * grestore.
 */
void Q()  { gs = grestore(); }

/**
 * Append rectangle
 */
void re()
{
    // Get rect
    float x = getFloat(_index-4), y = getFloat(_index-3);
    float w = getFloat(_index-2), h = getFloat(_index-1);
    
    // Create new path and add rect and reset current point to start of rect
    if(_path==null) _path = new GeneralPath();
    _path.moveTo(x,y); _path.lineTo(x+w,y); _path.lineTo(x+w,y+h); _path.lineTo(x,y+h); _path.closePath();
    gs.cp.x = x; gs.cp.y = y;  // TODO: Check that this is what really happens in pdf
}

/**
 * Set render intent
 */
void ri()
{
    gs.renderingIntent = PDFGState.getRenderingIntentID(getToken(_index-1).getString());
}

/**
 * Set rgb color
 */
void rg()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceRGB", _pfile, _page);
    gs.color = getColor(cspace, _index);
    gs.colorSpace = cspace;
}

/**
 * Set stroke rgb color
 */
void RG()
{
    ColorSpace cspace = PDFColorSpace.getColorspace("DeviceRGB", _pfile, _page);
    gs.scolor = getColor(cspace, _index);
    gs.scolorSpace = cspace;
}

/**
 * Closepath
 */
void s()
{
    _path.closePath();
    strokePath(gs, _path);
    didDraw();
}

/**
 * Set color in colorspace
 */
void sc()  { gs.color = getColor(gs.colorSpace, _index); }

/**
 * Set color in colorspace
 */
void scn()
{
    // Handle PatternSpace
    if(gs.colorSpace instanceof PDFColorSpaces.PatternSpace) { // && numops>=1) {
        
        System.err.println("PDFPagePainter: sc for PatternSpace not implemented");
        /*String pname = getToken(_index-1).getName();
        PDFPattern pat = getPattern(pname);
        gs.color = pat.getPaint();
        
        // this is really stupid.  change this around
        if(pat instanceof PDFPatterns.Tiling && gs.color==null) {
            // Uncolored tiling patterns require color values be passed. Note, that although you can draw them
            // any number of times in different colors, we only do it once (after which it will be cached)
            if (numops>1) {
                ColorSpace tileSpace=((PDFColorSpaces.PatternSpace)gs.colorSpace).tileSpace;
                if(tileSpace==null) tileSpace = gs.colorSpace;
                gs.color = getColor(tileSpace,_index-1, numops-1);
            }
            this.executePatternStream((PDFPatterns.Tiling)pat);
            gs.color = pat.getPaint();
        }*/
    }
    
    // Do normal version
    else gs.color = getColor(gs.colorSpace, _index);
}

/**
 * Set shader
 */
void sh()
{
    System.err.println("PDFPagePainter: Set shader (sh) not implemented");
    /*String shadename = getToken(_index-1).getName();
    java.awt.Paint oldPaint = gs.color;
    PDFPatterns.Shading shade = getShading(shadename);
    gs.color = shade.getPaint();  //save away old color
    // Get area to fill. If shading specifies bounds, use that, if not, use clip. else fill whole page.
    GeneralPath shadearea;
    if(shade.getBounds() != null)
        shadearea = new GeneralPath(shade.getBounds());
    else {
        Rectangle2D r = new Rectangle2D.Double(_bounds.x, _bounds.y, _bounds.width, _bounds.height);
        shadearea = gs.clip!=null? (GeneralPath)gs.clip.clone() : new GeneralPath(r);
        try { shadearea.transform(gs.trans.createInverse()); } // transform from page to user space
        catch(NoninvertibleTransformException e) { throw new PDFException("Invalid user space xform"); }
    }
    fillPath(gs, shadearea);
    gs.color = oldPaint;
    didDraw();*/
}

/**
 * Stroke path
 */
void S()
{
    strokePath(gs, _path);
    didDraw();
}

/**
 * Set stroke color in normal colorspaces
 */
void SC()  { gs.scolor = getColor(gs.scolorSpace, _index); }

/**
 * Set strokecolor in normal colorspaces
 */
void SCN()  { SC(); } // TODO: deal with weird colorspaces

/**
 * Move to next line
 */
void T_x()  { _text.positionText(0, -gs.tleading); }

/**
 * Set character spacing
 */
void Tc()  { gs.tcs = getFloat(_index-1); }

/**
 * Move relative to current line start
 */
void Td()
{
    float x = getFloat(_index-2);
    float y = getFloat(_index-1);
    _text.positionText(x,y);
}

/**
 * Move relative to current line start and set leading to -ty
 */
void TD()
{
    float x = getFloat(_index-2);
    float y = getFloat(_index-1);
    _text.positionText(x,y);
    gs.tleading = -y;
}

/**
 * Set font name and size
 */
void Tf()
{
    String fontalias = getToken(_index-2).getName(); // name in dict is key, so lose leading /
    gs.font = getFontDictForAlias(fontalias);
    gs.fontSize = getFloat(_index-1);
}

/**
 * Show text.
 */
void Tj()
{
    PageToken tok = getToken(_index-1);
    int tloc = tok.getStart(), tlen = tok.getLength();
    _text.showText(_pageBytes, tloc, tlen, gs);
}

/**
 * Show text with spacing adjustment array
 */
void TJ()
{
    List tArray = (List)(getToken(_index-1).value);
    _text.showText(_pageBytes, tArray, gs);
}

/**
 * Set text leading
 */
void TL()  { gs.tleading = getFloat(_index-1); }

/**
 * Set text matrix
 */
void Tm()
{
    float a = getFloat(_index-6), b = getFloat(_index-5), c = getFloat(_index-4), d = getFloat(_index-3);
    float tx = getFloat(_index-2), ty = getFloat(_index-1);
    _text.setTextMatrix(a, b, c, d, tx, ty);
}

/**
 * Set text rendering mode
 */
void Tr()  { gs.trendermode = getInt(_index-1); }

/**
 * Set text rise
 */
void Ts()  { gs.trise = getFloat(_index-1); }

/**
 * Set text word spacing
 */
void Tw()  { gs.tws = getFloat(_index-1); }

/**
 * Set text horizontal scale factor
 */
void Tz()  { gs.thscale = getFloat(_index-1)/100f; }

/**
 * Curveto (first control point is current point)
 */
void v()
{
    double cp1x = gs.cp.x, cp1y = gs.cp.y;
    Point cp2 = getPoint(_index-2);
    getPoint(_index, gs.cp);
    _path.curveTo(cp1x, cp1y, cp2.x, cp2.y, gs.cp.x, gs.cp.y);
}

/**
 * Set linewidth
 */
void w()
{
    gs.lineWidth = getFloat(_index-1);
    gs.lineStroke = gs.createStroke();
}

/**
 * Set clip
 */
void W()
{
    // Somebody at Adobe's been smoking crack. The clipping operation doesn't modify the clipping in the gstate.
    // Instead, the next path drawing operation will do that, but only AFTER it draws.  
    // So a sequence like 0 0 99 99 re W f will fill the rect first and then set the clip path using the rect.
    // Because the W operation doesn't do anything, they had to introduce the 'n' operation, which is a drawing no-op,
    // in order to do a clip and not also draw the path. You might think it would be safe to just reset the clip here,
    // since the path it will draw is the same as the path it will clip to. However, there's at least one (admittedly
    // obscure) case I can think of where clip(path),draw(path)  is different from draw(path),clip(path): 
    //     W* f  %eoclip, nonzero-fill
    // Note also, Acrobat considers it an error to have a W not immediately followed by drawing op (f,f*,F,s,S,B,b,n)
    if(_path != null) {
        _path.setWindingRule(GeneralPath.WIND_NON_ZERO);
        _futureClip = (GeneralPath)_path.clone();
     }
}

/**
 * Set clip (EO)
 */
void W_x()
{
    if(_path != null) {
        _path.setWindingRule(GeneralPath.WIND_EVEN_ODD);
        _futureClip = (GeneralPath)_path.clone();
     }
}

/**
 * Curveto (final point replicated)
 */
void y()
{
    Point cp1 = getPoint(_index-2);
    getPoint(_index, gs.cp);
    _path.curveTo(cp1.x, cp1.y, gs.cp.x, gs.cp.y, gs.cp.x, gs.cp.y);
}

/** quote */
//void quote()  { } //if(tlen==1 && parseTextOperator(c, i, numops, gs, pageBytes)) swallowedToken = true;
//void single_quote_close()  { _text.positionText(0, -gs.tleading); } // Fall through
//void double_quote_close() { gs.tws = getFloat(_index-3); gs.tcs = getFloat(_index-2); numops = 1;
//    _text.positionText(0, -gs.tleading); }

/**
 * Called after drawing op.
 */
void didDraw()
{
    // Note that unlike other ops that change gstate, there is a specific call into markup handler when clip changes.
    // Markup handler can choose whether to respond to clipping change or just to pull clip out of gstate when it draws.
    if(_futureClip != null)
        establishClip(_futureClip, true); _futureClip = null;

    // The current path and the current point are undefined after a draw
    _path = null;
}

/** Returns the token at the given index as a float. */
private float getFloat(int i) { return getToken(i).floatValue(); }

/** Returns the token at the given index as an int. */
private int getInt(int i) { return getToken(i).intValue(); }

/** Returns the token at the given index as an array of floats */
private float[] getFloatArray(int i) 
{
    List <PageToken> ftokens = (List)(getToken(i).value);
    float farray[] = new float[ftokens.size()];
    for(int j=0, jMax=ftokens.size(); j<jMax; j++)  // We assume all tokens are floats
        farray[j] = ftokens.get(j).floatValue();
    return farray;
}

/** Returns a new point at the given index */
private Point getPoint(int i)
{
    double x = getFloat(i-2);
    double y = getFloat(i-1);
    return new Point(x,y);
}

/** Gets the token at the given index as a point. */
private void getPoint(int i, Point2D.Float pt)
{
    pt.x = getFloat(i-2);
    pt.y = getFloat(i-1);
}

/** Returns the token at the given index as a transform. */
private AffineTransform getTransform(int i)
{
    float a = getFloat(i-6), b = getFloat(i-5);
    float c = getFloat(i-4), d = getFloat(i-3);
    float tx = getFloat(i-2), ty = getFloat(i-1);
    return new AffineTransform(a, b, c, d, tx, ty);
}

/** Called with any of the set color ops to create new color from values in stream. */
private Color getColor(ColorSpace space, int tindex)
{
    int cc = space.getNumComponents();
    float ary[] = new float[cc]; for(int i=0;i<cc;i++) ary[i] = getFloat(tindex-(cc-i));
    return new Color(space, ary, 1f);
}

/**
 * The values for keys in inline images are limited to a small subset of names, numbers, arrays and maybe a dict.
 */
Object getInlineImageValue(PageToken token, byte pageBytes[])
{
    // Names (like /DeviceGray or /A85). Names can optionally be abbreviated.
    if(token.type==PageToken.PDFNameToken) { String abbrev = token.getName(); 
        for(int i=0, n=_inline_image_value_abbreviations.length; i<n; ++i) {
            if(_inline_image_value_abbreviations[i][0].equals(abbrev))
                return '/' + _inline_image_value_abbreviations[i][1]; }
        return '/' + abbrev;  // not found, so it's not an abbreviation.  We assume it's valid
    }
    
    // Numbers or bools
    else if(token.type==PageToken.PDFNumberToken || token.type==PageToken.PDFBooleanToken)
        return token.value;
        
    // An array of numbers or names (for Filter or Decode)
    else if(token.type==PageToken.PDFArrayToken) { List tokenarray = (List)token.value;
           List newarray = new ArrayList(tokenarray.size());
           for(int j=0, jMax=tokenarray.size(); j<jMax; ++j)     // recurse
               newarray.add(getInlineImageValue((PageToken)tokenarray.get(j), pageBytes));
           return newarray;
    }
    
    // Hex strings for indexed color spaces
    else if (token.type == PageToken.PDFStringToken)
        return token.byteArrayValue(pageBytes);
    
    // TODO: One possible key in an inline image is DecodeParms (DP). The normal decodeparms for an image is a dict.
    // The pdf spec doesn't give any information on the format of the dictionary.  Does it use the normal dictionary
    // syntax, or does it use the inline image key/value syntax? I have no idea, and I don't know how to generate a
    // pdf file that would have an inline image with a decodeparms dict.
    else { }
    throw new PDFException("Error parsing inline image dictionary");
}
        
/** 
 * Converts the tokens & data inside a BI/EI block into an image and draws it.
 * Returns the index of the last token consumed.
 */
public int parseInlineImage(int tIndex, byte[] pageBytes)
{
    Hashtable imageDict = new Hashtable();
    imageDict.put("Subtype", "/Image");

    // Get the inline image key/value pairs and create a normal image dictionary
    for(int i=tIndex, iMax=_tokens.size(); i<iMax; ++i) {
        PageToken token = getToken(i);

        // Handle NameToken: Translate key, get value, add translated key/value pair to the real dict
        if(token.type==PageToken.PDFNameToken) {
            String key = translateInlineImageKey(token.getName());
            if (++i<iMax) {
                token = getToken(i);
                Object value = getInlineImageValue(token, pageBytes);
                imageDict.put(key,value);
            }
        }
        
        // The actual inline data. Create stream with dict & data and create image. The image does not get cached.
        // The only way an inline image would ever get reused is if it were inside a form xobject.
        // First get a colorspace object.  Inline images can use any colorspace a regular image can.
        // Create stream, tell imageFactory to create image and draw it
        else if (token.type==PageToken.PDFInlineImageData) {
            Object space = imageDict.get("ColorSpace");
            ColorSpace imageCSpace = space==null ? null : PDFColorSpace.getColorspace(space, _pfile, _page);
            PDFStream imageStream = new PDFStream(pageBytes, token.getStart(), token.getLength(), imageDict);
            drawImage(PDFImage.getImage(imageStream, imageCSpace, _pfile));
            return i; // return token index
        }
    }
    
    // should only get here on an error (like running out of tokens or having bad key/value pairs)
    throw new PDFException("Syntax error parsing inline image dictionary");
}

/** map for translating inline image abbreviations into standard tokens */
static final String _inline_image_key_abbreviations[][] = {
    {"BPC", "BitsPerComponent"}, {"CS", "ColorSpace"}, {"D", "Decode"}, {"DP", "DecodeParms"},
    {"F", "Filter"}, {"H", "Height"}, {"IM", "ImageMask"}, {"I", "Interpolate"}, {"W", "Width"}};

/** Looks up an abbreviation in the above map. */
private String translateInlineImageKey(String abbreviation)
{
    for(int i=0, n=_inline_image_key_abbreviations.length; i<n; ++i) {
        if(_inline_image_key_abbreviations[i][0].equals(abbreviation))
            return _inline_image_key_abbreviations[i][1]; }
    return abbreviation; // not found, so it's not an abbreviation
}

static final String _inline_image_value_abbreviations[][] = {
    {"G", "DeviceGray"}, {"RGB", "DeviceRGB"}, {"CMYK", "DeviceCMYK"}, {"I", "Indexed"}, {"AHx", "ASCIIHexDecode"},
    {"A85", "ASCII85Decode"}, {"LZW", "LZWDecode"}, {"Fl", "FlateDecode"}, {"RL", "RunLengthDecode"},
    {"CCF", "CCITTFaxDecode"}, {"DCT", "DCTDecode"}
};

public void executeForm(PDFForm aForm)
{
    Rectangle2D bbox = aForm.getBBox();
    AffineTransform xform = aForm.getTransform();
    
    // save the current gstate and set the transform in the newgstate
    PDFGState gs = gsave();
    gs.trans.concatenate(xform);
    
    // clip to the form bbox
    establishClip(new GeneralPath(bbox), true);
  
    // add the form's resources to the page resource stack
    _page.pushResources(aForm.getResources(_pfile));
    paintTokens(aForm.getTokens(), aForm.getBytes());  // recurse back into the parser with a new set of tokens
    _page.popResources();    // restore the old resources, gstate,ctm, & clip
    grestore();
}

/**
 * A pattern could execute its pdf over and over, like a form (above) but for performance reasons,
 * we only execute it once and cache a tile. To do this, we temporarily set the markup handler in the file to a new 
 * BufferedMarkupHander, add the pattern's resource dictionary and fire up the parser.
 */
public void executePatternStream(PDFPatterns.Tiling pat)
{
    // Create image painter and set
    PDFPagePainter pntr = new PDFPagePainter(null, null, _page);
    
    // By adding the pattern's resources to page's resource stack, it means pattern will have access to resources
    // defined by the page.  I'll bet Acrobat doesn't allow you to do this, but it shouldn't hurt anything.
    _page.pushResources(pat.getResources());
    
    // save the current gstate
    PDFGState gs = pntr.gsave();
    
    // Establish the pattern's transformation
    gs.trans.concatenate(pat.getTransform());
    
    // Begin the markup handler. TODO:probably going to have to add a translate by -x, -y of the bounds rect
    Rectangle2D prect = pat.getBounds();
    pntr.beginPage(prect.getWidth(), prect.getHeight());
    
    // Get the pattern stream's tokens
    byte contents[] = pat.getContents();
    List <PageToken> tokens = PageToken.getTokens(contents);
    
    // Fire up parser
    pntr.paintTokens(tokens, contents);
    
    // Get the image and set the tile.  All the resources can be freed up now
    pat.setTile(pntr.getBufferedImage());
}

/**
 * Pull out anything useful from an extended gstate dictionary
 */
void readExtendedGState(PDFGState gs, Map exgstate)
{
    boolean strokeChanged = false;
    boolean transparencyChanged = false;
    
    if(exgstate==null) return;
    
    // The dictionary will have been read in by PDFReader, so
    // elements will have been converted into appropriate types, like Integer, Float, List, etc.
    
    Iterator entries = exgstate.entrySet().iterator();
    while(entries.hasNext()) {
        Map.Entry entry=(Map.Entry)entries.next();
        String key = (String)entry.getKey();
        Object val = entry.getValue();
        
        //line width, line cap, line join, & miter limit
        if(key.equals("LW")) { gs.lineWidth = ((Number)val).floatValue(); strokeChanged = true; }
        else if(key.equals("LC")) { gs.lineCap = ((Number)val).intValue(); strokeChanged = true; }
        else if(key.equals("LJ")) { gs.lineJoin = ((Number)val).intValue(); strokeChanged = true; }
        else if(key.equals("ML")) { gs.miterLimit = ((Number)val).floatValue(); strokeChanged = true; }
     
        // Dash:       "/D  [ [4 2 5 5] 0 ]"
        else if(key.equals("D")) {
            List dash = (List)val;
            gs.dashPhase = ((Number)dash.get(1)).floatValue();
            List dashArray = (List)dash.get(0);
            int n = dashArray.size();
            gs.lineDash = new float[n];
            for(int i=0; i<n; ++i) gs.lineDash[i] = ((Number)dashArray.get(i)).floatValue();
            strokeChanged = true;
        }
    
        // Rendering intent
        else if(key.equals("RI"))
            gs.renderingIntent = PDFGState.getRenderingIntentID((String)val);
    
        // Transparency blending mode
        else if(key.equals("BM")) {
            int bm = getBlendModeID((String)val);
            if(bm != gs.blendMode) { gs.blendMode = bm; transparencyChanged = true; }
        }
        
        // Transparency - whether to treat alpha values as shape or transparency
        else if(key.equals("AIS")) {
            boolean ais = ((Boolean)val).booleanValue();
            if(ais != gs.alphaIsShape) { gs.alphaIsShape = ais; transparencyChanged=true; }
        }
        
        // Soft mask 
        else if(key.equals("SMask")) {
            if(val.equals("/None")) gs.softMask = null;
            else System.err.println("Soft mask being specified : "+val);
        }
        
        // Transparency - stroke alpha
        else if (key.equals("CA")) {
           float a = ((Number)val).floatValue();
           if(a != gs.salpha) { gs.alpha = a; transparencyChanged = true; }
        }
        
        // Transparency - nonstroke alpha
        else if (key.equals("ca")) {
            float a = ((Number)val).floatValue();
            if (a != gs.alpha) { gs.alpha = a; transparencyChanged = true; }
        }
        // Some other possible entries in this dict that are not currently handled include:
        // Font, BG, BG2, UCR, UCR2, OP, op, OPM, TR, TR2, HT, FL, SM, SA,TK
    }
   
    // cache a new stroke object
    if(strokeChanged)
        gs.lineStroke = gs.createStroke();
    
    // cache new composite objects if necessary
    if(transparencyChanged) {
        gs.composite = PDFComposite.createComposite(gs.blendMode, gs.alphaIsShape, gs.alpha);
        gs.scomposite = PDFComposite.createComposite(gs.blendMode, gs.alphaIsShape, gs.salpha);
    }
}

static int getBlendModeID(String pdfName)
{
    if(pdfName.equals("/Normal") || pdfName.equals("/Compatible")) return PDFComposite.NormalBlendMode;
    if(pdfName.equals("/Multiply")) return PDFComposite.MultiplyBlendMode;
    if(pdfName.equals("/Screen")) return PDFComposite.ScreenBlendMode;
    if(pdfName.equals("/Overlay")) return PDFComposite.OverlayBlendMode;
    if(pdfName.equals("/Darken")) return PDFComposite.DarkenBlendMode;
    if(pdfName.equals("/Lighten")) return PDFComposite.LightenBlendMode;
    if(pdfName.equals("/ColorDodge")) return PDFComposite.ColorDodgeBlendMode;
    if(pdfName.equals("/ColorBurn")) return PDFComposite.ColorBurnBlendMode;
    if(pdfName.equals("/HardLight")) return PDFComposite.HardLightBlendMode;
    if(pdfName.equals("/SoftLight")) return PDFComposite.SoftLightBlendMode;
    if(pdfName.equals("/Difference")) return PDFComposite.DifferenceBlendMode;
    if(pdfName.equals("/Exclusion")) return PDFComposite.ExclusionBlendMode;
    if(pdfName.equals("/Hue")) return PDFComposite.HueBlendMode;
    if(pdfName.equals("/Saturation")) return PDFComposite.SaturationBlendMode;
    if(pdfName.equals("/Color")) return PDFComposite.ColorBlendMode;
    if(pdfName.equals("/Luminosity")) return PDFComposite.LuminosityBlendMode;
    throw new PDFException("Unknown blend mode name \""+pdfName+"\"");
}

/**
 * Accessors for the resource dictionaries.
 */
public Map getExtendedGStateNamed(String name) { return (Map)_page.findResource("ExtGState", name); }

/**
 * Returns the pdf Font dictionary for a given name (like "/f1").  You
 * can use the FontFactory to get interesting objects from the dictionary.
 */  
public Map getFontDictForAlias(String alias) { return (Map)_page.findResource("Font", alias); }

/**
 * Like above, but for XObjects. XObjects can be Forms or Images.
 * If the dictionary represents an Image, this routine calls the ImageFactory to create a java.awt.Image.
 * If it's a Form XObject, the object returned will be a PDFForm.
*/ 
public Object getXObject(String pdfName)
{
    PDFStream xobjStream = (PDFStream)_page.findResource("XObject",pdfName);
    
    if (xobjStream != null) {
        Map xobjDict = xobjStream.getDict();
        
        // Check to see if we went through this already
        Object cached = xobjDict.get("_rbcached_xobject_");
        if(cached != null)
            return cached;
        
        String type = (String)xobjDict.get("Subtype");
        if (type==null)
            throw new PDFException("Unknown xobject type");
        
        // Image XObject - pass it to the ImageFactory
        if (type.equals("/Image")) {
            // First check for a colorspace entry for the image, and create an awt colorspace.
            Object space = _page.getXRefObj(xobjDict.get("ColorSpace"));
            ColorSpace imageCSpace = space==null ? null : PDFColorSpace.getColorspace(space, _pfile, _page);
            cached = PDFImage.getImage(xobjStream, imageCSpace, _pfile);
        }
        
        // A PDFForm just saves the stream away for later parsing
        else if (type.equals("/Form"))
            cached = new PDFForm(xobjStream);
        
        if (cached != null) {
            xobjDict.put("_rbcached_xobject_", cached);
            return cached;
        }
    }
    
    // Complain and return null
    System.err.println("Unable to get xobject named \""+pdfName+"\"");
    return null;
}

/**
 * Creates a new pattern object for the resource name
 */
public PDFPattern getPattern(String pdfName)
{
    Object pat = _page.findResource("Pattern", pdfName);
    PDFPattern patobj = PDFPattern.getInstance(pat, _pfile);
    
    // Resolve the colorspace.
    if (patobj instanceof PDFPatterns.Shading) {
        Map shmap = (Map)_page.getXRefObj(((Map)pat).get("Shading"));
        Object csobj = _page.getXRefObj(shmap.get("ColorSpace"));
        if(csobj!=null)
            ((PDFPatterns.Shading)patobj).setColorSpace(PDFColorSpace.getColorspace(csobj, _pfile, _page));
    }
    
    return patobj;
}

/**
 * Creates a new shadingPattern for the resource name.  Used by the shading operator.
 */
public PDFPatterns.Shading getShading(String pdfName)
{
    Map pat = (Map)_page.findResource("Shading", pdfName);
    PDFPatterns.Shading patobj = PDFPatterns.Shading.getInstance(pat, _pfile);
    
    // Resolve the colorspace.
    Object csobj = _page.getXRefObj(pat.get("ColorSpace"));
    if(csobj!=null)
        patobj.setColorSpace(PDFColorSpace.getColorspace(csobj, _pfile, _page));
    return patobj;
}

/**
 * Returns the painter.
 */
public Painter getPainter()  { return _pntr; }

/**
 * Returns the image, if painter was created for image.
 */
public Image getImage()  { return _image; }
  
/**
 * Return graphics.
 */
public Graphics2D getGraphics()  { return _gfx; }

/**
 * Returns pdf image.
 */
public BufferedImage getBufferedImage()  { return (BufferedImage)_image.getNative(); }

/**
 * Stroke the current path with the current miter limit, color, etc.
 */
public void strokePath(PDFGState aGS, GeneralPath aShape)
{
    AffineTransform old = establishTransform(aGS);
    if(aGS.scomposite != null) _gfx.setComposite(aGS.scomposite);
    _pntr.setColor(aGS.scolor); _gfx.setStroke(aGS.lineStroke); _gfx.draw(aShape);
    _gfx.setTransform(old);
}

/**
 * Fill the current path using the fill params in the gstate
 */
public void fillPath(PDFGState aGS, GeneralPath aShape)
{
    AffineTransform old = establishTransform(aGS);
    if(aGS.composite != null) _gfx.setComposite(aGS.composite);
    _pntr.setPaint(aGS.color); _gfx.fill(aShape);
    _gfx.setTransform(old);
}    

/**
 * Establishes an image transform and tells markup engine to draw the image
 */
public void drawImage(java.awt.Image im) 
{
    // In pdf, an image is defined as occupying the unit square no matter how many pixels wide or high
    // it is (image space goes from {0,0} - {1,1}). A pdf producer will scale up ctm to get whatever size they want.
    // We remove pixelsWide & pixelsHigh from scale since awt image space goes from {0,0} - {width,height}
    // Also note that in pdf image space, {0,0} is at the upper-, left.  Since this is flipped from all the other
    // primatives, we also include a flip here for consistency.
    int pixWide = im.getWidth(null);
    int pixHigh = im.getHeight(null);
    if(pixWide<0 || pixHigh<0)
        throw new PDFException("Error loading image"); //This shouldn't happen

    AffineTransform ixform = new AffineTransform(1.0/pixWide, 0.0, 0.0, -1.0/pixHigh, 0, 1.0);
    drawImage(_gstate, im, ixform);
}

/**
 * Draw an image.
 */
public void drawImage(PDFGState aGS, java.awt.Image anImg, AffineTransform ixform) 
{
    AffineTransform old = establishTransform(aGS);
    if(aGS.composite!=null)
        _gfx.setComposite(aGS.composite);
    
    // normal image case - If image drawing throws exception, try workaround
    _gfx.drawImage(anImg, ixform, null); // If fails with ImagingOpException, see RM14 sun_bug_4723021_workaround
    
    // restore transform
    _gfx.setTransform(old);
}

/**
 * Draw some text at the current text position.  
 */
public void showText(PDFGState aGS, GlyphVector v)
{
    AffineTransform old = establishTransform(aGS);
    
    // TODO: eventually need check the font render mode in the gstate
    if (aGS.composite != null)
        _gfx.setComposite(aGS.composite);
    
    _pntr.setPaint(aGS.color);
    _gfx.drawGlyphVector(v,0,0);
    _gfx.setTransform(old);
}

/**
 * Pushes a copy of the current gstate onto the gstate stack and returns the new gstate.
 */
public PDFGState gsave()
{
    PDFGState newstate = (PDFGState)_gstate.clone();
    _gstates.push(newstate);
    return _gstate = newstate;
}

/**
 * Pops the current gstate from the gstate stack and returns the restored gstate.
 */
public PDFGState grestore()
{
    // also calls into the markup handler if the change in gstate will cause the clipping path to change.
    GeneralPath currentclip = _gstates.pop().clip;
    PDFGState gs = _gstate = _gstates.peek();
    
    //
    GeneralPath savedclip = gs.clip;
     if(currentclip!=null && savedclip!=null) {
        if (!currentclip.equals(savedclip))
            clipChanged(gs);
     }
     else if(currentclip!=savedclip)
         clipChanged(gs);
     return _gstate = gs;
}

/**
 * Reset the clip
 */
void clipChanged(PDFGState g)
{
    // apply original clip, if any. A null clip in the gstate resets the clip to whatever it was originally
    if(_initialClip!=null || g.clip==null)
        _gfx.setClip(_initialClip);
    
     // Clip is defined in page space, so apply only the page->awtspace transform
     if (g.clip != null) {
        AffineTransform old = establishTransform(null);
        if(_initialClip == null) _gfx.setClip(g.clip);
        else _gfx.clip(g.clip);
        _gfx.setTransform(old);
    }
}

/**
 * Establish transform.
 */
AffineTransform establishTransform(PDFGState aGS)
{
    AffineTransform old = _gfx.getTransform();
    _gfx.setTransform(_flipXForm);
    if(aGS!=null) _gfx.transform(aGS.trans);
    return old;
}

/**
 * Called when the clipping path changes. The clip in the gstate is defined to be in page space.
 * Whenever clip is changed, we calculate new clip, which can be intersected with the old clip, and save it in gstate.
 * NB. This routine modifies the path that's passed in to it.
 */
public void establishClip(GeneralPath newclip, boolean intersect)
{
    // transform the new clip path into page space
    newclip.transform(_gstate.trans);
    
    // If we're adding a clip to an existing clip, calculate the intersection
    if(intersect && _gstate.clip!=null) {
        Area clip_area = new Area(_gstate.clip);
        Area newclip_area = new Area(newclip);
        clip_area.intersect(newclip_area);
        newclip = new GeneralPath(clip_area);
    }
    _gstate.clip = newclip;
    
    // notify the markup handler of the new clip
    clipChanged(_gstate);
}

}