import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.filechooser.FileNameExtensionFilter;

final class EditorWindow extends JFrame {
    private static final Color INK = new Color(0x202c29);
    private static final Color MUTED = new Color(0x55635f);
    private static final Color RULE = new Color(0xd8dedb);
    private static final Color ACCENT = new Color(0x285b50);
    private final Drawing drawing = new Drawing();
    private final EditorCanvas canvas = new EditorCanvas(drawing, this::refresh);
    private final Map<Figure.Kind, JToggleButton> tools = new HashMap<>();
    private final JButton undo = button("Отменить", "undo", canvas::undo);
    private final JButton delete = button("Удалить", "delete", canvas::deleteSelection);
    private final JButton fill = button("Заливка", null, () -> pickColor(false));
    private final JButton stroke = button("Обводка", null, () -> pickColor(true));
    private final JLabel status = new JLabel();

    private final JFileChooser files = new JFileChooser();
    private Path project;

    EditorWindow() {
        super("VGraph");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { if (mayDiscard()) dispose(); }
        });
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RULE),
                new EmptyBorder(10, 20, 10, 16)));
        JLabel name = new JLabel("VGraph");
        name.setForeground(INK);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 20f));
        header.add(name, BorderLayout.WEST);
        JPanel fileActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        fileActions.setOpaque(false);
        fileActions.add(button("Открыть", "open", this::open));
        fileActions.add(button("Сохранить", "save", () -> save(false)));
        fileActions.add(button("Экспорт SVG", "export", () -> save(true)));
        header.add(fileActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(sidebar(), BorderLayout.WEST);

        JPanel desk = new JPanel(new GridBagLayout());
        desk.setBackground(new Color(0xe9ecea));
        desk.setBorder(new EmptyBorder(20, 20, 20, 20));
        JPanel paper = new JPanel(new BorderLayout());
        paper.setBorder(BorderFactory.createLineBorder(new Color(0xc7cfcb)));
        paper.add(canvas);
        desk.add(paper);
        JScrollPane scroll = new JScrollPane(desk);
        scroll.setBorder(null);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RULE),
                new EmptyBorder(6, 16, 6, 12)));
        status.setForeground(MUTED);
        footer.add(status, BorderLayout.CENTER);
        JPanel scale = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        scale.setOpaque(false);
        scale.add(new JLabel("Масштаб"));
        String[] levels = {"25%", "50%", "75%", "100%", "150%", "200%", "400%"};
        JComboBox<String> zoom = new JComboBox<>(levels);
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
        rail.setBackground(Color.WHITE);
        rail.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, RULE),
                new EmptyBorder(16, 12, 16, 12)));
        JPanel groups = new JPanel();
        groups.setOpaque(false);
        groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
        JPanel controls = section(groups, "Инструменты");
        ButtonGroup group = new ButtonGroup();
        addTool(controls, group, null, "Выбор", "select");
        for (Figure.Kind kind : Figure.Kind.values()) {
            addTool(controls, group, kind, kind.label, kind.name().toLowerCase(Locale.ROOT));
        }
        section(groups, "Цвет", fill, stroke);
        section(groups, "Действия", undo, delete);
        rail.add(groups, BorderLayout.NORTH);
        return rail;
    }

    private JPanel section(JPanel groups, String text, JButton... buttons) {
        if (groups.getComponentCount() > 0) groups.add(Box.createVerticalStrut(16));
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(new EmptyBorder(0, 10, 4, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        groups.add(label);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 2));
        rows.setOpaque(false);
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JButton button : buttons) rows.add(button);
        groups.add(rows);
        return rows;
    }

    private void style(AbstractButton button, String icon) {
        boolean primary = "save".equals(icon);
        button.setUI(new BasicButtonUI() {
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel model = button.getModel();
                Color surface = Color.WHITE;
                if (primary) surface = model.isPressed() ? INK : ACCENT;
                else if (model.isSelected() || model.isPressed()) surface = new Color(0xddece6);
                else if (model.isRollover() && button.isEnabled()) surface = new Color(0xf0f4f2);
                g.setColor(surface);
                g.fillRoundRect(1, 1, button.getWidth() - 2, button.getHeight() - 2, 10, 10);
                if (button.hasFocus()) {
                    g.setColor(ACCENT);
                    g.drawRoundRect(0, 0, button.getWidth() - 1, button.getHeight() - 1, 10, 10);
                }
                g.dispose();
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(7, 10, 7, 10));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(10);
        if (icon != null) {
            SvgIcon glyph = new SvgIcon(icon);
            button.setIcon(glyph);
            button.setDisabledIcon(glyph);
        }
    }

    private void addTool(JPanel rows, ButtonGroup group, Figure.Kind kind, String label, String icon) {
        JToggleButton tool = new JToggleButton(label);
        style(tool, icon);
        tool.addActionListener(e -> canvas.choose(kind));
        tools.put(kind, tool);
        group.add(tool);
        rows.add(tool);
    }

    private JButton button(String label, String icon, Runnable action) {
        JButton b = new JButton(label);
        style(b, icon);
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
        String filename = project == null ? "Без названия" : project.getFileName().toString();
        setTitle(filename + (drawing.isModified() ? " *" : "") + " | VGraph");
        Figure.Kind tool = canvas.tool();
        Figure f = canvas.selection();
        tools.get(tool).setSelected(true);
        undo.setEnabled(drawing.canUndo());
        delete.setEnabled(f != null);
        fill.setEnabled(tool != Figure.Kind.LINE && (f == null || f.kind != Figure.Kind.LINE));
        fill.setIcon(new Swatch(canvas.color(false)));
        stroke.setIcon(new Swatch(canvas.color(true)));
        if (tool == Figure.Kind.POLYGON) status.setText("Укажите вершины. Enter завершает многоугольник");
        else if (tool != null) status.setText("Протяните мышью по холсту");
        else if (f != null) status.setText(f.kind.label + " · Тяните фигуру или угловой маркер");
        else status.setText("Выберите инструмент или фигуру");
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
            drawing.open(ProjectFile.read(path));
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
