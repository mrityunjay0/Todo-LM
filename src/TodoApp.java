import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TodoApp {

    static class TodoItem {
        String text;
        boolean done;
        TodoItem(String text) { this.text = text; this.done = false; }
        TodoItem(String text, boolean done) { this.text = text; this.done = done; }
        @Override public String toString() { return (done ? "[✓] " : "[ ] ") + text; }
    }

    static class TodoModel {
        private final DefaultListModel<TodoItem> data = new DefaultListModel<>();
        DefaultListModel<TodoItem> getData() { return data; }

        void add(String text) {
            if (text == null) return;
            text = text.trim();
            if (!text.isEmpty()) data.addElement(new TodoItem(text));
        }
        void deleteIndices(int[] indices) {
            if (indices == null || indices.length == 0) return;
            for (int i = indices.length - 1; i >= 0; i--) data.remove(indices[i]);
        }
        void toggle(int[] indices) {
            if (indices == null || indices.length == 0) return;
            for (int idx : indices) {
                TodoItem item = data.get(idx);
                item.done = !item.done;
                data.set(idx, item);
            }
        }
        void clearCompleted() {
            for (int i = data.size() - 1; i >= 0; i--) {
                if (data.get(i).done) data.remove(i);
            }
        }

        void save(Path file) throws IOException {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                TodoItem it = data.get(i);
                lines.add((it.done ? "1|" : "0|") + it.text.replace("\n", " "));
            }
            Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        void load(Path file) throws IOException {
            data.clear();
            if (!Files.exists(file)) return;
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                int sep = line.indexOf('|');
                if (sep < 0) continue;
                boolean done = "1".equals(line.substring(0, sep));
                String text = line.substring(sep + 1);
                data.addElement(new TodoItem(text, done));
            }
        }
    }

    static class TodoView extends JFrame {
        final JTextField input = new JTextField();
        final JButton addBtn = new JButton("Add");
        final JButton deleteBtn = new JButton("Delete");
        final JButton toggleBtn = new JButton("Toggle Complete");
        final JButton clearBtn = new JButton("Clear Completed");
        final JButton saveBtn = new JButton("Save");
        final JButton loadBtn = new JButton("Load");
        final JList<TodoItem> list = new JList<>();

        TodoView() {
            super("Swing To-Do");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(520, 420));
            setLocationByPlatform(true);

            // Top: input + add
            JPanel top = new JPanel(new BorderLayout(8, 8));
            input.setToolTipText("Type a task and press Enter");
            top.add(input, BorderLayout.CENTER);
            top.add(addBtn, BorderLayout.EAST);
            top.setBorder(new EmptyBorder(8, 8, 8, 8));

            // Center: scroll list
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setCellRenderer(new TodoCellRenderer());
            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(new EmptyBorder(0, 8, 0, 8));

            // Bottom: actions
            JPanel bottom = new JPanel();
            bottom.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
            bottom.add(deleteBtn);
            bottom.add(toggleBtn);
            bottom.add(clearBtn);
            bottom.add(saveBtn);
            bottom.add(loadBtn);

            add(top, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        // Custom renderer to strike through completed tasks
        static class TodoCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TodoItem item) {
                    lbl.setText(item.text);
                    Map<TextAttribute, Object> attrs = new java.util.HashMap<>(lbl.getFont().getAttributes());
                    if (item.done) {
                        attrs.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
                    } else {
                        attrs.remove(TextAttribute.STRIKETHROUGH);
                    }
                    lbl.setFont(lbl.getFont().deriveFont(attrs));
                    // prepend checkbox look
                    lbl.setIcon(item.done ? UIManager.getIcon("CheckBoxMenuItem.selectedIcon") : UIManager.getIcon("CheckBox.icon"));
                    lbl.setIconTextGap(8);
                }
                return lbl;
            }
        }
    }

    static class TodoController {
        private final TodoModel model;
        private final TodoView view;
        private final Path store = Paths.get("todos.txt");

        TodoController(TodoModel model, TodoView view) {
            this.model = model;
            this.view = view;
            view.list.setModel(model.getData());
            wireEvents();
        }

        private void wireEvents() {
            view.addBtn.addActionListener(e -> addFromField());
            view.input.addActionListener(e -> addFromField());
            view.deleteBtn.addActionListener(e -> model.deleteIndices(view.list.getSelectedIndices()));
            view.toggleBtn.addActionListener(e -> model.toggle(view.list.getSelectedIndices()));
            view.clearBtn.addActionListener(e -> model.clearCompleted());
            view.saveBtn.addActionListener(e -> {
                try { model.save(store); toast("Saved to " + store.toAbsolutePath()); }
                catch (IOException ex) { error("Save failed: " + ex.getMessage()); }
            });
            view.loadBtn.addActionListener(e -> {
                try { model.load(store); toast("Loaded from " + store.toAbsolutePath()); }
                catch (IOException ex) { error("Load failed: " + ex.getMessage()); }
            });

            view.list.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                        model.deleteIndices(view.list.getSelectedIndices());
                    }
                }
            });
        }

        private void addFromField() {
            String text = view.input.getText();
            model.add(text);
            view.input.setText("");
            view.input.requestFocusInWindow();
            int last = model.getData().size() - 1;
            if (last >= 0) view.list.ensureIndexIsVisible(last);
        }

        private void toast(String msg) { JOptionPane.showMessageDialog(view, msg, "Info", JOptionPane.INFORMATION_MESSAGE); }
        private void error(String msg) { JOptionPane.showMessageDialog(view, msg, "Error", JOptionPane.ERROR_MESSAGE); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TodoModel model = new TodoModel();
            TodoView view = new TodoView();
            new TodoController(model, view);
            view.setVisible(true);
        });
    }
}