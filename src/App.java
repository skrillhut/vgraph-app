import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("FileChooser.openDialogTitleText", "Открыть проект");
            UIManager.put("FileChooser.saveDialogTitleText", "Сохранить файл");
            new EditorWindow().setVisible(true);
        });
    }
}
