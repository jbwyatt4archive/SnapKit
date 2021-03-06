/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.view;

/**
 * A ListView subclass to act as a BrowserView column.
 */
public class BrowserCol <T> extends ListView <T> {
    
    // The Browser
    BrowserView <T>  _browser;

    // The index of this browser column
    int              _index;
    
/**
 * Creates new BrowserCol for given BrowserView.
 */
public BrowserCol(BrowserView aBrsr)
{
    // Set browser
    _browser = aBrsr;
    setFocusWhenPressed(aBrsr.isFocusWhenPressed());
    
    // Update some attribues
    setRowHeight(_browser.getRowHeight());
    
    // Configure ScrollView
    ScrollView scroll = getScrollView();
    scroll.setShowHBar(false); scroll.setShowVBar(true);
}

/**
 * Returns the browser.
 */
public BrowserView <T> getBrowser()  { return _browser; }

/**
 * Returns the column index.
 */
public int getIndex()  { return _index; }

/**
 * Override to forward to browser.
 */
protected void configureCell(ListCell <T> aCell)
{
    _browser.configureBrowserCell(this, aCell);
}

/**
 * Override to suppress.
 */
protected void fireActionEvent(ViewEvent anEvent)
{
    _browser.setSelColIndex(_index);
    _browser.scrollSelToVisible();
    _browser.fireActionEvent(anEvent);
}

/**
 * Override to request size of Browser/VisColCount (Should really be set in BrowserView.setWidth).
 */
protected double getPrefWidthImpl(double aH)
{
    double width = _browser.getScrollView().getScroller().getWidth();
    double pw = width/_browser.getPrefColCount();
    return pw;
}
    
}