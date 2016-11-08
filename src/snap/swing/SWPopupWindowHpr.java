package snap.swing;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import snap.gfx.*;
import snap.view.*;

/**
 * A ViewHelper for JPopupMenu.
 */
public class SWPopupWindowHpr  <T extends SWPopupWindowHpr.SnapPopupMenu> extends ViewHelper <T> {
    
    // PopupMenuListener
    PopupMenuListener      _pl;

/** Creates the native. */
protected T createNative()  { return (T)new SnapPopupMenu(); }

/**
 * Shows the popup at given point relative to given node.
 */
public void show(View aView, double aX, double aY)
{
    // Add PopupNode.RootView to JPopupMenu
    PopupWindow pnode = getView(PopupWindow.class);
    RootView rview = pnode.getRootView();
    JComponent rviewNtv = rview.getNative(JComponent.class);
    get().add(rviewNtv);
    
    // Get proper bounds for given view and x/y (and available screen space at that point)
    Rect bnds = getPrefBounds(aView, aX, aY); bnds.snap();
    int x = (int)bnds.x, y = (int)bnds.y, bw = (int)bnds.width, bh = (int)bnds.height;
    
    // Set popup size
    get().setPopupSize(bw,bh); //get().setPreferredSize(new java.awt.Dimension(bw,bh)); get().pack();
    get().setBorder(BorderFactory.createEmptyBorder()); // MacOS adds 4pt border to top/bottom
    
    // Get ParentPopup and suppress close if found
    RootView viewRoot = aView!=null? aView.getRootView() : null;
    JComponent viewRootNtv = viewRoot!=null? viewRoot.getNative(JComponent.class) : null;
    SnapPopupMenu pp = viewRootNtv!=null? SwingUtils.getParent(viewRootNtv, SnapPopupMenu.class) : null;
    if(pp!=null) pp.setSuppressNextClose(true);

    // Show popup and set RootView.Showing.
    get().setFocusable(pnode.isFocusable());
    get().show(viewRootNtv, x, y);
    setRootViewShowing(pp);
}

/**
 * Hides the popup.
 */
public void hide()  { get().setVisible(false); }

/**
 * Sets the window size to preferred size.
 */
public void setPrefSize()
{
    Rect bnds = getPrefBounds(null, get().getX(), get().getY()); bnds.snap();
    get().setPopupSize((int)bnds.width, (int)bnds.height);
}

/**
 * Returns the preferred bounds for given view and location.
 */
public Rect getPrefBounds(View aView, double aX, double aY)
{
    // Get X and Y relative to aView.RootView
    RootView view = aView!=null? aView.getRootView() : null;
    JComponent nodeNtv = view!=null? view.getNative(JComponent.class) : null;
    int x = (int)aX, y = (int)aY;
    if(view!=null && view!=aView) {
        Point pnt = aView.localToParent(view, x, y); x = (int)pnt.x; y = (int)pnt.y; }
    
    // Get to best size (why doesn't this happen automatically?)
    PopupWindow pview = getView(PopupWindow.class);
    Size bs = pview.getBestSize();
    int bw = (int)Math.round(bs.getWidth()), bh = (int)Math.round(bs.getHeight());
    
    // If size not available at location, shrink size (because otherwise, window will automatically be moved)
    if(view!=null) {
        Dimension ss = SwingUtils.getScreenSizeAvailable(nodeNtv, x, y);
        bw = Math.min(bw, ss.width); bh = Math.min(bh,ss.height);
    }
    
    // Return rect
    return new Rect(x, y, bw, bh);
}

/**
 * Sets RootView.Showing with listener to set false (and remove listener).
 */
public void setRootViewShowing(SnapPopupMenu pp)
{
    PopupWindow pnode = getView(PopupWindow.class);
    ViewUtils.setShowing(pnode, true);
    _pl = new PopupMenuListener() {
        public void popupMenuCanceled(PopupMenuEvent e) { }
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            ViewUtils.setShowing(pnode,false); get().removePopupMenuListener(_pl); _pl = null;
            if(pp!=null) SwingUtilities.invokeLater(() -> pp.setVisible(false));
        }
    };
    get().addPopupMenuListener(_pl);
}


/**
 * A JPopupMenu subclass to facilitate PopupWindow features.
 */
public static class SnapPopupMenu extends JPopupMenu {
    
    /** Sets whether to suppress popup hide calls. */
    public void setSuppressNextClose(boolean b)  { _noClose = b; } boolean _noClose;
    
    /** Override to suppress window close. */ 
    public void setVisible(boolean b) { if(b || !_noClose) super.setVisible(b); _noClose = false; }
}

}