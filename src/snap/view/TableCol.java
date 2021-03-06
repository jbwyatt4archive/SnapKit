/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.view;
import java.util.List;
import java.util.function.Consumer;
import snap.util.*;

/**
 * Represents a Table Column.
 */
public class TableCol <T> extends ListArea <T> {
    
    // The Table
    TableView          _table;

    // The header value
    Label              _header = new Label();
    
    // Whether is resizable
    boolean            _resizable;

    // Constants for properties
    public static final String HeaderText_Prop = "HeaderText";
    
/**
 * Creates a new TableCol.
 */
public TableCol()
{
    _header.setPadding(4,4,4,4);
    setFocusable(false); setFocusWhenPressed(false);
}

/**
 * Returns the table.
 */
public TableView getTable()  { return _table; }

/**
 * Returns the header label.
 */
public Label getHeader()  { return _header; }

/**
 * Returns the header text.
 */
public String getHeaderText()  { return _header.getText(); }

/**
 * Sets the header text.
 */
public void setHeaderText(String aValue)  { _header.setText(aValue); }

/**
 * Returns whether resizable.
 */
public boolean isResizable()  { return _resizable; }

/**
 * Sets the resizable.
 */
public void setResizable(boolean aValue)
{
    firePropChange("Resizable", _resizable, _resizable = aValue);
}

/**
 * Override to get row height from table.
 */
public double getRowHeight()  { return getTable().getRowHeight(); }

/**
 * Override to get row height from table.
 */
protected double getRowHeightSuper()  { return super.getRowHeight(); }

/**
 * Returns the column index.
 */
public int getColIndex()  { return ArrayUtils.indexOfId(getTable().getCols(), this); }

/**
 * Override to give control to table.
 */
protected void configureCell(ListCell <T> aCell)
{
    getTable().configureCell(this, aCell);
}

/**
 * Override to return table cell configure if one isn't set for col.
 */
public Consumer <ListCell<T>> getCellConfigure()
{
    Consumer <ListCell<T>> cconf = super.getCellConfigure();
    return cconf!=null? cconf : getTable().getCellConfigure();
}

/**
 * Override to suppress setting items in pick list (already done by TableView).
 */
public void setItems(List <T> theItems)  { itemsChanged(); }

/**
 * Override to set Table.SelCol.
 */
protected void processEvent(ViewEvent anEvent)
{
    // Handle MousePress: Forward to table
    if(anEvent.isMousePress())
        getTable().colDidMousePress(this, anEvent);
        
    // Do normal version
    super.processEvent(anEvent);
}

/**
 * Override to account for header (if showing).
 */
protected double getPrefWidthImpl(double aH)
{
    double pw = super.getPrefWidthImpl(aH);
    TableView table = getTable(); if(table!=null && table.isShowHeader())
        pw = Math.max(getHeader().getPrefWidth(aH), pw);
    return pw;
}

/**
 * Override to forward to table.
 */
protected View getFocusNext(View aChild)  { return getTable().getFocusNext(); }

/**
 * Override to forward to table.
 */
protected View getFocusPrev(View aChild)  { return getTable().getFocusPrev(); }

/**
 * XML archival - table columns.
 */
public XMLElement toXMLView(XMLArchiver anArchiver)
{
    // Create xml for column
    XMLElement e = super.toXMLView(anArchiver);
    
    // Archive Header, Resizable
    if(getHeaderText()!=null) e.add("Header", getHeaderText());
    if(isResizable()) e.add("Resizable", false);
    
    // Return column xml
    return e;
}
    
/**
 * XML unarchival - table columns.
 */
public void fromXMLView(XMLArchiver anArchiver, XMLElement anElement)
{
    // Do normal version
    super.fromXMLView(anArchiver, anElement);
    
    // Unarchive Header, Resizable
    if(anElement.hasAttribute("Header")) setHeaderText(anElement.getAttributeValue("Header"));
    if(anElement.hasAttribute("Resizable")) setResizable(anElement.getAttributeBoolValue("Resizable"));
}

}