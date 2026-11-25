import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

final class Drawing {
    static final int WIDTH = 1000, HEIGHT = 700;
    private static final int UNDO_LIMIT = 5;
    private List<Figure> figures = Collections.emptyList();
    private List<Figure> saved = figures;
    private final Deque<List<Figure>> history = new ArrayDeque<>();

    List<Figure> figures() { return figures; }
    boolean canUndo() { return !history.isEmpty(); }
    boolean isModified() { return figures != saved; }
    void markSaved() { saved = figures; }

    void open(List<Figure> loaded) {
        figures = Collections.unmodifiableList(new ArrayList<>(loaded));
        history.clear();
        markSaved();
    }

    void add(Figure figure) { edit(next -> next.add(figure)); }
    void remove(int index) { edit(next -> next.remove(index)); }
    void replace(int index, Figure figure) { edit(next -> next.set(index, figure)); }

    private void edit(Consumer<List<Figure>> change) {
        List<Figure> next = new ArrayList<>(figures);
        change.accept(next);
        if (history.size() == UNDO_LIMIT) history.removeLast();
        history.push(figures);
        figures = Collections.unmodifiableList(next);
    }

    void undo() {
        if (canUndo()) figures = history.pop();
    }
}
