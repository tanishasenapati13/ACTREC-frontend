import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.Queue;

import javax.swing.*;
import javax.swing.event.*;


public class gui4 extends JFrame {
    private static final long serialVersionUID = 1L;
    
    private final DrawingPanel drawingPanel = new DrawingPanel();
    private final List<FunctionBlock> functionBlocks = new ArrayList<>();
    private int functionCounter = 1;    
    FunctionBlock dragSource = null;
    int dragSourceOutputIndex = -1;
    List<Connection> connections = new ArrayList<>();
    private JTextArea resultArea;
    private JTabbedPane tabbedPane;
    private Connection selectedConnection = null;
    private final Map<String, BlockTemplate> blockLibrary = new HashMap<>();
    private final Map<String, Integer> instanceCounter = new HashMap<>();
    private JComboBox<String> blockSelector;
    private JPanel blockListPanel;
    public int inputCount = 0;
    List<FunctionBlock> blockInstances = new ArrayList<>();
    private double prevZoomFactor = 1.0;
    private double zoomFactor = 1.0;

    private JButton hamburgerButton; // Hamburger icon button added

    private static String getDefaultValue(String type) {
        switch (type) {
            case "float": return "0.0";
            case "integer": return "0";
            case "int": return "0";
            case "string": return "default_string";
            case "file": return "";
            case "graph": return "default_graph";
            case "Status": return "default_status";
            case "character": return "a";
            case "char": return "a";
            default: return "default";
        }
    }

    public gui4() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("Visual Function Graph");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // === Top Panel (controls) ===
        JPanel topPanel = new JPanel();
        topPanel.setLayout((LayoutManager) new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // === Hamburger Icon Button ===
        hamburgerButton = new JButton("\u2630"); // Unicode for hamburger icon ☰
        hamburgerButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        hamburgerButton.setToolTipText("Show Block Names");
        hamburgerButton.setMargin(new Insets(2, 8, 2, 8));
        hamburgerButton.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem editInputs = new JMenuItem("Edit Block Inputs");
            editInputs.addActionListener(ev -> showBlockNamesDialog());
            menu.add(editInputs);
            JMenuItem viewNamingHistory = new JMenuItem("View Naming History");
            viewNamingHistory.addActionListener(ev -> showNamingHistoryDialog());
            menu.add(viewNamingHistory);
            menu.show(hamburgerButton, 0, hamburgerButton.getHeight());
        });
        controlsPanel.add(hamburgerButton);

        // === Block Selector Dropdown ===
        blockSelector = new JComboBox<String>();
        controlsPanel.add(new JLabel("Select Block:"));
        controlsPanel.add(blockSelector);

        // === Add Block Button ===
        JButton addBlockBtn = new JButton("Add Block");
        addBlockBtn.addActionListener(e -> addSelectedBlockInstance());
        controlsPanel.add(addBlockBtn);

        // === Add New Block Template Button ===
        JButton addTemplateBtn = new JButton("Add New Block Template");
        addTemplateBtn.addActionListener(e -> showNewBlockTemplateDialog());
        controlsPanel.add(addTemplateBtn);

        // === Search Field and Button ===
        JTextField searchField = new JTextField(15);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchFunctionBlock(searchField.getText().toLowerCase()));
        controlsPanel.add(new JLabel("Search:"));
        controlsPanel.add(searchField);
        controlsPanel.add(searchButton);

        // === Zoom In/Out Buttons ===
        JButton zoomInButton = new JButton("Zoom In");
        zoomInButton.addActionListener(e -> drawingPanel.zoom(1.1));
        JButton zoomOutButton = new JButton("Zoom Out");
        zoomOutButton.addActionListener(e -> drawingPanel.zoom(0.9));
        controlsPanel.add(zoomInButton);
        controlsPanel.add(zoomOutButton);

        // === Execute Button ===
        JButton executeBtn = new JButton("Execute");
        executeBtn.addActionListener(e -> executeGraph());
        controlsPanel.add(executeBtn);

        topPanel.add(controlsPanel);

        // === Vertical Scrollable Block Library Panel ===
        blockListPanel = new JPanel();
        blockListPanel.setLayout(new BoxLayout(blockListPanel, BoxLayout.Y_AXIS));
        JScrollPane blockScrollPane = new JScrollPane(blockListPanel);
        blockScrollPane.setPreferredSize(new Dimension(250, 100));
        topPanel.add(blockScrollPane);

        // Initialize block library after all components are created
        populateBlockLibrary();

        add(topPanel, BorderLayout.NORTH);

        // === Main Content with Tabs ===
        tabbedPane = new JTabbedPane();
        
        // Drawing Canvas Tab
        JScrollPane canvasScroll = new JScrollPane(drawingPanel);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
        tabbedPane.addTab("Graph Editor", canvasScroll);
        
        // Results Tab
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        tabbedPane.addTab("Results", resultScroll);
        
        add(tabbedPane, BorderLayout.CENTER);

        setVisible(true);
    }

    // Placeholder JTextField class
    class PlaceholderTextField extends JTextField {
        private String placeholder = "";

        public PlaceholderTextField(String placeholder) {
            this.placeholder = placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Color.GRAY);
                FontMetrics fm = g2.getFontMetrics();
                int x = getInsets().left;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, x, y);
                g2.dispose();
            }
        }
    }

    // Custom cell editor for Value column with type placeholder
    class TypeAwareCellEditor extends DefaultCellEditor {
        private String currentType;

        public TypeAwareCellEditor() {
            super(new PlaceholderTextField(""));
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            PlaceholderTextField field = (PlaceholderTextField) super.getTableCellEditorComponent(table, value, isSelected, row, column);
            currentType = (String) table.getValueAt(row, 2);
            field.setPlaceholder(currentType);
            return field;
        }

        @Override
        public boolean stopCellEditing() {
            return super.stopCellEditing();
        }
    }

    // New method to show dialog with editable input values for all blocks
    private void showBlockNamesDialog() {
        // Calculate total inputs
        int totalInputs = 0;
        for (FunctionBlock fb : functionBlocks) {
            totalInputs += fb.template.inputCount;
        }

        // Create table model with columns: Block Name, Input, Type, Value
        String[] columnNames = {"Block Name", "Input", "Type", "Value"};
        Object[][] data = new Object[totalInputs][4];

        int row = 0;
        for (FunctionBlock fb : functionBlocks) {
            for (int i = 0; i < fb.template.inputCount; i++) {
                data[row][0] = fb.name;
                data[row][1] = "Input " + (i + 1);
                data[row][2] = fb.template.inputTypes[i];
                // Check if connected
                boolean connected = false;
                String value = fb.inputValues[i];
                for (Connection c : connections) {
                    if (c.to == fb && c.toIdx == i) {
                        connected = true;
                        value = "$" + c.from.name + ".output" + (c.fromIdx + 1);
                        break;
                    }
                }
                data[row][3] = value;
                row++;
            }
        }

        // Create oldData for reverting invalid changes
        Object[][] oldData = new Object[totalInputs][4];
        for(int i=0; i<data.length; i++) oldData[i] = data[i].clone();

        // Custom table model to make only Value column editable for unconnected inputs
        class InputTableModel extends javax.swing.table.DefaultTableModel {
            public InputTableModel(Object[][] data, Object[] columnNames) {
                super(data, columnNames);
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == 3) {
                    // Determine if this row's input is connected
                    int currentRow = 0;
                    for (FunctionBlock fb : functionBlocks) {
                        for (int i = 0; i < fb.template.inputCount; i++) {
                            if (currentRow == row) {
                                boolean connected = false;
                                for (Connection c : connections) {
                                    if (c.to == fb && c.toIdx == i) {
                                        connected = true;
                                        break;
                                    }
                                }
                                return !connected;
                            }
                            currentRow++;
                        }
                    }
                }
                return false;
            }
        }

        JTable table = new JTable(new InputTableModel(data, columnNames));
        table.setPreferredScrollableViewportSize(new Dimension(600, 400));
        table.setFillsViewportHeight(true);

        // Set custom editor for Value column
        table.getColumnModel().getColumn(3).setCellEditor(new TypeAwareCellEditor());

        JScrollPane scrollPane = new JScrollPane(table);

        // Create custom dialog
        JDialog dialog = new JDialog(this, "Edit Block Input Values", true);

        // Add real-time validation listener
        table.getModel().addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 3) {
                int r = e.getFirstRow();
                String value = (String) table.getValueAt(r, 3);
                String type = (String) table.getValueAt(r, 2);
                if (!isValidValue(value, type)) {
                    JOptionPane.showMessageDialog(dialog, "Invalid value for type " + type + ": '" + value + "'", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    // Revert to old value
                    table.setValueAt(oldData[r][3], r, 3);
                } else {
                    // Update oldData
                    oldData[r][3] = value;
                }
            }
        });
        dialog.setLayout(new BorderLayout());
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        final int[] result = {JOptionPane.CANCEL_OPTION}; // Default to cancel

        okButton.addActionListener(e -> {
            // Validate values for unconnected inputs only
            boolean allValid = true;
            StringBuilder errorMsg = new StringBuilder("Invalid values:\n");
            int r = 0;
            for (FunctionBlock fb : functionBlocks) {
                for (int i = 0; i < fb.template.inputCount; i++) {
                    boolean connected = false;
                    for (Connection c : connections) {
                        if (c.to == fb && c.toIdx == i) {
                            connected = true;
                            break;
                        }
                    }
                    if (!connected) {
                        String value = (String) table.getValueAt(r, 3);
                        String type = fb.template.inputTypes[i];
                        if (!isValidValue(value, type)) {
                            allValid = false;
                            errorMsg.append("- Block '").append(fb.name).append("', Input ").append((i + 1))
                                    .append(" (").append(type).append("): '").append(value).append("'\n");
                        }
                    }
                    r++;
                }
            }
            if (!allValid) {
                JOptionPane.showMessageDialog(dialog, errorMsg.toString(), "Validation Error", JOptionPane.ERROR_MESSAGE);
                // Do not dispose, keep dialog open
            } else {
                result[0] = JOptionPane.OK_OPTION;
                dialog.dispose();
            }
        });

        cancelButton.addActionListener(e -> {
            result[0] = JOptionPane.CANCEL_OPTION;
            dialog.dispose();
        });

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (result[0] == JOptionPane.OK_OPTION) {
            // Update inputValues if all valid
            row = 0;
            for (FunctionBlock fb : functionBlocks) {
                for (int i = 0; i < fb.template.inputCount; i++) {
                    boolean connected = false;
                    for (Connection c : connections) {
                        if (c.to == fb && c.toIdx == i) {
                            connected = true;
                            break;
                        }
                    }
                    if (!connected) {
                        fb.inputValues[i] = (String) table.getValueAt(row, 3);
                    }
                    row++;
                }
            }
        }
    }

    private boolean isValidValue(String value, String type) {
        switch (type) {
            case "float":
                try {
                    Double.parseDouble(value);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            case "integer":
            case "int":
                try {
                    Integer.parseInt(value);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            case "char":
                return value != null && value.length() <= 1;
            case "string":
                // Accept any value for string, but reject if value is null
                return value != null;
            case "file":
                // Accept empty or strings that look like file paths (basic check)
                return value == null || value.trim().isEmpty() || (value.contains(".") || value.contains("/") || value.contains("\\"));
            case "graph":
            case "Status":
            case "character":
            default:
                return true; // Accept any value for these types
        }
    }

    // New method to show dialog for viewing naming history
    private void showNamingHistoryDialog() {
        StringBuilder historyText = new StringBuilder();
        for (FunctionBlock fb : functionBlocks) {
            historyText.append("Block: ").append(fb.name).append("\n");
            historyText.append("Original: ").append(fb.originalName).append("\n");
            historyText.append("History: ").append(String.join(" -> ", fb.nameHistory)).append("\n\n");
        }

        JTextArea textArea = new JTextArea(historyText.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JOptionPane.showMessageDialog(this, scrollPane, "Naming History", JOptionPane.INFORMATION_MESSAGE);
    }

    public void searchFunctionBlock(String query) {
        drawingPanel.searchResults.clear();
        boolean found = false;
        for (FunctionBlock block : functionBlocks) {
            if (block.name != null && block.name.toLowerCase().contains(query)) {
                drawingPanel.searchResults.add(block);
                // Highlight and scroll to the first match
                if (!found) {
                    highlightAndScrollToBlock(block);
                    found = true;
                }
            }
        }
        if (!found && !query.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No blocks found matching: " + query);
        }
        drawingPanel.repaint();
    }

    private void highlightAndScrollToBlock(FunctionBlock block) {
        Rectangle bounds = block.getBounds();

        // Convert to zoomed coordinates
        int x = (int) (bounds.x * drawingPanel.zoomFactor + drawingPanel.translateX);
        int y = (int) (bounds.y * drawingPanel.zoomFactor + drawingPanel.translateY);
        int w = (int) (bounds.width * drawingPanel.zoomFactor);
        int h = (int) (bounds.height * drawingPanel.zoomFactor);
        Rectangle zoomed = new Rectangle(x, y, w, h);

        drawingPanel.scrollRectToVisible(zoomed);
        block.setBorder(BorderFactory.createLineBorder(Color.RED, 3));

        new javax.swing.Timer(3000, e -> {
            block.setBorder(BorderFactory.createTitledBorder(block.name));
            drawingPanel.repaint();
        }).start();
    }

    private void populateBlockLibrary() {
        addTemplate(new BlockTemplate("cudf", 3, 2, new String[]{"file", "integer","integer"}, new String[]{"file","file"}));
        addTemplate(new BlockTemplate("start", 4, 1, new String[]{"file", "file","file","file"}, new String[]{"Status"}));
        addTemplate(new BlockTemplate("wgx", 2, 1, new String[]{"string", "string"}, new String[]{"string"}));
        addTemplate(new BlockTemplate("mff", 1, 1, new String[]{"float"}, new String[]{"graph"}));
        addTemplate(new BlockTemplate("result", 4, 3, new String[]{"float","string","file","graph"}, new String[]{"graph","string","float"}));

        // Add to dropdown
        for (String blockName : blockLibrary.keySet()) {
            blockSelector.addItem(blockName);
        }
        blockSelector.addItem("Add New Block Template...");
    }

    private void updateCanvasSize() {
        int maxX = 0;
        int maxY = 0;

        for (FunctionBlock block : functionBlocks) {
            Rectangle bounds = block.getBounds();
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
        }

        maxX += 100;
        maxY += 100;

        drawingPanel.setPreferredSize(new Dimension(maxX, maxY));
        drawingPanel.revalidate();
    }

    private void addTemplate(BlockTemplate template) {
        blockLibrary.put(template.name, template);
        instanceCounter.put(template.name, 0);

        JButton blockBtn = new JButton(template.name);
        blockBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        blockBtn.setMaximumSize(new Dimension(180, 30));
        blockBtn.addActionListener(e -> addBlockInstance(template.name));

        blockListPanel.add(blockBtn);
        blockListPanel.revalidate();
        blockListPanel.repaint();
    }

    private void addBlockInstance(String blockName) {
        if (!blockLibrary.containsKey(blockName)) return;

        BlockTemplate template = blockLibrary.get(blockName);
        int count = instanceCounter.getOrDefault(template.name, 0) + 1;
        instanceCounter.put(template.name, count);

        String instanceName = template.name + "_" + count;
        FunctionBlock block = new FunctionBlock(instanceName, template);
        int x = 100 + functionCounter * 60;
        int y = 100 + functionCounter * 40;
        
        // Apply current zoom factor to new blocks
        int scaledWidth = (int)(block.getPreferredSize().width * zoomFactor);
        int scaledHeight = (int)(block.getPreferredSize().height * zoomFactor);
        block.setBounds(x, y, scaledWidth, scaledHeight);

        functionCounter++;
        functionBlocks.add(block);
        drawingPanel.add(block);
        updateCanvasSize();
        drawingPanel.repaint();
    }

    private void addSelectedBlockInstance() {
        String selected = (String) blockSelector.getSelectedItem();
        if (selected != null && selected.equals("Add New Block Template...")) {
            showNewBlockTemplateDialog();
            return;
        }
        addBlockInstance(selected);
    }

        private void showNewBlockTemplateDialog() {
            JTextField nameField = new JTextField(10);
            JSpinner inputSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
            JSpinner outputSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
    
            JPanel panel = new JPanel(new GridLayout(0, 2));
            panel.add(new JLabel("Function Name:"));
            panel.add(nameField);
            panel.add(new JLabel("# Inputs:"));
            panel.add(inputSpinner);
            panel.add(new JLabel("# Outputs:"));
            panel.add(outputSpinner);
    
            int res = JOptionPane.showConfirmDialog(this, panel, "New Block Template", JOptionPane.OK_CANCEL_OPTION);
            if (res != JOptionPane.OK_OPTION) return;
    
            int inCount = (int) inputSpinner.getValue();
            int outCount = (int) outputSpinner.getValue();
    
            String name = nameField.getText().trim();
            if (name.isEmpty() || blockLibrary.containsKey(name)) {
                JOptionPane.showMessageDialog(this, "Invalid or duplicate name");
                return;
            }
    
            String[] types = {"float", "string", "file", "graph","int", "Status", "char"};
            String[] inTypes = new String[inCount];
            String[] outTypes = new String[outCount];
    
            JPanel typePanel = new JPanel(new GridLayout(inCount + outCount, 2));
            @SuppressWarnings("unchecked")
            JComboBox<String>[] inComboBoxes = new JComboBox[inCount];
            @SuppressWarnings("unchecked")
            JComboBox<String>[] outComboBoxes = new JComboBox[outCount];
            
            for (int i = 0; i < inCount; i++) {
                typePanel.add(new JLabel("Input " + (i + 1) + ":"));
                JComboBox<String> cb = new JComboBox<>(types);
                inComboBoxes[i] = cb;
                typePanel.add(cb);
                inTypes[i] = types[0];
            }
            for (int i = 0; i < outCount; i++) {
                typePanel.add(new JLabel("Output " + (i + 1) + ":"));
                JComboBox<String> cb = new JComboBox<>(types);
                outComboBoxes[i] = cb;
                typePanel.add(cb);
                outTypes[i] = types[0];
            }
    
            int typeRes = JOptionPane.showConfirmDialog(this, typePanel, "Select Types", JOptionPane.OK_CANCEL_OPTION);
            if (typeRes == JOptionPane.OK_OPTION) {
                // Get selected types
                for (int i = 0; i < inCount; i++) {
                    inTypes[i] = (String) inComboBoxes[i].getSelectedItem();
                }
                for (int i = 0; i < outCount; i++) {
                    outTypes[i] = (String) outComboBoxes[i].getSelectedItem();
                }
                
                addTemplate(new BlockTemplate(name, inCount, outCount, inTypes, outTypes));
                blockSelector.removeAllItems();
                for (String blockName : blockLibrary.keySet()) {
                    blockSelector.addItem(blockName);
                }
                blockSelector.addItem("Add New Block Template...");
            }
        }

    private void executeGraph() {
        List<FunctionBlock> order = getTopologicalOrder();
        if (order == null) return;

        saveExecutionPlan(order);
        resultArea.setText("Execution plan saved to execution_plan.txt\n");
    }

    class BlockTemplate {
        String name;
        int inputCount;
        int outputCount;
        String[] inputTypes;
        String[] outputTypes;
        String[] defaultValues;

        BlockTemplate(String name, int inputCount, int outputCount, String[] inputTypes, String[] outputTypes) {
            this.name = name;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.inputTypes = inputTypes;
            this.outputTypes = outputTypes;
            this.defaultValues = new String[inputCount];
            for (int i = 0; i < inputCount; i++) {
                this.defaultValues[i] = getDefaultValue(inputTypes[i]);
            }
        }

        @Override
        public String toString() {
            return name + " (" + inputCount + " in, " + outputCount + " out)";
        }
    }

    class DrawingPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        
        public double zoomFactor = 1.0;
        private transient Connection selectedConnection = null;
        private Point mousePoint = null;
        private boolean showMagnifier = false;
        private transient List<FunctionBlock> searchResults = new ArrayList<>();
        private transient FunctionBlock focusedBlock = null;
        public double translateX = 0;
        public double translateY = 0;

        DrawingPanel() {
            setPreferredSize(new Dimension(2000, 1200));
            setLayout(null); // Using absolute positioning for blocks

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    selectedConnection = null;

                    Point rawClick = e.getPoint();
                    Point click = rawClick;
                    Container parent = getParent();
                    if (parent instanceof JViewport) {
                        JViewport viewport = (JViewport) parent;
                        Point viewPos = viewport.getViewPosition();
                        click = new Point((int)((rawClick.x + viewPos.x - translateX) / zoomFactor),
                                          (int)((rawClick.y + viewPos.y - translateY) / zoomFactor));
                    } else {
                        click = new Point((int)((rawClick.x - translateX) / zoomFactor),
                                          (int)((rawClick.y - translateY) / zoomFactor));
                    }

                    for (Connection conn : connections) {
                        Point from = getOutputPoint(conn.from, conn.fromIdx);
                        Point to = getInputPoint(conn.to, conn.toIdx);
                        if (isPointNearLine(click, from, to, 20)) {
                            selectedConnection = conn;
                            gui4.this.selectedConnection = conn;
                            break;
                        }
                    }

                    if (SwingUtilities.isRightMouseButton(e) && selectedConnection != null) {
                        JPopupMenu contextMenu = new JPopupMenu();
                        JMenuItem deleteItem = new JMenuItem("Delete Connection");
                        deleteItem.addActionListener(ev -> {
                            connections.remove(selectedConnection);
                            selectedConnection = null;
                            gui4.this.selectedConnection = null;
                            repaint();
                        });
                        contextMenu.add(deleteItem);
                        contextMenu.show(drawingPanel, e.getX(), e.getY());
                        e.consume();
                        return;
                    }

                    repaint();
                    requestFocusInWindow();
                }

                public void mouseExited(MouseEvent e) {
                    showMagnifier = false;
                    repaint();
                }
            });


            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    mousePoint = e.getPoint();
                    showMagnifier = (zoomFactor < 1.0);
                    repaint();
                }
            });
            addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                    Point focus = e.getPoint(); // Mouse position as zoom center
                    zoom(factor, focus);
                    e.consume(); // Prevent it from scrolling when zooming
                }
            });



            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE && selectedConnection != null) {
                        connections.remove(selectedConnection);
                        selectedConnection = null;
                        gui4.this.selectedConnection = null;
                        repaint(); 
                    }
                }
            });

        }

        private Color getConnectionColor(String type) {
            switch (type) {
                case "float": return new Color(139, 0, 0); // Dark Red
                case "integer": return new Color(0, 0, 139); // Dark Blue
                case "int": return new Color(0, 0, 139); // Dark Blue
                case "string": return new Color(255, 140, 0); // Dark Orange
                case "file": return new Color(199, 21, 133); // Medium Violet Red (darker pink)
                case "graph": return new Color(0, 139, 139); // Dark Cyan
                case "Status": return new Color(0, 100, 0); // Dark Green
                case "character": return new Color(184, 134, 11); // Dark Goldenrod (darker yellow)
                case "char": return new Color(184, 134, 11); // Dark Goldenrod (darker yellow)
                default: return Color.DARK_GRAY;
            }
        }

        public void zoom(double factor, Point focusPoint) {
            double oldZoom = zoomFactor;
            zoomFactor *= factor;
            zoomFactor = Math.max(0.1, Math.min(5.0, zoomFactor));
            gui4.this.zoomFactor = zoomFactor;

            // Scale all blocks
            for (FunctionBlock block : functionBlocks) {
                Point loc = block.getLocation();
                Dimension size = block.getPreferredSize();

                int newX = (int)(loc.x * zoomFactor / oldZoom);
                int newY = (int)(loc.y * zoomFactor / oldZoom);
                int newWidth = (int)(size.width * zoomFactor);
                int newHeight = (int)(size.height * zoomFactor);

                block.setBounds(newX, newY, newWidth, newHeight);
            }

            // Resize canvas to fit new content
            updateCanvasSize();

            // Reposition view to keep focus point at same logical location
            Container parent = getParent(); // should be the JViewport
            if (parent instanceof JViewport) {
                JViewport viewport = (JViewport) parent;

                Point viewPos = viewport.getViewPosition();
                double dx = (focusPoint.x - viewPos.x) / oldZoom;
                double dy = (focusPoint.y - viewPos.y) / oldZoom;

                int newX = (int)(dx * zoomFactor - focusPoint.x);
                int newY = (int)(dy * zoomFactor - focusPoint.y);

                viewport.setViewPosition(new Point(viewPos.x + newX, viewPos.y + newY));
            }

            repaint();
        }


        public void zoom(double factor) {
            double oldZoomFactor = zoomFactor;
            zoomFactor *= factor;
            zoomFactor = Math.max(0.1, Math.min(5.0, zoomFactor));
            gui4.this.zoomFactor = zoomFactor;
            
            // Scale all blocks with zoom
            for (FunctionBlock block : functionBlocks) {
                Point loc = block.getLocation();
                Dimension pref = block.getPreferredSize();
                
                // Scale position and size
                int newX = (int)(loc.x * zoomFactor / oldZoomFactor);
                int newY = (int)(loc.y * zoomFactor / oldZoomFactor);
                int newWidth = (int)(pref.width * zoomFactor);
                int newHeight = (int)(pref.height * zoomFactor);
                
                block.setBounds(newX, newY, newWidth, newHeight);
            }
            
            updateCanvasSize();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Apply transformations for arrows only
            g2.translate(translateX, translateY);
            g2.scale(zoomFactor, zoomFactor);

            // Draw connections
            for (Connection conn : connections) {
                Point from = getOutputPoint(conn.from, conn.fromIdx);
                Point to = getInputPoint(conn.to, conn.toIdx);
                drawCurvedArrow(g2, from, to, conn.type, conn == selectedConnection);
            }

            g2.dispose();

            // Draw magnifier
            // if (showMagnifier && mousePoint != null && zoomFactor < 1.0) {
            //     drawMagnifier(g);
            // }
            
            // Highlight search results
            if (!searchResults.isEmpty()) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(new Color(255, 0, 0, 100)); 
                g2d.setStroke(new BasicStroke(3));
                for (FunctionBlock block : searchResults) {
                    Rectangle bounds = block.getBounds();
                    g2d.drawRect(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);
                }
                g2d.dispose();
            }
        }

        private void drawMagnifier(Graphics g) {
            int zoomSize = 100;
            int magnifyScale = 3;

            BufferedImage zoomImage = new BufferedImage(zoomSize, zoomSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gZoom = zoomImage.createGraphics();
            gZoom.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Clear background
            gZoom.setColor(getBackground());
            gZoom.fillRect(0, 0, zoomSize, zoomSize);

            gZoom.scale(magnifyScale, magnifyScale);

            double tx = (mousePoint.x / zoomFactor - zoomSize / (2.0 * magnifyScale));
            double ty = (mousePoint.y / zoomFactor - zoomSize / (2.0 * magnifyScale));
            gZoom.translate(-tx, -ty);

            // Draw blocks in magnifier
            for (FunctionBlock block : functionBlocks) {
                Rectangle bounds = block.getBounds();
                Rectangle magnifierBounds = new Rectangle(
                    (int)(bounds.x * zoomFactor), 
                    (int)(bounds.y * zoomFactor),
                    (int)(bounds.width * zoomFactor), 
                    (int)(bounds.height * zoomFactor)
                );
                
                gZoom.setColor(new Color(230, 230, 250));
                gZoom.fillRect(magnifierBounds.x, magnifierBounds.y, magnifierBounds.width, magnifierBounds.height);
                gZoom.setColor(Color.BLACK);
                gZoom.drawRect(magnifierBounds.x, magnifierBounds.y, magnifierBounds.width, magnifierBounds.height);
                gZoom.drawString(block.name, magnifierBounds.x + 5, magnifierBounds.y + 15);
            }

            // Draw connections in magnifier
            Graphics2D contentG = (Graphics2D) gZoom.create();
            contentG.scale(zoomFactor, zoomFactor);
            for (Connection conn : connections) {
                Point from = getOutputPoint(conn.from, conn.fromIdx);
                Point to = getInputPoint(conn.to, conn.toIdx);
                drawCurvedArrow(contentG, from, to, conn.type + " connection", conn == selectedConnection);
            }
            contentG.dispose();
            gZoom.dispose();

            int drawX = mousePoint.x + 20;
            int drawY = mousePoint.y + 20;
            g.drawImage(zoomImage, drawX, drawY, null);
            g.setColor(Color.BLACK);
            g.drawRect(drawX, drawY, zoomSize, zoomSize);
        }

        private void drawCurvedArrow(Graphics2D g2, Point from, Point to, String type, boolean isSelected) {
            int dx = to.x - from.x;
            int dy = to.y - from.y;

            int ctrlX = (from.x + to.x) / 2 + Math.min(50, Math.abs(dx) / 4);
            int ctrlY = (from.y + to.y) / 2 - Math.min(50, Math.abs(dy) / 4);

            QuadCurve2D q = new QuadCurve2D.Float();
            q.setCurve(from.x, from.y, ctrlX, ctrlY, to.x, to.y);

            Color connColor = getConnectionColor(type);
            g2.setColor(isSelected ? Color.RED : connColor);
            g2.setStroke(new BasicStroke(2));
            g2.draw(q);

            double t = 0.5;
            double oneMinusT = 1 - t;
            double midX = oneMinusT * oneMinusT * from.x + 2 * oneMinusT * t * ctrlX + t * t * to.x;
            double midY = oneMinusT * oneMinusT * from.y + 2 * oneMinusT * t * ctrlY + t * t * to.y;

            double dxTangent = 2 * (1 - t) * (ctrlX - from.x) + 2 * t * (to.x - ctrlX);
            double dyTangent = 2 * (1 - t) * (ctrlY - from.y) + 2 * t * (to.y - ctrlY);
            double angle = Math.atan2(dyTangent, dxTangent);

            Font baseFont = new Font("SansSerif", Font.PLAIN, 12);
            g2.setFont(baseFont);

            AffineTransform old = g2.getTransform();
            g2.translate(midX, midY);
            g2.rotate(angle);
            String label = type;
            g2.drawString(label, -label.length() * 3, -5);
            g2.setTransform(old);

            drawArrowHead(g2, ctrlX, ctrlY, to.x, to.y, isSelected ? Color.RED : connColor);
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2, Color color) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int arrowLength = 10;

            int x = x2;
            int y = y2;

            int xA = (int)(x - arrowLength * Math.cos(angle - Math.PI / 6));
            int yA = (int)(y - arrowLength * Math.sin(angle - Math.PI / 6));
            int xB = (int)(x - arrowLength * Math.cos(angle + Math.PI / 6));
            int yB = (int)(y - arrowLength * Math.sin(angle + Math.PI / 6));

            int[] xPoints = {x, xA, xB};
            int[] yPoints = {y, yA, yB};

            g2.setColor(color);
            g2.fillPolygon(xPoints, yPoints, 3);
        }
    }

    Point getOutputPoint(FunctionBlock fb, int outputIndex) {
        if (fb.outputArrows == null || outputIndex >= fb.outputArrows.length) {
            return new Point(fb.getX() + fb.getWidth(), fb.getY() + 30);
        }
        JLabel output = fb.outputArrows[outputIndex];
        Point p = SwingUtilities.convertPoint(output, output.getWidth(), output.getHeight() / 2, drawingPanel);
        // Don't apply zoom transformation here since it's applied in paintComponent
        return new Point((int)(p.x / drawingPanel.zoomFactor), (int)(p.y / drawingPanel.zoomFactor));
    }

    Point getInputPoint(FunctionBlock fb, int inputIndex) {
        if (inputIndex == -1) {
            // Status connection to center of block
            Point p = new Point(fb.getX() + fb.getWidth() / 2, fb.getY() + fb.getHeight() / 2);
            return new Point((int)(p.x / drawingPanel.zoomFactor), (int)(p.y / drawingPanel.zoomFactor));
        }
        if (fb.inputArrows == null || inputIndex >= fb.inputArrows.length) {
            return new Point(fb.getX(), fb.getY() + 30);
        }
        JLabel input = fb.inputArrows[inputIndex];
        Point p = SwingUtilities.convertPoint(input, 0, input.getHeight() / 2, drawingPanel);
        // Don't apply zoom transformation here since it's applied in paintComponent
        return new Point((int)(p.x / drawingPanel.zoomFactor), (int)(p.y / drawingPanel.zoomFactor));
    }

    // === TOPOLOGICAL SORT METHOD ===
    private List<FunctionBlock> getTopologicalOrder() {
        Map<FunctionBlock, Set<FunctionBlock>> adj = new HashMap<>();
        Map<FunctionBlock, Integer> inDegree = new HashMap<>();

        // Build adjacency list and in-degree map
        for (FunctionBlock fb : functionBlocks) {
            adj.put(fb, new HashSet<>());
            inDegree.put(fb, 0);
        }

        for (Connection c : connections) {
            if (adj.containsKey(c.from) && adj.containsKey(c.to)) {
                adj.get(c.from).add(c.to);
                inDegree.put(c.to, inDegree.get(c.to) + 1);
            }
        }

        // Initialize queue with all nodes having in-degree 0
        Queue<FunctionBlock> q = new LinkedList<>();
        Set<FunctionBlock> visited = new HashSet<>();

        for (FunctionBlock fb : functionBlocks) {
            if (inDegree.get(fb) == 0) {
                q.add(fb);
                visited.add(fb);
            }
        }

        List<FunctionBlock> order = new ArrayList<>();

        // Kahn’s Algorithm (extended for disconnected graphs)
        while (!q.isEmpty()) {
            FunctionBlock curr = q.poll();
            order.add(curr);

            for (FunctionBlock neighbor : adj.get(curr)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0 && !visited.contains(neighbor)) {
                    q.add(neighbor);
                    visited.add(neighbor);
                }
            }

            // Handle isolated or disconnected blocks
            if (q.isEmpty()) {
                for (FunctionBlock fb : functionBlocks) {
                    if (!visited.contains(fb)) {
                        q.add(fb);
                        visited.add(fb);
                        break;
                    }
                }
            }
        }

        // === Cycle detection ===
        if (order.size() != functionBlocks.size()) {
            Set<FunctionBlock> visitedC = new HashSet<>();
            Set<FunctionBlock> recStack = new HashSet<>();
            List<String> cyclePath = new ArrayList<>();

            for (FunctionBlock fb : functionBlocks) {
                if (!visitedC.contains(fb)) {
                    cyclePath.clear();
                    if (detectCycle(fb, adj, visitedC, recStack, cyclePath)) {
                        JOptionPane.showMessageDialog(this,
                                "Circular dependency detected:\n" + String.join(" → ", cyclePath) +
                                        "\nPlease edit/delete some arrows to resolve.",
                                "Cycle Detected",
                                JOptionPane.ERROR_MESSAGE);
                        return null;
                    }
                }
            }
        }

        // === Fallback: ensure all blocks are included ===
        for (FunctionBlock fb : functionBlocks) {
            if (!order.contains(fb)) {
                order.add(fb);
            }
        }

        return order;
    }

    private boolean detectCycle(FunctionBlock node, Map<FunctionBlock, Set<FunctionBlock>> adj,
                            Set<FunctionBlock> visited, Set<FunctionBlock> recStack, List<String> cyclePath) {
        System.out.println("detectCycle called on node: " + node.name);
        Map<FunctionBlock, FunctionBlock> parentMap = new HashMap<>();
        boolean result = dfsCycle(node, adj, visited, recStack, parentMap, cyclePath);
        System.out.println("detectCycle result for node " + node.name + ": " + result);
        return result;
    }

    private boolean dfsCycle(FunctionBlock current, Map<FunctionBlock, Set<FunctionBlock>> adj,
                            Set<FunctionBlock> visited, Set<FunctionBlock> recStack,
                            Map<FunctionBlock, FunctionBlock> parentMap, List<String> cyclePath) {
        //System.out.println("dfsCycle visiting: " + current.name);
        visited.add(current);
        recStack.add(current);

        for (FunctionBlock neighbor : adj.get(current)) {
            //System.out.println("Checking neighbor: " + neighbor.name + " of " + current.name);
            if (!visited.contains(neighbor)) {
                parentMap.put(neighbor, current);
                if (dfsCycle(neighbor, adj, visited, recStack, parentMap, cyclePath)) {
                    return true;
                }
            } else if (recStack.contains(neighbor)) {
                //System.out.println("Cycle detected at neighbor: " + neighbor.name + " from " + current.name);
                List<String> tempCycle = new ArrayList<>();
                FunctionBlock temp = current;
                tempCycle.add(neighbor.name);

                while (temp != null && temp != neighbor) {
                    tempCycle.add(temp.name);
                    temp = parentMap.get(temp);
                }
                tempCycle.add(neighbor.name);

                Collections.reverse(tempCycle);
                cyclePath.addAll(tempCycle);
                //System.out.println("Cycle path: " + String.join(" -> ", cyclePath));
                return true;
            }
        }

        recStack.remove(current);
        //System.out.println("dfsCycle leaving: " + current.name);
        return false;
    }

    class StatusBlock extends FunctionBlock {
        private boolean success;

        public StatusBlock(String name, BlockTemplate template) {
            super(name, template);  // match the constructor
        }

        // No @Override, since FunctionBlock has no execute()
        public void execute() {
            success = Math.random() > 0.5;
            System.out.println("StatusBlock " + name + " result: " 
                            + (success ? "SUCCESS" : "FAILURE"));
        }

        public boolean isSuccess() {
            return success;
        }
    }



    class Connection {
        FunctionBlock from, to;
        int fromIdx, toIdx;
        String type;

        Connection(FunctionBlock from, int fromIdx, FunctionBlock to, int toIdx, String type) {
            this.from = from;
            this.fromIdx = fromIdx;
            this.to = to;
            this.toIdx = toIdx;
            this.type = type;
        }
    }

    class FunctionBlock extends JPanel {
        private static final long serialVersionUID = 1L;

        String name;
        String originalName; // New field to store original name
        List<String> nameHistory; // List to store naming history
        String[] inputValues;
        JButton[] outputDots;
        transient BlockTemplate template;
        public JLabel[] inputArrows;
        public JLabel[] outputArrows;
        private Component outputPanel;

        private String getDefaultValue(String type) {
            switch (type) {
                case "float": return "0.0";
                case "integer": return "0";
                case "int": return "0";
                case "string": return "default_string";
                case "file": return "";
                case "graph": return "default_graph";
                case "Status": return "default_status";
                case "character": return "a";
                case "char": return "a";
                default: return "default";
            }
        }

        FunctionBlock(String name, BlockTemplate template) {
            super();
            this.name = name;
            this.originalName = name; // Initialize originalName with initial name
            this.nameHistory = new ArrayList<>();
            this.nameHistory.add(name);
            this.template = template;
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createTitledBorder(name));
            setBackground(new Color(230, 230, 250));
            setOpaque(true);

            inputValues = new String[template.inputCount];
            JPanel inputPanel = new JPanel();
            inputPanel.setLayout(new GridLayout(template.inputCount, 1, 0, 8));
            inputPanel.setOpaque(false);
            inputArrows = new JLabel[template.inputCount];

            for (int i = 0; i < template.inputCount; i++) {
                inputValues[i] = getDefaultValue(template.inputTypes[i]);

                final int idx = i;
                JLabel inArrow = new JLabel("→");
                inArrow.setFont(new Font("SansSerif", Font.BOLD, 16));
                inArrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                inArrow.setToolTipText("input" + (idx + 1) + " (" + template.inputTypes[idx] + ")");
                inArrow.setBorder(BorderFactory.createEmptyBorder(6, 5, 6, 20));
                inArrow.addMouseListener(new MouseAdapter() {
                    public void mouseReleased(MouseEvent e) {
                        if (dragSource != null) {
                            String outType = dragSource.template.outputTypes[dragSourceOutputIndex];
                            String inType = template.inputTypes[idx];
                            if (!outType.equals(inType)) {
                                JOptionPane.showMessageDialog(gui4.this,
                                    "Type mismatch: Cannot connect output (" + outType + ") to input (" + inType + ")",
                                    "Type Mismatch",
                                    JOptionPane.ERROR_MESSAGE);
                                dragSource = null;
                                dragSourceOutputIndex = -1;
                                return;
                            }

                            // Check if input already connected
                            boolean inputAlreadyConnected = false;
                            for (Connection conn : connections) {
                                if (conn.to == FunctionBlock.this && conn.toIdx == idx) {
                                    inputAlreadyConnected = true;
                                    break;
                                }
                            }
                            if (inputAlreadyConnected) {
                                JOptionPane.showMessageDialog(gui4.this,
                                    "Input " + (idx + 1) + " of block " + name + " is already connected.",
                                    "Connection Error",
                                    JOptionPane.ERROR_MESSAGE);
                                dragSource = null;
                                dragSourceOutputIndex = -1;
                                return;
                            }

                            connections.add(new Connection(dragSource, dragSourceOutputIndex, FunctionBlock.this, idx, inType));
                            dragSource = null;
                            dragSourceOutputIndex = -1;
                            drawingPanel.setCursor(Cursor.getDefaultCursor());
                            drawingPanel.repaint();
                        }
                    }
                });
                inputArrows[i] = inArrow;
                inputPanel.add(inArrow);
            }

            JPanel outputPanel = new JPanel(new GridLayout(template.outputCount, 1, 0, 8));
            outputPanel.setOpaque(false);
            outputArrows = new JLabel[template.outputCount];

            for (int j = 0; j < template.outputCount; j++) {
                final int oidx = j;
                JLabel outArrow = new JLabel("→");
                outArrow.setFont(new Font("SansSerif", Font.BOLD, 16));
                outArrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                outArrow.setToolTipText("output" + (oidx + 1) + " (" + template.outputTypes[oidx] + ")");
                outArrow.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        dragSource = FunctionBlock.this;
                        dragSourceOutputIndex = oidx;
                    }
                });
                outputArrows[j] = outArrow;
                outputPanel.add(outArrow);
            }

            JPanel ioWrapper = new JPanel(new BorderLayout());
            ioWrapper.setOpaque(false);
            ioWrapper.add(inputPanel, BorderLayout.WEST);
            ioWrapper.add(new JLabel(), BorderLayout.CENTER);

            this.add(ioWrapper, BorderLayout.CENTER);
            this.add(outputPanel, BorderLayout.EAST);

            // Add mouse listener for status connections (to the entire block)
            addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if (dragSource != null) {
                        // Check if the drag is from a Status output
                        boolean isStatusOutput = false;
                        if (dragSourceOutputIndex != -1 && dragSource.template != null &&
                            dragSourceOutputIndex < dragSource.template.outputTypes.length) {
                            isStatusOutput = "Status".equals(dragSource.template.outputTypes[dragSourceOutputIndex]);
                        }

                        if (isStatusOutput) {
                            // Create status connection to the entire block
                            // Check if already connected via status to avoid duplicates
                            boolean alreadyConnected = false;
                            for (Connection conn : connections) {
                                if (conn.from == dragSource && conn.to == FunctionBlock.this && conn.toIdx == -1) {
                                    alreadyConnected = true;
                                    break;
                                }
                            }
                            if (!alreadyConnected) {
                                connections.add(new Connection(dragSource, dragSourceOutputIndex, FunctionBlock.this, -1, "Status"));
                                dragSource = null;
                                dragSourceOutputIndex = -1;
                                drawingPanel.setCursor(Cursor.getDefaultCursor());
                                drawingPanel.repaint();
                            } else {
                                JOptionPane.showMessageDialog(gui4.this, "Status connection already exists.");
                            }
                        }
                    }
                }
            });

            JPopupMenu menu = new JPopupMenu();
            JMenuItem rename = new JMenuItem("Rename");
            JMenuItem changeInputs = new JMenuItem("Change Input Count");
            JMenuItem delete = new JMenuItem("Delete");

            rename.addActionListener(e -> {
                String oldName = FunctionBlock.this.name;
                String newName = JOptionPane.showInputDialog("Enter new name for " + oldName);

                if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                    newName = newName.trim();
                    renameFunctionBlock(oldName, newName);
                    FunctionBlock.this.name = newName;
                    FunctionBlock.this.nameHistory.add(newName);
                    // Keep originalName unchanged to track original
                    setBorder(BorderFactory.createTitledBorder(newName));
                    repaint();
                }
            });

            changeInputs.addActionListener(e -> {
                try {
                    int newCount = Integer.parseInt(JOptionPane.showInputDialog("Enter new input count:"));
                    if (newCount >= 0 && newCount != template.inputCount) {
                        // Remove existing connections to this block
                        connections.removeIf(conn -> conn.to == FunctionBlock.this);
                        
                        // Update template
                        String[] newInputTypes = new String[newCount];
                        for (int i = 0; i < newCount && i < template.inputTypes.length; i++) {
                            newInputTypes[i] = template.inputTypes[i];
                        }
                        for (int i = template.inputTypes.length; i < newCount; i++) {
                            newInputTypes[i] = "float"; // default type
                        }
                        
                        template.inputCount = newCount;
                        template.inputTypes = newInputTypes;
                        
                        // Rebuild the UI
                        rebuildUI();
                    }
                } catch (Exception ignored) {}
            });

            delete.addActionListener(e -> {
                functionBlocks.remove(FunctionBlock.this);
                drawingPanel.remove(FunctionBlock.this);
                connections.removeIf(conn -> conn.from == FunctionBlock.this || conn.to == FunctionBlock.this);
                updateCanvasSize();
                drawingPanel.repaint();
            });

            menu.add(rename);
            menu.add(changeInputs);
            menu.add(delete);
            this.setComponentPopupMenu(menu);

            int minHeight = 100;
            int numSlots = Math.max(template.inputCount, template.outputCount);
            int dynamicHeight = 30 * numSlots + 50;
            this.setSize(160, Math.max(minHeight, dynamicHeight));
            this.setPreferredSize(new Dimension(getWidth(), getHeight()));
            this.revalidate();
            this.setBounds(getLocation().x, getLocation().y, getWidth(), getHeight());

            enableDrag(this);
        }

        private void rebuildUI() {
            this.removeAll();

            this.setLayout(new BorderLayout(10, 10));
            this.setBorder(BorderFactory.createTitledBorder(name));
            this.setBackground(new Color(230, 230, 250));

            inputValues = new String[template.inputCount];
            JPanel inputPanel = new JPanel();
            inputPanel.setLayout(new GridLayout(template.inputCount, 1, 0, 8));
            inputPanel.setOpaque(false);
            inputArrows = new JLabel[template.inputCount];

            String[] oldValues = inputValues != null ? inputValues.clone() : null;

            for (int i = 0; i < template.inputCount; i++) {
                inputValues[i] = (oldValues != null && i < oldValues.length) ? oldValues[i] : getDefaultValue(template.inputTypes[i]);

                final int idx = i;
                JLabel inArrow = new JLabel("→");
                inArrow.setFont(new Font("SansSerif", Font.BOLD, 16));
                inArrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                inArrow.setToolTipText("input" + (idx + 1) + " (" + template.inputTypes[idx] + ")");
                inArrow.setBorder(BorderFactory.createEmptyBorder(6, 5, 6, 20));
                inArrow.addMouseListener(new MouseAdapter() {
                    public void mouseReleased(MouseEvent e) {
                        if (dragSource != null) {
                            String outType = dragSource.template.outputTypes[dragSourceOutputIndex];
                            String inType = template.inputTypes[idx];
                            if (!outType.equals(inType)) {
                                JOptionPane.showMessageDialog(gui4.this,
                                    "Type mismatch: Cannot connect output (" + outType + ") to input (" + inType + ")",
                                    "Type Mismatch",
                                    JOptionPane.ERROR_MESSAGE);
                                dragSource = null;
                                dragSourceOutputIndex = -1;
                                return;
                            }

                            connections.add(new Connection(dragSource, dragSourceOutputIndex, FunctionBlock.this, idx, inType));
                            dragSource = null;
                            dragSourceOutputIndex = -1;
                            drawingPanel.setCursor(Cursor.getDefaultCursor());
                            drawingPanel.repaint();
                        }
                    }
                });
                inputArrows[i] = inArrow;
                inputPanel.add(inArrow);
            }

            JPanel outputPanel = new JPanel(new GridLayout(template.outputCount, 1, 0, 8));
            outputPanel.setOpaque(false);
            outputArrows = new JLabel[template.outputCount];

            for (int j = 0; j < template.outputCount; j++) {
                final int oidx = j;
                JLabel outArrow = new JLabel("→");
                outArrow.setFont(new Font("SansSerif", Font.BOLD, 16));
                outArrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                outArrow.setToolTipText("output" + (oidx + 1) + " (" + template.outputTypes[oidx] + ")");
                outArrow.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        dragSource = FunctionBlock.this;
                        dragSourceOutputIndex = oidx;
                    }
                });
                outputArrows[j] = outArrow;
                outputPanel.add(outArrow);
            }

            JPanel ioWrapper = new JPanel(new BorderLayout());
            ioWrapper.setOpaque(false);
            ioWrapper.add(inputPanel, BorderLayout.WEST);
            ioWrapper.add(new JLabel(), BorderLayout.CENTER);

            this.add(ioWrapper, BorderLayout.CENTER);
            this.add(outputPanel, BorderLayout.EAST);

            int minHeight = 100;
            int numSlots = Math.max(template.inputCount, template.outputCount);
            int dynamicHeight = 30 * numSlots + 50;
            this.setSize(160, Math.max(minHeight, dynamicHeight));
            this.setPreferredSize(new Dimension(getWidth(), getHeight()));
            this.revalidate();
            this.repaint();
        }

        private void renameFunctionBlock(String oldName, String newName) {}

        void startDrag(FunctionBlock src, int outIndex) {
            dragSource = src;
            dragSourceOutputIndex = outIndex;
        }

        public String[] getInputs() {
            return inputValues;
        }

        public String getName() {
            return name;
        }

        private void enableDrag(JComponent comp) {
            final Point[] offset = new Point[1];

            comp.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    offset[0] = e.getPoint();
                    comp.requestFocusInWindow(); 
                }
            });

            comp.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    Point parent = SwingUtilities.convertPoint(comp, e.getPoint(), drawingPanel);
                    if (offset[0] != null) {
                        // Account for current zoom factor when dragging
                        int newX = parent.x - offset[0].x;
                        int newY = parent.y - offset[0].y;
                        
                        comp.setLocation(newX, newY);
                        updateCanvasSize();
                        drawingPanel.repaint();
                    }
                }
            });
        }
    }

    private boolean isPointNearLine(Point pt, Point a, Point b, double tolerance) {
        double dist = ptLineDist(a.x, a.y, b.x, b.y, pt.x, pt.y);
        return dist <= tolerance;
    }

    private double ptLineDist(int x1, int y1, int x2, int y2, int px, int py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            dx = px - x1;
            dy = py - y1;
            return Math.sqrt(dx * dx + dy * dy);
        }

        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        if (t < 0) {
            dx = px - x1;
            dy = py - y1;
        } else if (t > 1) {
            dx = px - x2;
            dy = py - y2;
        } else {
            double nearX = x1 + t * dx;
            double nearY = y1 + t * dy;
            dx = px - nearX;
            dy = py - nearY;
        }
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void saveExecutionPlan(List<FunctionBlock> order) {
        if (order == null || order.isEmpty()) {
            System.err.println("No execution plan to save.");
            return;
        }

        try (PrintWriter writer = new PrintWriter("execution.txt")) {

            // === Print Topological Order ===
            writer.println("# Topological Sort Order");
            for (int i = 0; i < order.size(); i++) {
                writer.println((i + 1) + ". " + order.get(i).originalName);
            }
            writer.println(); // blank line for separation

            // === Write Each Function Block ===
            for (FunctionBlock fb : order) {
                String varName = fb.originalName;
                writer.println("let $" + varName);

                // Mark status blocks if applicable
                if (fb.template.outputTypes.length > 0 &&
                    "Status".equals(fb.template.outputTypes[0])) {
                    writer.println("#status " + fb.originalName);
                }

                // Inputs (either connection or default)
                for (int i = 0; i < fb.template.inputCount; i++) {
                    String inputValue = "";

                    // First try: connection input
                    for (Connection c : connections) {
                        if (c.to == fb && c.toIdx == i) {
                            inputValue = "$" + c.from.originalName + ".output" + (c.fromIdx + 1);
                            break;
                        }
                    }

                    // If no connection found, use the input value (which is the default or user-set)
                    if (inputValue.isEmpty()) {
                        inputValue = fb.inputValues[i];
                    }

                    writer.println("    input" + (i + 1) + " " + inputValue);
                }

                // Outputs
                for (int o = 0; o < fb.template.outputCount; o++) {
                    writer.println("    output" + (o + 1) + " $" + varName + ".output" + (o + 1));
                }
            }

            // === Print All Connections ===
            writer.println(); // separation line
            for (Connection c : connections) {
                if (c.toIdx == -1) {
                    writer.println("$" + c.from.originalName + ".output" + (c.fromIdx + 1) + " -> $" + c.to.originalName + " (status)");
                } else {
                    writer.println("$" + c.from.originalName + ".output" + (c.fromIdx + 1)
                            + " -> $" + c.to.originalName + ".input" + (c.toIdx + 1));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

   
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new gui4().setVisible(true);
        });
    }
}
