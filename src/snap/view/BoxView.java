/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.view;
import snap.gfx.*;
import snap.util.*;

/**
 * A View that holds another view.
 */
public class BoxView extends ChildView {

    // The spacing between nodes
    double        _spacing;
    
    // Whether to fill width, height
    boolean       _fillWidth, _fillHeight;
    
    // Constants for properties
    public static final String FillWidth_Prop = "FillWidth";
    public static final String FillHeight_Prop = "FillHeight";
    
/**
 * Creates a new Box.
 */
public BoxView()  { }

/**
 * Creates a new Box for content.
 */
public BoxView(View aContent)  { setContent(aContent); }

/**
 * Creates a new Box for content with FillWidth, FillHeight params.
 */
public BoxView(View aContent, boolean isFillWidth, boolean isFillHeight)
{
    setContent(aContent); setFillWidth(isFillWidth); setFillHeight(isFillHeight);
}

/**
 * Returns the box content.
 */
public View getContent()  { return getGuestCount()>0? getGuest(0) : null; }

/**
 * Sets the box content.
 */
public void setContent(View aView)
{
    if(aView==getContent()) return;
    removeGuests();
    if(aView!=null) addGuest(aView);
}

/**
 * Returns the spacing.
 */
public double getSpacing()  { return _spacing; }

/**
 * Sets the spacing.
 */
public void setSpacing(double aValue)
{
    if(aValue==_spacing) return;
    firePropChange(Spacing_Prop, _spacing, _spacing = aValue);
    relayout(); relayoutParent();
}

/**
 * Returns whether children will be resized to fill width.
 */
public boolean isFillWidth()  { return _fillWidth; }

/**
 * Sets whether children will be resized to fill width.
 */
public void setFillWidth(boolean aValue)
{
    _fillWidth = aValue;
    repaint(); relayoutParent();
}

/**
 * Returns whether children will be resized to fill height.
 */
public boolean isFillHeight()  { return _fillHeight; }

/**
 * Sets whether children will be resized to fill height.
 */
public void setFillHeight(boolean aValue)
{
    _fillHeight = aValue;
    repaint(); relayoutParent();
}

/**
 * Override to change to CENTER.
 */    
public Pos getDefaultAlign()  { return Pos.CENTER; }

/**
 * Override.
 */
protected double getPrefWidthImpl(double aH)
{
    //return getPrefWidth(this, getContent(), aH);
    if(isHorizontal())
        return RowView.getPrefWidth(this, null, getSpacing(), aH);
    return ColView.getPrefWidth(this, null, aH);
}

/**
 * Override.
 */
protected double getPrefHeightImpl(double aW)
{
    //return getPrefHeight(this, getContent(), aW);
    if(isHorizontal())
        return RowView.getPrefHeight(this, null, aW);
    return ColView.getPrefHeight(this, null, _spacing, aW);
}

/**
 * Override.
 */
protected void layoutImpl()
{
    //layout(this, getContent(), null, _fillWidth, _fillHeight);
    if(isHorizontal())
        RowView.layout(this, null, null, isFillWidth(), isFillHeight(), getSpacing());
    else ColView.layout(this, null, null, isFillWidth(), isFillHeight(), getSpacing());
}

/**
 * XML archival.
 */
public XMLElement toXMLView(XMLArchiver anArchiver)
{
    // Archive basic view attributes
    XMLElement e = super.toXMLView(anArchiver);
    
    // Archive Spacing, FillWidth, FillHeight
    if(getSpacing()!=0) e.add(Spacing_Prop, getSpacing());
    if(isFillWidth()) e.add(FillWidth_Prop, true);
    if(isFillHeight()) e.add(FillHeight_Prop, true);
    return e;
}

/**
 * XML unarchival.
 */
public void fromXMLView(XMLArchiver anArchiver, XMLElement anElement)
{
    // Unarchive basic view attributes
    super.fromXMLView(anArchiver, anElement);

    // Unarchive Spacing, FillWidth, FillHeight
    if(anElement.hasAttribute(Spacing_Prop))setSpacing(anElement.getAttributeFloatValue(Spacing_Prop));
    if(anElement.hasAttribute(FillWidth_Prop)) setFillWidth(anElement.getAttributeBoolValue(FillWidth_Prop));
    if(anElement.hasAttribute(FillHeight_Prop)) setFillHeight(anElement.getAttributeBoolValue(FillHeight_Prop));
}

/**
 * Returns preferred width of layout.
 */
public static double getPrefWidth(ParentView aPar, View aChild, double aH)
{
    // Get insets (just return if empty)
    Insets ins = aPar.getInsetsAll(); if(aChild==null) return ins.getWidth();
    
    // Get height without insets, get best width and return
    double h = aH>=0? (aH - ins.getHeight()) : aH;
    double bw = aChild.getBestWidth(h);
    return bw + ins.getWidth();
}

/**
 * Returns preferred height of layout.
 */
public static double getPrefHeight(ParentView aPar, View aChild, double aW)
{
    // Get insets (just return if empty)
    Insets ins = aPar.getInsetsAll(); if(aChild==null) return ins.getHeight();
    
    // Get width without insets, get best height and return
    double w = aW>=0? (aW - ins.getWidth()) : aW;
    double bh = aChild.getBestHeight(w);
    return bh + ins.getHeight();
}

/**
 * Performs Box layout for given parent, child and fill width/height.
 */
public static void layout(ParentView aPar, View aChild, Insets theIns, boolean isFillWidth, boolean isFillHeight)
{
    // If no child, just return
    if(aChild==null) return;
    
    // Get parent bounds for insets (just return if empty)
    Insets ins = theIns!=null? theIns : aPar.getInsetsAll();
    double px = ins.left, py = ins.top;
    double pw = aPar.getWidth() - px - ins.right; if(pw<0) pw = 0; if(pw<=0) return;
    double ph = aPar.getHeight() - py - ins.bottom; if(ph<0) ph = 0; if(ph<=0) return;
    
    // Get content width/height
    double cw = isFillWidth || aChild.isGrowWidth()? pw : aChild.getBestWidth(-1); if(cw>pw) cw = pw;
    double ch = isFillHeight? ph : aChild.getBestHeight(cw);
    
    // Handle normal layout
    double dx = pw - cw, dy = ph - ch;
    double sx = aChild.getLeanX()!=null? ViewUtils.getLeanX(aChild) : ViewUtils.getAlignX(aPar);
    double sy = aChild.getLeanY()!=null? ViewUtils.getLeanY(aChild) : ViewUtils.getAlignY(aPar);
    aChild.setBounds(px+dx*sx, py+dy*sy, cw, ch);
}
    
}