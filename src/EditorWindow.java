import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

final class EditorWindow extends JFrame {
    private final Drawing drawing = new Drawing();
    private final EditorCanvas canvas = new EditorCanvas(drawing, this::refresh);
    private final EnumMap<Figure.Kind, JToggleButton> tools = new EnumMap<>(Figure.Kind.class);
    private final JToggleButton select = new JToggleButton("Выбор");
    private final JButton undo = new JButton("Отменить"), delete = new JButton("Удалить");
    private final JButton fill = new JButton("Заливка"), stroke = new JButton("Обводка");
    private final JLabel status = new JLabel();
    private final JLabel title = new JLabel();
    private final JFileChooser files = new JFileChooser();
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"25%", "50%", "75%", "100%", "150%", "200%", "400%"});
    private Path project;

    EditorWindow() {
        super("VGraph");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { if (mayDiscard()) dispose(); }
        });
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBorder(new EmptyBorder(8, 14, 8, 12));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        header.add(title, BorderLayout.CENTER);
        JPanel fileActions = new JPanel();
        fileActions.add(button("Открыть", this::open));
        fileActions.add(button("Сохранить", () -> save(false)));
        fileActions.add(button("Экспорт SVG", () -> save(true)));
        header.add(fileActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(sidebar(), BorderLayout.WEST);

        JPanel desk = new JPanel(new GridBagLayout());
        desk.setBackground(new Color(0xe2e6eb));
        desk.setBorder(new EmptyBorder(20, 20, 20, 20));
        desk.add(canvas);
        JScrollPane scroll = new JScrollPane(desk);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(new EmptyBorder(5, 14, 5, 12));
        footer.add(status, BorderLayout.CENTER);
        JPanel scale = new JPanel();
        scale.add(new JLabel("Масштаб"));
        zoom.setSelectedItem("100%");
        zoom.addActionListener(e -> {
            String value = (String)zoom.getSelectedItem();
            canvas.setZoom(Integer.parseInt(value.substring(0, value.length() - 1)) / 100.0);
        });
        scale.add(zoom);
        footer.add(scale, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        boolean isMac = System.getProperty("os.name").startsWith("Mac");
        int shortcut = isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        bind(KeyEvent.VK_Z, shortcut, "undo", canvas::undo);
        bind(KeyEvent.VK_S, shortcut, "save", () -> save(false));
        bind(KeyEvent.VK_O, shortcut, "open", this::open);
        bind(KeyEvent.VK_DELETE, 0, "delete", canvas::deleteSelection);
        bind(KeyEvent.VK_BACK_SPACE, 0, "backspace", canvas::deleteSelection);
        bind(KeyEvent.VK_ENTER, 0, "finish", canvas::finishPolygon);
        bind(KeyEvent.VK_ESCAPE, 0, "cancel", canvas::cancel);

        refresh();
        setMinimumSize(new Dimension(730, 535));
        setSize(1160, 810);
        setLocationRelativeTo(null);
    }

    private JPanel sidebar() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setBorder(new EmptyBorder(10, 12, 12, 12));
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 5));
        controls.add(new JLabel("Инструменты"));
        ButtonGroup group = new ButtonGroup();
        group.add(select);
        select.addActionListener(e -> canvas.choose(null));
        controls.add(select);
        for (Figure.Kind kind : Figure.Kind.values()) {
            JToggleButton tool = new JToggleButton(kind.label);
            tool.addActionListener(e -> canvas.choose(kind));
            tools.put(kind, tool);
            group.add(tool);
            controls.add(tool);
        }
        controls.add(new JLabel("Цвет"));
        fill.addActionListener(e -> pickColor(false));
        stroke.addActionListener(e -> pickColor(true));
        controls.add(fill);
        controls.add(stroke);
        controls.add(new JLabel("Действия"));
        undo.addActionListener(e -> canvas.undo());
        delete.addActionListener(e -> canvas.deleteSelection());
        controls.add(undo);
        controls.add(delete);
        rail.add(controls, BorderLayout.NORTH);
        return rail;
    }

    private JButton button(String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void bind(int key, int modifiers, String name, Runnable run) {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, modifiers), name);
        root.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { run.run(); }
        });
    }

    private void refresh() {
        title.setText((project == null ? "Без названия" : project.getFileName().toString())
                + (drawing.isModified() ? " *" : ""));
        setTitle(title.getText() + " — VGraph");
        Figure.Kind tool = canvas.tool();
        Figure f = canvas.selection();
        if (tool == null) select.setSelected(true);
        else tools.get(tool).setSelected(true);
        undo.setEnabled(drawing.canUndo());
        delete.setEnabled(f != null);
        fill.setEnabled(tool != Figure.Kind.LINE && (f == null || f.kind != Figure.Kind.LINE));
        fill.setIcon(new Swatch(canvas.color(false)));
        stroke.setIcon(new Swatch(canvas.color(true)));
        if (tool == Figure.Kind.POLYGON) status.setText("Отмечайте вершины. Enter или двойной щелчок завершает фигуру.");
        else if (tool != null) status.setText("Протяните мышью по холсту, чтобы нарисовать фигуру.");
        else if (f != null) status.setText(f.kind.label + "  ·  Тяните фигуру для перемещения или маркер для изменения размера.");
        else status.setText("Выберите инструмент или щёлкните по фигуре.");
    }

    private void pickColor(boolean outline) {
        Color value = JColorChooser.showDialog(this, outline ? "Цвет обводки" : "Цвет заливки", canvas.color(outline));
        if (value != null) canvas.setColor(outline, value);
    }

    private boolean mayDiscard() {
        if (!drawing.isModified()) return true;
        int choice = JOptionPane.showOptionDialog(this, "Сохранить изменения в проекте?", "Проект изменён",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"Сохранить", "Без сохранения", "Отмена"}, "Сохранить");
        return choice == 1 || (choice == 0 && save(false));
    }

    private void configureChooser(boolean svg) {
        files.resetChoosableFileFilters();
        files.setAcceptAllFileFilterUsed(false);
        files.setFileFilter(new FileNameExtensionFilter(svg ? "Рисунок SVG (*.svg)" : "Проект VGraph (*.vgraph)",
                svg ? "svg" : "vgraph"));
    }

    private void open() {
        configureChooser(false);
        if (files.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = files.getSelectedFile().toPath();
        try {
            if (!mayDiscard()) return;
            List<Figure> loaded = ProjectFile.read(path);
            drawing.open(loaded);
            project = path;
            canvas.choose(null);
        } catch (IOException ex) { showError(ex); }
    }

    private boolean save(boolean svg) {
        Path path = project;
        if (svg || path == null) {
            configureChooser(svg);
            String base = project == null ? "Рисунок" : project.getFileName().toString().replaceFirst("(?i)\\.vgraph$", "");
            String ext = svg ? ".svg" : ".vgraph";
            files.setSelectedFile(new File(files.getCurrentDirectory(), base + ext));
            if (files.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            path = files.getSelectedFile().toPath();
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext))
                path = path.resolveSibling(path.getFileName() + ext);
            if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Заменить файл «" + path.getFileName() + "»?",
                    "Файл существует", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        }
        try {
            ProjectFile.write(path, drawing.figures(), svg);
            if (!svg) {
                project = path;
                drawing.markSaved();
            }
            refresh();
            status.setText("Сохранено: " + path.getFileName());
            return true;
        } catch (IOException ex) {
            showError(ex);
            return false;
        }
    }

    private void showError(IOException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка файла", JOptionPane.ERROR_MESSAGE);
    }

    private static final class Swatch implements Icon {
        private final Color value;
        Swatch(Color value) { this.value = value; }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(value);
            g.fillRect(x + 1, y + 1, 14, 14);
            g.setColor(Color.GRAY);
            g.drawRect(x, y, 15, 15);
        }
    }
}
