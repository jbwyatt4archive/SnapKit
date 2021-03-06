/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.view;
import snap.gfx.Insets;
import snap.gfx.Pos;
import snap.util.*;

/**
 * A View subclass to manage subviews along edges (top, bottom, left, right) and center.
 */
public class BorderView extends ParentView {
    
    // The panes
    View              _top, _center, _bottom, _left, _right;
    
    // Whether to fill center
    boolean           _fillCenter = true;
    
/**
 * Returns the center node.
 */
public View getCenter()  { return _center; }

/**
 * Sets the center node.
 */
public void setCenter(View aView)
{
    View old = getCenter(); if(aView==old) return;
    if(old!=null) removeChild(old); if(aView!=null) addChild(aView);
    _center = aView;
    firePropChange("Center", old, aView);
}

/**
 * Returns the top node.
 */
public View getTop()  { return _top; }

/**
 * Sets the top node.
 */
public void setTop(View aView)
{
    View old = getTop(); if(aView==old) return;
    if(old!=null) removeChild(old); if(aView!=null) addChild(aView);
    _top = aView;
    firePropChange("Top", old, aView);
}

/**
 * Returns the bottom node.
 */
public View getBottom()  { return _bottom; }

/**
 * Sets the bottom node.
 */
public void setBottom(View aView)
{
    View old = getBottom(); if(aView==old) return;
    if(old!=null) removeChild(old); if(aView!=null) addChild(aView);
    _bottom = aView;
    firePropChange("Bottom", old, aView);
}

/**
 * Returns the left node.
 */
public View getLeft()  { return _left; }

/**
 * Sets the left node.
 */
public void setLeft(View aView)
{
    View old = getLeft(); if(aView==old) return;
    if(old!=null) removeChild(old); if(aView!=null) addChild(aView);
    _left = aView;
    firePropChange("Left", old, aView);
}

/**
 * Returns the right node.
 */
public View getRight()  { return _right; }

/**
 * Sets the right node.
 */
public void setRight(View aView)
{
    View old = getRight(); if(aView==old) return;
    if(old!=null) removeChild(old); if(aView!=null) addChild(aView);
    _right = aView;
    firePropChange("Right", old, aView);
}

/**
 * Returns whether layout should fill center when bigger than pref size.
 */
public boolean isFillCenter()  { return _fillCenter; }

/**
 * Sets whether to fill center when bigger than pref size.
 */
public void setFillCenter(boolean aValue)  { _fillCenter = aValue; }
    
/**
 * Returns the default alignment.
 */    
public Pos getDefaultAlign()  { return Pos.CENTER; }

/**
 * Returns the preferred width.
 */
protected double getPrefWidthImpl(double aH)  { return getPrefWidth(this, _center, _top, _right, _bottom, _left, aH); }

/**
 * Returns the preferred height.
 */
protected double getPrefHeightImpl(double aW)  { return getPrefHeight(this, _center, _top, _right, _bottom, _left, aW); }

/**
 * Layout children.
 */
protected void layoutImpl()  { layout(this, _center, _top, _right, _bottom, _left); }
    
/**
 * XML archival of children.
 */
protected void toXMLChildren(XMLArchiver anArchiver, XMLElement anElement)
{
    // Archive Top, Left, Center, Bottom, Right
    if(getTop()!=null) { XMLElement top = new XMLElement("Top"); anElement.add(top);
        top.add(anArchiver.toXML(getTop(), this)); }
    if(getLeft()!=null) { XMLElement lft = new XMLElement("Left"); anElement.add(lft);
        lft.add(anArchiver.toXML(getLeft(), this)); }
    if(getCenter()!=null) { XMLElement ctr = new XMLElement("Center"); anElement.add(ctr);
        ctr.add(anArchiver.toXML(getCenter(), this)); }
    if(getBottom()!=null) { XMLElement btm = new XMLElement("Bottom"); anElement.add(btm);
        btm.add(anArchiver.toXML(getBottom(), this)); }
    if(getRight()!=null) { XMLElement rgt = new XMLElement("Right"); anElement.add(rgt);
        rgt.add(anArchiver.toXML(getRight(), this)); }
}

/**
 * XML unarchival for shape children.
 */
protected void fromXMLChildren(XMLArchiver anArchiver, XMLElement anElement)
{
    // Unarchive Top
    XMLElement top = anElement.get("Top"); top = top!=null && top.getElementCount()>0? top.getElement(0) : null;
    Object topView = top!=null? anArchiver.fromXML(top, this) : null;
    if(topView instanceof View) setTop((View)topView);
    
    // Unarchive Left
    XMLElement lft = anElement.get("Left"); lft = lft!=null && lft.getElementCount()>0? lft.getElement(0) : null;
    Object lftView = lft!=null? anArchiver.fromXML(lft, this) : null;
    if(lftView instanceof View) setLeft((View)lftView);
    
    // Unarchive Center
    XMLElement ctr = anElement.get("Center"); ctr = ctr!=null && ctr.getElementCount()>0? ctr.getElement(0) : null;
    Object ctrView = ctr!=null? anArchiver.fromXML(ctr, this) : null;
    if(ctrView instanceof View) setCenter((View)ctrView);
    
    // Unarchive Bottom
    XMLElement btm = anElement.get("Bottom"); btm = btm!=null && btm.getElementCount()>0? btm.getElement(0) : null;
    Object btmView = btm!=null? anArchiver.fromXML(btm, this) : null;
    if(btmView instanceof View) setBottom((View)btmView);
    
    // Unarchive Right
    XMLElement rgt = anElement.get("Right"); rgt = rgt!=null && rgt.getElementCount()>0? rgt.getElement(0) : null;
    Object rgtView = rgt!=null? anArchiver.fromXML(rgt, this) : null;
    if(rgtView instanceof View) setRight((View)rgtView);
}

/** Returns array of column kids (top, row-proxy, bottom). */
private static View[] getColKids(View aCtr, View aTp, View aRt, View aBtm, View aLft)
{
    _rproxy.relayoutParent(); _cproxy.relayoutParent();
    _cproxy._kid = aCtr;
    _rproxy._kids = asArray(aLft, aCtr!=null? _cproxy : null, aRt);
    return asArray(aTp, _rproxy, aBtm);
}
    
/** RowProxy to model left, center, right of BorderView. */
private static RowProxy _rproxy = new RowProxy();
private static class RowProxy extends ParentView {
    public RowProxy() { setGrowWidth(true); setGrowHeight(true); } View _kids[];
    protected double getPrefWidthImpl(double aH)  { return RowView.getPrefWidth(this, _kids, 0, aH); }
    protected double getPrefHeightImpl(double aW)  { return RowView.getPrefHeight(this, _kids, aW); }
}

/** CenterProxy to model center as always grow width/height. */
private static CenterProxy _cproxy = new CenterProxy();
private static class CenterProxy extends ParentView {
    public CenterProxy() { setGrowWidth(true); setGrowHeight(true); } View _kid;
    protected double getPrefWidthImpl(double aH)  { return BoxView.getPrefWidth(this, _kid, aH); }
    protected double getPrefHeightImpl(double aW)  { return BoxView.getPrefHeight(this, _kid, aW); }
}
    
/** Returns array of non-null views from given view args. */
private static View[] asArray(View ... theViews)
{
    int i = 0, len = 0; for(View n : theViews) if(n!=null) len++;
    View views[] = new View[len]; for(View n : theViews) if(n!=null) views[i++] = n;
    return views;
}

/**
 * Returns preferred width of given parent with given children.
 */
public static double getPrefWidth(ParentView aPar, View aCtr, View aTp, View aRt, View aBtm, View aLft, double aH)
{
    return ColView.getPrefWidth(aPar, getColKids(aCtr, aTp, aRt, aBtm, aLft), aH);
}

/**
 * Returns the preferred height.
 */
public static double getPrefHeight(ParentView aPar, View aCtr, View aTp, View aRt, View aBtm, View aLft, double aW)
{
    return ColView.getPrefHeight(aPar, getColKids(aCtr, aTp, aRt, aBtm, aLft), 0, aW);
}

/**
 * Layout children.
 */
public static void layout(ParentView aPar, View aCtr, View aTp, View aRt, View aBtm, View aLft)
{
    // Do vertical layout (top, horiz-proxy, bottom)
    View vkids[] = getColKids(aCtr, aTp, aRt, aBtm, aLft);
    ColView.layout(aPar, vkids, null, true, 0);
    
    // Do horizontal layout (left, center-proxy, bottom)
    double right = aPar.getWidth() - _rproxy.getX() - _rproxy.getWidth();
    double bottom = aPar.getHeight() - _rproxy.getY() - _rproxy.getHeight();
    Insets hins = new Insets(_rproxy.getY(), right, bottom, _rproxy.getX());
    RowView.layout(aPar, _rproxy._kids, hins, true, 0);
    
    // Do center layout
    if(aCtr==null) return;
    aCtr.setBounds(_cproxy.getX(), _cproxy.getY(), _cproxy.getWidth(), _cproxy.getHeight());
}
    
}