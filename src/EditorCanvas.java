import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

final class EditorCanvas extends JPanel {
    private static final Color ACCENT = new Color(0x315ba8);
    private final Drawing drawing;
    private final Runnable changed;
    private final List<Point> vertices = new ArrayList<>();
    private int selected = -1, handle = -1;
    private Figure.Kind tool;
    private Point start, cursor;
    private Figure preview;
    private Color fill = new Color(0x91bcff), stroke = new Color(0x26456a);
    private double zoom = 1;

    EditorCanvas(Drawing drawing, Runnable changed) {
        this.drawing = drawing;
        this.changed = changed;
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                requestFocusInWindow();
                press(e);
            }
            public void mouseDragged(MouseEvent e) {
                if (start != null) drag(position(e));
            }
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && start != null) release(position(e));
            }
            public void mouseMoved(MouseEvent e) {
                if (!vertices.isEmpty()) {
                    cursor = position(e);
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    Figure.Kind tool() { return tool; }
    Figure selection() { return selected < 0 ? null : drawing.figures().get(selected); }
    Color color(boolean outline) {
        Figure f = selection();
        if (outline) return f == null ? stroke : f.stroke;
        return f == null ? fill : f.fill;
    }

    void setColor(boolean outline, Color value) {
        Figure f = selection();
        if (outline) stroke = value;
        else fill = value;
        if (f != null && !value.equals(outline ? f.stroke : f.fill)) {
            drawing.replace(selected, f.recolor(outline ? f.fill : value, outline ? value : f.stroke));
        }
        update();
    }

    void choose(Figure.Kind kind) {
        select(kind, -1);
    }

    private void select(Figure.Kind kind, int index) {
        clearGesture();
        tool = kind;
        selected = index;
        setCursor(Cursor.getPredefinedCursor(kind == null ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        update();
    }

    void undo() {
        if (!vertices.isEmpty() || start != null) { cancel(); return; }
        drawing.undo();
        choose(null);
    }

    void deleteSelection() {
        if (selected >= 0) {
            drawing.remove(selected);
            choose(null);
        }
    }

    void cancel() {
        clearGesture();
        update();
    }

    void setZoom(double factor) {
        zoom = factor;
        revalidate();
        update();
    }
    public Dimension getPreferredSize() {
        return new Dimension((int)Math.round(Drawing.WIDTH * zoom), (int)Math.round(Drawing.HEIGHT * zoom));
    }

    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private Point position(MouseEvent e) {
        return new Point(clamp((int)Math.round(e.getX() / zoom), 0, Drawing.WIDTH),
                clamp((int)Math.round(e.getY() / zoom), 0, Drawing.HEIGHT));
    }

    private void press(MouseEvent event) {
        Point p = position(event);
        if (tool == Figure.Kind.POLYGON) {
            placeVertex(p, event.getClickCount());
            return;
        }
        start = p;
        if (tool != null) return;
        Figure current = selection();
        handle = -1;
        if (current != null) {
            Point[] handles = current.handles();
            double nearest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < handles.length; i++) {
                double distance = Point.distance(event.getX(), event.getY(),
                        handles[i].x * zoom, handles[i].y * zoom);
                if (distance <= 7 && distance < nearest) {
                    nearest = distance;
                    handle = i;
                }
            }
            if (handle >= 0) return;
        }
        selected = -1;
        for (int i = drawing.figures().size() - 1; i >= 0; i--) {
            if (drawing.figures().get(i).contains(p, 4 / zoom)) {
                selected = i;
                break;
            }
        }
        if (selected < 0) start = null;
        update();
    }

    private void placeVertex(Point p, int clicks) {
        if (vertices.size() >= 3 && vertices.get(0).distance(p) * zoom <= 6) {
            finishPolygon();
            return;
        }
        if (vertices.isEmpty() || !p.equals(vertices.get(vertices.size() - 1))) vertices.add(p);
        cursor = p;
        if (clicks > 1) finishPolygon();
        repaint();
    }

    private void drag(Point p) {
        preview = dragPreview(p);
        repaint();
    }

    private Figure dragPreview(Point p) {
        if (p.equals(start)) return null;
        if (tool != null) {
            if (tool != Figure.Kind.LINE && (start.x == p.x || start.y == p.y)) return null;
            return Figure.create(tool, Arrays.asList(start, p), fill, stroke);
        }

        Figure figure = selection();
        if (figure == null) return null;
        if (handle >= 0) {
            if (p.equals(figure.handles()[handle])) return null;
            return figure.resize(handle, p);
        }

        Rectangle bounds = figure.bounds();
        int dx = clamp(p.x - start.x, -bounds.x, Drawing.WIDTH - bounds.x - bounds.width);
        int dy = clamp(p.y - start.y, -bounds.y, Drawing.HEIGHT - bounds.y - bounds.height);
        if (dx == 0 && dy == 0) return null;
        return figure.move(dx, dy);
    }

    private void release(Point p) {
        Figure result = dragPreview(p);
        if (result != null && tool != null) {
            add(result);
            return;
        }
        if (result != null && result != selection()) drawing.replace(selected, result);
        cancel();
    }

    void finishPolygon() {
        if (vertices.size() < 3) return;
        Figure polygon = Figure.create(Figure.Kind.POLYGON, vertices, fill, stroke);
        if (!polygon.isValidGeometry()) return;
        add(polygon);
    }

    private void add(Figure figure) {
        drawing.add(figure);
        select(null, drawing.figures().size() - 1);
    }

    private void clearGesture() {
        start = null;
        cursor = null;
        preview = null;
        handle = -1;
        vertices.clear();
    }

    private void update() {
        repaint();
        changed.run();
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.scale(zoom, zoom);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < drawing.figures().size(); i++) {
                Figure f = i == selected && preview != null ? preview : drawing.figures().get(i);
                f.draw(g);
            }
            if (tool != null && preview != null) preview.draw(g);
            if (selected >= 0) drawHandles(g, preview == null ? selection() : preview);
            if (!vertices.isEmpty()) {
                Path2D path = new Path2D.Double();
                path.moveTo(vertices.get(0).x, vertices.get(0).y);
                for (Point p : vertices) path.lineTo(p.x, p.y);
                if (cursor != null) path.lineTo(cursor.x, cursor.y);
                g.setColor(stroke);
                g.setStroke(new BasicStroke(2));
                g.draw(path);
                for (Point p : vertices) drawHandle(g, p);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawHandles(Graphics2D g, Figure f) {
        g.setColor(ACCENT);
        float unit = (float)(1 / zoom);
        g.setStroke(new BasicStroke(unit, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[]{4 * unit, 3 * unit}, 0));
        if (f.kind != Figure.Kind.LINE) g.draw(f.bounds());
        for (Point p : f.handles()) drawHandle(g, p);
    }

    private void drawHandle(Graphics2D g, Point p) {
        int radius = Math.max(1, (int)Math.round(4 / zoom));
        g.setStroke(new BasicStroke((float)(1 / zoom)));
        g.setColor(Color.WHITE);
        g.fillRect(p.x - radius, p.y - radius, 2 * radius, 2 * radius);
        g.setColor(ACCENT);
        g.drawRect(p.x - radius, p.y - radius, 2 * radius, 2 * radius);
    }
}
