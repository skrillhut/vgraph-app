import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.swing.Icon;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class SvgIcon implements Icon {
    private static final Map<String, Path2D> paths = loadPaths();
    private final Path2D path;

    SvgIcon(String name) {
        path = paths.get(name);
        if (path == null) throw new IllegalArgumentException(name);
    }

    private static Map<String, Path2D> loadPaths() {
        Map<String, Path2D> result = new HashMap<>();
        try (InputStream stream = SvgIcon.class.getResourceAsStream("/icons.svg")) {
            if (stream == null) throw new IllegalStateException("icons.svg отсутствует в classpath");
            NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(stream).getElementsByTagName("path");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element)nodes.item(i);
                result.put(element.getAttribute("id"), parse(element.getAttribute("d")));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось загрузить icons.svg", ex);
        }
        return result;
    }

    private static Path2D parse(String data) {
        Path2D path = new Path2D.Double();
        StringTokenizer tokens = new StringTokenizer(data);
        while (tokens.hasMoreTokens()) {
            String command = tokens.nextToken();
            switch (command) {
                case "M": path.moveTo(number(tokens), number(tokens)); break;
                case "L": path.lineTo(number(tokens), number(tokens)); break;
                case "C": path.curveTo(number(tokens), number(tokens), number(tokens), number(tokens),
                        number(tokens), number(tokens)); break;
                case "Z": path.closePath(); break;
                default: throw new IllegalArgumentException(command);
            }
        }
        return path;
    }

    private static double number(StringTokenizer tokens) { return Double.parseDouble(tokens.nextToken()); }

    public int getIconWidth() { return 18; }
    public int getIconHeight() { return 18; }

    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.translate(x, y);
            g.scale(getIconWidth() / 24.0, getIconHeight() / 24.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.isEnabled() ? c.getForeground() : new Color(0x929896));
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        } finally {
            g.dispose();
        }
    }
}
