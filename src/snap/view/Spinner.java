/*
 * Copyright (c) 2010, ReportMill Software. All rights reserved.
 */
package snap.view;
import java.text.DecimalFormat;
import snap.gfx.*;
import snap.util.*;

/**
 * A control to show a text value with convenient up/down buttons.
 */
public class Spinner <T> extends ParentView {

    // The current value of spinner
    T                _value;
    
    // The step size of spinner
    double           _step = 1;
    
    // The min/max of spinner
    double           _min = Integer.MIN_VALUE, _max = Integer.MAX_VALUE;
    
    // The text field
    TextField        _text;
    
    // The up down buttons
    ArrowView        _arrowView;
    
    // Constants for properties
    public static final String Min_Prop = "Min";
    public static final String Max_Prop = "Max";
    public static final String Step_Prop = "StepSize";
    public static final String Value_Prop = "Value";
    
    // Constant for Button width
    static final int BTN_W = 14;
    static DecimalFormat _fmt = new DecimalFormat("#.##");

/**
 * Creates a new Spinner.
 */
public Spinner()
{
    // Enable Action event
    enableEvents(Action);
    
    // Create/configure Text
    _text = new TextField();
    _text.addEventHandler(e -> textChanged(), Action);
    addChild(_text);
    
    // Create/configure arrow view
    _arrowView = new ArrowView();
    _arrowView.addEventHandler(e -> arrowViewDidFireAction(), Action);
    addChild(_arrowView);
}

/**
 * Returns the text field.
 */
public TextField getTextField()  { return _text; }

/**
 * Returns the spinner value.
 */
public T getValue()  { return _value; }

/**
 * Sets the spinner value.
 */
public void setValue(T aValue)
{
    if(SnapUtils.equals(aValue,getValue())) return;
    firePropChange(Value_Prop, _value, _value = aValue);
    _text.setText(getText());
}

/**
 * Returns the text for value.
 */
public String getText()
{
    if(_value==null) return "";
    if(_value instanceof Double) return _fmt.format((Double)_value);
    if(_value instanceof Float) return _fmt.format((Float)_value);
    return _value.toString();
}

/**
 * Returns the value type.
 */
public Class getValueClass()
{
    Object val = getValue();
    if(val instanceof Integer) return int.class;
    else if(val instanceof Long) return long.class;
    else if(val instanceof Byte) return byte.class;
    else if(val instanceof Short) return short.class;
    else if(val instanceof Float) return float.class;
    else if(val instanceof Double) return double.class;
    return val!=null? val.getClass() : null;
}

/**
 * Returns the spinner min.
 */
public double getMin()  { return _min; }

/**
 * Sets the spinner min.
 */
public void setMin(double aValue)
{
    firePropChange(Min_Prop, _min, _min = aValue);
}

/**
 * Returns the spinner max.
 */
public double getMax()  { return _max; }

/**
 * Sets the spinner min.
 */
public void setMax(double aValue)
{
    firePropChange(Max_Prop, _max, _max = aValue);
}

/**
 * Returns the spinner step size.
 */
public double getStep()  { return _step; }

/**
 * Sets the spinner step size.
 */
public void setStep(double aValue)
{
    firePropChange(Step_Prop, _step, _step = aValue);
}

/**
 * Called when ArrowView fires action.
 */
protected void arrowViewDidFireAction()
{
    if(_arrowView.isUp()) increment();
    else if(_arrowView.isDown()) decrement();
}

/**
 * Increments spinner.
 */
public void increment()
{
    Object val = getValue();
    if(val instanceof Integer) { int i1 = (Integer)val, i2 = (int)Math.round(getStep()); val = i1 + i2; }
    else if(val instanceof Long) { long i1 = (long)val, i2 = (long)getStep(); val = i1 + i2; }
    else if(val instanceof Byte) { byte i1 = (byte)val, i2 = (byte)getStep(); val = i1 + i2; }
    else if(val instanceof Short) { short i1 = (short)val, i2 = (short)getStep(); val = i1 + i2; }
    else if(val instanceof Float) { float i1 = (Float)val, i2 = (float)getStep(); val = i1 + i2; }
    else if(val instanceof Double) { double i1 = (Double)val, i2 = getStep(); val = i1 + i2; }
    else { System.err.println("Spinner: Unsuported value/step type: " + getValueClass()); return; }
    
    // If new value, set and fire action
    if(SnapUtils.equals(val, getValue())) return;
    setValue((T)val);
    fireActionEvent(null);
}

/**
 * Increments spinner.
 */
public void decrement()
{
    Object val = getValue();
    if(val instanceof Integer) { int i1 = (Integer)val, i2 = (int)Math.round(getStep()); val = i1 - i2; }
    else if(val instanceof Long) { long i1 = (long)val, i2 = (long)getStep(); val = i1 - i2; }
    else if(val instanceof Byte) { byte i1 = (byte)val, i2 = (byte)getStep(); val = i1 - i2; }
    else if(val instanceof Short) { short i1 = (short)val, i2 = (short)getStep(); val = i1 - i2; }
    else if(val instanceof Float) { float i1 = (Float)val, i2 = (float)getStep(); val = i1 - i2; }
    else if(val instanceof Double) { double i1 = (Double)val, i2 = getStep(); val = i1 - i2; }
    else { System.err.println("Spinner: Unsuported value/step type: " + getValueClass()); return; }
    
    // If new value, set and fire action
    if(SnapUtils.equals(val, getValue())) return;
    setValue((T)val);
    fireActionEvent(null);
}

/**
 * Called when text changes.
 */
public void textChanged()
{
    // Get Text value based as same current type of spinner
    Object oval = getValue(), nval = null; String str = _text.getText();
    if(oval instanceof Integer) nval = SnapUtils.intValue(str);
    else if(oval instanceof Long) nval = SnapUtils.longValue(str);
    else if(oval instanceof Byte) nval = (byte)SnapUtils.intValue(str);
    else if(oval instanceof Short) nval = (short)SnapUtils.intValue(str);
    else if(oval instanceof Float) nval = SnapUtils.floatValue(str);
    else if(oval instanceof Double) nval = SnapUtils.doubleValue(str);
    else { System.err.println("Spinner: Unsuported value type: " + getValueClass()); return; }
    
    setValue((T)nval);
    fireActionEvent(null);
}

/**
 * Returns the preferred width.
 */
protected double getPrefWidthImpl(double aH)  { return _text.getPrefWidth()+BTN_W; }

/**
 * Returns the preferred height.
 */
protected double getPrefHeightImpl(double aW)  { return _text.getPrefHeight(); }

/**
 * Layout children.
 */
protected void layoutImpl()
{
    double w = getWidth(), h = getHeight();
    Insets ins = getInsetsAll(); double px = ins.left, py = ins.top, pw = w - px - ins.right, ph = h - py - ins.bottom;
    
    // Layout text in bounds minus Button width - 2 (spacing)
    double tw = pw - BTN_W - 2;
    _text.setBounds(px, py, tw, ph);
    
    // Layout ArrowView
    double bh = (ph/2-1); // Math.round()?
    double bx = px + tw + 2, by = py + 1;
    _arrowView.setBounds(bx, by, BTN_W, bh*2);
}

/**
 * Override to forward to StringView.
 */
public void setAlign(Pos aPos)
{
    super.setAlign(aPos);
    _text.setAlign(aPos.getHPos());
}

/**
 * Returns the value for given key.
 */
public Object getValue(String aPropName)
{
    if(aPropName.equals("Value")) return getValue();
    return super.getValue(aPropName);
}

/**
 * Sets the value for given key.
 */
public void setValue(String aPropName, Object aValue)
{
    if(aPropName.equals("Value")) setValue((T)aValue);
    else super.setValue(aPropName, aValue);
}

/**
 * XML archival.
 */
public XMLElement toXMLView(XMLArchiver anArchiver)
{
    // Archive basic view attributes
    XMLElement e = super.toXMLView(anArchiver);
    
    // Archive Min, Max, Step, Value
    if(getMin()!=Integer.MIN_VALUE) e.add(Min_Prop, getMin());
    if(getMax()!=Integer.MAX_VALUE) e.add(Max_Prop, getMax());
    if(getStep()!=1) e.add(Step_Prop, getStep());
    if(getValue()!=null) e.add(Value_Prop, getValue());
    
    // Return element
    return e;
}

/**
 * XML unarchival.
 */
public void fromXMLView(XMLArchiver anArchiver, XMLElement anElement)
{
    // Unarchive basic view attributes
    super.fromXMLView(anArchiver, anElement);
    
    // Unarchive Min, Max, Step, Value
    if(anElement.hasAttribute(Min_Prop)) setMin(anElement.getAttributeFloatValue(Min_Prop));
    if(anElement.hasAttribute(Max_Prop)) setMax(anElement.getAttributeFloatValue(Max_Prop));
    if(anElement.hasAttribute(Step_Prop)) setStep(anElement.getAttributeFloatValue(Step_Prop));
    if(anElement.hasAttribute(Value_Prop)) setValue((T)(Integer)anElement.getAttributeIntValue(Value_Prop));
}

}