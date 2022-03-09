import com.formdev.flatlaf.FlatLightLaf;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.*;

class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private static final String FRAME_X = "frame-x";
    private static final String FRAME_Y = "frame-y";
    private static final String FRAME_W = "frame-w";
    private static final String FRAME_H = "frame-h";

    private JPanel panel;
    private JTable table;
    private JFrame frame;
    private DeviceTableModel model;

    public MainApplication() {
        BasicConfigurator.configure();

        SwingUtilities.invokeLater(this::initializeUI);
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Device Manager");

        MainApplication app = new MainApplication();
        log.info("main: ");
    }

    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.error("initializeUI: {}", e.getMessage());
        }

        final Taskbar taskbar = Taskbar.getTaskbar();
        try {
            Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
            taskbar.setIconImage(image);
        } catch (final Exception e) {
            log.error("Exception: {}", e.getMessage());
        }

        frame = new JFrame("Device Manager");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        restoreFrame();
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveFrameSize();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveFrameSize();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        table = new JTable();
        table.setRowHeight(30);
        //table.getTableHeader().setBackground(Color.LIGHT_GRAY);

        model = new DeviceTableModel();
        table.setModel(model);

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        frame.setContentPane(panel);
        //frame.pack();
        frame.setVisible(true);

        // Create the drag and drop listener
        MyDragDropListener myDragDropListener = new MyDragDropListener(table);

        // Connect the label with a drag and drop listener
        new DropTarget(table, myDragDropListener);

        //listDevices();
    }

    private void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        prefs.putInt(FRAME_X, frame.getX());
        prefs.putInt(FRAME_Y, frame.getY());
        prefs.putInt(FRAME_W, frame.getWidth());
        prefs.putInt(FRAME_H, frame.getHeight());
    }

    private void restoreFrame() {
        Preferences prefs = Preferences.userRoot();
        int x = prefs.getInt(FRAME_X, 200);
        int y = prefs.getInt(FRAME_Y, 200);
        int w = prefs.getInt(FRAME_W, 500);
        int h = prefs.getInt(FRAME_H, 300);

        log.debug("restoreFrame: x:{}, y:{}, w:{}, h:{}", x, y, w, h);
        frame.setLocation(x, y);
        frame.setSize(w, h);
    }

    private void listDevices() {
        List<String> results = runScript("device-list");
        List<Device> deviceList = new ArrayList<>();
        if (results != null) {
            for (String result : results) {
                if (result.length() == 0 || result.startsWith("List")) continue;

                String[] deviceArr = result.split(" ");
                if (deviceArr.length <= 1) continue;

                Device device = new Device();
                device.serial = deviceArr[0];
                deviceList.add(device);
            }
        }

        model.setDeviceList(deviceList);
    }

    private List<String> runScript(String name) {
        log.debug("runScript: {}", name);
        try {
            InputStream is = getClass().getResourceAsStream("scripts/" + name + ".sh");
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) {
                sb.append(line).append('\n');
            }
            r.close();

            // create temp file
            File tempFile = File.createTempFile(name, ".sh");
            FileUtils.writeStringToFile(tempFile, sb.toString());
            tempFile.setExecutable(true);

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(tempFile.getAbsolutePath());
            try {
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                List<String> resultList = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    log.debug("runScript: {}", line);
                    resultList.add(line);
                }
                reader.close();
                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    log.debug("runScript: DONE: {}", resultList.size());
                    System.exit(0);
                } else {
                    log.error("runScript: error:{}", exitVal);
                }
                return resultList;
            } catch (Exception e) {
                log.error("runScript: Exception:{}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("runScript: {}", e.getMessage());
        }
        return null;
    }

}
