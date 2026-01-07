package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ScrcpyOptionsDialog extends JPanel {
    private final JTextField scrcpyPathField;
    private final HintTextField customFlagsField;
    private final JCheckBox doNotShowAgainCheckBox;
    // dynamic option storage
    private final Map<String, JCheckBox> checkBoxMap = new HashMap<>();
    private final Map<String, JRadioButton> radioButtonMap = new HashMap<>();
    private final Map<String, ButtonGroup> radioGroups = new HashMap<>();

    // option definitions
    private static final Object[][] OPTION_DEFS = new Object[][] {
        {"--show-touches", "Show Touches", "checkbox"},
        {"--stay-awake", "Stay Awake", "checkbox"},
        {"--no-audio", "No Audio", "checkbox"},
        {"video-codec", "Video Codec", "radio", new String[][] {
            {"h264", "--video-codec=h264"},
            {"h265", "--video-codec=h265"},
            {"av1", "--video-codec=av1"}
        }, "h264"}
    };

    /**
     * @return true if this dialog should NOT be displayed
     */
    public static boolean isDoNotShowAgain() {
        return PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SCRCPY_DO_NOT_SHOW_AGAIN);
    }

    public static List<String> getCustomArgs() {
        String argsStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SCRCPY_ARGS);
        List<String> argList = GsonHelper.stringToList(argsStr, String.class);
        if (argList.isEmpty()) {
            // defaults
            argList.add("--show-touches");
            argList.add("--stay-awake");
            argList.add("--no-audio");
        }
        return argList;
    }

    public static boolean showRemoteServerDialog(Component parent) {
        ScrcpyOptionsDialog dialog = new ScrcpyOptionsDialog();
        int rc = DialogHelper.showCustomDialog(parent, dialog, "scrcpy settings", new String[]{"Ok", "Cancel"});
        if (rc != JOptionPane.OK_OPTION) return false;
        // save settings
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SCRCPY_PATH, dialog.getScrcpyPath());
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SCRCPY_ARGS, GsonHelper.toJson(dialog.getArgs()));
        PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SCRCPY_DO_NOT_SHOW_AGAIN, dialog.doNotShowAgainCheckBox.isSelected());
        return true;
    }

    public ScrcpyOptionsDialog() {
        String path = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SCRCPY_PATH);
        if (TextUtils.isEmpty(path)) {
            // try to locate scrcpy in PATH
            path = DeviceManager.getInstance().findApp(DeviceManager.APP_SCRCPY);
        }
        List<String> argList = getCustomArgs();
        boolean doNotShowAgain = isDoNotShowAgain();

        setLayout(new MigLayout("fillx,wrap 2", "[][grow]"));

        scrcpyPathField = new JTextField(path != null ? path : "");
        JButton browseButton = new JButton();
        browseButton.setIcon(UiUtils.getImageIcon(Icons.OPEN_FOLDER, 20));
        browseButton.setToolTipText("Browse");
        browseButton.addActionListener(e -> browseForScrcpyPath());
        JPanel pathPanel = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill][]", ""));
        pathPanel.add(new JLabel("Path:"));
        pathPanel.add(scrcpyPathField, "growx, push, spanx 1");
        pathPanel.add(browseButton, "");
        add(pathPanel, "growx,span 2,wrap");

        // dynamically add options
        for (Object[] def : OPTION_DEFS) {
            String key = (String) def[0];
            String label = (String) def[1];
            String type = (String) def[2];
            if ("checkbox".equals(type)) {
                JCheckBox cb = new JCheckBox(label);
                checkBoxMap.put(key, cb);
                add(cb, "span 2");
            } else if ("radio".equals(type)) {
                add(new JLabel(label + ":"));
                JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                ButtonGroup group = new ButtonGroup();
                String[][] radios = (String[][]) def[3];
                String defaultRadio = (String) def[4];
                for (String[] radioDef : radios) {
                    String radioLabel = radioDef[0];
                    String radioFlag = radioDef[1];
                    JRadioButton rb = new JRadioButton(radioLabel);
                    radioButtonMap.put(radioFlag, rb);
                    group.add(rb);
                    radioPanel.add(rb);
                    if (radioLabel.equals(defaultRadio)) rb.setSelected(true);
                }
                radioGroups.put(key, group);
                add(radioPanel, "growx,span 1,wrap");
            }
        }

        add(new JLabel("custom flags:"));
        customFlagsField = new HintTextField("eg: --max-fps=60", null);
        add(customFlagsField, "growx,span 1,wrap");

        add(Box.createVerticalStrut(10), "span 2");
        doNotShowAgainCheckBox = new JCheckBox("Do not show again");
        add(doNotShowAgainCheckBox, "span 2,wrap");

        // set initial values
        for (String arg : argList) {
            if (checkBoxMap.containsKey(arg)) {
                checkBoxMap.get(arg).setSelected(true);
            } else if (arg.startsWith("--video-codec=")) {
                for (String flag : radioButtonMap.keySet()) {
                    if (arg.equals(flag)) radioButtonMap.get(flag).setSelected(true);
                }
            } else {
                String prev = customFlagsField.getText();
                if (prev == null || prev.isEmpty() || prev.equals("eg: --max-fps=60")) customFlagsField.setText(arg);
                else customFlagsField.setText(prev + " " + arg);
            }
        }
        doNotShowAgainCheckBox.setSelected(doNotShowAgain);
    }

    private void browseForScrcpyPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            scrcpyPathField.setText(file.getAbsolutePath());
        }
    }

    public String getScrcpyPath() {
        return scrcpyPathField.getText().trim();
    }

    public List<String> getArgs() {
        List<String> args = new ArrayList<>();
        for (String key : checkBoxMap.keySet()) {
            if (checkBoxMap.get(key).isSelected()) args.add(key);
        }
        for (String flag : radioButtonMap.keySet()) {
            if (radioButtonMap.get(flag).isSelected()) args.add(flag);
        }
        String custom = customFlagsField.getText();
        if (custom != null && !custom.isEmpty() && !custom.equals("eg: --max-fps=60")) {
            for (String s : custom.split(" ")) {
                if (!s.trim().isEmpty()) args.add(s.trim());
            }
        }
        return args;
    }
}
