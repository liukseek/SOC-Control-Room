package it.soc.client;

import javax.swing.*;
import java.awt.*;

public class ScrollableList extends JPanel implements Scrollable {

    public ScrollableList() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height - 30;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    public static JTextArea wrap(String text, int size, int style, Color color) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setFont(Theme.font(size, style));
        area.setForeground(color);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setHighlighter(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }
}
