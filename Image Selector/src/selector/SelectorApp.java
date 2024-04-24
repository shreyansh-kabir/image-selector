package selector;

import static selector.SelectionModel.SelectionState.NO_SELECTION;
import static selector.SelectionModel.SelectionState.PROCESSING;
import static selector.SelectionModel.SelectionState.SELECTED;
import static selector.SelectionModel.SelectionState.SELECTING;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import selector.SelectionModel.SelectionState;

/**
 * A graphical application for selecting and extracting regions of images.
 */
public class SelectorApp implements PropertyChangeListener {

    /**
     * Our application window.  Disposed when application exits.
     */
    private final JFrame frame;

    /**
     * Component for displaying the current image and selection tool.
     */
    private final ImagePanel imgPanel;

    /**
     * The current state of the selection tool.  Must always match the model used by `imgPanel`.
     */
    private SelectionModel model;

    /* Components whose state must be changed during the selection process. */
    private JMenuItem saveItem;
    private JMenuItem undoItem;
    private JButton cancelButton;
    private JButton undoButton;
    private JButton resetButton;
    private JButton finishButton;
    private final JLabel statusLabel;


    /**
     * Construct a new application instance.  Initializes GUI components, so must be invoked on the
     * Swing Event Dispatch Thread.  Does not show the application window (call `start()` to do
     * that).
     */
    public SelectorApp() {
        // Initialize application window
        frame = new JFrame("Selector");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add status bar
        statusLabel = new JLabel();
        frame.add(statusLabel, BorderLayout.PAGE_END);

        // Add image component with scrollbars
        imgPanel = new ImagePanel();
        JScrollPane scrollPane = new JScrollPane(imgPanel);
        scrollPane.setPreferredSize(new Dimension(500, 500));
        frame.add(scrollPane, BorderLayout.CENTER);

        // Add menu bar
        frame.setJMenuBar(makeMenuBar());

        // Add control buttons
        frame.add(makeControlPanel(), BorderLayout.EAST);

        // Controller: Set initial selection tool and update components to reflect its state
        setSelectionModel(new PointToPointSelectionModel(true));
    }

    /**
     * Create and populate a menu bar with our application's menus and items and attach listeners.
     * Should only be called from constructor, as it initializes menu item fields.
     */
    private JMenuBar makeMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Create and populate File menu
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openItem = new JMenuItem("Open...");
        fileMenu.add(openItem);
        saveItem = new JMenuItem("Save...");
        fileMenu.add(saveItem);
        JMenuItem closeItem = new JMenuItem("Close");
        fileMenu.add(closeItem);
        JMenuItem exitItem = new JMenuItem("Exit");
        fileMenu.add(exitItem);

        // Create and populate Edit menu
        JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        undoItem = new JMenuItem("Undo");
        editMenu.add(undoItem);

        //Keyboard shortcuts
        openItem.setMnemonic(KeyEvent.VK_O);
        saveItem.setMnemonic(KeyEvent.VK_S);
        closeItem.setMnemonic(KeyEvent.VK_C);
        exitItem.setMnemonic(KeyEvent.VK_E);
        undoItem.setMnemonic(KeyEvent.VK_U);

        // Controller: Attach menu item listeners
        openItem.addActionListener(e -> openImage());
        closeItem.addActionListener(e -> imgPanel.setImage(null));
        saveItem.addActionListener(e -> saveSelection());
        exitItem.addActionListener(e -> frame.dispose());
        undoItem.addActionListener(e -> model.undo());

        return menuBar;
    }

    /**
     * Return a panel containing buttons for controlling image selection.  Should only be called
     * from constructor, as it initializes button fields.
     */
    private JPanel makeControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(0, 1)); // Any number of rows, 1 column

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> model.cancelProcessing());
        controlPanel.add(cancelButton);

        undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> model.undo());
        controlPanel.add(undoButton);

        resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> model.reset());
        controlPanel.add(resetButton);

        finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> model.finishSelection());
        controlPanel.add(finishButton);

        return controlPanel;
    }

    /**
     * Start the application by showing its window.
     */
    public void start() {
        // Compute ideal window size
        frame.pack();

        frame.setVisible(true);
    }

    /**
     * React to property changes in an observed model.  Supported properties include: * "state":
     * Update components to reflect the new selection state.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("state".equals(evt.getPropertyName())) {
            reflectSelectionState(model.state());
        }
    }

    /**
     * Update components to reflect a selection state of `state`.  Disable buttons and menu items
     * whose actions are invalid in that state, and update the status bar.
     */
    private void reflectSelectionState(SelectionState state) {
        // Update status bar to show current state
        statusLabel.setText(state.toString());

        cancelButton.setEnabled(state == PROCESSING);
        undoButton.setEnabled(state != NO_SELECTION);
        finishButton.setEnabled(state == SELECTING);
        saveItem.setEnabled(state == SELECTED);
    }

    /**
     * Return the model of the selection tool currently in use.
     */
    public SelectionModel getSelectionModel() {
        return model;
    }

    /**
     * Use `newModel` as the selection tool and update our view to reflect its state.  This
     * application will no longer respond to changes made to its previous selection model and will
     * instead respond to property changes from `newModel`.
     */
    public void setSelectionModel(SelectionModel newModel) {
        // Stop listening to old model
        if (model != null) {
            model.removePropertyChangeListener(this);
        }

        imgPanel.setSelectionModel(newModel);
        model = imgPanel.selection();
        model.addPropertyChangeListener("state", this);

        // Since the new model's initial state may be different from the old model's state, manually
        //  trigger an update to our state-dependent view.
        reflectSelectionState(model.state());
    }

    /**
     * Start displaying and selecting from `img` instead of any previous image.  Argument may be
     * null, in which case no image is displayed and the current selection is reset.
     */
    public void setImage(BufferedImage img) {
        imgPanel.setImage(img);
    }

    /**
     * Allow the user to choose a new image from an "open" dialog.  If they do, start displaying and
     * selecting from that image.  Show an error message dialog (and retain any previous image) if
     * the chosen image could not be opened.
     */
    private void openImage() {
        JFileChooser chooser = new JFileChooser();

        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

        // Filter for file extensions supported by Java's ImageIO readers
        chooser.setFileFilter(new FileNameExtensionFilter("Image files",
                ImageIO.getReaderFileSuffixes()));

        int returnVal = chooser.showOpenDialog(this.frame);
        while (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            try {
                BufferedImage img = ImageIO.read(file);
                if (img == null) { // happens when the file is not a supported image
                    // show error dialog
                    JOptionPane.showMessageDialog(frame,
                            "Could not read the image at " + file.getPath(),
                            "Unsupported image format",
                            JOptionPane.ERROR_MESSAGE);
                    returnVal = chooser.showOpenDialog(this.frame); // re-show the open dialog
                } else {
                    this.setImage(img);
                    return;
                }

            } catch (IOException e) {
                // show error dialog
                JOptionPane.showMessageDialog(frame,
                        "An IOException was thrown while trying to read the file",
                        "File reading error",
                        JOptionPane.ERROR_MESSAGE);
                returnVal = chooser.showOpenDialog(this.frame); // re-show the open dialog
            }
        }
    }

    /**
     * Save the selected region of the current image to a file selected from a "save" dialog. Show
     * an error message dialog if the image could not be saved.
     */
    private void saveSelection() {
        JFileChooser chooser = new JFileChooser();
        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        // We always save in PNG format, so only show existing PNG files
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));

        int returnVal = chooser.showSaveDialog(this.frame);
        while (returnVal == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith("png")) {
                f = new File(f.getAbsolutePath() + ".png");
            }

            if (f.exists()) {
                int confirm = JOptionPane.showConfirmDialog(frame,
                        "The file already exists. Do you want to overwrite it?",
                        "File Exists",
                        JOptionPane.YES_NO_CANCEL_OPTION);
                if (confirm == JOptionPane.NO_OPTION || confirm == JOptionPane.CANCEL_OPTION) {
                    returnVal = chooser.showSaveDialog(this.frame);
                }
            }

            try {
                ImageIO.write(model.image(), "png", f);
                model.saveSelection(new FileOutputStream(f));
                JOptionPane.showMessageDialog(null, "Image saved successfully.");
                return;

            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                        e.getMessage(),
                        e.getClass().toString(),
                        JOptionPane.ERROR_MESSAGE);
                returnVal = chooser.showSaveDialog(this.frame);
            }
        }
    }

    /**
     * Run an instance of SelectorApp.  No program arguments are expected.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Set Swing theme to look the same (and less old) on all operating systems.
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
                /* If the Nimbus theme isn't available, just use the platform default. */
            }

            // Create and start the app
            SelectorApp app = new SelectorApp();
            app.start();
        });
    }
}
