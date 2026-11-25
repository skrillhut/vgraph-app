import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

abstract class Figure {
    enum Kind {
        RECTANGLE("Прямоугольник"), ELLIPSE("Эллипс"), LINE("Линия"), POLYGON("Многоугольник");
        final String label;
        Kind(String label) { this.label = label; }
    }

    final Kind kind;
    final Color fill;
    final Color stroke;
    private final List<Point> points = new ArrayList<>();

    Figure(Kind kind, List<Point> points, Color fill, Color stroke) {
        this.kind = kind;
        this.fill = fill;
        this.stroke = stroke;
        for (Point p : points) this.points.add(new Point(p));
    }

    static Figure create(Kind kind, List<Point> points, Color fill, Color stroke) {
        if (kind == Kind.POLYGON) {
            if (points.size() < 3) throw new IllegalArgumentException("Многоугольнику нужны хотя бы три точки");
        } else if (points.size() != 2) {
            throw new IllegalArgumentException("Для фигуры нужны две точки");
        }
        switch (kind) {
            case RECTANGLE: return new RectangleFigure(points, fill, stroke);
            case ELLIPSE: return new EllipseFigure(points, fill, stroke);
            case LINE: return new LineFigure(points, fill, stroke);
            case POLYGON: return new PolygonFigure(points, fill, stroke);
            default: throw new AssertionError(kind);
        }
    }

    final Point point(int index) { return new Point(points.get(index)); }
    final int pointCount() { return points.size(); }
    abstract Shape geometry();
    abstract String svgElement();

    Rectangle bounds() {
        Rectangle box = new Rectangle(points.get(0));
        for (Point p : points) box.add(p);
        return box;
    }

    void draw(Graphics2D g) {
        Shape shape = geometry();
        if (kind != Kind.LINE) {
            g.setColor(fill);
            g.fill(shape);
        }
        g.setColor(stroke);
        g.setStroke(new BasicStroke(2));
        g.draw(shape);
    }

    boolean contains(Point p, double tolerance) {
        Shape shape = geometry();
        return shape.contains(p) || new BasicStroke((float)(2 * tolerance),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shape).contains(p);
    }

    Figure recolor(Color fill, Color stroke) {
        return create(kind, points, fill, stroke);
    }

    Figure move(int dx, int dy) {
        List<Point> moved = new ArrayList<>();
        for (Point p : points) moved.add(new Point(p.x + dx, p.y + dy));
        return create(kind, moved, fill, stroke);
    }

    Point[] handles() {
        Rectangle b = bounds();
        return new Point[] { b.getLocation(), new Point(b.x + b.width, b.y),
                new Point(b.x + b.width, b.y + b.height), new Point(b.x, b.y + b.height) };
    }

    Figure resize(int handle, Point target) {
        Point[] corners = handles();
        Point anchor = corners[(handle + 2) % 4];
        Point start = corners[handle];
        if (target.x == anchor.x || target.y == anchor.y) return this;
        double sx = (target.x - anchor.x) / (double)(start.x - anchor.x);
        double sy = (target.y - anchor.y) / (double)(start.y - anchor.y);
        List<Point> scaled = new ArrayList<>();
        for (Point p : points) {
            scaled.add(new Point(anchor.x + (int)Math.round((p.x - anchor.x) * sx),
                    anchor.y + (int)Math.round((p.y - anchor.y) * sy)));
        }
        return create(kind, scaled, fill, stroke);
    }

    static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06x", color.getRGB() & 0xffffff);
    }

    String svg() {
        return "  <" + svgElement() + " fill=\"" + (kind == Kind.LINE ? "none" : hex(fill))
                + "\" stroke=\"" + hex(stroke) + "\" stroke-width=\"2\"/>";
    }
}

final class RectangleFigure extends Figure {
    RectangleFigure(List<Point> points, Color fill, Color stroke) {
        super(Kind.RECTANGLE, points, fill, stroke);
    }
    Shape geometry() { return bounds(); }
    String svgElement() {
        Rectangle b = bounds();
        return "rect x=\"" + b.x + "\" y=\"" + b.y + "\" width=\"" + b.width + "\" height=\"" + b.height + "\"";
    }
}

final class EllipseFigure extends Figure {
    EllipseFigure(List<Point> points, Color fill, Color stroke) {
        super(Kind.ELLIPSE, points, fill, stroke);
    }
    Shape geometry() {
        Rectangle b = bounds();
        return new Ellipse2D.Double(b.x, b.y, b.width, b.height);
    }
    String svgElement() {
        Rectangle b = bounds();
        return "ellipse cx=\"" + b.getCenterX() + "\" cy=\"" + b.getCenterY()
                + "\" rx=\"" + b.width / 2.0 + "\" ry=\"" + b.height / 2.0 + "\"";
    }
}

final class LineFigure extends Figure {
    LineFigure(List<Point> points, Color fill, Color stroke) {
        super(Kind.LINE, points, fill, stroke);
    }
    Shape geometry() { return new Line2D.Double(point(0), point(1)); }
    Point[] handles() { return new Point[] {point(0), point(1)}; }
    Figure resize(int handle, Point target) {
        if (target.equals(point(1 - handle))) return this;
        List<Point> ends = Arrays.asList(point(0), point(1));
        ends.set(handle, target);
        return create(kind, ends, fill, stroke);
    }
    String svgElement() {
        Point a = point(0), b = point(1);
        return "line x1=\"" + a.x + "\" y1=\"" + a.y + "\" x2=\"" + b.x + "\" y2=\"" + b.y + "\"";
    }
}

final class PolygonFigure extends Figure {
    PolygonFigure(List<Point> points, Color fill, Color stroke) {
        super(Kind.POLYGON, points, fill, stroke);
    }
    Shape geometry() {
        Path2D path = new Path2D.Double();
        path.moveTo(point(0).x, point(0).y);
        for (int i = 1; i < pointCount(); i++) {
            Point p = point(i);
            path.lineTo(p.x, p.y);
        }
        path.closePath();
        return path;
    }
    String svgElement() {
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < pointCount(); i++) {
            Point p = point(i);
            if (i > 0) coords.append(' ');
            coords.append(p.x).append(',').append(p.y);
        }
        return "polygon points=\"" + coords + "\"";
    }
}
