import java.awt.Color;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class ProjectFile {
    private static final String HEADER = "VGRAPH 1";

    static List<Figure> read(Path file) throws IOException {
        List<Figure> result = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(in.readLine())) throw new IOException("Неизвестный формат проекта.");
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    result.add(parseFigure(line));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Некорректная фигура в строке " + (result.size() + 2) + ".", e);
                }
            }
        }
        return result;
    }

    private static Figure parseFigure(String line) {
        String[] fields = line.split(" ");
        if (fields.length < 7 || fields.length % 2 == 0) throw new IllegalArgumentException();

        Figure.Kind kind = Figure.Kind.valueOf(fields[0]);
        Color fill = parseColor(fields[1]);
        Color stroke = parseColor(fields[2]);
        List<Point> points = new ArrayList<>();
        for (int i = 3; i < fields.length; i += 2) {
            int x = Integer.parseInt(fields[i]);
            int y = Integer.parseInt(fields[i + 1]);
            if (x < 0 || x > Drawing.WIDTH || y < 0 || y > Drawing.HEIGHT) {
                throw new IllegalArgumentException();
            }
            points.add(new Point(x, y));
        }

        Figure figure = Figure.create(kind, points, fill, stroke);
        if (!figure.isValidGeometry()) throw new IllegalArgumentException();
        return figure;
    }

    private static Color parseColor(String field) {
        if (!field.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
        return Color.decode(field);
    }

    static void write(Path file, List<Figure> figures, boolean svg) throws IOException {
        List<String> lines = new ArrayList<>();
        if (svg) {
            lines.add("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + Drawing.WIDTH
                    + "\" height=\"" + Drawing.HEIGHT + "\" viewBox=\"0 0 " + Drawing.WIDTH + " " + Drawing.HEIGHT + "\">");
            for (Figure f : figures) lines.add(f.svg());
            lines.add("</svg>");
        } else {
            lines.add(HEADER);
            for (Figure f : figures) {
                StringBuilder row = new StringBuilder(f.kind.name()).append(' ').append(Figure.hex(f.fill))
                        .append(' ').append(Figure.hex(f.stroke));
                for (int i = 0; i < f.pointCount(); i++) {
                    Point p = f.point(i);
                    row.append(' ').append(p.x).append(' ').append(p.y);
                }
                lines.add(row.toString());
            }
        }
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), ".vgraph-", ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private ProjectFile() {}
}
